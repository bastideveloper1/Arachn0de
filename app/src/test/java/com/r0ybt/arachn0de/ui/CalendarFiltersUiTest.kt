package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CalendarFiltersUiTest {
    @get:Rule val compose = createComposeRule()
    private val utc = TimeZone.getTimeZone("UTC")
    private val today = CalendarDay(2026,10,5)
    private val now = CalendarDates.labelInstant(today)
    private val roy = Person("roy", "Roy")
    private val ana = Person("ana", "Ana")
    private val people = listOf(roy,ana)
    private fun task(id: String, day: CalendarDay = today, completed: Boolean = false) =
        Node(id,"p",null,id,"",completed,0,1,1,false,dueAt=CalendarDates.labelInstant(day))
    private val nodes = listOf(task("Roy pendiente").copy(obligation=Obligation(15000,"CLP")), task("Completada",completed=true).copy(obligation=Obligation(1050,"USD")),
        task("Sin responsable"), task("Mañana compartida",TemporalRanges.shift(today,1)), task("Semana siguiente",TemporalRanges.shift(today,7)), task("Mes anterior tarea",CalendarDay(2026,9,5)),
        task("Nota").copy(purpose=NodePurpose.NOTE),task("Sin vencimiento").copy(dueAt=null,startAt=now))
    private val assignments = mapOf("Roy pendiente" to listOf(roy), "Completada" to people, "Mañana compartida" to people, "Semana siguiente" to listOf(roy))
    private val source = CalendarSnapshot(NodeTreeSnapshot(nodes),utc)
    private val projects = listOf(Project("p","Personal","",0,1,1))
    private fun mount(restorer: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = { Arachn0deTheme { AppSafeArea { CalendarScreen(source,projects,assignments,true,now,"UTC",{}, {}, people) } } }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
    }
    private fun awaitTag(tag: String) = compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun mode(name: String) = compose.onNodeWithTag("calendar-view:$name").performClick()
    private fun row(id: String) { compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-task:$id")); awaitTag("calendar-task:$id") }
    private fun filters() = compose.onNodeWithTag("calendar-filters").performClick()
    private fun time(label: String) { compose.onNodeWithTag("calendar-time-filter").performClick(); compose.onNodeWithTag("calendar-time-option:${TimeFilter.entries.first { timeFilterLabel(it) == label }.name}").performClick() }
    private fun person(id: String) { compose.onNodeWithTag("calendar-person-filter").performClick(); compose.onNodeWithTag("calendar-person:$id").performClick() }
    private fun status(label: String) { compose.onNodeWithTag("calendar-status-filter").performClick(); compose.onNodeWithText(label).performClick() }
    private fun done() = compose.onNodeWithText("Listo").performClick()
    @Test fun todayWeekMonthAndPreviousNextCurrentNavigationKeepRealNodeRows() {
        mount(); awaitTag("calendar-month"); mode("DAY"); row("Roy pendiente")
        mode("WEEK"); row("Mañana compartida")
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Semana siguiente").performClick(); row("Semana siguiente")
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Semana anterior").performClick(); row("Roy pendiente")
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-current-period"))
        compose.onNodeWithTag("calendar-current-period").performClick(); row("Mañana compartida")
        mode("MONTH"); compose.onNodeWithTag("calendar-list").performScrollToIndex(0); awaitTag("calendar-month")
        compose.onNodeWithContentDescription("Mes anterior").performClick(); row("Mes anterior tarea")
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0); compose.onNodeWithContentDescription("Mes siguiente").performClick(); row("Roy pendiente")
    }
    @Test fun andFiltersAffectRowsAndMonthCountsAndCanBeCleared() {
        mount(); filters(); person("roy"); status("Pendientes"); done()
        compose.waitUntil(10000) { compose.onNodeWithTag("calendar-day:2026-10-5").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] == "Hoy · 1 tarea" }
        row("Roy pendiente"); compose.onNodeWithTag("calendar-active-filters").assert(hasText("Roy · Pendientes"))
        filters(); time("Hoy"); status("Completados"); done(); row("Completada")
        filters(); time("Mañana"); done()
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        compose.waitUntil(10000) { compose.onAllNodesWithText("No hay elementos que coincidan con los filtros.").fetchSemanticsNodes().isNotEmpty() }
        filters(); compose.onNodeWithTag("calendar-clear-filters").performScrollTo().performClick(); done(); row("Mañana compartida")
        compose.onNodeWithTag("calendar-active-filters").assert(hasText("Mañana"))
    }
    @Test fun restoredWeekSelectionPersonaAndStateSurviveRecomposition() {
        val restoration = StateRestorationTester(compose); mount(restoration)
        mode("WEEK"); filters(); person("roy"); status("Pendientes"); done()
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0); compose.onNodeWithContentDescription("Semana siguiente").performClick()
        row("Semana siguiente"); restoration.emulateSavedInstanceStateRestore(); row("Semana siguiente")
        compose.onNodeWithTag("calendar-view:WEEK").assertIsSelected()
        compose.onNodeWithTag("calendar-active-filters").assert(hasText("Roy · Pendientes"))
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0); compose.onNodeWithContentDescription("Semana anterior").performClick(); row("Roy pendiente")
        filters(); compose.onNodeWithTag("calendar-person-filter").assert(hasText("Persona: Roy")); compose.onNodeWithTag("calendar-status-filter").assert(hasText("Estado: Pendientes"))
    }
    @Test fun extraTimePresetsAllAndEmptyPersonaAreAvailableWithoutAddingNotes() {
        mount(); filters(); time("Todo"); done(); row("Mes anterior tarea"); row("Semana siguiente")
        compose.onNodeWithTag("calendar-view:MONTH").assertIsNotSelected()
        filters(); time("Próximo mes"); done(); compose.onNodeWithTag("calendar-list").performScrollToIndex(2)
        compose.waitUntil(10000) { compose.onAllNodesWithText("Sin tareas para este día").fetchSemanticsNodes().isNotEmpty() }
        filters(); time("Mes anterior"); done(); row("Mes anterior tarea")
        filters(); time("Esta semana"); done(); row("Mañana compartida")
        filters(); time("Próxima semana"); done(); row("Semana siguiente")
    }
    @Test fun todayTracksClockAndZoneWhileKeepingFiltersAndPersistedDates() {
        var instant by mutableStateOf(now)
        var zone by mutableStateOf("UTC")
        compose.setContent { val calendar = remember(zone) { CalendarSnapshot(source.tree,TimeZone.getTimeZone(zone)) }
            Arachn0deTheme { AppSafeArea { CalendarScreen(calendar,projects,assignments,true,instant,zone,{}, {}, people) } } }
        mode("DAY"); filters(); person("roy"); status("Pendientes"); done(); row("Roy pendiente")
        compose.runOnIdle { instant = CalendarDates.labelInstant(TemporalRanges.shift(today,1)) }; row("Mañana compartida")
        compose.runOnIdle { instant = now - 11 * 3600000; zone = "GMT-03:00" }
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        compose.waitUntil(10000) { compose.onAllNodesWithText("No hay elementos que coincidan con los filtros.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(nodes,source.tree.nodes)
        compose.onNodeWithTag("calendar-active-filters").assert(hasText("Roy · Pendientes"))
    }
    @Test fun deletedSelectedPersonRemainsSpecificAndShowsEmptyInsteadOfAll() {
        var persons by mutableStateOf(people)
        var assigned by mutableStateOf(assignments)
        compose.setContent { Arachn0deTheme { AppSafeArea { CalendarScreen(source,projects,assigned,true,now,"UTC",{}, {}, persons) } } }
        mode("DAY"); filters(); person("roy"); done(); row("Roy pendiente")
        compose.runOnIdle { persons = listOf(ana); assigned = assignments.mapValues { (_,list) -> list.filter { it.id != "roy" } } }
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        compose.waitUntil(10000) { compose.onAllNodesWithText("No hay elementos que coincidan con los filtros.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calendar-active-filters").assert(hasText("Persona ausente"))
    }
}
