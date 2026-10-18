package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.ProjectEntity
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagPanelAdminTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:Arachn0deDatabase
    @Before fun setup() {RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(RuntimeEnvironment.getApplication())}
    @After fun close() {db.close();RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db")}
    private fun pump() {
        compose.runOnUiThread {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
    }
    private fun awaitUi(condition:()->Boolean) {
        try {compose.waitUntil(15000) {pump();condition()}}
        catch(e:androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError(compose.onNodeWithTag("tag-panel",useUnmergedTree=true).printToString(),e)
        }
    }
    private fun awaitTag(name:String)=awaitUi {compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()}


    @Test fun catalogCreatesRenamesAndConfirmsDeletionWithoutDeletingOwners() {
        val nodes=NodeRepository(db) {1L}
        val setup=runBlocking {
            db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))
            val tag=nodes.tags.create("Compartida")
            val layer=nodes.createNode("p",null,"Capa",purpose=NodePurpose.LAYER,tagIds=setOf(tag.id))
            val task=nodes.createNode("p",layer.id,"Tarea",tagIds=setOf(tag.id))
            Triple(tag,layer,task)
        }
        val (tag,layer,task)=setup
        val initial=runBlocking {nodes.tags.observe().first()}
        compose.setContent {Arachn0deTheme {TagManager(nodes.tags,initial,showAccess=false,initiallyOpen=true)}}
        compose.onNodeWithTag("tag-choice:${tag.id}").performClick()
        compose.onNodeWithTag("tag-edit").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Actualizada")
        compose.onNodeWithText("Guardar").assertIsEnabled().assertIsDisplayed().performClick()
        awaitTag("Actualizada")
        awaitUi {compose.onAllNodesWithTag("tag-delete").fetchSemanticsNodes().isNotEmpty()}
        runBlocking {
            assertEquals(listOf(tag.id),db.tagDao().nodeIds(layer.id))
            assertEquals(listOf(tag.id),db.tagDao().nodeIds(task.id))
        }
        compose.onNodeWithTag("tag-delete").performClick()
        compose.onNode(hasText("2 elementos y plantillas",substring=true)).assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        runBlocking {assertNotNull(db.tagDao().get(tag.id))}
        compose.onNodeWithTag("tag-delete").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        awaitUi {compose.onAllNodesWithTag("tag-choice:${tag.id}").fetchSemanticsNodes().isEmpty()}
        runBlocking {
            assertNotNull(nodes.getNode(layer.id));assertNotNull(nodes.getNode(task.id))
            assertTrue(db.tagDao().nodeIds(layer.id).isEmpty());assertTrue(db.tagDao().nodeIds(task.id).isEmpty())
            assertNotNull(db.projectDao().getById("p"))
        }
        compose.onNodeWithTag("tag-new").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("  Nueva   Etiqueta  ")
        compose.onNodeWithText("Guardar").assertIsEnabled().assertIsDisplayed().performClick()
        awaitTag("Nueva Etiqueta")
        awaitUi {compose.onAllNodesWithText("Cerrar").fetchSemanticsNodes().isNotEmpty()}
        runBlocking {assertEquals("nueva etiqueta",nodes.tags.observe().first().tags.single().normalizedName)}
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("tag-panel").assertDoesNotExist()
    }
}
