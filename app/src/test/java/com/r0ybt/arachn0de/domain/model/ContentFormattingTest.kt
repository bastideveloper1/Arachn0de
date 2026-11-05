package com.r0ybt.arachn0de.domain.model

import com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.*
import com.r0ybt.arachn0de.ui.state.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class ContentFormattingTest {
    @Test fun repeatedCodeTogglePreservesShortAndLongDescriptionsAndSelection() {
        for(source in listOf("texto breve", "  if (ready) {\n\treturn true;\n  }\n".repeat(150))) {
            val draft=EditorDraft(null,null,"",source)
            draft.descriptionSelectionStart=0;draft.descriptionSelectionEnd=source.length
            repeat(20) {
                assertTrue(draft.toggleDescriptionFormat(Code))
                assertEquals(Code.marker+source+Code.marker,draft.description)
                assertEquals(Code.marker.length,draft.descriptionSelectionStart)
                assertEquals(Code.marker.length+source.length,draft.descriptionSelectionEnd)
                assertTrue(draft.toggleDescriptionFormat(Code))
                assertEquals(source,draft.description)
                assertEquals(0,draft.descriptionSelectionStart)
                assertEquals(source.length,draft.descriptionSelectionEnd)
            }
        }
    }

    @Test fun codeToggleHandlesProjectedSelectionIncludingHiddenClosingMarker() {
        val source="  if (ready) {\n\treturn true;\n  }"
        val marker=Code.marker
        for((start,end) in listOf(marker.length to (marker.length*2+source.length),
            0 to (marker.length*2+source.length),
            (marker.length*2+source.length) to (marker.length*2+source.length))) {
            val draft=EditorDraft(null,null,"",marker+source+marker)
            draft.descriptionSelectionStart=start;draft.descriptionSelectionEnd=end
            assertTrue(draft.toggleDescriptionFormat(Code))
            assertEquals(source,draft.description)
            assertTrue(draft.descriptionSelectionStart in 0..source.length)
            assertTrue(draft.descriptionSelectionEnd in 0..source.length)
        }
    }

    private fun visible(s:String)=DescriptionParser.parse(s).joinToString("") {it.text}
    @Test fun titleAndCodeToggleKeepIndentationAndLegacyFormatting() {
        for(format in listOf(Title,Code)) {
            val source=if(format==Code)"  if (a) {\n\treturn **literal**;\n  }\n" else "Frase seleccionada"
            val draft=EditorDraft(null,null,"",source)
            draft.descriptionSelectionStart=0;draft.descriptionSelectionEnd=source.length
            assertTrue(draft.toggleDescriptionFormat(format))
            assertEquals(source,visible(draft.description))
            assertTrue(DescriptionParser.parse(draft.description).all {format in it.formats})
            assertTrue(draft.toggleDescriptionFormat(format));assertEquals(source,draft.description)
        }
        val legacy=DescriptionParser.parse("__***todo***__").single()
        assertEquals(setOf(Bold,Italic,Underline),legacy.formats)
    }
    @Test fun deletingFormattedWordsRemovesOnlyTheirOrphanDelimitersAndKeepsLiteralPunctuation() {
        for(format in DescriptionParser.Format.entries) {
            val marker=format.marker
            val source="2 * 3 ${marker}palabra${marker} final"
            val draft=EditorDraft(null,null,"",source)
            val at=source.indexOf("palabra")
            assertTrue(draft.editDescription(TextFieldValue(source.removeRange(at,at+7+marker.length),TextRange(at))))
            assertEquals("2 * 3  final",draft.description)
        }
    }

}
