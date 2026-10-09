package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.security.SecureFiles
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.*
import java.io.File

/** SAF is an external provider: partial writes are invalid and deletion is best effort. */
internal class BackupDocuments(private val resolver: ContentResolver) {
    suspend fun save(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "URI de documento inválida." }
        try {
            // Verify our private artifact before handing any bytes to the selected provider.
            SecureFiles.input(file).use { input ->
                if(SecureFiles.protected(file)) {
                    val prefix=ByteArray(8);java.io.DataInputStream(input).readFully(prefix)
                    require(com.r0ybt.arachn0de.security.EncryptedBackup.isEncrypted(prefix)) { "El backup de distribución debe estar cifrado." }
                    val buffer=ByteArray(32768);while(input.read(buffer)>=0) {} // Authenticates the complete private file.
                } else BackupContainer.readBackup(input)
            }
            SecureFiles.input(file).use { input ->
                requireNotNull(resolver.openOutputStream(uri, "wt")) { "No se pudo abrir el destino." }.use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
            throw failure
        }
    }
    suspend fun read(repository: BackupRepository, uri: Uri,password:CharArray?=null): BackupData = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "URI de documento inválida." }
        repository.inspect(requireNotNull(resolver.openInputStream(uri)) { "No se pudo abrir el archivo." },password)
    }
}
