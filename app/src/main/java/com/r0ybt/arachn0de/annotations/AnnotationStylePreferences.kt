package com.r0ybt.arachn0de.annotations

import android.content.SharedPreferences

internal data class AnnotationStyle(val color:Int=0xffe53935.toInt(),val width:Float=.006f) {
    init {require(color in AnnotationStylePreferences.colors && width in AnnotationStylePreferences.widths)}
}

/** Two optional values in the existing per-vault private-preferences transport. */
internal class AnnotationStylePreferences(private val storage:SharedPreferences) {
    fun read():AnnotationStyle {
        validate(storage.all)
        return AnnotationStyle(storage.getInt("color",0xffe53935.toInt()),storage.getFloat("width",.006f))
    }
    fun save(value:AnnotationStyle) {
        check(storage.edit().putInt("color",value.color).putFloat("width",value.width).commit()) {"No se pudo recordar el color y grosor."}
    }
    companion object {
        const val NAME="image_annotation_style"
        // Also orders a rapid close/reopen after pending style changes.
        val operations=kotlinx.coroutines.sync.Mutex()
        val colors=setOf(-1,0xff000000.toInt(),0xffe53935.toInt(),0xffffc107.toInt(),0xff00c853.toInt(),0xff2196f3.toInt())
        val widths=setOf(.003f,.006f,.014f)
        fun validate(values:Map<String,*>) {
            require(values.keys.all {it in setOf("color","width")}) {"Ajustes de anotación desconocidos."}
            require("color" !in values || values["color"] is Int && values["color"] in colors) {"Color de anotación inválido."}
            require("width" !in values || values["width"] is Float && values["width"] in widths) {"Grosor de anotación inválido."}
        }
    }
}
