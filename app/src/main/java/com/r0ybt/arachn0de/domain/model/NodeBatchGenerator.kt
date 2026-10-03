package com.r0ybt.arachn0de.domain.model

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

enum class NumberingMode { NONE, PREFIX, SUFFIX }
enum class BatchTemporalRule { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

data class NodeBatchParameters(
    val baseName: String,
    val quantity: Int,
    val numberingMode: NumberingMode = NumberingMode.NONE,
    val startNumber: Int = 1,
    val purpose: NodePurpose = NodePurpose.ACTION,
    val description: String = "",
    val temporalRule: BatchTemporalRule = BatchTemporalRule.NONE,
    val firstDueAt: Long? = null,
)

data class GeneratedNodeSpec(val title: String, val description: String, val purpose: NodePurpose, val dueAt: Long?)

/** Finite, pure generation. The explicit zone belongs to this calculation, not persisted Nodes. */
object NodeBatchGenerator {
    const val MAX_BATCH_SIZE = 500

    fun generate(parameters: NodeBatchParameters, zone: TimeZone): List<GeneratedNodeSpec> {
        val p = parameters
        require(p.quantity in 1..MAX_BATCH_SIZE) { "La cantidad debe estar entre 1 y $MAX_BATCH_SIZE." }
        val base = p.baseName.trim()
        validateTitle(base)
        if (p.numberingMode != NumberingMode.NONE) {
            require(p.startNumber >= 0) { "El número inicial debe ser un entero de 0 o mayor." }
            require(p.startNumber.toLong() + p.quantity - 1 <= Int.MAX_VALUE) { "La numeración supera el máximo permitido." }
        }
        require(p.purpose != NodePurpose.NOTE || p.temporalRule == BatchTemporalRule.NONE) { "Las notas no permiten generación temporal." }
        val anchor = if (p.temporalRule == BatchTemporalRule.NONE) null else {
            requireNotNull(p.firstDueAt) { "Elige la primera fecha y hora de vencimiento." }
            GregorianCalendar(zone).apply { timeInMillis = p.firstDueAt }.also(::validateCalendarRange)
        }
        return List(p.quantity) { index ->
            val number = p.startNumber.toLong() + index
            val title = when (p.numberingMode) {
                NumberingMode.NONE -> base
                NumberingMode.PREFIX -> "$number $base"
                NumberingMode.SUFFIX -> "$base $number"
            }
            validateTitle(title)
            GeneratedNodeSpec(title, p.description, p.purpose, anchor?.let { dueAt(it, p.temporalRule, index, zone) })
        }
    }

    fun validateSpecs(specs: List<GeneratedNodeSpec>) {
        require(specs.size in 1..MAX_BATCH_SIZE) { "La cantidad debe estar entre 1 y $MAX_BATCH_SIZE." }
        specs.forEach {
            validateTitle(it.title)
            require(it.title == it.title.trim()) { "El título generado contiene espacios en sus extremos." }
            require(it.purpose != NodePurpose.NOTE || it.dueAt == null) { "Las notas no permiten generación temporal." }
        }
    }

    private fun validateTitle(title: String) {
        require(title.isNotBlank()) { "El nombre base es obligatorio." }
        require(TitleLimits.count(title) <= TitleLimits.NODE) { "Un título generado supera los ${TitleLimits.NODE} caracteres. Acorta el nombre o cambia la numeración." }
    }

    private fun validateCalendarRange(calendar: Calendar) {
        require(calendar.get(Calendar.ERA) == GregorianCalendar.AD && calendar.get(Calendar.YEAR) in 1..9999) { "La fecha generada está fuera del rango permitido (años 1–9999)." }
    }

    private fun dueAt(anchor: Calendar, rule: BatchTemporalRule, index: Int, zone: TimeZone): Long {
        if (index == 0) return anchor.timeInMillis
        // Add to the ORIGINAL date every time: Jan 31 -> Feb end -> Mar 31, and leap day returns.
        val target = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(anchor.get(Calendar.YEAR), anchor.get(Calendar.MONTH), anchor.get(Calendar.DAY_OF_MONTH), 12, 0)
            timeInMillis // Resolve the original calendar date before applying the offset.
            when (rule) {
                BatchTemporalRule.DAILY -> add(Calendar.DAY_OF_MONTH, index)
                BatchTemporalRule.WEEKLY -> add(Calendar.DAY_OF_MONTH, index * 7)
                BatchTemporalRule.MONTHLY -> add(Calendar.MONTH, index)
                BatchTemporalRule.YEARLY -> add(Calendar.YEAR, index)
                BatchTemporalRule.NONE -> error("No temporal rule")
            }
        }
        validateCalendarRange(target)
        // Rebuild wall time strictly: reject a DST gap rather than silently changing the hour.
        return try {
            GregorianCalendar(zone).apply {
                clear(); isLenient = false
                set(target.get(Calendar.YEAR), target.get(Calendar.MONTH), target.get(Calendar.DAY_OF_MONTH),
                    anchor.get(Calendar.HOUR_OF_DAY), anchor.get(Calendar.MINUTE), anchor.get(Calendar.SECOND))
                set(Calendar.MILLISECOND, anchor.get(Calendar.MILLISECOND))
            }.timeInMillis
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("La fecha ${index + 1} cae en una hora inexistente por cambio horario. Elige otra hora inicial.")
        }
    }
}
