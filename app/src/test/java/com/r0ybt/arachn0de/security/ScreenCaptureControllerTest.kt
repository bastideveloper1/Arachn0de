package com.r0ybt.arachn0de.security

import android.view.WindowManager
import androidx.activity.ComponentActivity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class ScreenCaptureControllerTest {
    @Test fun activeProcessPermissionSurvivesRecreationAndEndsExplicitly()=runBlocking<Unit> {
        val first=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val second=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val policy=ScreenCaptureController();policy.attach(first.get().window)
        assertTrue(policy.grant(policy.epoch) {true})
        policy.detach(first.get().window,closingSession=false);assertTrue(policy.allowed.value)
        policy.attach(second.get().window)
        assertEquals(0,second.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(10))
        assertTrue(policy.allowed.value)
        policy.revoke();assertFalse(policy.allowed.value)
        assertTrue(second.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
        assertTrue(policy.grant(policy.epoch) {true});policy.detach(second.get().window)
        assertFalse(policy.allowed.value);assertFalse(ScreenCaptureController().allowed.value)
        first.pause().stop().destroy();second.pause().stop().destroy()
    }
    @Test fun flagsAndPermissionAgreeAndRevokedAttemptsCannotGrant()=runBlocking<Unit> {
        val activity=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val window=activity.get().window
        val policy=ScreenCaptureController()
        fun secure()=window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0
        policy.attach(window);assertTrue(secure());assertFalse(policy.allowed.value)
        assertFalse(policy.grant(policy.epoch) {false});assertTrue(secure())
        assertTrue(policy.grant(policy.epoch) {true});assertFalse(secure());assertTrue(policy.allowed.value)
        val stale=policy.epoch;policy.revoke();assertTrue(secure());assertFalse(policy.allowed.value)
        assertFalse(policy.grant(stale) {true});assertTrue(secure())
        assertTrue(policy.grant(policy.epoch) {true})
        policy.detach(window);assertTrue(secure());assertFalse(policy.allowed.value)
        policy.attach(window);assertTrue(secure())
        assertFalse(ScreenCaptureController().allowed.value)
        policy.detach(window);activity.pause().stop().destroy()
    }
}
