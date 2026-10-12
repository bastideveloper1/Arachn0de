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
class PersonGalleryUiTest {
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
    private fun photoMenu(index:Int) {
        compose.waitUntil(15000) {pump();runCatching {compose.onAllNodesWithText("⋮")[index].assertIsEnabled()}.isSuccess}
        compose.onAllNodesWithText("⋮")[index].performClick()
    }
    private fun work(text:String) {compose.onNodeWithTag("person-work-list").performScrollToNode(hasText(text));compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("person-work-list"))).performClick()}
    @Test fun gridSearchesAliasesAndMultipleRelationshipCategories() {
        runBlocking {
            app.personRepository.save("a","Ana",null);app.personRepository.save("b","Bruno",null);app.personRepository.save("c","Carla",null)
            app.personRepository.saveProfile(PersonProfileEntity("a",alias="Anita"))
            listOf("FAMILY","WORK").forEach {category->app.personRepository.saveRelation(PersonRelationEntity(category,"a","b",category,"vínculo","vínculo","","RECIPROCAL",com.r0ybt.arachn0de.domain.model.relationKey("a","b",category,"vínculo","vínculo","RECIPROCAL")),true)}
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository) {}}}
        await("Buscar Personas");await("Carla")
        val firstRow=listOf("Ana","Bruno","Carla").map {compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top}
        assertEquals(firstRow[0],firstRow[1],1f);assertEquals(firstRow[0],firstRow[2],1f)
        compose.onNodeWithText("Buscar Personas").performTextReplacement("Anita");await("Ana");compose.onAllNodesWithText("Bruno").assertCountEquals(0)
        compose.onNodeWithText("Buscar Personas").performTextReplacement("");click("Familiares");await("Bruno");compose.onAllNodesWithText("Carla").assertCountEquals(0)
        click("Laborales");await("Ana");click("Ana");await("Anita");click("Relaciones");compose.onAllNodesWithText("Bruno")[0].performClick();await("Editar nombre y avatar");click("Volver");await("Anita")
    }
    @Test fun birthdaySaveAndReferencePhotosAreAccessibleWithLargeText() {
        runBlocking {app.personRepository.save("a","Ana",null);app.personRepository.saveProfile(PersonProfileEntity("a",alias="Anita",description="A long description that remains readable with large fonts"))}
        compose.setContent {Arachn0deTheme {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)) {PeopleScreen(app.personRepository,initialPersonId="a") {}}}}
        click("Añadir fecha de nacimiento");compose.onNodeWithText("AAAA-MM-DD").performTextReplacement("2000-01-02");click("Guardar")
        compose.waitUntil(15000) {pump();runBlocking {app.database.personGalleryDao().birthdays().size==1}}
        click("Fotos de referencia (0/4)");await("Añadir foto");click("Cerrar");click("Contacto");click("Editar Contacto");click("Cancelar");click("General");await("Anita")
    }
    @Test fun creationAndIdentityEditKeepBirthdayInExistingFormat() {
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository) {}}}
        click("Nueva Persona");await("Nombre");compose.onNodeWithText("Nombre").performTextInput("Nueva")
        compose.onNodeWithText("AAAA-MM-DD").performTextInput("1930-02-28")
        click("Elegir fecha de nacimiento");click("Seleccionar");click("Guardar")
        await("Nueva");click("Nueva");await("Editar nombre y avatar");click("Editar nombre y avatar")
        compose.waitUntil(15000) {pump();compose.onAllNodes(hasSetTextAction() and hasText("1930-02-28")).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Nombre").performTextReplacement("Renombrada");click("Guardar")
        compose.waitUntil(15000) {pump();runBlocking {app.database.backupDao().persons().any {it.name=="Renombrada"}}}
        runBlocking {assertEquals("1930-02-28",app.database.personGalleryDao().birthdays().single().date)}
    }
    @Test fun referenceViewerOnlyChangesAvatarAfterExplicitAction() {
        val repository=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context, syncDirectory=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
        val file=runBlocking {
            repository.save("a","Ana",null)
            val image=java.io.File(app.cacheDir,"reference-source.png").apply {writeBytes(com.r0ybt.arachn0de.backup.BackupFixture.png())}
            repository.importAvatar(android.net.Uri.fromFile(image)).also {repository.savePhoto(PersonPhotoEntity("ref","a",it,0,2f,.1f,.2f),"draft")}
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(repository,initialPersonId="a") {}}}
        await("Fotos de referencia (1/4)");compose.onNodeWithTag("person-photo-ref").performClick()
        compose.onNodeWithTag("person-photo-viewer").assertExists()
        compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithText("Ampliar").assertIsEnabled()}.isSuccess}
        click("Ampliar");compose.onNodeWithTag("zoomable-image").assert(hasStateDescription("Zoom 150 %"))
        click("Alejar");compose.onNodeWithTag("zoomable-image").assert(hasStateDescription("Zoom 100 %"))
        click("Restablecer")
        runBlocking {assertNull(app.database.personDao().get("a")!!.avatarFile)}
        click("Cerrar");runBlocking {assertNull(app.database.personDao().get("a")!!.avatarFile)}
        compose.onNodeWithTag("person-photo-ref").performClick();click("Establecer como avatar principal")
        compose.waitUntil(15000) {pump();runBlocking {app.database.personDao().get("a")!!.avatarFile==file}}
        runBlocking {assertEquals(2f,app.database.personDao().get("a")!!.avatarZoom,0f);assertEquals(file,app.database.personGalleryDao().photos().single().file)}
        compose.onNodeWithTag("person-photo-viewer").assertDoesNotExist()
    }
    @Test fun selectedReferencePhotoDoesNotChangeAvatarAndContextualReorderAndDeleteWork() {
        val repository=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context, syncDirectory=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
        val main=runBlocking {
            repository.save("a","Ana",null)
            val image=java.io.File(app.cacheDir,"photo.png").apply {writeBytes(com.r0ybt.arachn0de.backup.BackupFixture.png())}
            val avatar=repository.importAvatar(android.net.Uri.fromFile(image));repository.save("a","Ana",avatar,false,zoom=3f)
            repeat(2) {val imported=repository.importAvatar(android.net.Uri.fromFile(image));repository.savePhoto(PersonPhotoEntity("photo$it","a",imported,it),"draft")}
            avatar
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(repository,initialPersonId="a") {}}}
        await("Fotos de referencia (2/4)");compose.onNodeWithTag("person-photo-main").assertIsSelected();compose.onNodeWithTag("person-photo-photo1").performClick();compose.onNodeWithTag("person-photo-viewer").assertExists();click("Anterior");click("Siguiente");click("Cerrar")
        runBlocking {assertEquals(main,app.database.personDao().get("a")!!.avatarFile);assertEquals(3f,app.database.personDao().get("a")!!.avatarZoom,0f)}
        click("Fotos de referencia (2/4)");photoMenu(1);click("Ajustar encuadre");compose.waitUntil(15000) {pump();compose.onAllNodesWithContentDescription("Vista previa del avatar").fetchSemanticsNodes().isNotEmpty()};click("Ampliar");click("Cancelar")
        runBlocking {assertEquals(1f,app.database.personGalleryDao().photos().first {it.id=="photo1"}.zoom,0f)}
        photoMenu(1);click("Ajustar encuadre");await("Ampliar");click("Ampliar");click("Confirmar")
        compose.waitUntil(15000) {pump();runBlocking {app.database.personGalleryDao().photos().first {it.id=="photo1"}.zoom>1f}}
        photoMenu(1);click("Mover antes")
        compose.waitUntil(15000) {pump();runBlocking {app.database.personGalleryDao().photos().first().id=="photo1"}}
        photoMenu(0);click("Eliminar foto");await("Foto 1");click("Cerrar");await("Fotos de referencia (1/4)")
        runBlocking {assertEquals(main,app.database.personDao().get("a")!!.avatarFile);assertEquals("photo0",app.database.personGalleryDao().photos().single().id)}
        compose.onNodeWithTag("person-photo-main").assertIsSelected()
    }

}
