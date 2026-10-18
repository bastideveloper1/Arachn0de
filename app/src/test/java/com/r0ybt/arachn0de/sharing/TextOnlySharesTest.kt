package com.r0ybt.arachn0de.sharing

import com.r0ybt.arachn0de.ui.appendWebLinks
import android.content.Intent
import android.net.Uri
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=android.app.Application::class,sdk=[28])
class TextOnlySharesTest {
    @Test fun mixedTextKeepsEveryUrlAndIgnoresAllStreamsWithoutOpeningThem() {
        val body="Descripción de una imagen y un audio.\nhttps://example.org/first\nhttps://example.org/second?a=1&b=2"
        val intent=Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_SUBJECT,"Título completo").putExtra(Intent.EXTRA_TEXT,body).putExtra(Intent.EXTRA_STREAM,Uri.parse("content://unreadable/image"))
        val value=SharedContent.read(intent)
        assertEquals("Título completo",value.title);assertEquals(body,value.description);assertNull(value.image);assertTrue(value.linkNote)
        val subjectUrl="https://example.org/subject"
        val fromSubject=SharedContent.read(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,subjectUrl).putExtra(Intent.EXTRA_TEXT,"Texto complementario"))
        assertTrue(fromSubject.description.contains(subjectUrl));assertTrue(fromSubject.description.contains("Texto complementario"))
        val repeated=SharedContent.read(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"Título $subjectUrl").putExtra(Intent.EXTRA_TEXT,"Texto $subjectUrl"))
        assertEquals(1,Regex(Regex.escape(subjectUrl)).findAll(repeated.description).count())
    }
    @Test fun abbreviatedWebLabelKeepsCompleteClickableTarget() {
        val url="https://example.org/"+"a".repeat(180)+"?q=1"
        var opened=""
        val rendered=androidx.compose.ui.text.buildAnnotatedString {appendWebLinks(url,androidx.compose.ui.graphics.Color.Blue) {opened=it}}
        assertTrue(rendered.text.endsWith("…"));assertTrue(rendered.text.length<url.length)
        val annotation=rendered.getLinkAnnotations(0,rendered.length).single().item as androidx.compose.ui.text.LinkAnnotation.Clickable
        annotation.linkInteractionListener?.onClick(annotation)
        assertEquals(url,opened)
    }
    @Test fun onlyExplicitStandaloneMediaMetadataIsRemovedAndMediaAloneIsRejected() {
        val body="Texto legítimo sobre una imagen y un audio.\n[Imagen: archivo.png]\n[Audio: archivo.mp3]\nhttps://example.org"
        val value=SharedContent.read(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,body))
        assertEquals("Texto legítimo sobre una imagen y un audio.\nhttps://example.org",value.description)
        val legitimate="El manual cita [Imagen: ejemplo.png] como texto."
        assertEquals(legitimate,SharedContent.cleanMetadata(legitimate))
        for(mime in listOf("image/png","audio/mpeg")) assertTrue(runCatching {SharedContent.read(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,Uri.parse("content://unreadable/media")))}.isFailure)
    }
}
