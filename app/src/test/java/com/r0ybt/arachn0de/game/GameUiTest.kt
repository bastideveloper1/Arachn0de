package com.r0ybt.arachn0de.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.ui.AppIdentityDrawer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GameUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun storage() = compose.activity.getSharedPreferences("experimental_game", 0)
    private fun seed(count: Int) {
        val s = GameRules.beginTurn(GameRules.newGame((1..count).map { "Jugador $it" }, RatCharacter.entries.take(count), FirstGameMap.value))
        storage().edit().clear().putString("session", GameSessionCodec.encode(s)).commit()
    }
    @Test fun drawerOffersGameAfterProductivityAndInvokesNavigation() {
        var opened = false
        compose.setContent { AppIdentityDrawer(onDismiss = {}, onGame = { opened = true }) }
        compose.onNodeWithText("Juego").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
    @Test fun setupStartsDistinctCharactersAndRequiresHandoff() {
        storage().edit().clear().commit()
        compose.setContent { GameScreen({}) }
        compose.onNodeWithText("Nueva partida").performClick()
        compose.onNodeWithText("Comenzar partida").performScrollTo().performClick()
        compose.onNodeWithText("Turno de Jugador 1").assertExists()
        compose.onNodeWithText("Comenzar turno").performClick()
        compose.onNodeWithText("Tirar dado").assertIsEnabled()
        compose.runOnIdle {
            val s = GameSessionCodec.decode(storage().getString("session", "")!!, FirstGameMap.value)!!
            assertEquals(TurnPhase.READY, s.phase)
            assertEquals(2, s.players.map { it.character }.distinct().size)
        }
    }
    @Test fun fourTokensShareTileInStableNonOverlappingQuadrantsAndMovementFinishes() {
        seed(4)
        compose.setContent { GameScreen({}) }
        compose.onNodeWithText("Continuar partida").performClick()
        val bounds = TokenQuadrant.entries.mapIndexed { index, q ->
            compose.onNodeWithTag("token-player-${index + 1}-${q.name}").fetchSemanticsNode().boundsInRoot
        }
        assertTrue(bounds[0].right <= bounds[1].left)
        assertTrue(bounds[0].bottom <= bounds[2].top)
        assertTrue(bounds[2].right <= bounds[3].left)
        compose.onNodeWithText("Tirar dado").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Comenzar turno").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Turno de Jugador 2").assertExists()
        compose.runOnIdle {
            val s = GameSessionCodec.decode(storage().getString("session", "")!!, FirstGameMap.value)!!
            assertEquals("tile-${s.lastRoll}", s.players[0].tileId)
            assertEquals(1, s.currentIndex)
        }
    }

    @Test fun zeroChargesKeepAbilityVisibleAndDisabled() {
        val s = GameRules.beginTurn(GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), FirstGameMap.value))
            .let { it.copy(players = it.players.map { p -> p.copy(abilityCharges = 0) }) }
        storage().edit().clear().putString("session", GameSessionCodec.encode(s)).commit()
        compose.setContent { GameScreen({}) }
        compose.onNodeWithText("Continuar partida").performClick()
        compose.onNodeWithText("Estandarte ×0").assertExists()
        compose.onNodeWithTag("ability-button").assertIsNotEnabled()
        compose.onNodeWithTag("dice-button").assertIsEnabled()
    }
    @Test fun abilityHelpDoesNotChangeSession() {
        seed(2)
        compose.setContent { GameScreen({}) }
        compose.onNodeWithText("Continuar partida").performClick()
        val before = storage().getString("session", "")
        compose.onNodeWithTag("ability-help").performClick()
        compose.onNodeWithText(ObjectType.BANNER.description()).assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("dice-button").assertIsEnabled()
        compose.runOnIdle { assertEquals(before, storage().getString("session", "")) }
    }
    @Test fun fogDoesNotExposeFarEnemyOrTrapInSemanticsAndHandoffHidesBoard() {
        val map = FirstGameMap.value
        val s = GameRules.beginTurn(GameRules.newGame(listOf("Ana", "Enemigo lejano"), RatCharacter.entries.take(2), map))
            .let { it.copy(players = it.players.map { p -> if (p.id == "player-2") p.copy(tileId = "tile-20") else p },
                objects = listOf(BoardObject("ice", "player-2", "tile-21", ObjectType.ICE_BARRIER))) }
        storage().edit().clear().putString("session", GameSessionCodec.encode(s)).commit()
        compose.setContent { GameScreen({}) }
        compose.onNodeWithText("Continuar partida").performClick()
        compose.onNodeWithTag("token-player-2-TOP_RIGHT").assertDoesNotExist()
        compose.onNodeWithTag("object-ice").assertDoesNotExist()
        compose.onNodeWithTag("world-fog").assertExists()
        compose.onNodeWithText("Enemigo lejano", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Tirar dado").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Comenzar turno").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("game-board").assertDoesNotExist()
    }
    @Test fun diceAnimationAndPlacementSurviveSavedStateRestoration() {
        seed(2)
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { GameScreen({}) }
        compose.onNodeWithText("Continuar partida").performClick()
        compose.onNodeWithTag("ability-button").performClick()
        compose.onNodeWithTag("place-tile-1").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Colocar").assertIsEnabled().performClick()
        compose.runOnIdle {
            val placed = GameSessionCodec.decode(storage().getString("session", "")!!, FirstGameMap.value)!!
            assertEquals("tile-1", placed.objects.single().tileId)
            assertEquals(1, placed.currentPlayer.abilityCharges)
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("dice-button").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        // Let Robolectric dispatch the invalidation before advancing the paused frame clock.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val result = GameSessionCodec.decode(storage().getString("session", "")!!, FirstGameMap.value)!!.lastRoll
        assertNotNull(result)
        compose.onNodeWithTag("dice-button").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(1600)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10000) { compose.onAllNodesWithText("Comenzar turno").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            val restored = GameSessionCodec.decode(storage().getString("session", "")!!, FirstGameMap.value)!!
            assertEquals(result, restored.lastRoll)
            assertEquals("tile-$result", restored.players[0].tileId)
        }
    }

    @Test fun setupRestoresPlayerNamesAndFourLapConfiguration() {
        storage().edit().clear().commit()
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { GameScreen({}) }
        compose.onNodeWithText("Nueva partida").performClick()
        compose.onNodeWithTag("laps-4").performClick()
        compose.onAllNodesWithText("Nombre (máximo 16 caracteres)")[0].performTextReplacement("Camila")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Camila").assertExists()
        compose.onNodeWithTag("laps-4").assertIsSelected()
        compose.onNodeWithText("Comenzar partida").performScrollTo().performClick()
        compose.runOnIdle {
            val s = GameSessionCodec.decode(storage().getString("session", "")!!, FirstGameMap.value)!!
            assertEquals(4, s.targetLaps)
            assertEquals("Camila", s.players[0].name)
        }
    }
}
