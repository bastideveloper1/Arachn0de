package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
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
@Config(sdk=[28],application=Application::class,qualifiers="w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BashGreenPresentationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun paletteAndOneAdaptiveAsciiSeparatorKeepOriginalActionsAndTraditionalMode() {
        val original=AppearanceTheme.Dark.palette
        val bashPalette=BashColors.palette(original)
        listOf(SemanticColors.Error,SemanticColors.HighPriority,SemanticColors.Upcoming,SemanticColors.Attention).forEach {
            assertEquals(original.semantic(it),bashPalette.semantic(it))
        }
        var width by mutableStateOf(320.dp)
        var bash by mutableStateOf(true)
        var selected=0
        compose.setContent {Arachn0deTheme {
            BashPresentationScope(bash) {
                Column(Modifier.width(width)) {
                    ProjectCard(Project("core","Directorio ñ","",0L,0L),ProjectCardAlerts(),{selected++},{},{})
                    NodeCard(Node("note","core",null,"Archivo","",false,0,0,0,false,purpose=NodePurpose.NOTE),
                        null,false,false,{selected++},{},{},false,false,{_,done->done()},{})
                }
            }
        }}
        fun layout(text:SemanticsNodeInteraction):TextLayoutResult {
            val result=mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(result)}
            return result.single()
        }
        compose.onAllNodesWithTag("bash-entry-separator",useUnmergedTree=true).assertCountEquals(2)
        val first=layout(compose.onAllNodesWithTag("bash-entry-separator",useUnmergedTree=true)[0])
        assertTrue(first.layoutInput.text.text.all {it=='-'})
        assertEquals(Color(0xFF486A50),first.layoutInput.style.color)
        assertEquals(Color(0xFF39FF14),bashListing("Dir/",true).spanStyles.single().item.color)
        assertEquals(Color(0xFF7CFF7A),bashListing("File.note",false).spanStyles.single().item.color)
        val actions=compose.onNodeWithContentDescription("Opciones del proyecto").fetchSemanticsNode().boundsInRoot
        val divider=compose.onAllNodesWithTag("bash-entry-separator",useUnmergedTree=true)[0].fetchSemanticsNode().boundsInRoot
        assertTrue(divider.top>=actions.bottom)
        compose.runOnIdle {width=380.dp}
        val wider=layout(compose.onAllNodesWithTag("bash-entry-separator",useUnmergedTree=true)[0])
        assertTrue(wider.layoutInput.text.length>first.layoutInput.text.length)
        compose.onNodeWithTag("project-card:core").performClick()
        compose.runOnIdle {assertEquals(1,selected);bash=false}
        compose.onAllNodesWithTag("bash-entry-separator").assertCountEquals(0)
        compose.onNodeWithTag("project-title:core",useUnmergedTree=true).assertTextEquals("Directorio ñ")
    }

    @Test fun sharedDrawerUsesBashColorsOutsideGeneralWithoutChangingBaseTheme() {
        val preferences=AppearancePreferences(compose.activity.getSharedPreferences("bash-green-menu",0))
        preferences.setTheme(AppearanceTheme.GreenBash)
        compose.setContent {Arachn0deTheme(preferences) {
            AppIdentityDrawer(onDismiss={},onProjects={},projectsSelected=true,onPeople={},onCalendar={})
        }}
        fun color(label:String):Color {
            val output=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label,useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(output)}
            return output.single().layoutInput.style.color
        }
        assertEquals(BashColors.Electric,color("Núcleos"))
        assertEquals(BashColors.Terminal,color("Antisocial"))
        compose.runOnIdle {assertEquals(AppearanceTheme.GreenBash,preferences.settings.theme);preferences.setTheme(AppearanceTheme.DayGrey)}
        assertNotEquals(BashColors.Electric,color("Núcleos"))
    }
    @Test fun neutralTintKeepsInactiveStateAndConfiguredFunctionalColors() {
        val preferences=AppearancePreferences(compose.activity.getSharedPreferences("bash-green-indicators",0))
        preferences.setIndicatorColor("high",IndicatorColor.RED)
        preferences.setIndicatorColor("people",IndicatorColor.BLUE)
        var bash by mutableStateOf(true)
        var inactive=Color.Unspecified
        var neutral=Color.Unspecified
        var alert=Color.Unspecified
        var configured=Color.Unspecified
        compose.setContent {Arachn0deTheme(preferences) {
            BashPresentationScope(bash) {
                val i=cardIndicatorColor("notes",false)
                val n=cardIndicatorColor("notes",true)
                val a=cardIndicatorColor("high",true)
                val c=cardIndicatorColor("people",true)
                SideEffect {inactive=i;neutral=n;alert=a;configured=c}
            }
        }}
        compose.runOnIdle {
            assertEquals(BashColors.Muted,inactive);assertEquals(BashColors.Soft,neutral)
            assertEquals(IndicatorColor.RED.color,alert);assertEquals(IndicatorColor.BLUE.color,configured)
            bash=false
        }
        compose.runOnIdle {assertEquals(BlackWhite90.Inactive,inactive);assertEquals(IndicatorColor.RED.color,alert)}
    }

}
