package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28],qualifiers="w360dp-h800dp")
class ElementCorrectionVisualTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun taskTotalsUseCommonBadgesWithoutAddingActionsAndCreationLabelsOwnFullTargets() {
        var chosen=NodePurpose.ACTION
        compose.setContent {Arachn0deTheme {Column {
            InteriorTaskStateIndicators(InteriorTaskCounts(setOf("a","b"),emptySet(),setOf("v")),true,"node:n")
            FormChoices(listOf(NodePurpose.ACTION to "Tarea",NodePurpose.NOTE to "Nota",NodePurpose.LAYER to "Capa"),NodePurpose.ACTION,true,touchAccessible=true) {chosen=it}
        }}}
        compose.onNodeWithTag("detail-state-count:node:n:PENDING",useUnmergedTree=true).onChildren().filter(hasText("2")).assertCountEquals(1)
        compose.onNodeWithTag("detail-state-count:node:n:VERIFY",useUnmergedTree=true).onChildren().filter(hasText("1")).assertCountEquals(1)
        for(status in listOf("PENDING","VERIFY")) {
            val node=compose.onNodeWithTag("detail-task-state:node:n:$status",useUnmergedTree=true)
            node.assertHasNoClickAction()
            val b=node.fetchSemanticsNode().boundsInRoot
            assertTrue(b.width>=48*compose.density.density-1);assertTrue(b.height>=48*compose.density.density-1)
        }
        val choice=compose.onNode(hasText("Nota") and hasClickAction())
        val b=choice.fetchSemanticsNode().boundsInRoot
        assertTrue(b.height>=48*compose.density.density-1)
        compose.onNodeWithText("Nota").performClick()
        compose.runOnIdle {assertEquals(NodePurpose.NOTE,chosen)}
    }
}
