package com.r0ybt.arachn0de.ui.state

import android.net.Uri
import com.r0ybt.arachn0de.data.repository.PersonRepository
import kotlinx.coroutines.CoroutineScope

internal class PersonActions(private val repository: PersonRepository, scope: CoroutineScope) {
    val operation = OperationState(scope)
    fun save(id: String, name: String, avatar: String?, isNew: Boolean, onSuccess: () -> Unit) =
        operation.submit("No se pudo guardar la Persona. Puedes reintentar.", { repository.save(id, name, avatar, isNew) }, onSuccess)
    fun delete(id: String, onSuccess: () -> Unit) =
        operation.submit("No se pudo eliminar la Persona. Puedes reintentar.", { repository.delete(id) }, onSuccess)
    fun assign(nodeId: String, ids: Set<String>, onSuccess: () -> Unit) =
        operation.submit("No se pudieron guardar los responsables. Puedes reintentar.", { repository.setResponsiblePeople(nodeId, ids) }, onSuccess)
    fun import(uri: Uri, onSuccess: (String) -> Unit) {
        var file: String? = null
        operation.submit("No se pudo importar la imagen. Selecciona otra imagen (máximo 20 MB).", {
            file = repository.importAvatar(uri)
            true
        }, { onSuccess(checkNotNull(file)) })
    }
}
