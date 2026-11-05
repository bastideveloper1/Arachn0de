package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class AttachmentEditingStateTest {
    private val first = "00000000-0000-0000-0000-000000000001"
    private val second = "00000000-0000-0000-0000-000000000002"
    private val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
    @Test fun applyingListsShowsOneSpaceContinuesAndSurvivesReopening() {
        for(alphabetic in listOf(false,true)) {
            val draft=EditorDraft(null,null,"","")
            assertTrue(draft.applyDescriptionList(alphabetic))
            val marker=if(alphabetic) "a. " else "1. "
            assertEquals(marker,draft.description)
            val typed=marker+"primero"
            draft.editDescription(TextFieldValue(typed,TextRange(typed.length)))
            draft.editDescription(TextFieldValue(typed+"\n",TextRange(typed.length+1)))
            val expected=typed+"\n"+if(alphabetic) "b. " else "2. "
            assertEquals(expected,draft.description)
            val saved=with(EditorDraft.Saver) {scope.save(draft)}!!
            val reopened=EditorDraft.Saver.restore(saved)!!
            assertEquals(expected,reopened.description)
            assertEquals(expected,AttachmentVisualTransformation(emptyMap()).filter(AnnotatedString(reopened.description)).text.text)
        }
    }
    @Test fun applyingListsOnlyChangesSelectedLinePrefixesAndPreservesAttachmentsAndCode() {
        val token=AttachmentReferences.token(first,"Foto")
        val source="Antes\n  uno\n  $token\n\nDespués"
        val draft=EditorDraft(null,null,"",source).apply {
            descriptionSelectionStart=source.indexOf("  uno")
            descriptionSelectionEnd=source.indexOf("\n\n")+1
        }
        assertTrue(draft.applyDescriptionList(false))
        assertEquals("Antes\n  1. uno\n  2. $token\n\nDespués",draft.description)
        assertEquals(setOf(first),AttachmentReferences.ids(draft.description));assertTrue(draft.removedAttachmentIds.isEmpty())
        val many=EditorDraft(null,null,"",(1..28).joinToString("\n") {"texto"}).apply {descriptionSelectionStart=0}
        assertTrue(many.applyDescriptionList(true))
        assertTrue(many.description.endsWith("z. texto\naa. texto\nab. texto"))
        val unchanged=many.description
        many.descriptionSelectionStart=0
        assertTrue(many.applyDescriptionList(true));assertEquals(unchanged,many.description)
        val code=com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Code.marker
        val coded=EditorDraft(null,null,"",code+"uno"+code)
        assertFalse(coded.applyDescriptionList(false));assertEquals(code+"uno"+code,coded.description)
        val sentence=EditorDraft(null,null,"","Nota. Conservar")
        assertTrue(sentence.applyDescriptionList(false));assertEquals("1. Nota. Conservar",sentence.description)
        val emptyIndented=EditorDraft(null,null,"","\t  ")
        assertTrue(emptyIndented.applyDescriptionList(false));assertEquals("\t  1. ",emptyIndented.description)
    }
    @Test fun typedListsContinueExitAndPreserveSavedTextWithoutChangingCodeOrPaste() {
        fun enter(before:String):EditorDraft {
            val draft=EditorDraft(null,null,"",before)
            val next=before+"\n"
            assertTrue(draft.editDescription(TextFieldValue(next,TextRange(next.length))))
            return draft
        }
        listOf("- uno" to "- uno\n- ","1. uno" to "1. uno\n2. ","a. uno" to "a. uno\nb. ",
            "  9. nueve" to "  9. nueve\n  10. ","z. fin" to "z. fin\naa. ","z. fin\naa. otro" to "z. fin\naa. otro\nab. ").forEach {(from,to)->
            val draft=enter(from);assertEquals(to,draft.description)
            val encoded=with(EditorDraft.Saver) {scope.save(draft)}!!
            assertEquals(to,EditorDraft.Saver.restore(encoded)!!.description)
        }
        listOf("- ","1. ","a. ").forEach {assertEquals("",enter(it).description)}
        val code=com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Code.marker
        val old=code+"- código"+code
        val at=old.length-code.length
        val next=old.substring(0,at)+"\n"+old.substring(at)
        val draft=EditorDraft(null,null,"",old)
        assertTrue(draft.editDescription(TextFieldValue(next,TextRange(at+1))));assertEquals(next,draft.description)
        val paste=EditorDraft(null,null,"","")
        assertTrue(paste.editDescription(TextFieldValue("- pegado\n",TextRange(9))));assertEquals("- pegado\n",paste.description)
        assertEquals("Texto 1. normal\n",enter("Texto 1. normal").description)
    }
    @Test fun listEnterWaitsForImeCommitAndKeepsCursorSuffixAndSavedText() {
        for((before,expected) in listOf("- uno" to "- uno\n- ","4. cuatro" to "4. cuatro\n5. ","a. uno" to "a. uno\nb. ","  - " to "")) {
            val draft=EditorDraft(null,null,"",before)
            val typed=before+"\n"
            assertTrue(draft.editDescription(TextFieldValue(typed,TextRange(typed.length),TextRange(0,before.length))))
            assertEquals(typed,draft.description)
            assertTrue(draft.editDescription(TextFieldValue(typed,TextRange(typed.length))))
            assertEquals(expected,draft.description)
            assertEquals(expected.length,draft.descriptionSelectionEnd)
            val saved=with(EditorDraft.Saver) {scope.save(draft)}!!
            assertEquals(expected,EditorDraft.Saver.restore(saved)!!.description)
        }
        val middle=EditorDraft(null,null,"","  4. primero segundo\nFinal")
        val at=middle.description.indexOf(" segundo")
        val entered=middle.description.substring(0,at)+"\n"+middle.description.substring(at)
        assertTrue(middle.editDescription(TextFieldValue(entered,TextRange(at+1))))
        assertEquals("  4. primero\n  5.  segundo\nFinal",middle.description)
        assertEquals(at+6,middle.descriptionSelectionEnd)
        val interrupted=EditorDraft(null,null,"","- uno")
        interrupted.editDescription(TextFieldValue("- uno\n",TextRange(6),TextRange(0,5)))
        interrupted.editDescription(TextFieldValue("- uno\nnormal",TextRange(12)))
        assertEquals("- uno\nnormal",interrupted.description)
    }
    @Test fun codeRoundTripsSpecialCharactersAndRemovesFromCaret() {
        val text="Antes\n  val x = \"<a>&\\ruta\"\n    println(x)\nDespués"
        val draft=EditorDraft(null,null,"",text)
        draft.descriptionSelectionStart=text.indexOf("  val")
        draft.descriptionSelectionEnd=text.indexOf("\nDespués")
        assertTrue(draft.toggleDescriptionFormat(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Code))
        assertEquals(text,com.r0ybt.arachn0de.domain.model.DescriptionParser.projection(draft.description).text)
        val encoded=with(EditorDraft.Saver) {scope.save(draft)}!!
        val restored=EditorDraft.Saver.restore(encoded)!!
        assertEquals(draft.description,restored.description)
        val body=descriptionBody(com.r0ybt.arachn0de.domain.model.DescriptionParser.parse(restored.description),emptyMap()) {}
        assertEquals(text,body.text)
        assertEquals(1,body.getStringAnnotations("description-code",0,body.length).size)
        restored.descriptionSelectionStart=restored.description.indexOf("println")
        restored.descriptionSelectionEnd=restored.descriptionSelectionStart
        assertTrue(restored.toggleDescriptionFormat(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Code))
        assertEquals(text,restored.description)
    }
    @Test fun draftIdentityPendingReferencesAndCursorSurviveCloseRestoreReopen() {
        val store = EditorDraftStore(); store.open(null) { EditorDraft(null, null, "Task", "Before\nAfter") }
        val draft = store.active!!; draft.descriptionSelectionEnd = 7; draft.insertImage(first)
        draft.removedAttachmentIds = listOf(second)
        val id = draft.attachmentDraftId; val text = draft.description; store.close()
        val encoded = with(EditorDraftStore.Saver) { scope.save(store) }!!
        val restored = EditorDraftStore.Saver.restore(encoded)!!
        restored.open(null) { error("Expected previous draft") }
        assertEquals(id, restored.active!!.attachmentDraftId); assertEquals(text, restored.active!!.description)
        assertEquals(listOf(second), restored.active!!.removedAttachmentIds)
        assertEquals(draft.descriptionSelectionEnd, restored.active!!.descriptionSelectionEnd)
        restored.clear(); restored.open(null) { EditorDraft(null, null, "", "") }
        assertNotEquals(id, restored.active!!.attachmentDraftId)
    }
    @Test fun twoImagesInsertedAtCursorKeepTextAndOrder() {
        val draft = EditorDraft(null, null, "Task", "Before\nAfter")
        draft.descriptionSelectionEnd = 7; draft.insertImage(first); draft.insertImage(second)
        assertEquals("Before\n${AttachmentReferences.token(first)}\n${AttachmentReferences.token(second)}\nAfter", draft.description)
        assertEquals(listOf(first, second), AttachmentReferences.ids(draft.description).toList())
    }
    @Test fun textCanChangeAroundReferenceButNotInsideIt() {
        val draft = EditorDraft("node", null, "Task", "Before ${AttachmentReferences.token(first)} After")
        assertTrue(draft.editDescription(TextFieldValue("New ${AttachmentReferences.token(first)} After")))
        val preserved = draft.description
        assertFalse(draft.editDescription(TextFieldValue(preserved.replace("image:", "imag:"))))
        assertEquals(preserved, draft.description)
        assertTrue(draft.editDescription(TextFieldValue("New  After", TextRange(5))))
        assertEquals(listOf(first), draft.removedAttachmentIds)
    }
    @Test fun manuallyInventingReferenceIsRejected() {
        val draft = EditorDraft(null, null, "Task", "Plain")
        assertFalse(draft.editDescription(TextFieldValue("Plain ${AttachmentReferences.token(first)}")))
        assertEquals("Plain", draft.description)
    }
    @Test fun transformationHidesIdsAndHasBoundedMonotonicOffsetMaps() {
        val original = "A ${AttachmentReferences.token(first)} B ${AttachmentReferences.token(second)} C"
        val result = AttachmentVisualTransformation(emptyMap()).filter(AnnotatedString(original))
        assertEquals("A Imagen no disponible B Imagen no disponible C", result.text.text)
        assertFalse(result.text.text.contains(first)); assertFalse(result.text.text.contains("arachnode"))
        val forward = (0..original.length).map(result.offsetMapping::originalToTransformed)
        val reverse = (0..result.text.length).map(result.offsetMapping::transformedToOriginal)
        assertTrue(forward.zipWithNext().all { it.first <= it.second }); assertTrue(reverse.zipWithNext().all { it.first <= it.second })
        assertTrue(forward.all { it in 0..result.text.length }); assertTrue(reverse.all { it in 0..original.length })
    }
    @Test fun labelEditsOneOccurrenceAndCanReturnToOriginalName() {
        val draft = EditorDraft("node", null, "Task", "${AttachmentReferences.token(first)} ${AttachmentReferences.token(first, "Otro uso")}")
        assertTrue(draft.setImageLabel(0, "Gráfico"))
        assertEquals(listOf("Gráfico", "Otro uso"), AttachmentReferences.matches(draft.description).map { AttachmentReferences.label(it) }.toList())
        assertEquals(setOf(first), AttachmentReferences.ids(draft.description))
        assertTrue(draft.removedAttachmentIds.isEmpty())
        assertTrue(draft.setImageLabel(0, ""))
        assertNull(AttachmentReferences.label(AttachmentReferences.matches(draft.description).first()))
    }
    @Test fun everyFormatTogglesAndPreservesSelectionIncludingReversedSelection() {
        com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.entries.filterNot {it==com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Highlight}.forEach { format ->
            val draft = EditorDraft(null, null, "Task", "Before selected After").apply {
                descriptionSelectionStart = 15; descriptionSelectionEnd = 7
            }
            assertTrue(draft.toggleDescriptionFormat(format))
            assertEquals("Before ${format.marker}selected${format.marker} After", draft.description)
            assertEquals("selected", draft.description.substring(draft.descriptionSelectionStart, draft.descriptionSelectionEnd))
            assertTrue(draft.toggleDescriptionFormat(format))
            assertEquals("Before selected After", draft.description)
            assertEquals(7, draft.descriptionSelectionStart); assertEquals(15, draft.descriptionSelectionEnd)
        }
    }
    @Test fun selectedWrappedSyntaxCanBeRemovedAndWhitespaceStaysOutside() {
        val format = com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Bold
        val draft = EditorDraft(null, null, "Task", " **selected** ").apply {
            descriptionSelectionStart = 0; descriptionSelectionEnd = description.length
        }
        assertTrue(draft.toggleDescriptionFormat(format)); assertEquals(" selected ", draft.description)
        draft.descriptionSelectionStart = 0; draft.descriptionSelectionEnd = draft.description.length
        assertTrue(draft.toggleDescriptionFormat(format)); assertEquals(" **selected** ", draft.description)
    }
    @Test fun combiningFormatsDoesNotAccidentallyRemoveBoldStars() {
        val draft = EditorDraft(null, null, "Task", "selected").apply {
            descriptionSelectionStart = 0; descriptionSelectionEnd = description.length
        }
        com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.entries.filterNot {it==com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Highlight}.forEach { assertTrue(draft.toggleDescriptionFormat(it)) }
        assertEquals(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.entries.filterNot {it==com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Highlight}.toSet(),
            com.r0ybt.arachn0de.domain.model.DescriptionParser.parse(draft.description).single().formats)
        com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.entries.filterNot {it==com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Highlight}.forEach { assertTrue(draft.toggleDescriptionFormat(it)) }
        assertEquals("selected", draft.description)
    }
    @Test fun formatDoesNotCorruptWholeOrPartiallySelectedReferences() {
        val draft = EditorDraft(null, null, "Task", AttachmentReferences.token(first, "Gráfico")).apply {
            descriptionSelectionStart = 1; descriptionSelectionEnd = description.length - 1
        }
        val original = draft.description
        assertFalse(draft.toggleDescriptionFormat(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Bold))
        assertEquals(original, draft.description)
        draft.descriptionSelectionStart = 0; draft.descriptionSelectionEnd = draft.description.length
        assertTrue(draft.toggleDescriptionFormat(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Bold))
        assertEquals(setOf(first), AttachmentReferences.ids(draft.description))
        assertEquals(first, com.r0ybt.arachn0de.domain.model.DescriptionParser.parse(draft.description).single().imageId)
    }
    @Test fun editedLabelsAndFormattingSurviveDraftSaver() {
        val store = EditorDraftStore().apply { open(null) { EditorDraft(null, null, "Task", AttachmentReferences.token(first)) } }
        val draft = store.active!!
        draft.setImageLabel(0, "Gráfico")
        draft.descriptionSelectionStart = 0; draft.descriptionSelectionEnd = draft.description.length
        draft.toggleDescriptionFormat(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Underline)
        store.close()
        val encoded = with(EditorDraftStore.Saver) { scope.save(store) }!!
        val restored = EditorDraftStore.Saver.restore(encoded)!!
        restored.open(null) { error("Missing draft") }
        assertEquals(draft.description, restored.active!!.description)
        assertEquals("Gráfico", com.r0ybt.arachn0de.domain.model.DescriptionParser.parse(restored.active!!.description).single().label)
    }
    @Test fun sharedRendererUsesStylesLabelsFallbackAndCorrectClickableIdentity() {
        val file = com.r0ybt.arachn0de.data.local.AttachmentFileEntity(first, "$first.png", "original.png", "image/png", 1, 1, 1, "hash", 0)
        val other = file.copy(id = second, storageName = "$second.png")
        var clicked: String? = null
        val source = "**bold** *italic* __under__ ${AttachmentReferences.token(first, "Gráfico")} ${AttachmentReferences.token(second, "Gráfico")} ${AttachmentReferences.token(first)}"
        val body = descriptionBody(com.r0ybt.arachn0de.domain.model.DescriptionParser.parse(source), mapOf(first to file, second to other)) { clicked = it }
        assertEquals("bold italic under Gráfico Gráfico original.png", body.text)
        assertTrue(body.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
        assertTrue(body.spanStyles.any { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
        assertTrue(body.spanStyles.any { it.item.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline })
        val links = body.getLinkAnnotations(0, body.length).map { it.item as androidx.compose.ui.text.LinkAnnotation.Clickable }
        assertEquals(listOf(first, second, first), links.map { it.tag })
        links[1].linkInteractionListener!!.onClick(links[1]); assertEquals(second, clicked)
        assertTrue(links.all { it.styles?.style?.color == com.r0ybt.arachn0de.ui.theme.SemanticColors.ImageLink })
    }
    @Test fun shortAndLongLabelsKeepOffsetMappingsBoundedAndReferenceAtomic() {
        listOf("G", "Gráfico | ] y \\ 🕷 " + "largo ".repeat(50)).forEach { label ->
            val draft = EditorDraft(null, null, "Task", "A ${AttachmentReferences.token(first)} B")
            val previousEnd = draft.descriptionSelectionEnd
            assertTrue(draft.setImageLabel(2, label))
            assertEquals(draft.description.length, draft.descriptionSelectionEnd)
            assertNotEquals(previousEnd, draft.descriptionSelectionEnd)
            val result = AttachmentVisualTransformation(emptyMap()).filter(AnnotatedString(draft.description))
            assertEquals("A ${label.trim()} B", result.text.text)
            val forward = (0..draft.description.length).map(result.offsetMapping::originalToTransformed)
            val reverse = (0..result.text.length).map(result.offsetMapping::transformedToOriginal)
            assertTrue(forward.zipWithNext().all { it.first <= it.second })
            assertTrue(reverse.zipWithNext().all { it.first <= it.second })
            assertTrue(forward.all { it in 0..result.text.length }); assertTrue(reverse.all { it in 0..draft.description.length })
            val range = AttachmentReferences.matches(draft.description).single().range
            val imageStart = result.offsetMapping.originalToTransformed(range.first)
            val imageEnd = result.offsetMapping.originalToTransformed(range.last + 1)
            assertTrue((imageStart..imageEnd).all {
                result.offsetMapping.transformedToOriginal(it) in listOf(range.first, range.last + 1)
            })
        }
    }
}
