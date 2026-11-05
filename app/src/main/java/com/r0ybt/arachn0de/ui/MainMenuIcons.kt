package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.R

/** Scoped to AppIdentityDrawer, independent of bottom bars and destination screens. */
internal fun mainMenuIconResource(label:String):Int? = when(label) {
    "Acerca de" -> R.drawable.menu_acerca_de
    "Ajustes" -> R.drawable.menu_ajustes
    "Antisocial" -> R.drawable.menu_antisocial
    "Apariencia" -> R.drawable.menu_apariencia
    "Borradores" -> R.drawable.menu_borradores
    "Calendario" -> R.drawable.menu_calendario
    "Cerrar menú" -> R.drawable.menu_cerrar_menu
    "Diagramas" -> R.drawable.menu_diagramas
    "Estadísticas de Metro" -> R.drawable.menu_estadisticas_metro
    "Etiquetas" -> R.drawable.menu_etiquetas
    "Herramientas" -> R.drawable.menu_herramientas
    "Inicio" -> R.drawable.menu_inicio
    "Metro" -> R.drawable.menu_metro
    "Núcleos" -> R.drawable.menu_nucleos
    "Organizar" -> R.drawable.menu_organizar
    "Pagos" -> R.drawable.menu_pagos
    "Pomodoro" -> R.drawable.menu_pomodoro
    "Recurrencias" -> R.drawable.menu_recurrencias
    "Seguridad y respaldos" -> R.drawable.menu_seguridad_respaldos
    "Tragaperras" -> R.drawable.menu_tragaperras
    "Wireframe" -> R.drawable.menu_wireframe
    else -> null
}
