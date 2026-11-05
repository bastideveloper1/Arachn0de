package com.r0ybt.arachn0de.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.r0ybt.arachn0de.annotations.*
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class,qualifiers="w320dp-h700dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageAnnotationUiTest {
    @Test fun styleSurvivesToolsPanelAndImmediateCloseReopen() {
        compose.activity.getSharedPreferences(AnnotationStylePreferences.NAME,0).edit().clear().commit()
        var open by mutableStateOf(true)
        val original=repo.localFile(row).readBytes()
        compose.setContent {
            if(open) ImageAnnotationEditor(row,repo,"editor",onSaved={},onClose={open=false})
            else androidx.compose.material3.TextButton({open=true}) {androidx.compose.material3.Text("Reabrir imagen")}
        }
        awaitEditor()
        compose.onNodeWithTag("annotation-style-toggle").performClick()
        compose.onNodeWithContentDescription("Azul").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Fino").performScrollTo().performClick().assertIsSelected()
        val blue=compose.onNodeWithContentDescription("Azul").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("annotation-style-toggle").performClick()
        compose.onNodeWithTag("annotation-tool:PEN").performScrollTo().performClick()
        compose.onNodeWithTag("annotation-style-toggle").performClick()
        compose.onNodeWithContentDescription("Azul").assertIsOn()
        assertEquals(blue,compose.onNodeWithContentDescription("Azul").fetchSemanticsNode().boundsInRoot.left,.5f)
        compose.onNodeWithText("Fino").assertIsSelected()
        compose.onNodeWithText("Grueso").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Reabrir imagen").performClick()
        awaitEditor()
        compose.onNodeWithTag("annotation-style-toggle").performClick()
        compose.onNodeWithContentDescription("Azul").assertIsOn()
        compose.onNodeWithText("Grueso").assertIsSelected()
        assertArrayEquals(original,repo.localFile(row).readBytes())
    }

    @Test fun precisionIsTemporaryAndNewStylesDoNotChangeExistingMarksOrExport() {
        val image=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.WHITE)}
        var marks by mutableStateOf(emptyList<ImageMark>())
        var color by mutableIntStateOf(android.graphics.Color.BLUE)
        var width by mutableFloatStateOf(.003f)
        var mode by mutableStateOf(AnnotationMode.DRAW)
        compose.setContent {ImageAnnotationStage(image,marks,null,mode,MarkTool.PEN,color,width,false,true,
            onSelect={},onCommit={marks=it},modifier=Modifier.size(280.dp))}
        val stage=compose.onNodeWithTag("image-annotation-stage")
        fun hasPrecisionPixels():Boolean {
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            val bounds=stage.fetchSemanticsNode().boundsInRoot
            val view=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            compose.runOnUiThread {view.draw(android.graphics.Canvas(bitmap))}
            return try {
                (bounds.top.toInt() until bounds.bottom.toInt()).any {y->
                    (bounds.left.toInt() until bounds.right.toInt()).any {x->
                        val pixel=bitmap.getPixel(x,y)
                        android.graphics.Color.alpha(pixel)>0 && android.graphics.Color.red(pixel)<100 && android.graphics.Color.green(pixel)<100 && android.graphics.Color.blue(pixel)<100
                    }
                }
            } finally {bitmap.recycle()}
        }
        stage.performTouchInput {down(center);moveBy(Offset(30f,20f))}
        stage.assert(hasStateDescription("Referencia de precisión visible"))
        assertTrue(hasPrecisionPixels())
        assertTrue(marks.isEmpty())
        stage.performTouchInput {up()}
        stage.assert(hasStateDescription("Referencia de precisión oculta"))
        assertFalse(hasPrecisionPixels())
        val first=marks.single()
        compose.runOnIdle {color=android.graphics.Color.RED;width=.014f}
        draw()
        assertEquals(first,marks.first())
        assertEquals(android.graphics.Color.RED,marks.last().color);assertEquals(.014f,marks.last().width,0f)
        val bytes=markedPng(image,marks)
        val exported=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        val pixels=IntArray(exported.width*exported.height)
        exported.getPixels(pixels,0,exported.width,0,0,exported.width,exported.height)
        assertFalse(pixels.any {it==android.graphics.Color.BLACK || it==android.graphics.Color.DKGRAY})
        exported.recycle()
        val before=marks
        stage.performTouchInput {down(center);moveBy(Offset(20f,10f))}
        stage.assert(hasStateDescription("Referencia de precisión visible"))
        stage.performTouchInput {cancel()}
        stage.assert(hasStateDescription("Referencia de precisión oculta"));assertEquals(before,marks)
        compose.runOnIdle {mode=AnnotationMode.PAN}
        stage.performTouchInput {swipeLeft()}
        stage.assert(hasStateDescription("Referencia de precisión oculta"));assertEquals(before,marks)
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:AttachmentRepository
    private lateinit var row:AttachmentFileEntity
    @Before fun setup()=runBlocking {
        val context=compose.activity
        db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        repo=AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory))
        val input=File(context.cacheDir,"annotate-${UUID.randomUUID()}.png")
        val bitmap=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.WHITE)}
        input.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        row=repo.import(Uri.fromFile(input),"editor");input.delete();Unit
    }
    @After fun close() {
        compose.runOnUiThread {
            fun dispose(v:android.view.View) {
                if(v is androidx.compose.ui.platform.AbstractComposeView)v.disposeComposition()
                if(v is android.view.ViewGroup)(0 until v.childCount).forEach {dispose(v.getChildAt(it))}
            }
            dispose(compose.activity.window.decorView)
        }
        compose.waitForIdle();db.close()
    }
    private fun awaitEditor()=compose.waitUntil(15000) {compose.onAllNodesWithTag("image-annotation-stage").fetchSemanticsNodes().isNotEmpty()}
    private fun draw()=compose.onNodeWithTag("image-annotation-stage").performTouchInput {swipe(center-Offset(50f,30f),center+Offset(50f,30f))}
    @Test fun fourToolsCommitOneMarkPerGestureWithoutSelectionOutline() {
        val image=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.WHITE)}
        var tool by mutableStateOf(MarkTool.RECT)
        var marks by mutableStateOf(emptyList<ImageMark>())
        var selected by mutableStateOf<String?>(null)
        compose.setContent {ImageAnnotationStage(image,marks,selected,AnnotationMode.DRAW,tool,android.graphics.Color.BLUE,.006f,false,true,
            onSelect={selected=it},onCommit={marks=it},modifier=Modifier.size(280.dp))}
        listOf(MarkTool.RECT,MarkTool.ELLIPSE,MarkTool.ARROW,MarkTool.PEN).forEach {value->
            compose.runOnIdle {tool=value;marks=emptyList()}
            draw()
            compose.runOnIdle {assertEquals(1,marks.size);assertEquals(value,marks.single().tool)}
        }
        // The completed selection used to add a cyan rectangle around every new mark.
        val view=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val captured=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        compose.runOnUiThread {view.draw(android.graphics.Canvas(captured))}
        val pixels=IntArray(captured.width*captured.height);captured.getPixels(pixels,0,captured.width,0,0,captured.width,captured.height)
        assertFalse(pixels.any {it==android.graphics.Color.CYAN});captured.recycle()
    }
    @Test fun compactToolsStyleUndoAndCancelKeepOriginalBytes() {
        var closed=false
        val original=repo.localFile(row).readBytes()
        compose.setContent {ImageAnnotationEditor(row,repo,"editor",onSaved={},onClose={closed=true})}
        awaitEditor()
        listOf("Rectángulo","Óvalo","Flecha","Libre").forEach {compose.onNodeWithText(it).performScrollTo().performClick().assertIsSelected()}
        listOf("Seleccionar","Desplazar","Línea","Marcador","PNG","Guardar copia editable").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        compose.onNodeWithTag("annotation-style-toggle").performClick()
        compose.onNodeWithContentDescription("Azul").performClick().assertIsOn()
        compose.onNodeWithText("Grueso").performClick().assertIsSelected()
        compose.onNodeWithTag("annotation-style-toggle").performClick()
        draw();compose.onNodeWithText("Deshacer").performClick().assertIsNotEnabled()
        draw();compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle {assertTrue(closed)}
        assertArrayEquals(original,repo.localFile(row).readBytes())
        assertEquals(listOf(row),runBlocking {repo.pendingImports("editor")})
    }
    @Test fun saveReturnsOneFlattenedReplacementWithoutOpeningExporter() {
        var saved:String?=null
        val full=Bitmap.createBitmap(2200,300,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.WHITE)}
        val original=try {markedPng(full,emptyList())} finally {full.recycle()}
        row=runBlocking {repo.importGuestBytes(original,"editor","grande.png")}
        compose.setContent {ImageAnnotationEditor(row,repo,"editor",onSaved={saved=it},onClose={})}
        awaitEditor();draw();compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(15000) {saved!=null}
        val replacement=runBlocking {repo.availableFiles().getValue(saved!!)}
        assertNotEquals(row.id,replacement.id)
        assertEquals(2200,replacement.width);assertEquals(300,replacement.height)
        val bytes=repo.localFile(replacement).readBytes()
        assertNull(EditableImagePng.read(bytes))
        val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)!!
        try {val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height);assertTrue(pixels.any {it==0xffe53935.toInt()})}
        finally {bitmap.recycle()}
        assertTrue(repo.localFile(row).exists()) // Outer draft still owns the old reference until its commit/cancel.
    }
}
