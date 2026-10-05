package com.r0ybt.arachn0de.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class CreationDefaultsUiTest {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var app:Arachn0deApplication
    private lateinit var project:Project
    private lateinit var layer:Node
    private lateinit var old:Node
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app=ApplicationProvider.getApplicationContext();runBlocking {
            project=app.projectRepository.createProject("Defaults project")
            old=app.nodeRepository.createNode(project.id,null,"Old task")
            layer=app.nodeRepository.createNode(project.id,null,"Cuentas",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            app.nodeRepository.createNode(project.id,layer.id,"Child")
        } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(label:String) { try { compose.waitUntil(10000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() } } catch(t:Throwable) { throw AssertionError("Esperando $label: ${compose.onRoot().printToString()}",t) } }
    private var openGlobalDefaults: (() -> Unit)? = null
    // The drawer entry was retired. Keep testing the retained defaults component directly.
    private fun mount() {
        compose.activity.setContent {
            com.r0ybt.arachn0de.ui.theme.Arachn0deTheme {
                val global = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                openGlobalDefaults = { global.value = true }
                androidx.compose.foundation.layout.Box {
                    AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) }
                    if (global.value) AppSafeArea {
                        CreationDefaultsScreen(app.nodeRepository.creationDefaults, DefaultsScope.Global, "Global", emptyList(), emptyList()) { global.value = false }
                    }
                }
            }
        }
    }
    private fun recreateHost() { compose.activityRule.scenario.recreate(); mount() }
    private fun open() { mount(); await("Defaults project");compose.onNodeWithText("Defaults project").performClick();await("Nuevo elemento") }
    private fun settings() { compose.runOnIdle { checkNotNull(openGlobalDefaults).invoke() }; await("Guardar valores predeterminados") }
    private fun choose(label:String,value:String) { compose.onNodeWithTag("defaults-choice:$label").performScrollTo().performClick();compose.onNodeWithText(value).performClick() }
    private fun save() { compose.onNodeWithText("Guardar valores predeterminados").performClick();await("Configuración guardada");compose.onNodeWithText("Guardar valores predeterminados").assertExists();compose.onNodeWithText("Volver").performClick();await("Nuevo elemento") }
    private fun fresh() { compose.onNodeWithText("Nuevo elemento").performClick();await("Título") }
    @Test fun dueHourWithoutDefaultDateSeedsPickerAndOverrideKeepsGlobalAfterRecreation() {
        open(); settings(); choose("Hora de vencimiento", "09:00")
        compose.onNodeWithText("Hora de vencimiento (24 h · HH:mm)").performScrollTo().performTextReplacement("20:00")
        save()
        recreateHost(); await("Nuevo elemento")
        fresh()
        recreateHost(); await("Título")
        compose.onNodeWithText("Título").performTextInput("Hora propia")
        compose.onNodeWithTag("option:Vencimiento").performScrollTo().performClick()
        compose.onNodeWithText("Vencimiento: Sin fecha").performScrollTo().performClick()
        compose.onNodeWithText("Elegir hora").performClick()
        val inputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("task-time-input")))
        inputs[0].assert(hasText("20")); inputs[1].assert(hasText("00"))
        inputs[0].performTextReplacement("18"); inputs[1].performTextReplacement("30")
        compose.onNodeWithText("Aplicar").performClick()
        compose.onNodeWithText("Crear").performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getProjectNodes(project.id).any { it.title == "Hora propia" } } }
        val task = runBlocking { app.nodeRepository.getProjectNodes(project.id).single { it.title == "Hora propia" } }
        val time = java.util.Calendar.getInstance().apply { timeInMillis = task.dueAt!! }
        assertEquals(18, time.get(java.util.Calendar.HOUR_OF_DAY)); assertEquals(30, time.get(java.util.Calendar.MINUTE))
        assertEquals(DefaultTime.Minute(1200), runBlocking { app.nodeRepository.creationDefaults.configuration(DefaultsScope.Global).effective.dueTime })
        recreateHost(); await("Nuevo elemento")
        fresh()
        compose.onNodeWithTag("option:Vencimiento").performScrollTo().performClick()
        compose.onNodeWithText("Vencimiento: Sin fecha").performScrollTo().performClick()
        compose.onNodeWithText("Elegir hora").performClick()
        inputs[0].assert(hasText("20")); inputs[1].assert(hasText("00"))
    }

    @Test fun globalSettingsPersistAndUnsavedSettingsSurviveActivityRecreation() {
        runBlocking { app.nodeRepository.creationDefaults.save(DefaultsScope.Global,CreationDefaults(obligation=DefaultValue.Own(true),priority=DefaultValue.Own(Priority.HIGH),start=DefaultValue.Own(DefaultDate(DefaultDateKind.TOMORROW)),due=DefaultValue.Own(DefaultDate(DefaultDateKind.TODAY)))) }
        open();settings();choose("Moneda","USD");choose("Tipo","Nota")
        recreateHost();await("Guardar valores predeterminados")
        compose.onNodeWithTag("defaults-choice:Tipo").performScrollTo().assert(hasText("Elegido aquí · Nota"))
        save();assertEquals(NodePurpose.NOTE,runBlocking { app.nodeRepository.creationDefaults.configuration(DefaultsScope.Global).effective.purpose })
        fresh();compose.onNodeWithText("Título").performTextInput("Nueva nota");compose.onNodeWithText("Crear").performClick()
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getProjectNodes(project.id).any { it.title=="Nueva nota" } } }
        val note=runBlocking { app.nodeRepository.getProjectNodes(project.id).single { it.title=="Nueva nota" } }
        assertEquals(NodePurpose.NOTE,note.purpose);assertEquals(Priority.NONE,note.priority);assertNull(note.dueAt)
    }
    @Test fun currentLayerOverridesOnlyOnePropertyAndResetRestoresHeritage() {
        runBlocking { app.nodeRepository.creationDefaults.save(DefaultsScope.Global,CreationDefaults(currency=DefaultValue.Own("USD"),priority=DefaultValue.Own(Priority.MEDIUM))) }
        open();compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Cuentas"));compose.onNode(hasText("Cuentas") and hasAnyAncestor(hasTestTag("nodes-list"))).performClick();compose.onNodeWithTag("nodes-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Opciones del elemento").performScrollTo().performClick();compose.onNodeWithText("Valores predeterminados").performScrollTo().performClick();await("Guardar valores predeterminados")
        compose.onNodeWithTag("defaults-choice:Moneda").performScrollTo().assert(hasText("Heredar · USD"))
        choose("Moneda","CLP")
        compose.runOnUiThread {
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.OnBackPressedDispatcherOwner
            dialog.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("¿Descartar cambios de configuración?").assertExists()
        compose.onNodeWithText("Seguir editando").performClick();save()
        val effective=runBlocking { app.nodeRepository.creationDefaults.resolve(project.id,layer.id) };assertEquals("CLP",effective.currency);assertEquals(Priority.MEDIUM,effective.priority)
        compose.onNodeWithContentDescription("Opciones del elemento").performScrollTo().performClick();compose.onNodeWithText("Valores predeterminados").performScrollTo().performClick();await("Guardar valores predeterminados")
        compose.onNodeWithText("Restablecer herencia").performScrollTo().performClick();compose.onNodeWithText("Cancelar").performClick()
        assertEquals("CLP",runBlocking { app.nodeRepository.creationDefaults.resolve(project.id,layer.id).currency })
        compose.onNodeWithText("Restablecer herencia").performScrollTo().performClick();compose.onNodeWithText("Restablecer").performClick();await("Configuración guardada");compose.onNodeWithText("Volver").performClick();await("Nuevo elemento")
        assertEquals("USD",runBlocking { app.nodeRepository.creationDefaults.resolve(project.id,layer.id).currency })
    }
    @Test fun existingClosedDraftKeepsTypeAcrossGlobalChangeAndDiscardUsesNewDefaults() {
        open();fresh();compose.onNodeWithText("Título").performTextInput("Borrador anterior");compose.onNodeWithText("Cerrar").performClick()
        settings();choose("Tipo","Nota");save();fresh()
        compose.onNodeWithText("Borrador anterior").assertExists();compose.onNodeWithText("Tarea").assertExists()
        compose.onNodeWithText("Descartar").performScrollTo().performClick();compose.onNodeWithText("Descartar borrador").performClick()
        fresh();compose.onNodeWithText("Nota").assertExists()
    }
    @Test fun invalidDayAndHourBlockSaveAndCorrectionPersistsRules() {
        open();settings();choose("Vencimiento","Día 1 del mes")
        compose.onNodeWithText("Día del mes").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Guardar valores predeterminados").assertIsNotEnabled()
        compose.onNodeWithText("Día del mes").performTextReplacement("31")
        choose("Hora de vencimiento","09:00")
        compose.onNodeWithText("Hora de vencimiento (24 h · HH:mm)").performScrollTo().performTextReplacement("25:88")
        compose.onNodeWithText("Guardar valores predeterminados").assertIsNotEnabled()
        compose.onNodeWithText("Hora de vencimiento (24 h · HH:mm)").performTextReplacement("22:00")
        compose.onNodeWithText("Guardar valores predeterminados").assertIsEnabled()
        save();val effective=runBlocking { app.nodeRepository.creationDefaults.configuration(DefaultsScope.Global).effective }
        assertEquals(DefaultDate(DefaultDateKind.DAY_OF_MONTH,31),effective.due);assertEquals(DefaultTime.Minute(1320),effective.dueTime)
    }
    @Test fun editingExistingNodeIgnoresGlobalNoteAndDates() {
        runBlocking { app.nodeRepository.creationDefaults.save(DefaultsScope.Global,CreationDefaults(purpose=DefaultValue.Own(NodePurpose.NOTE),due=DefaultValue.Own(DefaultDate(DefaultDateKind.TODAY)))) }
        open();compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("node-options:${old.id}"));compose.onNodeWithTag("node-options:${old.id}").performClick();compose.onNodeWithText("Editar").performScrollTo().performClick()
        await("Título");compose.onNode(hasText("Old task") and hasSetTextAction()).assertExists()
        assertEquals(old,runBlocking { app.nodeRepository.getProjectNodes(project.id).single { it.id==old.id } })
    }
}
