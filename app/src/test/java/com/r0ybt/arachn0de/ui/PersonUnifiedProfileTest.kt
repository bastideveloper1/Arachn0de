package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class PersonUnifiedProfileTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump()
        runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}}
        app.database.close()
    }
    private fun edit() {
        await {compose.onAllNodesWithContentDescription("Opciones del perfil").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Opciones del perfil").performClick()
        compose.onNodeWithText("Editar perfil").performClick()
        await {compose.onAllNodesWithTag("person-profile-fields").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun field(label:String)=compose.onNodeWithText(label).also {
        compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasText(label))
    }
    @Test fun unifiedEditorRestoresDraftConfirmsDiscardAndPreservesOtherSections() {
        val original=PersonProfileEntity("person",alias="Ani",description="b".repeat(180),notes="Notas privadas",phones="123",emails="a@example.org",addresses="Dirección",links="www.google.com\nhttps://example.org",qualities="Cualidad",skills="Habilidad",interests="Interés",availability="Horario")
        runBlocking {app.personRepository.save("person","Ana",null);app.personRepository.saveProfile(original);app.personRepository.saveBirthday("person","1980-04-12")}
        val restore=StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {PeopleScreen(app.personRepository,initialPersonId="person") {}}}
        await {compose.onAllNodesWithText("Fotografías").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("Ana").assertCountEquals(1)
        compose.onNodeWithText("b".repeat(150)).assertExists()
        compose.onNodeWithContentDescription("Editar identidad").assertDoesNotExist()
        compose.onNodeWithContentDescription("Editar enlace web").assertDoesNotExist()
        compose.onNodeWithText("Leer descripción completa").assertDoesNotExist()
        edit();field("Apodo").performTextReplacement("Borrador")
        restore.emulateSavedInstanceStateRestore()
        field("Apodo").assertTextContains("Borrador")
        compose.onNodeWithText("Cancelar").performClick();compose.onNodeWithText("¿Descartar cambios?").assertExists()
        compose.onNodeWithText("Descartar cambios").performClick()
        assertEquals(original,runBlocking {app.database.personProfileDao().profiles().single()})
        edit();field("Nombre").performTextReplacement("Ana nueva");field("Apodo").performTextReplacement("Anita")
        field("Teléfonos").performTextReplacement("456\n789");field("Enlaces").performTextReplacement("www.google.com\nhttps://example.org/nuevo")
        field("Identificar como yo").performClick();compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.personDao().get("person")?.name=="Ana nueva"}}
        assertEquals(original.copy(alias="Anita",phones="456\n789",links="www.google.com\nhttps://example.org/nuevo"),runBlocking {app.database.personProfileDao().profiles().single()})
        assertEquals("1980-04-12",runBlocking {app.database.personGalleryDao().birthdays().single().date})
        assertEquals("person",runBlocking {app.database.cascadeRelationDao().identities().single().personId})
        edit();field("Apodo").assertTextContains("Anita");field("Teléfonos").assertTextContains("456\n789")
    }
    @Test fun headerUsesOnlyBiographyFirstWebAndMonthDayWithoutInventingAge() {
        var avatarTapped=false
        compose.setContent {Arachn0deTheme {PersonPhotoHeader(Person("person","Ana",null),PersonProfileEntity("person",alias="Ani",description="Biografía",notes="No soy biografía",links="www.google.com\nhttps://example.org"),birthday="--04-12",onEditAvatar={avatarTapped=true})}}
        compose.onNodeWithText("Ana").assertDoesNotExist();compose.onNodeWithText("Ani").assertExists()
        compose.onNodeWithText("Biografía").assertExists();compose.onNodeWithText("No soy biografía").assertDoesNotExist()
        compose.onNodeWithText("https://example.org").assertDoesNotExist()
        compose.onAllNodes(hasText("años",substring=true)).assertCountEquals(0)
        compose.onNodeWithText("www.google.com").performClick()
        assertEquals("https://www.google.com",org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity.data.toString())
        compose.onNodeWithTag("person-photo:person").performClick();assertTrue(avatarTapped)
        assertNull(personWebUri("javascript:alert(1)"))
    }
}
