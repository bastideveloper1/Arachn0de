package com.r0ybt.arachn0de.ui.state

import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver

/** Only user input and destination identity belong in saved state, never Room entities. */
internal class EditorDraft(
    val id: String?,
    val parentId: String?,
    title: String,
    description: String,
    val creationId: String = id ?: UUID.randomUUID().toString(),
    startAt: Long? = null,
    dueAt: Long? = null,
    purpose: com.r0ybt.arachn0de.domain.model.NodePurpose = com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION,
) {
    var purpose by mutableStateOf(purpose)
    var title by mutableStateOf(title)
    var description by mutableStateOf(description)
    var startAt by mutableStateOf(startAt)
    var dueAt by mutableStateOf(dueAt)

    companion object {
        val Saver = listSaver<EditorDraft?, String>(
            save = {
                if (it == null) emptyList()
                else listOf(it.id.orEmpty(), it.parentId.orEmpty(), it.title, it.description, it.creationId, it.startAt?.toString().orEmpty(), it.dueAt?.toString().orEmpty(), it.purpose.name)
            },
            restore = {
                if (it.isEmpty()) null
                else EditorDraft(it[0].ifEmpty { null }, it[1].ifEmpty { null }, it[2], it[3], it[4], it.getOrNull(5)?.toLongOrNull(), it.getOrNull(6)?.toLongOrNull(), it.getOrNull(7)?.let(com.r0ybt.arachn0de.domain.model.NodePurpose::valueOf) ?: com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION)
            },
        )
    }
}
