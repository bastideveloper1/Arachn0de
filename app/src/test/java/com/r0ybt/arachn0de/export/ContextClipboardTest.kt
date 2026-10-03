package com.r0ybt.arachn0de.export

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,33])
class ContextClipboardTest {
    @Test fun plainTextClipboardReceivesExactlyTheRenderedStringAndRejectsInvalidInput() {
        val context:Context=ApplicationProvider.getApplicationContext()
        val snapshot=NodeExportSnapshot.capture(NodeTreeSnapshot(listOf(
            Node("id","p",null,"Español 🕷","Contenido\nMultilínea",false,0,1,1,false))),"id",true)
        val text=NodeMarkdownRenderer.render(snapshot)
        val delivery=ContextClipboard(context)
        delivery.copy(text)
        val manager=context.getSystemService(ClipboardManager::class.java)
        assertEquals(text,manager.primaryClip!!.getItemAt(0).text.toString())
        assertTrue(manager.primaryClipDescription!!.hasMimeType("text/plain"))
        assertThrows(IllegalArgumentException::class.java){delivery.copy(" ")}
        assertEquals(text,manager.primaryClip!!.getItemAt(0).text.toString())
    }
}
