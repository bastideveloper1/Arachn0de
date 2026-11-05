package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.room.withTransaction
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.data.repository.TechnologyState
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.ui.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads existing fields only. The original values guard a single-property edit. */
internal suspend fun corePropertyValues(app:Arachn0deApplication,id:String,field:NodeProperty,db:Arachn0deDatabase=app.database,projects:ProjectRepository=app.projectRepository):List<String> {
    val owner=requireNotNull(projects.getProject(id))
    val root=db.conversionDao().project(id)
    return when(field) {
        NodeProperty.TITLE->listOf(owner.name)
        NodeProperty.DESCRIPTION->listOf(owner.description)
        NodeProperty.PEOPLE->root?.let {r->db.conversionDao().people().filter {it.rootId==r.id}.map {it.personId}.sorted()}.orEmpty()
        NodeProperty.TAGS->root?.let {r->db.conversionDao().tags().filter {it.rootId==r.id}.map {it.tagId}.sorted()}.orEmpty()
        NodeProperty.PRIORITY->listOf(root?.priority ?: Priority.NONE.name)
        else->error("Propiedad no aplicable al Núcleo")
    }
}

/** Reuses the current transaction and project writer; does not replace unrelated metadata. */
internal suspend fun saveCoreMetadata(app:Arachn0deApplication,id:String,field:NodeProperty,original:List<String>,values:List<String>,db:Arachn0deDatabase=app.database,projects:ProjectRepository=app.projectRepository):Boolean =
    withContext(Dispatchers.IO) {db.withTransaction {
        require(field in setOf(NodeProperty.PEOPLE,NodeProperty.TAGS,NodeProperty.PRIORITY))
        check(corePropertyValues(app,id,field,db,projects)==original) {"La propiedad cambió durante la edición."}
        val current=requireNotNull(projects.getProject(id))
        projects.saveWithTechnologies(id,id,current.name,current.description,null,
            tagIds=values.toSet().takeIf {field==NodeProperty.TAGS},
            peopleIds=values.toSet().takeIf {field==NodeProperty.PEOPLE},
            priority=values.singleOrNull()?.let {if(field==NodeProperty.PRIORITY)Priority.valueOf(it) else null})
    }}

/** One existing property panel at a time, with no nested general project editor. */
@Composable internal fun CoreContentModule(project:Project,app:Arachn0deApplication,people:List<Person>,peopleLoaded:Boolean,
    tags:TagState,technologies:TechnologyState,maxHeight:Dp,context:CoreBottomContext?,onContext:(CoreBottomContext?)->Unit,
    contextActions:ContextActions.()->Unit,creationActions:@Composable ()->Unit,viewControls:TaskViewControls?,
    editRequest:String?=null,onRequestHandled:()->Unit={}) {
    val scope=rememberCoroutineScope()
    val database=app.database
    val projects=app.projectRepository
    val attachments=app.attachmentRepository
    val tagRepository=app.nodeRepository.tags
    val actions=remember(project.id,projects,attachments) {ProjectActions(projects,scope,attachments)}
    val op=actions.operation
    var currentPriority by remember(project.id) {mutableStateOf<Priority?>(null)}
    LaunchedEffect(project.id,project.updatedAt) {
        currentPriority=withContext(Dispatchers.IO) {Priority.valueOf(corePropertyValues(app,project.id,NodeProperty.PRIORITY,database,projects).single())}
    }
    fun togglePriority() {
        var next=Priority.NONE
        op.submit("No se pudo cambiar la prioridad.",{
            withContext(Dispatchers.IO) {database.withTransaction {
                val baseline=corePropertyValues(app,project.id,NodeProperty.PRIORITY,database,projects)
                next=if(Priority.valueOf(baseline.single())==Priority.HIGH) Priority.NONE else Priority.HIGH
                saveCoreMetadata(app,project.id,NodeProperty.PRIORITY,baseline,listOf(next.name),database,projects)
            }}
        },{currentPriority=next})
    }
    val drafts=rememberSaveable(project.id,saver=EditorDraftStore.Saver) {EditorDraftStore()}
    var fieldName by rememberSaveable(project.id) {mutableStateOf<String?>(null)}
    var original by rememberSaveable(project.id) {mutableStateOf(emptyList<String>())}
    val draft=drafts.active
    val field=fieldName?.let(NodeProperty::valueOf)
    fun finish() {drafts.clear();fieldName=null;original=emptyList();onContext(CoreBottomContext.Content)}
    fun open(property:NodeProperty) {
        if(property==NodeProperty.TECHNOLOGIES)return
        if(op.busy || draft!=null)return
        var seed:EditorDraft?=null
        var baseline=emptyList<String>()
        op.submit("No se pudo abrir la propiedad.",{
            withContext(Dispatchers.IO) {database.withTransaction {
                val owner=requireNotNull(projects.getProject(project.id))
                baseline=corePropertyValues(app,project.id,property,database,projects)
                seed=EditorDraft(owner.id,null,owner.name,owner.description).apply {
                    when(property) {
                        NodeProperty.PEOPLE->responsibleIds=baseline
                        NodeProperty.TAGS->tagIds=baseline
                        NodeProperty.PRIORITY->priority=Priority.valueOf(baseline.single())
                        else->Unit
                    }
                }
            }}
            true
        },{
            original=baseline;fieldName=property.name
            drafts.open(null,project.id) {requireNotNull(seed)}
            onContext(CoreBottomContext.Content)
        })
    }
    LaunchedEffect(editRequest,op.busy) {
        if(editRequest!=null && !op.busy && draft==null) {
            open(when(editRequest) {"title"->NodeProperty.TITLE;"description"->NodeProperty.DESCRIPTION;else->error("Campo desconocido")})
            onRequestHandled()
        }
    }
    fun save() {
        if(draft==null || field==null)return
        op.submit("No se pudo guardar. La propiedad puede haber cambiado; tu borrador se conserva.",{
            when(field) {
                NodeProperty.TITLE->projects.updateField(project.id,"title",original.single(),draft.title.trim())
                NodeProperty.DESCRIPTION->attachments.saveProjectDraft(draft.attachmentDraftId,project.id,
                    draft.description.trim(),draft.removedAttachmentIds.toSet()) {description->
                        projects.updateField(project.id,"description",original.single(),description)
                    }
                else->saveCoreMetadata(app,project.id,field,original,draft.propertyValues(field),database,projects)
            }
        },::finish)
    }
    OperationErrorDialog(op)
    if(draft!=null && field!=null) {
        Box(Modifier.imePadding()) {
            NodePropertyEditor(draft,field,!op.busy,maxHeight*1.7f,people,tags,tagRepository,technologies.catalog,
                onCancel={actions.discardDraft(draft,::finish)},onConfirm={save()},project=true,titleLimit=TitleLimits.PROJECT,originalTagIds=original.toSet(),
                onAssignTags={ids,closeAfterSave->submitTagAssignment(draft,ids,op,{values->
                    saveCoreMetadata(app,project.id,NodeProperty.TAGS,original,values.sorted(),database,projects)
                },{original=ids.sorted();if(closeAfterSave)actions.discardDraft(draft,::finish)})})
        }
    } else {
        val properties=buildList {
            fun property(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,value:NodeProperty,ready:Boolean=true) {
                add(ContextAction(label,icon,{open(value)},enabled=!op.busy && ready))
            }
            property("Título",Icons.Default.Title,NodeProperty.TITLE)
            property("Descripción",Icons.Default.Description,NodeProperty.DESCRIPTION)
            property("Responsables",Icons.Default.People,NodeProperty.PEOPLE,peopleLoaded)
            property("Etiquetas",Icons.Default.Label,NodeProperty.TAGS)
            add(ContextAction(if(currentPriority==Priority.HIGH) "Prioritario" else "Común",Icons.Default.Flag,
                {togglePriority()},enabled=!op.busy && currentPriority!=null))
            addAll(ContextActions().apply(contextActions).items.filter {it.label in setOf("Planificación","Fotografía","Tecnologías","Valores iniciales","Cambiar estado")+FavoriteActionLabels})
        }
        CoreBottomModule(context,onContext,maxHeight,contextActions,creationActions,contentActions=properties,viewControls=viewControls)
    }
}
