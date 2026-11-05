package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.state.OperationState

/** Optimistic selection with the existing guarded transaction; failures restore the last saved set. */
internal fun submitTagAssignment(draft:EditorDraft,ids:Set<String>,operation:OperationState,
    save:suspend (Set<String>)->Boolean,onSaved:()->Unit) {
    if(operation.busy)return
    val previous=draft.tagIds
    if(previous.toSet()==ids)return
    draft.tagIds=ids.toList()
    operation.submit("No se pudieron guardar las etiquetas. Se conserva la asignación anterior.",{
        var saved=false
        try {saved=save(ids);saved}
        finally {if(!saved)draft.tagIds=previous}
    },onSaved)
}
