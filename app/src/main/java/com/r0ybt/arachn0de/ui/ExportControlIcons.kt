package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.R

/** Scope distinguishes saving an image tree from other descendant operations. */
internal fun exportControlIconResource(label:String,parent:String?=null):Int? = when(label) {
    "Compartir y exportar", "Exportar o compartir" -> R.drawable.export_control_exportar_compartir
    "Copiar elemento" -> R.drawable.export_control_copiar_elemento
    "Copiar con descendientes" -> R.drawable.export_control_con_descendientes
    "Solo elemento" -> R.drawable.export_control_solo_elemento
    "Con descendientes" -> if(parent=="Guardar imagen") R.drawable.export_control_guardar_con_descendientes else R.drawable.export_control_con_descendientes
    "Compartir imagen", "Compartir como imagen" -> R.drawable.export_control_compartir_imagen
    "Guardar imagen" -> R.drawable.export_control_guardar_imagen
    "Guardar con descendientes" -> R.drawable.export_control_guardar_con_descendientes
    "PDF / Word" -> R.drawable.export_control_pdf_word
    "PDF", "Exportar PDF" -> R.drawable.export_control_exportar_pdf
    "Word / DOCX", "Exportar Word/DOCX" -> R.drawable.export_control_exportar_word
    "Exportar Markdown" -> R.drawable.export_control_exportar_markdown
    "Exportar JSON" -> R.drawable.export_control_exportar_json
    else -> null
}
