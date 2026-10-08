package com.r0ybt.arachn0de.ui.state

import android.net.Uri
import com.r0ybt.arachn0de.data.repository.TechnologyRepository
import kotlinx.coroutines.CoroutineScope

internal class TechnologyActions(private val repository: TechnologyRepository, scope: CoroutineScope) {
    val operation = OperationState(scope)
    fun save(id: String, name: String, icon: String?, isNew: Boolean, onSuccess: () -> Unit) =
        operation.submit("No se pudo guardar la tecnología. Los datos anteriores se conservan.", { repository.save(id, name, icon, isNew, draftId = id) }, onSuccess)
    fun delete(id: String, onSuccess: () -> Unit) =
        operation.submit("No se pudo eliminar la tecnología. Puedes reintentar.", { repository.delete(id) }, onSuccess)
    fun assign(owner: String, project: Boolean, ids: Set<String>, onSuccess: () -> Unit) =
        operation.submit("No se pudieron guardar las tecnologías. Puedes reintentar.", { repository.assign(owner, project, ids) }, onSuccess)
    fun import(uri: Uri, onSuccess: (String) -> Unit) {
        var file: String? = null
        operation.submit("No se pudo importar el icono. Selecciona otra imagen (máximo 20 MiB).", {
            file = repository.importIcon(uri); true
        }, { onSuccess(checkNotNull(file)) })
    }
}
