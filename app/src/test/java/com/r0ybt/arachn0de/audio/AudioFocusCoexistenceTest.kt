package com.r0ybt.arachn0de.audio

import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test

class AudioFocusCoexistenceTest {
    @Test fun notificationDuckingKeepsRecordingAndRealInterruptionsStillPause() {
        assertFalse(shouldPauseAudioForFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,true))
        assertTrue(shouldPauseAudioForFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,false))
        assertTrue(shouldPauseAudioForFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,true))
        assertTrue(shouldPauseAudioForFocus(AudioManager.AUDIOFOCUS_LOSS,true))
        assertFalse(shouldPauseAudioForFocus(AudioManager.AUDIOFOCUS_GAIN,true))
    }
}
