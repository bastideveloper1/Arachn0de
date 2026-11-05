package com.r0ybt.arachn0de.ui

/** Temporary focused diagnostics; debug builds only, never identifiers or private content. */
internal object ResponseTiming {
    fun start(event:String):Long {
        val now=android.os.SystemClock.elapsedRealtime()
        if(com.r0ybt.arachn0de.BuildConfig.DEBUG) android.util.Log.d("Arachn0deResponse","$event t=$now")
        return now
    }
    fun end(event:String,started:Long) {
        if(com.r0ybt.arachn0de.BuildConfig.DEBUG) android.util.Log.d("Arachn0deResponse","$event elapsed_ms=${android.os.SystemClock.elapsedRealtime()-started}")
    }
}
