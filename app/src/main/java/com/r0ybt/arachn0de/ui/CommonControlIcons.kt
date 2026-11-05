package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.R

/** Action descriptions only; never inferred from Check/Close glyphs used as indicators. */
internal fun commonControlIconResource(description:String?):Int? = when {
    description==null || description.startsWith("Cerrar menú") -> null
    description=="Confirmar" || description=="Guardar elemento" || description=="Guardando" -> R.drawable.common_control_confirmar
    description=="Atrás" || description=="← Atrás" || description.startsWith("Volver") -> R.drawable.common_control_atras
    description=="Cancelar" || description.startsWith("Cancelar ") || description=="Cerrar" || description.startsWith("Cerrar ") -> R.drawable.common_control_cancelar_cerrar
    description=="Expandir" || description.startsWith("Expandir ") || description.startsWith("Desplegar ") -> R.drawable.common_control_expandir
    description=="Plegar" || description.startsWith("Plegar ") -> R.drawable.common_control_plegar
    else -> null
}
