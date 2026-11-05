package com.r0ybt.arachn0de.annotations

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlin.math.min

internal fun decodeAnnotationSource(bytes: ByteArray, fullSize:Boolean=false): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if(fullSize) require(bounds.outWidth.toLong()*bounds.outHeight in 1..12_000_000L) {"La imagen supera el presupuesto de edición de 12 megapíxeles. La versión anterior se conserva."}
    var sample = 1
    while (!fullSize && (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048)) sample *= 2
    val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }))
    val orientation = bytes.inputStream().use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
    }
    val matrix = Matrix().apply {
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
    if (matrix.isIdentity) return source
    return try { Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true) }
    finally { source.recycle() }
}

internal data class AnnotationViewport(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun point(x: Float, y: Float) = MarkPoint(((x-left)/width).coerceIn(0f,1f), ((y-top)/height).coerceIn(0f,1f))
    fun contains(x: Float, y: Float) = x in left..left+width && y in top..top+height
}

internal fun annotationViewport(w: Float, h: Float, imageW: Int, imageH: Int, zoom: Float,
    panX: Float, panY: Float): AnnotationViewport {
    val v=com.r0ybt.arachn0de.visual.canvasViewport(w,h,imageW,imageH,zoom,androidx.compose.ui.geometry.Offset(panX,panY))
    return AnnotationViewport(v.left,v.top,v.width,v.height)
}
