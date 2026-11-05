package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.LocalFireDepartment
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconographyBlockFourTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val task=Node("task","p",null,"Tarea prioritaria","",false,0,0,0,false,priority=Priority.HIGH)
    private fun pixels(tag:String):List<Color> {
        val bounds=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val view=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread {view.draw(android.graphics.Canvas(bitmap))}
        val pixels=bitmap.asImageBitmap().toPixelMap()
        val colors=buildList {for(x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(pixels.width))
            for(y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(pixels.height)) add(pixels[x,y])}
        bitmap.recycle();return colors
    }
    @Test fun identityAndPriorityPixelsAreMonochromeByDefaultAcrossOfficialThemes() {
        var palette by mutableStateOf(AppearanceTheme.Arachn0de.applicationPalette)
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalAppearancePalette provides palette) {
            Row {
                Box(Modifier.testTag("title-icon")) {PropertyIdentityIcon()}
                Box(Modifier.testTag("description-icon")) {PropertyIdentityIcon(description=true)}
                Box(Modifier.testTag("core-icon")) {OfficialIcon(R.drawable.bw90_project,size=32.dp)}
                Box(Modifier.testTag("layer-icon")) {OfficialIcon(R.drawable.bw90_layer,size=32.dp)}
                Box(Modifier.testTag("priority-icon")) {PriorityIndicator(task)}
                Box(Modifier.testTag("verify-setting-icon")) {IndicatorSettingIcon("verify",androidx.compose.material.icons.Icons.Default.Visibility,Arachn0deColors.Icon)}
                Box(Modifier.testTag("birthday-setting-icon")) {IndicatorSettingIcon("birthday",androidx.compose.material.icons.Icons.Default.Cake,Arachn0deColors.Icon)}
                Box(Modifier.testTag("effort-setting-icon")) {IndicatorSettingIcon("effort",androidx.compose.material.icons.Icons.Default.LocalFireDepartment,Arachn0deColors.Icon)}
            }
        }}}
        val tags=listOf("title-icon","description-icon","core-icon","layer-icon","priority-icon","verify-setting-icon","birthday-setting-icon","effort-setting-icon")
        for(tag in tags) {
            val opaque=pixels(tag).filter {it.alpha>.8f}
            assertTrue("Visible $tag",opaque.isNotEmpty())
            assertTrue("No legacy orange in $tag",opaque.all {kotlin.math.abs(it.red-it.green)<.02f && kotlin.math.abs(it.green-it.blue)<.02f})
        }
        compose.onAllNodesWithText("Prioritario").assertCountEquals(0)
    }
    @Test fun allDifficultyLevelsLiveInsideMetadataWithoutAProgressBarAtLargeFont() {
        var level by mutableIntStateOf(1)
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                Box(Modifier.width(320.dp)) {NodeCard(task.copy(effort=level),null,false,true,{},{},{},false,false,{_,done->done()},{})}
            }
        }}
        var neighbourLeft:Float?=null
        for(value in 1..5) {
            compose.runOnIdle {level=value}
            val effort=compose.onNodeWithTag("task-effort:task",useUnmergedTree=true)
            effort.assertContentDescriptionEquals("Esfuerzo: ${effortLabel(value)}").assertHasNoClickAction()
            compose.onAllNodes(hasText(value.toString()) and hasAnyAncestor(hasTestTag("task-effort:task")),useUnmergedTree=true).assertCountEquals(1)
            val mini=effort.fetchSemanticsNode().boundsInRoot
            val strip=compose.onNodeWithTag("card-indicators:node:task",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue(mini.top>=strip.top && mini.bottom<=strip.bottom)
            val badge=compose.onNodeWithTag("task-effort-badge:task",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue(badge.left>=mini.left && badge.right<=mini.left+16*compose.density.density+1)
            assertTrue(badge.top>=mini.top && badge.bottom<=mini.bottom+1)
            assertEquals(16*compose.density.density,mini.height,1f)
            val neighbour=compose.onNodeWithTag("card-signal:node:task:high",useUnmergedTree=true).assertExists().fetchSemanticsNode().boundsInRoot
            neighbourLeft?.let {assertEquals(it,neighbour.left,1f)} ?: run {neighbourLeft=neighbour.left}
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            compose.onAllNodesWithText("Prioritario").assertCountEquals(0)
        }
    }
}
