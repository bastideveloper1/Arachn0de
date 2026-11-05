package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w400dp-h800dp")
class DescriptionSelectionPrototypeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @OptIn(ExperimentalTestApi::class)
    @Test fun dedicatedClipboardKeysPreserveRawReferences() {
        val token=AttachmentReferences.token("00000000-0000-0000-0000-000000000001","Fotografía")
        val original="**Título**\n$token\nFinal"
        val state=TextFieldState(original)
        compose.setContent {Arachn0deTheme {Column {DescriptionSelectionPrototype(state,rememberScrollState())}}}
        val field=compose.onNodeWithTag("prototype-field")
        field.performClick()
        compose.runOnIdle {state.edit {selection=TextRange(0,length)}}
        field.performKeyInput {pressKey(androidx.compose.ui.input.key.Key.Copy)}
        val clipboard=compose.activity.getSystemService(android.content.ClipboardManager::class.java)
        compose.runOnIdle {
            val copied=clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertEquals(original,copied)
            assertEquals(setOf("00000000-0000-0000-0000-000000000001"),AttachmentReferences.ids(copied))
        }
        compose.runOnIdle {state.edit {selection=TextRange(0,length)}}
        field.performKeyInput {pressKey(androidx.compose.ui.input.key.Key.Cut)}
        compose.runOnIdle {assertEquals("",state.text.toString())}
        field.performKeyInput {pressKey(androidx.compose.ui.input.key.Key.Paste)}
        compose.runOnIdle {assertEquals(original,state.text.toString())}
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun codeBackgroundScrollsAndClips() {
        val marker=DescriptionParser.Format.Code.marker
        val state=TextFieldState("plain\n"+marker+(1..100).joinToString("\n") {"line $it"}+marker)
        lateinit var scroll:ScrollState
        var codeColor=androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {Arachn0deTheme {
            codeColor=androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
            Column {scroll=rememberScrollState();DescriptionSelectionPrototype(state,scroll)}
        }}
        val field=compose.onNodeWithTag("prototype-field")
        fun pixel(x:Int,y:Int):androidx.compose.ui.graphics.Color {
            val bounds=field.fetchSemanticsNode().boundsInRoot
            val view=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnUiThread {view.draw(android.graphics.Canvas(bitmap))}
            return try {
                androidx.compose.ui.graphics.Color(bitmap.getPixel(bounds.left.toInt()+with(compose.density) {x.dp.roundToPx()},bounds.top.toInt()+with(compose.density) {y.dp.roundToPx()}))
            } finally {bitmap.recycle()}
        }
        assertNotEquals(codeColor,pixel(20,12))
        compose.runOnIdle {runBlocking {scroll.scrollTo(240)}}
        assertEquals(codeColor,pixel(20,12))
        assertNotEquals("Background must not bleed into decorator padding",codeColor,pixel(4,12))
    }
    @Test fun projectionScrollAndRestore() {
        val source=prototypeDescription()
        val tester=StateRestorationTester(compose)
        lateinit var state:TextFieldState
        lateinit var scroll:ScrollState
        tester.setContent {Arachn0deTheme {
            state=rememberTextFieldState(source)
            scroll=rememberScrollState()
            Column {DescriptionSelectionPrototype(state,scroll)}
        }}
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("prototype-field").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val result=layouts.last()
        assertEquals(DescriptionParser.projection(source).text,result.layoutInput.text.text)
        assertTrue(result.layoutInput.text.spanStyles.isNotEmpty())
        assertTrue(result.layoutInput.text.paragraphStyles.isNotEmpty())
        compose.runOnIdle {
            assertTrue(scroll.maxValue>0)
            state.edit {selection=TextRange(0,length)}
            runBlocking {scroll.scrollTo(scroll.maxValue)}
        }
        compose.runOnIdle {assertEquals(source,state.text.toString());assertEquals(TextRange(0,source.length),state.selection)}
        val offset=scroll.value
        tester.emulateSavedInstanceStateRestore()
        compose.runOnIdle {assertEquals(source,state.text.toString());assertEquals(TextRange(0,source.length),state.selection);assertEquals(offset,scroll.value)}
        // Restore a cut document too: provenance must survive even when no token is visible.
        compose.onNodeWithText("Cortar").performClick()
        compose.runOnIdle {assertEquals("",state.text.toString())}
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Pegar").performClick()
        compose.runOnIdle {assertEquals(source,state.text.toString())}
    }
    @Test fun clipboardKeepsRawTokensAndRejectsPartialEdits() {
        val token=AttachmentReferences.token("00000000-0000-0000-0000-000000000001","Fotografía")
        val source="**Título**\n$token\nFinal"
        val state=TextFieldState(source,TextRange(0,source.length))
        compose.setContent {Arachn0deTheme {Column {DescriptionSelectionPrototype(state,rememberScrollState())}}}
        compose.onNodeWithTag("prototype-field").performSemanticsAction(SemanticsActions.CopyText) {it()}
        val clipboard=compose.activity.getSystemService(android.content.ClipboardManager::class.java)
        assertEquals(source,clipboard.primaryClip!!.getItemAt(0).text.toString())
        compose.onNodeWithText("Cortar").performClick()
        compose.runOnIdle {assertEquals("",state.text.toString())}
        compose.onNodeWithTag("prototype-field").performSemanticsAction(SemanticsActions.PasteText) {it()}
        compose.runOnIdle {assertEquals(source,state.text.toString())}
        compose.runOnIdle {
            val at=source.indexOf(token)
            state.edit {selection=TextRange(at+2,at+5)}
            assertEquals("Copy must not emit a partial attachment token",token,prototypeCopy(state))
            assertFalse(prototypeReplace(state,""))
            assertEquals(source,state.text.toString())
        }
    }
    @Test fun imeAndRecompositionKeepOriginal() {
        val state=TextFieldState("Texto",TextRange(5))
        var extra by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {Column {
            DescriptionSelectionPrototype(state,rememberScrollState())
            if(extra)TextStub()
        }}}
        compose.onNodeWithTag("prototype-field").performClick().performTextInput(" añadido")
        compose.runOnIdle {extra=true}
        compose.runOnIdle {assertEquals("Texto añadido",state.text.toString());assertEquals(TextRange(13),state.selection)}
    }
    @Test fun handleGestureProbe() {
        val source=(1..100).joinToString("\n") {"Paragraph $it several selectable words"}
        val state=TextFieldState(source)
        lateinit var scroll:ScrollState
        compose.setContent {Arachn0deTheme {Column {
            scroll=rememberScrollState()
            DescriptionSelectionPrototype(state,scroll)
        }}}
        val field=compose.onNodeWithTag("prototype-field")
        field.performClick()
        field.performTouchInput {down(androidx.compose.ui.geometry.Offset(40f,30f))}
        compose.mainClock.advanceTimeBy(700)
        field.performTouchInput {up()}
        val handle=SemanticsMatcher("selection end handle") {node->
            node.config.any {it.key.name=="SelectionHandleInfo" && it.value.toString().contains("SelectionEnd")}
        }
        org.junit.Assume.assumeTrue("Robolectric did not expose a selection handle; use the debug Activity",compose.onAllNodes(handle,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())
        val old=state.selection
        compose.onNode(handle,useUnmergedTree=true).performTouchInput {
            swipe(center,center+androidx.compose.ui.geometry.Offset(0f,650f),durationMillis=1500)
        }
        org.junit.Assume.assumeTrue("Robolectric popup drag did not change selection; physical validation required",state.selection!=old)
        compose.runOnIdle {
            assertTrue("Handle must scroll beyond initial viewport",scroll.value>0)
            assertEquals(source,state.text.toString())
        }
        val stopped=scroll.value
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle {assertEquals("Scrolling must stop on release",stopped,scroll.value)}
    }
    @Test fun composingInputSurvivesRecomposition() {
        val state=TextFieldState("Inicio ",TextRange(7))
        var extra by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {Column {
            DescriptionSelectionPrototype(state,rememberScrollState())
            if(extra)TextStub()
        }}}
        compose.onNodeWithTag("prototype-field").performClick()
        fun editor(view:android.view.View):android.view.View? {
            if(view.onCheckIsTextEditor())return view
            if(view is android.view.ViewGroup) for(i in 0 until view.childCount)editor(view.getChildAt(i))?.let {return it}
            return null
        }
        var connection:android.view.inputmethod.InputConnection?=null
        compose.runOnIdle {connection=editor(compose.activity.window.decorView)?.onCreateInputConnection(android.view.inputmethod.EditorInfo())}
        org.junit.Assume.assumeNotNull(connection)
        compose.runOnIdle {connection!!.setComposingText("ñ",1)}
        compose.runOnIdle {assertNotNull(state.composition);extra=true}
        compose.runOnIdle {assertEquals("Inicio ñ",state.text.toString());assertNotNull(state.composition)}
        compose.runOnIdle {connection!!.finishComposingText()}
        compose.runOnIdle {assertNull(state.composition);assertEquals("Inicio ñ",state.text.toString())}
    }
}
@Composable private fun TextStub() {androidx.compose.material3.Text("Recomposición")}
