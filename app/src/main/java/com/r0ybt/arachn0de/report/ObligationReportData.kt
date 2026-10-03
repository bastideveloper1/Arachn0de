package com.r0ybt.arachn0de.report

import com.r0ybt.arachn0de.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

data class ReportCurrency(val code: String, val total: String, val pending: String, val completed: String)
data class ReportObligation(val title: String, val amount: String, val due: String, val project: String,
    val status: String, val responsibleNames: String?)

/** Frozen presentation data: no repository, mutable entity list, or financial calculation. */
class ObligationReportData private constructor(
    val period: String,
    val person: String?,
    val currencies: List<ReportCurrency>,
    val obligations: List<ReportObligation>,
    val counts: String,
    val generatedAt: Long,
    val generatedLabel: String,
    val zoneId: String,
) {
    companion object {
        fun from(snapshot: FinancialSnapshot, projects: List<Project>, personName: String?,
            generatedAt: Long, locale: Locale): ObligationReportData {
            require(snapshot.tasks.isNotEmpty()) { "No hay obligaciones para generar este informe." }
            if (snapshot.tasks.size > 1000) throw ReportTooLargeException()
            val zone = TimeZone.getTimeZone(snapshot.zoneId)
            val dueFormat = SimpleDateFormat("d MMM yyyy", locale).apply { timeZone = zone }
            val generatedFormat = SimpleDateFormat("d MMM yyyy · HH:mm", locale).apply { timeZone = zone }
            val period = snapshot.periodMonth?.let {
                SimpleDateFormat("MMMM yyyy", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date(CalendarDates.labelInstant(it.selecting(1))))
            } ?: "Todo el período"
            val projectNames = projects.associate { it.id to it.name }
            val summary = snapshot.summary
            val currencies = summary.byCurrency.map { (code, totals) ->
                ReportCurrency(code, Money.format(totals.totalMinor, code, locale),
                    Money.format(totals.pendingMinor, code, locale), Money.format(totals.completedMinor, code, locale))
            }
            // Preserve the projection's selection and order without filtering or summing again.
            val obligations = snapshot.tasks.map { node ->
                ReportObligation(node.title, Money.format(checkNotNull(node.obligation), locale),
                    node.dueAt?.let { "Vence ${dueFormat.format(Date(it))}" } ?: "Sin vencimiento",
                    projectNames[node.projectId] ?: "Proyecto eliminado",
                    if (node.isCompleted) "Completada" else "Pendiente",
                    snapshot.responsibleByNode[node.id].orEmpty().takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ") { it.name })
            }
            return ObligationReportData(period,
                if (snapshot.selection.personId == null) null else personName ?: "Persona eliminada",
                Collections.unmodifiableList(currencies), Collections.unmodifiableList(obligations),
                "${summary.count} ${if (summary.count == 1) "obligación" else "obligaciones"} · " +
                    "${summary.pendingCount} ${if (summary.pendingCount == 1) "pendiente" else "pendientes"} · " +
                    "${summary.completedCount} ${if (summary.completedCount == 1) "completada" else "completadas"}",
                generatedAt, generatedFormat.format(Date(generatedAt)), snapshot.zoneId)
        }
    }
}
