package com.r0ybt.arachn0de.domain.model

/** Portable inline grammar only: **bold**, *italic*, __underline__, escaped punctuation and images.
 * Images are atomic: captions and IDs never enter the formatting grammar. Unpaired markers are literal.
 */
object DescriptionParser {
    enum class Format(val marker: String) { Bold("**"), Italic("*"), Underline("__") }
    data class Part(val text: String = "", val imageId: String? = null, val label: String? = null,
        val formats: Set<Format> = emptySet())
    private data class Delimiter(val at: Int, val format: Format, val opening: Boolean, val mate: Int = -1)
    private const val escapable = "*_\\["

    data class FormatRange(val start: Int, val end: Int, val format: Format)
    fun formatRanges(text: String): List<FormatRange> = delimiters(text).values.filter { it.opening }.map { FormatRange(it.at, it.mate, it.format) }

    private fun delimiters(text: String): Map<Int, Delimiter> {
        val images = AttachmentReferences.matches(text).associateBy { it.range.first }
        val stack = mutableListOf<Delimiter>()
        val paired = mutableMapOf<Int, Delimiter>()
        var i = 0
        while (i < text.length) {
            val image = images[i]
            if (image != null) { i = image.range.last + 1; continue }
            if (text[i] == '\\' && i + 1 < text.length && text[i + 1] in escapable) { i += 2; continue }
            val top = stack.lastOrNull()
            // A pair of stars inside italic text opens bold; an odd run can close italic first.
            var starEnd = i
            if (top?.format == Format.Italic) while (starEnd < text.length && text[starEnd] == '*') starEnd++
            val canClose = top?.format != Format.Italic || (starEnd - i) % 2 != 0
            if (top != null && canClose && text.startsWith(top.format.marker, i) && i > top.at + top.format.marker.length && !text[i - 1].isWhitespace()) {
                paired[top.at] = top.copy(mate = i)
                paired[i] = Delimiter(i, top.format, false, top.at)
                stack.removeAt(stack.lastIndex)
                i += top.format.marker.length
            } else {
                val format = Format.entries.firstOrNull { text.startsWith(it.marker, i) }
                if (format != null) {
                    val end = i + format.marker.length
                    if (end < text.length && !text[end].isWhitespace()) stack.add(Delimiter(i, format, true))
                    i = end
                } else i++
            }
        }
        return paired
    }

    fun parse(text: String): List<Part> {
        val images = AttachmentReferences.matches(text).associateBy { it.range.first }
        val paired = delimiters(text)
        val parts = mutableListOf<Part>()
        val active = mutableListOf<Format>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) { parts.add(Part(text = buffer.toString(), formats = active.toSet())); buffer.clear() }
        }
        var i = 0
        while (i < text.length) {
            val image = images[i]
            val delimiter = paired[i]
            when {
                image != null -> {
                    flush(); parts.add(Part(imageId = image.groupValues[1], label = AttachmentReferences.label(image), formats = active.toSet()))
                    i = image.range.last + 1
                }
                delimiter != null -> {
                    flush()
                    if (delimiter.opening) active.add(delimiter.format) else active.remove(delimiter.format)
                    i += delimiter.format.marker.length
                }
                text[i] == '\\' && i + 1 < text.length && text[i + 1] in escapable -> { buffer.append(text[i + 1]); i += 2 }
                else -> buffer.append(text[i++])
            }
        }
        flush()
        return parts
    }
}
