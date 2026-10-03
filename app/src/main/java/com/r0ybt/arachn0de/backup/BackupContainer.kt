package com.r0ybt.arachn0de.backup

import java.io.*
import java.security.MessageDigest

/** Envelope v1: magic, format, encoding, payload size, SHA-256(header + payload), payload. */
internal object BackupContainer {
    private val magic = "ARACHNODE\n".toByteArray(Charsets.US_ASCII)
    private const val FORMAT = 1
    private const val PLAIN_JSON = 0
    private fun header(size: Int) = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out -> out.write(magic); out.writeInt(FORMAT); out.writeInt(PLAIN_JSON); out.writeLong(size.toLong()) }
    }.toByteArray()

    fun write(payload: ByteArray, output: OutputStream) {
        require(payload.size in 1..BackupLimits.PAYLOAD_BYTES)
        val head = header(payload.size)
        val hash = MessageDigest.getInstance("SHA-256").apply { update(head); update(payload) }.digest()
        output.write(head); output.write(hash); output.write(payload)
    }

    fun read(input: InputStream): ByteArray {
        val stream = DataInputStream(input)
        val signature = ByteArray(magic.size).also(stream::readFully)
        require(signature.contentEquals(magic)) { "No es un backup de Arachn0de." }
        require(stream.readInt() == FORMAT) { "Versión de backup no compatible." }
        require(stream.readInt() == PLAIN_JSON) { "Codificación de backup no compatible." }
        val length = stream.readLong()
        require(length in 1..BackupLimits.PAYLOAD_BYTES.toLong()) { "Tamaño de backup no válido (máximo 16 MiB)." }
        val expected = ByteArray(32).also(stream::readFully)
        val payload = ByteArray(length.toInt()).also(stream::readFully)
        require(stream.read() == -1) { "Contenido adicional en el backup." }
        val actual = MessageDigest.getInstance("SHA-256").apply { update(header(payload.size)); update(payload) }.digest()
        require(MessageDigest.isEqual(expected, actual)) { "Backup dañado o incompleto." }
        return payload
    }
}
