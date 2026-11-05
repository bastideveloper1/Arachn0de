package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.DescriptionParser
import com.r0ybt.arachn0de.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class CodeSelectionNormalizationTest {
    private val code=DescriptionParser.Format.Code
    private val m=code.marker
    private fun draft(text:String,start:Int=0,end:Int=text.length)=EditorDraft(null,null,"",text).apply {descriptionSelectionStart=start;descriptionSelectionEnd=end}
    @Test fun normalAndMixedMultilineSelectionsToggleRepeatedlyWithoutLeakingMarkers() {
        val plain=" normal\n  <a>&ñ_ ";val mixed=" normal${m}\n  <a>&ñ_ $m"
        for(source in listOf(plain,mixed)) {
            var d=draft(source)
            assertTrue(d.toggleDescriptionFormat(code))
            assertEquals(plain,DescriptionParser.projection(d.description).text)
            assertEquals(1,DescriptionParser.formatRanges(d.description).count {it.format==code})
            val scope=object:androidx.compose.runtime.saveable.SaverScope {override fun canBeSaved(value:Any)=true}
            val encoded=with(EditorDraft.Saver) {scope.save(d)}!!
            d=EditorDraft.Saver.restore(encoded)!!
            assertTrue(d.toggleDescriptionFormat(code));assertEquals(plain,d.description)
            repeat(3) {d.toggleDescriptionFormat(code);d.toggleDescriptionFormat(code);assertEquals(plain,d.description)}
        }
    }
    @Test fun partialCodeSelectionKeepsOutsideTextAndFormattingAndRejoinsOnRepeat() {
        val source="**antes** ${m}uno dos tres$m __después__"
        val start=source.indexOf("dos")
        val d=draft(source,start,start+3)
        assertTrue(d.toggleDescriptionFormat(code))
        assertEquals("antes uno dos tres después",DescriptionParser.projection(d.description).text)
        assertTrue(d.description.startsWith("**antes** "));assertTrue(d.description.endsWith(" __después__"))
        val parts=DescriptionParser.parse(d.description)
        assertTrue(parts.any {it.text.contains("dos") && code !in it.formats})
        assertTrue(parts.any {it.text.contains("uno") && code in it.formats})
        d.toggleDescriptionFormat(code);assertEquals(source,d.description)
    }
    @Test fun selectionCrossingCodeBoundaryDoesNotChangeOutsideContent() {
        val source="prefix ${m}abc def$m tail suffix"
        val d=draft(source,source.indexOf("def"),source.indexOf(" suffix"))
        d.toggleDescriptionFormat(code)
        assertEquals("prefix abc def tail suffix",DescriptionParser.projection(d.description).text)
        assertTrue(d.description.startsWith("prefix "));assertTrue(d.description.endsWith(" suffix"))
        assertEquals(1,DescriptionParser.formatRanges(d.description).count {it.format==code})
        d.toggleDescriptionFormat(code)
        assertEquals("prefix abc def tail suffix",DescriptionParser.projection(d.description).text)
    }
}
