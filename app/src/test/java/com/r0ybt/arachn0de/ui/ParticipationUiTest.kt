package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.room.withTransaction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.data.local.PersonEntity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class ParticipationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
        }
        compose.waitForIdle();runBlocking {app.database.withTransaction {}}
        // The application owns the database; closing it here races its flow collectors.
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String)=compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun click(text:String) {await(text);val node=compose.onNodeWithText(text);if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo();node.performClick()}
    private fun scroll(text:String)=compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("participation-list").performScrollToNode(hasText(text))}.isSuccess}
    @Test fun selectExistingPersonAssignContextRoleAndConfirmWithdrawalKeepsPerson() {
        val id=runBlocking {
            val project=app.projectRepository.createProject("Managed participants")
            app.database.personDao().insert(PersonEntity("existing","Existing Person",null))
            app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
        }
        compose.setContent {Arachn0deTheme {ParticipationDialog(id,app.nodeRepository.management.participation) {}}}
        click("Incorporar Personas");compose.onNodeWithText("Buscar Personas existentes").performTextReplacement("Existing")
        scroll("Existing Person");click("Existing Person")
        compose.waitUntil(15000) {pump();runBlocking {app.database.participationDao().participants().size==1}}
        await("Incorporada · Sin responsabilidad asignada");compose.onNodeWithText("Existing Person").assertIsSelected();click("Existing Person")
        scroll("Lead");click("Lead")
        compose.waitUntil(15000) {pump();runBlocking {app.database.participationDao().roles().size==1}}
        scroll("Quitar del equipo");click("Quitar del equipo");await("Confirmar retirada")
        click("Cancelar");runBlocking {assertEquals(1,app.database.participationDao().participants().size)}
        click("Quitar del equipo");click("Confirmar retirada")
        compose.waitUntil(15000) {pump();runBlocking {app.database.participationDao().participants().isEmpty()}}
        runBlocking {assertNotNull(app.database.personDao().get("existing"));assertTrue(app.database.participationDao().roles().isEmpty())}
    }
    @Test fun contextualObjectiveEditorWorksWithLargeFontsAndDoesNotValidateCriterion() {
        val name="Persona con un nombre muy extenso ".repeat(5)
        val data=runBlocking {
            val project=app.projectRepository.createProject("Contextual")
            val id=app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.HYBRID)!!.id
            app.database.personDao().insert(PersonEntity("existing",name,null,5f,.5f,-.5f))
            app.nodeRepository.management.participation.add(id,"existing")
            val goal=app.nodeRepository.management.planning.createObjective(id,"Objetivo extenso ".repeat(10))
            val criterion=app.nodeRepository.management.planning.createCriterion(goal.id,"Explicit")
            Triple(id,goal.id,criterion.id)
        }
        compose.setContent {Arachn0deTheme {val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,1.6f)) {ParticipationDialog(data.first,app.nodeRepository.management.participation,data.second,true) {}}}}
        scroll(name);click(name);scroll("Validador");click("Validador")
        compose.waitUntil(15000) {pump();runBlocking {app.database.participationDao().objectives().size==1}}
        runBlocking {assertFalse(app.database.planningDao().criterion(data.third)!!.validated);assertEquals("existing",app.database.participationDao().objectives().single().personId)}
        scroll("Detalle de Persona");click("Detalle de Persona");compose.onNodeWithTag("person-name:existing").assertExists()
    }
    @Test fun managementAndOriginalTaskExposeScopedResponsibilitiesWithoutGlobalTab() {
        runBlocking {
            val project=app.projectRepository.createProject("Participants entry")
            app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)
            app.nodeRepository.createNode(project.id,null,"Original task")
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        click("Participants entry")
        compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Acta de constitución"))}.isSuccess};click("Acta de constitución")
        compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Equipo"))}.isSuccess};click("Equipo")
        await("Sin participantes. Incorpora Personas del catálogo para formar el equipo.")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("Añadir sección")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Original task"))}.isSuccess};click("Original task")
        click("Responsabilidades de gestión")
        await("Sin participantes. Incorpora Personas del catálogo para formar el equipo.")
        runBlocking {assertTrue(app.database.participationDao().participants().isEmpty())}
    }

    @Test fun pickerKeepsMembersVisibleAndIncorporatesSeveralPeople() {
        val context=runBlocking {
            val project=app.projectRepository.createProject("Team selection")
            app.database.personDao().insert(PersonEntity("one","Persona Uno",null))
            app.database.personDao().insert(PersonEntity("two","Persona Dos",null))
            app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
        }
        compose.setContent {Arachn0deTheme {ParticipationDialog(context,app.nodeRepository.management.participation) {}}}
        click("Incorporar Personas");scroll("Persona Uno");click("Persona Uno")
        compose.waitUntil(15000) {pump();runBlocking {app.database.participationDao().participants().size==1}}
        compose.onNodeWithText("Persona Uno").assertIsSelected()
        scroll("Persona Dos");click("Persona Dos")
        compose.waitUntil(15000) {pump();runBlocking {app.database.participationDao().participants().size==2}}
        compose.onNodeWithText("Persona Uno").assertExists()
        compose.onNodeWithText("Persona Dos").assertIsSelected()
        click("Persona Uno");await("Roles generales del proyecto")
        runBlocking {assertEquals(2,app.database.participationDao().participants().size)}
        click("Volver al equipo");click("Volver al equipo")
        await("Equipo");compose.onNodeWithText("Persona Uno").assertExists();compose.onNodeWithText("Persona Dos").assertExists()
    }
    @Test fun peopleIndicatorShowsAvatarCardsAndOpensResponsibilitiesDirectly() {
        val context=runBlocking {
            val project=app.projectRepository.createProject("People indicator")
            app.database.personDao().insert(PersonEntity("member","Participante visual",null))
            val id=app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
            app.nodeRepository.management.participation.add(id,"member")
            id
        }
        compose.setContent {Arachn0deTheme {CardIndicatorDetail("project:indicator",{},managementContext=context,participationRepository=app.nodeRepository.management.participation)}}
        compose.waitUntil(15000) {pump();compose.onAllNodesWithContentDescription("Personas").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Personas").performClick()
        await("Participante visual")
        compose.onNodeWithContentDescription("Participante visual").assertExists()
        compose.onNodeWithText("Participante visual").assertHasClickAction().performTouchInput {click(androidx.compose.ui.geometry.Offset(width-4f,height/2f))}
        await("Roles generales del proyecto")
        compose.onNodeWithText("Responsabilidades sobre el trabajo").assertExists()
    }

    @Test fun portraitGridHasThreeColumnsSquarePhotosAndNamesBelow() {
        val context=runBlocking {
            val project=app.projectRepository.createProject("Portrait layout")
            repeat(16) {app.database.personDao().insert(PersonEntity("portrait$it","Persona ${it.toString().padStart(2,'0')} nombre muy largo ".repeat(4),null))}
            app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
        }
        compose.setContent {Arachn0deTheme {ParticipationDialog(context,app.nodeRepository.management.participation) {}}}
        click("Incorporar Personas")
        compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("person-photo:portrait2",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val photos=(0..3).map {compose.onNodeWithTag("person-photo:portrait$it",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot}
        assertEquals(photos[0].top,photos[1].top,1f);assertEquals(photos[1].top,photos[2].top,1f)
        assertTrue(photos[0].left<photos[1].left && photos[1].left<photos[2].left)
        assertTrue(photos[3].top>photos[0].bottom)
        photos.forEach {assertEquals(it.width,it.height,1f)}
        val name=compose.onNodeWithTag("person-name:portrait0",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(name.top>=photos[0].bottom)
        compose.onNodeWithTag("participation-list",useUnmergedTree=true).performScrollToNode(hasTestTag("person-photo:portrait9"))
        compose.onNodeWithTag("person-photo:portrait9",useUnmergedTree=true).assertIsDisplayed()
    }

}
