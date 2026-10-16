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
class PersonMemoryUiTest {
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
    @Test fun profileKeepsPrivateFieldsSectionsAndGalleryOnlyInGeneral() {
        setup();compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Biografía privada");text("https://example.org/perfil")
        compose.onNodeWithText("General").assertIsSelected()
        compose.onNodeWithText("Contacto").performClick();text("Editar Contacto")
        compose.onNodeWithTag("person-general").assertDoesNotExist()
        compose.onNodeWithText("123").assertExists()
        compose.onNodeWithText("General").performClick();text("Fotografías")
        openGallery();compose.onNodeWithText("Descripción del grupo").assertExists();compose.onNodeWithText("Descripción individual").assertExists()
        compose.onNodeWithTag("memory-carousel").performTouchInput {swipeLeft()}
        compose.mainClock.advanceTimeBy(1000);text("2/2")
        compose.onNodeWithText("Descripción individual").assertDoesNotExist()
        assertNull(runBlocking {app.database.personDao().get("person")!!.avatarFile})
    }
    @Test fun editingDescriptionsReordersCoverKeepsDatesAndCancellationDoesNotWrite() {
        setup();compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías");openGallery();compose.onNodeWithText("Editar").performClick();text("Descripción de la entrada")
        compose.onNodeWithText("Descripción de la entrada").performTextReplacement("Borrador")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("Descripción del grupo",runBlocking {app.database.personMemoryDao().memories().single().description})
        compose.onNodeWithText("Editar").performClick();text("Descripción de la entrada")
        compose.onNodeWithText("Descripción de la entrada").performTextReplacement("Guardada")
        compose.onAllNodesWithContentDescription("Mover después")[0].performClick()
        await {runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.personMemoryDao().memories().single().description=="Guardada"}}
        val post=runBlocking {app.database.personMemoryDao().memories().single()}
        val images=runBlocking {app.database.personMemoryDao().images()}
        assertEquals("1980-04-12",post.date)
        assertEquals("two",images.first().id)
        assertEquals("1981-01-02",images.first {it.id=="one"}.date)
        assertEquals("Descripción individual",images.first {it.id=="one"}.description)
        assertNull(runBlocking {app.database.personDao().get("person")!!.avatarFile})
    }
}
