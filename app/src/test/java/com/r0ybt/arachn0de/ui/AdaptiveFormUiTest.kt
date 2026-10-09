package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class AdaptiveFormUiTest {
    @get:Rule val compose = createComposeRule()
    private fun dates(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Arachn0deTheme { Column { TaskDatesEditor(EditorDraft(null,null,"",""), true, TaskDatePickerDraft(), progressive = true) } }
            }
        }
    }
    @Test @Config(qualifiers = "w320dp-h640dp") fun elementTypeRemainsVisibleAfterScrollingTheForm() {
        val draft=EditorDraft(null,null,"Borrador","",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION)
        compose.setContent {Arachn0deTheme {NodeDialog(draft,false,{}, {_,_->})}}
        compose.onNodeWithText("Capa",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Descartar borrador").performScrollTo()
        compose.onNodeWithText("Tarea",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Nota",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Capa",useUnmergedTree=true).assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER,draft.purpose)}
    }
    @Test fun descriptionNormalWeightDoesNotInheritEmphasisAndBoldRemainsDistinct() {
        compose.setContent { Arachn0deTheme {
            AttachmentText("Normal **destacado**", style = androidx.compose.ui.text.TextStyle(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        } }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Normal destacado").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val input = layouts.single().layoutInput
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, input.style.fontWeight)
        assertTrue(input.text.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
    }
    @Test @Config(qualifiers = "w600dp-h800dp") fun wideDatesShareRow() {
        dates(1f)
        val start = compose.onNodeWithTag("option:Inicio").fetchSemanticsNode().boundsInRoot
        val due = compose.onNodeWithTag("option:Vencimiento").fetchSemanticsNode().boundsInRoot
        assertEquals(start.top, due.top, 1f); assertTrue(start.right < due.left)
    }
    @Test @Config(qualifiers = "w320dp-h480dp") fun narrowLargeFontDatesStackWithoutOverflow() {
        dates(1.8f)
        val start = compose.onNodeWithTag("option:Inicio").fetchSemanticsNode().boundsInRoot
        val due = compose.onNodeWithTag("option:Vencimiento").fetchSemanticsNode().boundsInRoot
        assertTrue(due.top >= start.bottom)
        compose.onNodeWithTag("option:Inicio").assertIsDisplayed().performClick()
        compose.onNodeWithText("Inicio: Sin fecha").assertIsDisplayed()
    }
}
