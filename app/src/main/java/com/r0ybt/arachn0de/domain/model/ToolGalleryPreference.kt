package com.r0ybt.arachn0de.domain.model

/** Optional booleans in the existing encrypted preference namespace; absent keys mean grid. */
internal object ToolGalleryPreference {
    const val NAME="visual_gallery_preferences"
    fun validate(values:Map<String,Any>) {
        listOf("wireframe","diagrams").forEach {require(it !in values || values[it] is Boolean) {"Preferencia de galería inválida"}}
    }
}
