package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.room.withTransaction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.data.local.PersonEntity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class PersonEvolutionUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            content.removeAllViews()
        }
        compose.waitForIdle();runBlocking {app.database.withTransaction {}};app.database.close()
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String)=compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun click(text:String) {await(text);val node=compose.onNodeWithText(text);if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo();node.performClick()}
    private fun scroll(text:String)=compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag("participation-list").performScrollToNode(hasText(text))}.isSuccess}
    @Test fun listToFullPersonToRelatedPersonAndPredictableBack() {
        runBlocking {app.personRepository.save("a","Ana",null);app.personRepository.save("b","Bruno",null)}
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository) {}}}
        click("Ana");await("Relaciones");click("Relaciones");click("Añadir relación");click("Bruno");click("madre");click("Guardar vínculo")
        await("madre de");click("Bruno");await("Bruno");click("Relaciones");await("hijo/a de")
        click("Volver");await("Ana");click("Relaciones");click("⋮");click("Editar vínculo");click("padre");click("Guardar vínculo");await("padre de")
        runBlocking {assertEquals("padre",app.database.personProfileDao().relations().single().label)}
        click("⋮");click("Eliminar vínculo");click("Cancelar");runBlocking {assertEquals(1,app.database.personProfileDao().relations().size)}
        click("⋮");click("Eliminar vínculo");click("Eliminar");await("Sin relaciones.")
        click("Volver");await("Nueva Persona")
        runBlocking {assertTrue(app.database.personProfileDao().relations().isEmpty());assertEquals(2,app.database.backupDao().persons().size)}
    }
    @Test fun sectionSaveAndCancelDoNotResetOtherFieldsOrIdentity() {
        runBlocking {app.personRepository.save("a","Ana",null);app.personRepository.saveProfile(com.r0ybt.arachn0de.data.local.PersonProfileEntity("a",skills="Existing skill"))}
        compose.setContent {Arachn0deTheme {PeopleScreen(app.personRepository,initialPersonId="a") {}}}
        click("Editar General");compose.onNodeWithText("Alias").performTextReplacement("Anita");click("Guardar sección")
        await("Anita");click("Contacto");click("Editar Contacto");compose.onNodeWithText("Teléfonos").performTextReplacement("123");click("Cancelar")
        runBlocking {val p=app.database.personProfileDao().profiles().single();assertEquals("Anita",p.alias);assertEquals("Existing skill",p.skills);assertEquals("",p.phones);assertEquals("Ana",app.database.personDao().get("a")!!.name)}
    }
    @Test fun emptyPersonAndHundredPeopleAreSearchableWithLargeFont() {
        runBlocking {repeat(100) {app.personRepository.save("p$it","Persona $it",null)}}
        compose.setContent {Arachn0deTheme {val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,1.6f)) {PeopleScreen(app.personRepository) {}}}}
        await("Buscar Personas");compose.onNodeWithText("Buscar Personas").performTextReplacement("Persona 99");compose.onNode(hasText("Persona 99") and hasAnyAncestor(hasTestTag("people-gallery"))).performClick();await("Sin información adicional.");click("Relaciones");await("Sin relaciones.")
    }
}
