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
class ElementActionControlsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun previewAndOpenedElementMenusKeepActionsAndPinState() {
        var pinned by mutableStateOf(false)
        var editing by mutableStateOf(false)
        val events=mutableListOf<String>()
        compose.setContent {Arachn0deTheme {Box(Modifier.fillMaxWidth().height(800.dp)) {
            ContextActionMenu("Elemento",{},inline=true,showCancel=false,cardEditingIcons=editing) {
                action(if(pinned)"Desfijar" else "Fijar",Icons.Default.PushPin,{pinned=!pinned})
                action("Guardar como plantilla",Icons.Default.Description,{events+="template"})
                action("Historial",Icons.Default.History,{events+="history"})
                action("Seleccionar",Icons.Default.CheckBox,{events+="select"})
                action("Mover a…",Icons.Default.DriveFileMove,{events+="move"})
                action("Limpiar completadas",Icons.Default.Delete,{events+="clean"})
                action("Eliminar",Icons.Default.Delete,{events+="delete"},destructive=true)
            }
        }}}
        fun action(label:String)=compose.onNodeWithTag("context-action:$label").performScrollTo()
        action("Fijar").performClick()
        assertTrue(pinned)
        action("Desfijar").assertIsEnabled()
        compose.runOnIdle {editing=true}
        action("Desfijar").performClick()
        assertFalse(pinned)
        listOf("Guardar como plantilla","Historial","Seleccionar","Mover a…","Limpiar completadas","Eliminar").forEach {
            val control=action(it).assertIsEnabled()
            assertTrue(control.fetchSemanticsNode().boundsInRoot.height>=48*compose.density.density-1)
            control.performClick()
        }
        assertEquals(listOf("template","history","select","move","clean","delete"),events)
    }
}
