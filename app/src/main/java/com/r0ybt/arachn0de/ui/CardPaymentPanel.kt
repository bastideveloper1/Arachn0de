package com.r0ybt.arachn0de.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.bank.*
import kotlinx.coroutines.launch

/** Card entry only owns loading and dismissal; the form and transaction are Banco's. */
@Composable internal fun CardPaymentPanel(nodeId:String,onDismiss:()->Unit,onCorrectAmount:(()->Unit)?=null,prefillPending:Boolean=false) {
    val app=LocalContext.current.applicationContext as Arachn0deApplication
    val db=app.database
    val repo=remember(db) {BankRepository(db,nodeRepository=app.nodeRepository)}
    val scope=rememberCoroutineScope()
    var state by remember(nodeId) {mutableStateOf<BankState?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(db,nodeId) {
        try {db.invalidationTracker.createFlow("bank_roots","bank_accounts","bank_operations","bank_allocations","nodes","persons","node_person")
            .collect {state=repo.state()}
        } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {error=e.message ?: "No se pudo cargar Pago."}
    }
    if(state==null) BankPanel("Pago",onDismiss,{},enabled=false) {Text("Cargando…")}
    state?.let {s->
        if(s.nodes.none {it.id==nodeId}) BankPanel("Pago",onDismiss,onDismiss,confirmLabel="Cerrar") {Text("La tarjeta ya no está disponible.")}
        else BankPaymentEditor(s,repo,null,"DIRECT",null,busy,{if(!busy)onDismiss()},{error=it},
            {preview,parts->if(!busy) {busy=true;scope.launch {
                try {repo.confirmPayment(preview,parts);onDismiss()}
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {error=e.message ?: "No se pudo registrar el pago."}
                finally {busy=false}
            }}},selectedNodeId=nodeId,chooseSource=true,onCorrectAmount=onCorrectAmount,prefillPending=prefillPending)
    }
    error?.let {message->AlertDialog(onDismissRequest={error=null},title={Text("Pago")},text={Text(message)},
        confirmButton={TextButton(onClick={error=null}) {Text("Aceptar")}})}
}
