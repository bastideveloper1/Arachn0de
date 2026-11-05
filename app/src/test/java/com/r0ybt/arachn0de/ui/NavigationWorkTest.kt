package com.r0ybt.arachn0de.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.security.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.*

/** Plain Room UI fixture; encryption evidence belongs to the existing security tests. */
class NavigationWorkApplication:Arachn0deApplication() {
    val queries=CopyOnWriteArrayList<String>()
    @Volatile var intercept:((String)->Unit)?=null
    override fun onCreate() {
        super.onCreate()
        val id=UUID.randomUUID()
        val access=SecureFiles.Session(id,File(filesDir,"security-v1/stores/$id").apply {mkdirs()},VaultCrypto.randomKey())
        SecureFiles.register(access)
        val context=VaultContext(this,access)
        val db=Room.inMemoryDatabaseBuilder(this,Arachn0deDatabase::class.java)
            .setQueryCallback({sql,_->queries.add(sql);intercept?.invoke(sql)},Executor {it.run()}).build()
        context.database=db
        runBlocking(Dispatchers.IO) {context.preparePreferences()}
        val session=VaultSession(access,context,db,true)
        runBlocking(Dispatchers.IO) {session.appearance}
        SecureFiles.activeProfile=id;security.current.value=session
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=NavigationWorkApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class NavigationWorkTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Config(qualifiers="w411dp-h1800dp")
    @Test fun innerLayersKeepOnlyLegitimateIndentation() {
        val now=System.currentTimeMillis()
        val core=runBlocking {app.projectRepository.createProject("Inner alignment")}
        val parent=runBlocking {app.nodeRepository.createNode(core.id,null,"Parent",purpose=NodePurpose.LAYER)}
        val ids=runBlocking {
            val first=app.nodeRepository.createNode(core.id,parent.id,"First layer",purpose=NodePurpose.LAYER)
            val second=app.nodeRepository.createNode(core.id,parent.id,"Second layer",purpose=NodePurpose.LAYER)
            val child=app.nodeRepository.createNode(core.id,second.id,"Nested layer",purpose=NodePurpose.LAYER)
            val deep=app.nodeRepository.createNode(core.id,child.id,"Deep layer",purpose=NodePurpose.LAYER)
            app.nodeRepository.createNode(core.id,deep.id,"Only dated task",dueAt=now)
            listOf(first.id,second.id,child.id,deep.id)
        }
        val before=runBlocking {app.database.backupDao().nodes()}
        val store=ContextCalendarStore()
        compose.setContent {Arachn0deTheme {
            ProjectNodeScreen(core,app.nodeRepository,{},openNodeId=parent.id,clock={now},calendarStore=store,
                sharedAssignments=emptyMap(),sharedAssignmentsReady=true)
        }}
        fun card(title:String)=compose.onNode(hasText(title) and hasClickAction())
        fun rect(title:String)=card(title).fetchSemanticsNode().boundsInRoot
        await {tag("nodes-list") && compose.onAllNodesWithTag("node-title:${ids[1]}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val normal=rect("Second layer")
        assertEquals(rect("First layer").left,normal.left,.5f)
        val today=com.r0ybt.arachn0de.domain.model.CalendarDates.localDay(now,java.util.TimeZone.getDefault())
        compose.onNodeWithTag("context-calendar-day:${today.key}").performClick()
        await {compose.onAllNodesWithTag("node-title:${ids[3]}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.mainClock.advanceTimeBy(1500)
        val unit=with(compose.density) {12.dp.toPx()}
        val top=rect("Second layer")
        val nested=rect("Nested layer")
        val deep=rect("Deep layer")
        assertEquals(top.left+unit,nested.left,.5f)
        assertEquals(top.width-unit,nested.width,.5f)
        assertEquals(top.left+2*unit,deep.left,.5f)
        assertEquals(top.right,deep.right,.5f)
        // Folding hides the calendar; it intentionally does not clear the day filter.
        compose.onNodeWithTag("context-calendar-fold").performClick()
        await {tag("context-calendar-collapsed-date")}
        assertEquals(nested.left,rect("Nested layer").left,.5f)
        compose.runOnIdle {assertEquals(today,store.scope("project:${core.id}:${parent.id}",today).selected)}
        compose.onNodeWithTag("context-calendar-fold").performClick()
        await {tag("context-calendar-period")}
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-clear").performClick()
        await {compose.onAllNodesWithTag("node-title:${ids[2]}",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
        compose.mainClock.advanceTimeBy(1500)
        assertEquals(normal.left,rect("Second layer").left,.5f)
        assertEquals(normal.width,rect("Second layer").width,.5f)
        assertEquals(rect("First layer").left,rect("Second layer").left,.5f)
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
    }
    @Config(qualifiers="w411dp-h1800dp")
    @Test fun calendarCycleKeepsLayerAlignment() {
        val now=System.currentTimeMillis()
        val core=runBlocking {app.projectRepository.createProject("Calendar alignment")}
        val layers=runBlocking {
            List(18) {i->
                val layer=app.nodeRepository.createNode(core.id,null,"Layer $i",purpose=NodePurpose.LAYER)
                if(i in 2..4) {
                    val child=app.nodeRepository.createNode(core.id,layer.id,"Sub $i",purpose=NodePurpose.LAYER)
                    app.nodeRepository.createNode(core.id,child.id,"Dated $i",dueAt=now)
                }
                layer
            }
        }
        val store=ContextCalendarStore()
        compose.setContent {Arachn0deTheme {
            ProjectNodeScreen(core,app.nodeRepository,{},clock={now},calendarStore=store,
                sharedAssignments=emptyMap(),sharedAssignmentsReady=true)
        }}
        await {tag("nodes-list")}
        fun bounds(i:Int):androidx.compose.ui.geometry.Rect {
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Layer $i"))
            return compose.onNode(hasText("Layer $i") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        }
        val before=(0..5).associateWith {bounds(it).let {r->r.left to r.width}}
        val today=com.r0ybt.arachn0de.domain.model.CalendarDates.localDay(now,java.util.TimeZone.getDefault())
        repeat(2) {
            compose.onNodeWithTag("nodes-list").performScrollToIndex(0)
            await {tag("context-calendar-day:${today.key}")}
            compose.onNodeWithTag("context-calendar-day:${today.key}").performClick()
            await {tag("nodes-list") && store.scope("project:${core.id}:root",today).selected==today}
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Dated 2"))
            compose.onNodeWithTag("nodes-list").performScrollToIndex(0)
            compose.onNodeWithTag("context-calendar-period").performClick()
            compose.onNodeWithTag("context-calendar-next").performClick()
            compose.onNodeWithTag("context-calendar-clear").performClick()
            await {tag("nodes-list") && store.scope("project:${core.id}:root",today).selected==null}
            compose.mainClock.advanceTimeBy(1500)
            for(i in 0..5) {
                val actual=bounds(i)
                assertEquals("left of layer $i",before.getValue(i).first,actual.left,0.5f)
                assertEquals("width of layer $i",before.getValue(i).second,actual.width,0.5f)
            }
            // Restore the visible month without selecting a day for the next cycle.
            compose.runOnIdle {store.scope("project:${core.id}:root",today).current(today)}
        }
        bounds(17)
        assertEquals(layers.map {it.id},runBlocking {app.nodeRepository.getProjectNodes(core.id)}.filter {it.parentId==null}.map {it.id})
    }
    private val app get()=compose.activity.application as NavigationWorkApplication
    private val releases=mutableListOf<CountDownLatch>()
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame();block()
    }
    private fun tag(tag:String)=compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun showGlobalActions() {
        if(compose.onAllNodesWithContentDescription("Mostrar barra global").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithContentDescription("Mostrar barra global").performClick()
    }
    private fun pauseQuery(matches:(String)->Boolean):Pair<CountDownLatch,CountDownLatch> {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);releases.add(release)
        app.intercept={sql->if(matches(sql)) {entered.countDown();check(release.await(15,TimeUnit.SECONDS))}}
        return entered to release
    }
    @After fun close() {
        app.intercept=null;releases.forEach {it.countDown()}
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        stopAppearanceTestApplication(app)
    }
    @Test fun slowCoreLoadShowsFeedbackAndInactiveManagementDoesNotQuery() {
        val project=runBlocking {app.projectRepository.createProject("Small core")}
        runBlocking {app.nodeRepository.createNode(project.id,null,"Layer",purpose=NodePurpose.LAYER)}
        app.queries.clear()
        val (entered,release)=pauseQuery {it.startsWith("SELECT * FROM nodes WHERE projectId",true)}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            ProjectNodeScreen(project,app.nodeRepository,{},sharedAssignments=emptyMap(),sharedAssignmentsReady=true)
        }}}
        await {entered.count==0L && tag("project-loading")}
        compose.onNodeWithText("Cargando contenido…").assertIsDisplayed()
        app.intercept=null;release.countDown()
        await {tag("core-bottom-module")}
        compose.onNodeWithTag("project-loading").assertDoesNotExist()
        val reads=app.queries.filter {it.startsWith("SELECT",true)}
        assertTrue(reads.none {it.contains("iteration_plans") || it.contains("iteration_cycles") || it.contains("iteration_commitments") || it.contains("iteration_closures")})
        assertTrue(reads.none {it.contains("JOIN node_person",true)})
    }
    @Test fun slowCreateAcknowledgesTapAndDoesNotPrepareTwice() {
        val project=runBlocking {app.projectRepository.createProject("Core")}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{})}}
        await {tag("core-context:Creation")}
        compose.onNodeWithTag("core-context:Creation").performClick()
        val (entered,release)=pauseQuery {it.startsWith("SELECT * FROM creation_defaults WHERE",true)}
        app.queries.clear()
        compose.onNodeWithText("Nuevo").performClick()
        await {entered.count==0L && tag("operation-progress")}
        compose.onNodeWithText("Nuevo").assertIsNotEnabled()
        compose.onNodeWithText("Nuevo").performClick()
        app.intercept=null;release.countDown()
        await {tag("quick-create-title")}
        assertEquals(1,app.queries.count {it.startsWith("SELECT * FROM creation_defaults WHERE",true)})
        compose.onAllNodesWithTag("quick-create-title").assertCountEquals(1)
        assertTrue(runBlocking {app.nodeRepository.getProjectNodes(project.id).isEmpty()})
    }
    @Test fun appRootLoadsIndexOnDemandAndSearchReopensWithCurrentTitles() {
        val project=runBlocking {app.projectRepository.createProject("Search core")}
        val node=runBlocking {app.nodeRepository.createNode(project.id,null,"Original match")}
        app.queries.clear()
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(project))}}
        await {tag("global-bottom-bar")}
        val draftQuery="SELECT * FROM nodes WHERE projectId IS NULL ORDER BY updatedAt DESC, id"
        assertFalse(app.queries.contains(draftQuery))
        showGlobalActions()
        compose.onNodeWithContentDescription("Buscar").performClick()
        await {tag("contextual-search-query")}
        compose.onNodeWithTag("contextual-search-query").performTextReplacement("Original")
        await {tag("access-result:node:${node.id}")}
        assertTrue(app.queries.contains(draftQuery))
        compose.onNodeWithText("Cerrar").performClick()
        await {!tag("contextual-search-query")}
        runBlocking {app.nodeRepository.updateNode(node.id,"Changed match")}
        showGlobalActions()
        compose.onNodeWithContentDescription("Buscar").performClick()
        await {tag("contextual-search-query")}
        compose.onNodeWithTag("contextual-search-query").performTextReplacement("Changed")
        await {tag("access-result:node:${node.id}")}
        compose.onNodeWithText("Changed match").assertIsDisplayed()
        compose.onNodeWithText("Original match").assertDoesNotExist()
    }
    @Test fun calendarModeCannotRaceADurableFoldWrite() {
        val session=app.security.current.value!!
        val storage=session.context.getSharedPreferences(CalendarPresentation.NAME,0)
        val today=com.r0ybt.arachn0de.domain.model.CalendarDay(2026,10,29)
        val state=ContextCalendarStore().apply {bind(CalendarPresentation(storage))}.scope("test",today)
        compose.setContent {Arachn0deTheme {ContextCalendar(state,null,today)}}
        val (entered,release)=pauseQuery {it.startsWith("INSERT OR REPLACE",true) && it.contains("private_preferences")}
        compose.onNodeWithTag("context-calendar-fold").performClick()
        await {entered.count==0L}
        compose.onNodeWithTag("context-calendar-mode").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("context-calendar-fold").assertIsNotEnabled()
        app.intercept=null;release.countDown()
        await {state.mode==0}
        compose.onNodeWithTag("context-calendar-fold").performClick()
        await {state.mode!=0}
        compose.runOnIdle {assertEquals(1,state.expanded);assertEquals(1,state.mode)}
    }
    @Test fun profileBarRetainsItsPhotoPanelWithLargeFont() {
        runBlocking {app.personRepository.save("bar-person","Persona de barras",null)}
        compose.setContent {Arachn0deTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            val state=rememberBottomModulesState()
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.8f),
                LocalBottomModulesState provides state,LocalGlobalNavigation provides GlobalNavigation({},false,true)) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.width(320.dp).fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                        PeopleScreen(app.personRepository,initialPersonId="bar-person") {}
                    }
                    ContextualBottomPanels()
                    GlobalBottomBar()
                }
            }
        }}
        await {tag("profile-photo")}
        compose.onNodeWithTag("global-bottom-bar").assertDoesNotExist()
        compose.onNodeWithTag("profile-photo").performClick()
        await {tag("profile-change-photo")}
        compose.onNodeWithContentDescription("Mostrar barra global").performClick()
        compose.onNodeWithTag("global-bottom-bar").assertIsDisplayed()
        compose.onNodeWithTag("profile-photo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mostrar barra contextual").performClick()
        compose.onNodeWithTag("profile-change-photo").assertExists()
        if(compose.onAllNodesWithTag("profile-information").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("bottom-bar-overflow").performClick()
            compose.onNodeWithText("Información").assertIsDisplayed()
        } else compose.onNodeWithTag("profile-information").assertIsDisplayed()
        assertEquals("Persona de barras",runBlocking {app.database.personDao().get("bar-person")!!.name})
    }

    @Test fun bottomBarSwitchKeepsSearchPanelAndSelectionAcrossNavigation() {
        val core=runBlocking {app.projectRepository.createProject("Núcleo de barras")}
        val node=runBlocking {app.nodeRepository.createNode(core.id,null,"Resultado para alternar")}
        val before=runBlocking {app.database.backupDao().nodes()}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(core))}}
        await {tag("dashboard-create-core")}
        compose.onNodeWithTag("global-bottom-bar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mostrar barra global").performClick()
        compose.onNodeWithTag("dashboard-create-core").assertDoesNotExist()
        showGlobalActions()
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithTag("contextual-search-query").performTextInput("Resultado para alternar")
        await {compose.onAllNodesWithTag("node-title:${node.id}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Mostrar barra contextual").performClick()
        compose.onNodeWithTag("global-bottom-bar").assertDoesNotExist()
        compose.onNodeWithTag("contextual-search-query").assertTextContains("Resultado para alternar")
        compose.onNodeWithTag("dashboard-create-core").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mostrar barra global").performClick()
        compose.onNodeWithTag("contextual-search-query").assertTextContains("Resultado para alternar")
        compose.onNodeWithText("Cerrar").performClick()
        await {compose.onAllNodesWithText(core.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(core.name).performClick();await {tag("nodes-list")}
        compose.onNodeWithTag("global-bottom-bar").assertIsDisplayed()
        compose.onNodeWithTag("core-navigation-actions").assertDoesNotExist()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await {tag("projects-list")}
        compose.onNodeWithTag("global-bottom-bar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mostrar barra contextual").performClick()
        compose.onNodeWithText(core.name).performClick();await {tag("nodes-list")}
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithContentDescription("Mostrar barra global").performClick()
        compose.onNodeWithTag("core-navigation-actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mostrar barra contextual").performClick()
        compose.onNodeWithTag("core-context:Content").assertIsSelected()
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
    }

    @Test fun listCheckOpensPaymentInPlacePrefillsRemainderAndCompletesOnlyAfterAtomicFullPayment() {
        val core=runBlocking {app.projectRepository.createProject("Checks en lista")}
        val plain=runBlocking {app.nodeRepository.createNode(core.id,null,"Sin importe")}
        val target=runBlocking {
            repeat(18) {app.nodeRepository.createNode(core.id,null,"Relleno $it")}
            app.nodeRepository.createNode(core.id,null,"Deuda del check",obligation=com.r0ybt.arachn0de.domain.model.Obligation(100000,"CLP"))
        }
        runBlocking {app.database.nodeDao().setTaskStateCount(plain.id,3,1000);app.database.nodeDao().setTaskStateCount(target.id,3,1000)}
        val repo=com.r0ybt.arachn0de.bank.BankRepository(app.database,nodeRepository=app.nodeRepository)
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(core))}}
        await {compose.onAllNodesWithText(core.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(core.name).performClick();await {tag("nodes-list")}
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(plain.title))
        compose.onNodeWithTag("task-state:${plain.id}").performClick()
        await {runBlocking {app.nodeRepository.getNode(plain.id)!!.awaitingVerification}}
        compose.onNodeWithTag("bank-payment-amount").assertDoesNotExist()
        compose.onNodeWithTag("hierarchy-ancestor:root").assertDoesNotExist()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(target.title))
        fun scroll()=compose.onNodeWithTag("nodes-list").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        val beforeScroll=scroll()
        val before=runBlocking {app.database.backupDao().nodes()}
        compose.onNodeWithTag("task-state:${target.id}").performClick();await {tag("bank-payment-amount")}
        compose.onNodeWithTag("bank-payment-amount").assertTextContains("100000")
        compose.onNodeWithText("Origen: DIRECTO").assertExists()
        compose.onNodeWithText("Pagos pendientes").assertDoesNotExist()
        compose.onNodeWithText("Saldo pendiente: $100.000").assertExists()
        compose.onNodeWithText("Cancelar").performClick();await {!tag("bank-payment-amount")}
        compose.onNodeWithTag("hierarchy-ancestor:root").assertDoesNotExist()
        assertEquals(beforeScroll,scroll(),.0001f)
        assertEquals(before,runBlocking {app.database.backupDao().nodes()});assertTrue(runBlocking {repo.state().roots.isEmpty()})
        compose.onNodeWithTag("task-state:${target.id}").performClick();await {tag("bank-payment-amount")}
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextReplacement("30000")
        compose.onNodeWithText("Confirmar pago").performClick()
        await {compose.onAllNodesWithText("¿Confirmar pago de $30.000?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Confirmar").performClick();await {runBlocking {repo.state().paid(target.id)==30000L}}
        await {!tag("bank-payment-amount")}
        assertFalse(runBlocking {app.nodeRepository.getNode(target.id)!!.isCompleted})
        assertEquals(beforeScroll,scroll(),.0001f)
        compose.onNodeWithTag("hierarchy-ancestor:root").assertDoesNotExist()
        compose.onNodeWithTag("task-state:${target.id}").performClick();await {tag("bank-payment-amount")}
        compose.onNodeWithTag("bank-payment-amount").assertTextContains("70000")
        compose.onNodeWithText("Saldo pendiente: $70.000").assertExists()
        runBlocking {app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_check_completion BEFORE UPDATE OF isCompleted ON nodes WHEN NEW.isCompleted=1 BEGIN SELECT RAISE(ABORT,'fixture completion'); END")}
        compose.onNodeWithText("Confirmar pago").performClick()
        await {compose.onAllNodesWithText("¿Confirmar pago de $70.000?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Confirmar").performClick()
        await {compose.onAllNodesWithText("fixture completion",substring=true).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(30000L,runBlocking {repo.state().paid(target.id)});assertFalse(runBlocking {app.nodeRepository.getNode(target.id)!!.isCompleted})
        compose.onNodeWithText("Aceptar").performClick()
        runBlocking {app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_check_completion")}
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.nodeRepository.getNode(target.id)!!.isCompleted}};await {!tag("bank-payment-amount")}
        assertEquals(100000L,runBlocking {repo.state().paid(target.id)})
        assertEquals(3,runBlocking {app.database.nodeDao().getById(target.id)!!.taskStateCount})
        val paidCount=runBlocking {repo.state().operations.size}
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(target.title))
        compose.onNodeWithTag("task-state:${target.id}").performClick()
        await {runBlocking {!app.nodeRepository.getNode(target.id)!!.isCompleted}}
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(target.title))
        compose.onNodeWithTag("task-state:${target.id}").performClick()
        await {runBlocking {app.nodeRepository.getNode(target.id)!!.isCompleted}}
        compose.onNodeWithTag("bank-payment-amount").assertDoesNotExist()
        assertEquals(paidCount,runBlocking {repo.state().operations.size})
        assertEquals(before.map {it.id to it.position},runBlocking {app.database.backupDao().nodes()}.map {it.id to it.position})
    }

    @Test fun searchResultCheckPreservesQueryAndResultsWithoutEnteringTheCard() {
        val core=runBlocking {app.projectRepository.createProject("Origen de resultados")}
        val debt=runBlocking {app.nodeRepository.createNode(core.id,null,"Coincidencia con deuda",obligation=com.r0ybt.arachn0de.domain.model.Obligation(1000,"CLP"))}
        val plain=runBlocking {app.nodeRepository.createNode(core.id,null,"Coincidencia normal")}
        val hidden=runBlocking {app.nodeRepository.createNode(core.id,null,"Fuera del filtro")}
        val repo=com.r0ybt.arachn0de.bank.BankRepository(app.database,nodeRepository=app.nodeRepository)
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(core))}}
        await {tag("projects-list")}
        showGlobalActions()
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithTag("contextual-search-query").performTextInput("Coincidencia")
        await {compose.onAllNodesWithTag("node-title:${debt.id}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("projects-list").performScrollToNode(hasText(debt.title))
        compose.onNodeWithTag("task-state:${debt.id}").performClick();await {tag("bank-payment-amount")}
        compose.onNodeWithTag("bank-payment-amount").assertTextContains("1000")
        compose.onNodeWithText("Cancelar").performClick();await {!tag("bank-payment-amount")}
        compose.onNodeWithTag("contextual-search-query").assertTextContains("Coincidencia")
        compose.onNodeWithTag("projects-list").assertExists()
        compose.onNodeWithTag("detail-scope-title").assertDoesNotExist()
        assertTrue(runBlocking {repo.state().roots.isEmpty()})
        compose.onNodeWithTag("projects-list").performScrollToNode(hasText(debt.title))
        compose.onNodeWithTag("task-state:${debt.id}").performClick();await {tag("bank-payment-amount")}
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextReplacement("400")
        compose.onNodeWithText("Confirmar pago").performClick()
        await {compose.onAllNodesWithText("¿Confirmar pago de $400?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Confirmar").performClick();await {runBlocking {repo.state().paid(debt.id)==400L}}
        await {!tag("bank-payment-amount")}
        compose.onNodeWithTag("contextual-search-query").assertTextContains("Coincidencia")
        compose.onNodeWithTag("node-title:${hidden.id}",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("projects-list").performScrollToNode(hasText(plain.title))
        compose.onNodeWithTag("task-state:${plain.id}").performClick()
        await {runBlocking {app.nodeRepository.getNode(plain.id)!!.isCompleted}}
        compose.onNodeWithTag("detail-scope-title").assertDoesNotExist()
        compose.onNodeWithTag("bank-payment-amount").assertDoesNotExist()
        compose.onNodeWithTag("contextual-search-query").assertTextContains("Coincidencia")
        assertFalse(runBlocking {app.nodeRepository.getNode(debt.id)!!.isCompleted})
    }

    @Test fun cardPagoUsesBankFormPreselectionSourcesCorrectionAndReopening() {
        val core=runBlocking {app.projectRepository.createProject("Tarjetas financieras")}
        val task=runBlocking {app.nodeRepository.createNode(core.id,null,"Pago preseleccionado",obligation=com.r0ybt.arachn0de.domain.model.Obligation(100000,"CLP"))}
        val repo=com.r0ybt.arachn0de.bank.BankRepository(app.database,nodeRepository=app.nodeRepository)
        val account=runBlocking {
            app.database.personDao().insert(com.r0ybt.arachn0de.data.local.PersonEntity("payer","Titular",null))
            repo.createRoot("Banco real","","bank")
            repo.createAccount("bank","Cuenta real","payer")
            val id=repo.state().accounts.single().id
            repo.confirm(repo.preview(com.r0ybt.arachn0de.bank.BankRequest(rootId="bank",contextAccountId=id,kind="INCOME",amount=50000,day=20000,concept="Ingreso",destinationAccountId=id)))
            id
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(core))}}
        await {compose.onAllNodesWithText(core.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(core.name).performClick();await {tag("nodes-list")}
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(task.title))
        compose.onNodeWithTag("node-title:${task.id}",useUnmergedTree=true).performClick()
        await {tag("detail-completion:${task.id}")}
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithText("Pago",useUnmergedTree=true).performClick()
        await {tag("bank-payment-amount")}
        compose.onNodeWithText("Pagos pendientes").assertDoesNotExist()
        compose.onNodeWithText("Origen: DIRECTO").performScrollTo().performClick()
        compose.onNode(hasText("Cuenta real") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Saldo disponible: $50.000").assertExists()
        compose.onNodeWithText("Origen: Cuenta real").performScrollTo().performClick()
        compose.onNode(hasText("DIRECTO") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Saldo disponible:",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Origen: DIRECTO").performScrollTo().performClick()
        compose.onNode(hasText("Cuenta real") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextInput("20.000")
        compose.onNodeWithText("Confirmar pago").performClick()
        await {compose.onAllNodesWithText("¿Confirmar pago de $20.000?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {repo.state().paid(task.id)==20000L}}
        assertEquals(30000L,runBlocking {repo.state().balance(account)})
        await {!tag("bank-payment-amount") && tag("core-context:Content")}
        if(compose.onAllNodesWithText("Pago",useUnmergedTree=true).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("core-context:Content").performClick()
        await {compose.onAllNodesWithText("Pago",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Pago",useUnmergedTree=true).performClick();await {tag("bank-payment-amount")}
        compose.onNodeWithText("Origen: DIRECTO").assertExists()
        compose.onNodeWithText("Saldo pendiente: $80.000").assertExists()
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextInput("30.000")
        compose.onNodeWithText("Confirmar pago").performClick()
        await {compose.onAllNodesWithText("¿Confirmar pago de $30.000?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {repo.state().paid(task.id)==50000L}}
        await {!tag("bank-payment-amount") && tag("core-context:Content")}
        if(compose.onAllNodesWithText("Pago",useUnmergedTree=true).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithText("Pago",useUnmergedTree=true).performClick();await {tag("bank-payment-amount")}
        compose.onNodeWithText("Saldo pendiente: $50.000").assertExists()
        assertEquals(30000L,runBlocking {repo.state().balance(account)})
        assertEquals(2,runBlocking {repo.state().operations.count {it.kind in setOf("PAYMENT","DIRECT")}})
        compose.onNodeWithText("Corregir importe original").performScrollTo().performClick()
        await {tag("node-property-editor:PAYMENT")}
        compose.onNodeWithTag("bank-payment-amount").assertDoesNotExist()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(100000L,runBlocking {app.nodeRepository.getNode(task.id)!!.obligation!!.amountMinor})
        assertFalse(runBlocking {app.nodeRepository.getNode(task.id)!!.isCompleted})
    }

    @Test fun cardWithoutAmountKeepsExistingEditorAndFirstDirectPaymentCreatesOnlyBank() {
        val core=runBlocking {app.projectRepository.createProject("Importe inicial")}
        val task=runBlocking {app.nodeRepository.createNode(core.id,null,"Tarjeta sin importe")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(core))}}
        await {compose.onAllNodesWithText(core.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(core.name).performClick();await {tag("nodes-list")}
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(task.title))
        compose.onNodeWithTag("node-title:${task.id}",useUnmergedTree=true).performClick()
        await {tag("detail-completion:${task.id}")}
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithText("Pago",useUnmergedTree=true).performClick()
        await {tag("node-property-editor:PAYMENT")}
        compose.onNodeWithTag("bank-payment-amount").assertDoesNotExist()
        compose.onNodeWithText("Monto").performTextInput("10000")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.obligation?.amountMinor==10000L}}
        await {compose.onAllNodesWithText("Pago",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Pago",useUnmergedTree=true).performClick();await {tag("bank-payment-amount")}
        val repo=com.r0ybt.arachn0de.bank.BankRepository(app.database,nodeRepository=app.nodeRepository)
        assertTrue(runBlocking {repo.state().roots.isEmpty()})
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextInput("1.000")
        compose.onNodeWithText("Confirmar pago").performClick()
        await {compose.onAllNodesWithText("¿Confirmar pago de $1.000?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {repo.state().paid(task.id)==1000L}}
        val saved=runBlocking {repo.state()}
        assertEquals("Banco",saved.projects.single {it.id==saved.roots.single().id}.name)
        assertTrue(saved.accounts.isEmpty());assertTrue(saved.people.isEmpty())
        assertEquals(9000L,saved.remaining(saved.nodes.single()));assertFalse(saved.nodes.single().isCompleted)
    }

    @Test fun detailKeepsOneTitleCheckboxAndDirectAncestorNavigation() {
        val core=runBlocking {app.projectRepository.createProject("Núcleo de ruta")}
        val layer=runBlocking {app.nodeRepository.createNode(core.id,null,"Capa de ruta",purpose=NodePurpose.LAYER)}
        val task=runBlocking {app.nodeRepository.createNode(core.id,layer.id,"Título completo de la tarea")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(core))}}
        await {compose.onAllNodesWithText(core.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(core.name).performClick()
        fun open(id:String,title:String) {
            await {tag("nodes-list")}
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(title))
            compose.onNodeWithTag("node-title:$id",useUnmergedTree=true).performClick()
            await {compose.onAllNodes(hasTestTag("detail-scope-title") and hasText(title)).fetchSemanticsNodes().isNotEmpty()}
        }
        open(layer.id,layer.title)
        open(task.id,task.title)
        compose.onNodeWithTag("app-bar-title").assertDoesNotExist()
        compose.onNodeWithTag("detail-scope-title").assertTextEquals(task.title)
        compose.onNodeWithTag("hierarchy-current").assertHasNoClickAction()
        compose.onNodeWithTag("hierarchy-ancestor:${layer.id}").assertIsDisplayed()
        compose.onNodeWithTag("detail-completion:${task.id}").performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.taskStatus != task.taskStatus}}
        compose.onNodeWithTag("hierarchy-ancestor:${layer.id}").performClick()
        await {compose.onAllNodesWithText(layer.title).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("detail-scope-title").assertTextEquals(layer.title)
        compose.onNodeWithTag("hierarchy-ancestor:root").performClick()
        await {compose.onAllNodesWithText(core.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("detail-scope-title").assertTextEquals(core.name)
        compose.onNodeWithTag("hierarchy-current").assertIsDisplayed()
        assertEquals(layer.id,runBlocking {app.nodeRepository.getNode(task.id)!!.parentId})
    }

    @Test fun contextualNameSearchFiltersOriginalCardsAndClosingKeepsTheCurrentLayer() {
        val core=runBlocking {app.projectRepository.createProject("Ámbito de búsqueda")}
        val ids=runBlocking {
            val layer=app.nodeRepository.createNode(core.id,null,"Rama actual",purpose=NodePurpose.LAYER)
            val inner=app.nodeRepository.createNode(core.id,layer.id,"Subcapa",purpose=NodePurpose.LAYER)
            val deep=app.nodeRepository.createNode(core.id,inner.id,"Café profundo")
            val outside=app.nodeRepository.createNode(core.id,null,"Café fuera")
            val other=app.nodeRepository.createNode(core.id,layer.id,"Sin coincidencia","café solo en descripción")
            listOf(layer.id,inner.id,deep.id,outside.id,other.id)
        }
        fun awaitCard(id:String)=await {
            try {
                compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(runBlocking {app.nodeRepository.getNode(id)!!.title}))
                compose.onAllNodesWithTag("node-title:$id",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            } catch(_:AssertionError) {false}
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(core))}}
        await {compose.onAllNodesWithText(core.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(core.name).performClick()
        awaitCard(ids[0])
        compose.onNodeWithTag("node-title:${ids[0]}",useUnmergedTree=true).performClick()
        awaitCard(ids[1])
        val before=runBlocking {app.database.backupDao().nodes()}
        showGlobalActions()
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithTag("contextual-search-query").performTextInput("cafe")
        awaitCard(ids[2])
        compose.onNodeWithTag("node-title:${ids[3]}",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("node-title:${ids[4]}",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("search-location:${ids[2]}",useUnmergedTree=true).assert(hasText("Ámbito de búsqueda › Rama actual › Subcapa"))
        compose.onNodeWithText("Búsqueda avanzada").assertDoesNotExist()
        compose.onNodeWithTag("contextual-search-results").assertDoesNotExist()
        compose.onNodeWithText("Limpiar").performClick()
        awaitCard(ids[1])
        compose.onNodeWithTag("node-title:${ids[3]}",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("contextual-search-query").performTextInput("cafe")
        awaitCard(ids[2])
        compose.onNodeWithText("Cerrar").performClick()
        awaitCard(ids[1])
        compose.onNodeWithTag("contextual-search-query").assertDoesNotExist()
        compose.onNodeWithTag("node-title:${ids[3]}",useUnmergedTree=true).assertDoesNotExist()
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
    }

    @Test fun dashboardSearchUsesTheSameProjectListAndMenuHasNoDuplicateSearch() {
        val core=runBlocking {app.projectRepository.createProject("Índice principal")}
        val nodes=runBlocking {
            val layer=app.nodeRepository.createNode(core.id,null,"Interior",purpose=NodePurpose.LAYER)
            val match=app.nodeRepository.createNode(core.id,layer.id,"Archivo profundo")
            val description=app.nodeRepository.createNode(core.id,null,"Otro","archivo solo en descripción")
            listOf(match.id,description.id)
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(core))}}
        await {tag("project-card:${core.id}")}
        showGlobalActions()
        compose.onNodeWithTag("global-menu-access").performClick()
        compose.onNodeWithTag("drawer-search").assertDoesNotExist()
        compose.onNodeWithTag("drawer-close-bottom").performClick()
        await {compose.onAllNodes(hasContentDescription("Buscar") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        showGlobalActions()
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithTag("contextual-search-query").performTextInput("archivo")
        await {
            try {
                compose.onNodeWithTag("projects-list").performScrollToNode(hasText("Archivo profundo"))
                compose.onAllNodesWithTag("node-title:${nodes[0]}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            } catch(_:AssertionError) {false}
        }
        compose.onNodeWithTag("node-title:${nodes[1]}",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-location:${nodes[0]}",useUnmergedTree=true).assert(hasText("Índice principal › Interior"))
        compose.onNodeWithTag("contextual-search-results").assertDoesNotExist()
        compose.onNodeWithText("Cerrar").performClick()
        await {tag("project-card:${core.id}")}
        compose.onNodeWithTag("contextual-search-query").assertDoesNotExist()
    }

    @Test fun destinationLoadingCanBeDismissedWithoutFalseEmptyResults() {
        var index by mutableStateOf<List<com.r0ybt.arachn0de.access.ElementResult>?>(null)
        var dismissed=false
        compose.setContent {Arachn0deTheme {NodeDestinationDialog(emptySet(),index,app.nodeRepository,onDismiss={dismissed=true})}}
        compose.onNodeWithText("Cargando destinos…").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick();compose.runOnIdle {assertTrue(dismissed)}
    }
}
