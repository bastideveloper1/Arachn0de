package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NodeCardLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val task = Node("task", "project", null, "Título principal", "Descripción visible", false, 0, 0, 0, false)

    private fun render(node: Node, width: Int, metadata: Boolean) {
        compose.setContent {
            Box(Modifier.width(width.dp)) {
                NodeCard(node, null, false, true, {}, {}, {}, false, false, { _, done -> done() }, {},
                    responsiblePeople = if (metadata) listOf(Person("person", "Ana")) else emptyList(),
                    tags = if (metadata) listOf(Tag("tag", "Etiqueta larga que debe poder ajustarse", "etiqueta")) else emptyList(),
                    onAdvanceWorkState = {})
            }
        }
    }

    @Test fun smallCardKeepsMetadataInsideItsBounds() {
        render(task.copy(priority = Priority.HIGH, dueAt = 86400000, workState = WorkState.DOING), 240, true)
        compose.onNodeWithText(task.title).assertIsDisplayed()
        compose.onNodeWithText(task.description).assertIsDisplayed()
        val card = compose.onNode(hasClickAction() and hasText(task.title)).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(task.title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val check = compose.onNodeWithContentDescription("Confirmar Haciendo y avanzar a Terminada: ${task.title}").fetchSemanticsNode().boundsInRoot
        assertTrue(title.left >= check.right)
        assertEquals(card.center.y, check.center.y, 0.5f)
        assertEquals(card.left + with(compose.density) { 36.dp.toPx() }, check.center.x, 0.5f)
        for (node in listOf(compose.onNodeWithText("Haciendo", useUnmergedTree = true), compose.onNodeWithText("Prioridad alta", useUnmergedTree = true),
            compose.onNodeWithText("Etiqueta larga que debe poder ajustarse", useUnmergedTree = true), compose.onNodeWithContentDescription("Ana", useUnmergedTree = true),
            compose.onNodeWithText("Próxima", substring = true, useUnmergedTree = true))) {
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Metadato fuera de la tarjeta: $bounds / $card", bounds.left >= title.left && bounds.right <= card.right && bounds.bottom <= card.bottom)
        }
    }

    @Test fun avatarAppearsBelowPriorityWithoutSprintOrTags() {
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                NodeCard(task.copy(priority = Priority.HIGH), null, false, true, {}, {}, {}, false, false,
                    { _, done -> done() }, {}, responsiblePeople = listOf(Person("person", "Ana")))
            }
        }
        val avatar = compose.onNodeWithContentDescription("Ana", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val priority = compose.onNodeWithText("Prioridad alta", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(avatar.top >= priority.bottom)
        assertEquals(priority.left, avatar.left, 0.5f)
    }
    @Test fun tagsAndParticipantsShareBottomRowWithMarginAndSummary() {
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                NodeCard(task, null, false, true, {}, {}, {}, false, false,
                    { _, done -> done() }, {},
                    tags = listOf(Tag("tag", "UI", "ui")),
                    responsiblePeople = (1..4).map { Person("$it", "Persona $it") })
            }
        }
        val tag = compose.onNodeWithText("UI", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val avatar = compose.onNodeWithContentDescription("Persona 1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(task.title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = compose.onNode(hasClickAction() and hasText(task.title)).fetchSemanticsNode().boundsInRoot
        assertTrue(tag.top < avatar.bottom && tag.bottom > avatar.top)
        assertTrue(avatar.left >= tag.right)
        assertTrue(tag.left >= title.left && avatar.left >= title.left)
        assertTrue(card.bottom > avatar.bottom)
        compose.onNodeWithText("+1", useUnmergedTree = true).assertIsDisplayed()
    }

}
