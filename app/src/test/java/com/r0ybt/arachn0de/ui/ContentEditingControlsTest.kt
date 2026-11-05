package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33],qualifiers="w320dp-h900dp")
class ContentEditingControlsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun contentControlsKeepTheirCallbacksAndStateAcrossContextSwitches() {
        var context by mutableStateOf<CoreBottomContext?>(CoreBottomContext.Content)
        var favorite by mutableStateOf(false)
        var priority by mutableStateOf(false)
        val events=mutableListOf<String>()
        compose.setContent {Arachn0deTheme {Box(Modifier.fillMaxWidth().height(800.dp)) {
            val properties=buildList {
                listOf("Título","Descripción","Editar","Esfuerzo","Fotografía","Planificación","Responsables","Valores iniciales","Estados").forEach {label->
                    add(ContextAction(label,Icons.Default.Edit,{events+=label}))
                }
                add(ContextAction(if(favorite)"Quitar de Favoritos" else "Marcar favorito",if(favorite)Icons.Default.Star else Icons.Default.StarBorder,{favorite=!favorite}))
                add(ContextAction(if(priority)"Prioritario" else "Común",Icons.Default.Flag,{priority=!priority}))
                add(ContextAction("Sin recurrencia",Icons.Default.Refresh,{},enabled=false))
                add(ContextAction("Regla de recurrencia",Icons.Default.Refresh,{events+="recurrence"}))
            }
            CoreBottomModule(context,{context=it},600.dp,{}, {},contentActions=properties)
        }}}
        fun action(label:String)=compose.onNodeWithTag("context-action:$label").performScrollTo()
        action("Título").assertIsEnabled().performClick()
        assertEquals(listOf("Título"),events)
        action("Marcar favorito").performClick()
        action("Quitar de Favoritos").assertIsEnabled()
        action("Común").performClick()
        action("Prioritario").assertIsEnabled()
        action("Sin recurrencia").assertIsNotEnabled()
        action("Regla de recurrencia").performClick()
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("core-context:Content").performClick()
        action("Quitar de Favoritos").performClick()
        action("Prioritario").performClick()
        assertFalse(favorite);assertFalse(priority)
        assertEquals(listOf("Título","recurrence"),events)
        val bounds=action("Común").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height>=48*compose.density.density-1)
    }
}
