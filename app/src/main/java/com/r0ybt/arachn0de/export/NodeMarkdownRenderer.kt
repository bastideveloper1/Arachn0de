package com.r0ybt.arachn0de.export

/** Markdown sections inside nested list items preserve sibling/parent boundaries.
 * Beyond level six, a bounded list indent plus explicit relative levels avoids quadratic output.
 */
object NodeMarkdownRenderer {
    fun render(snapshot: NodeExportSnapshot, checkCancelled: () -> Unit = {}): String {
        val output = StringBuilder()
        fun append(value: String) {
            if (output.length.toLong() + value.length > NodeExportSnapshot.MAX_TEXT_CHARS) throw ContextTooLargeException()
            output.append(value)
        }
        snapshot.entries.forEach { entry ->
            checkCancelled()
            val indent = "    ".repeat((entry.depth - 1).coerceIn(0, 5))
            val title = literal(entry.title.replace(Regex("[\r\n]+"), " ").trim())
            val level = if (entry.depth >= 6) " *(nivel ${entry.depth + 1})*" else ""
            val prefix = when (entry.kind) {
                ExportKind.ACTION -> "- [${if (entry.completed) "x" else " "}] $title"
                ExportKind.NOTE -> if (entry.depth == 0) "# Nota: $title" else "- **Nota: $title**"
                ExportKind.LAYER -> when {
                    entry.depth == 0 -> "# $title"
                    entry.depth < 6 -> "- ${"#".repeat(entry.depth + 1)} $title"
                    else -> "- **$title**"
                }
            }
            val workflow = entry.workState?.let { " · ${it.label}" } ?: if (entry.sprintMode) " · Modo Sprint" else ""
            append("$indent$prefix$workflow$level\n")
            if (entry.description.isNotBlank()) {
                append("\n")
                val bodyIndent = if (entry.depth == 0 && entry.kind != ExportKind.ACTION) "" else "$indent    "
                val description = entry.description.replace("\r\n", "\n").replace('\r', '\n').trim('\n')
                description.lines().forEach { line ->
                    checkCancelled()
                    val text = literal(line.trimEnd())
                    // Descriptions are plain text in Arachn0de, not new exported structural sections.
                    val leading = text.takeWhile { it == ' ' || it == '\t' }
                    val content = text.drop(leading.length)
                    val structural = content.matches(Regex("^(#{1,6}\\s|[-+]\\s|[0-9]+[.)]\\s|[-=]{3,}).*"))
                    val protected = if (structural) "$leading\\$content" else text
                    append(if (protected.isBlank()) "\n" else "$bodyIndent$protected\n")
                }
            }
            append("\n")
        }
        return output.toString().trimEnd() + "\n"
    }

    private fun literal(text: String): String = buildString {
        text.forEach { c ->
            if (c in "\\*_[]`<>") append('\\')
            append(c)
        }
    }
}
