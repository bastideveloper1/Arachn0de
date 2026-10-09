package com.r0ybt.arachn0de.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w320dp-h640dp")
class CompactDefaultsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun allExistingControlsRemainAndRelatedSelectorsShareAvailableWidth() {
        val app=compose.activity.application as Arachn0deApplication
        val project=runBlocking {app.projectRepository.createProject("Proyecto")}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {CreationDefaultsScreen(app.nodeRepository.creationDefaults,DefaultsScope.Project(project.id),project.name,emptyList(),emptyList()) {}}}}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("defaults-choice:Tipo").fetchSemanticsNodes().isNotEmpty()}
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("defaults-choice:Tipo").performScrollTo()
        val type=compose.onNodeWithTag("defaults-choice:Tipo").fetchSemanticsNode().boundsInRoot
        val payment=compose.onNodeWithTag("defaults-choice:Obligación").fetchSemanticsNode().boundsInRoot
        assertTrue(type.width<root.width*.6f);assertTrue(payment.left>type.left);assertTrue(payment.right<=root.right)
        listOf("Tipo","Obligación","Moneda","Prioridad","Etiquetas","Responsables","Inicio","Hora de inicio","Vencimiento","Hora de vencimiento").forEach {label->
            val node=compose.onNodeWithTag("defaults-choice:$label").performScrollTo();val bounds=node.fetchSemanticsNode().boundsInRoot
            assertTrue(label,bounds.left>=root.left && bounds.right<=root.right)
        }
        compose.onNodeWithText("Guardar valores predeterminados").assertIsDisplayed()
    }
}
