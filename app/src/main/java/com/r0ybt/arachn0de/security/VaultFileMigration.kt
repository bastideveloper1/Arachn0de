package com.r0ybt.arachn0de.security

import java.io.*
import com.r0ybt.arachn0de.backup.syncBackupDirectory
import java.security.MessageDigest

/** Exact relative inventory; no broad deletion, symlink traversal or absolute backup paths. */
internal object VaultFileMigration {
    data class Entry(val path:String,val size:Long,val sha256:String)
    fun checked(root:File,path:String):File {
        require(path.isNotEmpty() && !File(path).isAbsolute && path.split('/').none { it.isEmpty() || it=="." || it==".." })
        val file=File(root.canonicalFile,path)
        require(file.canonicalFile==file.absoluteFile.normalize()) { "No se admiten enlaces en la migración." }
        require(file.canonicalPath.startsWith(root.canonicalPath+File.separator))
        return file
    }
    private fun digest(file:File,protected:Boolean):Pair<Long,String> {
        val hash=MessageDigest.getInstance("SHA-256");var size=0L
        (if(protected) SecureFiles.input(file) else file.inputStream()).use { input ->
            val buffer=ByteArray(32768)
            try { while(true) { val n=input.read(buffer);if(n<0) break;size=Math.addExact(size,n.toLong());hash.update(buffer,0,n) } }
            finally { buffer.fill(0) }
        }
        return size to hash.digest().joinToString("") { "%02x".format(it) }
    }
    fun inventory(root:File,excludedTopLevel:Set<String> = emptySet()):List<Entry> {
        if(!root.exists()) return emptyList()
        val canonicalRoot=root.canonicalFile
        val result=mutableListOf<Entry>()
        fun visit(directory:File) {
            require(directory.canonicalFile==directory.absoluteFile.normalize()) { "Directorio enlazado." }
            for(file in requireNotNull(directory.listFiles()) { "No se pudo inventariar el almacenamiento." }.sortedBy { it.name }) {
                val relative=file.relativeTo(canonicalRoot).invariantSeparatorsPath
                if(relative.substringBefore('/') in excludedTopLevel) continue
                checked(root,relative)
                if(file.isDirectory) visit(file)
                else { require(file.isFile);val (size,hash)=digest(file,false);result.add(Entry(relative,size,hash)) }
            }
        }
        visit(canonicalRoot);return result
    }
    fun copy(source:File,target:File,entries:List<Entry>,availableBytes:()->Long={ target.usableSpace },checkCancelled:()->Unit={},resumeUnactivated:Boolean=false,syncDirectory:(File)->Unit=::syncBackupDirectory) {
        require(source.canonicalFile!=target.canonicalFile)
        require(entries.map { it.path }.toSet().size==entries.size)
        val required=entries.fold(0L) { total,e ->
            require(e.size>=0 && e.sha256.matches(Regex("[a-f0-9]{64}")))
            Math.addExact(total,Math.addExact(e.size,60L+20L*((e.size+65535L)/65536L)))
        }
        require(availableBytes()>=required) { "No hay espacio suficiente; el origen se conserva." }
        for(entry in entries) {
            checkCancelled()
            val original=checked(source,entry.path);val destination=checked(target,entry.path)
            require(digest(original,false)==(entry.size to entry.sha256)) { "El origen cambió; se conserva sin activar." }
            check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
            if(destination.exists()) {
                require(resumeUnactivated) { "El destino de migración debe ser nuevo." }
                if(runCatching { digest(destination,true)==(entry.size to entry.sha256) }.getOrDefault(false)) {
                    syncParents(destination,target,syncDirectory);continue
                }
                check(destination.delete()) // Only the explicitly unactivated staging destination.
            }
            original.inputStream().use { input -> SecureFiles.output(destination).use { output ->
                val buffer=ByteArray(32768)
                try { while(true) { checkCancelled();val n=input.read(buffer);if(n<0) break;output.write(buffer,0,n) } }
                finally { buffer.fill(0) }
            } }
            syncParents(destination,target,syncDirectory)
            check(digest(destination,true)==(entry.size to entry.sha256)) { "La copia no coincide con el origen." }
        }
    }
    private fun syncParents(file:File,target:File,sync:(File)->Unit) {
        var directory:File?=file.parentFile
        val boundary=target.canonicalFile
        while(directory!=null && (directory.canonicalFile==boundary || directory.canonicalPath.startsWith(boundary.path+File.separator))) {
            sync(directory)
            if(directory.canonicalFile==boundary) break
            directory=directory.parentFile
        }
    }
    fun verify(source:File,target:File,entries:List<Entry>) {
        for(entry in entries) {
            check(digest(checked(source,entry.path),false)==(entry.size to entry.sha256))
            check(digest(checked(target,entry.path),true)==(entry.size to entry.sha256))
        }
    }
    /** Only after verified activation and transfer of every editor/backup reservation. */
    fun removeVerifiedSources(source:File,target:File,entries:List<Entry>) {
        for(entry in entries) {
            val original=checked(source,entry.path)
            if(!original.exists()) continue // Idempotent recovery of confirmed cleanup.
            check(digest(checked(target,entry.path),true)==(entry.size to entry.sha256))
            check(digest(original,false)==(entry.size to entry.sha256)) { "El archivo original cambió; no se elimina." }
            check(original.delete()) { "Limpieza pendiente; la copia cifrada se conserva." }
        }
    }
}
