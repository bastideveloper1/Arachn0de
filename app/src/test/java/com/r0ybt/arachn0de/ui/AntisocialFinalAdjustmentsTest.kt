package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[33],qualifiers="w360dp-h891dp")
class AntisocialFinalAdjustmentsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var people:PersonRepository
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun text(value:String)=await {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}
    @Before fun setup()=runBlocking {
        people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))
        people.save("person","Ana",null)
        people.saveProfile(PersonProfileEntity("person",alias="Ana",description="Bio",notes="Notas conservadas",phones="123",qualities="Cualidad",documentId="Privado"))
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump();runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}};app.database.close()
    }
    private fun openProfile() {
        compose.onNodeWithTag("person-edit-profile").performClick();text("Soy yo")
        compose.onNodeWithText("Guardar").assertDoesNotExist()
    }
    @Test fun inlineProfileKeepsConsultationInteractiveAndConfirmedPersonalDataVisible() {
        runBlocking {people.saveProfile(PersonProfileEntity("person",alias="Ana",description="Bio completa",givenName="Nombre guardado",familyName="Apellido guardado",documentId="Documento guardado",phones="123"))}
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías");openProfile()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithTag("person-publications").assertIsDisplayed()
        compose.onNodeWithTag("person-section:General").performClick();text("Información del perfil")
        val consultation=compose.onNodeWithTag("person-profile-consultation")
        consultation.performScrollToNode(hasText("Nombre guardado"))
        compose.onNodeWithText("Nombre guardado").assertIsDisplayed()
        consultation.performScrollToNode(hasText("Documento guardado"))
        compose.onNodeWithText("Documento guardado").assertIsDisplayed()
        compose.onNodeWithTag("profile-information").performClick()
        compose.onNodeWithTag("modular-field:7").performScrollTo().performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Nombre confirmado")
        compose.onNodeWithText("Confirmar").performClick()
        text("Nombre confirmado")
        consultation.performScrollToNode(hasText("Nombre confirmado"))
        compose.onNodeWithText("Nombre confirmado").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("profile-self").assertExists()
        val visible=profileConsultationFields(PersonProfileEntity("person",givenName="Nombre",documentId="Documento"),"General")
        assertEquals(listOf("Nombre" to "Nombre","DNI / RUT" to "Documento"),visible)
    }

    @Test fun informationEditsOneFieldAndSelfToggleConfirmsReplacementAndPersists() {
        runBlocking {people.save("other","Otro",null);people.identifySelf("other")}
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías");compose.onNodeWithTag("person-delete-profile").assert(hasText("Eliminar perfil"));openProfile()
        compose.onNodeWithTag("profile-information").performClick()
        compose.onNodeWithTag("modular-field:3").performScrollTo().performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Descartar")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("123",runBlocking {app.database.personProfileDao().profiles().first {it.personId=="person"}.phones})
        compose.onNodeWithTag("modular-field:7").performScrollTo().performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Nombre privado")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.database.personProfileDao().profiles().first {it.personId=="person"}.givenName=="Nombre privado"}}
        compose.onNodeWithTag("modular-field:9").performScrollTo().assert(hasText("DNI / RUT"))
        compose.onNodeWithText("Volver a opciones").performScrollTo().performClick()
        compose.onNodeWithTag("profile-self").assertIsOff().performClick();text("Cambiar mi perfil")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("other",runBlocking {people.observeSelf().first()!!.personId})
        compose.onNodeWithTag("profile-self").performClick();compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {people.observeSelf().first()?.personId=="person"}}
        compose.onNodeWithTag("profile-self").assertIsOn()
        compose.onNodeWithContentDescription("Cerrar perfil").performClick();openProfile()
        compose.onNodeWithTag("profile-self").assertIsOn().performClick()
        await {runBlocking {people.observeSelf().first()==null}}
        val profile=runBlocking {app.database.personProfileDao().profiles().first {it.personId=="person"}}
        assertEquals("Privado",profile.documentId);assertEquals("Notas conservadas",profile.notes);assertEquals("Cualidad",profile.qualities)
    }
    @Test fun photoFramingCancellationAndAvatarRemovalPreserveSharedImage() {
        val file=runBlocking {
            val source=java.io.File(app.cacheDir,"avatar-shared.png").apply {writeBytes(BackupFixture.png())}
            val imported=people.importAvatar(android.net.Uri.fromFile(source))
            people.savePhoto(PersonPhotoEntity("shared","person",imported,0),"seed")
            people.save("person","Ana",imported,isNew=false);imported
        }
        val restore=androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías");openProfile();compose.onNodeWithTag("profile-photo").performClick()
        compose.onNodeWithTag("profile-frame").performClick();text("Ampliar")
        compose.onNodeWithText("Ampliar").performClick();compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1f,runBlocking {app.database.personDao().get("person")!!.avatarZoom})
        fun pick() {
            compose.onNodeWithTag("profile-change-photo").performClick()
            val launched=checkNotNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult)
            val source=java.io.File(app.cacheDir,"picked-avatar.png").apply {writeBytes(BackupFixture.png())}
            compose.runOnUiThread {compose.activity.activityResultRegistry.dispatchResult(launched.requestCode,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(source)))}
            text("Ampliar")
        }
        val store=AvatarStore(app.security.current.value!!.context)
        pick();restore.emulateSavedInstanceStateRestore();text("Ampliar")
        compose.onNodeWithText("Cancelar").performClick()
        await {java.io.File(app.security.current.value!!.context.filesDir,"avatars").listFiles().orEmpty().size==1}
        assertEquals(file,runBlocking {app.database.personDao().get("person")!!.avatarFile})
        pick();compose.onNodeWithText("Ampliar").performClick();compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.database.personDao().get("person")!!.avatarFile!=file}}
        val replacement=runBlocking {app.database.personDao().get("person")!!.avatarFile!!}
        assertTrue(store.exists(file));assertTrue(store.exists(replacement))
        compose.onNodeWithTag("profile-remove-avatar").performClick()
        await {runBlocking {app.database.personDao().get("person")!!.avatarFile==null}}
        await {!store.exists(replacement)}
        compose.onNodeWithContentDescription("Volver a opciones").performClick()
        compose.onNodeWithContentDescription("Cerrar perfil").performClick()
        assertTrue(AvatarStore(app.security.current.value!!.context).exists(file))
        assertEquals(file,runBlocking {app.database.personGalleryDao().photos().single().file})
    }
    @Test fun technologySelectorStaysCompactSearchesAndReturnsThroughItsLevels() {
        runBlocking {repeat(30) {app.database.technologyDao().insert(TechnologyEntity("tech$it","Tecnología $it",null))}}
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person",initialSection="Tecnologías") {}}}
        text("Agregar Tecnología")
        val compact=compose.onNodeWithTag("person-technology-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(compact.height<300*compose.activity.resources.displayMetrics.density)
        compose.onNodeWithTag("person-add-technology").performClick()
        compose.onNodeWithText("Buscar Tecnologías").performTextReplacement("29")
        compose.onNodeWithText("Tecnología 29").performClick();text("Guardar dominio")
        compose.onNodeWithText("Experto").performClick();compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Buscar Tecnologías").assertIsDisplayed()
        assertTrue(runBlocking {app.database.personIntegrationDao().technologies().isEmpty()})
        compose.onNodeWithText("Tecnología 29").performClick();compose.onNodeWithText("Experto").performClick()
        compose.onNodeWithText("Guardar dominio").performClick()
        await {runBlocking {app.database.personIntegrationDao().technologies().singleOrNull()?.proficiency=="EXPERT"}}
        compose.onNodeWithTag("person-add-technology").assertIsDisplayed()
        compose.onNodeWithTag("person-technology:tech29").performClick();text("✓ Experto")
        compose.onNodeWithText("Retirar asociación").performScrollTo().performClick()
        await {runBlocking {app.database.personIntegrationDao().technologies().isEmpty()}}
        compose.onNodeWithTag("person-add-technology").assertIsDisplayed()
        assertEquals(30,runBlocking {app.database.technologyDao().catalog().size})
    }
    @Test fun compactWorkCardsKeepRealDestinationsAndSecondaryResponsibilities() {
        val task=runBlocking {
            val project=app.projectRepository.createProject("Núcleo de trabajo")
            val task=app.nodeRepository.createNode(project.id,null,"Tarea con título largo para comprobar su tarjeta compacta","")
            people.save("first","Aarón",null)
            people.setResponsiblePeople(task.id,setOf("person","first"));task
        }
        var destination:PersonDestination?=null
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalPersonNavigation provides {destination=it}) {
            PersonWorkSection("person",people)
        }}}
        text(task.title)
        compose.onNodeWithTag("person-work-item:${task.id}").assertIsDisplayed()
        compose.onNodeWithText("Tarea · Pendiente").assertExists()
        compose.onNodeWithText("Responsable tradicional").assertExists()
        await {compose.onAllNodesWithTag("person-photo:first",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("person-photo:first",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("person-work-project:${task.projectId}").onChildren().filter(hasAnyDescendant(hasTestTag("person-photo:first"))).assertCountEquals(0)
        compose.onNodeWithTag("person-work-item:${task.id}").performClick()
        assertEquals(task.id,destination?.nodeId);assertEquals(task.projectId,destination?.projectId)
        compose.onNodeWithTag("person-work-project:${task.projectId}").performScrollTo().performClick()
        assertEquals(null,destination?.nodeId);assertEquals(task.projectId,destination?.projectId)
    }
}
