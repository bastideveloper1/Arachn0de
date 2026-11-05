package com.r0ybt.arachn0de.guest

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.r0ybt.arachn0de.security.VaultSession
import kotlinx.coroutines.launch

@Composable internal fun GuestCaptureRecovery(session:VaultSession) {
    if(!session.primary)return
    val context=LocalContext.current.applicationContext
    val error by GuestCaptureImporter.error.collectAsState()
    var busy by remember(session) {mutableStateOf(false)}
    if(error!=null) AlertDialog(onDismissRequest={if(!busy)GuestCaptureImporter.error.value=null},
        title={Text("Captura pendiente")},text={Text(requireNotNull(error))},
        confirmButton={TextButton(enabled=!busy,onClick={busy=true;session.scope.launch {try {GuestCaptureImporter.importPending(context,session)} finally {busy=false}}}) {Text("Reintentar")}},
        dismissButton={TextButton(enabled=!busy,onClick={GuestCaptureImporter.error.value=null}) {Text("Cerrar")}})
}
