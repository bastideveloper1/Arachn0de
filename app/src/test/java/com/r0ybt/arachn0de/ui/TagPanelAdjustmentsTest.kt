package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.Tag
import com.r0ybt.arachn0de.domain.model.TagState
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagPanelAdjustmentsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun allTagsUsesOneThemedControlAndKeepsClearSelectionAcrossPalettes() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val preferences=com.r0ybt.arachn0de.ui.theme.AppearancePreferences(context.getSharedPreferences("tag-filter-${java.util.UUID.randomUUID()}",0))
        var selected by mutableStateOf(setOf("a"))
        compose.setContent {Arachn0deTheme(preferences) {Box(Modifier.width(320.dp)) {
            TagPanel(TagState(listOf(Tag("a","Consulta","consulta"))),null,selected=selected,onChange={selected=it},single=true,onClose={})
        }}}
        for(theme in com.r0ybt.arachn0de.ui.theme.AppearanceTheme.entries.filter {it.palette.light || it.name in setOf("Arachn0de","Dark","GreenBash")}) {
            compose.runOnIdle {preferences.setTheme(theme);selected=setOf("a")}
            compose.onNodeWithTag("tag-filter-all").assertIsDisplayed().assertIsEnabled()
            val bounds=compose.onNodeWithTag("tag-filter-all").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.height>=48*compose.density.density-1)
            compose.onNodeWithTag("tag-filter-all").performClick()
            compose.runOnIdle {assertTrue(selected.isEmpty())}
            compose.onNodeWithTag("tag-filter-all").assertIsNotEnabled()
        }
    }
    @Test fun shortCatalogWrapsContentAndLongCatalogScrollsAboveFooter() {
        var tags by mutableStateOf(listOf(Tag("a","Uno","uno"),Tag("b","Dos","dos")))
        compose.setContent {Arachn0deTheme {Box(Modifier.width(320.dp).heightIn(max=500.dp)) {
            TagPanel(TagState(tags),null,onClose={})
        }}}
        val short=compose.onNodeWithTag("tag-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Compact panel height: ${short.height}",short.height<240*compose.density.density)
        val chips=listOf("a","b").map {compose.onNodeWithTag("tag-choice:$it").fetchSemanticsNode().boundsInRoot}
        assertEquals(chips[0].top,chips[1].top,1f)
        chips.forEach {assertTrue(it.height>=48*compose.density.density-1)}
        compose.runOnIdle {tags=(0 until 200).map {Tag("$it","Etiqueta $it","etiqueta $it")}}
        compose.onNodeWithTag("tag-choice:199").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Listo").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("tag-panel").fetchSemanticsNode().boundsInRoot.height<=500*compose.density.density)
    }

    @Test fun assignmentChipsHaveCompactCloseAndKeepSelectionOnReopen() {
        var selected by mutableStateOf(emptySet<String>())
        compose.setContent {Arachn0deTheme {Box(Modifier.width(320.dp)) {
            TagSelector(listOf(Tag("a","Consulta","consulta")),selected,{selected=it})
        }}}
        compose.onNodeWithText("Etiquetas: Ninguna").performClick()
        compose.onNodeWithText("Cancelar").assertDoesNotExist()
        compose.onNodeWithTag("tag-choice:a").performClick()
        compose.onNodeWithTag("tag-panel").assertDoesNotExist()
        compose.onNodeWithText("Etiquetas: Consulta").performClick()
        compose.onNodeWithTag("tag-choice:a").assertIsSelected().performClick()
        compose.runOnIdle {assertTrue(selected.isEmpty())}
        compose.onNodeWithTag("tag-panel").assertDoesNotExist()
    }
    @Test fun draftFooterOnlyOffersSaveForChangedSelection() {
        val original=setOf("a","b")
        var selected by mutableStateOf(original)
        var closed=false
        compose.setContent {Arachn0deTheme {Box(Modifier.width(320.dp).heightIn(max=500.dp)) {
            val changed=selected!=original
            TagPanel(TagState(listOf(Tag("a","Uno","uno"),Tag("b","Dos","dos"))),null,selected,{selected=it},
                onCancel={},pendingSelection=changed,doneLabel=if(changed) "Guardar" else "Cerrar",onClose={closed=true})
        }}}
        compose.onNodeWithText("Guardar").assertDoesNotExist()
        compose.onNodeWithText("Cancelar").assertDoesNotExist()
        compose.onNodeWithTag("tag-choice:a").performClick()
        compose.onNodeWithText("Guardar").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithText("Eliminar").assertDoesNotExist()
        compose.runOnIdle {selected=original}
        compose.onNodeWithText("Cerrar").performClick()
        compose.runOnIdle {assertTrue(closed);assertEquals(original,selected)}
    }

    @Test fun technologiesOnlyRemainsInContentAndOtherOptionsStayAvailable() {
        var context by mutableStateOf<CoreBottomContext?>(CoreBottomContext.Options)
        var edited=false
        compose.setContent {Arachn0deTheme {CoreBottomModule(context,{context=it},400.dp,
            contextActions={
                action("Tecnologías",Icons.Default.Code,{edited=true})
                action("Otra opción",Icons.Default.Info,{})
            },creationActions={})}}
        compose.onNodeWithText("Tecnologías").assertDoesNotExist()
        compose.onNodeWithText("Otra opción").assertIsDisplayed()
        compose.runOnIdle {context=CoreBottomContext.Content}
        compose.onNodeWithText("Tecnologías").assertIsDisplayed().performClick()
        compose.runOnIdle {assertTrue(edited)}
    }
    @Test fun multipleSelectionCanReopenAndAddAnotherTagWithoutReplacingTheFirst() {
        var selected by mutableStateOf(emptySet<String>())
        compose.setContent {Arachn0deTheme {TagSelector(listOf(Tag("a","Uno","uno"),Tag("b","Dos","dos")),selected,{selected=it},singleAssignment=false)}}
        compose.onNodeWithText("Etiquetas: Ninguna").performClick()
        compose.onNodeWithTag("tag-choice:a").performClick()
        compose.onNodeWithTag("tag-panel").assertDoesNotExist()
        compose.onNodeWithText("Etiquetas: Uno").performClick()
        compose.onNodeWithTag("tag-choice:b").performClick()
        compose.runOnIdle {assertEquals(setOf("a","b"),selected)}
        compose.onNodeWithTag("tag-panel").assertDoesNotExist()
        compose.onNodeWithText("Etiquetas: Uno, Dos").performClick()
        compose.onNodeWithTag("tag-choice:a").performClick()
        compose.runOnIdle {assertEquals(setOf("b"),selected)}
    }

}
