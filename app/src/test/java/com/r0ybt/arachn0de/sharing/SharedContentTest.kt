package com.r0ybt.arachn0de.sharing

import android.content.Intent
import android.net.Uri
import com.r0ybt.arachn0de.domain.model.TitleLimits
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=android.app.Application::class,sdk=[28])
class SharedContentTest {
    private fun text(body:String,subject:String?=null)=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,body).apply {subject?.let {putExtra(Intent.EXTRA_SUBJECT,it)}}
    @Test fun unicodeTitleNeverCutsEmojiAndOriginalTextAndUrlsRemainComplete() {
        val body="😀".repeat(130)+" https://example.com/a?x=1&y=2"
        val result=SharedContent.read(text(body));assertEquals(100,TitleLimits.count(result.title));assertEquals("https://example.com/a?x=1&y=2",result.description)
        val subject="Asunto "+"😀".repeat(120)
        val withSubject=SharedContent.read(text(body,subject));assertEquals("https://example.com/a?x=1&y=2",withSubject.description)
        assertEquals(100,TitleLimits.count(withSubject.title))
    }
    @Test fun subjectAndTextArePreservedWithoutDuplicatingUrlOrLongSubject() {
        val url="https://example.com/"+"a".repeat(150)
        assertEquals(url,SharedContent.read(text(url,url)).description)
        assertEquals(url,SharedContent.read(text("",url)).description)
        val result=SharedContent.read(text("Texto y https://example.com","Título"))
        assertEquals("Título",result.title);assertEquals("https://example.com",result.description)
    }
    @Test fun limitsFormatsAndMultipleItemsAreRejectedExplicitly() {
        assertEquals(64*1024,SharedContent.read(text("a".repeat(64*1024))).description.length)
        listOf(text("a".repeat(64*1024+1)),text("😀".repeat(16385)),text(""),text("x").setType("image/webp"),text("x").setAction(Intent.ACTION_SEND_MULTIPLE)).forEach {assertTrue(runCatching {SharedContent.read(it)}.isFailure)}
    }
    @Test fun singlePngAndJpegKeepUriAndImageTitleWithoutOpeningProvider() {
        listOf("image/png","image/jpeg").forEach {mime->
            val uri=Uri.parse("content://sender/image")
            val result=SharedContent.read(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri))
            assertEquals("Imagen compartida",result.title);assertEquals(uri,result.image)
        }
        assertTrue(runCatching {SharedContent.read(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,Uri.parse("file:///tmp/private")))}.isFailure)
    }
    @Test fun sharedLinksSeparateTitlesAndKeepOnlyTheOriginalUrlOffline() {
        val url="https://youtu.be/abc?si=token&x=1#t=20"
        for((body,subject,expected) in listOf(
            Triple(url,null,"Enlace compartido"),
            Triple(url,"Vídeo de ejemplo","Vídeo de ejemplo"),
            Triple("Vídeo de ejemplo\n$url",null,"Vídeo de ejemplo"),
            Triple("Vídeo de ejemplo - $url",null,"Vídeo de ejemplo"),
            Triple("Descripción larga\nMás texto\n$url","Vídeo de ejemplo","Vídeo de ejemplo"),
            Triple("",url,"Enlace compartido"))) {
            val result=SharedContent.read(text(body,subject))
            assertTrue(result.linkNote);assertNull(result.image)
            assertEquals(expected,result.title);assertEquals(url,result.description)
        }
        assertEquals("Título Android",SharedContent.read(text(url).putExtra(Intent.EXTRA_TITLE,"Título Android")).title)
        val image=SharedContent.read(Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_TEXT,url).putExtra(Intent.EXTRA_STREAM,Uri.parse("content://sender/image")))
        assertFalse(image.linkNote);assertNotNull(image.image);assertEquals(url,image.description)
        assertFalse(SharedContent.read(text("Texto sin enlace")).linkNote)
        assertFalse(SharedContent.isLink("Texto $url"))
        assertFalse(SharedContent.isLink("$url\nTexto"))
    }

}
