package com.r0ybt.arachn0de.domain.model

/** Portable inline grammar only: **bold**, *italic*, __underline__, escaped punctuation and images.
 * Images are atomic: captions and IDs never enter the formatting grammar. Unpaired markers are literal.
 */
object DescriptionParser {
    enum class Format(val marker: String) { Bold("**"), Italic("*"), Underline("__"), Highlight("[[arachnode:highlight]]"), Title("[[arachnode:title]]"), Code("[[arachnode:code]]") }
    data class Part(val text: String = "", val imageId: String? = null, val label: String? = null,
        val formats: Set<Format> = emptySet(), val audio:Boolean=false)
    private data class Delimiter(val at: Int, val format: Format, val opening: Boolean, val mate: Int = -1)
    private const val escapable = "*_\\["

    data class FormatRange(val start: Int, val end: Int, val format: Format)
    fun formatRanges(text: String): List<FormatRange> = delimiters(text).values.filter { it.opening }.map { FormatRange(it.at, it.mate, it.format) }

    // HTTP URLs are literal text: underscores in paths/query strings are not formatting.
    private fun webTokens(text:String)=Regex("""(?i)https?://[^\s<>\[\]"*]+""").findAll(text).associate {match->
        val underlined=match.range.first>=2 && text.substring(match.range.first-2,match.range.first)=="__"
        val value=if(underlined && match.value.endsWith("__")) match.value.dropLast(2) else match.value
        match.range.first to value
    }

    private fun delimiters(text: String): Map<Int, Delimiter> {
        val images = AttachmentReferences.matches(text).associateBy { it.range.first }
        val web=webTokens(text)
        val stack = mutableListOf<Delimiter>()
        val paired = mutableMapOf<Int, Delimiter>()
        var i = 0
        while (i < text.length) {
            val image = images[i]
            val url=web[i]
            if(url!=null) {i+=url.length;continue}
            if (image != null) { i = image.range.last + 1; continue }
            if (stack.lastOrNull()?.format != Format.Code && text[i] == '\\' && i + 1 < text.length && text[i + 1] in escapable) { i += 2; continue }
            val top = stack.lastOrNull()
            // A pair of stars inside italic text opens bold; an odd run can close italic first.
            var starEnd = i
            if (top?.format == Format.Italic) while (starEnd < text.length && text[starEnd] == '*') starEnd++
            val canClose = top?.format != Format.Italic || (starEnd - i) % 2 != 0
            if (top != null && canClose && text.startsWith(top.format.marker, i) && i > top.at + top.format.marker.length && (top.format in setOf(Format.Code,Format.Title,Format.Highlight) || !text[i - 1].isWhitespace())) {
                paired[top.at] = top.copy(mate = i)
                paired[i] = Delimiter(i, top.format, false, top.at)
                stack.removeAt(stack.lastIndex)
                i += top.format.marker.length
            } else {
                val format = Format.entries.firstOrNull { text.startsWith(it.marker, i) && (top?.format != Format.Code || it == Format.Highlight) }
                if (format != null) {
                    val end = i + format.marker.length
                    if (end < text.length && (format in setOf(Format.Code,Format.Title,Format.Highlight) || !text[end].isWhitespace())) stack.add(Delimiter(i, format, true))
                    i = end
                } else i++
            }
        }
        return paired
    }

    fun parse(text: String): List<Part> {
        val images = AttachmentReferences.matches(text).associateBy { it.range.first }
        val web=webTokens(text)
        val paired = delimiters(text)
        val parts = mutableListOf<Part>()
        val active = mutableListOf<Format>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) { parts.add(Part(text = buffer.toString(), formats = active.filterNot { it == Format.Highlight }.toSet())); buffer.clear() }
        }
        var i = 0
        while (i < text.length) {
            val image = images[i]
            val url=web[i]
            if(url!=null) {buffer.append(url);i+=url.length;continue}
            val delimiter = paired[i]
            when {
                image != null -> {
                    flush(); parts.add(Part(imageId = image.groupValues[1], label = AttachmentReferences.label(image), formats = active.filterNot { it == Format.Highlight }.toSet(),audio=AttachmentReferences.isAudio(image)))
                    i = image.range.last + 1
                }
                delimiter != null -> {
                    flush()
                    if (delimiter.opening) active.add(delimiter.format) else active.remove(delimiter.format)
                    i += delimiter.format.marker.length
                }
                Format.Code !in active && text[i] == '\\' && i + 1 < text.length && text[i + 1] in escapable -> { buffer.append(text[i + 1]); i += 2 }
                else -> buffer.append(text[i++])
            }
        }
        flush()
        return parts
    }
    /** Source correspondence shared by word gestures and the editor; references stay atomic. */
    data class Projection(val text:String,val sources:List<Int>,val references:Set<Int>)
    fun projection(text:String,labels:Map<String,String> = emptyMap()):Projection {
        val paired=delimiters(text);val images=AttachmentReferences.matches(text).associateBy {it.range.first}
        val out=StringBuilder();val sources=mutableListOf<Int>();val refs=mutableSetOf<Int>()
        val active=mutableSetOf<Format>();var i=0
        while(i<text.length) {
            val d=paired[i];val image=images[i]
            when {
                image!=null->{
                    val label=AttachmentReferences.label(image) ?: labels[image.groupValues[1]] ?: if(AttachmentReferences.isAudio(image)) "Audio no disponible" else "Imagen no disponible"
                    label.forEach {refs.add(out.length);out.append(it);sources.add(i)};i=image.range.last+1
                }
                d!=null->{if(d.opening)active.add(d.format) else active.remove(d.format);i+=d.format.marker.length}
                Format.Code !in active && text[i]=='\\' && i+1<text.length && text[i+1] in escapable->{out.append(text[i+1]);sources.add(i);i+=2}
                else->{out.append(text[i]);sources.add(i);i++}
            }
        }
        return Projection(out.toString(),sources,refs)
    }
    fun hasExtendedFormats(text:String)=formatRanges(text).any {it.format in setOf(Format.Highlight,Format.Title,Format.Code)}

}
