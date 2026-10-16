package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.relationKey
import com.r0ybt.arachn0de.security.SecureFiles
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class AntisocialHierarchyTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var people:PersonRepository
    private lateinit var avatar:String
    private fun pump() {shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun tag(tag:String) {await {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    @Before fun seed() {runBlocking {
        people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))
        val source=File(app.cacheDir,"antisocial.png").apply {writeBytes(BackupFixture.png())}
        avatar=people.importAvatar(Uri.fromFile(source))
        people.save("a","Ana",avatar);people.save("b","Bruno",null);people.save("c","Carla",null);people.save("d","Dora",null)
        people.identifySelf("a")
        people.saveProfile(PersonProfileEntity("a",description="Biografía",links="https://example.org"))
        people.saveRelation(PersonRelationEntity("bc","b","c","SOCIAL","amigo/a","amigo/a","","RECIPROCAL",relationKey("b","c","SOCIAL","amigo/a","amigo/a","RECIPROCAL")),true)
        val other=people.importAvatar(Uri.fromFile(source))
        people.savePhoto(PersonPhotoEntity("older","a",other,0),"seed")
    }}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump();runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}};app.database.close()
    }
    @Test fun primaryOpensDirectlyAndNetworkUsesRealContactsAndRelationsWithCoherentBack() {
        var left=false
        compose.setContent {Arachn0deTheme {PeopleScreen(people) {left=true}}}
        tag("person-publications");compose.onNodeWithTag("person-publications").assert(hasText("1 Publicaciones"))
        compose.onNodeWithText("Biografía").assertExists()
        compose.onNodeWithTag("people-list").assertDoesNotExist()
        compose.onNodeWithTag("person-network").performClick();tag("network-contact:b")
        compose.onNodeWithTag("network-contact:c").assertExists();compose.onNodeWithTag("network-contact:d").assertExists()
        compose.onNodeWithTag("network-contact:b").performClick();tag("person-network")
        compose.onNodeWithTag("person-network").performClick();tag("network-contact:c")
        compose.onNodeWithTag("network-contact:a").assertDoesNotExist();compose.onNodeWithTag("network-contact:d").assertDoesNotExist()
        compose.onNodeWithTag("network-contact:c").performClick();tag("person-network")
        compose.onNodeWithText("Volver").performClick();tag("person-network")
        compose.onNodeWithText("Volver").performClick();tag("person-network")
        compose.onNodeWithText("Biografía").assertExists()
        assertFalse(left);assertEquals(4,runBlocking {app.database.backupDao().persons().size})
        assertEquals(1,runBlocking {app.database.personProfileDao().relations().size})
    }
    @Test fun avatarOpensViewerWithoutEditorAndVerticalPagingAndDocumentCopyKeepAvatarUnchanged() {
        compose.setContent {Arachn0deTheme {PeopleScreen(people) {}}}
        tag("person-photo:a");compose.onNodeWithTag("person-photo:a").performClick();tag("person-photo-viewer")
        compose.onNodeWithTag("person-profile-fields").assertDoesNotExist()
        compose.onNodeWithTag("person-photo-pager").performTouchInput {swipeUp()}
        await {compose.onAllNodesWithText("2/2",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("person-photo-save").performClick()
        val shadow=shadowOf(compose.activity)
        var launched:org.robolectric.shadows.ShadowActivity.IntentForResult?=null
        await {if(launched==null) launched=shadow.nextStartedActivityForResult;launched!=null}
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,launched!!.intent.action)
        compose.runOnIdle {shadow.receiveResult(launched!!.intent,Activity.RESULT_CANCELED,null)}
        await {runCatching {compose.onNodeWithTag("person-photo-save").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("person-photo-save").performClick();launched=null
        await {if(launched==null) launched=shadow.nextStartedActivityForResult;launched!=null}
        val destination=File(app.cacheDir,"antisocial-export.png")
        compose.runOnIdle {shadow.receiveResult(launched!!.intent,Activity.RESULT_OK,Intent().setData(Uri.fromFile(destination)))}
        await {destination.isFile && destination.length()>0 && runCatching {compose.onNodeWithTag("person-photo-save").assertIsEnabled()}.isSuccess}
        val original=runBlocking {app.database.personGalleryDao().photos().single().file}
        val source=File(app.security.current.value!!.context.filesDir,"avatars/$original")
        assertArrayEquals(SecureFiles.input(source).use {it.readBytes()},destination.readBytes())
        compose.onNodeWithText("Cerrar").performClick();compose.onNodeWithTag("person-profile-fields").assertDoesNotExist()
        assertEquals(avatar,runBlocking {app.database.personDao().get("a")!!.avatarFile})
    }
    @Test fun publicationViewerPagesVerticallyWithoutChangingOrderOrPrimaryPhoto() {
        runBlocking {
            val first=app.database.personGalleryDao().photos().single()
            people.savePhoto(first.copy(id="older-two",position=1),"seed")
            val dao=app.database.personMemoryDao();dao.clear()
            dao.save(PersonMemoryEntity("entry","a",null,"Publicación",0))
            dao.images(listOf(PersonMemoryImageEntity("first","entry",null,"older",0),PersonMemoryImageEntity("second","entry",null,"older-two",1)))
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(people) {}}}
        tag("person-general")
        compose.onNodeWithTag("person-general").performScrollToNode(hasTestTag("memory:entry"))
        compose.onNodeWithTag("memory:entry").performClick();tag("memory-carousel")
        compose.onNodeWithTag("memory-carousel").performTouchInput {swipeUp()}
        await {compose.onAllNodesWithText("2/2").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(listOf("first","second"),runBlocking {app.database.personMemoryDao().images().map {it.id}})
        assertEquals(avatar,runBlocking {app.database.personDao().get("a")!!.avatarFile})
    }

    @Test fun existingProfileEditingAndConfirmedDeletionRemainReachableFromPrimary() {
        compose.setContent {Arachn0deTheme {PeopleScreen(people) {}}}
        tag("person-publications")
        compose.onNodeWithContentDescription("Opciones del perfil").performClick()
        compose.onNodeWithText("Editar perfil").performClick();tag("person-profile-fields")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("person-profile-fields").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del perfil").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        assertEquals(4,runBlocking {app.database.backupDao().persons().size})
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithContentDescription("Opciones del perfil").performClick()
        compose.onNodeWithText("Eliminar").performClick();compose.onNodeWithText("Eliminar").performClick()
        await {runBlocking {app.database.backupDao().persons().size==3}}
        tag("people-list")
        assertTrue(runBlocking {app.database.personProfileDao().relations().any {it.id=="bc"}})
    }

    @Test fun selectingIdentityFromFallbackListOpensPrimaryAndReentryDoesNotRestoreTheList() {
        runBlocking {people.identifySelf(null)}
        var show by androidx.compose.runtime.mutableStateOf(true)
        val restore=androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restore.setContent {
            val holder=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            Arachn0deTheme {
                if(show) holder.SaveableStateProvider("people:list") {PeopleScreen(people) {show=false}}
                else androidx.compose.material3.Button(onClick={holder.removeState("people:list");show=true}) {androidx.compose.material3.Text("Entrar Antisocial")}
            }
        }
        tag("people-list");compose.onNodeWithText("Nueva Persona").performClick();tag("person-profile-fields")
        compose.onNode(hasSetTextAction() and hasText("Nombre")).performTextReplacement("Principal nueva")
        compose.onNodeWithTag("person-profile-fields").performScrollToNode(hasText("Identificar como yo"))
        compose.onNodeWithText("Identificar como yo").performClick();compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {app.database.cascadeRelationDao().identities().isNotEmpty()}}
        val id=runBlocking {app.database.cascadeRelationDao().identities().single().personId}
        tag("person-photo:$id");compose.onNodeWithTag("people-list").assertDoesNotExist()
        restore.emulateSavedInstanceStateRestore();tag("person-photo:$id")
        compose.onNodeWithText("Volver").performClick();compose.onNodeWithText("Entrar Antisocial").performClick()
        tag("person-photo:$id");compose.onNodeWithTag("people-list").assertDoesNotExist()
        assertEquals(id,runBlocking {app.database.cascadeRelationDao().identities().single().personId})
    }
    @Test fun horizontalCountsReactAndFavoritesReorderStablyWithoutChangingNetworkMembership() {
        val restore=androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {PeopleScreen(people) {}}}
        tag("person-publications")
        await {runCatching {compose.onNodeWithTag("person-network").assert(hasText("3 Red"))}.isSuccess}
        val publications=compose.onNodeWithTag("person-publications").fetchSemanticsNode().boundsInRoot
        val network=compose.onNodeWithTag("person-network").fetchSemanticsNode().boundsInRoot
        val avatar=compose.onNodeWithTag("person-photo:a").fetchSemanticsNode().boundsInRoot
        assertTrue(publications.right<=network.left);assertTrue(publications.center.y in network.top..network.bottom)
        assertTrue(avatar.bottom<=publications.top);assertEquals(avatar.width,avatar.height,0.1f)
        val biography=compose.onNodeWithText("Biografía").fetchSemanticsNode().boundsInRoot
        assertTrue(biography.top>=compose.onNodeWithTag("person-statistics").fetchSemanticsNode().boundsInRoot.bottom)
        runBlocking {
            people.save("e","Ema",null)
            val photo=app.database.personGalleryDao().photos().single();people.savePhoto(photo.copy(id="second-entry",position=1),"seed")
            people.saveRelation(PersonRelationEntity("bc2","b","c","CUSTOM","colega","","","ONE_WAY",relationKey("b","c","CUSTOM","colega","","ONE_WAY")),true)
        }
        await {runCatching {compose.onNodeWithTag("person-network").assert(hasText("4 Red"));compose.onNodeWithTag("person-publications").assert(hasText("2 Publicaciones"))}.isSuccess}
        runBlocking {people.delete("e")}
        await {runCatching {compose.onNodeWithTag("person-network").assert(hasText("3 Red"))}.isSuccess}
        compose.onNodeWithTag("person-network").performClick();tag("person-favorite:d")
        fun top(id:String)=compose.onNodeWithTag("network-contact:$id").fetchSemanticsNode().boundsInRoot.top
        assertTrue(top("b")<top("c") && top("c")<top("d"))
        compose.onNodeWithTag("person-favorite:d").performClick()
        await {runCatching {top("d")<top("b")}.getOrDefault(false)}
        assertTrue(top("b")<top("c"));compose.onNodeWithTag("person-favorite:d").assertIsSelected()
        restore.emulateSavedInstanceStateRestore()
        await {runCatching {compose.onNodeWithTag("person-favorite:d").assertIsSelected()}.isSuccess}
        compose.onNodeWithTag("person-favorite:d").performClick()
        await {runCatching {top("b")<top("c") && top("c")<top("d")}.getOrDefault(false)}
        runBlocking {people.toggleFavorite("d")}
        compose.onNodeWithTag("network-contact:b").performClick();tag("person-network")
        await {runCatching {compose.onNodeWithTag("person-network").assert(hasText("1 Red"))}.isSuccess}
        compose.onNodeWithTag("person-network").performClick();tag("network-contact:c")
        compose.onNodeWithTag("network-contact:d").assertDoesNotExist()
        runBlocking {people.deleteRelation("bc");people.deleteRelation("bc2")}
        await {compose.onAllNodesWithTag("network-contact:c").fetchSemanticsNodes().isEmpty()}
        assertEquals(setOf("d"),runBlocking {people.favoritePeople()})
        compose.onNodeWithText("Volver").performClick()
        await {runCatching {compose.onNodeWithTag("person-network").assert(hasText("0 Red"))}.isSuccess}
    }

}
