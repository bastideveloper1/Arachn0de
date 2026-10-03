package com.r0ybt.arachn0de.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class FinancialUiTest {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    private lateinit var root: String
    private lateinit var bill: String
    private lateinit var undated: String
    private lateinit var oldZone: TimeZone
    private val zone=TimeZone.getTimeZone("UTC")
    private val fixed=GregorianCalendar(zone).apply { clear();set(2026,Calendar.OCTOBER,16,12,0) }.timeInMillis
    private val clock=AtomicLong(fixed)
    @get:Rule val rules: RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() {
            oldZone=TimeZone.getDefault();TimeZone.setDefault(zone)
            app=ApplicationProvider.getApplicationContext()
            runBlocking {
                project=app.projectRepository.createProject("Personal").id
                root=app.nodeRepository.createNode(project,null,"Salud").id
                val inner=app.nodeRepository.createNode(project,root,"Tratamiento").id
                bill=app.nodeRepository.createNode(project,inner,"Cuenta",dueAt=fixed,obligation=Obligation(50000,"CLP")).id
                undated=app.nodeRepository.createNode(project,null,"Sin fecha",obligation=Obligation(1050,"USD")).id
                app.personRepository.save("r","Roy",null);app.personRepository.save("s","Scarlett",null)
                app.personRepository.setResponsiblePeople(bill,setOf("r","s"))
            }
        }
        override fun after() { app.database.close();TimeZone.setDefault(oldZone) }
    }).around(compose)
    private fun awaitText(text:String)=compose.waitUntil(10_000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}
    private fun awaitTag(tag:String)=compose.waitUntil(10_000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun mount(restorer:StateRestorationTester?=null) {
        if(restorer==null)compose.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository){clock.get()} } } }
        else restorer.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository){clock.get()} } } }
    }
    private fun open() {
        awaitText("Personal");compose.onNodeWithContentDescription("Abrir menú").performClick();compose.onNodeWithText("Obligaciones").performClick()
        awaitTag("financial-summary")
    }
    private fun row(id:String) { compose.onNodeWithTag("obligations-list").performScrollToNode(hasTestTag("obligation-task:$id"));awaitTag("obligation-task:$id") }
    private fun filter(text:String) { compose.onNodeWithTag("obligations-list").performScrollToIndex(0);compose.onNodeWithText(text).performClick() }
    private fun person(name:String) {
        compose.onNodeWithTag("obligations-list").performScrollToIndex(0)
        compose.onNode(hasText("Persona:",substring=true)).performClick();compose.onNodeWithText(name).performClick()
    }

    @Test fun filtersCurrencySummaryRealNavigationAndRestorationUseSameObligations() {
        val restorer=StateRestorationTester(compose)
        mount(restorer);open()
        compose.onNodeWithTag("financial-currency:CLP").assertExists();compose.onNodeWithTag("financial-currency:USD").assertDoesNotExist()
        filter("Todo");awaitText("USD");person("Roy");awaitText("Persona: Roy")
        compose.waitUntil(10_000){compose.onAllNodesWithTag("financial-currency:USD").fetchSemanticsNodes().isEmpty()}
        restorer.emulateSavedInstanceStateRestore();awaitText("Persona: Roy")
        compose.onNodeWithText("Todo").assertIsSelected();row(bill)
        compose.onNodeWithTag("obligation-task:$bill").assert(hasText("Personal › Salud › Tratamiento"))
        compose.onNodeWithTag("obligation-task:$bill").assert(hasText("Roy",substring=true));compose.onNodeWithTag("obligation-task:$bill").assert(hasText("Scarlett",substring=true))
        val before=runBlocking { app.nodeRepository.getProjectNodes(project) }
        compose.onNodeWithTag("obligation-task:$bill").performClick();awaitText("CAPA 3")
        restorer.emulateSavedInstanceStateRestore();awaitText("CAPA 3")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Completar"));compose.onNodeWithText("Completar").performClick()
        compose.waitUntil(10_000){runBlocking { app.nodeRepository.getNode(bill)!!.isCompleted }}
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        awaitText("Obligaciones");row(bill);compose.onNodeWithTag("obligation-task:$bill").assert(hasText("Completada"))
        filter("Todo");compose.onNodeWithText("Todo").assertIsSelected();compose.onNodeWithText("Persona: Roy").assertExists()
        assertEquals(before.map { it.copy(isCompleted=it.id==bill,updatedAt=0) },runBlocking { app.nodeRepository.getProjectNodes(project).map { it.copy(updatedAt=0) } })
    }
    @Test fun dataChangesAndTimezoneRefreshTotalsWithoutChangingStoredMoneyOrDates() {
        mount();open();row(bill)
        runBlocking { app.personRepository.save("r","Renamed",null,isNew=false) }
        awaitText("Renamed")
        person("Renamed");awaitTag("financial-summary")
        runBlocking { app.personRepository.setResponsiblePeople(bill,setOf("s")) }
        awaitText("No hay obligaciones para este filtro.")
        person("Todas las Personas");awaitTag("financial-summary")
        val edge=GregorianCalendar(zone).apply { clear();set(2026,Calendar.NOVEMBER,1,1,0) }.timeInMillis
        runBlocking { app.nodeRepository.updateNodeWithDates(bill,"Cuenta","",null,edge) }
        awaitText("No hay obligaciones para este filtro.")
        val before=runBlocking { app.nodeRepository.getNode(bill) }
        compose.runOnIdle { TimeZone.setDefault(TimeZone.getTimeZone("GMT-03:00"));app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED)) }
        awaitTag("financial-summary");row(bill)
        assertEquals(before,runBlocking { app.nodeRepository.getNode(bill) })
        filter("Todo");awaitText("USD");row(undated);compose.onNodeWithTag("obligation-task:$undated").assert(hasText("Sin vencimiento"))
        runBlocking { app.nodeRepository.deleteNode(bill);app.nodeRepository.deleteNode(undated) }
        awaitText("No hay obligaciones para este filtro.")
    }
    @Test fun projectAndLayerContextsShowRecursiveTotalsAndVisitingViewKeepsLocation() {
        mount();awaitText("Personal");compose.onNodeWithText("Personal").performClick()
        awaitText("USD")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Salud"));compose.onNodeWithText("Salud").performClick();awaitText("CAPA 1")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Total",substring=true))
        compose.onNodeWithTag("financial-currency:CLP").assertExists();compose.onNodeWithTag("financial-currency:USD").assertDoesNotExist()
        compose.onNodeWithContentDescription("Abrir menú").performClick();compose.onNodeWithText("Obligaciones").performClick();awaitTag("financial-summary")
        compose.onNodeWithText("Volver").performClick();awaitText("CAPA 1")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Tratamiento"));compose.onNodeWithText("Tratamiento").assertExists()
    }
}
