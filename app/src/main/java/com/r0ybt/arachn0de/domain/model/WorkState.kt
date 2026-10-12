package com.r0ybt.arachn0de.domain.model

/** Direct or explicitly linked ACTION tasks of a Sprint LAYER have one active work state. */
enum class WorkState(val label: String, val completed: Boolean) {
    UNPLANNED("No planificada", false), PLANNED("Planificada", false),
    DOING("Haciendo", false), DONE("Terminada", true), VALIDATED("Validada", true);
    fun next(): WorkState = entries.getOrElse(ordinal + 1) { this }
    companion object { fun fromCompletion(completed: Boolean) = if (completed) DONE else UNPLANNED }
}
