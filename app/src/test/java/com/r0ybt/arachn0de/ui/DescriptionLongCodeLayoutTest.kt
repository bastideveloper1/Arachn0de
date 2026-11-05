package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.DescriptionParser
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28],qualifiers="w400dp-h800dp")
class DescriptionLongCodeLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun longSelectionStopsAtOuterViewport() {
        val source=(1..100).joinToString("\n") {"Paragraph $it with several selectable words"}
        var value by mutableStateOf(TextFieldValue(source))
        lateinit var scroll:androidx.compose.foundation.ScrollState
        compose.setContent {Arachn0deTheme {
            scroll=rememberScrollState()
            Column(Modifier.height(320.dp).fillMaxWidth().verticalScroll(scroll).testTag("selection-viewport")) {
                AttachmentDescriptionField(value,{value=it},true,
                    transformation=AttachmentVisualTransformation(emptyMap()),onReference={})
            }
        }}
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNodeWithTag("description-inner",useUnmergedTree=true).performTouchInput {
            down(androidx.compose.ui.geometry.Offset(30f,10f))
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("selection-viewport").performTouchInput {
            moveTo(androidx.compose.ui.geometry.Offset(100f,height-8f),delayMillis=600)
            repeat(20) {
                advanceEventTime(100)
                moveTo(androidx.compose.ui.geometry.Offset(100f+(it%2),height-8f))
            }
            up()
        }
        compose.runOnIdle {
            assertFalse("Gesture must actually select text",value.selection.collapsed)
            assertTrue(scroll.maxValue>0)
            assertEquals("Selection drag does not scroll the enclosing viewport",0,scroll.value)
            assertTrue(value.selection.max<source.length/2)
            assertEquals(source,value.text)
        }
        val selected=value.selection
        compose.runOnIdle {runBlocking {scroll.scrollTo(scroll.maxValue)}}
        compose.runOnIdle {assertEquals(selected,value.selection);assertEquals(source,value.text)}
    }
    @Test fun multilineCodeSharesOuterScrollAndKeepsControlsOutsideTextViewport() {
        val marker=DescriptionParser.Format.Code.marker
        val source="Antes\n"+marker+(1..60).joinToString("\n") {"  línea $it <a>"}+marker+"\nDespués"
        lateinit var scroll:androidx.compose.foundation.ScrollState
        compose.setContent {Arachn0deTheme {
            scroll=rememberScrollState()
            Column(Modifier.height(480.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).testTag("editor-viewport")) {
                    AttachmentDescriptionField(TextFieldValue(source),{},true,maxLines=3,
                        transformation=AttachmentVisualTransformation(emptyMap()),onReference={})
                }
                Text("Controles",Modifier.fillMaxWidth().height(48.dp).testTag("editor-controls"))
            }
        }}
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNode(hasSetTextAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.single().lineCount>60)
        compose.runOnIdle {runBlocking {scroll.scrollTo(scroll.maxValue)}}
        val viewport=compose.onNodeWithTag("editor-viewport").fetchSemanticsNode().boundsInRoot
        val controls=compose.onNodeWithTag("editor-controls").fetchSemanticsNode().boundsInRoot
        assertTrue(viewport.bottom<=controls.top+1)
        compose.runOnIdle {assertEquals(scroll.maxValue,scroll.value);assertTrue(scroll.maxValue>0)}
        assertEquals("Antes\n"+(1..60).joinToString("\n") {"  línea $it <a>"}+"\nDespués",DescriptionParser.projection(source).text)
    }
}
