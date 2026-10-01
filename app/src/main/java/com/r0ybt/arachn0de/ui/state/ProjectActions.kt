package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.data.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope

internal class ProjectActions(private val repository: ProjectRepository, scope: CoroutineScope) {
    val operation = OperationState(scope)
    fun save(id: String?, name: String, description: String, onSuccess: () -> Unit) =
        operation.submit("No se pudo guardar el proyecto. Tus cambios siguen en el formulario.", {
            if (id == null) { repository.createProject(name, description); true }
            else repository.updateProject(id, name, description)
        }, onSuccess)

    fun delete(id: String, onSuccess: () -> Unit) =
        operation.submit("No se pudo eliminar el proyecto. Puedes reintentar.", {
            repository.deleteProject(id)
        }, onSuccess)
}
