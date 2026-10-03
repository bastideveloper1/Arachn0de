package com.r0ybt.arachn0de.report

import com.r0ybt.arachn0de.domain.model.*
import java.util.*

internal object ReportFixture {
    val zone = TimeZone.getTimeZone("America/Santiago")
    val locale = Locale.forLanguageTag("es-CL")
    val projects = listOf(Project("p", "Compras", "", 1, 1))
    val roy = Person("r", "Roy")
    val scarlett = Person("s", "Scarlett")
    val now = instant(10, 3)
    fun instant(month: Int, day: Int = 15) = GregorianCalendar(zone).apply {
        clear(); set(2026, month - 1, day, 12, 0)
    }.timeInMillis
    fun node(id: String, code: String = "CLP", due: Long? = instant(10), completed: Boolean = false,
        title: String = id) = Node(id, "p", null, title, "", completed, 0, 1, 1, false,
        dueAt = due, obligation = Obligation(if (code == "CLP") 50000 else 1050, code))
    val nodes = listOf(node("oct", title = "Cuota notebook"), node("paid", completed = true, due = instant(10, 20)),
        node("usd", "USD", due = null), node("sep", due = instant(9)), node("nov", due = instant(11)))
    fun snapshot(period: FinancialPeriod = FinancialPeriod.ALL, person: String? = null,
        source: List<Node> = nodes, assignments: Map<String, List<Person>> = mapOf("oct" to listOf(roy, scarlett))) =
        FinancialSnapshot(NodeTreeSnapshot(source), assignments, FinancialSelection(period, person), CalendarMonth(2026, 10), zone)
    fun data(snapshot: FinancialSnapshot = snapshot()) =
        ObligationReportData.from(snapshot, projects, roy.name, now, locale)
}
