package com.r0ybt.arachn0de.security

import java.io.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Platform-independent v1 crypto. No Android keystore, persistent plaintext or global provider. */
internal object VaultCrypto {
    const val KEY_BYTES = 32
    const val CHUNK_BYTES = 64 * 1024
    const val ARGON_MEMORY_KIB = 64 * 1024
    const val ARGON_PASSES = 3
    private val random = SecureRandom()
    private val wrapMagic = "ANKEY001".toByteArray(StandardCharsets.US_ASCII)
    internal val streamMagic = "ANENC001".toByteArray(StandardCharsets.US_ASCII)
    fun randomKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    /** Every invocation has independent salt and nonce, including password changes. */
    fun wrap(id: UUID, password: CharArray, dataKey: ByteArray): ByteArray {
        require(dataKey.size == KEY_BYTES)
        val salt = randomKey()
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                write(wrapMagic); writeLong(id.mostSignificantBits); writeLong(id.leastSignificantBits)
                writeInt(ARGON_MEMORY_KIB); writeInt(ARGON_PASSES); writeInt(1); write(salt); write(nonce)
            }
        }.toByteArray()
        val kek = derive(password, salt)
        return try { header + gcm(Cipher.ENCRYPT_MODE, kek, nonce, header, dataKey) }
        finally { kek.fill(0) }
    }

    fun unwrap(id: UUID, password: CharArray, envelope: ByteArray): ByteArray {
        try {
            require(envelope.size == 128)
            val input = DataInputStream(ByteArrayInputStream(envelope))
            require(ByteArray(8).also(input::readFully).contentEquals(wrapMagic))
            require(UUID(input.readLong(), input.readLong()) == id)
            // Whitelist rather than letting untrusted metadata allocate arbitrary KDF work/memory.
            require(input.readInt() == ARGON_MEMORY_KIB && input.readInt() == ARGON_PASSES && input.readInt() == 1)
            val salt = ByteArray(32).also(input::readFully)
            val nonce = ByteArray(12).also(input::readFully)
            val kek = derive(password, salt)
            return try { gcm(Cipher.DECRYPT_MODE, kek, nonce, envelope.copyOfRange(0, 80), envelope.copyOfRange(80,128)) }
            finally { kek.fill(0) }
        } catch (failure: Exception) { throw VaultAuthenticationException(failure) }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password))
        val bytes = ByteArray(encoded.remaining()).also(encoded::get)
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(ARGON_MEMORY_KIB)
            .withIterations(ARGON_PASSES).withParallelism(1).withSalt(salt).build()
        try {
            return ByteArray(KEY_BYTES).also { result ->
                Argon2BytesGenerator().apply { init(parameters); generateBytes(bytes, result) }
            }
        } finally {
            bytes.fill(0); parameters.clear()
            if (encoded.hasArray()) encoded.array().fill(0)
        }
    }

    /** HKDF-SHA256 (RFC 5869), one 32-byte output block; purpose separates DB/files/backup. */
    fun subkey(master: ByteArray, salt: ByteArray, purpose: String): ByteArray {
        require(master.size == KEY_BYTES && salt.size == KEY_BYTES)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val extracted = mac.doFinal(master)
        return try {
            mac.init(SecretKeySpec(extracted, "HmacSHA256"))
            mac.doFinal(purpose.toByteArray(StandardCharsets.UTF_8) + byteArrayOf(1))
        } finally { extracted.fill(0) }
    }

    internal fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, value: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad); doFinal(value)
        }
    internal fun nonce(index: Long): ByteArray = ByteBuffer.allocate(12).putInt(0).putLong(index).array()
    internal fun aad(header: ByteArray, purpose: String, index: Long, size: Int): ByteArray =
        header + purpose.toByteArray(StandardCharsets.UTF_8) + ByteBuffer.allocate(12).putLong(index).putInt(size).array()

    /** Records are authenticated before delivery; final empty record binds end-of-stream. */
    fun encrypt(input: InputStream, output: OutputStream, master: ByteArray, purpose: String) {
        val salt = randomKey()
        val header = streamMagic + salt
        val key = subkey(master, salt, "stream-v1:$purpose")
        val data = DataOutputStream(output)
        val buffer = ByteArray(CHUNK_BYTES)
        try {
            data.write(header)
            var index = 0L
            while (true) {
                var size = 0
                while (size < buffer.size) {
                    val count = input.read(buffer, size, buffer.size-size)
                    if (count < 0) break
                    if (count == 0) continue
                    size += count
                }
                require(index < Long.MAX_VALUE)
                val plain = buffer.copyOf(size)
                val cipher = try { gcm(Cipher.ENCRYPT_MODE,key,nonce(index),aad(header,purpose,index,size),plain) }
                    finally { plain.fill(0) }
                data.writeInt(size); data.write(cipher)
                index++
                if (size == 0) break
            }
            data.flush()
        } finally { key.fill(0); buffer.fill(0) }
    }

    /** Caller must stage/validate the result before committing; this method never swallows tags. */
    fun decrypt(input: InputStream, output: OutputStream, master: ByteArray, purpose: String) {
        var key: ByteArray? = null
        try {
            val data = DataInputStream(input)
            val header = ByteArray(40).also(data::readFully)
            require(header.copyOfRange(0,8).contentEquals(streamMagic))
            key = subkey(master,header.copyOfRange(8,40),"stream-v1:$purpose")
            var index = 0L
            while (true) {
                require(index < Long.MAX_VALUE)
                val size = data.readInt()
                require(size in 0..CHUNK_BYTES)
                val cipher = ByteArray(size+16).also(data::readFully)
                val plain = gcm(Cipher.DECRYPT_MODE,key,nonce(index),aad(header,purpose,index,size),cipher)
                try { if (size > 0) output.write(plain) } finally { plain.fill(0) }
                index++
                if (size == 0) { require(data.read() == -1); break }
            }
        } catch (failure: Exception) { throw VaultAuthenticationException(failure) }
        finally { key?.fill(0) }
    }
}

/** Intentionally identical for wrong credentials, altered metadata and failed authentication. */
internal class VaultAuthenticationException(cause: Throwable? = null) : IOException("No se pudo autenticar el contenido protegido.", cause)
