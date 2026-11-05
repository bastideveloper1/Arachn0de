package com.r0ybt.arachn0de.status

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.ManagedKanbanDialog
import com.r0ybt.arachn0de.data.repository.ManagementMethodology
import com.r0ybt.arachn0de.ui.ProjectNodeScreen
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class ContextCalendarIntegrationTest {
    @Test fun individualCardFiltersAreAvailableInSortPanelAndPreserveCardsAndIndicators() {
        val now=System.currentTimeMillis()
        val p=runBlocking {app.projectRepository.createProject("Filtros individuales")}
        val target=runBlocking {app.nodeRepository.createNode(p.id,null,"Urgente vencida",dueAt=now-1,priority=Priority.HIGH)}
        val other=runBlocking {app.nodeRepository.createNode(p.id,null,"Vencida común",dueAt=now-1)}
        runBlocking {app.nodeRepository.createNode(p.id,null,"Urgente futura",dueAt=now+48*60*60*1000,priority=Priority.HIGH)}
        val access=com.r0ybt.arachn0de.access.AccessRepository(app.database)
        runBlocking {access.toggle("node:${target.id}",true)}
        val before=runBlocking {app.database.backupDao().nodes()}
        val favorites=runBlocking {access.snapshot()}
        val store=com.r0ybt.arachn0de.ui.ContextCalendarStore()
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(com.r0ybt.arachn0de.ui.LocalElementAccess provides access,
                com.r0ybt.arachn0de.ui.LocalElementAccessState provides favorites) {
                ProjectNodeScreen(p,app.nodeRepository,{},clock={now},calendarStore=store)
            }
        }}
        await {compose.onAllNodesWithContentDescription("Ordenar").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Ordenar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        await {compose.onAllNodesWithTag("sort-filter-toggle").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sort-filter-toggle").performClick()
        for((label,choice) in listOf("Favoritos" to "Solo favoritos","Urgentes" to "Solo urgentes pendientes","Vencimiento" to "Vencidas")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText(choice).performScrollTo().performClick()
        }
        compose.onNodeWithTag("sort-apply").performClick()
        await {compose.onAllNodesWithTag("node-title:${target.id}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("node-title:${other.id}",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("card-signal:node:${target.id}:favorite",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("card-signal:node:${target.id}:high",useUnmergedTree=true).assert(hasStateDescription("Configurado"))
        compose.onNodeWithTag("context-calendar-attention").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-favorites").assertDoesNotExist()
        compose.onNodeWithContentDescription("Ordenar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        await {compose.onAllNodesWithTag("sort-filter-toggle").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sort-filter-toggle").performClick()
        compose.onNodeWithText("Solo favoritos").assertExists()
        compose.onNodeWithText("Solo urgentes pendientes").assertExists()
        compose.onNodeWithText("Vencidas").assertExists()
        compose.onNodeWithTag("sort-filter-clear").performClick()
        await {compose.onAllNodesWithTag("node-title:${other.id}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame();block()
    }
    private fun assertCalendarResults(count:Int) {
        await {compose.onAllNodesWithTag("context-calendar-period").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("context-calendar-period").performClick()
        await {compose.onAllNodesWithText("$count resultados",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("context-calendar-period-close").performClick()
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()}
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin()}
        val database=app.database
        runBlocking {
            app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        database.close()
    }
    @Test fun emptyDescriptionsUseNoCardSpaceAndRemainEditableThroughExistingContentActions() {
        val p=runBlocking {app.projectRepository.createProject("Descripción de Núcleo")}
        val layer=runBlocking {app.nodeRepository.createNode(p.id,null,"Capa sin descripción",purpose=NodePurpose.LAYER)}
        val task=runBlocking {app.nodeRepository.createNode(p.id,layer.id,"Tarea con descripción","Texto original conservado")}
        var target by mutableStateOf<String?>(null)
        compose.setContent {Arachn0deTheme {key(target) {ProjectNodeScreen(p,app.nodeRepository,{},openNodeId=target)}}}
        for(id in listOf(null,layer.id,task.id)) {
            compose.runOnIdle {target=id}
            await {compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("detail-scope-title"))
            compose.onAllNodesWithText("Añadir descripción").assertCountEquals(0)
            if(id==task.id) {
                compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("detail-description"))
                compose.onNodeWithText("Texto original conservado").assertIsDisplayed()
            } else compose.onNodeWithTag("detail-description").assertDoesNotExist()
            compose.onNodeWithTag("core-context:Content").performClick()
            compose.onNode(hasText("Descripción") and hasClickAction()).assertIsDisplayed()
        }
    }

    @Test fun descriptionAndPaymentsHaveIndependentMeasuredHeightsAtNormalAndLargeFont() {
        val p=runBlocking {app.projectRepository.createProject("Descripción y pagos")}
        val descriptions=listOf("Una descripción breve.",(1..6).joinToString("\n") {"Línea $it de una descripción que ocupa su altura real."},"")
        val layers=runBlocking {descriptions.mapIndexed {index,text->
            val layer=app.nodeRepository.createNode(p.id,null,"Capa $index",text,purpose=NodePurpose.LAYER)
            app.nodeRepository.createNode(p.id,layer.id,"Pago $index",obligation=Obligation(1234567890,"CLP"))
            layer
        }}
        var target by mutableStateOf(layers.first().id)
        var scale by mutableStateOf(1f)
        compose.setContent {Arachn0deTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,scale)) {
                key(target,scale) {ProjectNodeScreen(p,app.nodeRepository,{},openNodeId=target)}
            }
        }}
        for(font in listOf(1f,1.5f)) for((index,layer) in layers.withIndex()) {
            compose.runOnIdle {target=layer.id;scale=font}
            await {compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("detail-payments"))
            compose.onNodeWithTag("detail-payments").assertIsDisplayed()
            if(descriptions[index].isEmpty()) compose.onNodeWithTag("detail-description").assertDoesNotExist()
            else {
                val description=compose.onNodeWithTag("detail-description").fetchSemanticsNode().boundsInRoot
                val payments=compose.onNodeWithTag("detail-payments").fetchSemanticsNode().boundsInRoot
                assertTrue("Description must end before payments at font $font",description.bottom<=payments.top-1)
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithText(descriptions[index],useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
                assertFalse(layouts.single().hasVisualOverflow)
            }
        }
    }

    @Test fun rootAndNestedLayerHeadersCountActualTaskStatesWithinTheirOwnScope() {
        val initial=runBlocking {app.projectRepository.createProject("Estados del ámbito")}
        val project=runBlocking {
            app.projectRepository.setVerificationEnabled(initial.id,true)
            app.projectRepository.getProject(initial.id)!!
        }
        val layer=runBlocking {app.nodeRepository.createNode(project.id,null,"Capa",purpose=NodePurpose.LAYER)}
        val nested=runBlocking {app.nodeRepository.createNode(project.id,layer.id,"Anidada",purpose=NodePurpose.LAYER)}
        runBlocking {
            app.nodeRepository.createNode(project.id,null,"Pendiente del Núcleo")
            app.nodeRepository.createNode(project.id,layer.id,"Pendiente de Capa")
            val verification=app.nodeRepository.createNode(project.id,nested.id,"Por verificar")
            app.nodeRepository.setTaskStatus(verification.id,TaskStatus.VERIFY)
            val done=app.nodeRepository.createNode(project.id,nested.id,"Completada")
            app.nodeRepository.setTaskStatus(done.id,TaskStatus.COMPLETED)
            app.nodeRepository.createNode(project.id,nested.id,"Nota",purpose=NodePurpose.NOTE)
        }
        var target by mutableStateOf<String?>(null)
        compose.setContent {Arachn0deTheme {key(target) {ProjectNodeScreen(project,app.nodeRepository,{},openNodeId=target)}}}
        for((id,expected) in listOf(null to (2 to 1),layer.id to (1 to 1),nested.id to (0 to 1))) {
            compose.runOnIdle {target=id}
            await {compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty()}
            val owner=id?.let {"node:$it"} ?: "project:${project.id}"
            val pending="detail-task-state:$owner:PENDING"
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag(pending))
            compose.onNodeWithTag(pending).assert(hasStateDescription("${expected.first} tareas pendientes")).assertHasNoClickAction()
            compose.onNodeWithTag("detail-task-state:$owner:VERIFY").assert(hasStateDescription("${expected.second} tareas por verificar")).assertHasNoClickAction()
        }
    }

    @Test @Config(qualifiers="w411dp-h1600dp") fun birthdaysLeadLayerAndDashboardResultsAndKeepExistingOrder() {
        val today=CalendarDates.localDay(System.currentTimeMillis(),java.util.TimeZone.getDefault())
        val p=runBlocking {app.projectRepository.createProject("Cumpleaños")}
        val layer=runBlocking {app.nodeRepository.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)}
        val task=runBlocking {app.nodeRepository.createNode(p.id,layer.id,"Tarea del cumpleaños",
            dueAt=TemporalRanges.day(today,java.util.TimeZone.getDefault()).startInclusive+12*60*60*1000)}
        val second=runBlocking {app.nodeRepository.createNode(p.id,layer.id,"Segunda tarea",
            dueAt=task.dueAt!!+60*60*1000)}
        val tomorrow=TemporalRanges.shift(today,1)
        val nextTask=runBlocking {app.nodeRepository.createNode(p.id,layer.id,"Sin cumpleaños",dueAt=TemporalRanges.day(tomorrow,java.util.TimeZone.getDefault()).startInclusive+12*60*60*1000)}
        runBlocking {
            app.personRepository.save("birthday-layer","Persona cumpleaños",null,birthday="1990-%02d-%02d".format(today.month,today.day))
            app.personRepository.save("birthday-first","Ana cumpleaños",null,birthday="1991-%02d-%02d".format(today.month,today.day))
        }
        val before=runBlocking {app.database.backupDao().nodes()}
        val store=com.r0ybt.arachn0de.ui.ContextCalendarStore()
        val tree=runBlocking {app.nodeRepository.observeAllState().first()}
        var dashboard by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            if(dashboard) com.r0ybt.arachn0de.ui.ProjectDashboardScreen(app.projectRepository,listOf(p),emptyMap(),exportTree=tree,copyDescendantsReady=true,calendarStore=store)
            else ProjectNodeScreen(p,app.nodeRepository,{},openNodeId=layer.id,calendarStore=store)
        }}
        await {compose.onAllNodesWithTag("context-calendar-today").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("context-calendar-day:${today.key}").performClick()
        fun assertOrder() {
            await {compose.onAllNodesWithTag("calendar-birthday:birthday-first").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("node-title:${second.id}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            val first=compose.onNodeWithTag("calendar-birthday:birthday-first").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val birthday=compose.onNodeWithTag("calendar-birthday:birthday-layer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val one=compose.onNodeWithTag("node-title:${task.id}",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val two=compose.onNodeWithTag("node-title:${second.id}",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(first.bottom<=birthday.top);assertTrue(birthday.bottom<=one.top);assertTrue(one.top<two.top)
        }
        assertOrder()
        compose.runOnIdle {store.scope("dashboard",today).selected=today;dashboard=true}
        assertOrder()
        compose.runOnIdle {store.scope("dashboard",today).selected=tomorrow}
        await {compose.onAllNodesWithTag("node-title:${nextTask.id}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("calendar-birthday:birthday-first").assertDoesNotExist()
        compose.onNodeWithTag("calendar-birthday:birthday-layer").assertDoesNotExist()
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
        compose.runOnIdle {store.scope("dashboard",today).selected=today}
        assertOrder()
        compose.onNodeWithTag("calendar-birthday:birthday-layer").performClick()
        await {compose.onAllNodes(hasText("Antisocial · Persona cumpleaños") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun dashboardCalendarFindsRealTasksAndReturnsFromDeepTaskWithoutWrites() {
        val zone=java.util.TimeZone.getDefault()
        val today=CalendarDates.localDay(System.currentTimeMillis(),zone)
        val due=TemporalRanges.day(today,zone).startInclusive+12*60*60*1000
        val p=runBlocking {app.projectRepository.createProject("Ciberseguridad")}
        val q=runBlocking {app.projectRepository.createProject("Compras")}
        val layer=runBlocking {app.nodeRepository.createNode(p.id,null,"Estudios",purpose=NodePurpose.LAYER)}
        val deep=runBlocking {app.nodeRepository.createNode(p.id,layer.id,"SQL",purpose=NodePurpose.LAYER)}
        val target=runBlocking {app.nodeRepository.createNode(p.id,deep.id,"Ejercicios SQL",dueAt=due)}
        runBlocking {
            app.nodeRepository.createNode(p.id,null,"Preparar clase",dueAt=due)
            app.nodeRepository.createNode(q.id,null,"Comprar batería",dueAt=due)
        }
        val before=runBlocking {app.database.backupDao().nodes()}
        val initial=runBlocking {app.nodeRepository.observeAllState().first()}
        val access=com.r0ybt.arachn0de.access.AccessRepository(app.database)
        runBlocking {access.toggle("node:${target.id}")}
        val accessBefore=runBlocking {access.snapshot()}
        val calendarStore=com.r0ybt.arachn0de.ui.ContextCalendarStore()
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(com.r0ybt.arachn0de.ui.LocalElementAccess provides access,com.r0ybt.arachn0de.ui.LocalElementAccessState provides accessBefore) {
                val tree by remember {app.nodeRepository.observeAllState()}.collectAsState(initial=initial)
                com.r0ybt.arachn0de.ui.ProjectDashboardScreen(app.projectRepository,listOf(p,q),emptyMap(),exportTree=tree,copyDescendantsReady=true,calendarStore=calendarStore)
            }
        }}
        await {compose.onAllNodesWithTag("context-calendar-day:${today.key}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("context-calendar-day:${today.key}").performClick()
        assertCalendarResults(3)
        compose.runOnIdle {calendarStore.scope("dashboard",today).favorites=true}
        assertCalendarResults(1)
        compose.runOnIdle {calendarStore.scope("dashboard",today).favorites=false}
        compose.onNodeWithTag("context-calendar-results").performScrollToNode(hasText(target.title))
        compose.onNodeWithTag("context-calendar-location:${target.id}",useUnmergedTree=true).assertTextEquals("Ciberseguridad / Estudios / SQL")
        compose.onNodeWithTag("node-options:${target.id}",useUnmergedTree=true).assertHasClickAction()
        compose.onNodeWithTag("favorite:node:${target.id}",useUnmergedTree=true).assertHasClickAction()
        compose.onNodeWithTag("node-title:${target.id}",useUnmergedTree=true).performTouchInput {click(center)}
        await {compose.onAllNodesWithTag("detail-scope-title",useUnmergedTree=true).fetchSemanticsNodes().any {it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {t->t.text=="Ejercicios SQL"}}}
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await {compose.onAllNodesWithTag("context-calendar-results").fetchSemanticsNodes().isNotEmpty()}
        assertCalendarResults(3)
        // The same existing editor remains reachable through a result card's options.
        compose.onNodeWithTag("context-calendar-results").performScrollToNode(hasText(target.title))
        compose.onNodeWithTag("node-options:${target.id}",useUnmergedTree=true).performClick()
        compose.onNode(hasText("Contenido") and hasClickAction()).performClick()
        await {runCatching {compose.onNode(hasText("Título") and hasClickAction()).assertIsEnabled()}.isSuccess}
        compose.onNode(hasText("Título") and hasClickAction()).performClick()
        await {compose.onAllNodesWithTag("core-property-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Cancelar") and hasAnyAncestor(hasTestTag("core-property-editor"))).performClick()
        await {compose.onAllNodesWithTag("core-property-editor").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("core-context:Content").performClick().assertIsNotSelected()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await {compose.onAllNodesWithTag("context-calendar-results").fetchSemanticsNodes().isNotEmpty()}
        val week=TemporalRanges.weekStart(today,java.util.Calendar.MONDAY)
        val emptyDay=TemporalRanges.shift(week,if(week==today) 1 else 0)
        compose.onNodeWithTag("context-calendar-day:${emptyDay.key}").performClick()
        await {compose.onAllNodesWithTag("context-calendar-empty").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-clear").performClick()
        await {compose.onAllNodesWithTag("projects-list").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(accessBefore.favorites,runBlocking {access.snapshot()}.favorites)
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
    }

    @Test fun coreCalendarUsesSubtreeAndBackRestoresDateFilter() {
        val zone=java.util.TimeZone.getDefault();val today=CalendarDates.localDay(System.currentTimeMillis(),zone)
        val due=TemporalRanges.day(today,zone).startInclusive+12*60*60*1000
        val p=runBlocking {app.projectRepository.createProject("Núcleo calendario")}
        val q=runBlocking {app.projectRepository.createProject("Ajeno")}
        val layer=runBlocking {app.nodeRepository.createNode(p.id,null,"Capa profunda",purpose=NodePurpose.LAYER)}
        val target=runBlocking {app.nodeRepository.createNode(p.id,layer.id,"Tarea del subárbol",dueAt=due)}
        runBlocking {app.nodeRepository.createNode(q.id,null,"Tarea ajena",dueAt=due)}
        val before=runBlocking {app.database.backupDao().nodes()}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(p,app.nodeRepository,{})}}
        await {compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("context-calendar"))
        compose.onNodeWithTag("context-calendar-day:${today.key}").performClick()
        assertCalendarResults(1)
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(target.title))
        compose.onNodeWithText("Tarea ajena").assertDoesNotExist()
        compose.onNodeWithTag("node-title:${target.id}",useUnmergedTree=true).performTouchInput {click(center)}
        await {compose.onAllNodesWithTag("detail-scope-title",useUnmergedTree=true).fetchSemanticsNodes().any {it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {t->t.text=="Tarea del subárbol"}}}
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await {compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("context-calendar"))
        assertCalendarResults(1)
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-clear").performClick()
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
    }
    @Test @Config(qualifiers="w320dp-h891dp")
    fun calendarPrecedesScopeTitleAtEveryLayerAndScrollsWithContent() {
        val p=runBlocking {app.projectRepository.createProject("Orden del núcleo")}
        val first=runBlocking {app.nodeRepository.createNode(p.id,null,"Primera capa",purpose=NodePurpose.LAYER)}
        val second=runBlocking {app.nodeRepository.createNode(p.id,first.id,"Segunda capa",purpose=NodePurpose.LAYER)}
        val third=runBlocking {app.nodeRepository.createNode(p.id,second.id,"Tercera capa",purpose=NodePurpose.LAYER)}
        runBlocking {
            // Enough content to scroll the first item fully away, even with a short header.
            for(parent in listOf(null,first.id,second.id)) repeat(6) {index->
                app.nodeRepository.createNode(p.id,parent,"Contenido $parent $index",purpose=NodePurpose.NOTE)
            }
        }
        compose.setContent {Arachn0deTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.5f)) {
                ProjectNodeScreen(p,app.nodeRepository,{})
            }
        }}
        for((title,next) in listOf(p.name to first,first.title to second,second.title to third,third.title to null)) {
            await {compose.onAllNodesWithTag("detail-scope-title",useUnmergedTree=true).fetchSemanticsNodes().any {node->
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text==title}
            }}
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("context-calendar"))
            compose.onNodeWithTag("context-calendar-fold").performClick()
            compose.onAllNodesWithTag("context-calendar").assertCountEquals(1)
            compose.onNodeWithTag("context-calendar-attention").assertDoesNotExist()
            compose.onNodeWithTag("context-calendar-favorites").assertDoesNotExist()
            val calendar=compose.onNodeWithTag("context-calendar").fetchSemanticsNode().boundsInRoot
            val heading=compose.onNodeWithTag("detail-scope-title",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue("Calendar must precede the title in $title",calendar.bottom<=heading.top)
            if(next!=null) {
                compose.onNodeWithTag("nodes-list").performScrollToIndex(1)
                // The calendar is a normal list item, not a pinned heading.
                compose.onNodeWithTag("context-calendar").assertIsNotDisplayed()
                compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(next.title))
                compose.onNodeWithTag("node-title:${next.id}",useUnmergedTree=true).performTouchInput {click(center)}
            }
        }
    }
}
