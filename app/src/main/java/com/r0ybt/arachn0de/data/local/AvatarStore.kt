package com.r0ybt.arachn0de.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/** Durable private files, never Room blobs or remote URLs. Call on an IO dispatcher. */
class AvatarStore(context: Context) {
    private val context = context.applicationContext
    private val directory get() = File(context.filesDir, "avatars").apply { mkdirs() }
    private fun file(name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}\\.png")))
        return File(directory, name)
    }
    fun exists(name: String) = file(name).isFile
    fun read(name: String): Bitmap? = BitmapFactory.decodeFile(file(name).path)
    fun delete(name: String) { file(name).delete() }

    fun import(uri: Uri): String {
        val inputFile = File.createTempFile("import-", ".tmp", directory)
        var outputFile: File? = null
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Image unavailable" }
                inputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= 20L * 1024 * 1024) { "Image too large" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(inputFile.path, bounds)
            require(bounds.outWidth in 1..32000 && bounds.outHeight in 1..32000) { "Invalid image" }
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            var bitmap = requireNotNull(BitmapFactory.decodeFile(inputFile.path, BitmapFactory.Options().apply { inSampleSize = sample }))
            // Camera photos often encode orientation in EXIF rather than in pixel order.
            val orientation = runCatching { android.media.ExifInterface(inputFile.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = android.graphics.Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            if (!matrix.isIdentity) {
                val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (oriented !== bitmap) bitmap.recycle()
                bitmap = oriented
            }
            val name = "${UUID.randomUUID()}.png"
            outputFile = file(name)
            try {
                outputFile.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            return name
        } catch (failure: Exception) {
            outputFile?.delete()
            throw failure
        } finally { inputFile.delete() }
    }
}
