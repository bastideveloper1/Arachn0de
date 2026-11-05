package com.r0ybt.arachn0de.ui

private val compactEnumeration = Regex("""(?m)^([\t ]*(?:[0-9]+|\p{L})\.)(?=\p{L})""")
private val webEnding = Regex("""(?i)\.(?:com|org|net|edu|gov|mil|io|co|cl|es|ar|mx|uk|de|fr|app|dev|info|biz|me|tv|ai|online|site|xyz)(?:[/?#:]|$)""")

/** Card-only projection: preserve saved text, indentation and line endings. */
internal fun cardEnumerationSpacing(text:String):String = compactEnumeration.replace(text) {match ->
    val token=text.substring(match.range.first).takeWhile {!it.isWhitespace()}.trimEnd('.', ',', ';', '!', '?')
    val tail=token.substringAfter('.')
    if(tail.contains('.') || token.any {it in "/\\@:#"} || webEnding.containsMatchIn(token)) match.value
    else match.value+" "
}
