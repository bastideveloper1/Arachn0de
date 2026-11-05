package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.GeneratedNodeSpec
import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.CoroutineScope
import java.util.UUID
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import com.r0ybt.arachn0de.domain.model.CreationUndo
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.TaskStatus

internal data class TaskViewFeedback(val matches:(Node,Set<String>?,Set<String>?)->Boolean,val showAll:()->Unit,val capturePosition:(Node)->((Node)->Unit))

internal class NodeActions(private val repository: NodeRepository, private val scope: CoroutineScope, private val attachments: com.r0ybt.arachn0de.data.repository.AttachmentRepository? = null) {
    val operation = OperationState(scope)
    val snackbar = SnackbarHostState()
    private var notice: kotlinx.coroutines.Job? = null
    var taskViewFeedback:TaskViewFeedback?=null
    private var lastReorderNotice:Long?=null
    fun blockedReorder() {
        val at=android.os.SystemClock.elapsedRealtime()
        if(lastReorderNotice?.let {at-it<3000}==true)return
        lastReorderNotice=at;feedback("Vuelve a Todas para reordenar")
    }
    private fun notice(message:String,label:String,action:()->Unit) {
        notice?.cancel();snackbar.currentSnackbarData?.dismiss()
        notice=scope.launch {if(snackbar.showSnackbar(message,label,duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed) action()}
    }
    private fun createdInView(nodes:List<Node>,people:Set<String>?=null,tags:Set<String>?=null):Boolean {
        val view=taskViewFeedback ?: return false
        val tasks=nodes.filter {it.isCompletable}
        if(tasks.any {!view.matches(it,people,tags)}) notice("Tarea creada. No aparece en esta vista","Ver todas",view.showAll)
        return tasks.isNotEmpty()
    }
    fun feedback(message: String) {
        notice?.cancel(); snackbar.currentSnackbarData?.dismiss()
        notice = scope.launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Short) }
    }
    private fun showCreated(token: CreationUndo) {
        taskViewFeedback?.takeIf {token.nodes.any {it.isCompletable}}?.let {view->
            if(token.nodes.any {it.isCompletable && !view.matches(it,token.people[it.id].orEmpty(),token.tags[it.id].orEmpty())})
                notice("Tarea creada. No aparece en esta vista","Ver todas",view.showAll)
            return
        }
        notice?.cancel(); snackbar.currentSnackbarData?.dismiss()
        notice = scope.launch {
            val result = snackbar.showSnackbar(if(token.nodes.size == 1) "Elemento creado" else "${token.nodes.size} elementos creados", "Deshacer", duration = SnackbarDuration.Short)
            if(result == SnackbarResult.ActionPerformed) operation.submit("Ya no es seguro deshacer esta creación. Los elementos se conservaron.", { check(repository.undoCreation(token)); true }, { feedback("Creación deshecha") })
        }
    }
    fun deleteSelected(projectId: String, ids: Set<String>, onSuccess: () -> Unit) = operation.submit("No se pudo eliminar la selección. No se aplicaron cambios.", {
        repository.deleteSelected(projectId,ids); true
    }, { onSuccess(); feedback("${ids.size} elementos eliminados") })
    fun moveSelected(projectId: String, ids: Set<String>, parentId: String?, onSuccess: () -> Unit) = operation.submit("No se pudo mover la selección. Revisa el destino.", {
        repository.moveSelected(projectId,ids,parentId); true
    }, { onSuccess(); feedback("${ids.size} elementos movidos") })
    fun reviewGroup(draft: EditorDraft, onSuccess: (List<com.r0ybt.arachn0de.domain.model.Node>) -> Unit) {
        var members=emptyList<com.r0ybt.arachn0de.domain.model.Node>()
        operation.submit("No se pudo revisar el grupo.", { members=repository.groupMembers(requireNotNull(draft.creationGroupId)); members.isNotEmpty() }, { onSuccess(members) })
    }
    fun saveGroup(draft: EditorDraft, members: Set<String>, editDates: Boolean, onSuccess: () -> Unit) = operation.submit("No se pudo actualizar el grupo. Se conservan tus cambios; revisa sus miembros y capacidades.", {
        val patch = draft.sharedPatch()
        suspend fun write(description: String) = repository.withEditorAssignments(requireNotNull(draft.id),draft.technologyIds.takeIf {draft.technologiesLoaded},draft.templateMetroCatalog,draft.templateMetroPlan,draft.templateTraveler,draft.metroJourneyId,draft.metroJourneyRevision) { repository.updateGroup(requireNotNull(draft.id),
            patch.copy(description = patch.description?.let { description }), members, draft.title.trim(), description, draft.activeStart, draft.activeDue,
            editDates, draft.obligation(), draft.financialRemovalConfirmed, draft.tagIds.toSet(), draft.priority, draft.responsibleIds.toSet()) }
        if (attachments == null) write(draft.description.trim())
        else attachments.saveNodeDraft(draft.attachmentDraftId, if (patch.description != null) members.toList() else listOf(requireNotNull(draft.id)),
            draft.description.trim(), draft.removedAttachmentIds.toSet(), ::write)
        true
    }, { onSuccess(); feedback("${members.size} elementos actualizados") })
    fun save(projectId: String?, parentId: String?, id: String?, title: String, description: String, creationId: String = UUID.randomUUID().toString(), startAt: Long? = null, dueAt: Long? = null, editDates: Boolean = true, purpose: com.r0ybt.arachn0de.domain.model.NodePurpose = com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION, obligation: com.r0ybt.arachn0de.domain.model.Obligation? = null, removeObligation: Boolean = false, responsibleIds: Set<String> = emptySet(), tagIds: Set<String> = emptySet(), priority: com.r0ybt.arachn0de.domain.model.Priority = com.r0ybt.arachn0de.domain.model.Priority.NONE, editResponsible: Boolean = false, attachmentDraft: EditorDraft? = null, onSuccess: () -> Unit) {
        var created: CreationUndo? = null
        var createdForView:Node?=null
        val audioRequested=attachmentDraft?.audioCreationRequested==true
        val provisionalTitle=attachmentDraft?.audioProvisionalTitle
        attachmentDraft?.audioCreationRequested=false
        operation.submit("No se pudo guardar el elemento. Tus cambios siguen en el formulario.", {
            require(id!=null || tagIds.size<=1) {"Selecciona una etiqueta o ninguna antes de crear el elemento."}
            suspend fun write(description: String): Boolean = repository.withEditorAssignments(id ?: creationId, attachmentDraft?.technologyIds?.takeIf { attachmentDraft.technologiesLoaded }, attachmentDraft?.templateMetroCatalog, attachmentDraft?.templateMetroPlan, attachmentDraft?.templateTraveler,attachmentDraft?.metroJourneyId,attachmentDraft?.metroJourneyRevision) {
                val saved=if (id == null) { created = repository.createNodeWithUndo(projectId, parentId, title, description, creationId, startAt.takeIf { purpose == com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION }, dueAt.takeIf { purpose == com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION }, purpose, obligation, responsibleIds, tagIds, if (purpose == com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION) priority else com.r0ybt.arachn0de.domain.model.Priority.NONE); true }
                else repository.updateEditor(id, title, description, startAt, dueAt, obligation, removeObligation, editDates, tagIds, if (editDates) priority else null, if (editResponsible) responsibleIds else null)
                if(saved && audioRequested) repository.reserveAudioCreation(id ?: creationId,provisionalTitle)
                saved
            }
            val result = if (attachments != null && attachmentDraft != null) attachments.saveNodeDraft(
                attachmentDraft.attachmentDraftId, listOf(id ?: creationId), description, attachmentDraft.removedAttachmentIds.toSet(), ::write)
                else write(description)
            if (created != null) created = if(audioRequested || attachmentDraft?.templateMetroPlan!=null) null else repository.captureCreation(listOf(creationId))
            if(result && id==null && taskViewFeedback!=null) createdForView=repository.getNode(creationId)
            result
        }, { onSuccess(); if(created!=null) showCreated(requireNotNull(created)) else createdForView?.let {createdInView(listOf(it),responsibleIds,tagIds)} })
    }

    fun saveDraft(projectId: String?, draft: EditorDraft, editDates: Boolean, onSuccess: () -> Unit) {
        if(draft.id==null && draft.tagIds.size>1) {
            operation.submit("No se pudo guardar el elemento. Tus cambios siguen en el formulario.", {error("Selecciona una etiqueta o ninguna antes de crear el elemento.")})
            return
        }

        when {
            draft.id == null && draft.batchEnabled && draft.purpose != com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER -> {
                val batch = draft.batchDraft()
                val specs = com.r0ybt.arachn0de.domain.model.NodeBatchGenerator.generate(batch.parameters(), java.util.TimeZone.getDefault())
                createBatch(requireNotNull(projectId), batch, specs, batch.responsibleIds.toSet(), onSuccess)
            }
            draft.id == null && draft.recurrenceFrequency != "NONE" && draft.purpose == com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION -> createRecurrence(requireNotNull(projectId), draft, onSuccess)
            else -> save(projectId, draft.parentId, draft.id, draft.title.trim(), draft.description.trim(), draft.creationId,
                draft.activeStart, draft.activeDue, editDates, draft.purpose, draft.obligation(), draft.financialRemovalConfirmed,
                draft.responsibleIds.toSet(), draft.tagIds.toSet(), draft.priority, editResponsible = true, attachmentDraft = draft, onSuccess = onSuccess)
        }
    }

    fun togglePriority(id:String) = operation.submit("No se pudo cambiar la prioridad.", {
        repository.togglePriority(id)
    })

    fun saveProperty(draft:EditorDraft,field:com.r0ybt.arachn0de.domain.model.NodeProperty,original:List<String>,values:List<String>,
        groupIds:Set<String>?=null,onSuccess:()->Unit) = operation.submit(
        "No se pudo guardar la propiedad. Revisa si cambió en otro editor; tu borrador se conserva.", {
            suspend fun write(description:String)=repository.updateProperty(requireNotNull(draft.id),field,original,
                if(field==com.r0ybt.arachn0de.domain.model.NodeProperty.DESCRIPTION) listOf(description) else values,groupIds,draft.financialRemovalConfirmed)
            if(field==com.r0ybt.arachn0de.domain.model.NodeProperty.DESCRIPTION && attachments!=null)
                attachments.saveNodeDraft(draft.attachmentDraftId,groupIds?.toList() ?: listOf(requireNotNull(draft.id)),draft.description.trim(),draft.removedAttachmentIds.toSet(),::write)
            else write(draft.description)
        },onSuccess)

    fun discardDraft(draft: EditorDraft, onSuccess: () -> Unit) = operation.submit("No se pudo descartar el borrador. Puedes reintentar.", {
        attachments?.discardDraft(draft.attachmentDraftId); true
    }, onSuccess)

    fun createRecurrence(projectId: String, draft: EditorDraft, onSuccess: () -> Unit) {
        var created=emptyList<Node>()
        operation.submit("No se pudo guardar la recurrencia. Revisa fechas, destino y responsables.", {
            val before=if(taskViewFeedback!=null)repository.getProjectNodes(projectId).mapTo(hashSetOf()) {it.id} else emptySet()
            repository.recurrence.create(requireNotNull(draft.recurrenceRule(projectId)),draft.responsibleIds.toSet(),draft.tagIds.toSet())
            while(repository.recurrence.materializeDue()) kotlinx.coroutines.yield()
            if(taskViewFeedback!=null) created=repository.getProjectNodes(projectId).filter {it.id !in before}
            true
        },{onSuccess();if(created.isNotEmpty())createdInView(created,draft.responsibleIds.toSet(),draft.tagIds.toSet())})
    }

    fun createBatch(projectId: String, draft: NodeBatchDraft, specs: List<GeneratedNodeSpec>, responsibleIds: Set<String>, onSuccess: () -> Unit) {
        var created: CreationUndo? = null
        operation.submit("No se pudo crear el lote. Se conservan tus parámetros; revisa el destino y los responsables y reintenta.", {
            created = repository.createBatchWithUndo(projectId, draft.parentId, draft.batchId, specs, responsibleIds, draft.dates.tagIds.toSet())
            true
        }, { onSuccess(); created?.let { showCreated(it) } })
    }

    fun convert(id: String, purpose: com.r0ybt.arachn0de.domain.model.NodePurpose, removeObligation: Boolean = false, onSuccess: () -> Unit = {}) =
        operation.submit("No se pudo convertir el elemento. Puedes reintentar.", {
            repository.convertPurpose(id, purpose, removeObligation)
        }, onSuccess)

    fun delete(id: String, onSuccess: () -> Unit) =
        operation.submit("No se pudo eliminar el elemento. Puedes reintentar.", {
            repository.deleteNode(id)
        }, onSuccess)

    fun moveToDrafts(id:String,onSuccess:()->Unit) = operation.submit("Solo se pueden trasladar notas y tareas independientes. Retira primero sus relaciones de Sprint, planificación, recurrencia o grupo; el contenido se conserva.", {repository.relocateNode(id,null)},onSuccess)

    fun move(id: String, parentId: String?, onSuccess: () -> Unit) =
        operation.submit("No se pudo mover el elemento. Puedes reintentar.", {
            repository.moveNode(id, parentId)
        }, onSuccess)

    fun reorder(id: String, parentId: String?, moveUp: Boolean, onSuccess: () -> Unit) =
        operation.submit("No se pudo cambiar el orden. Puedes reintentar.", {
            repository.reorderNode(id, parentId, moveUp)
        }, onSuccess)

    fun reorderTo(id: String, parentId: String?, targetId: String) =
        operation.submit("No se pudo cambiar el orden. Puedes reintentar.", {
            repository.reorderNodeTo(id, parentId, targetId)
        })

    fun reorderLayers(id:String,parentId:String?,moveUp:Boolean,onSuccess:()->Unit)=operation.submit("No se pudo cambiar el orden de Capas.",{
        repository.reorderNode(id,parentId,moveUp,layersOnly=true)
    },onSuccess)
    fun reorderLayersTo(id:String,parentId:String?,targetId:String)=operation.submit("No se pudo cambiar el orden de Capas.",{
        repository.reorderNodeTo(id,parentId,targetId,layersOnly=true)
    })

    fun reorderContent(id:String,parentId:String?,moveUp:Boolean,onSuccess:()->Unit)=operation.submit("No se pudo cambiar el orden.",{
        repository.reorderNode(id,parentId,moveUp,contentOnly=true)
    },onSuccess)
    fun reorderContentTo(id:String,parentId:String?,targetId:String)=operation.submit("No se pudo cambiar el orden.",{
        repository.reorderNodeTo(id,parentId,targetId,contentOnly=true)
    })

    fun sprintMode(id: String, enabled: Boolean, onSuccess: () -> Unit) = operation.submit("No se pudo cambiar el modo. Puedes reintentar.", {
        repository.setSprintMode(id, enabled)
    }, onSuccess)
    fun workState(id: String, state: com.r0ybt.arachn0de.domain.model.WorkState, onSuccess: () -> Unit = {}) = operation.submit("No se pudo cambiar el estado. Puedes reintentar.", {
        repository.setWorkState(id, state)
    }, onSuccess)
    fun advanceWorkState(id: String) = operation.submit("No se pudo avanzar el estado. Puedes reintentar.", {
        repository.advanceWorkState(id)
    })

    fun confirmWorkState(id: String) = operation.submit("No se pudo confirmar el estado. Puedes reintentar.", {
        repository.confirmWorkState(id)
    })

    fun cycleTaskStatus(id:String)=changeTaskStatus(id,null) {}
    fun setTaskStatus(id:String,status:TaskStatus,onSuccess:()->Unit={})=changeTaskStatus(id,status,onSuccess)
    private fun changeTaskStatus(id:String,status:TaskStatus?,onSuccess:()->Unit) {
        var before:Node?=null;var after:Node?=null;var restorePosition:((Node)->Unit)?=null
        val view=taskViewFeedback
        if(view==null) {
            operation.submit("No se pudo cambiar el estado.",{if(status==null)repository.cycleTaskStatus(id) else repository.setTaskStatus(id,status)},onSuccess)
            return
        }
        operation.submit("No se pudo cambiar el estado.",{
            before=repository.getNode(id)
            before?.let {restorePosition=view?.capturePosition?.invoke(it)}
            val saved=if(status==null)repository.cycleTaskStatus(id) else repository.setTaskStatus(id,status)
            after=repository.getNode(id);saved
        },{
            onSuccess()
            val old=before;val changed=after
            if(view!=null && old!=null && changed!=null && view.matches(old,null,null) && !view.matches(changed,null,null)) {
                notice("La tarea ya no pertenece a esta vista","Deshacer") {
                    var restored:Node?=null
                    operation.submit("No se pudo deshacer: la tarea o su configuración cambiaron.",{
                        val saved=repository.restoreTaskStatus(changed,old.taskStatus)
                        if(saved)restored=repository.getNode(changed.id)
                        saved
                    },{restored?.let {restorePosition?.invoke(it)}})
                }
            }
        })
    }

    fun setCompleted(id: String, completed: Boolean) = operation.submit("No se pudo cambiar el completado. Puedes reintentar.", {
        repository.setCompleted(id, completed)
    })

    fun navigate(id: String, onSuccess: (List<String>) -> Unit) {
        var path = emptyList<String>()
        operation.submit("No se pudo abrir la capa. Vuelve a intentarlo.", {
            path = repository.getNodePath(id).map { it.id }
            path.isNotEmpty()
        }, { onSuccess(path) })
    }
}
