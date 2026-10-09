package com.r0ybt.arachn0de.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28], qualifiers = "w411dp-h891dp")
class GameExpansionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = compose.activity.application as Arachn0deApplication
    @After fun close() { app.database.close() }
    private fun await(predicate: () -> Boolean) = compose.waitUntil(10000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame(); predicate()
    }
    @Test fun finalSpriteArrivesBeforeEffectsOrTurnAdvanceAndAnimationAcknowledgesOnce() {
        val map = FirstGameMap.value
        var state by mutableStateOf(GameExpansion.planStep(GameExpansion.finishRoll(GameRules.roll(GameRules.beginTurn(
            GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), map)), 1)), map))
        var arrivals = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { Column { GameBoard(state, map, {}, {}, {}, {}, {}, onArrived = { arrivals++; state = GameExpansion.arrive(state, map) }) } }
        val from = compose.onNodeWithTag("token-player-1-TOP_LEFT").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        assertEquals(0, arrivals); assertEquals(TurnPhase.ANIMATING, state.phase); assertEquals(0, state.currentIndex)
        compose.mainClock.advanceTimeBy(120); compose.waitForIdle()
        assertEquals(1, arrivals); assertEquals(TurnPhase.RESOLVING, state.phase)
        val to = compose.onNodeWithTag("token-player-1-TOP_LEFT").fetchSemanticsNode().boundsInRoot.left
        assertEquals(411f / map.columns, to - from, 1f)
        compose.mainClock.advanceTimeBy(2000); assertEquals(1, arrivals); assertNull(state.resultDeadline)
    }
    @Test fun resultWaitsTenSecondsThenAdvancesExactlyOnce() = runBlocking<Unit> {
        val repository = GameStateRepository(app.database)
        var now = 0L
        val initial = GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), FirstGameMap.value)
            .copy(phase = TurnPhase.RESULT, result = "Terminado", resultDeadline = 10000)
        repository.start(initial)
        compose.mainClock.autoAdvance = false
        compose.setContent { GameScreen({}, clock = { now }) }
        await { compose.onAllNodesWithText("Continuar partida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continuar partida").performClick()
        await { compose.onAllNodesWithTag("continue-game-turn").fetchSemanticsNodes().isNotEmpty() }
        now = 9000; compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        assertEquals(0, repository.load().session!!.currentIndex)
        now = 10000; compose.mainClock.advanceTimeBy(300)
        await { compose.onAllNodesWithText("Comenzar turno").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, repository.load().session!!.currentIndex)
        compose.mainClock.advanceTimeBy(20000); assertEquals(1, repository.load().session!!.currentIndex)
    }
    @Test fun continueSkipsCountdownAndSavedStateRecreationKeepsSameActor() = runBlocking<Unit> {
        val repository = GameStateRepository(app.database)
        repository.start(GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), FirstGameMap.value)
            .copy(phase = TurnPhase.RESULT, result = "Terminado", resultDeadline = 10000))
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { GameScreen({}, clock = { 0 }) }
        await { compose.onAllNodesWithText("Continuar partida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continuar partida").performClick()
        await { compose.onAllNodesWithTag("continue-game-turn").fetchSemanticsNodes().isNotEmpty() }
        restoration.emulateSavedInstanceStateRestore()
        await { compose.onAllNodesWithTag("continue-game-turn").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("continue-game-turn").performClick()
        await { compose.onAllNodesWithText("Comenzar turno").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, repository.load().session!!.currentIndex)
    }
    @Test fun hiddenBotSpiderAnimationAndResultNeverLeakIntoHumanSemantics() {
        val map = GameMapGenerator.generate(33)
        val base = GameRules.newGame(listOf("A", "Enemigo secreto"), RatCharacter.entries.take(2), map, controls = listOf(PlayerControl.HUMAN, PlayerControl.HARD))
        val state = base.copy(currentIndex = 1, phase = TurnPhase.RESULT, result = "Encontró una araña secreta",
            players = base.players.mapIndexed { i, p -> if (i == 1) p.copy(tileId = map.goal) else p })
        compose.setContent { Column { GameBoard(state, map, {}, {}, {}, {}, {}, viewerId = "player-1") } }
        compose.onNodeWithTag("game-spider").assertDoesNotExist()
        compose.onNodeWithTag("token-player-2-TOP_RIGHT").assertDoesNotExist()
        compose.onNodeWithText("Enemigo secreto", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Encontró una araña secreta").assertDoesNotExist()
    }
    @Test fun arrivingFromHiddenTerrainDoesNotExposeTheHiddenAnimation() {
        val map = FirstGameMap.value
        val base = GameRules.newGame(listOf("A", "Bot"), RatCharacter.entries.take(2), map, controls = listOf(PlayerControl.HUMAN, PlayerControl.HARD))
        var state by mutableStateOf(base.copy(currentIndex = 1, phase = TurnPhase.ANIMATING,
            motion = GameMotion("player-2", "tile-4", "tile-3", MotionKind.RETREAT),
            players = base.players.mapIndexed { i, p -> if (i == 1) p.copy(tileId = "tile-3") else p }))
        compose.mainClock.autoAdvance = false
        compose.setContent { Column { GameBoard(state, map, {}, {}, {}, {}, {}, viewerId = "player-1", onArrived = { state = GameExpansion.arrive(state, map) }) } }
        compose.onNodeWithTag("token-player-2-TOP_RIGHT").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(520); compose.waitForIdle()
        compose.onNodeWithTag("token-player-2-TOP_RIGHT").assertExists()
    }
    @Test fun continueCanSkipBotWaitWithoutRevealingItsHiddenResult() {
        val map = FirstGameMap.value
        val base = GameRules.newGame(listOf("A", "Bot"), RatCharacter.entries.take(2), map, controls = listOf(PlayerControl.HUMAN, PlayerControl.HARD))
        // The retreat finishes in view, but the combat took place outside it.
        val state = base.copy(currentIndex = 1, phase = TurnPhase.RESULT, result = "Secreto", resultDeadline = 10000,
            spider = SpiderState("tile-4"), combat = SpiderCombat(1, 6),
            players = base.players.mapIndexed { i, p -> if (i == 1) p.copy(tileId = "tile-2") else p })
        var continued = 0
        compose.setContent { Column { GameBoard(state, map, {}, {}, {}, {}, {}, viewerId = "player-1", onContinue = { continued++ }) } }
        compose.onNodeWithText("Secreto").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dado de la araña: 6").assertDoesNotExist()
        compose.onNodeWithTag("continue-game-turn").assertIsEnabled().performClick()
        assertEquals(1, continued)
    }
    @Test fun replacingSamePendingMotionRestartsItsAcknowledgementWithFreshRevision() {
        val map = FirstGameMap.value
        val state = GameExpansion.planStep(GameExpansion.finishRoll(GameRules.roll(GameRules.beginTurn(
            GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), map)), 1)), map)
        var revision by mutableLongStateOf(1L)
        val acknowledgements = mutableListOf<Long>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val captured = revision
            Column { GameBoard(state, map, {}, {}, {}, {}, {}, animationRevision = revision, onArrived = { acknowledgements.add(captured) }) }
        }
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        compose.runOnIdle { revision = 2 }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
        assertTrue(acknowledgements.isEmpty())
        compose.mainClock.advanceTimeBy(350); compose.waitForIdle()
        assertEquals(listOf(2L), acknowledgements)
        compose.mainClock.advanceTimeBy(1000); assertEquals(listOf(2L), acknowledgements)
    }
}
