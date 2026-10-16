package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Subject
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w360dp-h800dp")
class ModularPanelTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun compactMenuUsesHorizontalContentAndFallsBackForLargeFonts() {
        var large by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large) 2f else 1f)) {
                Box(Modifier.width(340.dp)) {
                    ModularFieldMenu(listOf("Cualidades","Habilidades","Intereses","Disponibilidad","Fecha de nacimiento").mapIndexed {i,label->
                        ModularPanelOption(label,androidx.compose.material.icons.Icons.Default.Subject,"compact:$i") {}
                    },compact=true)
                }
            }
        }}
        val first=compose.onNodeWithTag("compact:0").fetchSemanticsNode().boundsInRoot
        val second=compose.onNodeWithTag("compact:1").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top,second.top,1f)
        compose.onNodeWithTag("compact:0").assertHeightIsAtLeast(48.dp).assertHeightIsEqualTo(48.dp)
        val icon=compose.onNodeWithTag("compact:0:icon",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val label=compose.onNodeWithText("Cualidades",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(icon.right<label.left)
        assertEquals(icon.center.y,label.center.y,1f)
        compose.runOnIdle {large=true}
        val enlargedFirst=compose.onNodeWithTag("compact:0").fetchSemanticsNode().boundsInRoot
        val enlargedSecond=compose.onNodeWithTag("compact:1").fetchSemanticsNode().boundsInRoot
        assertTrue(enlargedSecond.top>=enlargedFirst.bottom)
        compose.onNodeWithText("Fecha de nacimiento").assertIsDisplayed()
    }

    @Test fun textSectionsKeepConfirmedFieldsAndCancelOnlyTheCurrentBufferAcrossRecreation() {
        var saved:List<String>?=null
        val restore=StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {
            ModularTextFieldsEditor("profile","Editar Perfil",listOf("Cualidades","Habilidades","Intereses","Disponibilidad"),
                listOf("A","B","C","D"),"Guardar sección",{}) {saved=it}
        }}
        compose.onNodeWithTag("modular-field:0").assert(hasText("Cualidades")).performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Confirmado")
        compose.onNodeWithText("Confirmar").performClick()
        compose.onNodeWithText("Confirmado").assertDoesNotExist()
        compose.onNodeWithTag("modular-field:1").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Descartar")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("modular-field:2").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Provisional")
        compose.runOnUiThread {org.robolectric.shadows.ShadowDialog.getLatestDialog().onBackPressed()}
        compose.onNodeWithText("Guardar sección").assertIsNotEnabled()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("modular-field:2").performClick()
        compose.onNodeWithTag("modular-text-field").assert(hasText("Provisional"))
        compose.onNodeWithText("Cancelar").performClick()
        assertNull(saved)
        compose.onNodeWithText("Guardar sección").assertIsEnabled().performClick()
        assertEquals(listOf("Confirmado","B","C","D"),saved)
    }

    @Test fun boundedBodyScrollsWhileActionsStayVisibleWithLargeFonts() {
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.6f)) {
                Box(Modifier.width(320.dp).height(350.dp)) {
                    ModularPanelLayout(header={Text("Editar")},actions={
                        FlowRow {TextButton({}) {Text("Cancelar")};TextButton({}) {Text("Confirmar")}}
                    }) {
                        repeat(12) {i->OutlinedTextField("Valor $i",{},label={Text("Campo $i")},
                            modifier=modularPanelFieldModifier().testTag("field:$i"))}
                    }
                }
            }
        }}
        compose.onNodeWithText("Confirmar").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithTag("field:11").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirmar").assertIsDisplayed()
    }

    @Test fun availableHeightChangesKeepDraftAndReturnToNaturalHeight() {
        val available=mutableStateOf(650.dp)
        val restore=StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {
            var value by rememberSaveable {mutableStateOf("")}
            Box(Modifier.fillMaxWidth().height(available.value)) {
                ModularPanelSurface(Modifier.testTag("panel")) {
                    ModularPanelLayout(fieldOwnsScroll=true,header={Text("Descripción")},
                        actions={TextButton({}) {Text("Confirmar")}}) {
                        OutlinedTextField(value,{value=it},minLines=3,
                            modifier=modularPanelFieldModifier(autoFocus=true).testTag("description"))
                    }
                }
            }
        }}
        val small=compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot.height
        val text=(1..30).joinToString("\n") {"Línea $it"}
        compose.onNodeWithTag("description").assertIsFocused().performTextReplacement(text)
        compose.runOnIdle {available.value=350.dp}
        compose.onNodeWithText("Confirmar").assertIsDisplayed()
        compose.onNodeWithTag("description").assert(hasText(text))
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("description").assert(hasText(text))
        compose.runOnIdle {available.value=650.dp}
        compose.onNodeWithTag("description").performTextReplacement("")
        assertEquals(small,compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot.height,1f)
        compose.onNodeWithText("Confirmar").assertIsDisplayed()
    }

    @Test fun dialogCancellationReleasesPanelAndPreservesParentDraft() {
        var parent="Original"
        compose.setContent {Arachn0deTheme {
            var open by remember {mutableStateOf(false)}
            TextButton({open=true}) {Text("Editar propiedad")}
            if(open) {
                var provisional by rememberSaveable {mutableStateOf(parent)}
                ModularPanelDialog(onDismissRequest={open=false},title={Text("Propiedad")},
                    text={OutlinedTextField(provisional,{provisional=it},modifier=modularPanelFieldModifier().testTag("value"))},
                    confirmButton={TextButton({parent=provisional;open=false}) {Text("Confirmar")}},
                    dismissButton={TextButton({open=false}) {Text("Cancelar")}})
            }
        }}
        compose.onNodeWithText("Editar propiedad").performClick()
        compose.onNodeWithTag("value").performTextReplacement("Provisional")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("Original",parent)
        compose.onNodeWithTag("value").assertDoesNotExist()
        compose.onNodeWithText("Editar propiedad").performClick()
        compose.onNodeWithTag("value").assert(hasText("Original"))
        compose.onNodeWithTag("value").performTextReplacement("Confirmado")
        compose.onNodeWithText("Confirmar").performClick()
        assertEquals("Confirmado",parent)
    }

    @Test fun responsibleSelectionCancelsLocallyAndConfirmsOnlyTheChosenPeople() {
        var confirmed=setOf("a")
        compose.setContent {Arachn0deTheme {
            var open by remember {mutableStateOf(false)}
            TextButton({open=true}) {Text("Seleccionar")}
            if(open) ResponsibleDialog("draft",listOf(
                com.r0ybt.arachn0de.domain.model.Person("a","Ana"),
                com.r0ybt.arachn0de.domain.model.Person("b","Bea")),
                listOf(com.r0ybt.arachn0de.domain.model.Person("a","Ana")),false,{open=false}) {
                confirmed=it;open=false
            }
        }}
        compose.onNodeWithText("Seleccionar").performClick()
        compose.onNodeWithTag("person-choice:b").performClick()
        compose.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
        assertEquals(setOf("a"),confirmed)
        compose.onNodeWithText("Seleccionar").performClick()
        compose.onNodeWithTag("person-choice:b").performClick()
        compose.onNodeWithText("Guardar").assertIsDisplayed().performClick()
        assertEquals(setOf("a","b"),confirmed)
    }

    @Test fun nestedCalendarFilterSelectionReturnsToTheOpenPanel() {
        val day=com.r0ybt.arachn0de.domain.model.CalendarDay(2026,12,31)
        val state=com.r0ybt.arachn0de.ui.state.CalendarFilterState(day).apply {showFilters=true}
        compose.setContent {Arachn0deTheme {
            if(state.showFilters) CalendarFiltersDialog(state,day,
                listOf(com.r0ybt.arachn0de.domain.model.Person("a","Ana")),true)
        }}
        compose.onNodeWithTag("calendar-person-filter").performClick()
        compose.onNodeWithTag("calendar-person:a").performClick()
        assertEquals("a",state.personId)
        compose.onNodeWithText("Filtros del Calendario").assertIsDisplayed()
        compose.onNodeWithText("Listo").assertIsDisplayed().performClick()
        assertFalse(state.showFilters)
        assertEquals("a",state.personId)
    }
}
