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
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[33],qualifiers="w360dp-h891dp")
class PersonMemoryPeopleUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var people:PersonRepository
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    @Before fun setup()=runBlocking<Unit> {
        people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))
        people.save("owner","Yo",null);people.save("ana","Ana",null);people.save("pedro","Pedro",null)
        val source=java.io.File(app.cacheDir,"people-post.png").apply {writeBytes(BackupFixture.png())}
        val name=people.importAvatar(android.net.Uri.fromFile(source));people.savePhoto(PersonPhotoEntity("legacy","owner",name,0),"seed")
        app.database.personMemoryDao().clear()
        app.database.personMemoryDao().save(PersonMemoryEntity("post","owner",null,"Publicación",0))
        app.database.personMemoryDao().images(listOf(PersonMemoryImageEntity("photo","post",null,"legacy",0)))
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump()
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin();app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}}
        app.database.close()
    }
    private fun open() {
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="owner") {}}}
        await {compose.onAllNodesWithTag("memory:post").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("person-general").performScrollToNode(hasTestTag("memory:post"))
        compose.onNodeWithTag("memory:post").performClick()
        await {compose.onAllNodesWithText("Editar").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun editPeople() {compose.onNodeWithText("Editar").performClick();compose.onNodeWithTag("memory-field-people").performClick()}
    @Test fun contextualPanelSavesMultiplePeopleVisibilityAndCancellationIndependently() {
        open();editPeople()
        compose.onNodeWithTag("memory-person-name").performScrollTo().performTextInput("Invitada")
        compose.onNodeWithText("Añadir nombre libre").performScrollTo().performClick()
        compose.onNodeWithTag("memory-profile:ana").performScrollTo().performClick()
        compose.onNodeWithTag("memory-people-visible").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.database.personMemoryDao().people().size==2}}
        assertFalse(runBlocking {app.database.personMemoryDao().memories().single().peopleVisible})
        compose.onNodeWithTag("memory-field-people").performClick()
        compose.onNodeWithTag("memory-person-name").performScrollTo().performTextReplacement("Descartar")
        compose.onNodeWithText("Añadir nombre libre").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(2,runBlocking {app.database.personMemoryDao().people().size})
        compose.onNodeWithContentDescription("Cerrar entrada").performClick()
        compose.onNodeWithText("Personas presentes").assertDoesNotExist()
        compose.onNodeWithText("Invitada").assertDoesNotExist()
        assertEquals(3,runBlocking {app.database.backupDao().persons().size})
    }
    @Test fun aFreeReferenceCanLinkToProfileAndOpenItThenReturnToSamePublication() {
        open();editPeople()
        compose.onNodeWithTag("memory-person-name").performScrollTo().performTextInput("Compañera")
        compose.onNodeWithText("Añadir nombre libre").performScrollTo().performClick()
        compose.onNodeWithText("Compañera").assertExists()
        compose.onNodeWithText("Compañera").performScrollTo().performClick()
        compose.onNodeWithText("Vincular a perfil").performScrollTo().performClick()
        compose.onNodeWithTag("memory-profile:ana").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.database.personMemoryDao().people().singleOrNull()?.personId=="ana"}}
        val stored=runBlocking {app.database.personMemoryDao().people().single()};assertEquals("Compañera",stored.label)
        compose.onNodeWithContentDescription("Cerrar entrada").performClick()
        compose.onNodeWithTag("memory-person:${stored.id}").performScrollTo().performClick()
        await {compose.onAllNodesWithText("Antisocial · Ana").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("Volver").onLast().performClick()
        await {compose.onAllNodesWithTag("memory-viewer").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("memory-person:${stored.id}").assertExists()
    }
}
