package com.r0ybt.arachn0de.annotations

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** SAF owns these outputs; only documents created by this invocation can be rolled back. */
internal suspend fun exportImageAnnotations(context: Context, tree: Uri, image: Bitmap,
    marks: List<ImageMark>, name: String, observations: Boolean): List<Uri> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val created = mutableListOf<Uri>()
    suspend fun write(name: String, body: (java.io.OutputStream) -> Unit) {
        currentCoroutineContext().ensureActive()
        val uri = requireNotNull(DocumentsContract.createDocument(resolver, parent, "image/png", name))
        created += uri
        requireNotNull(resolver.openOutputStream(uri, "wt")).use(body)
    }
    try {
        write("imagen-anotada.png") { it.write(markedPng(image, marks)) }
        if (observations) {
            val document = ObservationDocument(name, marks.filter { it.tool == MarkTool.MARKER }.map { Observation(text=it.text) })
            val pages = observationPages(document)
            pages.forEachIndexed { i, page ->
                write("imagen-observaciones-${i+1}.png") { observationPng(document, page, i, pages.size, it) }
            }
        }
        created.toList()
    } catch (failure: Exception) {
        val remaining = created.filter { runCatching { DocumentsContract.deleteDocument(resolver, it) }.getOrDefault(false).not() }
        if (remaining.isNotEmpty()) throw IllegalStateException("Exportación incompleta. Revisa la carpeta elegida: quedaron archivos parciales que el proveedor no permitió retirar.", failure)
        throw failure
    }
}
