package com.r0ybt.arachn0de.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.TechnologyRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class TechnologyUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as Arachn0deApplication
    private lateinit var repository: TechnologyRepository
    @Before fun setup() {
        repository = TechnologyRepository(app.database, TechnologyIconStore(app, BackupFixture::syncDirectory))
    }
    @After fun close() { repository.close() }
    private fun library() {
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { TechnologiesScreen(repository) {} } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Nueva tecnología").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun create(name: String) {
        compose.onNodeWithText("Nueva tecnología").performClick()
        compose.onNodeWithText("Nombre").performTextInput(name)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.technologyDao().catalog().any { it.name == name } } }
    }
    @Test fun libraryCreateEditAndConfirmedDeleteLeaveTheOwnersUntouched() {
        val project = runBlocking { app.projectRepository.createProject("Project") }
        library(); create("Python")
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Python personalizada")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Python personalizada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("Eliminar tecnología").assertExists()
        assertEquals(1, runBlocking { app.database.technologyDao().catalog().size })
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1, runBlocking { app.database.technologyDao().catalog().size })
        compose.onNodeWithText("Eliminar").performClick()
        compose.onAllNodesWithText("Eliminar").onLast().performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.technologyDao().catalog().isEmpty() } }
        assertNotNull(runBlocking { app.database.projectDao().getById(project.id) })
    }
    @Test fun pickerImportsPrivateIconAndReplacingItRemovesOnlyPreviousUnsharedFile() {
        library(); create("Kotlin")
        fun selectIcon(color: Int) {
            val beforeCount = File(app.filesDir, "technology-icons").listFiles().orEmpty().size
            compose.onNodeWithText("Seleccionar icono").performClick()
            val launched = shadowOf(compose.activity).nextStartedActivityForResult
            val file = File(app.cacheDir, "technology-picker.png")
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(launched.requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
            compose.waitUntil(10_000) { File(app.filesDir, "technology-icons").listFiles().orEmpty().size > beforeCount && compose.onAllNodesWithText("Guardar").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNodeWithText("Editar").performClick(); selectIcon(android.graphics.Color.BLUE)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.technologyDao().catalog().single().iconFile != null } }
        val old = runBlocking { app.database.technologyDao().catalog().single().iconFile!! }
        compose.onNodeWithText("Editar").performClick(); selectIcon(android.graphics.Color.RED)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.technologyDao().catalog().single().iconFile != old } }
        val new = runBlocking { app.database.technologyDao().catalog().single().iconFile!! }
        compose.waitUntil(10_000) { !File(app.filesDir,"technology-icons/$old").exists() }
        assertTrue(File(app.filesDir, "technology-icons/$new").isFile); assertFalse(File(app.filesDir, "technology-icons/$old").exists())
    }
    @Test fun emptyOwnerHasNoSectionAndReadOnlyNamesNeverRemoveAssignments() {
        val project=runBlocking {app.projectRepository.createProject("Project")}
        val task=runBlocking {app.nodeRepository.createNode(project.id,null,"Task")}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {TechnologyOwnerControl(task.id,false,"Task",repository)}}}
        compose.onNodeWithText("Tecnologías").assertDoesNotExist()
        runBlocking {repository.save("python","Python",null,true);repository.assign(task.id,false,setOf("python"))}
        compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Python").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Python").performClick()
        compose.onAllNodesWithText("Python").onFirst().assertExists();compose.onNodeWithText("Quitar Python").assertDoesNotExist()
        compose.onNodeWithText("Cerrar").performClick()
        assertEquals(listOf("python"),runBlocking {app.database.technologyDao().nodes().map {it.technologyId}})
    }
    @Test fun editingUsesExplicitRemovalWhileNameOnlyShowsInformation() {
        val technology=TechnologyEntity("tool","Python",null)
        var selected=listOf("tool")
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            val ids=androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf(selected)}
            TechnologySelection(listOf(technology),ids.value,true) {ids.value=it;selected=it}
        }}}
        compose.onNodeWithText("Tecnologías (1)").performClick()
        compose.onAllNodesWithContentDescription("Python").onFirst().performClick()
        assertEquals(listOf("tool"),selected)
        compose.onNodeWithText("Cancelar").performClick();assertEquals(listOf("tool"),selected)
        compose.onNodeWithText("Tecnologías (1)").performClick()
        compose.onAllNodesWithContentDescription("Python").onFirst().performClick()
        compose.onNodeWithText("Confirmar").performClick();assertTrue(selected.isEmpty())
        compose.onNodeWithText("Tecnologías (0)").performClick()
        compose.onAllNodesWithContentDescription("Python").onFirst().performClick()
        compose.onNodeWithText("Confirmar").performClick();assertEquals(listOf("tool"),selected)
    }
    @Test fun heldDragReordersSelectedTechnologiesWithoutRemovingThem() {
        val catalog=listOf(TechnologyEntity("a","Alpha",null),TechnologyEntity("b","Beta",null),TechnologyEntity("c","Gamma",null))
        var selected=listOf("a","b","c")
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            val ids=androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf(selected)}
            TechnologySelection(catalog,ids.value,true) {ids.value=it;selected=it}
        }}}
        compose.onNodeWithText("Tecnologías (3)").performClick()
        compose.onNodeWithText("Ordenar seleccionadas").performClick()
        val from=compose.onNodeWithContentDescription("Arrastrar Alpha").fetchSemanticsNode().boundsInRoot.center
        val to=compose.onNodeWithContentDescription("Arrastrar Beta").fetchSemanticsNode().boundsInRoot.center
        assertTrue(to.y>from.y)
        compose.mainClock.autoAdvance=false
        try {
            compose.onAllNodes(isRoot()).onLast().performTouchInput {down(from);advanceEventTime(600)}
            compose.mainClock.advanceTimeBy(650)
            compose.onAllNodes(isRoot()).onLast().performTouchInput {moveTo(from)}
            compose.mainClock.advanceTimeByFrame()
            repeat(6) {step->compose.onAllNodes(isRoot()).onLast().performTouchInput {moveTo(from+(to-from)*((step+1)/6f),delayMillis=40)};compose.mainClock.advanceTimeByFrame()}
            compose.onAllNodes(isRoot()).onLast().performTouchInput {up()}
        } finally {compose.mainClock.autoAdvance=true}
        compose.waitForIdle();compose.onNodeWithText("Confirmar").performClick();assertEquals(listOf("b","a","c"),selected)
    }
    @Test fun compactOrderHiddenCountAndWrappedFullNamesFitNarrowWidth() {
        val catalog=(1..4).map {TechnologyEntity("$it","Tecnología original número $it",null)}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {Column(Modifier.width(180.dp)) {TechnologyLabels(catalog.reversed(),true)}}}}
        compose.onNodeWithText("+1").assertExists()
        compose.onNodeWithContentDescription(catalog[0].name).assertDoesNotExist()
        compose.onNodeWithContentDescription(catalog[3].name).performClick()
        compose.onNodeWithText(catalog[3].name).assertExists();compose.onNodeWithText("Cerrar").performClick()
    }
    @Test fun largeCatalogIsCompactSearchableAndCancelKeepsOrder() {
        val catalog=(1..500).map {TechnologyEntity("$it","Herramienta $it",null)}
        var selected=listOf("500","2","1")
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            val ids=androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf(selected)}
            TechnologySelection(catalog,ids.value,true) {ids.value=it;selected=it}
        }}}
        compose.onNodeWithText("Herramienta 1").assertDoesNotExist()
        compose.onNodeWithText("Tecnologías (3)").performClick()
        assertTrue(compose.onAllNodes(hasTestTag("technology-tile:500")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Buscar tecnologías").performTextInput("Herramienta 499")
        compose.onNodeWithContentDescription("Herramienta 499").performClick()
        compose.onNodeWithText("Cancelar").performClick();assertEquals(listOf("500","2","1"),selected)
        compose.onNodeWithText("Tecnologías (3)").performClick()
        compose.onNodeWithText("Ordenar seleccionadas").performClick()
        compose.onNodeWithContentDescription("Subir Herramienta 2").performClick()
        compose.onNodeWithText("Confirmar").performClick();assertEquals(listOf("2","500","1"),selected)
    }
    @Test fun consultationGridShowsAssignedNamesWithoutRemoval() {
        val catalog=(1..100).map {TechnologyEntity("$it","Herramienta $it",null)}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {TechnologyLabels(catalog,true)}}}
        compose.onNodeWithText("+97").performClick()
        compose.onNodeWithText("Buscar tecnologías").performTextInput("Herramienta 100")
        compose.onNodeWithContentDescription("Herramienta 100").performClick()
        compose.onNodeWithText("Quitar Herramienta 100").assertDoesNotExist()
        compose.onNodeWithText("Cerrar").performClick()
    }
    @Test fun technologiesAndParticipantsShareNarrowSpaceWithLargeFonts() {
        val project=runBlocking {app.projectRepository.createProject("Proyecto")}
        val task=runBlocking {app.nodeRepository.createNode(project.id,null,"Tarea")}
        runBlocking {repository.save("tool","Herramienta",null,true);repository.assign(task.id,false,setOf("tool"))}
        compose.runOnUiThread {compose.activity.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.8f)) {
                Arachn0deTheme {androidx.compose.foundation.layout.Box(Modifier.width(280.dp).testTag("available")) {
                    CompactAssignments(task.id,listOf(com.r0ybt.arachn0de.domain.model.Person("person","Participante")))
                }}
            }
        }}
        compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Herramienta").fetchSemanticsNodes().isNotEmpty()}
        val available=compose.onNodeWithTag("available").fetchSemanticsNode().boundsInRoot
        for(name in listOf("Herramienta","Participante")) {
            val node=compose.onNodeWithContentDescription(name).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(node.left>=available.left && node.right<=available.right)
        }
        compose.onNodeWithContentDescription("Participante").performClick()
        compose.onAllNodesWithText("Participante").onFirst().assertExists()
        compose.onNodeWithText("Cerrar").performClick()
    }
    @Test fun navigationMenuOpensLocalTechnologyCatalog() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Tecnologías").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Nueva tecnología").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nueva tecnología").assertExists(); compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithContentDescription("Abrir menú").assertExists()
    }
}
