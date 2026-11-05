package com.r0ybt.arachn0de.pomodoro

import android.app.Application
import android.app.NotificationManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PomodoroIdleTest {
    @Test fun idlePausedAndManualEndHaveNoPeriodicClockReads()=runBlocking<Unit> {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PomodoroController.CACHE,0).edit().clear().commit()
        val reads=AtomicInteger()
        val timer=PomodoroController(context) {reads.incrementAndGet();PomodoroTime(1_800_000_000_000,100_000,7)}
        val observer=launch(start=CoroutineStart.UNDISPATCHED) {timer.observeForeground()}
        suspend fun assertIdle() {delay(50);val before=reads.get();delay(350);assertEquals(before,reads.get())}
        try {
            assertIdle()
            timer.start();yield();timer.pause();assertIdle()
            timer.start();repeat(5) {timer.adjust(5)};assertTrue(timer.state.value.session!!.manualEnd);assertIdle()
            timer.finish();assertIdle()
        } finally {observer.cancelAndJoin();timer.sounds.stop()}
    }

    @Test fun foregroundWaitsForDeadlineAndCompletesOnceWithoutVisualTicks()=runBlocking<Unit> {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PomodoroController.CACHE,0).edit().clear().commit()
        var now=PomodoroTime(1_800_000_000_000,100_000,7)
        val timer=PomodoroController(context) {now}
        timer.configure(PomodoroConfig(1,1,1,2,false,false));timer.start()
        now=now.copy(wall=now.wall+59_980,elapsed=now.elapsed+59_980)
        val observer=launch(start=CoroutineStart.UNDISPATCHED) {timer.observeForeground()}
        try {
            now=now.copy(wall=now.wall+20,elapsed=now.elapsed+20)
            withTimeout(2000) {while(timer.state.value.session!!.status==PomodoroStatus.RUNNING) delay(5)}
            assertEquals(PomodoroPhase.SHORT_BREAK,timer.state.value.session!!.phase)
            assertEquals(PomodoroStatus.READY,timer.state.value.session!!.status)
            val finished=timer.state.value
            delay(150);assertEquals(finished,timer.state.value)
            assertNotNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(917))
            val recovered=PomodoroController(context) {now}
            assertEquals(finished,recovered.state.value);recovered.sounds.stop()
        } finally {observer.cancelAndJoin();timer.sounds.stop()}
    }
}
