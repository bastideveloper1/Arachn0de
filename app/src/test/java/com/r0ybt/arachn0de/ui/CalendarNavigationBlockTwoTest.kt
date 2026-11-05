package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.CalendarDay
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w411dp-h850dp")
class CalendarNavigationBlockTwoTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun birthdayAndActivityIndicatorsStayInsideTheirDay() {
        val day=CalendarDay(2026,10,29)
        val dates=java.text.DateFormat.getDateInstance().apply {timeZone=java.util.TimeZone.getTimeZone("UTC")}
        compose.setContent {Arachn0deTheme {
            CalendarWeekStrip(listOf(day),day,day,java.util.Locale.forLanguageTag("es"),null,dates,{},
                eventCounts=mapOf(day to 4),eventColors=mapOf(day to listOf("BLUE","BLUE","BLUE")),
                specialCounts=mapOf(day to 1),birthdayCounts=mapOf(day to 2))
        }}
        val cell=compose.onNodeWithTag("calendar-week-day:${day.key}")
        val bounds=cell.fetchSemanticsNode().boundsInRoot
        val cake=compose.onNodeWithTag("calendar-birthday-marker",useUnmergedTree=true).assertExists().fetchSemanticsNode().boundsInRoot
        assertTrue(cake.left>=bounds.left && cake.right<=bounds.right && cake.top>=bounds.top && cake.bottom<=bounds.bottom)
        compose.onAllNodesWithTag("calendar-event-dash",useUnmergedTree=true).assertCountEquals(3)
    }

    @Test fun collapsedIndicatorsFollowSelectedDayAndShareBirthdayIcon() {
        val today=CalendarDay(2026,10,29)
        val selected=CalendarDay(2026,10,30)
        val state=ContextCalendarState(today).apply {mode=0;this.selected=selected}
        val task=com.r0ybt.arachn0de.domain.model.Node("t","p",null,"Tarea","",false,0,0,0,false,
            dueAt=com.r0ybt.arachn0de.domain.model.CalendarDates.labelInstant(selected))
        val index=com.r0ybt.arachn0de.domain.model.ContextCalendarIndex(com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot(listOf(task)),java.util.TimeZone.getTimeZone("UTC"))
        val birthdays=com.r0ybt.arachn0de.calendar.CalendarBirthdays(listOf(com.r0ybt.arachn0de.domain.model.Person("person","Ana")),
            listOf(com.r0ybt.arachn0de.data.local.PersonBirthdayEntity("person","--10-30")))
        compose.setContent {Arachn0deTheme {ContextCalendar(state,index,today,birthdays=birthdays)}}
        compose.onNodeWithTag("context-calendar-collapsed-date").assertTextEquals("Viernes, 30 de octubre de 2026")
        compose.onNodeWithTag("context-collapsed-task-marker").assertExists()
        compose.onNodeWithTag("context-collapsed-birthday-marker").assertExists()
        compose.runOnIdle {state.selected=today}
        compose.onNodeWithTag("context-collapsed-task-marker").assertDoesNotExist()
        compose.onNodeWithTag("context-collapsed-birthday-marker").assertDoesNotExist()
    }

    @Test fun foldedCalendarIsOneBarAndScopesRetainTheirStateAcrossNavigationAndRestoration() {
        val today=CalendarDay(2026,10,28)
        val selected=CalendarDay(2024,2,29)
        val restore=StateRestorationTester(compose)
        var scope by mutableStateOf("project:one:root")
        lateinit var store:ContextCalendarStore
        restore.setContent {
            store=rememberContextCalendarStore()
            Arachn0deTheme {key(scope) {Box(Modifier.width(280.dp)) {ContextCalendar(store.scope(scope,today),null,today)}}}
        }
        compose.runOnIdle {store.scope(scope,today).apply {this.selected=selected;anchor=selected;mode=2;expanded=2;urgentOnly=true;favorites=true}}
        val expandedHeight=compose.onNodeWithTag("context-calendar").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("context-calendar-fold").assertIsDisplayed().performClick()
        compose.onNodeWithTag("context-calendar-collapsed-date").assertIsDisplayed()
        compose.onNodeWithTag("context-calendar-today").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-attention").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-mode").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-day:${selected.key}").assertDoesNotExist()
        val collapsedHeight=compose.onNodeWithTag("context-calendar").fetchSemanticsNode().boundsInRoot.height
        assertTrue(collapsedHeight<expandedHeight)
        assertTrue(collapsedHeight<=60*compose.density.density)
        val label=compose.onNodeWithTag("context-calendar-collapsed-date").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        assertEquals("Jueves, 29 de febrero de 2024",label)
        for(destination in listOf("project:one:layer", "project:two:root")) {
            compose.runOnIdle {scope=destination}
            compose.onNodeWithTag("context-calendar-collapsed-date").assertExists()
            compose.runOnIdle {scope="project:one:root"}
            compose.onNodeWithTag("context-calendar-collapsed-date").assertTextEquals(label)
            compose.runOnIdle {val state=store.scope(scope,today);assertEquals(selected,state.selected);assertTrue(state.urgentOnly);assertTrue(state.favorites)}
        }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("context-calendar-collapsed-date").assertTextEquals(label)
        compose.onNodeWithTag("context-calendar-fold").performClick()
        compose.runOnIdle {val state=store.scope(scope,today);assertEquals(2,state.mode);assertEquals(2,state.expanded);assertEquals(selected,state.selected)}
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-today").performClick()
        compose.runOnIdle {val state=store.scope(scope,today);assertEquals(today,state.anchor);assertEquals(today,state.selected);assertTrue(state.urgentOnly);assertTrue(state.favorites);assertEquals(2,state.mode)}
    }

    @Test fun existingDrawerKeepsEveryDestinationAndBottomCloseWithinLimitedSpaceAndInsets() {
        var bottom by mutableStateOf(0.dp)
        val routes=mutableListOf<String>()
        val groups=linkedMapOf(
            "Inicio" to listOf("Núcleos","Borradores","Metro","Buscar","Antisocial","Calendario","Tragaperras"),
            "Herramientas" to listOf("Diagramas","Wireframe","Pomodoro","Captura rápida"),
            "Organizar" to listOf("Tecnologías","Etiquetas","Pagos","Recurrencias","Estadísticas de Metro"),
            "Ajustes" to listOf("Apariencia","Seguridad y respaldos","Acerca de"))
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                fun route(label:String):()->Unit={routes+=label}
                Box(Modifier.requiredSize(320.dp,520.dp).testTag("drawer-viewport")) {
                    AppIdentityDrawer({routes+="dismiss"},onProjects=route("Núcleos"),onDrafts=route("Borradores"),
                        onMetro=route("Metro"),onSearch=route("Buscar"),onPeople=route("Antisocial"),onCalendar=route("Calendario"),onSlots=route("Tragaperras"),
                        onDiagrams=route("Diagramas"),onWireframe=route("Wireframe"),onPomodoro=route("Pomodoro"),onQuickCapture=route("Captura rápida"),
                        onTechnologies=route("Tecnologías"),onTags=route("Etiquetas"),onObligations=route("Pagos"),onRecurrences=route("Recurrencias"),onMetroStatistics=route("Estadísticas de Metro"),
                        onAppearance=route("Apariencia"),onSecurity=route("Seguridad y respaldos"),onAbout=route("Acerca de"),safeInsets=WindowInsets(top=12.dp,bottom=bottom+24.dp))
                }
            }
        }}
        for(inset in listOf(0.dp,180.dp)) {
            compose.runOnIdle {bottom=inset}
            for((category,entries) in groups) {
                compose.onNodeWithTag("menu-category:$category").assertIsDisplayed().performClick()
                val close=compose.onNodeWithTag("drawer-close-bottom").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val viewport=compose.onNodeWithTag("drawer-viewport").fetchSemanticsNode().boundsInRoot
                val panel=compose.onNodeWithTag("navigation-drawer").fetchSemanticsNode().boundsInRoot
                assertTrue(panel.top>=viewport.top)
                assertTrue(close.bottom<=viewport.bottom-(inset.value+24)*compose.density.density+1)
                for(entry in entries) {
                    val item=compose.onNode(hasText(entry) and hasClickAction()).performScrollTo().assertIsDisplayed()
                    val bounds=item.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.top>=panel.top && bounds.bottom<=close.top+1)
                    val before=routes.size
                    item.performClick()
                    assertEquals(listOf("dismiss",entry),routes.drop(before))
                }
            }
        }
    }

    @Test fun layerTitleKeepsIconAndFullTextWithoutRedundantLevelBadge() {
        var name by mutableStateOf("Capa breve")
        var depth by mutableStateOf(1)
        var fontScale by mutableStateOf(1f)
        var skin by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale),
                LocalAppearancePalette provides if(skin) AppearanceTheme.DayGrey.palette else BlackWhite90.palette) {
                Box(Modifier.width(280.dp).testTag("heading-bounds")) {DetailScopeTitle(name,compact=true,level=depth,layer=true)}
            }
        }}
        for(customSkin in listOf(false,true)) for(scale in listOf(1f,1.5f)) for(title in listOf("Capa breve","Título largo que conserva todas sus palabras y no contiene el número derivado de anidamiento")) for(level in listOf(1,12)) {
            compose.runOnIdle {name=title;depth=level;fontScale=scale;skin=customSkin}
            val titleNode=compose.onNodeWithTag("detail-scope-title").assertTextEquals(title)
            compose.onNodeWithTag("detail-scope-level").assertDoesNotExist()
            compose.onNodeWithTag("detail-scope-level-badge").assertDoesNotExist()
            compose.onNodeWithContentDescription("Capa",useUnmergedTree=true).assertIsDisplayed()
            val titleLayouts=mutableListOf<TextLayoutResult>()
            titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(titleLayouts)}
            assertFalse(titleLayouts.single().hasVisualOverflow)
            assertEquals(level,depth)
            assertEquals(title,name)
        }
    }
}
