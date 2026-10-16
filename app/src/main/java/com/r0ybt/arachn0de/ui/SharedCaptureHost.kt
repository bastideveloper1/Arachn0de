package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.sharing.SharedCaptureQueue

internal class CaptureGate {var active by mutableStateOf<String?>(null)}
internal val LocalCaptureGate=staticCompositionLocalOf<CaptureGate> {CaptureGate()}

@Composable internal fun SharedCaptureHost(index:List<ElementResult>,repo:AccessRepository,ordinaryOpen:Boolean) {
    val app=LocalContext.current.applicationContext as Arachn0deApplication
    val session=app.security.current.value ?: return
    val queue=session.sharedCaptures
    var entries by remember(queue) {mutableStateOf(emptyList<SharedCaptureQueue.Entry>())}
    val loading=remember(queue) {com.r0ybt.arachn0de.ui.state.LoadState()}
    LaunchedEffect(queue,loading.attempt) {loading.collect(queue.observe()) {entries=it}}
    val operation=remember(queue) {com.r0ybt.arachn0de.ui.state.OperationState(session.scope)}
    LaunchedEffect(queue) {operation.submit("No se pudo recuperar la captura compartida. Se conserva para reintentar.",{queue.recover();true})}
    val gate=LocalCaptureGate.current
    entries.firstOrNull()?.let {entry->
        DisposableEffect(queue) {val hold=app.security.captures.holdPrivateView();onDispose {hold.close()}}
        if(!ordinaryOpen && (gate.active==null || gate.active==entry.id)) key(entry.id) {
            QuickCaptureDialog(index,repo,{},pending=entry,queue=queue)
        }
    }
    OperationErrorDialog(operation)
    LoadErrorDialog(loading)
}
