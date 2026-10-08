package com.r0ybt.arachn0de.domain.model

import kotlin.math.max

/** Translation is a fraction of the available overflow, independent of display size. */
data class AvatarFraming(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun validate() { require(zoom.isFinite() && zoom in 1f..5f && x.isFinite() && x in -1f..1f && y.isFinite() && y in -1f..1f) { "Encuadre de avatar inválido." } }
    fun geometry(width: Float, height: Float, side: Float): AvatarGeometry {
        val scale = max(side / width, side / height) * zoom
        val w = width * scale; val h = height * scale
        return AvatarGeometry(w, h, (side - w) / 2 + x * (w - side) / 2, (side - h) / 2 + y * (h - side) / 2)
    }
    fun transform(width: Float, height: Float, side: Float, panX: Float, panY: Float, factor: Float, centroidX: Float = side / 2, centroidY: Float = side / 2): AvatarFraming {
        val old = geometry(width, height, side)
        val next = copy(zoom = (zoom * factor).coerceIn(1f, 5f))
        val resized = next.geometry(width, height, side)
        val ratio = next.zoom / zoom
        val left = centroidX - (centroidX - old.left) * ratio + panX
        val top = centroidY - (centroidY - old.top) * ratio + panY
        fun position(offset: Float, size: Float) = if (size <= side + .001f) 0f else ((offset + (size - side) / 2) / ((size - side) / 2)).coerceIn(-1f, 1f)
        return next.copy(x = position(left, resized.width), y = position(top, resized.height))
    }
}
data class AvatarGeometry(val width: Float, val height: Float, val left: Float, val top: Float)
