package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class PersonPhotoUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    @After fun dispose() {compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()}}
    private fun fullPhoto(portrait:Boolean) {
        val bitmap=Bitmap.createBitmap(if(portrait) 100 else 200,if(portrait) 200 else 100,Bitmap.Config.ARGB_8888)
        for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) bitmap.setPixel(x,y,when((if(portrait) y*3/bitmap.height else x*3/bitmap.width)) {0->Color.RED;1->Color.GREEN;else->Color.BLUE})
        val source=File(app.cacheDir,"full-${portrait}.png");source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val repo=PersonRepository(app.database,AvatarStore(privateStorageContext(app),syncDirectory=BackupFixture::syncDirectory))
        val person=runBlocking {val photo=repo.importAvatar(Uri.fromFile(source));repo.save("photo","Nombre extenso",photo,zoom=4f,x=1f,y=-1f);repo.observePeople().first().single()}
        compose.setContent {Arachn0deTheme {Box(Modifier.width(180.dp)) {ParticipantCard(person,"Seleccionada",true) {}}}}
        compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("person-image:photo",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val photo=compose.onNodeWithTag("person-photo:photo",useUnmergedTree=true)
        val photoBounds=photo.fetchSemanticsNode().boundsInRoot
        lateinit var rendered:Bitmap
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.View>(android.R.id.content)
            rendered=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(rendered))
        }
        fun sample(position:Float)=if(portrait) rendered.getPixel(photoBounds.center.x.toInt(),(photoBounds.top+photoBounds.height*position).toInt()) else rendered.getPixel((photoBounds.left+photoBounds.width*position).toInt(),photoBounds.center.y.toInt())
        assertEquals(Color.RED,sample(.1f));assertEquals(Color.GREEN,sample(.5f));assertEquals(Color.BLUE,sample(.9f))
        val corner=if(portrait) rendered.getPixel((photoBounds.left+photoBounds.width*.27f).toInt(),(photoBounds.top+photoBounds.height*.03f).toInt()) else rendered.getPixel((photoBounds.left+photoBounds.width*.03f).toInt(),(photoBounds.top+photoBounds.height*.27f).toInt())
        assertEquals("Stored image corners must remain visible",Color.RED,corner)
        val letterbox=if(portrait) rendered.getPixel((photoBounds.left+photoBounds.width*.1f).toInt(),photoBounds.center.y.toInt()) else rendered.getPixel(photoBounds.center.x.toInt(),(photoBounds.top+photoBounds.height*.1f).toInt())
        assertNotEquals("The source proportions must be preserved",Color.GREEN,letterbox)
        val bounds=photo.fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width,bounds.height,1f)
        assertTrue(compose.onNodeWithTag("person-name:photo",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.top>=bounds.bottom)
        runBlocking {assertEquals(person,repo.observePeople().first().single());assertTrue(AvatarStore(privateStorageContext(app)).exists(person.avatarFile!!))}
    }
    @Test fun landscapePhotoLoadsFromPrivateStorageAndKeepsBothEdgesDespiteSavedZoom() {fullPhoto(false)}
    @Test fun portraitPhotoKeepsTopAndBottomWithoutStretchingOrCircularMask() {fullPhoto(true)}
    @Test fun absentPhotoAndBrokenReferenceAreDistinguishedAndRecompositionLoadsReplacement() {
        var person by mutableStateOf(Person("state","Sin foto"))
        compose.setContent {Arachn0deTheme {Box(Modifier.width(180.dp)) {PersonPhoto(person)}}}
        compose.waitUntil(15000) {pump();compose.onAllNodesWithContentDescription("Sin foto: Sin fotografía").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {person=person.copy(avatarFile="11111111-1111-1111-1111-111111111111.png")}
        compose.waitUntil(15000) {pump();compose.onAllNodesWithText("Foto no disponible").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Sin foto: Fotografía no disponible").assertExists()
        val repo=PersonRepository(app.database,AvatarStore(privateStorageContext(app),syncDirectory=BackupFixture::syncDirectory))
        val source=File(app.cacheDir,"replacement.png").apply {writeBytes(BackupFixture.png())}
        val saved=runBlocking {val photo=repo.importAvatar(Uri.fromFile(source));repo.save("state","Sin foto",photo);repo.observePeople().first().single()}
        compose.runOnIdle {person=saved}
        compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("person-image:state",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Foto no disponible").assertDoesNotExist()
    }
    @Test fun catalogueLoadsStoredPhotoInThreeColumnsWithIndependentNameStrips() {
        val repo=PersonRepository(app.database,AvatarStore(privateStorageContext(app),syncDirectory=BackupFixture::syncDirectory))
        val source=File(app.cacheDir,"catalogue-photo.png").apply {writeBytes(BackupFixture.png())}
        runBlocking {
            val photo=repo.importAvatar(Uri.fromFile(source))
            repeat(3) {repo.save("catalogue$it","Persona $it nombre extenso ".repeat(3),if(it==0) photo else null)}
        }
        compose.setContent {Arachn0deTheme {PeopleScreen(repo) {}}}
        compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("person-image:catalogue0",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("person-photo:catalogue2",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val cards=(0..2).map {compose.onNodeWithTag("person-card:catalogue$it",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot}
        val photos=(0..2).map {compose.onNodeWithTag("person-photo:catalogue$it",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot}
        assertEquals(photos[0].top,photos[1].top,1f);assertEquals(photos[1].top,photos[2].top,1f)
        assertTrue(photos[0].left<photos[1].left && photos[1].left<photos[2].left)
        photos.forEachIndexed {i,photo->
            assertEquals(photo.width,photo.height,1f)
            assertEquals(cards[i].left,photo.left,1f);assertEquals(cards[i].right,photo.right,1f);assertEquals(cards[i].top,photo.top,1f)
        }
        compose.onNodeWithTag("person-options:catalogue0",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Editar Persona").assertExists()
        compose.onNodeWithText("Eliminar").assertExists()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        val name=compose.onNodeWithTag("person-name:catalogue0",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(name.top>=photos[0].bottom)
    }

}
