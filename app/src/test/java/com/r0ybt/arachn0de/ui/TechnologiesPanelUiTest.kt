package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class TechnologiesPanelUiTest {
    private var testRepository:com.r0ybt.arachn0de.data.repository.TechnologyRepository?=null
    @After fun close() {testRepository?.close()}
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun await(test:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();test()
    }
    @Test fun createsEditsAndClosesUsingTheExistingRepository() {
        val app=compose.activity.application as Arachn0deApplication
        val repository=com.r0ybt.arachn0de.data.repository.TechnologyRepository(app.database,
            com.r0ybt.arachn0de.data.local.TechnologyIconStore(app,com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
        testRepository=repository
        var closed=false
        compose.setContent {Arachn0deTheme {TechnologiesScreen(repository) {closed=true}}}
        await {compose.onAllNodesWithText("Nueva tecnología").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Nueva tecnología").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Referencia")
        compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.technologyDao().catalog().any {it.name=="Referencia"}}}
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Nombre").performTextClearance()
        compose.onNodeWithText("Nombre").performTextInput("Editada")
        compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.technologyDao().catalog().any {it.name=="Editada"}}}
        compose.onNodeWithText("Eliminar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cerrar tecnologías").assertIsDisplayed().performClick()
        assertTrue(closed)
    }
}
