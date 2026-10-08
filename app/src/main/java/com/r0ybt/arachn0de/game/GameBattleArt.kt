package com.r0ybt.arachn0de.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext

/** The supplied battle PNGs are opaque RGB. Mask only near-black pixels connected to
 * the exterior while rendering; retain the original files and every foreground RGB value. */
@Composable
internal fun battlePainter(resource: Int): BitmapPainter {
    val resources = LocalContext.current.resources
    return remember(resources, resource) {
        val original = BitmapFactory.decodeResource(resources, resource)
        val width = original.width
        val height = original.height
        val pixels = IntArray(width * height)
        original.getPixels(pixels, 0, width, 0, 0, width, height)
        maskExteriorBlack(pixels, width, height)
        val masked = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        BitmapPainter(masked.asImageBitmap())
    }
}

/** Mutates alpha only, retaining dark interior details surrounded by the character. */
internal fun maskExteriorBlack(pixels: IntArray, width: Int, height: Int) {
    require(width > 0 && height > 0 && pixels.size == width * height)
    val queued = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    var head = 0
    var tail = 0
    fun add(index: Int) {
        if (queued[index]) return
        val pixel = pixels[index]
        if ((pixel ushr 16 and 255) > 12 || (pixel ushr 8 and 255) > 12 || (pixel and 255) > 12) return
        queued[index] = true
        queue[tail++] = index
    }
    for (x in 0 until width) { add(x); add((height - 1) * width + x) }
    for (y in 0 until height) { add(y * width); add(y * width + width - 1) }
    while (head < tail) {
        val index = queue[head++]
        pixels[index] = pixels[index] and 0x00FFFFFF
        if (index % width > 0) add(index - 1)
        if (index % width < width - 1) add(index + 1)
        if (index >= width) add(index - width)
        if (index < pixels.size - width) add(index + width)
    }
}
