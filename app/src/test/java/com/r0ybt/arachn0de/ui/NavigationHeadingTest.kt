package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationHeadingTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val project=Project("p","Proyecto de Trabajo","",0,0,0)
    private fun node(purpose:NodePurpose)=Node("n","p",null,"Amigos","",false,0,0,0,false,purpose=purpose)

    @Test fun lateralHeadingUsesEqualTypographyAlignedTextAndExistingBackControl() {
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalReturnHeading provides HeadingLocation("Amigos",HeadingKind.TASK,project.name)) {
                Box(Modifier.width(320.dp)) {HeaderBar({},title="Antisocial",onBack={})}
            }
        }}
        val title=compose.onNodeWithTag("app-bar-title")
        val context=compose.onNodeWithTag("navigation-context-text")
        fun layout(node:SemanticsNodeInteraction):androidx.compose.ui.text.TextLayoutResult {
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
            return layouts.single()
        }
        val current=layout(title).layoutInput.style
        val previous=layout(context).layoutInput.style
        assertEquals(16.sp,current.fontSize);assertEquals(current.fontSize,previous.fontSize)
        assertEquals(FontWeight.SemiBold,current.fontWeight);assertEquals(FontWeight.Normal,previous.fontWeight)
        val a=title.fetchSemanticsNode().boundsInRoot
        val b=context.fetchSemanticsNode().boundsInRoot
        assertEquals(a.left,b.left,1f)
        assertTrue(b.top-a.bottom<=3*compose.density.density)
        val menu=compose.onNodeWithContentDescription("Abrir menú").fetchSemanticsNode().boundsInRoot
        assertTrue(b.bottom<=menu.bottom+1)
        compose.onNodeWithContentDescription("Volver").assertIsDisplayed().assertHasClickAction()
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test fun resolverUsesCurrentNamesTypesAndRejectsDeletedOrForeignLocations() {
        for((purpose,kind) in listOf(NodePurpose.ACTION to HeadingKind.TASK,NodePurpose.NOTE to HeadingKind.NOTE,NodePurpose.LAYER to HeadingKind.LAYER)) {
            val n=node(purpose)
            assertEquals(HeadingLocation("Amigos",kind,project.name),headingLocation(project,n.id,mapOf(n.id to n)))
            assertEquals("Renombrada",headingLocation(project,n.id,mapOf(n.id to n.copy(title="Renombrada")))!!.name)
            assertNull(headingLocation(project,n.id,emptyMap()))
            assertNull(headingLocation(project,n.id,mapOf(n.id to n.copy(projectId="other"))))
        }
        assertNull(headingLocation(null,null,emptyMap()))
        assertEquals(HeadingLocation(project.name,HeadingKind.CORE),headingLocation(project,null,emptyMap()))
    }

    @Test fun contextUpdatesAndClosesWithoutAddingButtonsOrIncreasingNormalHeaderHeight() {
        var side by mutableStateOf(false)
        var source by mutableStateOf<HeadingLocation?>(HeadingLocation("Nota Amigos",HeadingKind.NOTE,project.name))
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalReturnHeading provides if(side) source else null) {
                Box(Modifier.width(320.dp)) {
                    HeaderBar({},title=if(side) "Antisocial" else "Nota Amigos",onBack={side=false},
                        context=if(side) source else HeadingLocation(project.name,HeadingKind.CORE))
                }
            }
        }}
        compose.onNodeWithTag("app-bar-title").assertTextEquals("Nota Amigos")
        compose.onNodeWithContentDescription("Regresar a").assertDoesNotExist()
        val normal=compose.onNodeWithContentDescription("Abrir menú").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {side=true}
        compose.onNodeWithTag("app-bar-title").assertTextEquals("Antisocial")
        compose.onNodeWithText("Nota Amigos  ›  ◈ ${project.name}").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        val context=compose.onNodeWithTag("navigation-context").fetchSemanticsNode().boundsInRoot
        assertTrue(context.bottom<=normal.bottom+1)
        compose.runOnIdle {source=source!!.copy(name="Nota renombrada")}
        compose.onNodeWithText("Nota renombrada  ›  ◈ ${project.name}").assertIsDisplayed()
        compose.runOnIdle {source=null}
        compose.onNodeWithTag("navigation-context").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("app-bar-title").assertTextEquals("Nota Amigos")
        compose.onNodeWithContentDescription("Regresar a").assertDoesNotExist()
    }
}
