package com.r0ybt.arachn0de.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=NavigationWorkApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class MetroObserverLifecycleTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=RuntimeEnvironment.getApplication() as NavigationWorkApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(Looper.getMainLooper()).idle();Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    private val journeysQuery="SELECT * FROM metro_journeys ORDER BY id"
    @After fun close() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        val activity=compose.activity
        compose.runOnUiThread {
            val root=activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        stopAppearanceTestApplication(app)
    }
    @Test fun rootStopsMetroSnapshotsInBackgroundAndReloadsOnReturn() {
        val project=runBlocking {app.projectRepository.createProject("Metro")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository,initialProjects=listOf(project))}}
        await {app.queries.contains(journeysQuery)}
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        // Let cancelled Room transactions drain before measuring the background invalidation.
        Thread.sleep(200);app.queries.clear()
        runBlocking {app.metroRepository.settings {it.copy(vibration=!it.vibration)}}
        Thread.sleep(250);shadowOf(Looper.getMainLooper()).idle()
        assertFalse(app.queries.toString(),app.queries.contains(journeysQuery))
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        await {app.queries.contains(journeysQuery)}
        compose.onNodeWithTag("global-bottom-bar").assertIsDisplayed()
    }
}
