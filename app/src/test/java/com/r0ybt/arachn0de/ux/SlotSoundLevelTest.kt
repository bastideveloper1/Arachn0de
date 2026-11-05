package com.r0ybt.arachn0de.ux

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import com.r0ybt.arachn0de.slots.SlotSounds
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@Implements(ToneGenerator::class)
class CapturedTone {
    companion object {var stream=-1;var gain=-1;val tones=mutableListOf<Pair<Int,Int>>()}
    @Implementation fun __constructor__(s:Int,v:Int) {stream=s;gain=v;tones.clear()}
    @Implementation fun startTone(t:Int,d:Int):Boolean {tones+=t to d;return true}
    @Implementation fun stopTone() {}
    @Implementation fun release() {}
}
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],shadows=[CapturedTone::class])
class SlotSoundLevelTest {
    @Test fun victoryHasADistinctBoundedMediaEffect() {
        SlotSounds().use {it.win();it.stop()}
        assertEquals(AudioManager.STREAM_MUSIC,CapturedTone.stream)
        assertEquals(1,CapturedTone.tones.size)
        assertTrue(CapturedTone.tones.single().second in 500..1500)
        assertNotEquals(ToneGenerator.TONE_PROP_BEEP2,CapturedTone.tones.single().first)
    }
    @Test fun louderEffectsKeepMusicStreamAndOriginalTimingBelowMaximumGain() {
        SlotSounds().use {it.coin();it.tick();it.stopReel();it.finish();it.stop()}
        assertEquals(AudioManager.STREAM_MUSIC,CapturedTone.stream)
        assertTrue(CapturedTone.gain in 65..85)
        assertEquals(listOf(ToneGenerator.TONE_PROP_ACK to 130,ToneGenerator.TONE_PROP_BEEP to 35,ToneGenerator.TONE_PROP_ACK to 85,ToneGenerator.TONE_PROP_BEEP2 to 180),CapturedTone.tones)
    }
}
