package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.TechnologyState
import com.r0ybt.arachn0de.ui.state.*

/** A node-specific owner for the approved shared module; existing creation and menu lambdas stay intact. */
@Composable internal fun NodeBottomModule(node:Node,repository:NodeRepository,actions:NodeActions,
    people:List<Person>,assigned:List<Person>,assignmentsLoaded:Boolean,tags:TagState,technologies:TechnologyState,
    maxHeight:Dp,contextActions:ContextActions.()->Unit,creationActions:@Composable ()->Unit,
    editRequest:NodeProperty?,onRequestHandled:()->Unit) {
    var context by remember(node.id) {mutableStateOf(CoreBottomContext.Creation)}
    val drafts=rememberSaveable(node.id,saver=EditorDraftStore.Saver) {EditorDraftStore()}
    var fieldName by rememberSaveable(node.id) {mutableStateOf<String?>(null)}
    var baseline by rememberSaveable(node.id) {mutableStateOf(emptyList<String>())}
    var groupIds by rememberSaveable(node.id) {mutableStateOf<List<String>?>(null)}
    val draft=drafts.active
    val field=fieldName?.let(NodeProperty::valueOf)
    val busy=actions.operation.busy
    val ready=assignmentsLoaded && technologies.loaded && !busy
    fun finish() {drafts.clear();fieldName=null;groupIds=null;context=CoreBottomContext.Content}
    fun open(property:NodeProperty) {
        if(!ready || draft!=null) return
        baseline=nodePropertyValues(property,node,tags.nodeIds[node.id].orEmpty(),assigned.map {it.id},technologies.forOwner(node.id,false).map {it.id})
        fieldName=property.name
        drafts.open(node.parentId,node.id) {
            EditorDraft(node.id,node.parentId,node.title,node.description,startAt=node.startAt,dueAt=node.dueAt,
                purpose=node.purpose,obligation=node.obligation,priority=node.priority).apply {
                tagIds=tags.nodeIds[node.id].orEmpty().toList()
                responsibleIds=assigned.map {it.id}
                technologyIds=technologies.forOwner(node.id,false).map {it.id};technologiesLoaded=true
                creationGroupId=node.creationGroupId;captureSharedBaseline()
            }
        }
    }
    fun save(ids:Set<String>?=null) {
        if(draft!=null && field!=null) actions.saveProperty(draft,field,baseline,draft.propertyValues(field),ids,::finish)
    }
    fun confirm() {
        if(draft==null || field==null) return
        val shared=field in setOf(NodeProperty.DESCRIPTION,NodeProperty.PAYMENT,NodeProperty.PRIORITY,NodeProperty.PEOPLE,NodeProperty.TAGS)
        if(shared && draft.creationGroupId!=null && draft.propertyValues(field)!=baseline) {
            actions.reviewGroup(draft) {members->if(members.size>1) groupIds=members.map {it.id} else save()}
        } else save()
    }
    LaunchedEffect(editRequest,ready) {if(editRequest!=null && ready) {open(editRequest);onRequestHandled()}}
    LaunchedEffect(draft?.id) {if(draft!=null) context=CoreBottomContext.Content}
    BackHandler(enabled=draft==null && context!=CoreBottomContext.Creation) {context=CoreBottomContext.Creation}
    if(draft!=null && field!=null) {
        Box(Modifier.imePadding()) {
            NodePropertyEditor(draft,field,!busy,maxHeight*1.7f,people,tags,repository.tags,technologies.catalog,
                onCancel={actions.discardDraft(draft,::finish)},onConfirm={confirm()})
        }
    } else {
        val properties=buildList {
            fun property(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,value:NodeProperty) {
                add(ContextAction(label,icon,{open(value)},enabled=ready))
            }
            property("Título",Icons.Default.Title,NodeProperty.TITLE)
            property("Descripción",Icons.Default.Description,NodeProperty.DESCRIPTION)
            if(node.isCompletable) {
                property("Planificación",Icons.Default.Settings,NodeProperty.DATES)
                property("Prioridad",Icons.Default.LocalFireDepartment,NodeProperty.PRIORITY)
                property("Pago",Icons.Default.Payments,NodeProperty.PAYMENT)
            }
            property("Responsables",Icons.Default.People,NodeProperty.PEOPLE)
            property("Etiquetas",Icons.Default.Label,NodeProperty.TAGS)
            property("Editar tecnologías",Icons.Default.Code,NodeProperty.TECHNOLOGIES)
            addAll(ContextActions().apply(contextActions).items.filter {it.label=="Valores predeterminados"})
        }
        CoreBottomModule(context,{context=it},maxHeight,contextActions,creationActions,contentActions=properties)
    }
    groupIds?.let {ids->
        AlertDialog(onDismissRequest={if(!busy) groupIds=null},title={Text("Aplicar cambios compartidos")},
            text={Text("El grupo tiene ${ids.size} elementos existentes, incluidos los movidos a otras capas. Solo se propagará la propiedad compartida modificada.")},
            confirmButton={Button(enabled=!busy,onClick={save(ids.toSet())}) {Text("Todos los elementos del grupo")}},
            dismissButton={Column {
                TextButton(enabled=!busy,onClick={groupIds=null;save()}) {Text("Solo este elemento")}
                TextButton(enabled=!busy,onClick={groupIds=null}) {Text("Volver al editor")}
            }})
    }
}
