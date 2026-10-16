package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.PersonProfileEntity
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w360dp-h800dp")
class ThreeUiCorrectionsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun primaryDestinationsKeepUniformRowsAndCalendarHasOneDirectAccess() {
        var calendar=0;var dismissed=0
        compose.setContent {Arachn0deTheme {AppIdentityDrawer(onDismiss={dismissed++},
            onSearch={},onProjects={},onPeople={},onCalendar={calendar++},onMetro={},onAttention={})}}
        val names=listOf("Buscar","Núcleos","Antisocial","Calendario","Metro")
        val bounds=names.map {compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot}
        bounds.zipWithNext().forEach {(a,b)->assertTrue(a.top<b.top);assertEquals(a.left,b.left,1f)}
        val gaps=bounds.zipWithNext().map {(a,b)->b.top-a.top}
        gaps.forEach {assertEquals(gaps[0],it,1f)}
        compose.onNodeWithText("Seguimiento").performClick()
        compose.onAllNodesWithText("Calendario").assertCountEquals(1)
        compose.onNodeWithTag("drawer-calendar").performClick()
        assertEquals(1,calendar);assertEquals(1,dismissed)
    }

    @Test fun profileStatisticsUseOneBoldStyleAndKeepCountsAndNavigationLive() {
        var publications by mutableStateOf(3);var network by mutableStateOf(7);var opened=0
        compose.setContent {Arachn0deTheme {PersonPhotoHeader(Person("a","Ana"),
            PersonProfileEntity("a",description="Biografía"),onEditAvatar={},
            publications=publications,networkCount=network,onNetwork={opened++})}}
        compose.onNodeWithTag("person-publications").assert(hasText("3 Publicaciones"))
        compose.onNodeWithTag("person-network").assert(hasText("7 Red")).performClick()
        assertEquals(1,opened)
        fun layout(tag:String):TextLayoutResult {
            val results=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(results)}
            return results.single()
        }
        val publicationLayout=layout("person-publications")
        val networkLayout=layout("person-network")
        assertEquals(publicationLayout.layoutInput.style,networkLayout.layoutInput.style)
        assertEquals(FontWeight.Bold,publicationLayout.layoutInput.style.fontWeight)
        val a=compose.onNodeWithTag("person-publications").fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("person-network").fetchSemanticsNode().boundsInRoot
        assertEquals(a.center.y,b.center.y,1f);assertTrue(a.right<=b.left+1f)
        compose.runOnIdle {publications=4;network=8}
        compose.onNodeWithTag("person-publications").assert(hasText("4 Publicaciones"))
        compose.onNodeWithTag("person-network").assert(hasText("8 Red"))
        compose.onNodeWithText("Biografía").assertExists()
    }

    @Test fun panelActionsStayOnOppositeEdgesAndAlignedWithLargeFonts() {
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.6f)) {
                Box(Modifier.width(320.dp)) {
                    ModularPanelActions(cancel={TextButton({},Modifier.testTag("cancel")) {Text("Cancelar Descripción")}},
                        confirm={TextButton({},Modifier.testTag("confirm")) {Text("Confirmar Descripción")}})
                }
            }
        }}
        val a=compose.onNodeWithTag("cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("confirm").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(a.right<b.left);assertEquals(a.center.y,b.center.y,1f)
    }
}
