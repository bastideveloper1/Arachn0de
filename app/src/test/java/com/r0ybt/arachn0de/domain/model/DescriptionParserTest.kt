package com.r0ybt.arachn0de.domain.model

import com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.*
import org.junit.Assert.*
import org.junit.Test

class DescriptionParserTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private fun visible(text: String) = DescriptionParser.parse(text).joinToString("") { it.text }
    @Test fun plainTextAndUnpairedDelimitersStayLiteral() {
        listOf("Texto normal\n\ncon párrafos", "2 * 3 = 6", "*", "**", "__", "****", "**sin cierre", "archivo_uno", "C:\\temp").forEach {
            assertEquals(it, visible(it))
        }
    }
    @Test fun escapesAllowLiteralFormattingAndReferences() {
        assertEquals("**literal** *también* __esto__", visible("\\*\\*literal\\*\\* \\*también\\* \\_\\_esto\\_\\_"))
        val escaped = "\\${AttachmentReferences.token(id)}"
        assertTrue(AttachmentReferences.ids(escaped).isEmpty())
        assertEquals(AttachmentReferences.token(id), visible(escaped))
    }
    @Test fun threeFormatsAndNestedCombination() {
        val parts = DescriptionParser.parse("**negrita** *cursiva* __subrayado__")
        assertEquals(setOf(Bold), parts[0].formats)
        assertEquals(setOf(Italic), parts[2].formats)
        assertEquals(setOf(Underline), parts[4].formats)
        assertEquals("negrita cursiva subrayado", parts.joinToString("") { it.text })
        val combined = DescriptionParser.parse("__***todos***__").single()
        assertEquals("todos", combined.text); assertEquals(setOf(Bold, Italic, Underline), combined.formats)
    }
    @Test fun labelsArePerOccurrenceAndTheirPunctuationIsNeverFormatting() {
        val label = "Gráfico | ] \\ **literal**\nfin"
        val first = AttachmentReferences.token(id, label)
        val second = AttachmentReferences.token(id, "Otro uso")
        val matches = AttachmentReferences.matches("$first $second").toList()
        assertEquals(2, matches.size); assertEquals(label, AttachmentReferences.label(matches[0]))
        assertEquals("Otro uso", AttachmentReferences.label(matches[1])); assertEquals(setOf(id), AttachmentReferences.ids("$first $second"))
        assertEquals(label, DescriptionParser.parse(first).single().label)
        assertTrue(DescriptionParser.parse(first).single().formats.isEmpty())
        assertEquals(AttachmentReferences.token(id), AttachmentReferences.token(id, "  "))
        assertNull(AttachmentReferences.label(AttachmentReferences.matches(AttachmentReferences.token(id)).single()))
    }
    @Test fun formattedTextImageTextKeepsOrderAndIdentity() {
        val text = "**Antes** ${AttachmentReferences.token(id, "Gráfico")} __Después__"
        val parts = DescriptionParser.parse(text)
        assertEquals(listOf("Antes", " ", "", " ", "Después"), parts.map { it.text })
        assertEquals(id, parts[2].imageId); assertEquals("Gráfico", parts[2].label)
        assertEquals(setOf(Bold), parts[0].formats); assertEquals(setOf(Underline), parts.last().formats)
        assertEquals("**Antes**  __Después__", AttachmentReferences.withoutReferences(text))
        assertEquals("**Antes**  __Después__", AttachmentReferences.remove(text, id))
    }
    @Test fun formatCanWrapWholeImageWithoutParsingCaptionOrUuid() {
        val text = "**${AttachmentReferences.token(id, "*literal*")}**"
        val image = DescriptionParser.parse(text).single()
        assertEquals(id, image.imageId); assertEquals("*literal*", image.label); assertEquals(setOf(Bold), image.formats)
    }
    @Test fun unknownReferenceSyntaxRemainsLiteral() {
        val text = "[[arachnode:image:not-a-uuid|Gráfico]]"
        assertEquals(text, visible(text)); assertTrue(AttachmentReferences.ids(text).isEmpty())
    }
    @Test fun boldCanNestInsideItalicAndItalicInsideBold() {
        val italicOutside = DescriptionParser.parse("*antes **dentro** después*")
        assertEquals("antes dentro después", italicOutside.joinToString("") { it.text })
        assertEquals(setOf(Italic), italicOutside.first().formats)
        assertEquals(setOf(Italic, Bold), italicOutside[1].formats)
        assertEquals(setOf(Italic), italicOutside.last().formats)
        val boldOutside = DescriptionParser.parse("**antes *dentro* después**")
        assertEquals("antes dentro después", boldOutside.joinToString("") { it.text })
        assertEquals(setOf(Bold, Italic), boldOutside[1].formats)
    }
}
