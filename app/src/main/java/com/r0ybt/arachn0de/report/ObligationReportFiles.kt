package com.r0ybt.arachn0de.report

import com.r0ybt.arachn0de.security.SecureFiles
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

/** Private staging, atomic cache publication, and explicit user-directed delivery. */
class ObligationReportFiles(private val context: Context) {
    companion object {
        const val MIME = "image/png"
        const val CACHE_DIRECTORY = "obligation-reports"
        const val RETENTION_MILLIS = 24L * 60 * 60 * 1000
        fun filename(generatedAt: Long, zoneId: String): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss-SSS", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone(zoneId)
            }.format(Date(generatedAt))
            return "arachn0de-obligations-$stamp-${UUID.randomUUID().toString().take(8)}.png"
        }

        internal fun writePng(bitmap: Bitmap, output: OutputStream) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("PNG compression failed")
            output.flush()
        }

        internal fun publish(file: File, write: (OutputStream) -> Unit) {
            val part = File(file.parentFile, "${file.name}.part")
            try {
                SecureFiles.output(part).use(write)
                if (part.length() == 0L || !part.renameTo(file)) throw IOException("PNG publication failed")
            } catch (failure: Throwable) {
                part.delete()
                throw failure
            }
        }
    }

    suspend fun generate(data: ObligationReportData): File = withContext(Dispatchers.IO) {
        val jobContext = currentCoroutineContext()
        var published: File? = null
        try {
            val renderer = ObligationPngRenderer(context.resources)
            val document = renderer.measure(data, checkCancelled = { jobContext.ensureActive() })
            val directory = File(context.cacheDir, CACHE_DIRECTORY)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cache unavailable")
            // Keep recent shared files readable; remove only old reports in this dedicated directory.
            directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > RETENTION_MILLIS }
                ?.forEach { it.delete() }
            val file = File(directory, filename(data.generatedAt, data.zoneId))
            val bitmap = renderer.render(document) { jobContext.ensureActive() }
            try {
                publish(file) { output ->
                    jobContext.ensureActive()
                    writePng(bitmap, output)
                    jobContext.ensureActive()
                }
                published = file
                jobContext.ensureActive()
                file
            } finally { bitmap.recycle() }
        } catch (failure: OutOfMemoryError) {
            published?.delete()
            throw ReportMemoryException()
        } catch (failure: Throwable) {
            published?.delete()
            throw failure
        }
    }

    fun shareIntent(file: File): Intent {
        val checked=checkedFile(file)
        val uri = if(SecureFiles.protected(checked)) {
            SecureFiles.requireActive(checked)
            Uri.Builder().scheme("content").authority("${context.packageName}.reports").appendPath("v1")
                .appendPath(SecureFiles.ownerId(checked).toString()).appendPath(checked.name).build()
        } else FileProvider.getUriForFile(context, "${context.packageName}.reports", checked)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Informe de Obligaciones", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun cachedFile(name: String): File {
        require(name == File(name).name && name.startsWith("arachn0de-obligations-") && name.endsWith(".png"))
        return checkedFile(File(File(context.cacheDir, CACHE_DIRECTORY), name))
    }

    private fun checkedFile(file: File): File {
        require(file.canonicalFile.parentFile == File(context.cacheDir, CACHE_DIRECTORY).canonicalFile)
        if (!file.isFile || file.length() == 0L) throw IOException("Report unavailable")
        return file
    }

    suspend fun save(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Invalid document URI" }
        try {
            SecureFiles.input(checkedFile(file)).use { input ->
                val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Document unavailable")
                output.use {
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        it.write(buffer, 0, read)
                    }
                    it.flush()
                }
            }
        } catch (failure: Throwable) {
            // External providers cannot promise atomic writes; try removing the incomplete document.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw failure
        }
    }
}
