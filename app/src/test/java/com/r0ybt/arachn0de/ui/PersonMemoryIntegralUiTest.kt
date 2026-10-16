package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[33],qualifiers="w360dp-h891dp")
class PersonMemoryIntegralUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var people:PersonRepository
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun text(value:String)=await {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump();runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}};app.database.close()
    }
    private fun setup(count:Int=1)=runBlocking {
        app.personRepository.save("person","Ana",null)
        people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))
        val file=java.io.File(app.cacheDir,"memory-integral.png").apply {writeBytes(BackupFixture.png())}
        val name=people.importAvatar(android.net.Uri.fromFile(file));people.savePhoto(PersonPhotoEntity("legacy-one","person",name,0),"seed");people.savePhoto(PersonPhotoEntity("legacy-two","person",name,1),"seed")
        val dao=app.database.personMemoryDao();dao.clear()
        repeat(count) {index->val id="entry$index";dao.save(PersonMemoryEntity(id,"person","1980-04-12","Compartida $index",index));dao.images(listOf(PersonMemoryImageEntity("one$index",id,null,"legacy-one",0,"1981-01-02",if(index==0) "Texto individual histórico" else ""),PersonMemoryImageEntity("two$index",id,null,"legacy-two",1)))}
    }
    private fun open() {compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.3f)) {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}};text("Fotografías")}
    private fun entry(id:String) {compose.onNodeWithTag("person-general").performScrollToNode(hasTestTag("memory:$id"));compose.onNodeWithTag("memory:$id").performClick();text("1/2")}
    @Config(qualifiers="w320dp-h891dp")
    @Test fun sharedEditorAndReadOnlyHistoryPreserveIndividualDescriptionsAndPhotoOrder() {
        setup();open();entry("entry0")
        compose.onNodeWithText("Compartida 0").assertExists();compose.onNodeWithText("Texto individual histórico").assertDoesNotExist()
        compose.onNodeWithText("Editar").performClick();text("Editar entrada")
        compose.onNodeWithText("Descripción de esta fotografía").assertDoesNotExist();compose.onNodeWithText("Esta fotografía").assertDoesNotExist()
        compose.onNodeWithText("Descripción de la entrada").performTextReplacement("Cancelar borrador");compose.onNodeWithText("Cancelar").performClick()
        assertEquals("Compartida 0",runBlocking {app.database.personMemoryDao().memories().single().description})
        compose.onNodeWithText("Editar").performClick();text("Editar entrada");compose.onNodeWithText("Descripción de la entrada").performTextReplacement("Compartida nueva")
        compose.onAllNodesWithContentDescription("Mover después")[0].performScrollTo().performClick()
        val window=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        compose.runOnIdle {
            val insets=androidx.core.view.WindowInsetsCompat.Builder().setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,160)).setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),true).build()
            fun composeView(view:android.view.View):android.view.View? {
                if(view.javaClass.simpleName=="AndroidComposeView") return view
                if(view is android.view.ViewGroup) for(i in 0 until view.childCount) composeView(view.getChildAt(i))?.let {return it}
                return null
            }
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(checkNotNull(composeView(window)),insets)
        }
        compose.onNodeWithText("Cancelar").assertIsDisplayed();compose.onNodeWithText("Guardar").assertIsDisplayed()
        assertTrue(compose.onNodeWithText("Guardar").fetchSemanticsNode().boundsInRoot.bottom<=window.height-160)
        await {runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess};compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.personMemoryDao().memories().single().description=="Compartida nueva"}}
        val images=runBlocking {app.database.personMemoryDao().images()}
        assertEquals("two0",images.first().id);assertEquals("Texto individual histórico",images.last().description);assertEquals("1981-01-02",images.last().date)
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("memory-options:entry0").performClick();compose.onNodeWithText("Textos históricos").performClick();text("Texto individual histórico")
        compose.onNodeWithText("Fotografía 2").assertExists();compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("memory-options:entry0").performClick();compose.onNodeWithText("Fijar").performClick()
        await {runBlocking {app.database.personMemoryDao().memories().single().isPinned}}
        compose.onNodeWithContentDescription("Entrada fijada").assertExists()
    }
    @Test fun longPressDragMovesEntriesAcrossColumnsAndRowsWithoutOpeningViewer() {
        setup(18);open()
        val root=compose.onNodeWithTag("person-general").fetchSemanticsNode().boundsInRoot
        val from=compose.onNodeWithTag("memory:entry0").fetchSemanticsNode().boundsInRoot.center-root.topLeft
        val to=compose.onNodeWithTag("memory:entry5").fetchSemanticsNode().boundsInRoot.center-root.topLeft
        drag(from,to)
        compose.onNodeWithTag("memory-viewer").assertDoesNotExist()
        await {runBlocking {app.database.personMemoryDao().memories()[5].id=="entry0"}}
        compose.onNodeWithTag("memory-viewer").assertDoesNotExist()
        compose.onNodeWithTag("memory-options:entry0").performClick();compose.onNodeWithText("Fijar").performClick()
        await {runBlocking {app.database.personMemoryDao().memories().first().isPinned}}
        val before=runBlocking {app.database.personMemoryDao().memories()}
        val src=compose.onNodeWithTag("memory:entry0").fetchSemanticsNode().boundsInRoot.center-root.topLeft
        val dst=compose.onNodeWithTag("memory:entry2").fetchSemanticsNode().boundsInRoot.center-root.topLeft
        drag(src,dst)
        compose.waitForIdle();assertEquals(before,runBlocking {app.database.personMemoryDao().memories()})
        compose.onNodeWithTag("person-general").performTouchInput {swipeUp()}
        compose.onNodeWithTag("memory:entry17").assertIsDisplayed()
        entry("entry0");compose.onNodeWithTag("memory-carousel").assertExists()
    }
    private fun drag(from:Offset,to:Offset) {
        compose.mainClock.autoAdvance=false
        try {
            compose.onNodeWithTag("person-general").performTouchInput {down(from);advanceEventTime(700);moveTo(from)}
            compose.mainClock.advanceTimeBy(32)
            repeat(6) {step->
                compose.onNodeWithTag("person-general").performTouchInput {moveTo(from+(to-from)*((step+1)/6f),delayMillis=40)}
                compose.mainClock.advanceTimeBy(48)
            }
            compose.onNodeWithTag("person-general").performTouchInput {up()}
        } finally {compose.mainClock.autoAdvance=true}
        compose.waitForIdle()
    }
    @Test fun compactTechnologyCardsKeepAllFiveStatesAndExistingEditorWithLargeFont() {
        setup();runBlocking {PersonProficiency.entries.forEachIndexed {index,level->app.database.technologyDao().insert(TechnologyEntity("tech$index","Tecnología $index con nombre extenso",null));people.saveTechnology("person","tech$index",level)}}
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {Arachn0deTheme {PersonTechnologiesSection("person",people)}}}
        text("Sin evaluar")
        PersonProficiency.entries.forEachIndexed {index,level->compose.onNodeWithTag("person-technology-grid").performScrollToNode(hasTestTag("person-technology:tech$index"));compose.onNodeWithText(level.label).assertIsDisplayed()}
        compose.onAllNodes(hasText("Dominio:",substring=true)).assertCountEquals(0)
        compose.onNodeWithTag("person-technology-grid").performScrollToNode(hasTestTag("person-technology:tech0"));compose.onNodeWithTag("person-technology:tech0").performClick();text("Guardar dominio")
        compose.onNode(hasText("Avanzado") and hasAnyAncestor(isDialog())).performClick();compose.onNodeWithText("Guardar dominio").performClick()
        await {runBlocking {app.database.personIntegrationDao().technologies().first {it.technologyId=="tech0"}.proficiency=="ADVANCED"}}
        val levels=runBlocking {app.database.personIntegrationDao().technologies()};assertEquals(5,levels.size)
        assertEquals(PersonProficiency.entries.drop(1).map {it.name},levels.filter {it.technologyId!="tech0"}.map {it.proficiency})
    }
}
