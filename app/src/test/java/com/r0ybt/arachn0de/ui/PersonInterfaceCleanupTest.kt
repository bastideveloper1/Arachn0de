package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class PersonInterfaceCleanupTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump()
        runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}}
        app.database.close()
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun text(value:String) {await {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}}
    private lateinit var people:PersonRepository
    private fun setup()=runBlocking {
        app.personRepository.save("person","Ana",null)
        app.personRepository.saveProfile(PersonProfileEntity("person",description="Biografía privada",links="https://example.org/perfil",phones="123"))
        app.personRepository.saveBirthday("person","1980-04-12")
        people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))
        val file=java.io.File(app.cacheDir,"gallery-original.png").apply {writeBytes(BackupFixture.png())}
        val name=people.importAvatar(android.net.Uri.fromFile(file))
        people.savePhoto(PersonPhotoEntity("legacy-one","person",name,0),"seed")
        people.savePhoto(PersonPhotoEntity("legacy-two","person",name,1),"seed")
        val dao=app.database.personMemoryDao();dao.clear()
        dao.save(PersonMemoryEntity("entry","person","1980-04-12","Descripción del grupo",0))
        dao.images(listOf(PersonMemoryImageEntity("one","entry",null,"legacy-one",0,"1981-01-02","Descripción individual"),PersonMemoryImageEntity("two","entry",null,"legacy-two",1)))
    }
    private fun openGallery() {
        compose.onNodeWithTag("person-general").performScrollToNode(hasTestTag("memory:entry"))
        compose.onNodeWithTag("memory:entry").performClick();text("1/2")
    }
    private fun section(label:String) {
        val node=compose.onNodeWithContentDescription(label)
        node.performClick();node.assertIsSelected()
    }
    @Test fun sectionsAutomaticallyExposeAllFieldsAndPreserveDraftsAndPrivateData() {
        runBlocking {
            app.personRepository.save("person","Ana",null)
            app.personRepository.saveProfile(PersonProfileEntity("person",alias="Ana",notes="Original",phones="123",documentId="Privado"))
            app.personRepository.saveBirthday("person","2000-01-02")
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository,initialPersonId="person") {}}}
        text("Fotografías");section("General");text("Editar General")
        listOf("Apodo","Descripción","Notas").forEachIndexed {i,label->compose.onNodeWithTag("modular-field:$i").assert(hasText(label))}
        compose.onNodeWithContentDescription("Editar Notas").assertDoesNotExist()
        compose.onNodeWithText("Original").assertIsDisplayed()
        compose.onNodeWithTag("modular-field:0").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Luna")
        compose.onNodeWithText("Confirmar").performClick()
        compose.onNodeWithTag("modular-field:2").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Descartar")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Guardar sección").performClick()
        await {runBlocking {app.database.personDao().get("person")!!.name=="Luna"}}
        section("Contacto");text("Editar Contacto")
        listOf("Teléfonos","Correos","Direcciones","Enlaces").forEachIndexed {i,label->compose.onNodeWithTag("modular-field:$i").assert(hasText(label))}
        compose.onNodeWithTag("modular-field:2").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Dirección nueva")
        compose.onNodeWithText("Confirmar").performClick();compose.onNodeWithText("Guardar sección").performClick()
        await {runBlocking {app.database.personProfileDao().profiles().single().addresses=="Dirección nueva"}}
        section("Perfil");text("Editar Perfil")
        listOf("Cualidades","Habilidades","Intereses","Disponibilidad","Fecha de nacimiento").forEachIndexed {i,label->compose.onNodeWithTag("modular-field:$i").performScrollTo().assert(hasText(label))}
        compose.onNodeWithTag("modular-field:4").performClick();compose.onNodeWithTag("birthday-year").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("modular-field:0").performScrollTo().performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Conservada")
        section("Contacto");text("Editar Contacto");section("Perfil");text("Editar Perfil")
        compose.onNodeWithTag("modular-field:0").performClick()
        compose.onNodeWithTag("modular-text-field").assert(hasText("Conservada"))
        compose.onNodeWithText("Confirmar").performClick();compose.onNodeWithText("Guardar sección").performClick()
        await {runBlocking {app.database.personProfileDao().profiles().single().qualities=="Conservada"}}
        runBlocking {
            val profile=app.database.personProfileDao().profiles().single()
            assertEquals("Original",profile.notes);assertEquals("123",profile.phones);assertEquals("Privado",profile.documentId)
            assertEquals("2000-01-02",app.database.personGalleryDao().birthdays().single().date)
        }
    }

    @Test fun technologiesOpensItsExistingSelectorDirectlyAndCanReopenFromItsTab() {
        runBlocking {app.personRepository.save("person","Ana",null);app.database.technologyDao().insert(TechnologyEntity("tech","Tecnología",null))}
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository,initialPersonId="person") {}}}
        text("Fotografías");section("Tecnologías");text("Tecnologías existentes")
        compose.onNodeWithText("Asociar Tecnología").assertDoesNotExist()
        compose.onNodeWithText("Buscar Tecnologías").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        section("Tecnologías");text("Tecnologías existentes")
        compose.onNodeWithText("Tecnología").performClick();text("Guardar dominio")
        compose.onNodeWithText("Guardar dominio").performClick()
        await {runBlocking {app.database.personIntegrationDao().technologies().any {it.personId=="person" && it.technologyId=="tech"}}}
    }
    @Test fun nicknameOnlyProfileCanEditPrivatePersonalDataWithoutShowingItInTheHeader() {
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository) {}}}
        text("Nueva Persona");compose.onNodeWithText("Nueva Persona").performClick()
        text("Apodo");compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Apodo").performTextReplacement("Luna")
        await {runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.backupDao().persons().size==1}}
        val person=runBlocking {app.database.backupDao().persons().single()}
        assertEquals("Luna",person.name)
        assertEquals("",runBlocking {app.database.personProfileDao().profiles().single().givenName})
        text("Luna");compose.onNodeWithTag("person-card:${person.id}").performClick();text("Fotografías")
        compose.onNodeWithContentDescription("Opciones del perfil").performClick()
        compose.onNodeWithText("Editar perfil").performClick();text("Datos personales")
        listOf("Nombre" to "Nombre privado","Apellidos" to "Apellido privado","DNI / RUT" to "Documento privado").forEach {(label,value)->
            compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).performTextReplacement(value)
        }
        compose.onNodeWithText("Guardar").performClick();text("Fotografías")
        listOf("Nombre privado","Apellido privado","Documento privado").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        val profile=runBlocking {app.database.personProfileDao().profiles().single()}
        assertEquals("Nombre privado",profile.givenName);assertEquals("Apellido privado",profile.familyName);assertEquals("Documento privado",profile.documentId)
        assertEquals("Luna",runBlocking {app.database.personDao().get(person.id)!!.name})
        compose.onNodeWithContentDescription("Opciones del perfil").performClick()
        compose.onNodeWithText("Editar perfil").performClick();text("Datos personales")
        compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasText("DNI / RUT"))
        compose.onNodeWithText("DNI / RUT").assert(hasText("Documento privado"))
        compose.onNodeWithText("Cancelar").performClick()
    }
    @Test fun notesPanelCancelsOnlyItsBufferAndSavesWithoutChangingOtherProfileFields() {
        runBlocking {
            app.personRepository.save("person","Ana",null)
            app.personRepository.saveProfile(PersonProfileEntity("person",notes="Original",phones="123"))
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository,initialPersonId="person") {}}}
        text("Fotografías");section("General");text("Editar General")
        compose.onNodeWithTag("modular-field:2").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Provisional")
        compose.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("Original",runBlocking {app.database.personProfileDao().profiles().single().notes})
        section("General");text("Editar General")
        compose.onNodeWithTag("modular-field:2").performClick()
        compose.onNode(hasSetTextAction()).assert(hasText("Original")).performTextReplacement("Confirmada")
        compose.onNodeWithText("Confirmar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Guardar sección").assertIsDisplayed().performClick()
        text("Confirmada")
        runBlocking {
            val profile=app.database.personProfileDao().profiles().single()
            assertEquals("Confirmada",profile.notes);assertEquals("123",profile.phones)
        }
    }
    @Test fun sevenIconDestinationsKeepContextualEditorsAndGalleryOnlyInInicio() {
        setup();compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías")
        val destinations=listOf("Inicio","General","Contacto","Perfil","Relaciones","Tecnologías","Proyectos y trabajo")
        destinations.forEach {compose.onNodeWithContentDescription(it).assertExists();compose.onAllNodesWithText(it).assertCountEquals(0)}
        compose.onNodeWithContentDescription("Inicio").assertIsSelected()
        section("General");text("Editar General");compose.onNodeWithText("Fotografías").assertDoesNotExist()
        compose.onNodeWithTag("modular-field:0").assert(hasText("Apodo"))
        compose.onNodeWithText("Cancelar").performClick()
        section("Contacto");text("Editar Contacto");text("123")
        compose.onNodeWithTag("modular-field:0").assert(hasText("Teléfonos"))
        compose.onNodeWithText("Cancelar").performClick()
        section("Perfil");text("Editar Perfil")
        compose.onNodeWithTag("modular-field:4").performScrollTo().performClick()
        compose.onNodeWithTag("birthday-year").assertExists();compose.onNodeWithText("Cancelar").performClick()
        section("Relaciones");text("Añadir relación")
        section("Tecnologías");text("Tecnologías existentes");compose.onNodeWithText("Cancelar").performClick()
        section("Proyectos y trabajo")
        section("Inicio");text("Fotografías")
        compose.onNodeWithText("Añadir fecha de nacimiento").assertDoesNotExist()
        assertEquals("1980-04-12",runBlocking {app.database.personGalleryDao().birthdays().single().date})
        assertEquals(2,runBlocking {app.database.personMemoryDao().images().size})
    }
    @Test fun unifiedProfileEditorPreservesBirthdayAndStagesYoUntilSave() {
        setup();compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías");compose.onNodeWithContentDescription("Opciones del perfil").performClick();compose.onNodeWithText("Editar perfil").performClick();text("Editar perfil")
        compose.onNodeWithText("Cambiar fotografía").assertExists()
        compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasTestTag("birthday-year"));compose.onNodeWithTag("birthday-year").assertExists()
        compose.onNodeWithText("A · Gris").assertDoesNotExist();compose.onNodeWithText("B · Naranja-amarillo").assertDoesNotExist()
        compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasText("Identificar como yo"));compose.onNodeWithText("Identificar como yo").performClick();compose.onNodeWithText("Cancelar").performClick();compose.onNodeWithText("Descartar cambios").performClick()
        assertNull(runBlocking {app.database.cascadeRelationDao().identities().firstOrNull()})
        compose.onNodeWithContentDescription("Opciones del perfil").performClick();compose.onNodeWithText("Editar perfil").performClick();text("Editar perfil")
        await {runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasText("Apodo"));compose.onNodeWithText("Apodo").performTextReplacement("Ana editada")
        compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasText("Identificar como yo"));compose.onNodeWithText("Identificar como yo").performClick();compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.personDao().get("person")!!.name=="Ana editada" && app.database.cascadeRelationDao().identities().firstOrNull()?.personId=="person"}}
        assertEquals("1980-04-12",runBlocking {app.database.personGalleryDao().birthdays().single().date})
        assertEquals(2,runBlocking {app.database.personMemoryDao().images().size})
        assertNull(runBlocking {app.database.personDao().get("person")!!.avatarFile})
        text("Fotografías");compose.onNodeWithContentDescription("Opciones del perfil").performClick();compose.onNodeWithText("Editar perfil").performClick();text("Editar perfil")
        await {runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasText("Identificar como yo"));compose.onNodeWithText("Identificar como yo").performClick();compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.cascadeRelationDao().identities().firstOrNull()==null}}
    }
    @Test fun technologyPanelsFoldWithoutLosingQueryOrProvisionalProficiency() {
        runBlocking {
            app.personRepository.save("person","Ana",null)
            app.database.technologyDao().insert(TechnologyEntity("tech","Kotlin",null))
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository,initialPersonId="person") {}}}
        text("Fotografías");section("Tecnologías");text("Tecnologías existentes")
        compose.onNodeWithText("Sin coincidencias disponibles.").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("Kotl")
        compose.onNodeWithTag("modular-fold:Tecnologías").performClick()
        compose.onNodeWithTag("modular-reopen:Tecnologías").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).assert(hasText("Kotl"))
        compose.onNodeWithText("Kotlin").performClick();text("Guardar dominio")
        val proficiency=PersonProficiency.entries.last()
        compose.onNodeWithText(proficiency.label,substring=true).performClick()
        compose.onNodeWithTag("modular-fold:Tecnologías").performClick()
        compose.onNodeWithTag("modular-reopen:Tecnologías").performClick()
        compose.onNodeWithText("✓ ${proficiency.label}").assertExists()
        compose.onNodeWithText("Guardar dominio").performClick()
        await {runBlocking {app.database.personIntegrationDao().technologies().singleOrNull()?.proficiency==proficiency.name}}
        section("Tecnologías");text("Tecnologías existentes")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("modular-reopen:Tecnologías").assertDoesNotExist()
    }

    @Test fun relationPanelFitsFewPeopleAndRetainsItsDraftWhenFolded() {
        runBlocking {
            app.personRepository.save("person","Ana",null)
            app.personRepository.save("other","Bea",null)
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository,initialPersonId="person") {}}}
        text("Fotografías");section("Relaciones")
        compose.onNodeWithText("Añadir relación").performClick();text("Buscar Persona relacionada")
        val search=compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val cancel=compose.onNodeWithText("Cancelar").fetchSemanticsNode().boundsInRoot
        assertTrue("El selector no debe dejar un gran espacio vacío",cancel.top-search.bottom<search.height*4)
        compose.onNode(hasSetTextAction()).performTextReplacement("Be")
        compose.onNodeWithTag("modular-fold:Añadir relación").performTouchInput {
            swipe(center,center+androidx.compose.ui.geometry.Offset(0f,160f),durationMillis=400)
        }
        compose.onNodeWithTag("modular-reopen:Añadir relación").performClick()
        compose.onNode(hasSetTextAction()).assert(hasText("Be"))
        compose.onNodeWithText("Bea").performClick();text("Nombre del vínculo")
        compose.onNodeWithText("Nombre del vínculo").performTextReplacement("Familia")
        compose.onNodeWithTag("modular-fold:Añadir relación").performClick()
        compose.onNodeWithTag("modular-reopen:Añadir relación").performClick()
        compose.onNodeWithText("Nombre del vínculo").assert(hasText("Familia"))
        compose.onNodeWithText("Guardar vínculo").assertIsDisplayed().performClick()
        await {runBlocking {app.database.personProfileDao().relations().any {it.label=="Familia"}}}
        compose.onNodeWithText("Añadir relación").performClick();text("Buscar Persona relacionada")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("modular-reopen:Añadir relación").assertDoesNotExist()
    }

}
