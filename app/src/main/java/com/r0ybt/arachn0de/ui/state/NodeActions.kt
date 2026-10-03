package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.GeneratedNodeSpec
import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

internal class NodeActions(private val repository: NodeRepository, scope: CoroutineScope) {
    val operation = OperationState(scope)
    fun save(projectId: String, parentId: String?, id: String?, title: String, description: String, creationId: String = UUID.randomUUID().toString(), startAt: Long? = null, dueAt: Long? = null, editDates: Boolean = true, purpose: com.r0ybt.arachn0de.domain.model.NodePurpose = com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION, obligation: com.r0ybt.arachn0de.domain.model.Obligation? = null, removeObligation: Boolean = false, responsibleIds: Set<String> = emptySet(), tagIds: Set<String> = emptySet(), priority: com.r0ybt.arachn0de.domain.model.Priority = com.r0ybt.arachn0de.domain.model.Priority.NONE, editResponsible: Boolean = false, onSuccess: () -> Unit) =
        operation.submit("No se pudo guardar el elemento. Tus cambios siguen en el formulario.", {
            if (id == null) { repository.createNode(projectId, parentId, title, description, creationId, startAt, dueAt, purpose, obligation, responsibleIds, tagIds, if (purpose == com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION) priority else com.r0ybt.arachn0de.domain.model.Priority.NONE); true }
            else repository.updateEditor(id, title, description, startAt, dueAt, obligation, removeObligation, editDates, tagIds, if (editDates) priority else null, if (editResponsible) responsibleIds else null)
        }, onSuccess)

    fun saveDraft(projectId: String, draft: EditorDraft, editDates: Boolean, onSuccess: () -> Unit) {
        when {
            draft.id == null && draft.batchEnabled -> {
                val batch = draft.batchDraft()
                val specs = com.r0ybt.arachn0de.domain.model.NodeBatchGenerator.generate(batch.parameters(), java.util.TimeZone.getDefault())
                createBatch(projectId, batch, specs, batch.responsibleIds.toSet(), onSuccess)
            }
            draft.id == null && draft.recurrenceFrequency != "NONE" && draft.purpose == com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION -> createRecurrence(projectId, draft, onSuccess)
            else -> save(projectId, draft.parentId, draft.id, draft.title.trim(), draft.description.trim(), draft.creationId,
                draft.activeStart, draft.activeDue, editDates, draft.purpose, draft.obligation(), draft.financialRemovalConfirmed,
                draft.responsibleIds.toSet(), draft.tagIds.toSet(), draft.priority, editResponsible = true, onSuccess = onSuccess)
        }
    }

    fun createRecurrence(projectId: String, draft: EditorDraft, onSuccess: () -> Unit) =
        operation.submit("No se pudo guardar la recurrencia. Revisa fechas, destino y responsables.", {
            repository.recurrence.create(requireNotNull(draft.recurrenceRule(projectId)), draft.responsibleIds.toSet(), draft.tagIds.toSet())
            while (repository.recurrence.materializeDue()) kotlinx.coroutines.yield()
            true
        }, onSuccess)

    fun createBatch(projectId: String, draft: NodeBatchDraft, specs: List<GeneratedNodeSpec>, responsibleIds: Set<String>, onSuccess: () -> Unit) =
        operation.submit("No se pudo crear el lote. Se conservan tus parámetros; revisa el destino y los responsables y reintenta.", {
            repository.createBatch(projectId, draft.parentId, draft.batchId, specs, responsibleIds, draft.dates.tagIds.toSet())
            true
        }, onSuccess)

    fun convert(id: String, purpose: com.r0ybt.arachn0de.domain.model.NodePurpose, removeObligation: Boolean = false, onSuccess: () -> Unit = {}) =
        operation.submit("No se pudo convertir el elemento. Puedes reintentar.", {
            repository.convertPurpose(id, purpose, removeObligation)
        }, onSuccess)

    fun delete(id: String, onSuccess: () -> Unit) =
        operation.submit("No se pudo eliminar el elemento. Puedes reintentar.", {
            repository.deleteNode(id)
        }, onSuccess)

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
