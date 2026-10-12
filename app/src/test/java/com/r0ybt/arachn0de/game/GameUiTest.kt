package com.r0ybt.arachn0de.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.AppIdentityDrawer
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Updated acceptance tests: the tactical race replaces the former battle menus. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class GameUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() { app.database.close() }
    private fun await(predicate: ()->Boolean) = compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame(); predicate()
    }
    @Test fun drawerKeepsGameAfterProductivity() {
        var opened=false; compose.setContent { AppIdentityDrawer(onDismiss={},onGame={opened=true}) }
        compose.onNodeWithText("Juego").performScrollTo().performClick(); compose.runOnIdle { assertTrue(opened) }
    }
    @Test fun newRaceCreatesTenDistinctRacersAndOnlyHumanHandoffOpensControls() {
        compose.setContent { GameScreen({}) }; compose.onNodeWithText("Nueva carrera").performClick()
        compose.onNodeWithText("Corredores: 10").assertExists(); compose.onNodeWithText("Vueltas: 3").assertExists()
        compose.onNodeWithText("Iniciar · 10 corredores").performClick(); compose.onNodeWithText("Iniciar",useUnmergedTree=true).performClick()
        await { compose.onAllNodesWithText("Ver mi turno · Conejo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lanzar dado").assertDoesNotExist()
        compose.onNodeWithText("Ver mi turno · Conejo").performClick()
        await { compose.onAllNodesWithText("Lanzar dado").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lanzar dado").assertIsEnabled()
        val saved=runBlocking { RaceCodec.decode(app.database.gameStateDao().get()!!.payload)!! }
        assertEquals(10,saved.players.size); assertEquals(1,saved.players.count { it.human }); assertEquals(RacePhase.READY,saved.phase)
    }
    @Test fun itemCancelDoesNotConsumeAndRerollIsOnlyOfferedAfterRoll() = runBlocking<Unit> {
        val s=RaceRules.begin(RaceRules.newGame(Racer.entries.take(2),setOf(0,1),3,45)).let { it.copy(players=it.players.map { p->if(p.id==0)p.copy(inventory=listOf(HeldRaceItem(RaceItem.REROLL,1))) else p }) }
        RaceRepository(app.database).start(s); compose.setContent { GameScreen({}) }
        await { compose.onAllNodesWithText("Continuar carrera · ronda 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continuar carrera · ronda 1").performClick(); compose.onNodeWithText("Usar · −1").performClick()
        compose.onNodeWithText("Cancelar").performClick(); assertEquals(s,RaceCodec.decode(app.database.gameStateDao().get()!!.payload))
        compose.onNodeWithText("Usar · −1").performClick(); compose.onNodeWithText("Confirmar · −1 casilla").performClick()
        await { compose.onAllNodesWithText("Lanzar dado · −1 casilla").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lanzar dado · −1 casilla").performClick()
        await { compose.onAllNodesWithText("Repetir una vez").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Repetir una vez").assertIsEnabled()
    }
    @Test fun sampledOriginalArtRendersInRaceAndExportsVisualReview() = runBlocking<Unit> {
        val s=RaceRules.begin(RaceRules.newGame(Racer.entries.toList(),setOf(0),3,42)).let { it.copy(players=it.players.map { p->if(p.id==0)p.copy(inventory=listOf(HeldRaceItem(RaceItem.ROCK,1),HeldRaceItem(RaceItem.EYE,1))) else p }) }
        RaceRepository(app.database).start(s)
        compose.setContent { com.r0ybt.arachn0de.ui.theme.Arachn0deTheme { GameScreen({}) } }
        await { compose.onAllNodesWithText("Continuar carrera · ronda 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continuar carrera · ronda 1").performClick()
        await { compose.onAllNodesWithContentDescription("#1 Conejo · Tú",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            assertTrue(view.width>0 && view.height>0)
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File("/tmp/arachnode-race-screen.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }

    @Test fun hiddenBotDoesNotRevealDiceStepsOrBoxPickup() = runBlocking<Unit> {
        val base=RaceRules.newGame(Racer.entries.take(2),setOf(0),3,44)
        val s=base.copy(current=1,phase=RacePhase.PRIZE,die=4,steps=2,itemUsed=true,cost=1,prize=RacePrize(RaceItem.ROCK),message="Caja secreta",players=base.players.map { if(it.id==1)it.copy(turns=1,tile=33,history=listOf(32,33),invisible=true,collected=setOf(33)) else it })
        RaceRepository(app.database).start(s); compose.mainClock.autoAdvance=false
        compose.setContent { GameScreen({}) }
        await { compose.onAllNodesWithText("Continuar carrera · ronda 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continuar carrera · ronda 1").performClick()
        compose.onNodeWithTag("race-token-1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dado 4").assertDoesNotExist()
        compose.onNodeWithText("Caja secreta").assertDoesNotExist()
        compose.onNodeWithText("Caja sorpresa…").assertDoesNotExist()
    }

}
