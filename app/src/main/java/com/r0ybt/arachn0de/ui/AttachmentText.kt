package com.r0ybt.arachn0de.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import com.r0ybt.arachn0de.domain.model.DescriptionParser
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Shared rendering for description details and compact previews; no thumbnails. */
@Composable
internal fun AttachmentText(text: String, modifier: Modifier = Modifier, color: Color = Arachn0deColors.TextSecondary,
    fontSize: TextUnit = 12.sp, style: TextStyle = TextStyle.Default, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, lineHeight: TextUnit = TextUnit.Unspecified) {
    val parts = remember(text) { DescriptionParser.parse(text) }
    if (parts.none { it.imageId != null }) {
        Text(descriptionBody(parts, emptyMap()) {}, modifier, color = color, fontSize = fontSize, style = style, maxLines = maxLines, overflow = overflow, softWrap = softWrap, lineHeight = lineHeight)
        return
    }
    val app = LocalContext.current.applicationContext as Arachn0deApplication
    val rows by remember(app) { app.attachmentRepository.observeFiles() }.collectAsState(emptyList())
    var available by remember { mutableStateOf(emptyMap<String, AttachmentFileEntity>()) }
    LaunchedEffect(rows) { available = app.attachmentRepository.availableFiles() }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    val body = descriptionBody(parts, available) { viewing = it }
    Text(body, modifier, color = color, fontSize = fontSize, style = style, maxLines = maxLines, overflow = overflow, softWrap = softWrap, lineHeight = lineHeight)
    viewing?.let { id -> AttachmentViewer(id, app.attachmentRepository) { viewing = null } }
}

/** Shared annotated renderer: caller keeps its existing size, wrapping and preview limits. */
internal fun descriptionBody(parts: List<DescriptionParser.Part>, files: Map<String, AttachmentFileEntity>,
    onImage: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        withStyle(SpanStyle(
            fontWeight = if (DescriptionParser.Format.Bold in part.formats) FontWeight.Bold else null,
            fontStyle = if (DescriptionParser.Format.Italic in part.formats) FontStyle.Italic else null,
            textDecoration = if (DescriptionParser.Format.Underline in part.formats) TextDecoration.Underline else null
        )) {
            val id = part.imageId
            if (id == null) append(part.text)
            else withLink(LinkAnnotation.Clickable(id,
                TextLinkStyles(style = SpanStyle(color = Arachn0deColors.ImageLink)),
                linkInteractionListener = { onImage(id) })) {
                append("${part.label ?: files[id]?.originalName ?: "Imagen no disponible"}")
            }
        }
    }
}

/** The field displays labels/filenames; saved text retains atomic, stable image references. */
internal class AttachmentVisualTransformation(private val files: Map<String, AttachmentFileEntity>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val forward = IntArray(original.length + 1)
        val reverse = mutableListOf(0)
        val output = StringBuilder()
        val labelRanges = mutableListOf<IntRange>()
        var cursor = 0
        for (match in AttachmentReferences.matches(original)) {
            while (cursor < match.range.first) {
                forward[cursor] = output.length; output.append(original[cursor++]); reverse.add(cursor)
            }
            val begin = output.length; val from = cursor; val to = match.range.last + 1
            val label = "${AttachmentReferences.label(match) ?: files[match.groupValues[1]]?.originalName ?: "Imagen no disponible"}"
            for (i in from until to) forward[i] = if (i - from < (to - from) / 2) begin else begin + label.length
            output.append(label)
            labelRanges.add(begin until output.length)
            for (i in 1..label.length) reverse.add(if (i < label.length / 2) from else to)
            cursor = to
        }
        while (cursor < original.length) {
            forward[cursor] = output.length; output.append(original[cursor++]); reverse.add(cursor)
        }
        forward[original.length] = output.length
        return TransformedText(buildAnnotatedString {
            append(output.toString())
            labelRanges.forEach { addStyle(SpanStyle(color = Arachn0deColors.ImageLink), it.first, it.last + 1) }
        }, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = forward[offset.coerceIn(0, original.length)]
            override fun transformedToOriginal(offset: Int) = reverse[offset.coerceIn(0, output.length)]
        })
    }
}
