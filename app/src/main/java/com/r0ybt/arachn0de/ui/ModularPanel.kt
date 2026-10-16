package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subject
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Presentation only. The caller owns drafts, confirmation, cancellation and back navigation. */
@Composable
internal fun ModularPanelSurface(modifier: Modifier = Modifier, shadowElevation: Dp = 4.dp, content: @Composable () -> Unit) {
    Surface(modifier, color = Arachn0deColors.Surface, contentColor = Arachn0deColors.TextPrimary,
        shadowElevation = shadowElevation) {
        Column {
            HorizontalDivider(color = Arachn0deColors.Outline)
            content()
        }
    }
}

/** Keep actions outside the scrolling area; a multiline field can own its own scrolling. */
@Composable
internal fun ModularPanelLayout(
    fullScreen: Boolean = false,
    fieldOwnsScroll: Boolean = false,
    bodyModifier: Modifier = Modifier,
    outlinedBody: Boolean = true,
    headerDivider: Boolean = true,
    compact: Boolean = false,
    header: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()
            .heightIn(max = if (fieldOwnsScroll && !fullScreen) maxHeight * .75f else maxHeight)
            .then(if (fullScreen) Modifier.fillMaxHeight() else Modifier)
            .padding(horizontal = if(compact) 8.dp else 16.dp, vertical = if(compact) 4.dp else 8.dp)) {
            header()
            if(headerDivider) HorizontalDivider(Modifier.padding(vertical = if(compact) 4.dp else 8.dp), color = Arachn0deColors.Outline)
            Column(Modifier.weight(1f, fill = fullScreen).fillMaxWidth()
                .then(if(outlinedBody) Modifier.border(1.dp, Arachn0deColors.Outline, RoundedCornerShape(8.dp)).padding(8.dp) else Modifier)
                .then(if (fieldOwnsScroll) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .then(bodyModifier), verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
            HorizontalDivider(Modifier.padding(vertical = if(compact) 4.dp else 8.dp), color = Arachn0deColors.Outline)
            actions()
        }
    }
}

internal data class ModularPanelOption(val label: String, val icon: ImageVector, val tag: String,
    val enabled: Boolean = true, val onClick: () -> Unit)

/** Six equal tiles, always three columns. Text measurement preserves equality with enlarged fonts. */
@Composable
internal fun ModularOptionGrid(options: List<ModularPanelOption>, modifier: Modifier = Modifier) {
    require(options.size == 6)
    ModularFieldMenu(options,modifier,columns=3)
}

@Composable
internal fun ModularFieldMenu(options: List<ModularPanelOption>, modifier: Modifier = Modifier,
    columns: Int = if(options.size==1) 1 else 2, compact: Boolean = false) {
    require(options.isNotEmpty() && columns>0)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density=LocalDensity.current
        val measurer=rememberTextMeasurer()
        val style=MaterialTheme.typography.labelSmall
        val horizontalLabelWidth=with(density) {((maxWidth-4.dp)/2-48.dp).roundToPx().coerceAtLeast(1)}
        val actualColumns=if(compact && (density.fontScale>=1.5f || options.any {
            measurer.measure(it.label,style,softWrap=false).size.width>horizontalLabelWidth
        })) 1 else columns
        val gap=if(compact) 4.dp else 8.dp
        val textWidth=with(density) {((maxWidth-gap*(actualColumns-1))/actualColumns-if(compact) 48.dp else 16.dp).roundToPx().coerceAtLeast(1)}
        val labelHeight=with(density) {options.maxOf {measurer.measure(it.label,style,
            softWrap=!compact, maxLines=if(compact) 1 else Int.MAX_VALUE,
            constraints=Constraints(maxWidth=textWidth)).size.height}.toDp()}
        val tileHeight=if(compact) (labelHeight+8.dp).coerceAtLeast(48.dp) else (labelHeight+46.dp).coerceAtLeast(72.dp)
        Column(verticalArrangement=Arrangement.spacedBy(gap)) {
            options.chunked(actualColumns).forEach {row->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
                    row.forEach {option->
                        if(compact) TextButton(onClick=option.onClick,enabled=option.enabled,
                            modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag(option.tag),
                            shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(8.dp),
                            colors=ButtonDefaults.textButtonColors(contentColor=Arachn0deColors.TextPrimary,
                                disabledContentColor=Arachn0deColors.Disabled)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Icon(option.icon,null,Modifier.size(20.dp).testTag("${option.tag}:icon"))
                                Text(option.label,Modifier.weight(1f),style=style)
                            }
                        } else
                        OutlinedButton(onClick=option.onClick,enabled=option.enabled,
                            modifier=Modifier.weight(1f).height(tileHeight).testTag(option.tag),
                            shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(8.dp),
                            border=androidx.compose.foundation.BorderStroke(1.dp,Arachn0deColors.TextSecondary.copy(alpha=if(option.enabled) .8f else .35f)),
                            colors=ButtonDefaults.outlinedButtonColors(containerColor=Arachn0deColors.SurfaceRaised,
                                contentColor=Arachn0deColors.TextPrimary,disabledContainerColor=Arachn0deColors.Surface,
                                disabledContentColor=Arachn0deColors.Disabled)) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Icon(option.icon,null,Modifier.size(24.dp).testTag("${option.tag}:icon"))
                                Text(option.label,Modifier.fillMaxWidth().height(labelHeight),style=style,textAlign=TextAlign.Center)
                            }
                        }
                    }
                    repeat(actualColumns-row.size) {Spacer(Modifier.weight(1f))}
                }
            }
        }
    }
}

/** Text-only sections share one in-panel editor; no repository writes occur before final save. */
@Composable
internal fun ModularTextFieldsEditor(editorKey:String,title:String,labels:List<String>,initialValues:List<String>,
    saveLabel:String,onCancel:()->Unit,inline:Boolean=false,enabled:Boolean=true,menuRequest:Int?=null,compactMenu:Boolean=false,
    fieldIcon:(Int)->ImageVector={Icons.Default.Subject},
    validField:(Int,String)->Boolean={_,_->true},
    fieldEditor:(@Composable (Int,String,(String)->Unit)->Unit)?=null,
    onSave:(List<String>)->Unit) {
    require(labels.isNotEmpty() && labels.size==initialValues.size)
    var values by rememberSaveable(editorKey) {mutableStateOf(initialValues)}
    var buffers by rememberSaveable(editorKey) {mutableStateOf(initialValues)}
    var active by rememberSaveable(editorKey) {mutableIntStateOf(-1)}
    var lastMenuRequest by rememberSaveable(editorKey) {mutableStateOf(menuRequest)}
    LaunchedEffect(menuRequest) {if(menuRequest!=lastMenuRequest) {active=-1;lastMenuRequest=menuRequest}}
    val editing=active>=0
    val pending=buffers!=values
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun returnToMenu() {focus.clearFocus();keyboard?.hide();active=-1}
    ModularPanelDialog(onDismissRequest={if(enabled) {if(editing) returnToMenu() else onCancel()}},
        title={if(editing || !compactMenu) Text(if(editing) labels[active] else title)},
        text={
            BackHandler {if(enabled) {if(editing) returnToMenu() else onCancel()}}
            if(editing) key(active) {
                val index=active
                val change:(String)->Unit={text->if(active==index && enabled && text.length<=maxOf(16000,initialValues[index].length))
                    buffers=buffers.toMutableList().also {it[index]=text}}
                if(fieldEditor!=null) fieldEditor(index,buffers[index],change)
                else ModularTextField(buffers[index],change,labels[index],enabled)

            } else ModularFieldMenu(labels.mapIndexed {index,label->
                ModularPanelOption(label,fieldIcon(index),"modular-field:$index",enabled) {active=index}
            },Modifier.testTag("modular-field-menu"),compact=compactMenu)
        },dismissButton={PrimaryControlButton(enabled=enabled,onClick={
            if(editing) {buffers=buffers.toMutableList().also {it[active]=values[active]};returnToMenu()}
            else onCancel()
        },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cancelar")}},
        confirmButton={PrimaryControlButton(enabled=enabled && (if(editing) validField(active,buffers[active]) else !pending && values.indices.all {validField(it,values[it])}),onClick={
            if(editing) {values=values.toMutableList().also {it[active]=buffers[active]};returnToMenu()}
            else onSave(values)
        },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(if(editing) "Confirmar" else saveLabel)}},
        plainBody=true,inline=inline,compact=compactMenu)
}

@Composable
internal fun ModularTextField(value:String,onChange:(String)->Unit,label:String,enabled:Boolean=true,singleLine:Boolean=false) {
    OutlinedTextField(value,onChange,label={Text(label)},minLines=if(singleLine) 1 else 2,maxLines=if(singleLine) 1 else 4,singleLine=singleLine,enabled=enabled,
        modifier=modularPanelFieldModifier(autoFocus=enabled).testTag("modular-text-field"))
}

/** Stable action edges, including when labels wrap with enlarged fonts. */
@Composable
internal fun ModularPanelActions(
    modifier: Modifier = Modifier,
    cancel: (@Composable () -> Unit)? = null,
    confirm: @Composable () -> Unit
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { cancel?.invoke() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { confirm() }
    }
}

/** Small existing dialogs use the same layout, without sheet anchors or forced expansion. */
@Composable
internal fun ModularPanelDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    fieldOwnsScroll: Boolean = false,
    plainBody: Boolean = false,
    inline: Boolean = false,
    compact: Boolean = false,
    foldLabel: String? = null,
    foldEnabled: Boolean = true
) {
    var folded by rememberSaveable(foldLabel) { mutableStateOf(false) }
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val fold:()->Unit={if(foldEnabled) {focus.clearFocus();keyboard?.hide();folded=true}}
    if(foldLabel!=null && folded) {
        Popup(alignment=Alignment.BottomCenter,properties=PopupProperties(focusable=false)) {
            Surface(color=Arachn0deColors.Surface,modifier=Modifier.fillMaxWidth().navigationBarsPadding()) {
                TextButton(onClick={folded=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    .testTag("modular-reopen:$foldLabel"),colors=ButtonDefaults.textButtonColors(contentColor=Arachn0deColors.TextPrimary)) {
                    Icon(Icons.Default.KeyboardArrowUp,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp));Text(foldLabel,style=MaterialTheme.typography.labelMedium)
                }
            }
        }
        return
    }
    val panel:@Composable ()->Unit={
        ModularPanelSurface(Modifier.fillMaxWidth(),shadowElevation=if(plainBody) 0.dp else 4.dp) {
            ModularPanelLayout(fieldOwnsScroll=fieldOwnsScroll,outlinedBody=!plainBody,headerDivider=!plainBody,compact=compact,
                header={
                    if(foldLabel!=null) {
                        val threshold=with(LocalDensity.current) {40.dp.toPx()}
                        Box(Modifier.fillMaxWidth().pointerInput(foldEnabled) {
                            var distance=0f
                            detectVerticalDragGestures(onDragStart={distance=0f},onDragEnd={if(distance>threshold) fold()},
                                onVerticalDrag={change,amount->change.consume();distance+=amount})
                        },contentAlignment=Alignment.Center) {
                            IconButton(onClick=fold,enabled=foldEnabled,modifier=Modifier.size(48.dp).testTag("modular-fold:$foldLabel")) {
                                Icon(Icons.Default.KeyboardArrowDown,"Plegar $foldLabel",Modifier.size(18.dp))
                            }
                        }
                    }
                    ProvideTextStyle(if(compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge) {title()}
                },
                actions={ModularPanelActions(cancel=dismissButton,confirm=confirmButton)},body={text()})
        }
    }
    if(inline) {panel();return}
    val dismiss=if(foldLabel!=null) fold else onDismissRequest
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Box(Modifier.matchParentSize().pointerInput(dismiss) {
                detectTapGestures { dismiss() }
            })
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {panel()}
        }
    }
}

/** Request IME once on entry, never on IME dismissal. Reposition only the focused field. */
@Composable
internal fun modularPanelFieldModifier(autoFocus: Boolean = false): Modifier {
    val bring = remember { BringIntoViewRequester() }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(Unit) {
        if (autoFocus) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(focused, ime) { if (focused) bring.bringIntoView() }
    return Modifier.fillMaxWidth().focusRequester(focus).bringIntoViewRequester(bring)
        .onFocusChanged { focused = it.isFocused }
}
