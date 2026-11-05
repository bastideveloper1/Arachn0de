package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.ui.theme.BashColors
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.*

internal enum class CoreBottomContext(val label:String) {Content("Contenido"),Creation("Crear"),Options("Opciones")}

/** Local to the interior of a Core. Editors keep owning their drafts and modal navigation. */
@Composable internal fun CoreBottomModule(context:CoreBottomContext?,onContext:(CoreBottomContext?)->Unit,maxHeight:Dp,
    contextActions:ContextActions.()->Unit,creationActions:@Composable ()->Unit,onEditProject:((String)->Unit)?=null,contentActions:List<ContextAction>?=null,habitualLabel:String=CoreBottomContext.Creation.label,viewControls:TaskViewControls?=null,visibleContexts:List<CoreBottomContext> = CoreBottomContext.entries,contentLabel:String=CoreBottomContext.Content.label,optionsLabel:String=CoreBottomContext.Options.label) {
    fun label(item:CoreBottomContext)=when(item) {CoreBottomContext.Content->contentLabel;CoreBottomContext.Creation->habitualLabel;CoreBottomContext.Options->optionsLabel}
    var lastContext by rememberSaveable {mutableStateOf(CoreBottomContext.Creation)}
    SideEffect {if(context!=null) lastContext=context}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val threshold=with(LocalDensity.current) {24.dp.toPx()}
    fun choose(next:CoreBottomContext?) {viewControls?.onClose?.invoke();focus.clearFocus();keyboard?.hide();onContext(next)}
    val gestures=Modifier.pointerInput(context,lastContext,threshold) {
        var distance=0f
        detectVerticalDragGestures(onDragStart={distance=0f},onDragCancel={distance=0f},onDragEnd={
            if(distance>threshold) choose(null) else if(distance < -threshold && context==null) choose(lastContext)
        },onVerticalDrag={change,amount->change.consume();distance+=amount})
    }
    Surface(color=Arachn0deColors.Panel,modifier=Modifier.fillMaxWidth().testTag("core-bottom-module")) {
        Column(Modifier.fillMaxWidth().heightIn(max=if(viewControls?.expanded==true)maxHeight*1.7f else maxHeight)) {
            if(context!=null) Box(Modifier.fillMaxWidth().height(24.dp).then(gestures)
                .testTag("core-panel-fold").clickable(onClickLabel="Contraer opciones principales") {choose(null)},contentAlignment=Alignment.BottomCenter) {
                Box(Modifier.padding(bottom=2.dp).size(36.dp,4.dp).background(Arachn0deColors.TextSecondary.copy(alpha=.65f),RoundedCornerShape(2.dp)))
            }
            if(context!=null) Column(Modifier.weight(1f,fill=false).fillMaxWidth()) {
                when(context) {
                    CoreBottomContext.Creation->creationActions()
                    CoreBottomContext.Options->ContextActionMenu("Opciones",{choose(null)},inline=true,showCancel=false,groupConversions=true,cardEditingIcons=true,
                        content={val source=ContextActions().apply(contextActions);exportSpec=source.exportSpec;items.addAll(source.items.filterNot {it.label in setOf("Tecnologías","Valores iniciales","Cambiar estado")})})
                    CoreBottomContext.Content-> {
                        val editing=onEditProject?.let {edit->listOf(
                            ContextAction("Título",Icons.Default.Title,{edit("title")}),
                            ContextAction("Descripción",Icons.Default.Description,{edit("description")}))}.orEmpty()
                        val properties=contentActions ?: (editing+ContextActions().apply(contextActions).items.filter {
                            it.label in setOf("Planificación","Fotografía","Tecnologías","Valores iniciales","Cambiar estado")+FavoriteActionLabels
                        })
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val columns=if(maxWidth>=300.dp*LocalDensity.current.fontScale) 2 else 1
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=12.dp)) {
                                ContextActionRows(properties,columns,cardEditingIcons=true)
                            }
                        }
                    }
                }
            }
            AlternatingBottomBar {
                Column {
                HorizontalDivider(color=Arachn0deColors.Outline,thickness=.5.dp)
                val barActions=visibleContexts.map {item->BottomBarAction(label(item),when(item) {
                    CoreBottomContext.Content->Icons.Default.Description
                    CoreBottomContext.Creation->Icons.Default.Add
                    CoreBottomContext.Options->Icons.Default.MoreVert
                },tag="core-context:${item.name}",selected=context==item) {choose(if(context==item)null else item)}}
                Row(Modifier.fillMaxWidth().background(navigationBarBackground).then(gestures).testTag("core-navigation-actions")) {
                    CompactBarActions(barActions,iconAbove=viewControls!=null) {a->
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick=a.run,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                                .testTag(a.tag).semantics {selected=a.selected},
                                contentPadding=PaddingValues(NavigationBarStyle.padding),
                                colors=ButtonDefaults.textButtonColors(contentColor=if(a.selected && LocalBashPresentation.current) BashColors.Electric else if(a.selected) Arachn0deColors.TextPrimary else Arachn0deColors.TextSecondary)) {
                                if(viewControls!=null)Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                    NavigationBarIcon(a.icon,label=a.label)
                                    Text(a.label,style=MaterialTheme.typography.labelSmall,softWrap=true,modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                } else {
                                    NavigationBarIcon(a.icon,label=a.label);Spacer(Modifier.width(4.dp));Text(a.label,style=MaterialTheme.typography.labelSmall)
                                }
                            }
                            HorizontalDivider(thickness=2.dp,color=if(a.selected && LocalBashPresentation.current) BashColors.Electric else if(a.selected) Arachn0deColors.TextPrimary else Arachn0deColors.Panel)
                        }
                    }
                }
                }
            }
        }
    }
}
