package com.r0ybt.arachn0de.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class AvatarPhoneFixTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var people:PersonRepository
    private fun pump() {shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    @Before fun setup() {people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump();runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}};app.database.close()
    }
    private suspend fun seed(id:String,w:Int,h:Int):Pair<Person,Bitmap> {
        val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        for(y in 0 until h) for(x in 0 until w) image.setPixel(x,y,Color.rgb(x*255/w,y*255/h,150))
        val file=java.io.File(app.cacheDir,"avatar-$id.png");file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        val avatar=people.importAvatar(android.net.Uri.fromFile(file))
        people.save(id,"Ana $id",avatar,zoom=1.8f,x=.6f,y=-.4f)
        return people.observePeople().first().first {it.id==id} to image
    }
    @Test fun savedSquareAvatarMatchesFramedPreviewForLandscapePortraitAndSquare() {
        val records=runBlocking {listOf(seed("landscape",240,120),seed("portrait",120,240),seed("square",160,160))}
        compose.setContent {Arachn0deTheme {Column {records.forEach {(person,bitmap)->Row {
            PersonPhoto(person,Modifier.size(96.dp),framed=true)
            FramedAvatar(bitmap,AvatarFraming(person.avatarZoom,person.avatarX,person.avatarY),Modifier.size(96.dp).testTag("expected:${person.id}"),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
        }}}}}
        records.forEach {(person,_)->
            await {compose.onAllNodesWithTag("person-image:${person.id}").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("person-image:${person.id}").assertWidthIsEqualTo(96.dp).assertHeightIsEqualTo(96.dp)
            // Robolectric PixelCopy does not reliably redraw Compose windows. Exercise the
            // shared production DrawScope directly with the persisted thumbnail and original.
            fun render(bitmap:Bitmap):androidx.compose.ui.graphics.PixelMap {
                val target=androidx.compose.ui.graphics.ImageBitmap(96,96)
                androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                    androidx.compose.ui.unit.Density(1f),androidx.compose.ui.unit.LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(target),androidx.compose.ui.geometry.Size(96f,96f)
                ) {drawFramedAvatar(bitmap.asImageBitmap(),AvatarFraming(person.avatarZoom,person.avatarX,person.avatarY))}
                return target.toPixelMap()
            }
            val actual=render(AvatarStore(app.security.current.value!!.context).readThumbnail(person.avatarFile!!,96)!!)
            val expected=render(records.first {it.first.id==person.id}.second)
            assertEquals(actual.width,actual.height)
            listOf(.1f to .5f,.5f to .1f,.9f to .5f,.5f to .9f,.5f to .5f).forEach {(x,y)->
                val a=actual[(actual.width*x).toInt(),(actual.height*y).toInt()];val b=expected[(expected.width*x).toInt(),(expected.height*y).toInt()]
                assertEquals(b.red,a.red,.04f);assertEquals(b.green,a.green,.04f);assertEquals(b.blue,a.blue,.04f)
            }
        }
    }
    @Test fun repeatedPickerAndCropActionsOpenOnlyOnceAndCanReopenAfterCancellation() {
        runBlocking {seed("person",240,120)}
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        await {compose.onAllNodesWithContentDescription("Opciones del perfil").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Opciones del perfil").performClick();compose.onNodeWithText("Editar perfil").performClick()
        val crop=compose.onNodeWithText("Ajustar encuadre").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle {repeat(5) {crop()}}
        compose.onAllNodesWithText("Encuadrar avatar").assertCountEquals(1)
        await {runCatching {compose.onNodeWithText("Confirmar").assertIsEnabled()}.isSuccess}
        compose.onAllNodesWithText("Cancelar").onLast().performClick()
        compose.runOnIdle {crop()};compose.onAllNodesWithText("Encuadrar avatar").assertCountEquals(1)
        compose.onAllNodesWithText("Cancelar").onLast().performClick()
        val picker=compose.onNodeWithText("Cambiar fotografía").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle {repeat(5) {picker()}}
        val shadow=shadowOf(compose.activity);val launched=checkNotNull(shadow.nextStartedActivityForResult)
        assertNull(shadow.nextStartedActivityForResult)
        compose.runOnIdle {shadow.receiveResult(launched.intent,Activity.RESULT_CANCELED,null)}
        compose.onNodeWithText("Cambiar fotografía").assertIsEnabled()
        compose.runOnIdle {picker()};assertNotNull(shadow.nextStartedActivityForResult)
    }
}
