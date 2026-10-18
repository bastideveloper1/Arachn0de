package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.relationKey
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
class AntisocialContextualSectionsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var people:PersonRepository
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun text(value:String)=await {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}
    @Before fun setup()=runBlocking {
        people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))
        people.save("person","Apodo visible",null)
        people.saveProfile(PersonProfileEntity("person",alias="Apodo visible",givenName="Nombre guardado",familyName="Apellido guardado",description="Biografía guardada",documentId="Documento guardado",notes="Notas guardadas",phones="123\n456",emails="correo@example.org",addresses="Dirección",links="https://example.org",qualities="Cualidad",skills="Habilidad",interests="Interés",availability="Disponible"))
        people.saveBirthday("person","2000-01-02")
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump();runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}};app.database.close()
    }
    private fun open() {compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}};text("Fotografías")}
    private fun section(label:String) {compose.onNodeWithTag("person-section:$label").performScrollTo().performClick()}
    private fun field(index:Int) {compose.onNodeWithTag("modular-field:$index").performScrollTo().performClick()}
    private fun profile()=runBlocking {app.database.personProfileDao().profiles().single {it.personId=="person"}}

    @Test fun sectionPanelsMatchConsultationAndGeneralKeepsRequiredOrderAndValidation() {
        open();compose.onNodeWithTag("profile-photo").assertExists();compose.onNodeWithTag("profile-self").assertExists()
        compose.onNodeWithTag("profile-information").assertDoesNotExist();compose.onNodeWithText("Editar perfil").assertDoesNotExist()
        section("General");text("Información del perfil")
        val labels=listOf("Nombre","Apellidos","Apodo","Biografía","DNI/RUT","Notas")
        assertEquals(labels,profileFields(profile(),"General").map {it.first})
        labels.forEachIndexed {i,label->compose.onNodeWithTag("modular-field:$i").performScrollTo().assert(hasText(label))}
        compose.onNodeWithTag("profile-self").assertDoesNotExist();compose.onNodeWithTag("person-delete-profile").assertDoesNotExist()
        field(0);compose.onNodeWithTag("modular-text-field").performTextReplacement("Nombre confirmado")
        compose.onNodeWithText("Confirmar").performClick();await {profile().givenName=="Nombre confirmado"}
        compose.onNodeWithTag("person-profile-consultation").performScrollToNode(hasText("Nombre confirmado"))
        compose.onNodeWithText("Nombre confirmado").assertIsDisplayed()
        field(4);compose.onNodeWithTag("modular-text-field").performTextReplacement("Pendiente")
        section("Contacto");text("Contacto")
        compose.onNodeWithTag("modular-field:0").assert(hasText("Teléfonos"))
        compose.onNodeWithTag("modular-field:4").assertDoesNotExist()
        compose.onNodeWithTag("person-profile-consultation").performScrollToNode(hasText("123\n456"))
        compose.onNodeWithText("123\n456").assertIsDisplayed()
        section("General");field(4);compose.onNodeWithTag("modular-text-field").assert(hasText("Documento guardado"))
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Documento confirmado")
        compose.onNodeWithText("Confirmar").performClick();await {profile().documentId=="Documento confirmado"}
        field(2);compose.onNodeWithTag("modular-text-field").performTextReplacement("")
        compose.onNodeWithText("Confirmar").assertIsNotEnabled();compose.onNodeWithText("Cancelar").performClick()
        field(3);compose.onNodeWithTag("modular-text-field").performTextReplacement("x".repeat(151))
        compose.onNodeWithText("Confirmar").assertIsNotEnabled()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("modular-field:0").assertExists()
        section("Perfil");compose.onNodeWithTag("modular-field:0").assert(hasText("Fecha de nacimiento"))
        field(0);compose.onNodeWithTag("birthday-year").assertExists();compose.onNodeWithText("Cancelar").performClick()
        assertEquals("Notas guardadas",profile().notes);assertEquals("Biografía guardada",profile().description)
        section("Tecnologías");text("Agregar Tecnología")
        compose.onNodeWithTag("profile-self").assertDoesNotExist();compose.onNodeWithTag("person-delete-profile").assertDoesNotExist()
        section("Proyectos y trabajo");text("Sin proyectos ni trabajo asociados.")
        compose.onNodeWithTag("modular-field-menu").assertDoesNotExist()
        section("Inicio");compose.onNodeWithTag("profile-self").assertExists();compose.onNodeWithTag("person-delete-profile").assertExists()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test fun relationshipAndNetworkActionsStayInTheirContextualPanel() {
        runBlocking {
            people.save("other","Otra Persona",null);people.identifySelf("person")
            people.saveRelation(PersonRelationEntity("relation","person","other","SOCIAL","Amistad","","Significado inicial","ONE_WAY",relationKey("person","other","SOCIAL","Amistad","","ONE_WAY")),true)
        }
        open();section("Relaciones");text("Amistad de")
        compose.onNodeWithTag("person-add-relation").assertExists()
        compose.onNodeWithTag("person-edit-relation").performClick()
        compose.onNodeWithTag("person-relation:relation").performClick();text("Significado o notas (opcional)")
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText("Significado o notas (opcional)").performScrollTo().performTextReplacement("Significado confirmado")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.database.personProfileDao().relations().single().meaning=="Significado confirmado"}}
        compose.onNodeWithTag("person-add-relation").assertExists()
        compose.onNodeWithTag("person-delete-relation").performClick();compose.onNodeWithTag("person-relation:relation").performClick()
        text("Eliminar vínculo");compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1,runBlocking {app.database.personProfileDao().relations().size})
        compose.onNodeWithTag("person-add-relation").performClick();text("Buscar Persona relacionada")
        section("General");compose.onNodeWithTag("modular-field:0").assert(hasText("Nombre"))
        section("Relaciones");compose.onNodeWithTag("person-add-relation").assertExists()
        compose.onNodeWithText("Buscar Persona relacionada").assertDoesNotExist()
        section("Inicio");compose.onNodeWithTag("person-network").performClick();text("Buscar Personas")
        compose.onNodeWithTag("person-network-favorites").performClick()
        compose.onNodeWithTag("person-favorite:other").performClick()
        await {runBlocking {people.favoritePeople().contains("other")}}
        compose.onNodeWithText("Volver a Red").performClick()
        compose.onNodeWithTag("person-network-add").assertExists()
        compose.onNodeWithTag("person-delete-profile").assertDoesNotExist()
    }

    @Test fun leavingInicioReleasesCancelledPhotoDraftAndKeepsConfirmedAvatar() {
        open();compose.onNodeWithTag("profile-photo").performClick()
        fun pick() {
            compose.onNodeWithTag("profile-change-photo").performClick()
            val launched=checkNotNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult)
            val source=java.io.File(app.cacheDir,"context-avatar.png").apply {writeBytes(BackupFixture.png())}
            compose.runOnUiThread {compose.activity.activityResultRegistry.dispatchResult(launched.requestCode,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(source)))}
            text("Ampliar")
        }
        pick();compose.onNodeWithText("Cancelar").performClick();section("General")
        await {java.io.File(app.security.current.value!!.context.filesDir,"avatars").listFiles().orEmpty().isEmpty()}
        assertNull(runBlocking {app.database.personDao().get("person")!!.avatarFile})
        section("Inicio");compose.onNodeWithTag("profile-photo").performClick();pick()
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.database.personDao().get("person")!!.avatarFile!=null}}
        val file=runBlocking {app.database.personDao().get("person")!!.avatarFile!!}
        section("Contacto");compose.onNodeWithTag("profile-change-photo").assertDoesNotExist()
        assertTrue(AvatarStore(app.security.current.value!!.context).exists(file))
        section("Inicio");compose.onNodeWithTag("profile-photo").assertExists()
        compose.onNodeWithTag("profile-change-photo").assertDoesNotExist()
        runBlocking {people.save("person","Apodo visible",null,isNew=false)}
        compose.onNodeWithTag("profile-self").performClick()
        await {runBlocking {people.observeSelf().first()?.personId=="person"}}
        assertNull(runBlocking {app.database.personDao().get("person")!!.avatarFile})
        section("General");await {!AvatarStore(app.security.current.value!!.context).exists(file)}
    }
}
