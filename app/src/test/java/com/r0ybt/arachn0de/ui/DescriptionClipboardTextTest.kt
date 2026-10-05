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
        assertEquals("Antes\n[Imagen: Gráfico]\nmedio\n[Imagen: original.png]\nDespués", descriptionClipboardText(text, mapOf(second to "original.png")))
    }
    @Test fun duplicateLabelsDifferentUsesAndMissingFilesNeverExposeIds() {
        val text = "${AttachmentReferences.token(first, "Gráfico")} ${AttachmentReferences.token(second, "Gráfico")} ${AttachmentReferences.token(first, "Otro uso")} ${AttachmentReferences.token(second)}"
        assertEquals("[Imagen: Gráfico] [Imagen: Gráfico] [Imagen: Otro uso] [Imagen: Imagen no disponible]", descriptionClipboardText(text, emptyMap()))
    }
    @Test fun plainAndEscapedFormattingStayLegibleAndEscapedInternalReferencesAreHumanized() {
        assertEquals("Sin formato\n\ncon párrafos", descriptionClipboardText("Sin formato\n\ncon párrafos", emptyMap()))
        assertEquals("**literal**", descriptionClipboardText("\\*\\*literal\\*\\*", emptyMap()))
        assertEquals("[Imagen: Gráfico]", descriptionClipboardText("\\${AttachmentReferences.token(first, "Gráfico")}", emptyMap()))
    }
}
