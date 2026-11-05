package com.r0ybt.arachn0de.ui

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.unit.em
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Shared rendering; only read views substitute image references with bounded thumbnails. */
@Composable
internal fun AttachmentText(text: String, modifier: Modifier = Modifier, color: Color = Arachn0deColors.TextSecondary,
    fontSize: TextUnit = 12.sp, style: TextStyle = TextStyle.Default, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, lineHeight: TextUnit = TextUnit.Unspecified, ownerNodeId:String?=null, webLinks:Boolean=false, selectableDescription:Boolean=false) {
    val bashCursor=rememberBashTextCursor()
    val linkContext=LocalContext.current
    val onWeb:((String)->Unit)?=if(webLinks) ({url->openDescriptionWebLink(linkContext,url)}) else null
    val parts = remember(text) { DescriptionParser.parse(text) }
    @Composable fun render(body:AnnotatedString,inlineContent:Map<String,InlineTextContent> = emptyMap()) {
        Column(modifier) {
            var codeLayout by remember {mutableStateOf<TextLayoutResult?>(null)}
            val codeBackground=androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
            val codeOutline=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            val content:@Composable ()->Unit={
                Text(body,bashCursor.modifier.drawBehind {codeLayout?.let {drawDescriptionCodeBlocks(it,codeBackground,codeOutline)}},onTextLayout={bashCursor.layout=it;codeLayout=it},color=color,fontSize=fontSize,
                    style=style.copy(fontWeight=FontWeight.Normal,fontFamily=if(LocalBashPresentation.current)androidx.compose.ui.text.font.FontFamily.Monospace else style.fontFamily),
                    maxLines=maxLines,overflow=overflow,softWrap=softWrap,lineHeight=lineHeight,inlineContent=inlineContent)
            }
            if(selectableDescription)SelectionContainer {content()} else content()
        }
    }
    if(parts.none {it.imageId!=null}) {
        render(descriptionBody(parts,emptyMap(),Arachn0deColors.ImageLink,{},onWeb));return
    }
    val app=LocalContext.current.applicationContext as Arachn0deApplication
    val rows by remember(app) {app.attachmentRepository.observeFiles()}.collectAsState(emptyList())
    var available by remember {mutableStateOf(emptyMap<String,AttachmentFileEntity>())}
    LaunchedEffect(rows) {available=app.attachmentRepository.availableFiles()}
    var viewing by rememberSaveable {mutableStateOf<String?>(null)}
    val thumbnailFiles=parts.filter {it.imageId!=null && !it.audio}.mapNotNull {available[it.imageId]}
        .filter {it.mimeType.startsWith("image/")}.associateBy {it.id}
    val body=descriptionBody(parts,available,Arachn0deColors.ImageLink,{viewing=it},onWeb,thumbnailFiles.keys)
    val inlineContent=thumbnailFiles.mapValues {(_,file)->
        InlineTextContent(Placeholder(72.sp,72.sp,PlaceholderVerticalAlign.Center)) {label->
            DescriptionThumbnail(file,app.attachmentRepository,label) {viewing=file.id}
        }
    }
    render(body,inlineContent)
    val scope=rememberCoroutineScope()
    val removal=remember(scope) {com.r0ybt.arachn0de.ui.state.OperationState(scope)}
    OperationErrorDialog(removal)
    viewing?.let {id->AttachmentViewer(id,app.attachmentRepository,
        onRemove=if(ownerNodeId!=null && available[id]?.mimeType=="audio/aac") ({removal.submit("No se pudo eliminar el audio.",{app.security.current.value?.audio?.beforeDelete(id);app.attachmentRepository.detachNode(ownerNodeId,id);true}) {viewing=null}}) else null,
        onClose={viewing=null})}
}

internal fun descriptionBody(parts:List<DescriptionParser.Part>,files:Map<String,AttachmentFileEntity>,onImage:(String)->Unit)=
    descriptionBody(parts,files,com.r0ybt.arachn0de.ui.theme.SemanticColors.ImageLink,onImage,null)

/** Shared annotated renderer: caller keeps its existing size, wrapping and preview limits. */
internal fun descriptionBody(parts: List<DescriptionParser.Part>, files: Map<String, AttachmentFileEntity>,
    linkColor:Color,onImage: (String) -> Unit,onWeb:((String)->Unit)?=null,thumbnailIds:Set<String> = emptySet()): AnnotatedString = buildAnnotatedString {
    val codeRanges=mutableListOf<IntRange>()
    parts.forEach { part ->
        val from=length
        withStyle(descriptionSpanStyle(part.formats)) {
            val id = part.imageId
            if (id == null) {if(onWeb==null || DescriptionParser.Format.Code in part.formats) append(part.text) else appendWebLinks(part.text,linkColor,onWeb)}
            else withLink(LinkAnnotation.Clickable(id,
                TextLinkStyles(style = SpanStyle(color = linkColor)),
                linkInteractionListener = { onImage(id) })) {
                val label=part.label ?: files[id]?.originalName ?: if(part.audio) "Audio no disponible" else "Imagen no disponible"
                if(id in thumbnailIds && !part.audio) appendInlineContent(id,label) else append(label)
            }
        }
        if(DescriptionParser.Format.Code in part.formats && length>from) {
            val last=codeRanges.lastOrNull()
            if(last!=null && last.last+1==from)codeRanges[codeRanges.lastIndex]=last.first until length
            else codeRanges+=from until length
        }
    }
    codeRanges.forEach {range->
        addStringAnnotation("description-code","code",range.first,range.last+1)
        addStyle(ParagraphStyle(textIndent=androidx.compose.ui.text.style.TextIndent(24.sp,8.sp),lineHeight=1.4.em),range.first,range.last+1)
    }
}

/** The field displays labels/filenames; saved text retains atomic, stable image references. */
internal class AttachmentVisualTransformation(private val files: Map<String, AttachmentFileEntity>,private val linkColor:Color=com.r0ybt.arachn0de.ui.theme.SemanticColors.ImageLink) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original=text.text
        val projection=DescriptionParser.projection(original,files.mapValues {it.value.originalName})
        val body=descriptionBody(DescriptionParser.parse(original),files,linkColor,{})
        return TransformedText(body,object:OffsetMapping {
            override fun originalToTransformed(offset:Int):Int {
                val target=offset.coerceIn(0,original.length)
                var low=0;var high=projection.sources.size
                while(low<high) {val mid=(low+high)/2;if(projection.sources[mid]<target)low=mid+1 else high=mid}
                return low
            }
            override fun transformedToOriginal(offset:Int)=projection.sources.getOrNull(offset.coerceAtLeast(0)) ?: original.length
        })
    }
}

internal fun descriptionSpanStyle(formats:Set<DescriptionParser.Format>):SpanStyle {
    val code=DescriptionParser.Format.Code in formats
    return SpanStyle(
        fontWeight=if(DescriptionParser.Format.Bold in formats || DescriptionParser.Format.Title in formats)FontWeight.Bold else null,
        fontStyle=if(DescriptionParser.Format.Italic in formats)FontStyle.Italic else null,
        textDecoration=if(DescriptionParser.Format.Underline in formats)TextDecoration.Underline else null,
        fontSize=if(code)0.85.em else if(DescriptionParser.Format.Title in formats)1.2.em else TextUnit.Unspecified,
        fontFamily=if(code)androidx.compose.ui.text.font.FontFamily.Monospace else null,
        background=Color.Unspecified,
        color=Color.Unspecified,
    )
}
