package com.r0ybt.arachn0de.ui.state

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.TaskTemporal
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** One foreground clock for the whole project screen, never one timer per card. */
@Composable
internal fun rememberTaskScreenNow(nodes: List<Node>, clock: () -> Long = System::currentTimeMillis): Long =
    rememberTaskScreenNow(nodes, clock, onRefresh = {})

/** Refresh display environment even when an injected clock returns the same instant. */
@Composable
internal fun rememberTaskScreenNow(nodes: List<Node>, clock: () -> Long, onRefresh: () -> Unit): Long {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val readClock by rememberUpdatedState(clock)
    val refreshEnvironment by rememberUpdatedState(onRefresh)
    var now by remember { mutableStateOf(readClock()) }
    var clockChange by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { clockChange++ }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            // Both actions are protected system broadcasts; no custom broadcast is accepted.
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(lifecycle, nodes, clockChange) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val instant = readClock()
                now = instant
                refreshEnvironment()
                val next = nodes.mapNotNull { TaskTemporal.nextTransition(it, instant) }.minOrNull()
                // Also recheck wall-clock changes at most once a minute while foregrounded.
                val until = next?.let { if (instant < 0 && it > Long.MAX_VALUE + instant) 60_000L else it - instant }
                delay(until?.coerceIn(1L, 60_000L) ?: 60_000L)
            }
        }
    }
    return now
}
