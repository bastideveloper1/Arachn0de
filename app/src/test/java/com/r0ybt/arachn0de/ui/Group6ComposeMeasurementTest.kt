package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Group6ComposeMeasurementTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun unchangedProjectionInputDoesNotRepeatDuringMenuRecompositions() {
        var tick by mutableIntStateOf(0);var calculations=0;var compositions=0
        val calls=mutableListOf<Long>();val text=mutableListOf<Long>();val allocations=mutableListOf<Long>()
        val bean=java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val project=Project("p","P","",0,0,0)
        val input=TaskViewInput(NodeTreeSnapshot(emptyList()),project,null,NodeFilter(),null,emptyMap(),TagState(),null)
        compose.setContent {Arachn0deTheme {Column {
            val frame=tick
            val result by produceState<TaskViewCalculation?>(null,input) {
                calculations++;value=withContext(Dispatchers.Default) {input.calculate()}
            }
            Text("$frame:${result?.projection?.total}")
            val measurer=rememberTextMeasurer();val style=MaterialTheme.typography.labelSmall
            val before=bean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0
            val started=System.nanoTime()
            val options=List(6) {ModularPanelOption("Campo $it",Icons.Default.Edit,"probe:$it",onClick={tick=frame+1})}
            ModularFieldMenu(options)
            ContextActionMenu("Opciones",{},inline=true) {repeat(16) {action("Acción $it",Icons.Default.Edit,{tick=frame+1})}}
            val elapsed=System.nanoTime()-started
            val textStarted=System.nanoTime()
            options.forEach {measurer.measure(it.label,style,constraints=Constraints(maxWidth=140))}
            val textElapsed=System.nanoTime()-textStarted
            val allocated=(bean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0)-before
            SideEffect {compositions++;if(frame>0) {calls+=elapsed;text+=textElapsed;allocations+=allocated}}
        }}}
        compose.waitUntil(15000) {calculations==1}
        repeat(20) {compose.runOnIdle {tick++};compose.waitForIdle()}
        assertEquals(1,calculations);assertTrue(compositions>=21)
        println("GROUP6_COMPOSE compositions=$compositions calculations=$calculations call_median_ms=${calls.sorted()[calls.size/2]/1e6} six_cached_text_median_ms=${text.sorted()[text.size/2]/1e6} allocated_median_bytes=${allocations.sorted()[allocations.size/2]}")
    }
}
