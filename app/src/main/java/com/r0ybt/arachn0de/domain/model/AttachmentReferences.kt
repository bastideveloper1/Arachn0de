package com.r0ybt.arachn0de.domain.model

/** Only canonical UUID markers are references. Unknown text and escaped markers stay literal. */
object AttachmentReferences {
    private val marker = Regex("""\[\[arachnode:image:([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})(?:\|((?:\\[\\\]|nr]|[^\]\\\r\n])*))?]]""")
    fun matches(text: String) = marker.findAll(text).filter { match ->
        var start = match.range.first - 1
        var escapes = 0
        while (start >= 0 && text[start--] == '\\') escapes++
        escapes % 2 == 0
    }
    /** Caption belongs to this occurrence, never to the file metadata. Old tokens remain valid. */
    fun token(id: String, label: String? = null): String {
        val caption = label?.trim()?.takeIf { it.isNotEmpty() } ?: return "[[arachnode:image:$id]]"
        val escaped = buildString {
            caption.forEach { c -> append(when (c) {
                '\\' -> "\\\\"; ']' -> "\\]"; '|' -> "\\|"; '\n' -> "\\n"; '\r' -> "\\r"; else -> c.toString()
            }) }
        }
        return "[[arachnode:image:$id|$escaped]]"
    }
    fun label(match: MatchResult): String? {
        val encoded = match.groups[2]?.value ?: return null
        return buildString {
            var i = 0
            while (i < encoded.length) {
                val c = encoded[i++]
                if (c == '\\' && i < encoded.length) append(when (val next = encoded[i++]) {
                    'n' -> '\n'; 'r' -> '\r'; else -> next
                }) else append(c)
            }
        }.takeIf { it.isNotBlank() }
    }
    fun withoutReferences(text: String): String {
        val result = StringBuilder(text)
        matches(text).toList().asReversed().forEach { result.delete(it.range.first, it.range.last + 1) }
        return result.toString()
    }
    fun ids(text: String): Set<String> = matches(text).map { it.groupValues[1] }.toSet()
    fun remove(text: String, id: String): String {
        val result = StringBuilder(text)
        matches(text).filter { it.groupValues[1] == id }.toList().asReversed().forEach { result.delete(it.range.first, it.range.last + 1) }
        return result.toString()
    }
}
