package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal enum class CoreBottomContext(val label:String) {Content("Contenido"),Creation("Crear | Capas"),Options("Opciones")}

/** Local to the interior of a Core. Editors keep owning their drafts and modal navigation. */
@Composable internal fun CoreBottomModule(context:CoreBottomContext,onContext:(CoreBottomContext)->Unit,maxHeight:Dp,
    contextActions:ContextActions.()->Unit,creationActions:@Composable ()->Unit,onEditProject:((String)->Unit)?=null,contentActions:List<ContextAction>?=null,habitualLabel:String=CoreBottomContext.Creation.label) {
    Surface(color=Arachn0deColors.Surface,modifier=Modifier.fillMaxWidth().testTag("core-bottom-module")) {
        Column(Modifier.fillMaxWidth().heightIn(max=maxHeight)) {
            Column(Modifier.weight(1f,fill=false).fillMaxWidth()) {
                when(context) {
                    CoreBottomContext.Creation->creationActions()
                    CoreBottomContext.Options->ContextActionMenu("Opciones",{onContext(CoreBottomContext.Creation)},inline=true,showCancel=false,content=contextActions)
                    CoreBottomContext.Content-> {
                        val editing=onEditProject?.let {edit->listOf(
                            ContextAction("Título",Icons.Default.Title,{edit("title")}),
                            ContextAction("Descripción",Icons.Default.Description,{edit("description")}))}.orEmpty()
                        val properties=contentActions ?: (editing+ContextActions().apply(contextActions).items.filter {
                            it.label in setOf("Planificación","Fotografía","Editar tecnologías","Valores predeterminados")
                        })
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val columns=if(maxWidth>=300.dp*LocalDensity.current.fontScale) 2 else 1
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=12.dp)) {
                                ContextActionRows(properties,columns)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color=Arachn0deColors.Outline,thickness=.5.dp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                CoreBottomContext.entries.forEach {item->
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick={onContext(item)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                            .testTag("core-context:${item.name}").semantics {selected=context==item},
                            contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp),
                            colors=ButtonDefaults.textButtonColors(contentColor=if(context==item) Arachn0deColors.TextPrimary else Arachn0deColors.TextSecondary)) {
                            Icon(when(item) {CoreBottomContext.Content->Icons.Default.Description;CoreBottomContext.Creation->Icons.Default.Add;CoreBottomContext.Options->Icons.Default.MoreVert},null,Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if(item==CoreBottomContext.Creation) habitualLabel else item.label,style=MaterialTheme.typography.labelSmall)
                        }
                        HorizontalDivider(thickness=2.dp,color=if(context==item) Arachn0deColors.TextPrimary else Arachn0deColors.Surface)
                    }
                }
            }
        }
    }
}
