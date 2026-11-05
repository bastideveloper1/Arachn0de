package com.r0ybt.arachn0de.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.bank.BankRepository
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.TaskStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** Financial checks stay in the current list; other checks retain their existing action. */
@Composable internal fun rememberPaymentCheck(node:Node,listState:androidx.compose.foundation.lazy.LazyListState?=null,onToggle:()->Unit):()->Unit {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication
    val scope=rememberCoroutineScope()
    var open by rememberSaveable(node.id) {mutableStateOf(false)}
    var resolving by remember(node.id) {mutableStateOf(false)}
    var error by remember(node.id) {mutableStateOf<String?>(null)}
    var position by remember(node.id) {mutableStateOf<Pair<Int,Int>?>(null)}
    var viewportHeight by remember(node.id) {mutableIntStateOf(0)}
    if(open) CardPaymentPanel(node.id,{
        open=false
        val saved=position
        if(saved!=null && listState!=null)scope.launch {
            withFrameNanos {}
            snapshotFlow {listState.layoutInfo.viewportSize.height}.first {it==viewportHeight}
            listState.scrollToItem(saved.first,saved.second)
        }
    },prefillPending=true)
    error?.let {message->AlertDialog(onDismissRequest={error=null},title={Text("Pago")},text={Text(message)},
        confirmButton={TextButton(onClick={error=null}) {Text("Aceptar")}})}
    return {
        if(node.obligation==null || node.workState!=null || app==null) onToggle()
        else if(!resolving && !open) {
            position=listState?.let {it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset}
            viewportHeight=listState?.layoutInfo?.viewportSize?.height ?: 0
            resolving=true
            scope.launch {
                try {
                    // Read the committed remainder, including when the list's paid projection is still loading.
                    val s=BankRepository(app.database,nodeRepository=app.nodeRepository).state()
                    val current=requireNotNull(s.nodes.find {it.id==node.id}) {"La tarjeta ya no está disponible."}
                    if(current.isCompleted || current.amountMinor==null) onToggle()
                    else if(s.remaining(current)>0) open=true
                    else check(app.nodeRepository.setTaskStatus(node.id,TaskStatus.COMPLETED))
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {error=e.message ?: "No se pudo preparar el pago."}
                finally {resolving=false}
            }
        }
    }
}
