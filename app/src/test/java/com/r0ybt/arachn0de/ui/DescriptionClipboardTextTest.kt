package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.ui.state.descriptionClipboardText
import org.junit.Assert.*
import org.junit.Test

class DescriptionClipboardTextTest {
    private val first = "00000000-0000-0000-0000-000000000001"
    private val second = "00000000-0000-0000-0000-000000000002"
    @Test fun formatsAndImagesKeepOrderAsHumanPlainText() {
        val text = "**Antes**\n${AttachmentReferences.token(first, "Gráfico")}\n*medio*\n${AttachmentReferences.token(second)}\n__Después__"
        assertEquals("Antes\nGráfico\nmedio\n\nDespués", descriptionClipboardText(text, mapOf(second to "original.png")))
    }
    @Test fun duplicateLabelsDifferentUsesAndMissingFilesNeverExposeIds() {
        val text = "${AttachmentReferences.token(first, "Gráfico")} ${AttachmentReferences.token(second, "Gráfico")} ${AttachmentReferences.token(first, "Otro uso")} ${AttachmentReferences.token(second)}"
        assertEquals("Gráfico Gráfico Otro uso ", descriptionClipboardText(text, emptyMap()))
    }
    @Test fun plainAndEscapedFormattingStayLegibleAndEscapedInternalReferencesAreHumanized() {
        assertEquals("Sin formato\n\ncon párrafos", descriptionClipboardText("Sin formato\n\ncon párrafos", emptyMap()))
        assertEquals("**literal**", descriptionClipboardText("\\*\\*literal\\*\\*", emptyMap()))
        assertEquals("Gráfico", descriptionClipboardText("\\${AttachmentReferences.token(first, "Gráfico")}", emptyMap()))
    }
    @Test fun legitimateImageMentionsAndLinksStayIntact() {
        val text = "Ver imagen archivo.png\n[Imagen: ejemplo.png]\nhttps://example.org/imagen.png\n[[arachnode:image:ejemplo]]"
        assertEquals(text, descriptionClipboardText(text, emptyMap()))
        assertEquals("Antes\n\nDespués", descriptionClipboardText("Antes\n${AttachmentReferences.token(first)}\nDespués", emptyMap()))
        assertEquals("[Audio: voz.m4a]", descriptionClipboardText(AttachmentReferences.audioToken(first), mapOf(first to "voz.m4a")))
    }
}
