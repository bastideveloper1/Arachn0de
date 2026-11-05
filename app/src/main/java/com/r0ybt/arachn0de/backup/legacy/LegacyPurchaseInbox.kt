package com.r0ybt.arachn0de.backup.legacy

import android.content.Context
import com.r0ybt.arachn0de.security.DurableVaultFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Recovery and read-only decoding of an old encrypted inbox. Never creates a key or input. */
internal class LegacyPurchaseInbox(private val file:File,private val key:()->SecretKey,private val sync:(File)->Unit) {
    constructor(context:Context):this(File(context.noBackupFilesDir,"purchase-input/inbox"),::deviceKey,::syncDirectory)
    fun read():PurchaseState = synchronized(operations) {
        if(!file.parentFile!!.exists()) return@synchronized PurchaseState()
        DurableVaultFile.recover(file,sync)
        if(!file.exists()) return@synchronized PurchaseState()
        require(file.length() in 29..300000)
        val bytes=file.readBytes()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));updateAAD(AAD)}
        val plain=cipher.doFinal(bytes,12,bytes.size-12)
        try {PurchaseCodec.decode(String(plain,Charsets.UTF_8))} finally {plain.fill(0)}
    }
    companion object {
        val operations=Any()
        private val AAD="arachnode-purchase-input-v1".toByteArray()
        private fun deviceKey():SecretKey {
            val alias="arachnode-purchase-input-v1"
            val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            return requireNotNull(store.getKey(alias,null) as? SecretKey) {"No se puede recuperar el archivo histórico de compras."}
        }
        private fun syncDirectory(dir:File) {val fd=android.system.Os.open(dir.path,android.system.OsConstants.O_RDONLY,0);try {android.system.Os.fsync(fd)} finally {android.system.Os.close(fd)}}
    }
}
