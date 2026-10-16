package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.data.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

internal class ProjectActions(private val repository: ProjectRepository, scope: CoroutineScope, private val attachments: com.r0ybt.arachn0de.data.repository.AttachmentRepository? = null) {
    val operation = OperationState(scope)
    fun save(id: String?, name: String, description: String, creationId: String = UUID.randomUUID().toString(), draft: EditorDraft? = null, onSuccess: () -> Unit) =
        operation.submit("No se pudo guardar el proyecto. Tus cambios siguen en el formulario.", {
            suspend fun write(description: String): Boolean =
                if(draft!=null && (draft.technologyEdited || draft.projectPropertiesLoaded)) repository.saveWithTechnologies(id,creationId,name,description,draft.technologyIds.takeIf {draft.technologyEdited},draft.tagIds.toSet().takeIf {draft.projectPropertiesLoaded},draft.responsibleIds.toSet().takeIf {draft.projectPropertiesLoaded},draft.priority.takeIf {draft.projectPropertiesLoaded})
                else if (id == null) { repository.createProject(name, description, creationId); true }
                else repository.updateProject(id, name, description)
            if (attachments != null && draft != null) attachments.saveProjectDraft(draft.attachmentDraftId, id ?: creationId,
                description, draft.removedAttachmentIds.toSet(), ::write) else write(description)
        }, onSuccess)

    fun saveField(id:String,field:String,original:String,value:String,onSuccess:()->Unit) =
        operation.submit("No se pudo guardar el campo. Puede haber cambiado en otro editor. Tu borrador se conserva; cancela y vuelve a abrirlo para revisar el valor actual.", {
            repository.updateField(id,field,original,value)
        },onSuccess)

    fun discardDraft(draft: EditorDraft, onSuccess: () -> Unit) = operation.submit("No se pudo descartar el borrador. Puedes reintentar.", {
        attachments?.discardDraft(draft.attachmentDraftId); true
    }, onSuccess)

    fun delete(id: String, onSuccess: () -> Unit) =
        operation.submit("No se pudo eliminar el proyecto. Puedes reintentar.", {
            repository.deleteProject(id)
        }, onSuccess)

    fun reorderTo(id: String, targetId: String) =
        operation.submit("No se pudo cambiar el orden. Puedes reintentar.", {
            repository.reorderProjectTo(id, targetId)
        })
}
