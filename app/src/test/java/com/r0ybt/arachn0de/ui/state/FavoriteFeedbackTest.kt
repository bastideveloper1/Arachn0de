package com.r0ybt.arachn0de.ui.state

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=android.app.Application::class,sdk=[28])
class FavoriteFeedbackTest {
    @Test fun immediateFeedbackSerializesClicksAndWaitsForConfirmation()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val feedback=FavoriteFeedback(scope);val gate=CompletableDeferred<Unit>();val writes=mutableListOf<Boolean>()
            feedback.toggle(false,{writes+=it;gate.await()})
            assertTrue(feedback.shown(false));assertTrue(feedback.operation.busy)
            repeat(5) {feedback.toggle(false,{writes+=it})}
            assertEquals(listOf(true),writes)
            gate.complete(Unit);yield()
            assertFalse(feedback.operation.busy);assertTrue(feedback.shown(false))
            feedback.acknowledge(true)
            feedback.toggle(true,{writes+=it})
            assertFalse(feedback.shown(true));assertEquals(listOf(true,false),writes)
            feedback.acknowledge(false);assertFalse(feedback.shown(false))
        } finally {scope.cancel()}
    }
    @Test fun failedWriteRestoresActualStateAndReportsError()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val feedback=FavoriteFeedback(scope);val gate=CompletableDeferred<Unit>();var reported=false
            feedback.toggle(true,{gate.await();error("fixture failure")},{reported=true})
            assertFalse(feedback.shown(true));gate.complete(Unit);yield()
            assertTrue(feedback.shown(true));assertNotNull(feedback.operation.error);assertTrue(reported)
        } finally {scope.cancel()}
    }
    @Test fun screenCancellationDoesNotCancelSessionOwnedWrite()=runBlocking {
        val session=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val screen=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val feedback=FavoriteFeedback(session);val gate=CompletableDeferred<Unit>();var committed=false
            screen.launch {feedback.toggle(false,{gate.await();committed=it})}
            screen.cancel();assertTrue(feedback.operation.busy)
            gate.complete(Unit);yield();assertTrue(committed);assertFalse(feedback.operation.busy)
        } finally {screen.cancel();session.cancel()}
    }
}
