package com.r0ybt.arachn0de.slots

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JungleSlotsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun ready()=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("slot-stage").fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun originalSymbolsAndPrizeAnimationsRemainAccessible() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        assertEquals(8,JungleAssets.names.size)
        assertEquals(SlotMachine.symbols.map {it.removePrefix("jungle/")},JungleAssets.names)
        JungleAssets.names.forEach {name->app.assets.open("slots/jungle/$name").use {
            val image=requireNotNull(BitmapFactory.decodeStream(it));assertTrue(image.width>0 && image.height>0);image.recycle()
        }}
        junglePrizeGifs.forEach {name->app.assets.open("slots/jungle/premios/premios_$name.gif").use {assertTrue(String(it.readBytes().take(6).toByteArray(),Charsets.US_ASCII) in setOf("GIF87a","GIF89a"))}}
        assertEquals(SlotMachine.payouts.map {it.toLong()},SavingsPayTable().prizeUnits)
    }
    @Test fun threeModesReuseCallbacksAndKeepSpinSoundAndPaymentTools() {
        var mode by mutableIntStateOf(0);var sound by mutableStateOf(true)
        val spins=mutableListOf<Int>();var deposits=0;var panels=0;var exits=0
        compose.setContent {Arachn0deTheme {
            JungleSlots(SlotMachine(),0,TaskSlotSession(),mode==1,mode==2,true,false,sound,null,null,TagState(),emptyMap(),
                onSpin={spins+=mode},onMode={mode=it},onVolume={sound=!sound},onExit={exits++},onPanel={panels++},onCredit={},onPay={deposits++},onChoose={},onOpenTask={})
        }}
        ready()
        compose.onNodeWithText("Tragaperras").assertExists();compose.onNodeWithContentDescription("Arachn0de").assertExists()
        for(next in listOf(0,2,1)) {
            compose.onNodeWithTag("slot-mode:$next").performScrollTo().performClick()
            compose.onNodeWithTag("slot-mode:$next").assertIsSelected()
            compose.onNodeWithTag("slot-spin").performScrollTo().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f)).performClick()
            if(next==2)compose.onNodeWithText("Depositar").performScrollTo().performClick() else compose.onNodeWithText("Depositar").assertDoesNotExist()
            if(next!=1)repeat(3) {compose.onNodeWithTag("slot-reel:$it").assertExists()}
        }
        compose.onNodeWithTag("slot-sound").performScrollTo().performClick()
        compose.onNodeWithTag("slot-sound").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Silenciado"))
        compose.onNodeWithText("Ajustes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Salir").performScrollTo().performClick()
        assertEquals(listOf(0,2,1),spins);assertEquals(1,deposits);assertEquals(1,panels);assertEquals(1,exits)
    }
    @Test fun taskCardsAndIndependentCoreSelectionRemainAvailable() {
        val projects=listOf(Project("p1","Núcleo 1","",0,0,0),Project("p2","Núcleo 2","",1,0,0))
        val nodes=projects.map {Node("t"+it.id,it.id,null,"Tarea","",false,0,0,0,false)}
        val task=TaskSlotSession();task.refresh(SlotCandidates(projects,nodes));task.prepareSelection()
        var revision by mutableIntStateOf(0)
        compose.setContent {Arachn0deTheme {
            JungleSlots(task.machine,revision,task,true,false,true,false,true,null,null,TagState(),emptyMap(),{}, {}, {}, {}, {}, {}, {},{task.choose(it);revision++},{})
        }}
        ready();compose.onNodeWithContentDescription("Núcleo 1").assertExists()
        compose.onNodeWithTag("slot-core-arrow:1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Núcleo 2").assertExists()
        assertEquals(SlotMachine.Phase.READY,task.machine.phase)
    }
    @Test fun spinningOrInactiveStateStillBlocksActions() {
        val engine=SlotMachine();engine.insert();engine.inserted();assertTrue(engine.spin())
        var active by mutableStateOf(false);var panels=0
        compose.setContent {Arachn0deTheme {JungleSlots(engine,0,TaskSlotSession(),false,false,active,false,true,null,null,TagState(),emptyMap(),{fail("Spin while inactive")},{fail("Mode during spin")},{fail("Sound while inactive")},{},{panels++},{},{},{},{})}}
        ready()
        compose.onNodeWithTag("slot-spin").performScrollTo().assertIsNotEnabled()
        repeat(3) {compose.onNodeWithTag("slot-mode:$it").performScrollTo().assertIsNotEnabled()}
        compose.onNodeWithTag("slot-sound").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {active=true}
        compose.onNodeWithTag("slot-spin").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Premios").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1,panels)
    }
}
