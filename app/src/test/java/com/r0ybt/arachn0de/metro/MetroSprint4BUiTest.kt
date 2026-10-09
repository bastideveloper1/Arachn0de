package com.r0ybt.arachn0de.metro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class MetroSprint4BUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    private fun screen() {compose.mainClock.autoAdvance=true;compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}};await {exists("Líneas")}}
    private fun choose(query:String,name:String) {compose.mainClock.autoAdvance=true;await {exists("Buscar estación")};compose.onNodeWithText("Buscar estación").performTextInput(query);compose.mainClock.advanceTimeBy(100);compose.onNode(hasText(name) and hasAnyAncestor(isDialog())).performClick().also {compose.mainClock.advanceTimeBy(100)}}
    private suspend fun active() {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.network
        val id=repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity));val j=repo.snapshot().journeys.single()
        val now=metroTime(compose.activity)
        repo.tracking(id,j.row.revision) {MetroTracking.pause(MetroTracking.confirm(it,"los-heroes",now,between=true),now)}
    }
    @Test fun planFieldsAreVerticalSelectedAndIntermediateStopsAndPersonRemainAccessible() {
        screen();compose.onNodeWithText("Planificar").performClick().also {compose.mainClock.advanceTimeBy(100)}
        compose.onNodeWithText("Origen: Elegir estación").assertExists();compose.onNodeWithText("Destino: Elegir estación").assertExists()
        val origin=compose.onNodeWithTag("metro-field:Origen").fetchSemanticsNode().boundsInRoot
        val destination=compose.onNodeWithTag("metro-field:Destino").fetchSemanticsNode().boundsInRoot
        assertTrue(destination.top>origin.bottom)
        compose.onNodeWithTag("metro-field:Origen").performClick().also {compose.mainClock.advanceTimeBy(100)};choose("los heroes","Los Héroes")
        compose.onNodeWithTag("metro-field:Destino").performClick().also {compose.mainClock.advanceTimeBy(100)};choose("republica","República")
        compose.onAllNodesWithText("Seleccionada · tocar para modificar").assertCountEquals(2)
        compose.onNodeWithText("+ Agregar parada").performClick().also {compose.mainClock.advanceTimeBy(100)};choose("santa ana","Santa Ana")
        compose.onNodeWithText("Parada 1: Santa Ana").assertExists()
        compose.onNodeWithText("Persona: Sin Persona").performClick().also {compose.mainClock.advanceTimeBy(100)};compose.onNodeWithText("Persona").assertExists()
        compose.onNodeWithText("Sin Persona").performClick().also {compose.mainClock.advanceTimeBy(100)};compose.onNodeWithText("Parada 1: Santa Ana").assertExists()
    }
    @Test fun activeMapExpansionKeepsSessionAndSeparatesCenterFromExpandAndArrival()=runBlocking<Unit> {
        active();screen();await {exists("Reanudar")};val before=app.metroRepository.snapshot()
        val expand=compose.onNodeWithContentDescription("Ampliar mapa").fetchSemanticsNode().boundsInRoot
        val center=compose.onNodeWithText("Centrar avatar").fetchSemanticsNode().boundsInRoot
        assertTrue(expand.left>center.right)
        val normal=compose.onNodeWithTag("metro-timeline").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithContentDescription("Ampliar mapa").performClick().also {compose.mainClock.advanceTimeBy(100)}
        compose.onNodeWithText("Inicio").assertDoesNotExist();compose.onNodeWithText("Reanudar").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("metro-timeline").fetchSemanticsNode().boundsInRoot.height>normal)
        compose.onNodeWithText("Los Héroes").performClick().also {compose.mainClock.advanceTimeBy(100)}
        compose.onNodeWithText("Estoy aquí").assertExists();compose.onNodeWithText("Cambiar destino").assertExists()
        compose.onNodeWithText("Usar como origen").assertDoesNotExist();compose.onNodeWithText("Usar como destino").assertDoesNotExist()
        compose.onNodeWithText("Estoy entre estaciones (desde aquí, aproximado)").assertDoesNotExist()
        compose.onNodeWithText("Cerrar").performClick().also {compose.mainClock.advanceTimeBy(100)};compose.onNodeWithContentDescription("Contraer mapa").performClick().also {compose.mainClock.advanceTimeBy(100)}
        compose.onNodeWithTag("metro-row-0").assertIsSelected()
        compose.onNodeWithContentDescription("Ampliar mapa").performClick().also {compose.mainClock.advanceTimeBy(100)}
        compose.onNodeWithTag("metro-row-0").assertIsSelected()
        compose.onNodeWithContentDescription("Contraer mapa").performClick().also {compose.mainClock.advanceTimeBy(100)}
        assertEquals(before,app.metroRepository.snapshot())
    }
    @Test fun destinationCancelDoesNotWriteAndConfirmKeepsPausedSessionAwaitingBoarding()=runBlocking<Unit> {
        active();screen();await {exists("Reanudar")};val before=app.metroRepository.snapshot()
        compose.onNodeWithText("Cambiar destino").performClick().also {compose.mainClock.advanceTimeBy(100)};choose("tobalaba","Tobalaba")
        await {exists("Confirma ubicación actual")};choose("los heroes","Los Héroes")
        await {exists("Confirmar cambio de destino")};compose.onNodeWithText("Cancelar").performClick().also {compose.mainClock.advanceTimeBy(100)}
        assertEquals(before,app.metroRepository.snapshot())
        compose.onNodeWithText("Cambiar destino").performClick().also {compose.mainClock.advanceTimeBy(100)};choose("tobalaba","Tobalaba")
        await {exists("Confirma ubicación actual")};choose("los heroes","Los Héroes")
        await {exists("Confirmar cambio de destino")};compose.onNodeWithText("Confirmar cambio").performClick().also {compose.mainClock.advanceTimeBy(100)}
        await {runBlocking {app.metroRepository.snapshot().journeys.single().data.active!!.control!!.phase==MetroPhase.READY}}
        val after=app.metroRepository.snapshot().journeys.single().data.active!!
        assertEquals(before.journeys.single().data.active!!.id,after.id);assertNotNull(after.pausedAt)
        compose.onNodeWithText("Reanudar").performClick().also {compose.mainClock.advanceTimeBy(100)};await {exists("Comenzar nueva etapa")}
        compose.onNodeWithText("Comenzar nueva etapa").performClick().also {compose.mainClock.advanceTimeBy(100)};await {exists("Pausar")}
        assertEquals(MetroPhase.RIDING,app.metroRepository.snapshot().journeys.single().data.active!!.control!!.phase)
    }
    @Test fun inactiveScheduleIsHiddenAndPlanningStationActionsAreContextual() {
        screen();compose.onNodeWithText("Horarios y tipos de servicio ▾").assertDoesNotExist()
        compose.onNodeWithText("Líneas").performClick().also {compose.mainClock.advanceTimeBy(100)};compose.onNodeWithText("San Pablo").performClick().also {compose.mainClock.advanceTimeBy(100)}
        compose.onNodeWithText("Usar como origen").assertExists();compose.onNodeWithText("Usar como destino").assertExists()
        compose.onNodeWithText("Estoy aquí").assertDoesNotExist();compose.onNodeWithText("Cambiar destino").assertDoesNotExist()
    }
    @Test fun fieldEditingAndInvalidStatesHaveTextAndIconsAsWellAsColor() {
        compose.setContent {Arachn0deTheme {Column {StationField("Origen",null) {};StationField("Destino","Cerrada",editing=true,invalid=true) {}}}}
        compose.onNodeWithText("Pendiente").assertExists();compose.onNodeWithText("Estación cerrada: elige otra").assertExists()
        compose.onNodeWithContentDescription("Modificar Destino").assertExists()
    }
    @Test fun actualStationCardsProvidePaletteForegroundForEveryThemeAndOverlappingState()=runBlocking<Unit> {
        val net=app.metroRepository.snapshot().preferences.network
        val line=net.lines.getValue("L5")
        val ids=listOf("R","V","C").map {classification->line.stations[line.express.indexOf(classification)]}
        var theme by androidx.compose.runtime.mutableStateOf(com.r0ybt.arachn0de.ui.theme.AppearanceTheme.Minimalist)
        val inherited=mutableMapOf<Int,androidx.compose.ui.graphics.Color>()
        compose.setContent {Arachn0deTheme {
            androidx.compose.runtime.CompositionLocalProvider(com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette provides theme.palette) {
                MetroVerticalTimeline(ids.map {MetroTimelineNode(it,"L5",emphasized=true)},net,progress=1 to .5f,selectedStation=ids[1],onStation={},extra={index->
                    val color=androidx.compose.material3.LocalContentColor.current
                    androidx.compose.runtime.SideEffect {inherited[index]=color}
                })
            }
        }}
        for(next in com.r0ybt.arachn0de.ui.theme.AppearanceTheme.entries) {
            compose.runOnIdle {inherited.clear();theme=next}
            try {await {inherited.size==3 && inherited.values.all {it==next.palette.text}}}
            catch(e:Throwable) {println("Theme ${next.name}: inherited=$inherited; expected=${next.palette.text}");throw e}
        }
    }

}
