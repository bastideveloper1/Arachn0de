package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BashPresentationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun node(id:String,title:String,parent:String?=null,purpose:NodePurpose=NodePurpose.LAYER)=
        Node(id,"core",parent,title,"Descripción completa",false,0,0,0,false,purpose=purpose)

    @Test fun pathsUseActualAncestorsAndPreserveUnicodeSpacesSlashesAndFileNames() {
        val a=node("a","Curso Google/seguridad")
        val b=node("b","Permisos ñ",a.id)
        val task=node("task","Practicar chmod '特殊'",b.id,NodePurpose.ACTION)
        val nodes=listOf(a,b,task).associateBy {it.id}
        assertEquals("~/General/Núcleo/Curso Google/seguridad/Permisos ñ",bashLocation("Núcleo",task,nodes))
        assertEquals("~/General/Núcleo/Curso Google/seguridad/Permisos ñ",bashLocation("Núcleo",b,nodes))
        assertEquals("~/General/Núcleo",bashLocation("Núcleo",null,nodes))
        assertEquals("Practicar chmod '特殊'.task",bashFileName(task.title,task.purpose))
        assertEquals("Apuntes.note",bashFileName("Apuntes",NodePurpose.NOTE))
        assertEquals("Capa/",bashFileName("Capa",NodePurpose.LAYER))
        assertEquals("drwx------ Capa/",bashListing("Capa/",true).text)
        assertTrue(bashLocation("Núcleo",task.copy(parentId=task.id),mapOf(task.id to task.copy(parentId=task.id))).length<200)
    }

    @Test fun originalCardsAndTerminalOutputShareTouchNavigationTagsAndIndicators() {
        var enabled by mutableStateOf(true)
        var opened by mutableStateOf(false)
        var mode by mutableStateOf(0)
        val longTitle="Título Unicode ñ 特殊 con espacios y una extensión suficientemente larga para varias líneas"
        compose.setContent {Arachn0deTheme {
            BashPresentationScope(enabled) {
                Column(Modifier.width(320.dp)) {
                    BashPrompt("~/General/Trabajo","ls -l")
                    if(opened) {
                        DetailScopeTitle(longTitle) {opened=false}
                        BashNodeProperties(node("child",longTitle,purpose=NodePurpose.ACTION))
                        AttachmentText("Descripción íntegra con https://example.com/ruta",webLinks=true)
                    } else if(mode==0) ProjectCard(Project("core",longTitle,"",0L,0L),ProjectCardAlerts(),{opened=true},{},{},
                        tags=listOf(Tag("tag","Etiqueta","etiqueta")))
                    else NodeCard(node("child",longTitle,purpose=if(mode==1) NodePurpose.NOTE else NodePurpose.ACTION),
                        null,false,mode==2,{opened=true},{},{},false,false,{_,done->done()},{},
                        modifier=Modifier.testTag("entry"),tags=listOf(Tag("tag","Etiqueta","etiqueta")))
                    BashTraditionalModules {Text("Barra persistente",Modifier.testTag("bash-excluded"))}
                }
            }
        }}
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("project-title:core",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals(FontFamily.Monospace,layouts.single().layoutInput.style.fontFamily)
        assertTrue(layouts.single().lineCount>1)
        val original=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("bash-excluded",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(original)}
        assertNotEquals(FontFamily.Monospace,original.single().layoutInput.style.fontFamily)
        compose.onNodeWithText("Etiqueta").assertExists()
        compose.onNodeWithContentDescription("Opciones del proyecto").assertExists()
        compose.onNodeWithTag("project-card:core").performClick()
        compose.onNodeWithText("title=$longTitle").assertExists()
        compose.onNodeWithText("status=pending").assertExists()
        compose.onNodeWithText("Descripción íntegra con https://example.com/ruta").assertExists()
        compose.onNodeWithText("title=$longTitle").performClick()
        for(type in 1..2) {
            compose.runOnIdle {mode=type}
            compose.onNodeWithTag("node-title:child",useUnmergedTree=true).assertTextContains(if(type==1) ".note" else ".task",substring=true)
            compose.onNodeWithTag("node-options:child").assertExists()
            compose.onNodeWithTag("entry").performClick()
            compose.onNodeWithText("id=child").assertExists()
            compose.onNodeWithText("title=$longTitle").performClick()
        }
        compose.runOnIdle {enabled=false;mode=0}
        compose.onNodeWithTag("bash-prompt").assertDoesNotExist()
        compose.onNodeWithTag("project-title:core",useUnmergedTree=true).assertTextEquals(longTitle)
    }
    @Test fun lightBaseKeepsReadableTerminalFieldsFullDateTimeAndOriginalPersistentStyle() {
        lateinit var terminal:ColorScheme
        lateinit var restored:AppearancePalette
        val stamp=1_800_000_000_000L
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalAppearancePalette provides AppearanceTheme.DayGrey.palette) {
                BashPresentationScope(true) {
                    val colors=MaterialTheme.colorScheme;SideEffect {terminal=colors}
                    Column {
                        TextField("Texto",{},modifier=Modifier.fillMaxWidth())
                        BashNodeProperties(node("dated","Título",purpose=NodePurpose.ACTION).copy(startAt=stamp,dueAt=stamp+60_000))
                        BashTraditionalModules {val palette=LocalAppearancePalette.current;SideEffect {restored=palette}}
                    }
                }
            }
        }}
        compose.onNodeWithText("start="+formatTaskDate(stamp)).assertExists()
        compose.onNodeWithText("due="+formatTaskDate(stamp+60_000)).assertExists()
        compose.runOnIdle {
            fun contrast(a:androidx.compose.ui.graphics.Color,b:androidx.compose.ui.graphics.Color):Float {
                val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
            }
            assertTrue(contrast(terminal.onSurface,terminal.surface)>=4.5f)
            assertTrue(contrast(terminal.onSurfaceVariant,terminal.surfaceVariant)>=4.5f)
            assertTrue(contrast(terminal.primary,terminal.surfaceVariant)>=4.5f)
            assertEquals(AppearanceTheme.DayGrey.palette,restored)
        }
    }

}
