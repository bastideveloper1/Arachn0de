package com.r0ybt.arachn0de.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w400dp-h800dp")
class DescriptionPrototypeIntegrityTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val id="00000000-0000-0000-0000-000000000001"
    private val other="00000000-0000-0000-0000-000000000002"
    private val source get()="**Título**\n"+AttachmentReferences.token(id,"Fotografía")+"\nFinal"
    private fun connection():android.view.inputmethod.InputConnection {
        fun editor(v:android.view.View):android.view.View? {
            if(v.onCheckIsTextEditor())return v
            if(v is android.view.ViewGroup)for(i in 0 until v.childCount)editor(v.getChildAt(i))?.let {return it}
            return null
        }
        return requireNotNull(editor(compose.activity.window.decorView)?.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
    }
    @Test fun shortcutsAndImeUseOriginalAndSyncUndo() {
        val state=TextFieldState(source)
        val draft=EditorDraft(null,null,"",source).apply {removedAttachmentIds=listOf(other)}
        val session=DescriptionPrototypeSession(state,draft)
        compose.setContent {Arachn0deTheme {Column {DescriptionSelectionPrototype(state,rememberScrollState(),session)}}}
        val field=compose.onNodeWithTag("prototype-field")
        field.performClick()
        val clipboard=compose.activity.getSystemService(ClipboardManager::class.java)
        fun shortcut(key:Key,shift:Boolean=false) {field.performKeyInput {
            keyDown(Key.CtrlLeft);if(shift)keyDown(Key.ShiftLeft)
            pressKey(key)
            if(shift)keyUp(Key.ShiftLeft);keyUp(Key.CtrlLeft)
        }}
        fun checkRemoved(vararg ids:String)=compose.runOnIdle {
            assertEquals(ids.toSet(),session.snapshotDraft().removedAttachmentIds.toSet())
            assertEquals(state.text.toString(),draft.description)
            assertEquals(state.selection.start,draft.descriptionSelectionStart)
            assertEquals(state.selection.end,draft.descriptionSelectionEnd)
        }
        for(route in listOf("ctrl","insert","ime","semantics","buttons")) {
            compose.runOnIdle {state.edit {selection=TextRange(0,length)}}
            when(route) {
                "ctrl"->shortcut(Key.C)
                "insert"->shortcut(Key.Insert)
                "ime"->compose.runOnIdle {assertTrue(connection().performContextMenuAction(android.R.id.copy))}
                "semantics"->field.performSemanticsAction(SemanticsActions.CopyText) {it()}
                else->compose.onNodeWithText("Copiar").performClick()
            }
            assertEquals("Copy $route",source,clipboard.primaryClip!!.getItemAt(0).text.toString())
            when(route) {
                "ctrl"->shortcut(Key.X)
                "insert"->field.performKeyInput {keyDown(Key.ShiftLeft);pressKey(Key.Delete);keyUp(Key.ShiftLeft)}
                "ime"->compose.runOnIdle {assertTrue(connection().performContextMenuAction(android.R.id.cut))}
                "semantics"->field.performSemanticsAction(SemanticsActions.CutText) {it()}
                else->compose.onNodeWithText("Cortar").performClick()
            }
            compose.runOnIdle {assertEquals("",state.text.toString())}
            checkRemoved(id,other)
            when(route) {
                "ctrl"->shortcut(Key.V)
                "insert"->field.performKeyInput {keyDown(Key.ShiftLeft);pressKey(Key.Insert);keyUp(Key.ShiftLeft)}
                "ime"->compose.runOnIdle {assertTrue(connection().performContextMenuAction(android.R.id.pasteAsPlainText))}
                "semantics"->field.performSemanticsAction(SemanticsActions.PasteText) {it()}
                else->compose.onNodeWithText("Pegar").performClick()
            }
            compose.runOnIdle {assertEquals("Paste $route",source,state.text.toString())}
            checkRemoved(other)
            shortcut(Key.Z);compose.runOnIdle {assertEquals("",state.text.toString())};checkRemoved(id,other)
            shortcut(Key.Z);compose.runOnIdle {assertEquals(source,state.text.toString())};checkRemoved(other)
            shortcut(Key.Z,true);compose.runOnIdle {assertEquals("",state.text.toString())};checkRemoved(id,other)
            shortcut(Key.Y);compose.runOnIdle {assertEquals(source,state.text.toString())};checkRemoved(other)
        }
    }
    @Test fun unsafeFragmentsAndUnknownReferencesAreRejected() {
        val state=TextFieldState(source)
        val session=DescriptionPrototypeSession(state)
        val clipboard=compose.activity.getSystemService(ClipboardManager::class.java)
        var errors=0
        val router=DescriptionPrototypeClipboard(session,clipboard) {errors++}
        compose.runOnIdle {
            clipboard.setPrimaryClip(ClipData.newPlainText("Old","previous clipboard"))
            state.edit {selection=TextRange(1,4)} // cuts only part of the opening format marker
            assertFalse(router.cut())
            assertEquals(source,state.text.toString())
            assertEquals("previous clipboard",clipboard.primaryClip!!.getItemAt(0).text.toString())
            val at=source.indexOf("[[arachnode:")
            state.edit {selection=TextRange(at+3,at+10)}
            assertFalse(router.cut())
            assertEquals(source,state.text.toString())
            clipboard.setPrimaryClip(ClipData.newPlainText("Foreign",AttachmentReferences.token(other)))
            state.edit {selection=TextRange(source.length)}
            assertFalse(router.paste())
            assertEquals(source,state.text.toString())
            assertTrue(session.snapshotDraft().removedAttachmentIds.isEmpty())
            assertEquals(3,errors)
            // Failed clipboard write must happen before any destructive edit.
            state.edit {selection=TextRange(0,length)}
            try {session.replace("") {throw SecurityException("denied")};fail()} catch(_:SecurityException) {}
            assertEquals(source,state.text.toString())
            assertTrue(session.snapshotDraft().removedAttachmentIds.isEmpty())
            try {
                session.replace("") {state.edit {append(" newer")}}
                fail("A newer edit must not be overwritten by the prepared cut")
            } catch(_:IllegalStateException) {}
            assertEquals(source+" newer",state.text.toString())
        }
    }
    @Test fun nativeInputAndUndoKeepDraftSynchronized() {
        val state=TextFieldState(source)
        val session=DescriptionPrototypeSession(state)
        compose.setContent {Arachn0deTheme {Column {DescriptionSelectionPrototype(state,rememberScrollState(),session)}}}
        val field=compose.onNodeWithTag("prototype-field")
        field.performClick()
        compose.runOnIdle {state.edit {selection=TextRange(source.length)}}
        field.performTextInput(" extra")
        compose.runOnIdle {assertEquals(source+" extra",session.snapshotDraft().description)}
        compose.runOnIdle {assertTrue(session.undo());assertEquals(source,session.snapshotDraft().description)}
        compose.runOnIdle {assertTrue(session.redo());assertEquals(source+" extra",session.snapshotDraft().description)}
        field.performTextClearance()
        compose.runOnIdle {assertEquals(setOf(id),session.snapshotDraft().removedAttachmentIds.toSet())}
        compose.runOnIdle {
            assertTrue(session.undo())
            assertEquals(source+" extra",session.snapshotDraft().description)
            assertTrue(session.snapshotDraft().removedAttachmentIds.isEmpty())
        }
    }
    @Test fun contextMenuInvokesRawRouter() {
        val state=TextFieldState(source)
        var provider:androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider?=null
        val capture=object:androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider {
            override suspend fun showTextContextMenu(dataProvider:androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider) {provider=dataProvider}
        }
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(
                androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider provides capture,
                androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider provides capture,
            ) {Column {DescriptionSelectionPrototype(state,rememberScrollState())}}
        }}
        val field=compose.onNodeWithTag("prototype-field")
        field.performClick()
        compose.runOnIdle {state.edit {selection=TextRange(0,length)}}
        field.performMouseInput {click(button=MouseButton.Secondary)}
        compose.waitUntil {provider!=null}
        val clipboard=compose.activity.getSystemService(ClipboardManager::class.java)
        val menuSession=object:androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession {override fun close() {}}
        fun action(key:String)=compose.runOnIdle {
            val items=provider!!.data().components
            assertTrue(items.none {it.key==androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys.CutKey || it.key==androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys.CopyKey || it.key==androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys.PasteKey})
            val item=items.single {it.key==key} as androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
            item.onClick(menuSession)
        }
        action("prototype-copy")
        assertEquals(source,clipboard.primaryClip!!.getItemAt(0).text.toString())
        action("prototype-cut")
        compose.runOnIdle {assertEquals("",state.text.toString())}
        action("prototype-paste")
        compose.runOnIdle {assertEquals(source,state.text.toString())}
    }
    /** Diagnostic: a keyboard can implement its own clipboard without issuing a context action. */
    @Test fun imeReadAndCommitStillBypassClipboardRouter() {
        val state=TextFieldState(source)
        val session=DescriptionPrototypeSession(state)
        compose.setContent {Arachn0deTheme {Column {DescriptionSelectionPrototype(state,rememberScrollState(),session)}}}
        compose.onNodeWithTag("prototype-field").performClick()
        lateinit var input:android.view.inputmethod.InputConnection
        compose.runOnIdle {state.edit {selection=TextRange(0,length)};input=connection()}
        var copied=""
        compose.runOnIdle {
            copied=input.getSelectedText(0).toString()
            assertEquals(com.r0ybt.arachn0de.domain.model.DescriptionParser.projection(source).text,copied)
            assertTrue(AttachmentReferences.ids(copied).isEmpty())
            // Equivalent to an IME that manages its own clipboard and selection replacement.
            assertTrue(input.commitText("",1))
        }
        compose.runOnIdle {assertEquals("",state.text.toString())}
        compose.runOnIdle {assertTrue(input.commitText(copied,1))}
        compose.runOnIdle {
            assertEquals(copied,state.text.toString())
            assertNotEquals(source,state.text.toString())
            assertEquals(setOf(id),session.snapshotDraft().removedAttachmentIds.toSet())
        }
    }
}
