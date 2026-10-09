package com.r0ybt.arachn0de.report

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.ObligationsScreen
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ObligationReportUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: Arachn0deApplication
    private val directory get() = File(app.cacheDir, ObligationReportFiles.CACHE_DIRECTORY)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            directory.deleteRecursively()
        }
        override fun after() { directory.deleteRecursively(); app.database.close() }
    }).around(compose)
    private fun mount(nodes: List<Node> = ReportFixture.nodes, restore: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            Arachn0deTheme {
                ObligationsScreen(NodeTreeSnapshot(nodes), ReportFixture.projects, listOf(ReportFixture.roy),
                    mapOf("oct" to listOf(ReportFixture.roy)), true, ReportFixture.now, ReportFixture.zone.id, {}, {})
            }
        }
        if (restore == null) compose.setContent(content) else restore.setContent(content)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Cargando Obligaciones…").fetchSemanticsNodes().isEmpty()
        }
    }
    @Test fun emptySelectionDisablesGenerationAndCreatesNoFile() {
        mount(emptyList())
        compose.onNodeWithTag("generate-obligation-report").assertIsNotEnabled()
        assertFalse(directory.exists())
    }
    @Test fun generationPresentsDeliveryOptionsRestoresCacheReferenceAndKeepsFilters() {
        val restoration = StateRestorationTester(compose)
        mount(restore = restoration)
        compose.onNodeWithText("Todo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("financial-currency:USD").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("obligations-list").performScrollToNode(hasTestTag("obligation-task:usd"))
        compose.onNodeWithTag("generate-obligation-report").assertIsDisplayed()
        compose.onNodeWithTag("generate-obligation-report").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Informe PNG listo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Compartir").assertIsEnabled()
        compose.onNodeWithText("Guardar PNG").assertIsEnabled()
        val files = directory.listFiles()!!.filter { it.extension == "png" }
        assertEquals(1, files.size)
        assertTrue(files.single().length() > 10000)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Informe PNG listo").assertExists()
        compose.onNodeWithText("Guardar PNG").performClick()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        compose.runOnUiThread {
            shadowOf(compose.activity).receiveResult(request.intent, android.app.Activity.RESULT_CANCELED, null)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardando informe…").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Informe PNG listo").assertExists()
        compose.onNodeWithText("Compartir").assertIsEnabled()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("obligations-list").performScrollToIndex(0)
        compose.onNodeWithText("Todo").assertIsSelected()
        compose.onNodeWithTag("generate-obligation-report").assertIsEnabled()
        assertEquals(27, app.database.openHelper.writableDatabase.version)
    }
    @Test fun cacheWriteFailureKeepsScreenAndFiltersAndAllowsRetry() {
        directory.writeText("block directory creation")
        mount()
        compose.onNodeWithTag("generate-obligation-report").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("No se pudo completar la operación").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithText("Este mes").assertIsSelected()
        compose.onNodeWithTag("generate-obligation-report").assertIsEnabled()
        assertTrue(directory.isFile)
    }
}
