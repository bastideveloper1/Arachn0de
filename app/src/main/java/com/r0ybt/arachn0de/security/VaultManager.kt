package com.r0ybt.arachn0de.security

import android.content.Context
import androidx.room.Room
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import java.io.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Authentication owns opening, migration and lifetime. No key or password is persisted plain. */
internal class VaultManager(private val base:Context,private val sync:(File)->Unit=::syncBackupDirectory,
    private val openEncrypted:(VaultContext,ByteArray)->Arachn0deDatabase={ctx,key->Arachn0deDatabase.create(ctx,key)}) {
    private val operations=Mutex()
    private val home=File(base.filesDir,"security-v1").apply { check(mkdirs() || isDirectory) }
    private val stores=File(home,"stores").apply { check(mkdirs() || isDirectory) }
    private val index=File(home,"index")
    private val pending=File(home,"pending")
    val current=MutableStateFlow<VaultSession?>(null)
    val backgrounded=MutableStateFlow(false)
    private val timerScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var backgroundJob:Job?=null
    private var backgroundAt:Long?=null
    private fun timeout():Long {
        val value=runCatching { current.value?.context?.getSharedPreferences("security_preferences",0)?.getLong("background_timeout",30000L) ?: 0L }.getOrDefault(0L)
        return if(value in setOf(0L,30000L,60000L,300000L)) value else 0L
    }
    fun background() {
        backgrounded.value=true;backgroundAt=android.os.SystemClock.elapsedRealtime();backgroundJob?.cancel()
        val wait=timeout();backgroundJob=timerScope.launch { delay(wait);lock() }
    }
    suspend fun foreground() {
        backgroundJob?.cancel();backgroundJob=null
        val elapsed=backgroundAt?.let { android.os.SystemClock.elapsedRealtime()-it }
        if(elapsed!=null && (elapsed<0 || elapsed>=timeout())) lock()
        backgroundAt=null;backgrounded.value=false
    }
    var savedUi:Map<String,List<Any?>>?=null
    private var failures=0
    private var retryAt=0L
    val canCreateSecondary:Boolean get()=ids().size==1
    val configured:Boolean get() { DurableVaultFile.recover(index,sync);return index.exists() }
    private fun ids():List<UUID> {
        DurableVaultFile.recover(index,sync)
        if(!index.exists()) return emptyList()
        val array=JSONArray(index.readText());val ids=(0 until array.length()).map { UUID.fromString(array.getString(it)) }
        require(ids.distinct().size==ids.size && ids.size in 1..2)
        return ids
    }
    private fun root(id:UUID)=File(stores,id.toString()).also { require(it.canonicalFile.parentFile==stores.canonicalFile) { "Almacén fuera del directorio controlado." } }
    private fun keyFile(id:UUID)=File(root(id),"key.envelope")
    private fun databaseKey(key:ByteArray,id:UUID):ByteArray {
        val salt=java.nio.ByteBuffer.allocate(32).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()
        val derived=VaultCrypto.subkey(key,salt,"database-v1")
        return try { derived.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII) } finally { derived.fill(0) }
    }
    private fun envelope(id:UUID):ByteArray { DurableVaultFile.recover(keyFile(id),sync);return keyFile(id).readBytes() }
    private fun context(id:UUID,key:ByteArray):VaultContext {
        val access=SecureFiles.Session(id,root(id),key);SecureFiles.register(access)
        return VaultContext(base,access)
    }
    private fun open(ctx:VaultContext,key:ByteArray):VaultSession {
        val password=databaseKey(key,ctx.session.id)
        var db:Arachn0deDatabase?=null
        try {
            val role=SecureFiles.text(File(ctx.session.root,"identity.enc"));require(role=="primary" || role=="secondary")
            ctx.primary=role=="primary"
            db=openEncrypted(ctx,password);ctx.database=db
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) }
            return VaultSession(ctx.session,ctx,db,role=="primary")
        } catch(failure:Throwable) { db?.close();ctx.clearMemory();SecureFiles.revoke(ctx.session);throw failure }
        finally { password.fill(0) }
    }
    suspend fun unlock(password:CharArray)=withContext(Dispatchers.IO) {
        operations.withLock {
            require(current.value==null)
            val wait=retryAt-android.os.SystemClock.elapsedRealtime()
            if(wait>0) delay(wait)
            var match:Pair<UUID,ByteArray>?=null
            try {
                for(id in ids()) {
                    val candidate=runCatching { VaultCrypto.unwrap(id,password,envelope(id)) }.getOrNull()
                    if(candidate!=null) { check(match==null);match=id to candidate }
                }
                if(match==null) {
                    failures++;retryAt=android.os.SystemClock.elapsedRealtime()+minOf(30000L,1000L shl minOf(failures-1,5))
                    throw VaultAuthenticationException()
                }
                val (id,key)=match!!
                val opened=open(context(id,key),key)
                try { AttachmentRepository.fileOperations.withLock { cleanupOriginals(opened.context) } } catch(failure:Throwable) { opened.close();throw failure }
                SecureFiles.activeProfile=id;current.value=opened;failures=0;retryAt=0
            } finally { match?.second?.fill(0);password.fill('\u0000') }
        }
    }
    /** Export the verified initial migration snapshot without opening a damaged native database.
     * Never replaces live data. The user must explicitly choose this older recovery point. */
    suspend fun exportInitialRecovery(password:CharArray,backupPassword:CharArray,output:OutputStream)=withContext(Dispatchers.IO) {
        operations.withLock {
            var match:Pair<UUID,ByteArray>?=null;var ctx:VaultContext?=null
            try {
                require(current.value==null && backupPassword.size>=8)
                val wait=retryAt-android.os.SystemClock.elapsedRealtime()
                if(wait>0) delay(wait)
                val candidates=ids().ifEmpty {
                    DurableVaultFile.recover(pending,sync)
                    if(pending.exists()) listOf(UUID.fromString(pending.readText())) else emptyList()
                }
                for(id in candidates) {
                    val candidate=runCatching {VaultCrypto.unwrap(id,password,envelope(id))}.getOrNull()
                    if(candidate!=null) {check(match==null);match=id to candidate}
                }
                if(match==null) {
                    failures++;retryAt=android.os.SystemClock.elapsedRealtime()+minOf(30000L,1000L shl minOf(failures-1,5))
                    throw VaultAuthenticationException()
                }
                val (id,key)=match!!
                ctx=context(id,key)
                require(SecureFiles.text(File(ctx.session.root,"identity.enc"))=="primary")
                val recovery=File(ctx.session.root,"recovery.arachnode.enc")
                AttachmentRepository.fileOperations.withLock {
                    SecureFiles.input(recovery).use {BackupContainer.readBackup(it)}
                    SecureFiles.input(recovery).use {EncryptedBackup.write(it,output,backupPassword)}
                    failures=0;retryAt=0
                }
            } finally {ctx?.let {SecureFiles.revoke(it.session)};match?.second?.fill(0);password.fill('\u0000');backupPassword.fill('\u0000')}
        }
    }
    suspend fun lock()=withContext(Dispatchers.IO+NonCancellable) {
        operations.withLock {
            backgroundJob?.cancel();backgroundJob=null
            val session=current.value ?: return@withLock
            current.value=null;savedUi=null;SecureFiles.activeProfile=null
            base.stopService(android.content.Intent(base,com.r0ybt.arachn0de.metro.MetroTrackingService::class.java))
            base.getSystemService(android.app.NotificationManager::class.java)?.let { it.cancel(843);it.cancel(844) }
            base.revokeUriPermission(android.net.Uri.parse("content://${base.packageName}.reports"),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching {
                val clipboard=base.getSystemService(android.content.ClipboardManager::class.java)
                if(clipboard?.primaryClipDescription?.label?.toString()=="Contexto de Arachn0de") {
                    if(android.os.Build.VERSION.SDK_INT>=28) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(android.content.ClipData.newPlainText("",""))
                }
            }
            session.scope.coroutineContext[Job]?.cancelAndJoin()
            AttachmentRepository.fileOperations.withLock { session.close() }
            com.r0ybt.arachn0de.metro.MetroNavigation.requests.value=null
        }
    }
    suspend fun changePassword(old:CharArray,new:CharArray)=withContext(Dispatchers.IO) {
        operations.withLock {
            val session=requireNotNull(current.value);val id=session.access.id
            var key:ByteArray?=null
            try {
                require(new.size>=8)
                key=VaultCrypto.unwrap(id,old,envelope(id))
                for(other in ids().filter { it!=id }) {
                    val duplicate=runCatching { VaultCrypto.unwrap(other,new,envelope(other)) }.getOrNull()
                    if(duplicate!=null) { duplicate.fill(0);error("La contraseña debe ser diferente de la del otro almacén.") }
                }
                DurableVaultFile.replace(keyFile(id),VaultCrypto.wrap(id,new,key),sync)
            } finally { key?.fill(0);old.fill('\u0000');new.fill('\u0000') }
        }
    }
    suspend fun createSecondary(primaryPassword:CharArray,password:CharArray)=withContext(Dispatchers.IO) {
        operations.withLock {
            val main=requireNotNull(current.value);require(main.primary && ids().size==1 && password.size>=8)
            var primaryKey:ByteArray?=null;var key:ByteArray?=null;var ctx:VaultContext?=null;var second:VaultSession?=null
            try {
                primaryKey=VaultCrypto.unwrap(main.access.id,primaryPassword,envelope(main.access.id))
                val same=runCatching { VaultCrypto.unwrap(main.access.id,password,envelope(main.access.id)) }.getOrNull()
                if(same!=null) { same.fill(0);error("Las contraseñas deben ser distintas.") }
                val id=UUID.randomUUID();key=VaultCrypto.randomKey();ctx=context(id,key)
                check(ctx.session.root.isDirectory || ctx.session.root.mkdirs())
                DurableVaultFile.replace(keyFile(id),VaultCrypto.wrap(id,password,key),sync)
                SecureFiles.write(File(ctx.session.root,"identity.enc"),"secondary".toByteArray())
                second=open(ctx,key)
                durableDatabase(ctx)
                sync(ctx.session.root);sync(stores)
                DurableVaultFile.replace(index,JSONArray(ids().map { it.toString() }+id.toString()).toString().toByteArray(),sync)
            } finally {
                second?.close() ?: ctx?.let { SecureFiles.revoke(it.session) }
                primaryKey?.fill(0);key?.fill(0);primaryPassword.fill('\u0000');password.fill('\u0000')
            }
        }
    }
    private fun durableDatabase(ctx:VaultContext) {
        val database=ctx.getDatabasePath("arachn0de.db")
        for(file in listOf(database,File(database.path+"-wal"))) {
            if(file.exists()) FileOutputStream(file,true).use { it.fd.sync() }
        }
        sync(database.parentFile!!)
    }
    private fun sourceRoots():Map<String,File> = mapOf("files" to base.filesDir,"cache" to base.cacheDir,
        "databases" to base.getDatabasePath("arachn0de.db").parentFile!!,"preferences" to File(base.applicationInfo.dataDir,"shared_prefs"))
    private fun archiveRoot(ctx:VaultContext,area:String)=File(ctx.session.root,"migration-original/$area").apply { check(mkdirs() || isDirectory) }
    private fun cleanupOriginals(ctx:VaultContext) {
        val manifest=File(ctx.session.root,"source-cleanup.enc")
        if(!manifest.exists()) return
        val json=JSONObject(SecureFiles.text(manifest))
        for((area,source) in sourceRoots()) {
            val rows=json.getJSONArray(area)
            val entries=(0 until rows.length()).map { n -> rows.getJSONObject(n).let { VaultFileMigration.Entry(it.getString("path"),it.getLong("size"),it.getString("sha256")) } }
            VaultFileMigration.removeVerifiedSources(source,archiveRoot(ctx,area),entries)
            if(source.isDirectory) sync(source)
        }
        check(manifest.delete());sync(ctx.session.root)
    }
    suspend fun setup(password:CharArray)=withContext(Dispatchers.IO) {
        operations.withLock {
            require(!configured && current.value==null && password.size>=8)
            var key:ByteArray?=null;var ctx:VaultContext?=null;var original:Arachn0deDatabase?=null
            try {
                DurableVaultFile.recover(pending,sync)
                val id=if(pending.exists()) UUID.fromString(pending.readText()) else UUID.randomUUID().also {
                    DurableVaultFile.replace(pending,it.toString().toByteArray(),sync)
                }
                check(root(id).isDirectory || root(id).mkdirs())
                DurableVaultFile.recover(keyFile(id),sync)
                key=if(keyFile(id).exists()) VaultCrypto.unwrap(id,password,envelope(id)) else VaultCrypto.randomKey().also {
                    DurableVaultFile.replace(keyFile(id),VaultCrypto.wrap(id,password,it),sync)
                }
                ctx=context(id,key)
                SecureFiles.write(File(ctx.session.root,"identity.enc"),"primary".toByteArray())
                original=Arachn0deDatabase.create(base)
                val source=BackupRepository(original,base,BackupAvatarFiles(base,sync))
                source.recover()
                // Capture every XML namespace into the table before the consistent logical backup.
                val preferences=File(base.applicationInfo.dataDir,"shared_prefs").listFiles().orEmpty().filter { it.extension=="xml" }.map { it.nameWithoutExtension }
                for(name in preferences) {
                    require(name.matches(Regex("[a-zA-Z0-9_-]+")))
                    original.privatePreferenceDao().save(PrivatePreferenceEntity(name,EncryptedPreferences.encode(base.getSharedPreferences(name,Context.MODE_PRIVATE).all)))
                }
                val recovery=File(ctx.session.root,"recovery.arachnode.enc")
                SecureFiles.output(recovery).use { source.writeSnapshot(it) }
                val inspect=File(ctx.session.root,"migration-inspect-${UUID.randomUUID()}").apply { check(mkdirs() || isDirectory) }
                val data=SecureFiles.input(recovery).use { BackupContainer.readBackup(it,inspect) }
                // Read and restore the actual archive, not just its checksum.
                val probeContext=VaultContext(base,ctx.session,"migration-probe")
                val probe=Room.inMemoryDatabaseBuilder(probeContext,Arachn0deDatabase::class.java).build()
                try {
                    probeContext.database=probe
                    BackupRepository(probe,probeContext,BackupAvatarFiles(probeContext,sync)).restore(data)
                    check(probe.backupDao().projects().size==data.projects.size && probe.backupDao().nodes().size==data.nodes.size)
                    check(probe.backupDao().persons().size==data.persons.size && probe.technologyDao().catalog().size==data.technologies.size)
                    probe.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) }
                } finally { probe.close();probeContext.clearMemory() }
                original.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { check(it.moveToFirst() && it.getInt(0)==0) }
                original.close();original=null
                val files=VaultFileMigration.inventory(base.filesDir,setOf("security-v1"))
                val caches=VaultFileMigration.inventory(base.cacheDir,setOf("updates"))
                val operationContext=currentCoroutineContext()
                val storeContext=requireNotNull(ctx)
                val master=requireNotNull(key)
                AttachmentRepository.fileOperations.withLock {
                    VaultFileMigration.copy(base.filesDir,ctx.filesDir,files,checkCancelled={ operationContext.ensureActive() },resumeUnactivated=true,syncDirectory=sync)
                    VaultFileMigration.copy(base.cacheDir,ctx.cacheDir,caches,checkCancelled={ operationContext.ensureActive() },resumeUnactivated=true,syncDirectory=sync)
                    val destination=ctx.getDatabasePath("arachn0de.db")
                    // This UUID is in the pending journal, never in the active index.
                    listOf(destination,File(destination.path+"-wal"),File(destination.path+"-shm")).forEach { check(!it.exists() || it.delete()) }
                    val databasePassword=databaseKey(master,id)
                    try { SqlCipherMigration.copy(base.getDatabasePath("arachn0de.db"),destination,databasePassword) }
                    finally { databasePassword.fill(0) }
                    VaultFileMigration.verify(base.filesDir,ctx.filesDir,files);VaultFileMigration.verify(base.cacheDir,ctx.cacheDir,caches)
                    // Keep encrypted originals independent of live files until cleanup completes.
                    val inventory=JSONObject()
                    for((area,sourceRoot) in sourceRoots()) {
                        val rows=when(area) { "files"->files;"cache"->caches;else->VaultFileMigration.inventory(sourceRoot) }
                        val archive=archiveRoot(storeContext,area)
                        VaultFileMigration.copy(sourceRoot,archive,rows,checkCancelled={ operationContext.ensureActive() },resumeUnactivated=true,syncDirectory=sync)
                        inventory.put(area,JSONArray(rows.map { row -> JSONObject().put("path",row.path).put("size",row.size).put("sha256",row.sha256) }))
                    }
                    SecureFiles.write(File(storeContext.session.root,"source-cleanup.enc"),inventory.toString().toByteArray())
                    sync(File(storeContext.session.root,"migration-original"))
                    sync(storeContext.session.root);sync(stores)
                    val session=open(storeContext,master)
                    // The active index is the final durable decision. Originals remain recoverable.
                    try {
                        durableDatabase(storeContext)
                        DurableVaultFile.replace(index,JSONArray(listOf(id.toString())).toString().toByteArray(),sync)
                        cleanupOriginals(storeContext)
                    } catch(failure:Throwable) { session.close();throw failure }
                    SecureFiles.activeProfile=id;current.value=session
                }
                // The store is already committed; journal cleanup can safely retry on restart.
                runCatching { pending.delete();sync(home) }
            } catch(failure:Throwable) {
                original?.close()
                if(current.value==null) ctx?.let { it.clearMemory();SecureFiles.revoke(it.session) }
                throw failure
            } finally { key?.fill(0);password.fill('\u0000') }
        }
    }
}
