package com.r0ybt.arachn0de.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.Person
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
class AvatarEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as Arachn0deApplication
    private lateinit var people: PersonRepository
    @Before fun setup() { people = PersonRepository(app.database, AvatarStore(app, syncDirectory = BackupFixture::syncDirectory)) }
    private fun waitText(text: String) { compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun show() {
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { PeopleScreen(people) {} } } }
        waitText("Nueva Persona")
    }
    private fun seed(): String = runBlocking {
        val source = File(app.cacheDir, "editor-source.png").apply { writeBytes(BackupFixture.png()) }
        val name = people.importAvatar(Uri.fromFile(source)); people.save("a", "Ana", name, zoom = 2f, x = .5f, y = -.3f); name
    }
    @Test fun editingAgainUsesSamePhotoAccessibleZoomAndCancelPreservesSavedFraming() {
        val file = seed(); val bytes = File(app.filesDir, "avatars/$file").readBytes()
        show(); waitText("Ana"); compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Ajustar encuadre").performClick(); waitText("Zoom: 2.00×")
        compose.onNodeWithText("Ampliar").performClick(); compose.onNodeWithText("Centrar").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar").performClick(); compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.personDao().get("a")!!.avatarZoom > 2f } }
        val saved = runBlocking { app.database.personDao().get("a")!! }
        assertEquals(0f, saved.avatarX); assertEquals(0f, saved.avatarY); assertEquals(file, saved.avatarFile)
        compose.onNodeWithText("Editar").performClick(); compose.onNodeWithText("Ajustar encuadre").performClick()
        waitText("Zoom: 2.40×"); compose.onNodeWithText("Restablecer").performScrollTo().performClick()
        compose.onAllNodesWithText("Cancelar").onLast().performClick()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Editar").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(saved, runBlocking { app.database.personDao().get("a") })
        assertArrayEquals(bytes, File(app.filesDir, "avatars/$file").readBytes())
    }
    @Test fun previewDragChangesPositionAndRemainsEditableAfterActivityRecreation() {
        seed(); show(); waitText("Ana"); compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Ajustar encuadre").performClick(); waitText("Zoom: 2.00×")
        compose.onNodeWithContentDescription("Vista previa del avatar").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(-25f, 20f), 200)
        }
        compose.onNodeWithText("Confirmar").performClick(); compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.personDao().get("a")!!.avatarX < .5f } }
        val saved = runBlocking { app.database.personDao().get("a")!! }
        compose.activityRule.scenario.recreate()
        show(); waitText("Ana"); compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Ajustar encuadre").performClick(); waitText("Zoom: 2.00×")
        compose.onNodeWithText("Confirmar").performClick(); compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Editar").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(saved, runBlocking { app.database.personDao().get("a") })
    }
    private fun select() {
        compose.onNodeWithText("Seleccionar avatar").performClick()
        val launched = shadowOf(compose.activity).nextStartedActivityForResult
        val file = File(app.cacheDir, "editor-picked.png").apply { writeBytes(BackupFixture.png()) }
        compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(launched.requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        waitText("Encuadrar avatar"); waitText("Zoom: 1.00×")
    }
    @Test fun selectedPhotoCanBeCancelledOrConfirmedWithoutDeletingPreviousBeforeSave() {
        val old = seed(); val before = runBlocking { app.database.personDao().get("a")!! }
        show(); waitText("Ana"); compose.onNodeWithText("Editar").performClick(); select()
        compose.onAllNodesWithText("Cancelar").onLast().performClick()
        compose.waitUntil(10_000) { File(app.filesDir, "avatars").listFiles().orEmpty().size == 1 }
        assertEquals(before, runBlocking { app.database.personDao().get("a") })
        select(); compose.onNodeWithText("Ampliar").performClick(); compose.onNodeWithText("Confirmar").performClick()
        assertTrue(File(app.filesDir, "avatars/$old").exists())
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.personDao().get("a")!!.avatarFile != old } }
        compose.waitUntil(10_000) { !File(app.filesDir, "avatars/$old").exists() }
        assertEquals(1.2f, runBlocking { app.database.personDao().get("a")!!.avatarZoom }, .0001f)
    }
    @Test fun cancellingPersonAfterConfirmingCropKeepsPreviousAvatar() {
        val old = seed(); val before = runBlocking { app.database.personDao().get("a")!! }
        show(); waitText("Ana"); compose.onNodeWithText("Editar").performClick(); select()
        compose.onNodeWithText("Confirmar").performClick(); compose.onNodeWithText("Cancelar").performClick()
        compose.waitUntil(10_000) { File(app.filesDir, "avatars").listFiles().orEmpty().size == 1 }
        assertEquals(before, runBlocking { app.database.personDao().get("a") }); assertTrue(File(app.filesDir, "avatars/$old").exists())
    }
    @Test fun sharedAvatarRendererShowsStoredOffsetInCardsAndSelectors() {
        val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) for (x in 0 until 32) image.setPixel(x, y, if (x < 16) Color.RED else Color.BLUE)
        val source = File(app.cacheDir, "two-colors.png")
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val file = runBlocking { people.importAvatar(Uri.fromFile(source)) }
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { Column {
            PersonAvatar(Person("a", "Left", file, 2f, 1f, 0f))
            ResponsibleAvatars(listOf(Person("b", "Right", file, 2f, -1f, 0f)))
        } } } }
        compose.onNodeWithContentDescription("Left").assertExists()
        compose.onNodeWithContentDescription("Right").assertExists()
        fun center(x: Float): androidx.compose.ui.graphics.Color {
            val sourceBitmap = AvatarStore(app).read(file)!!
            val target = androidx.compose.ui.graphics.ImageBitmap(28, 28)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(target), androidx.compose.ui.geometry.Size(28f, 28f)
            ) { drawFramedAvatar(sourceBitmap.asImageBitmap(), com.r0ybt.arachn0de.domain.model.AvatarFraming(2f, x, 0f)) }
            sourceBitmap.recycle()
            return target.toPixelMap()[14, 14]
        }
        assertTrue(center(1f).red > .9f && center(-1f).blue > .9f)
        assertTrue(center(1f).blue < .1f); assertTrue(center(-1f).red < .1f)
    }
}
