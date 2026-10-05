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
        description = next
    }
    descriptionSelectionStart = value.selection.start; descriptionSelectionEnd = value.selection.end
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
    var start = minOf(descriptionSelectionStart, descriptionSelectionEnd).coerceIn(0, description.length)
    var end = maxOf(descriptionSelectionStart, descriptionSelectionEnd).coerceIn(0, description.length)
    while (start < end && description[start].isWhitespace()) start++
    while (end > start && description[end - 1].isWhitespace()) end--
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
