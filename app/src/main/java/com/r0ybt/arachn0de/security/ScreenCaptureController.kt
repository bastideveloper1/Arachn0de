package com.r0ybt.arachn0de.security

import android.view.Window
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Volatile authorization only. Window mutations run on the UI thread. */
internal class ScreenCaptureController {
    private var window:Window?=null
    private val privateViews=mutableSetOf<Any>()
    @Synchronized fun holdPrivateView():AutoCloseable {
        val token=Any();privateViews.add(token);window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        return AutoCloseable {synchronized(this) {
            privateViews.remove(token)
            if(privateViews.isEmpty() && permission.value) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }}
    }
    private val permission=MutableStateFlow(false)
    val allowed=permission.asStateFlow()
    @Volatile var epoch:Long=0;private set
    @Synchronized fun attach(target:Window) {
        epoch++;window=target
        if(permission.value && privateViews.isEmpty()) target.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) else target.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    @Synchronized fun detach(target:Window, closingSession:Boolean=true) {
        if(window===target) {if(closingSession) revoke() else {epoch++;target.addFlags(WindowManager.LayoutParams.FLAG_SECURE)};window=null}
    }
    /** UI event handlers call this when manually blocking captures or ending the session. */
    @Synchronized fun revoke() {
        epoch++
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        permission.value=false
    }
    private suspend fun onWindowThread(action:()->Unit) {
        val completed=synchronized(this) {if(window==null) {action();true} else false}
        if(!completed) withContext(Dispatchers.Main.immediate) {action()}
    }
    suspend fun protectBeforeTransition()=onWindowThread {revoke()}
    /** A background/revocation during verification invalidates that authorization attempt. */
    suspend fun grant(expectedEpoch:Long,beforeGrant:()->Unit={},stillPrimarySession:()->Boolean):Boolean {
        var granted=false
        onWindowThread {
            synchronized(this) {
                if(epoch==expectedEpoch && stillPrimarySession()) {
                    beforeGrant()
                    if(privateViews.isEmpty()) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    permission.value=true;granted=true
                }
            }
        }
        return granted
    }
}
