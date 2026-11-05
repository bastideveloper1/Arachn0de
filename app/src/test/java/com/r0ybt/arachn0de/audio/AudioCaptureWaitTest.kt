package com.r0ybt.arachn0de.audio

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AudioCaptureWaitTest {
    @Test fun pausedWaitHasNoTimerAndResumeBeforeWaitIsNotLost()=runBlocking<Unit> {
        val control=AudioCaptureControl()
        control.pause(true);control.awaitChange()
        val waiter=async(start=CoroutineStart.UNDISPATCHED) {control.awaitChange()}
        delay(180);assertFalse(waiter.isCompleted)
        control.pause(false);withTimeout(1000) {waiter.await()};assertFalse(control.paused.get())
        control.pause(true);control.pause(false)
        withTimeout(1000) {control.awaitChange()};assertFalse(control.paused.get())
    }
    @Test fun stopWakesPausedCaptureAndCancellationDoesNotBlock()=runBlocking<Unit> {
        val control=AudioCaptureControl();control.pause(true);control.awaitChange()
        val waiter=async(start=CoroutineStart.UNDISPATCHED) {control.awaitChange()}
        control.stop();withTimeout(1000) {waiter.await()};assertTrue(control.stopped.get())
        val cancelled=launch(start=CoroutineStart.UNDISPATCHED) {control.awaitChange()}
        withTimeout(1000) {cancelled.cancelAndJoin()};assertTrue(cancelled.isCancelled)
        control.stop();withTimeout(1000) {control.awaitChange()}
    }
}
