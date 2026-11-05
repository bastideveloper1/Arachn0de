package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
class ConversionMenuIconsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun conversionSubmenuKeepsDestinationsGuardsAndBack() {
        val events=mutableListOf<String>()
        compose.setContent {Arachn0deTheme {Box(Modifier.fillMaxWidth().height(800.dp)) {
            ContextActionMenu("Elemento",{},inline=true,showCancel=false,groupConversions=true) {
                listOf("nota","tarea","capa","proyecto").forEach {type->
                    action("Convertir en $type",Icons.Default.SwapHoriz,{events+=type},enabled=type!="proyecto")
                }
                action("Mover a Borradores",Icons.Default.Description,{events+="draft"})
            }
        }}}
        compose.onNodeWithTag("context-action:Convertir en...").performClick()
        listOf("nota","tarea","capa").forEach {type->
            val node=compose.onNodeWithTag("context-action:Convertir en $type").assertIsEnabled()
            assertTrue(node.fetchSemanticsNode().boundsInRoot.height>=48*compose.density.density-1)
            node.performClick()
        }
        compose.onNodeWithTag("context-action:Convertir en proyecto").assertIsNotEnabled()
        compose.onNodeWithTag("context-action:Convertir en Borrador").performClick()
        assertEquals(listOf("nota","tarea","capa","draft"),events)
        compose.onNodeWithTag("context-action:← Atrás").performClick()
        compose.onNodeWithTag("context-action:Convertir en...").assertIsDisplayed()
        compose.onNodeWithTag("context-action:Convertir en nota").assertDoesNotExist()
    }
}
