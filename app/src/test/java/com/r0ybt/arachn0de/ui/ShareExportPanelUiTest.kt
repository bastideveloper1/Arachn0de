package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w320dp-h850dp")
class ShareExportPanelUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun contextualActionsAndBothCopyScopesStayAvailable() {
        var kind by mutableIntStateOf(0)
        val copied=mutableListOf<Boolean>()
        compose.setContent {Arachn0deTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.5f)) {
                key(kind) {ShareExportPanel("Elemento largo para fuentes ampliadas","project",if(kind==0)null else "node",kind<2,true,true,{copied+=it},{})}
            }
        }}
        fun exists(tag:String)=compose.onNodeWithTag(tag).assertExists()
        exists("share-export-pdf");exists("share-export-docx");exists("share-export-copy-tree")
        compose.onNodeWithTag("share-export-image").assertDoesNotExist()
        compose.onNodeWithTag("share-export-copy").performScrollTo().performClick()
        compose.onNodeWithTag("share-export-copy-tree").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(listOf(false,true),copied);kind=1}
        exists("share-export-image");exists("share-export-save-image");exists("share-export-pdf")
        compose.runOnIdle {kind=2}
        compose.onNodeWithTag("share-export-pdf").assertDoesNotExist()
        compose.onNodeWithTag("share-export-docx").assertDoesNotExist()
        compose.onNodeWithTag("share-export-copy-tree").assertDoesNotExist()
        exists("share-export-image");exists("share-export-markdown");exists("share-export-json")
    }
    @Test fun pngTreeActionUsesExistingMenuAndIsAbsentForEmptyContainersAndLeaves() {
        var kind by mutableIntStateOf(0);var saved=0
        compose.setContent {Arachn0deTheme {
            CardImageMenu(if(kind==0)null else CardImageCommands(false,{},{}),
                CardTreeImageCommands(kind<2,false,null,{saved++},{}),enabled=true)
        }}
        compose.onNodeWithTag("share-export-save-image-tree").assertExists().performClick()
        compose.onNodeWithTag("share-export-save-image").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,saved);kind=1}
        compose.onNodeWithTag("share-export-save-image").assertExists()
        compose.onNodeWithTag("share-export-save-image-tree").assertExists()
        compose.runOnIdle {kind=2}
        compose.onNodeWithTag("share-export-save-image").assertExists()
        compose.onNodeWithTag("share-export-save-image-tree").assertDoesNotExist()
        compose.runOnIdle {kind=3}
        compose.onNodeWithTag("share-export-save-image-tree").assertDoesNotExist()
    }

}
