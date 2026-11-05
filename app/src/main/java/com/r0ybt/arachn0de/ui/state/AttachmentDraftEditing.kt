package com.r0ybt.arachn0de.ui.state

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.domain.model.DescriptionParser

internal fun EditorDraft.insertImage(id: String) {
    val caret = descriptionSelectionEnd.coerceIn(0, description.length)
    val at = AttachmentReferences.matches(description).firstOrNull { caret > it.range.first && caret <= it.range.last }?.let { it.range.last + 1 } ?: caret
    val insertion = (if (at > 0 && description[at - 1] != '\n') "\n" else "") + AttachmentReferences.token(id) + "\n"
    description = description.substring(0, at) + insertion + description.substring(at)
    descriptionSelectionStart = at + insertion.length; descriptionSelectionEnd = descriptionSelectionStart
    removedAttachmentIds = removedAttachmentIds - id
}

/** References are indivisible; text edits may remove a whole reference but cannot corrupt it. */
internal fun EditorDraft.editDescription(value: TextFieldValue): Boolean {
    val old = description; val next = value.text
    var selectionStart=value.selection.start;var selectionEnd=value.selection.end
    val pending=pendingDescriptionList
    pendingDescriptionList=null
    if(next==old && pending!=null && pending.second==next && selectionStart==selectionEnd && selectionStart==pending.third) {
        if(value.composition!=null) pendingDescriptionList=pending
        else {
            val continued=continueDescriptionList(pending.first,next,selectionStart,selectionEnd)
            description=continued.first
            descriptionSelectionStart=continued.second;descriptionSelectionEnd=continued.second
            return true
        }
    }
    if (next != old) {
        var prefix = 0
        while (prefix < old.length && prefix < next.length && old[prefix] == next[prefix]) prefix++
        var suffix = 0
        while (suffix < old.length - prefix && suffix < next.length - prefix && old[old.lastIndex - suffix] == next[next.lastIndex - suffix]) suffix++
        val end = old.length - suffix
        val damaged = AttachmentReferences.matches(old).any { match ->
            val a = match.range.first; val b = match.range.last + 1
            (prefix < b && end > a && !(prefix <= a && end >= b)) || (prefix == end && prefix > a && prefix < b)
        }
        if (damaged || (AttachmentReferences.ids(next) - AttachmentReferences.ids(old)).isNotEmpty()) return false
        removedAttachmentIds = (removedAttachmentIds + (AttachmentReferences.ids(old) - AttachmentReferences.ids(next))).distinct()
        // A visible selection may include just one hidden delimiter. Repair only pairs
        // which existed before this edit; literal punctuation is never swept or rewritten.
        var cleaned=next
        val delta=next.length-old.length
        fun surviving(at:Int,size:Int):Int?=when {
            at+size<=prefix->at
            at>=end->at+delta
            else->null
        }
        val remove=mutableMapOf<Int,Int>()
        for(range in DescriptionParser.formatRanges(old)) {
            val size=range.format.marker.length
            val a=surviving(range.start,size);val b=surviving(range.end,size)
            if(a==null && b!=null)remove[b]=size
            if(b==null && a!=null)remove[a]=size
            if(a!=null && b!=null && a+size==b) {remove[a]=size;remove[b]=size}
        }
        remove.toSortedMap(reverseOrder()).forEach {(at,size)->
            cleaned=cleaned.removeRange(at,at+size)
            fun move(offset:Int)=if(offset>=at+size)offset-size else if(offset>at)at else offset
            selectionStart=move(selectionStart);selectionEnd=move(selectionEnd)
        }
        val continued=continueDescriptionList(old,cleaned,selectionStart,selectionEnd,value.composition!=null)
        if(value.composition!=null && continueDescriptionList(old,cleaned,selectionStart,selectionEnd).first!=cleaned)
            pendingDescriptionList=Triple(old,cleaned,selectionStart)
        description=continued.first
        selectionStart=continued.second;selectionEnd=if(continued.first==cleaned)selectionEnd else continued.second
    }
    descriptionSelectionStart = selectionStart; descriptionSelectionEnd = selectionEnd
    return true
}

internal fun EditorDraft.descriptionValue() = TextFieldValue(description,
    TextRange(descriptionSelectionStart.coerceIn(0, description.length), descriptionSelectionEnd.coerceIn(0, description.length)))

/** Changes one use; file identity, reservations and other occurrences remain untouched. */
internal fun EditorDraft.setImageLabel(at: Int, label: String): Boolean {
    val match = AttachmentReferences.matches(description).firstOrNull { it.range.first == at } ?: return false
    val end = match.range.last + 1
    val replacement = AttachmentReferences.token(match.groupValues[1], label)
    val delta = replacement.length - (end - at)
    fun move(offset: Int) = when {
        offset <= at -> offset
        offset >= end -> offset + delta
        else -> at + replacement.length
    }
    descriptionSelectionStart = move(descriptionSelectionStart)
    descriptionSelectionEnd = move(descriptionSelectionEnd)
    description = description.replaceRange(at, end, replacement)
    return true
}

/** Only wraps whole references; the selection stays on the original content for repeat toggles. */
internal fun EditorDraft.toggleDescriptionFormat(format: DescriptionParser.Format): Boolean {
    if(format==DescriptionParser.Format.Highlight)return false
    var start = minOf(descriptionSelectionStart, descriptionSelectionEnd).coerceIn(0, description.length)
    var end = maxOf(descriptionSelectionStart, descriptionSelectionEnd).coerceIn(0, description.length)
    if(format==DescriptionParser.Format.Code && start<end) {
        if(AttachmentReferences.matches(description).any {start>it.range.first && start<=it.range.last || end>it.range.first && end<=it.range.last})return false
        val result=toggleCodeSelection(description,start,end) ?: return false
        description=result.first;descriptionSelectionStart=result.second.start;descriptionSelectionEnd=result.second.end
        return true
    }
    if(format==DescriptionParser.Format.Code) {
        val block=DescriptionParser.formatRanges(description).firstOrNull {
            it.format==format && start>=it.start && end<=it.end+format.marker.length
        }
        if(block!=null) {
            val size=format.marker.length
            description=description.removeRange(block.end,block.end+size).removeRange(block.start,block.start+size)
            descriptionSelectionStart=start.coerceIn(block.start+size,block.end)-size
            descriptionSelectionEnd=end.coerceIn(block.start+size,block.end)-size
            return true
        }
    }
    if(format!=DescriptionParser.Format.Code) {
        while (start < end && description[start].isWhitespace()) start++
        while (end > start && description[end - 1].isWhitespace()) end--
    }
    if (start == end || AttachmentReferences.matches(description).any {
        start > it.range.first && start <= it.range.last || end > it.range.first && end <= it.range.last
    }) return false
    val marker = format.marker
    val size = marker.length
    val ranges = DescriptionParser.formatRanges(description)
    val delimiters = ranges.flatMap { listOf(it.start to it.format.marker, it.end to it.format.marker) }.toMap()
    fun onlyMarkers(from: Int, to: Int): Boolean {
        var cursor = from
        while (cursor < to) {
            val delimiter = delimiters[cursor] ?: return false
            cursor += delimiter.length
        }
        return cursor == to
    }
    val surrounding = ranges.filter {
        it.format == format && it.start + size <= start && end <= it.end &&
            onlyMarkers(it.start + size, start) && onlyMarkers(end, it.end)
    }.minByOrNull { it.end - it.start }
    when {
        surrounding != null -> {
            description = description.removeRange(surrounding.end, surrounding.end + size).removeRange(surrounding.start, surrounding.start + size)
            descriptionSelectionStart = start - size; descriptionSelectionEnd = end - size
        }
        ranges.any { it.format == format && it.start == start && it.end == end - size } -> {
            description = description.removeRange(end - size, end).removeRange(start, start + size)
            descriptionSelectionStart = start; descriptionSelectionEnd = end - size * 2
        }
        else -> {
            description = description.substring(0, start) + marker + description.substring(start, end) + marker + description.substring(end)
            descriptionSelectionStart = start + size; descriptionSelectionEnd = end + size
        }
    }
    return true
}

/** Removing one occurrence only detaches its file after the last use is removed and saved. */
internal fun EditorDraft.removeImageUse(at: Int): Boolean {
    val match = AttachmentReferences.matches(description).firstOrNull { it.range.first == at } ?: return false
    val id = match.groupValues[1]
    val end = match.range.last + 1
    val length = end - at
    fun move(offset: Int) = when { offset <= at -> offset; offset >= end -> offset - length; else -> at }
    descriptionSelectionStart = move(descriptionSelectionStart)
    descriptionSelectionEnd = move(descriptionSelectionEnd)
    description = description.removeRange(at, end)
    if (id !in AttachmentReferences.ids(description)) removedAttachmentIds = (removedAttachmentIds + id).distinct()
    return true
}

/** Normalize only paired code delimiters intersecting this selection, preserving other source bytes. */
internal fun toggleCodeSelection(text:String,start:Int,end:Int):Pair<String,TextRange>? {
    val marker=DescriptionParser.Format.Code.marker
    val size=marker.length
    val ranges=DescriptionParser.formatRanges(text).filter {it.format==DescriptionParser.Format.Code}
    val touched=ranges.filter {it.start<=end && it.end+size>=start}.sortedBy {it.start}
    val from=minOf(start,touched.minOfOrNull {it.start} ?: start)
    val to=maxOf(end,touched.maxOfOrNull {it.end+size} ?: end)
    val removed=touched.flatMap {listOf(it.start,it.end)}.toSet()
    val clean=StringBuilder();val source=mutableListOf<Int>();val coded=mutableListOf<Boolean>()
    var i=from;var codeIndex=0
    while(i<to) {
        if(i in removed) {i+=size;continue}
        while(codeIndex<touched.size && i>=touched[codeIndex].end)codeIndex++
        val range=touched.getOrNull(codeIndex)
        source.add(i);clean.append(text[i]);coded.add(range!=null && i>=range.start+size && i<range.end);i++
    }
    val a=source.indexOfFirst {it>=start}.let {if(it<0)source.size else it}
    val b=source.indexOfFirst {it>=end}.let {if(it<0)source.size else it}
    if(a>=b)return null
    val apply=!(a until b).all {coded[it]}
    for(at in a until b)coded[at]=apply
    val out=StringBuilder();val offsets=IntArray(clean.length+1);var active=false
    for(at in 0..clean.length) {
        val next=at<clean.length && coded[at]
        if(next!=active) {out.append(marker);active=next}
        offsets[at]=out.length
        if(at<clean.length)out.append(clean[at])
    }
    // Selection excludes its closing delimiter, so a second press removes the same format.
    val selectionEnd=offsets[b]-(if(b>0 && coded[b-1]!=(b<clean.length && coded[b]))size else 0)
    return (text.substring(0,from)+out+text.substring(to)) to TextRange(from+offsets[a],from+selectionEnd)
}
