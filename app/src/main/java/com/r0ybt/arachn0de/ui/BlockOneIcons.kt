package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.R

/** Only consumed by bottom navigation controls; never by general screen icons. */
internal fun blockOneIconResource(label:String?):Int? = when(label) {
    "Bloquear" -> R.drawable.custom_bloquear
    "Buscar" -> R.drawable.custom_buscar
    "Contenido" -> R.drawable.custom_contenido
    "Crear" -> R.drawable.custom_crear
    "Crear borrador" -> R.drawable.custom_crear_borrador
    "Crear núcleo", "Crear Núcleo" -> R.drawable.custom_crear_nucleo
    "Desde plantilla" -> R.drawable.custom_desde_plantilla
    "Nuevo elemento" -> R.drawable.custom_nuevo_elemento
    "Opciones" -> R.drawable.custom_opciones
    "Ordenar" -> R.drawable.custom_ordenar
    "Filtrar" -> R.drawable.custom_filtrar
    "Menú", "Abrir menú" -> R.drawable.custom_menu_a_hamburguesa
    "Cerrar menú" -> R.drawable.custom_cerrar_menu
    else -> null
}
