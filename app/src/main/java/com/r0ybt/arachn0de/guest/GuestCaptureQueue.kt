package com.r0ybt.arachn0de.guest

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.r0ybt.arachn0de.security.DurableVaultFile
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Temporary, uncommitted inputs. No vault key, password, public media or plaintext files. */
internal class GuestCaptureQueue(private val root:File,private val key:()->javax.crypto.SecretKey,private val sync:(File)->Unit) {
    constructor(context:Context):this(File(context.noBackupFilesDir,"guest-captures"),::deviceKey,::syncDirectory)
    data class Pending(val id:String,val vault:String,val person:String,val accepted:Boolean,val framing:AvatarFraming,val bytes:ByteArray)
    private fun path(id:String):File {require(UUID.fromString(id).toString()==id);return File(root,"$id.capture")}
    fun ids():List<String> = synchronized(operations) {root.listFiles().orEmpty().filter {it.name.endsWith(".capture")}.map {it.name.removeSuffix(".capture")}.sorted()}
    fun prepare(vault:String,person:String):String = synchronized(operations) {
        require(vault.isNotBlank() && person.isNotBlank());require(ids().size<8) {"Importa las capturas pendientes antes de continuar."}
        if(!root.exists()) {check(root.mkdirs());sync(requireNotNull(root.parentFile))}
        val id=UUID.randomUUID().toString();write(Pending(id,vault,person,false,AvatarFraming(),byteArrayOf()));return id
    }
    fun read(id:String):Pending = synchronized(operations) {
        val file=path(id);DurableVaultFile.recover(file,sync)
        require(file.length() in 29..(MAX_BYTES+8192).toLong())
        val sealed=file.readBytes();val nonce=sealed.copyOfRange(0,12)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,nonce));updateAAD(id.toByteArray())}
        val plain=cipher.doFinal(sealed,12,sealed.size-12)
        try {
            val input=java.io.DataInputStream(plain.inputStream());val size=input.readInt();require(size in 1..4096)
            val meta=JSONObject(String(ByteArray(size).also(input::readFully),Charsets.UTF_8))
            require(meta.getInt("version")==1)
            val bytes=input.readBytes();require(bytes.size<=MAX_BYTES)
            val framing=AvatarFraming(meta.getDouble("zoom").toFloat(),meta.getDouble("x").toFloat(),meta.getDouble("y").toFloat()).also {it.validate()}
            return Pending(id,meta.getString("vault"),meta.getString("person"),meta.getBoolean("accepted"),framing,bytes)
        } finally {plain.fill(0)}
    }
    fun accept(id:String,bytes:ByteArray,framing:AvatarFraming):Unit = synchronized(operations) {
        require(bytes.size in 1..MAX_BYTES);framing.validate()
        val previous=read(id);try {check(!previous.accepted);write(previous.copy(accepted=true,framing=framing,bytes=bytes))} finally {previous.bytes.fill(0)}
    }
    private fun write(row:Pending) {
        val meta=JSONObject().put("version",1).put("vault",row.vault).put("person",row.person).put("accepted",row.accepted).put("zoom",row.framing.zoom).put("x",row.framing.x).put("y",row.framing.y).toString().toByteArray()
        val plain=java.io.ByteArrayOutputStream().apply {java.io.DataOutputStream(this).apply {writeInt(meta.size);write(meta);write(row.bytes)}}.toByteArray()
        try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key());updateAAD(row.id.toByteArray())}
            DurableVaultFile.replace(path(row.id),cipher.iv+cipher.doFinal(plain),sync)
        } finally {plain.fill(0)}
    }
    fun requireSettled(vault:String):Unit = synchronized(operations) {
        ids().forEach {id->val row=read(id);try {check(row.vault!=vault || !row.accepted) {"Importa o recupera las capturas de invitado pendientes antes de respaldar o restaurar."}} finally {row.bytes.fill(0)}}
    }
    /** Only after the final DB commit has been verified, or explicit rejection of an unaccepted capture. */
    fun remove(id:String):Unit = synchronized(operations) {val file=path(id);DurableVaultFile.recover(file,sync);check(!file.exists() || file.delete());sync(root)}
    companion object {
        private val operations=Any()
        const val MAX_BYTES=20*1024*1024
        @Synchronized private fun deviceKey():javax.crypto.SecretKey {
            val alias="arachnode-guest-input-v1"
            val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            (store.getKey(alias,null) as? javax.crypto.SecretKey)?.let {return it}
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            }.generateKey()
        }
        private fun syncDirectory(dir:File) {val fd=android.system.Os.open(dir.path,android.system.OsConstants.O_RDONLY,0);try {android.system.Os.fsync(fd)} finally {android.system.Os.close(fd)}}
    }
}
