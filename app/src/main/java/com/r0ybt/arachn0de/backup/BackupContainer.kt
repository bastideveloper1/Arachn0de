package com.r0ybt.arachn0de.backup

import java.io.*
import java.security.MessageDigest

/** Envelope v1: magic, format, encoding, payload size, SHA-256(header + payload), payload. */
internal object BackupContainer {
    private val magic = "ARACHNODE\n".toByteArray(Charsets.US_ASCII)
    private const val FORMAT = 1
    private const val PLAIN_JSON = 0
    private fun header(size: Int, format: Int = FORMAT) = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out -> out.write(magic); out.writeInt(format); out.writeInt(PLAIN_JSON); out.writeLong(size.toLong()) }
    }.toByteArray()

    fun write(payload: ByteArray, output: OutputStream) {
        require(payload.size in 1..BackupLimits.PAYLOAD_BYTES)
        val head = header(payload.size)
        val hash = MessageDigest.getInstance("SHA-256").apply { update(head); update(payload) }.digest()
        output.write(head); output.write(hash); output.write(payload)
    }

    fun read(input: InputStream): ByteArray = readPayload(DataInputStream(input), false).second

    private fun readPayload(stream: DataInputStream, allowAttachments: Boolean): Pair<Int, ByteArray> {
        val signature = ByteArray(magic.size).also(stream::readFully)
        require(signature.contentEquals(magic)) { "No es un backup de Arachn0de." }
        val format = stream.readInt()
        require(format == FORMAT || (allowAttachments && format == 2)) { "Versión de backup no compatible." }
        require(stream.readInt() == PLAIN_JSON) { "Codificación de backup no compatible." }
        val length = stream.readLong()
        require(length in 1..BackupLimits.PAYLOAD_BYTES.toLong()) { "Tamaño de backup no válido (máximo 16 MiB)." }
        val expected = ByteArray(32).also(stream::readFully)
        val payload = ByteArray(length.toInt()).also(stream::readFully)
        if (format == FORMAT) require(stream.read() == -1) { "Contenido adicional en el backup." }
        val actual = MessageDigest.getInstance("SHA-256").apply { update(header(payload.size, format)); update(payload) }.digest()
        require(MessageDigest.isEqual(expected, actual)) { "Backup dañado o incompleto." }
        return format to payload
    }
    /** v2 retains the v1 JSON envelope; attachment bytes follow in manifest order, without paths. */
    suspend fun writeBackup(data: BackupData, output: OutputStream) {
        val payload = BackupJson.encode(data)
        val head = header(payload.size, 2)
        val hash = MessageDigest.getInstance("SHA-256").apply { update(head); update(payload) }.digest()
        output.write(head); output.write(hash); output.write(payload)
        require(data.attachmentContents.keys == data.attachmentFiles.map { it.storageName }.toSet()) { "Faltan archivos adjuntos." }
        data.attachmentFiles.forEach { row ->
            val file = data.attachmentContents.getValue(row.storageName)
            verifyBackupAttachment(row, file)
            file.inputStream().use { input ->
                copyBackupAttachment(row, input, output)
                require(input.read() == -1) { "Contenido adicional en adjunto." }
            }
        }
    }

    /** destination == null verifies a generated archive by streaming into a sink. */
    suspend fun readBackup(input: InputStream, destination: File? = null): BackupData {
        val stream = DataInputStream(input)
        val (format, payload) = readPayload(stream, true)
        val data = BackupJson.decode(payload)
        require(format == 2 || data.attachmentFiles.isEmpty()) { "El contenedor antiguo no admite adjuntos." }
        val files = linkedMapOf<String, File>()
        try {
            data.attachmentFiles.forEach { row ->
                if (destination == null) {
                    copyBackupAttachment(row, stream, object : OutputStream() {
                        override fun write(b: Int) {}
                        override fun write(b: ByteArray, off: Int, len: Int) {}
                    })
                } else {
                    val target = File(destination, row.storageName)
                    check(target.createNewFile())
                    files[row.storageName] = target
                    FileOutputStream(target).use { output -> copyBackupAttachment(row, stream, output); output.fd.sync() }
                    verifyBackupAttachment(row, target)
                }
            }
            if (format == 2) require(stream.read() == -1) { "Contenido adicional en el backup." }
            return data.copy(attachmentContents = files, inspectionDirectory = destination)
        } catch (failure: Throwable) {
            files.values.forEach { it.delete() }
            throw failure
        }
    }
}
