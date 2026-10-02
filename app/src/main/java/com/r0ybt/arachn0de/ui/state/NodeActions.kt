package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

internal class NodeActions(private val repository: NodeRepository, scope: CoroutineScope) {
    val operation = OperationState(scope)
    fun save(projectId: String, parentId: String?, id: String?, title: String, description: String, creationId: String = UUID.randomUUID().toString(), onSuccess: () -> Unit) =
        operation.submit("No se pudo guardar el elemento. Tus cambios siguen en el formulario.", {
            if (id == null) { repository.createNode(projectId, parentId, title, description, creationId); true }
            else repository.updateNode(id, title, description)
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

    fun toggle(id: String) = operation.submit("No se pudo cambiar el completado. Puedes reintentar.", {
        repository.toggleCompleted(id)
    })

    fun navigate(id: String, onSuccess: (List<String>) -> Unit) {
        var path = emptyList<String>()
        operation.submit("No se pudo abrir la capa. Vuelve a intentarlo.", {
            path = repository.getNodePath(id).map { it.id }
            path.isNotEmpty()
        }, { onSuccess(path) })
    }
}
