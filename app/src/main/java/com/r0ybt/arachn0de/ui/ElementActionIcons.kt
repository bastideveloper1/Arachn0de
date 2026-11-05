package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.R

/** Exact action labels only; no global replacement of Delete, PushPin or Description. */
internal fun elementActionIconResource(label:String):Int? = when(label) {
    "Desfijar" -> R.drawable.element_action_desfijar
    "Eliminar" -> R.drawable.element_action_eliminar
    "Fijar" -> R.drawable.element_action_fijar
    "Guardar como plantilla" -> R.drawable.element_action_guardar_plantilla
    "Historial" -> R.drawable.element_action_historial
    else -> null
}
