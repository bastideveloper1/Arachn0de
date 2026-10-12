package com.r0ybt.arachn0de.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Movement, occupancy and privacy acceptance for the replacement race board. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class GameExpansionUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @After fun close() { (compose.activity.application as com.r0ybt.arachn0de.Arachn0deApplication).database.close() }
    private fun initial()=RaceRules.newGame(Racer.entries.toList(),setOf(0,1),3,41)
    @Test fun tenTokensHaveDistinctBoundsAndAllOccupantsAreAccessible() {
        val s=initial(); compose.setContent { RaceBoard(s,0,emptyMap(),Modifier.height(600.dp)) }
        val bounds=s.players.map { compose.onNodeWithTag("race-token-${it.id}",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot }
        assertEquals(10,bounds.distinct().size); assertTrue(bounds[0].width>bounds[1].width)
        compose.onNodeWithTag("race-token-0").performClick()
        compose.onNodeWithText("#1 Conejo · Tú").assertExists()
        compose.onNodeWithText("#10 Dinosaurio").performScrollTo().assertIsDisplayed()
    }
    @Test fun hiddenRacerIsAbsentFromTokensAndOccupantsButOwnAndEyeViewsDetectIt() {
        var viewer by mutableIntStateOf(0)
        val s=initial().copy(players=initial().players.map { if(it.id==1)it.copy(invisible=true) else it })
        compose.setContent { RaceBoard(s,viewer,emptyMap(),Modifier.height(600.dp)) }
        compose.onNodeWithTag("race-token-1").assertDoesNotExist()
        compose.onNodeWithTag("race-token-0").performClick(); compose.onNodeWithText("#2 Tiburón").assertDoesNotExist(); compose.onNodeWithText("Cerrar").performClick()
        compose.runOnIdle { viewer=1 }; compose.onNodeWithTag("race-token-1").assertExists()
    }
    @Test fun movementChangesPositionWithoutChangingPortraitOrientation() {
        var s by mutableStateOf(initial()); compose.mainClock.autoAdvance=false
        compose.setContent { RaceBoard(s,0,emptyMap(),Modifier.height(600.dp)) }
        compose.mainClock.advanceTimeBy(1500); compose.onNodeWithText("Seguir · activado").performClick(); val start=compose.onNodeWithTag("race-token-0",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { s=s.copy(players=s.players.map { if(it.id==0)it.copy(tile=1,history=listOf(0,1)) else it }) }
        compose.mainClock.advanceTimeBy(1500); val end=compose.onNodeWithTag("race-token-0",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(start.width,end.width,.1f); assertEquals(start.height,end.height,.1f); assertNotEquals(start.left,end.left)
    }
}
