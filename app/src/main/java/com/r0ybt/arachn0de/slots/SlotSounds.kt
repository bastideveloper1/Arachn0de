package com.r0ybt.arachn0de.slots

import android.media.AudioManager
import android.media.ToneGenerator

/** Temporary synthesized effects, media volume, no files, preferences or background playback. */
internal class SlotSounds:AutoCloseable {
    private var tone:ToneGenerator?=runCatching {ToneGenerator(AudioManager.STREAM_MUSIC,75)}.getOrNull()
    fun coin() {tone?.startTone(ToneGenerator.TONE_PROP_ACK,130)}
    fun tick() {tone?.startTone(ToneGenerator.TONE_PROP_BEEP,35)}
    fun stopReel() {tone?.startTone(ToneGenerator.TONE_PROP_ACK,85)}
    fun finish() {tone?.startTone(ToneGenerator.TONE_PROP_BEEP2,180)}
    fun win() {tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE,900)}
    fun stop() {tone?.stopTone()}
    override fun close() {tone?.release();tone=null}
}
