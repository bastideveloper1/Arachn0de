package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.r0ybt.arachn0de.domain.model.DescriptionParser
import com.r0ybt.arachn0de.ui.state.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=android.app.Application::class,sdk=[33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentFormattingUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun viewerDisplaysStoredHighlightWithoutAnEmbeddedButton() {
        val marker=DescriptionParser.Format.Highlight.marker
        compose.setContent {Arachn0deTheme {AttachmentText("${marker}árbol$marker niño",selectableDescription=true)}}
        compose.onNodeWithText("árbol niño").assertExists()
        compose.onNodeWithContentDescription("Destacador").assertDoesNotExist()
    }
    @Test fun legacyHighlightIsInvisibleAndCodeKeepsTextWithCompactMonospace() {
        val h=DescriptionParser.Format.Highlight.marker
        val c=DescriptionParser.Format.Code.marker
        val raw="${h}palabra$h\n${c}  if (x) {\n\treturn x;\n  }$c"
        val result=AttachmentVisualTransformation(emptyMap()).filter(androidx.compose.ui.text.AnnotatedString(raw)).text
        assertEquals("palabra\n  if (x) {\n\treturn x;\n  }",result.text)
        assertTrue(result.spanStyles.none {it.item.background==androidx.compose.ui.graphics.Color(0xffffeb3b)})
        val style=descriptionSpanStyle(setOf(DescriptionParser.Format.Code))
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace,style.fontFamily)
        assertEquals(0.85f,style.fontSize.value)
        assertEquals(1,result.getStringAnnotations("description-code",0,result.length).size)
        assertFalse(EditorDraft(null,null,"", "palabra").toggleDescriptionFormat(DescriptionParser.Format.Highlight))
    }

}
