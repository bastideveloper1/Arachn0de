package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.templates.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class SimplificationTemplatesUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        stopAppearanceTestApplication(app)
    }
    @Test fun retiredMenuAccessIsAbsentAndTemplateLibraryFiltersTypesAndPreviewsBeforeInsertion() {
        runBlocking {
            app.database.projectDao().insert(ProjectEntity("ui-core","Núcleo de ejemplo","",0,1,1))
            app.database.nodeDao().insert(NodeEntity("ui-note","ui-core",null,"Nota de ejemplo","Contenido",false,0,1,1,purpose="NOTE"))
            val repo=app.savedTemplateRepository
            repo.save(repo.captureStructure(app.privateContext,app.attachmentRepository,"ui-core",null).copy(name="Plantilla de Núcleo"),true)
            repo.save(repo.captureStructure(app.privateContext,app.attachmentRepository,"ui-core","ui-note").copy(name="Plantilla de Nota"),true)
        }
        var view by mutableStateOf(0)
        compose.setContent {Arachn0deTheme {
            key(view) {when(view) {
                0->AppIdentityDrawer({},onProjects={},onTechnologies={},onTags={},onObligations={})
                1->StructureCreationPanel(null,null,onNew={},onDismiss={})
                2->StructureCreationPanel("ui-core",null,onNew={},onDismiss={})
                else->StructureCreationPanel(null,null,onNew={},draftDestination=true,onDismiss={})
            }}
        }}
        compose.onNodeWithText("Organizar").performClick()
        compose.onNodeWithText("Etiquetas").assertExists()
        listOf("Tecnologías","Modo Proyecto","Kanban","Gantt","Sprint").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        compose.runOnIdle {view=1}
        compose.onNodeWithText("Desde plantilla").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Plantilla de Núcleo").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Plantilla de Nota").assertDoesNotExist()
        compose.onNodeWithText("Plantilla de Núcleo").performClick()
        compose.onNodeWithText("Insertar").assertIsDisplayed()
        compose.onNode(hasText("2 elementos",substring=true)).assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle {view=2}
        compose.onNodeWithText("Desde plantilla").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Plantilla de Nota").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Plantilla de Núcleo").assertDoesNotExist()
        compose.runOnIdle {view=3}
        compose.onNodeWithText("Desde plantilla").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Plantilla de Nota").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Plantilla de Núcleo").assertDoesNotExist()
        assertEquals(1,runBlocking {app.database.projectDao().getAll()}.size)
        assertEquals("Inicio",antisocialSection("Tecnologías"))
    }
}
