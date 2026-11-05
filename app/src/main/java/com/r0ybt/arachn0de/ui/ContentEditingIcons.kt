package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.R

/** Card editing controls only: never used for preview indicators or generic menu icons. */
internal fun contentEditingIconResource(label:String):Int? = when(label) {
    "Configurar estados" -> R.drawable.editing_configuracion_estados
    "Descripción" -> R.drawable.editing_descripcion
    "Editar" -> R.drawable.editing_editar
    "Esfuerzo" -> R.drawable.editing_esfuerzo
    "Marcar favorito" -> R.drawable.editing_favorito_off
    "Quitar de Favoritos" -> R.drawable.editing_favorito_on
    "Fotografía" -> R.drawable.editing_fotografia
    "Planificación" -> R.drawable.editing_planificacion
    "Común" -> R.drawable.editing_prioridad_off
    "Prioritario" -> R.drawable.editing_prioridad_on
    "Sin recurrencia" -> R.drawable.editing_recurrencia_off
    "Responsables" -> R.drawable.editing_responsables
    "Título" -> R.drawable.editing_titulo
    "Valores iniciales" -> R.drawable.editing_valores_iniciales
    else -> null
}
