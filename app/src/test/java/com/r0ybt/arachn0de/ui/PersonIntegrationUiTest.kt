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
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.theme.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class PersonIntegrationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            // A still-attached ComposeView can recreate its composition during measurement.
            content.removeAllViews()
        }
        pump()
        val application=app;val db=application.database
        // Let in-flight Room reads/transactions finish before closing the Robolectric pool.
        runBlocking {
            application.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {db.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        db.close()
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String)=compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun click(text:String) {await(text);val node=compose.onNodeWithText(text);if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo();compose.waitUntil(15000) {pump();runCatching {node.assertIsEnabled()}.isSuccess};node.performClick()}
    private fun scroll(text:String)=compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("participation-list").performScrollToNode(hasText(text))}.isSuccess}
    private fun section(text:String) {click(text)}
    private fun work(text:String) {compose.onNodeWithTag("person-work-list").performScrollToNode(hasText(text));compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("person-work-list"))).performClick()}
    @Test fun searchableCatalogOfFiveHundredUsesExistingRecordsAndContextualLevels() {
        runBlocking {app.personRepository.save("a","Ana",null);repeat(500) {app.database.technologyDao().insert(TechnologyEntity("t$it","Technology $it",null))}}
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository,initialPersonId="a") {}}}
        section("Tecnologías");click("Asociar Tecnología");await("Buscar Tecnologías");compose.onNodeWithText("Buscar Tecnologías").performTextReplacement("499");click("Technology 499");click("Experto");click("Guardar dominio")
        compose.waitUntil(15000) {pump();runBlocking {app.database.personIntegrationDao().technologies().size==1}}
        click("Technology 499");await("✓ Experto");click("Básico");click("Cancelar")
        click("Technology 499");await("✓ Experto");click("Retirar asociación");await("Sin Tecnologías asociadas.")
        runBlocking {assertTrue(app.database.personIntegrationDao().technologies().isEmpty());assertEquals(500,app.database.technologyDao().catalog().size)}
        val db=app.database
        runBlocking {db.withTransaction {repeat(500) {db.personIntegrationDao().save(PersonTechnologyEntity("a","t$it","UNEVALUATED"))}}}
        compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("person-technology-grid").performScrollToIndex(499)}.isSuccess}
        click("Technology 99");await("✓ Sin evaluar");click("Cancelar")
    }
    @Test fun workListMergesRolesFiltersCompletionAndOpensRealObjective() {
        var secondProject=""
        val goal=runBlocking {
            app.personRepository.save("a","Ana",null)
            val p=app.projectRepository.createProject("First");val q=app.projectRepository.createProject("Second");secondProject=q.id
            val task=app.nodeRepository.createNode(p.id,null,"Shared task");val done=app.nodeRepository.createNode(q.id,null,"Completed task");app.nodeRepository.setCompleted(done.id,true)
            app.personRepository.setResponsiblePeople(task.id,setOf("a"));app.personRepository.setResponsiblePeople(done.id,setOf("a"))
            val c=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val part=app.nodeRepository.management.participation;part.add(c,"a");part.assign(c,"a",task.id,false,ResponsibilityRole.EXECUTOR);part.assign(c,"a",task.id,false,ResponsibilityRole.VALIDATOR)
            val g=app.nodeRepository.management.planning.createObjective(c,"Exact objective");part.assign(c,"a",g.id,true,ResponsibilityRole.SUPERVISOR);g.id
        }
        var destination:PersonDestination?=null
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalPersonNavigation provides {destination=it}) {PeopleScreen(app.personRepository,initialPersonId="a",initialSection="Proyectos y trabajo") {}}}}
        await("Abrir proyecto: First");compose.onAllNodesWithText("Abrir proyecto: First").assertCountEquals(1)
        work("Shared task");compose.onAllNodesWithText("Shared task").assertCountEquals(1);await("Ejecutor · First");await("Validador · First");click("Shared task");assertNotNull(destination);assertEquals("a",destination!!.personId)
        click("Estado: Todos");compose.onNode(hasText("Completada") and hasClickAction()).performClick();work("Completed task");compose.onAllNodesWithText("Shared task").assertCountEquals(0)
        click("Estado: Completada");click("Todos");click("Tipo: Todos los tipos");click("Objetivos");work("Exact objective");await("Volver a objetivos");await("Crear criterio")
        runBlocking {assertEquals(goal,app.database.planningDao().objectives().single().id);assertFalse(app.database.backupDao().nodes().single {it.title=="Shared task"}.isCompleted);assertTrue(app.database.backupDao().nodes().single {it.title=="Completed task"}.isCompleted)}
        click("Cerrar planificación");click("Proyecto: Todos los proyectos");click("Second");await("Abrir proyecto: Second")
        runBlocking {app.projectRepository.deleteProject(secondProject)}
        await("Proyecto: Todos los proyectos");await("Abrir proyecto: First");compose.onAllNodesWithText("Abrir proyecto: Second").assertCountEquals(0)
    }
    @Test fun appRootNavigatesPersonProjectAndNestedOriginalTaskThenReturnsToPerson() {
        runBlocking {
            app.personRepository.save("a","Ana",null)
            val p=app.projectRepository.createProject("Navigation project")
            val layer=app.nodeRepository.createNode(p.id,null,"Real layer",purpose=NodePurpose.LAYER)
            val task=app.nodeRepository.createNode(p.id,layer.id,"Real nested task",description="Original detail")
            app.personRepository.setResponsiblePeople(task.id,setOf("a"))
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await("Navigation project");compose.onNodeWithContentDescription("Abrir menú").performClick();compose.onNodeWithText("Recursos").performScrollTo().performClick();click("Personas");click("Ana");section("Proyectos y trabajo")
        click("Abrir proyecto: Navigation project");await("Real layer")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Abrir proyecto: Navigation project")
        work("Real nested task");await("Original detail")
        // Back follows the original task → layer → project hierarchy before returning to the ficha.
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Real layer")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Navigation project")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Abrir proyecto: Navigation project");await("Ana")
        runBlocking {assertEquals(2,app.database.backupDao().nodes().size);assertEquals(1,app.database.personDao().assignmentIds(app.database.backupDao().nodes().first {it.title=="Real nested task"}.id).size)}
    }
    @Test fun largeFontsAndEveryExistingThemeKeepNewSectionsReachable() {
        runBlocking {app.personRepository.save("a","Nombre largo ".repeat(4),null);app.database.technologyDao().insert(TechnologyEntity("k","Tecnología extensa ".repeat(3),null));app.personRepository.saveTechnology("a","k",PersonProficiency.ADVANCED)}
        val preferences=AppearancePreferences(compose.activity.getSharedPreferences("phase2-themes",0))
        compose.setContent {Arachn0deTheme(preferences) {val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,1.6f)) {PeopleScreen(app.personRepository,initialPersonId="a") {}}}}
        section("Tecnologías")
        AppearanceTheme.entries.forEach {theme->compose.runOnUiThread {preferences.setTheme(theme)};click("Tecnología extensa ".repeat(3));await("✓ Avanzado");click("Cancelar")}
        section("Proyectos y trabajo");await("Sin proyectos ni trabajo asociados.")
    }
    @Test fun navigationFromParticipantDismissesSourceDialogsInTheSameProject() {
        runBlocking {
            app.personRepository.save("a","Ana",null)
            val p=app.projectRepository.createProject("Same project")
            val c=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            app.nodeRepository.management.participation.add(c,"a")
            val task=app.nodeRepository.createNode(p.id,null,"Same original task",description="Same original detail")
            app.nodeRepository.management.participation.assign(c,"a",task.id,false,ResponsibilityRole.EXECUTOR)
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        click("Same project");click("Gestión del proyecto · Gestionado");click("Participantes y responsabilidades")
        compose.onNodeWithTag("participation-list").performScrollToNode(hasText("Configurar: Ana"));click("Configurar: Ana");click("Detalle de Persona");section("Proyectos y trabajo")
        work("Same original task");await("Same original detail")
        compose.onAllNodesWithText("Participantes y responsabilidades").assertCountEquals(0)
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Same project")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Abrir proyecto: Same project");await("Ana")
    }
    @Test fun projectReturnPreservesTheOriginalPersonRelationshipPath() {
        runBlocking {
            app.personRepository.save("a","Ana",null);app.personRepository.save("b","Bruno",null)
            val key=com.r0ybt.arachn0de.domain.model.relationKey("a","b","SOCIAL","amistad","amistad","RECIPROCAL")
            app.personRepository.saveRelation(PersonRelationEntity("edge","a","b","SOCIAL","amistad","amistad","","RECIPROCAL",key),true)
            val p=app.projectRepository.createProject("Related project")
            val task=app.nodeRepository.createNode(p.id,null,"Bruno task");app.personRepository.setResponsiblePeople(task.id,setOf("b"))
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await("Related project");compose.onNodeWithContentDescription("Abrir menú").performClick();compose.onNodeWithText("Recursos").performScrollTo().performClick();click("Personas");click("Ana");click("Relaciones");click("Bruno");section("Proyectos y trabajo")
        click("Abrir proyecto: Related project");await("Bruno task")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Abrir proyecto: Related project");await("Bruno")
        click("Volver");await("Ana");await("Editar General")
        click("Volver");await("Nueva Persona")
    }
}
