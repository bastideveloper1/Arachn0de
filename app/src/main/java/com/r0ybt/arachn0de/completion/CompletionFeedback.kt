package com.r0ybt.arachn0de.completion

import com.r0ybt.arachn0de.ui.theme.AppearanceSettings

internal interface CompletionAudio:AutoCloseable {
    fun select(clip:String?)
    fun play()
    fun stop()
}

/** Shared playback decision, separate from Android's audio device and persistence. */
internal class CompletionFeedback(private val audio:CompletionAudio) {
    private val seen=linkedSetOf<String>()
    fun completed(eventId:String,settings:AppearanceSettings) {
        if(!seen.add(eventId))return
        while(seen.size>256)seen.remove(seen.first())
        if(!settings.completionSoundEnabled)return
        audio.select(null)
        audio.play()
    }
}
