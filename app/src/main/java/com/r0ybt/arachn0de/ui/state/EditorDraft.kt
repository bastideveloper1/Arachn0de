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
) {
    var title by mutableStateOf(title)
    var description by mutableStateOf(description)

    companion object {
        val Saver = listSaver<EditorDraft?, String>(
            save = {
                if (it == null) emptyList()
                else listOf(it.id.orEmpty(), it.parentId.orEmpty(), it.title, it.description, it.creationId)
            },
            restore = {
                if (it.isEmpty()) null
                else EditorDraft(it[0].ifEmpty { null }, it[1].ifEmpty { null }, it[2], it[3], it[4])
            },
        )
    }
}
