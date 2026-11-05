package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33],qualifiers="es-rCL-w411dp-h1200dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BankVisualCompactTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    private fun bounds(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
    @Test fun bankCoreOmitsSignalsAndAccountsWrapOnlyWhenNeededAcrossOfficialThemes() {
        var images by mutableStateOf(false)
        val bank=Project("bank","Banco","",0,0,0);val regular=bank.copy(id="normal",name="Normal")
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalBankRootIds provides setOf("bank"),LocalAppearancePalette provides if(images)AppearanceTheme.DayGrey.palette else AppearanceTheme.Arachn0de.palette) {
            Column(Modifier.width(411.dp)) {
                ProjectCard(bank,ProjectCardAlerts(),{},{},{})
                ProjectCard(regular,ProjectCardAlerts(),{},{},{})
                BankAccountCard("Basti local","Basti",120000,"short") {}
                BankAccountCard("Caja","",0,"missing") {}
                BankAccountCard("Cuenta de ahorros con un nombre extenso que necesita envolver el texto","Titular de nombre largo",42,"long") {}
            }
        }}}
        compose.onNodeWithTag("bank-core-indicator:bank",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("bank-core-indicator:normal",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("card-indicators:project:bank",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("card-indicators:project:normal",useUnmergedTree=true).assertExists()
        compose.onAllNodesWithContentDescription("Opciones del proyecto",useUnmergedTree=true).assertCountEquals(2)
        compose.onNodeWithText("Basti local (Basti)").assertExists();compose.onNodeWithText("Basti",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Caja").assertExists();compose.onNodeWithText("Caja ()").assertDoesNotExist()
        compose.onNodeWithText("$120.000").assertExists()
        assertTrue("El indicador no debe aumentar la altura compacta: ${bounds("project-card:bank").height/compose.density.density}",bounds("project-card:bank").height/compose.density.density<=64f)
        assertTrue(bounds("project-card:bank").height<bounds("project-card:normal").height)
        assertTrue(bounds("bank-account:short").height<bounds("bank-account:long").height)
        val tags=listOf("project-card:bank","project-card:normal","bank-account:short")
        val before=tags.associateWith(::bounds)
        compose.runOnIdle {images=true}
        tags.forEach {assertEquals(before.getValue(it),bounds(it))}
        assertTrue(bounds("bank-core-indicator:bank").right<=bounds("project-title:bank").right+20f*compose.density.density)
        assertTrue(bounds("bank-account-name:long").height/compose.density.density<=56f)
    }
    @Test fun selectorCardsKeepIdentityInternallyAndAlignCheckboxWithTitle() {
        val first=NodeEntity("11111111-1111-4111-8111-111111111111","p",null,"Luz","",false,0,0,0,amountMinor=11022,currencyCode="CLP")
        val second=first.copy(id="22222222-2222-4222-8222-222222222222")
        val state=BankState(emptyList(),emptyList(),emptyList(),emptyList(),listOf(ProjectEntity("p","Ruta privada","",0,0,0)),listOf(first,second),emptyList(),emptyList(),emptyList())
        var selected by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalAppearancePalette provides AppearanceTheme.NightBlue.palette) {
            Column(Modifier.width(411.dp)) {
                BankDebtCard(state,first,null,selected,{selected=!selected})
                BankDebtCard(state,second,null,false,{})
            }
        }}}
        compose.onAllNodesWithText("Luz").assertCountEquals(2)
        compose.onNodeWithText(first.id,substring=true).assertDoesNotExist();compose.onNodeWithText("Ruta privada",substring=true).assertDoesNotExist()
        compose.onAllNodesWithText("Pendiente: $11.022").assertCountEquals(2)
        val card=bounds("bank-debt:${first.id}")
        assertTrue(card.height/compose.density.density<100f)
        val check=compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("bank-debt:${first.id}")))
        check.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val checkBounds=check.fetchSemanticsNode().boundsInRoot
        val title=bounds("node-title:${first.id}")
        assertEquals(checkBounds.center.y,title.center.y,2f)
        assertTrue(checkBounds.right<=title.left)
        check.performClick();compose.onNodeWithTag("bank-debt:${first.id}").assertIsSelected()
        compose.onNodeWithTag("bank-debt:${second.id}").assertIsNotSelected()
        assertEquals(11022L,state.remaining(first));assertTrue(state.operations.isEmpty());assertTrue(state.allocations.isEmpty())
    }
    @Test fun outerPartialSummaryAndFullInteriorPreservePaymentInformation() {
        val base=Node("none","p",null,"Pago","",false,0,0,0,false,obligation=Obligation(11522,"CLP"))
        val nodes=listOf(base,base.copy(id="partial"),base.copy(id="full",isCompleted=true),base.copy(id="manual",isCompleted=true))
        val paid=mapOf("partial" to 500L,"full" to 11522L,"manual" to 500L)
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalBankPaid provides paid) {
            Column {
                nodes.forEach {n->Box(Modifier.testTag(n.id)) {ObligationIndicator(n)}}
                Box(Modifier.testTag("interior")) {ObligationIndicator(nodes[1],compactSummary=false)}
            }
        }}}
        compose.onNode(hasText("Pendiente: $11.022 CLP") and hasAnyAncestor(hasTestTag("partial")),useUnmergedTree=true).assertExists()
        compose.onNode(hasText("$11.522 CLP",substring=false) and hasAnyAncestor(hasTestTag("none")),useUnmergedTree=true).assertExists()
        compose.onNode(hasText("Pagada",substring=true) and hasAnyAncestor(hasTestTag("full")),useUnmergedTree=true).assertExists()
        compose.onNode(hasText("Pendiente:",substring=true) and hasAnyAncestor(hasTestTag("full")),useUnmergedTree=true).assertDoesNotExist()
        compose.onNode(hasText("Abonado",substring=true) and hasAnyAncestor(hasTestTag("partial")),useUnmergedTree=true).assertDoesNotExist()
        compose.onNode(hasText("Abonado Banca:",substring=true) and hasAnyAncestor(hasTestTag("interior")),useUnmergedTree=true).assertExists()
        compose.onNode(hasText("$500 CLP",substring=true) and hasAnyAncestor(hasTestTag("interior")),useUnmergedTree=true).assertExists()
        assertFalse(nodes[1].isCompleted);assertTrue(nodes[2].isCompleted);assertEquals(11522L,nodes[1].obligation!!.amountMinor)
    }
}
