package com.r0ybt.arachn0de.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.util.*

@RunWith(RobolectricTestRunner::class) @Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class NodeHistoryUiTest {
    @get:Rule val compose=createComposeRule()
    private fun node(money: Boolean=false) = Node("n","p",null,"Item","",true,0,1,1,false,obligation=if (money) Obligation(15000,"CLP") else null)
    private val events=listOf(NodeEvent("last","n",NodeEventType.COMPLETED,3000),NodeEvent("reopen","n",NodeEventType.REOPENED,2000),NodeEvent("first","n",NodeEventType.COMPLETED,1000),NodeEvent("created","n",NodeEventType.CREATED,0))
    @Test fun taskShowsRepeatedTransitionsNewestFirstWithoutEditingActions() {
        compose.setContent { Arachn0deTheme { NodeHistoryContent(node(),events,onDismiss={}) } }
        compose.onAllNodesWithText("Completada").assertCountEquals(2); compose.onNodeWithText("Reabierta").assertExists(); compose.onNodeWithText("Creada").assertExists()
        assertTrue(compose.onNodeWithTag("node-event:last").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithTag("node-event:reopen").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("Eliminar evento").assertDoesNotExist(); compose.onNodeWithText("Editar fecha de completado").assertDoesNotExist()
    }
    @Test fun obligationShowsPaidAndReopened() {
        compose.setContent { Arachn0deTheme { NodeHistoryContent(node(true),events,onDismiss={}) } }
        compose.onAllNodesWithText("Pagada").assertCountEquals(2); compose.onNodeWithText("Reabierta").assertExists(); compose.onNodeWithText("Completada").assertDoesNotExist()
    }
    @Test fun emptyHistoryAndLoadingAreDistinct() {
        compose.setContent { Arachn0deTheme { NodeHistoryContent(node(),emptyList(),onDismiss={}) } }
        compose.onNodeWithText("Aún no hay eventos registrados.").assertExists(); compose.onNodeWithText("Cargando historial…").assertDoesNotExist()
    }
    @Test fun timestampIsFormattedInSelectedLocalZoneAndLocale() {
        val locale=Locale.forLanguageTag("es-CL"); val zone=TimeZone.getTimeZone("America/Santiago")
        val event=events.first().copy(occurredAt=1791564060123L)
        val expected=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,locale).apply { timeZone=zone }.format(Date(event.occurredAt))
        compose.setContent { Arachn0deTheme { NodeHistoryContent(node(),listOf(event),onDismiss={},locale=locale,zone=zone) } }
        compose.onNodeWithText(expected).assertExists()
    }
    @Test fun loadFailureOffersRetry() {
        var retried=false
        compose.setContent { Arachn0deTheme { NodeHistoryContent(node(),null,failed=true,onRetry={ retried=true },onDismiss={}) } }
        compose.onNodeWithText("Reintentar").performClick(); assertTrue(retried); compose.onNodeWithText("Aún no hay eventos registrados.").assertDoesNotExist()
    }
    @Test fun menuOpensPersistedHistoryAndFlowUpdatesAfterAnotherViewCompletes() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Arachn0deApplication>()
        try {
            val project=app.projectRepository.createProject("History project")
            val task=app.nodeRepository.createNode(project.id,null,"History task")
            compose.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository) } } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("History project").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("History project").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Más opciones").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Más opciones").performClick()
            compose.onNodeWithText("Historial").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Creada").fetchSemanticsNodes().isNotEmpty() }
            app.nodeRepository.setCompleted(task.id,true)
            compose.waitUntil(10000) { compose.onAllNodesWithText("Completada",substring=false).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Cerrar").performClick(); compose.onNodeWithTag("node-history-list").assertDoesNotExist()
        } finally { app.database.close() }
    }
}
