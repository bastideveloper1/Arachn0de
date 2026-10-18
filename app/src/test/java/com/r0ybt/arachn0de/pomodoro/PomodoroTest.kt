package com.r0ybt.arachn0de.pomodoro

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class PomodoroTest {
    private val start=PomodoroTime(1_800_000_000_000,100_000,7)
    @Test fun manualProgressPreservesStatusClampsAndHoldsEndWithoutAdvancing() {
        val running=PomodoroState().start(start)
        val now=start.copy(elapsed=start.elapsed+600_000)
        val backwards=running.adjust(-5,now)
        assertEquals(1_200_000L,backwards.session!!.remainingAt(now))
        assertEquals(PomodoroStatus.RUNNING,backwards.session.status)
        val forwards=running.adjust(5,now)
        assertEquals(600_000L,forwards.session!!.remainingAt(now))
        val paused=running.pause(now).adjust(1,now)
        assertEquals(PomodoroStatus.PAUSED,paused.session!!.status)
        assertEquals(840_000L,paused.session.remaining)
        var end=running
        repeat(5) {end=end.adjust(5,start)}
        assertEquals(0L,end.session!!.remainingAt(start));assertTrue(end.session!!.manualEnd)
        assertEquals(PomodoroStatus.RUNNING,end.session!!.status)
        assertEquals(end,end.advance(start.copy(elapsed=start.elapsed+999_999)))
        assertEquals(end,PomodoroCodec.decode(PomodoroCodec.encode(end)))
        assertEquals(60_000L,end.adjust(-1,start).session!!.remaining)
        var beginning=running
        repeat(8) {beginning=beginning.adjust(-5,start)}
        assertEquals(beginning.session!!.total,beginning.session!!.remaining)
    }

    @Test fun manualAdjustmentPersistsAndReschedulesTheExistingSession() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PomodoroController.CACHE,0).edit().clear().commit()
        var now=start
        val store=context.getSharedPreferences("adjustment-test",0).apply {edit().clear().commit()}
        val controller=PomodoroController(context) {now}
        controller.attach(store);controller.start();now=now.copy(elapsed=now.elapsed+600_000)
        controller.adjust(5)
        val saved=controller.state.value
        assertEquals(600_000L,controller.remaining())
        val reopened=PomodoroController(context) {now};reopened.attach(store)
        assertEquals(saved,reopened.state.value)
        reopened.pause();reopened.adjust(-1)
        assertEquals(660_000L,reopened.remaining());assertEquals(PomodoroStatus.PAUSED,reopened.state.value.session!!.status)
        controller.attach(null);reopened.attach(null)
    }

    @Test fun elapsedDeadlinePauseResumeAndRebootDoNotDependOnTicks() {
        val running=PomodoroState().start(start)
        val advanced=start.copy(elapsed=start.elapsed+90_000,wall=start.wall-5_000_000)
        assertEquals(1_410_000L,running.session!!.remainingAt(advanced))
        val restored=PomodoroCodec.decode(PomodoroCodec.encode(running))
        assertEquals(running,restored)
        val paused=restored.pause(advanced)
        assertEquals(PomodoroStatus.PAUSED,paused.session!!.status)
        assertEquals(1_410_000L,paused.session.remainingAt(start.copy(elapsed=900_000,wall=start.wall+900_000)))
        val resumed=paused.start(start.copy(elapsed=200_000))
        assertEquals(1_350_000L,resumed.session!!.remainingAt(start.copy(elapsed=260_000)))
        assertEquals(1_200_000L,running.session.remainingAt(start.copy(wall=start.wall+300_000,elapsed=10,boot=8)))
        assertEquals(1_500_000L,running.session.remainingAt(start.copy(wall=start.wall-1,elapsed=10,boot=8)))
        assertEquals("25:00",pomodoroTimeText(1_500_000));assertEquals("00:01",pomodoroTimeText(1));assertEquals("00:00",pomodoroTimeText(0))
    }
    @Test fun completeCyclesWaitForManualStartsAndKeepSessionNumbers() {
        var now=start
        var state=PomodoroState(PomodoroConfig(1,2,3,4))
        repeat(4) {index->
            state=state.start(now)
            assertEquals(PomodoroPhase.FOCUS,state.session!!.phase);assertEquals(index+1,state.session!!.number)
            now=now.copy(wall=now.wall+3_600_000,elapsed=now.elapsed+3_600_000)
            state=state.advance(now)
            assertEquals(if(index==3) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK,state.session!!.phase)
            assertEquals(PomodoroStatus.READY,state.session!!.status);assertEquals(index+1,state.session!!.number)
            assertEquals(state,state.advance(now.copy(elapsed=now.elapsed+999_999_999)))
            state=state.start(now)
            assertEquals(if(index==3) 180_000L else 120_000L,state.session!!.remaining)
            now=now.copy(wall=now.wall+180_000,elapsed=now.elapsed+180_000)
            state=state.advance(now)
        }
        assertEquals(PomodoroPhase.FOCUS,state.session!!.phase);assertEquals(1,state.session!!.number)
        assertEquals(PomodoroStatus.FINISHED,state.finish().session!!.status);assertFalse(state.finish().visible)
        assertEquals(1,state.finish().start(now).session!!.number)
    }
    @Test fun configurationChangesDoNotRewriteRunningPhaseOrCurrentCycleAndBadPayloadsFail() {
        val running=PomodoroState().start(start)
        val changed=running.copy(config=PomodoroConfig(2,3,4,2,true,false))
        assertEquals(1_500_000L,changed.session!!.total);assertEquals(4,changed.session.cycle)
        val next=changed.advance(start.copy(elapsed=start.elapsed+1_500_000))
        assertEquals(180_000L,next.session!!.remaining)
        assertEquals(changed,PomodoroCodec.decode(PomodoroCodec.encode(changed)))
        listOf(
            JSONObject(PomodoroCodec.encode(changed)).put("version",3).toString(),
            JSONObject(PomodoroCodec.encode(changed)).put("extra",true).toString(),
            JSONObject(PomodoroCodec.encode(changed)).apply {getJSONObject("config").put("focus",0)}.toString(),
            JSONObject(PomodoroCodec.encode(changed)).apply {getJSONObject("session").put("remaining",-1)}.toString(),
            JSONObject(PomodoroCodec.encode(changed)).apply {getJSONObject("config").put("sound","true")}.toString()
        ).forEach {assertTrue(runCatching {PomodoroCodec.decode(it)}.isFailure)}
    }
    @Test fun controllerRecoversAfterProcessDeathAndLockAndFinishesOnlyOnce() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PomodoroController.CACHE,0).edit().clear().commit()
        val storage=context.getSharedPreferences("pomodoro-test",0).apply {edit().clear().commit()}
        var now=start
        val first=PomodoroController(context) {now};first.attach(storage)
        first.configure(PomodoroConfig(1,1,1,2));first.start()
        now=now.copy(elapsed=now.elapsed+10_000,wall=now.wall+10_000)
        val second=PomodoroController(context) {now}
        assertEquals(50_000L,second.remaining())
        first.attach(null);second.attach(storage)
        // A restored running checkpoint must re-anchor clocks rather than trust another device's elapsed value.
        val imported=second.state.value.copy(session=second.state.value.session!!.copy(elapsed=8_000_000,boot=99),revision=20)
        storage.edit().putString(PomodoroCodec.KEY,PomodoroCodec.encode(imported)).commit()
        assertEquals(50_000L,second.remaining())
        assertEquals(now.elapsed,second.state.value.session!!.elapsed)
        second.pause()
        now=now.copy(elapsed=now.elapsed+500_000,wall=now.wall+500_000)
        assertEquals(50_000L,second.remaining())
        second.start();second.attach(null)
        now=now.copy(elapsed=now.elapsed+60_000,wall=now.wall+60_000)
        second.refresh()
        val finished=second.state.value
        assertEquals(PomodoroPhase.SHORT_BREAK,finished.session!!.phase)
        assertEquals(PomodoroStatus.READY,finished.session.status)
        second.refresh();assertEquals(finished,second.state.value)
        second.attach(storage)
        assertEquals(finished,PomodoroCodec.decode(storage.getString(PomodoroCodec.KEY,null)!!))
        second.finish();assertFalse(second.state.value.visible)
    }
}
