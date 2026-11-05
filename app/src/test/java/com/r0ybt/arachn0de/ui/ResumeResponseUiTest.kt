package com.r0ybt.arachn0de.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.security.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(application=LegacyUiTestApplication::class,sdk=[28])
class ResumeResponseUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {stopAppearanceTestApplication(app)}
    @Test fun authorizedResumeKeepsThemeAndScreenBehindOpaquePrivacyCover() {
        compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Abrir menú").fetchSemanticsNodes().isNotEmpty()}
        val session=app.security.current.value!!
        val creates=ShadowLog.getLogsForTag("Arachn0deResponse").count {it.msg.startsWith("theme_preferences_create")}
        assertTrue(creates>0)
        repeat(2) {
            compose.runOnIdle {app.security.background()}
            compose.waitForIdle()
            assertTrue(app.security.backgrounded.value)
            compose.onNodeWithTag("privacy-cover").assertExists()
            compose.onNodeWithContentDescription("Abrir menú").assertDoesNotExist()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Abrir menú").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("privacy-cover").assertDoesNotExist()
            assertSame(session,app.security.current.value)
        }
        assertEquals(creates,ShadowLog.getLogsForTag("Arachn0deResponse").count {it.msg.startsWith("theme_preferences_create")})
    }
}
