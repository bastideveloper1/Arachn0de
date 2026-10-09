package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.security.SecureFiles
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
            SecureFiles.input(file).use { input ->
                copyBackupAttachment(row, input, output)
                require(input.read() == -1) { "Contenido adicional en adjunto." }
            }
        }
        require(data.imageContents.keys == data.imageFiles.mapTo(hashSetOf()) { it.key }) { "Faltan imágenes del manifiesto." }
        data.imageFiles.forEach { row ->
            val file = data.imageContents.getValue(row.key); verifyImage(row, file)
            SecureFiles.input(file).use { input -> copyImage(row, input, output); require(input.read() == -1) }
        }
    }

    /** destination == null verifies a generated archive by streaming into a sink. */
    suspend fun readBackup(input: InputStream, destination: File? = null, acceptMetadata:(BackupData)->Unit={}): BackupData {
        val stream = DataInputStream(input)
        val (format, payload) = readPayload(stream, true)
        val data = BackupJson.decode(payload)
        acceptMetadata(data)
        require(format == 2 || data.attachmentFiles.isEmpty()) { "El contenedor antiguo no admite adjuntos." }
        require(format == 2 || data.imageFiles.isEmpty()) { "El contenedor antiguo no admite imágenes por streaming." }
        val files = linkedMapOf<String, File>()
        val images = linkedMapOf<String, File>()
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
                    SecureFiles.output(target).use { output -> copyBackupAttachment(row, stream, output); output.flush() }
                    verifyBackupAttachment(row, target)
                }
            }
            data.imageFiles.forEach { row ->
                if (destination == null) {
                    val bytes = ByteArrayOutputStream(row.byteSize.toInt())
                    copyImage(row, stream, bytes); validateAvatar(bytes.toByteArray())
                } else {
                    val target = File(destination, "images/${row.directory}/${row.name}")
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                    check(target.createNewFile()); images[row.key] = target
                    SecureFiles.output(target).use { output -> copyImage(row, stream, output); output.flush() }
                    verifyImage(row, target)
                }
            }
            if (format == 2) require(stream.read() == -1) { "Contenido adicional en el backup." }
            return data.copy(attachmentContents = files, inspectionDirectory = destination, imageContents = images)
        } catch (failure: Throwable) {
            files.values.forEach { it.delete() }; images.values.forEach { it.delete() }
            throw failure
        }
    }
}
