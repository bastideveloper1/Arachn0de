package com.r0ybt.arachn0de.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.ui.state.EditorDraft

/** Debug-only document session. No file import, reservation release or database writes. */
internal class DescriptionPrototypeSession(
    val state:TextFieldState,
    private val draft:EditorDraft=EditorDraft(null,null,"",state.text.toString()),
) {
    private data class Edit(val text:String,val selection:TextRange)
    private val baselineIds=AttachmentReferences.ids(draft.description)
    private val originallyRemoved=draft.removedAttachmentIds.toSet()
    val knownReferences=baselineIds+AttachmentReferences.ids(state.text.toString())
    private val undo=java.util.ArrayDeque<Edit>()
    private val redo=java.util.ArrayDeque<Edit>()
    private fun current()=Edit(state.text.toString(),state.selection)
    private fun beforeChange(before:Edit,after:String) {
        if(before.text==after)return
        undo.addLast(before)
        // Bounded synthetic-session history, never a persistent history of real documents.
        while(undo.size>50)undo.removeFirst()
        redo.clear()
    }
    fun recordInput(old:String,selection:TextRange,next:String) {beforeChange(Edit(old,selection),next)}
    fun replace(text:String,beforeCommit:()->Unit = {}):Boolean {
        val before=current()
        val candidate=TextFieldState(before.text,before.selection)
        if(!prototypeReplace(candidate,text,knownReferences))return false
        beforeCommit()
        state.edit {
            check(asCharSequence().toString()==before.text && selection==before.selection) {"El documento cambió durante la operación"}
            replace(0,length,candidate.text);selection=candidate.selection
        }
        beforeChange(before,state.text.toString())
        snapshotDraft()
        return true
    }
    fun undo():Boolean {
        if(undo.isEmpty())return false
        redo.addLast(current())
        restore(undo.removeLast());return true
    }
    fun redo():Boolean {
        if(redo.isEmpty())return false
        undo.addLast(current())
        restore(redo.removeLast());return true
    }
    private fun restore(edit:Edit) {
        state.edit {replace(0,length,edit.text);selection=edit.selection}
        snapshotDraft()
    }
    /** Always derive removals from the canonical current source, including undo/redo. */
    fun snapshotDraft():EditorDraft {
        val source=state.text.toString()
        val ids=AttachmentReferences.ids(source)
        check(knownReferences.containsAll(ids)) {"Referencia ajena a la sesión de prueba"}
        Snapshot.withMutableSnapshot {
            draft.description=source
            draft.descriptionSelectionStart=state.selection.start
            draft.descriptionSelectionEnd=state.selection.end
            draft.removedAttachmentIds=((originallyRemoved+baselineIds)-ids).toList()
        }
        return draft
    }
}

/** Every supported clipboard entry point calls this router, never the visual field clipboard. */
internal class DescriptionPrototypeClipboard(
    private val session:DescriptionPrototypeSession,
    private val clipboard:ClipboardManager,
    private val rejected:()->Unit,
) {
    fun copy():Boolean {
        if(session.state.selection.collapsed)return false
        return safely {clipboard.setPrimaryClip(ClipData.newPlainText("Descripción",prototypeCopy(session.state)));true}
    }
    fun cut():Boolean {
        if(session.state.selection.collapsed)return false
        val text=prototypeCopy(session.state)
        // Keep the old clipboard untouched if removing the original range is unsafe.
        return safely {
            session.replace("") {clipboard.setPrimaryClip(ClipData.newPlainText("Descripción",text))}
                .also {if(!it)rejected()}
        }
    }
    fun paste():Boolean {
        return safely {
            val clip=clipboard.primaryClip
            if(clip==null || clip.itemCount!=1 || clip.getItemAt(0).text==null) {rejected();false}
            else session.replace(clip.getItemAt(0).text.toString()).also {if(!it)rejected()}
        }
    }
    private inline fun safely(action:()->Boolean):Boolean = try {action()} catch(_:RuntimeException) {rejected();false}
    fun contextAction(id:Int):Boolean? = when(id) {
        android.R.id.copy->{copy();true}
        android.R.id.cut->{cut();true}
        android.R.id.paste,android.R.id.pasteAsPlainText->{paste();true}
        else->null
    }
}
