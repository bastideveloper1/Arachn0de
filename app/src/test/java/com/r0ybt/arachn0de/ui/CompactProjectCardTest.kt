package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompactProjectCardTest {
    @get:Rule val compose = createComposeRule()
    private val project = Project("p", "Proyecto", "Descripción secundaria", 0, 0)
    @Test fun emptyProjectUsesOneCompactRowWithAComfortableTouchArea() {
        var density = 1f
        compose.setContent { Arachn0deTheme {
            density = LocalDensity.current.density
            ProjectCard(project.copy(description = ""), ProjectCardAlerts(), {}, {}, {})
        } }
        compose.onNodeWithText(project.name).assertIsDisplayed()
        compose.onNodeWithText("prioritaria", substring = true).assertDoesNotExist()
        compose.onNodeWithText("vence", substring = true).assertDoesNotExist()
        val height = compose.onNodeWithTag("project-card:p").fetchSemanticsNode().boundsInRoot.height / density
        assertTrue(height >= 48f && height <= 56.1f)
    }
    @Test fun noAlertsReserveNoExtraRowAndSecondaryDataIsAbsent() {
        var alerts by mutableStateOf(ProjectCardAlerts())
        compose.setContent { Arachn0deTheme { ProjectCard(project, alerts, {}, {}, {}) } }
        val original = compose.onNodeWithTag("project-card:p").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText("Proyecto").assertIsDisplayed()
        compose.onNodeWithText(project.description).assertDoesNotExist()
        compose.onNodeWithText("No hay tareas por realizar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tecnologías de Proyecto").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del proyecto").assertIsDisplayed()
        compose.runOnIdle { alerts = ProjectCardAlerts(3, 2) }
        compose.onNodeWithText("3 prioritarias").assertIsDisplayed()
        compose.onNodeWithText("2 vencen hoy").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("project-card:p").fetchSemanticsNode().boundsInRoot.height >= original)
        compose.runOnIdle { alerts = ProjectCardAlerts() }
        assertEquals(original, compose.onNodeWithTag("project-card:p").fetchSemanticsNode().boundsInRoot.height, 0.1f)
    }
    @Test fun singleAlertsAndMenusRemainAccessibleWithoutOpeningTheProject() {
        var opened = false; var edited = false
        var alerts by mutableStateOf(ProjectCardAlerts(1, 0))
        compose.setContent { Arachn0deTheme { ProjectCard(project, alerts, { opened = true }, { edited = true }, {}) } }
        compose.onNodeWithText("1 prioritaria").assertIsDisplayed()
        compose.onNodeWithText("vence hoy", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.onNodeWithContentDescription("Editar proyecto").performClick()
        compose.runOnIdle { assertTrue(edited); assertFalse(opened); alerts = ProjectCardAlerts(0, 1) }
        compose.onNodeWithText("1 vence hoy").assertIsDisplayed()
        compose.onNodeWithText("1 prioritaria").assertDoesNotExist()
        compose.onNodeWithText(project.name).performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
    @Test @Config(qualifiers = "w320dp-h600dp") fun completeLongTitleAndAlertsWrapWithLargeFont() {
        val long = "Proyecto con título largo para comprobar todo el ancho útil"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                Arachn0deTheme { Box(Modifier.fillMaxWidth().testTag("available")) {
                    ProjectCard(project.copy(name = long), ProjectCardAlerts(12, 20), {}, {}, {})
                } }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("project-title:p", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(layout.lineCount > 2)
        assertFalse(layout.hasVisualOverflow)
        assertEquals(long.length, layout.getLineEnd(layout.lineCount - 1))
        compose.onNodeWithText("12 prioritarias").assertIsDisplayed()
        compose.onNodeWithText("20 vencen hoy").assertIsDisplayed()
        val card = compose.onNodeWithTag("project-card:p").fetchSemanticsNode().boundsInRoot
        val available = compose.onNodeWithTag("available").fetchSemanticsNode().boundsInRoot
        assertTrue(card.left >= available.left && card.right <= available.right)
    }
}
