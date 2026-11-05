package com.r0ybt.arachn0de.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.*
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme

/** Debug-only synthetic harness: no repository, file import, save or production entry point. */
class DescriptionSelectionPrototypeActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {Arachn0deTheme {
            val state=rememberTextFieldState(prototypeDescription())
            val scroll=rememberScrollState()
            Column(Modifier.fillMaxSize().imePadding().padding(12.dp).verticalScroll(rememberScrollState())) {
                Text("Prototipo aislado · datos de ejemplo")
                DescriptionSelectionPrototype(state,scroll)
                Text("Sin guardado en tarjetas. Probar tiradores, teclado y rotación.")
            }
        }}
    }
}

internal fun prototypeDescription():String = "**Inicio con formato**\n"+
    AttachmentReferences.token("00000000-0000-0000-0000-000000000001","Imagen de ejemplo")+"\n"+
    DescriptionParser.Format.Code.marker+(1..100).joinToString("\n") {"  línea $it <código>"}+
    DescriptionParser.Format.Code.marker+"\nFinal"

/** Preserve original offsets through individual edits, never replace the whole projected document. */
internal fun descriptionPrototypeOutput():OutputTransformation = OutputTransformation {
        // Output can be recalculated before recomposition after an edit or cut.
        val source=asCharSequence().toString()
        val projection=DescriptionParser.projection(source)
        val body=descriptionBody(DescriptionParser.parse(source),emptyMap()) {}
        var end=projection.text.length
        while(end>0) {
            var start=end-1
            while(start>0 && projection.sources[start-1]==projection.sources[end-1])start--
            val from=projection.sources[start]
            val to=if(end<projection.sources.size)projection.sources[end] else source.length
            val replacement=projection.text.substring(start,end)
            if(source.substring(from,to)!=replacement) replace(from,to,replacement)
            end=start
        }
        val prefix=projection.sources.firstOrNull() ?: source.length
        if(prefix>0)delete(0,prefix)
        body.spanStyles.forEach {addStyle(it.item,it.start,it.end)}
        body.paragraphStyles.forEach {addStyle(it.item,it.start,it.end)}
}

/** Uses the same edit validation as production, including indivisible attachment references. */
internal fun prototypeReplace(state:TextFieldState,replacement:String,knownReferences:Set<String> = emptySet()):Boolean {
    val old=state.text.toString()
    val range=state.selection
    val draft=EditorDraft(null,null,"",old)
    val next=old.replaceRange(range.min,range.max,replacement)
    val added=AttachmentReferences.ids(next)-AttachmentReferences.ids(old)
    if(added.isEmpty()) {
        if(!draft.editDescription(TextFieldValue(next,TextRange(range.min+replacement.length))))return false
        // Clipboard replacement must not silently normalize bytes outside its range.
        if(draft.description!=next)return false
        state.edit {replace(0,length,draft.description);selection=draft.descriptionValue().selection}
    } else {
        // Debug session only: permit re-pasting its own known synthetic references.
        if(!knownReferences.containsAll(added))return false
        if(AttachmentReferences.matches(old).any {
            range.min<it.range.last+1 && range.max>it.range.first &&
                !(range.min<=it.range.first && range.max>=it.range.last+1) ||
                (range.collapsed && range.start>it.range.first && range.start<=it.range.last)
        })return false
        state.edit {replace(range.min,range.max,replacement);selection=TextRange(range.min+replacement.length)}
    }
    return true
}
internal fun prototypeCopy(state:TextFieldState):String {
    var start=state.selection.min
    var end=state.selection.max
    if(start==end)return ""
    AttachmentReferences.matches(state.text.toString()).forEach {
        if(start<it.range.last+1 && end>it.range.first) {
            start=minOf(start,it.range.first);end=maxOf(end,it.range.last+1)
        }
    }
    return state.text.substring(start,end)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DescriptionSelectionPrototype(state:TextFieldState,scroll:ScrollState,session:DescriptionPrototypeSession?=null) {
    val context=LocalContext.current
    val clipboard=remember(context) {context.getSystemService(ClipboardManager::class.java)}
    var error by remember {mutableStateOf(false)}
    val original=state.text.toString()
    val baseline=rememberSaveable {original}
    val activeSession=session ?: remember(state) {DescriptionPrototypeSession(state,EditorDraft(null,null,"",baseline))}
    val router=remember(activeSession,clipboard) {DescriptionPrototypeClipboard(activeSession,clipboard) {error=true}}
    SideEffect {activeSession.snapshotDraft()}
    val body=remember(original) {descriptionBody(DescriptionParser.parse(original),emptyMap()) {}}
    var layout by remember {mutableStateOf<TextLayoutResult?>(null)}
    val background=MaterialTheme.colorScheme.surfaceVariant
    val outline=MaterialTheme.colorScheme.onSurfaceVariant
    fun copy()=router.copy()
    fun cut()=router.cut()
    fun paste()=router.paste()
    val interceptor=remember(router) {PlatformTextInputInterceptor {request,next->
        next.startInputMethod(object:PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes:EditorInfo):InputConnection =
                object:InputConnectionWrapper(request.createInputConnection(outAttributes),false) {
                    override fun performContextMenuAction(id:Int):Boolean =
                        router.contextAction(id) ?: super.performContextMenuAction(id)
                }
        })
    }}
    val input=InputTransformation {
        val draft=EditorDraft(null,null,"",originalText.toString())
        if(!draft.editDescription(TextFieldValue(asCharSequence().toString(),selection)))revertAllChanges()
        else {
            if(draft.description!=asCharSequence().toString()) {
                replace(0,length,draft.description);selection=draft.descriptionValue().selection
            }
            activeSession.recordInput(originalText.toString(),originalSelection,asCharSequence().toString())
        }
    }
    InterceptPlatformTextInput(interceptor=interceptor) {
    BasicTextField(state=state,scrollState=scroll,lineLimits=TextFieldLineLimits.MultiLine(3,12),
        cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
        inputTransformation=input,outputTransformation=remember {descriptionPrototypeOutput()},
        textStyle=LocalTextStyle.current.copy(color=MaterialTheme.colorScheme.onSurface),
        onTextLayout={layout=it()},
        modifier=Modifier.fillMaxWidth().heightIn(max=320.dp).testTag("prototype-field")
            .semantics {copyText {copy();true};cutText {cut();true};pasteText {paste();true}}
            .onPreviewKeyEvent {
                if(it.type!=KeyEventType.KeyDown)false else when {
                    it.key==Key.Copy || (it.isCtrlPressed && it.key==Key.C) || (it.isCtrlPressed && it.key==Key.Insert)->{copy();true}
                    it.key==Key.Cut || (it.isCtrlPressed && it.key==Key.X) || (it.isShiftPressed && it.key==Key.Delete)->{cut();true}
                    it.key==Key.Paste || (it.isCtrlPressed && it.key==Key.V) || (it.isShiftPressed && it.key==Key.Insert)->{paste();true}
                    it.isCtrlPressed && it.key==Key.Z->{if(it.isShiftPressed)activeSession.redo() else activeSession.undo();true}
                    it.isCtrlPressed && it.key==Key.Y->{activeSession.redo();true}
                    else->false
                }
            }
            .filterTextContextMenuComponents {it.key !in setOf(TextContextMenuKeys.CopyKey,TextContextMenuKeys.CutKey,TextContextMenuKeys.PasteKey)}
            .appendTextContextMenuComponents {
                if(!state.selection.collapsed) {
                    item("prototype-copy","Copiar") {copy();close()}
                    item("prototype-cut","Cortar") {cut();close()}
                }
                item("prototype-paste","Pegar") {paste();close()}
            },
        decorator={inner->
            // Decoration is outside the internal scroller; translate once and clip to its viewport.
            Box(Modifier.border(1.dp,outline).padding(8.dp)) {
                Box(Modifier.clipToBounds().drawBehind {
                    val result=layout
                    if(result!=null) translate(top=-scroll.value.toFloat()) {
                        body.getStringAnnotations("description-code",0,body.length).forEach {
                            if(it.start<it.end && it.end<=result.layoutInput.text.length) {
                                val top=result.getLineTop(result.getLineForOffset(it.start))
                                val bottom=result.getLineBottom(result.getLineForOffset(it.end-1))
                                drawRoundRect(background,Offset(0f,top),Size(size.width,bottom-top),CornerRadius(4.dp.toPx()))
                                drawRoundRect(outline.copy(alpha=.65f),Offset(0f,top),Size(size.width,bottom-top),CornerRadius(4.dp.toPx()),style=Stroke(1.dp.toPx()))
                            }
                        }
                    }
                }) {inner()}
            }
        })
    }
    Row {
        TextButton(onClick={copy()}) {Text("Copiar")}
        TextButton(onClick={cut()}) {Text("Cortar")}
        TextButton(onClick={paste()}) {Text("Pegar")}
    }
    if(error)Text("Operación rechazada: referencia o fragmento de formato incompleto.")
}
