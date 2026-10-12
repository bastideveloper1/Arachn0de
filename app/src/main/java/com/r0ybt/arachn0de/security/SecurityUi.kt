package com.r0ybt.arachn0de.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import com.r0ybt.arachn0de.ui.theme.BlackWhite90
import com.r0ybt.arachn0de.ui.theme.materialColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable private fun PasswordField(label:String,value:String,change:(String)->Unit) {
    OutlinedTextField(value=value,onValueChange=change,label={Text(label)},singleLine=true,
        visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
}
@Composable internal fun UnlockScreen(manager:VaultManager) {
    val scope=rememberCoroutineScope()
    var password by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    val configured=runCatching { manager.configured }
    // Scope the locked palette to this screen; shared theme and security controls stay unchanged.
    MaterialTheme(colorScheme=BlackWhite90.palette.materialColors()) {
        Surface(Modifier.fillMaxSize(),color=BlackWhite90.Background,contentColor=BlackWhite90.Text) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // AppSafeArea already consumes system/IME insets. Center when there is room and scroll otherwise.
                Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min=maxHeight).padding(20.dp),contentAlignment=Alignment.Center) {
                    Column(Modifier.widthIn(max=420.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        com.r0ybt.arachn0de.ui.OfficialIcon(com.r0ybt.arachn0de.R.drawable.bw90_brand_logo,"Logo de Arachn0de",size=160.dp,
                            modifier=Modifier.fillMaxWidth().height(96.dp))
                        if(configured.isFailure) Text("No se pudo recuperar la configuración de seguridad. No se han sustituido los datos existentes.")
                        else {
                            if(!configured.getOrThrow()) Text("Proteger tus datos",style=MaterialTheme.typography.titleMedium)
                            if(!configured.getOrThrow()) Text("Crea una contraseña de al menos ocho caracteres. La migración conservará tus datos y una copia cifrada recuperable. Perder la contraseña puede impedir recuperarlos.")
                            PasswordField("Contraseña",password) {password=it;error=null}
                            if(!configured.getOrThrow()) PasswordField("Confirmar contraseña",confirm) {confirm=it;error=null}
                            Button(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(8.dp),elevation=null,
                                colors=ButtonDefaults.buttonColors(containerColor=BlackWhite90.Raised,contentColor=BlackWhite90.Text),
                                enabled=!busy && password.isNotEmpty(),onClick={
                                if(!configured.getOrThrow() && (password.length<8 || password!=confirm)) error="Confirma la misma contraseña de al menos ocho caracteres."
                                else {
                                    val secret=password.toCharArray();password="";confirm="";busy=true;error=null
                                    scope.launch {
                                        try { if(configured.getOrThrow()) manager.unlock(secret) else manager.setup(secret) }
                                        catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
                                        catch(_:Exception) { error=if(manager.configured) "No se pudo abrir el almacén. Comprueba la contraseña o reintenta la recuperación pendiente." else "No se pudo completar la migración. Los originales se conservan; reintenta con la misma contraseña y espacio suficiente." }
                                        finally { secret.fill('\u0000');busy=false }
                                    }.invokeOnCompletion { secret.fill('\u0000') }
                                }
                            }) {Text(if(busy) "Procesando…" else if(configured.getOrThrow()) "Desbloquear" else "Proteger y migrar")}
                            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            error?.let {Text(it)}
                            InitialRecoveryAction(manager,!busy)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun SecurityControls(manager:VaultManager,session:VaultSession,showLock:Boolean=true,onLockReady:(com.r0ybt.arachn0de.ui.ProjectLockAction)->Unit={},showChrome:Boolean=true,onSettingsReady:(()->Unit)->Unit={},onPageReady:((@Composable (()->Unit)->Unit)->Unit)={}) {
    val captureAllowed by manager.captures.allowed.collectAsState()
    val scope=rememberCoroutineScope();var settings by remember {mutableStateOf(false)}
    var action by remember {mutableStateOf<String?>(null)}
    var old by remember {mutableStateOf("")};var next by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)};var notice by remember {mutableStateOf<String?>(null)}
    val preferences=remember(session) {session.context.getSharedPreferences("security_preferences",0)}
    var timeout by remember(session) {mutableLongStateOf(preferences.getLong("background_timeout",30000L))}
    val lockAction=remember(busy,manager,scope) {com.r0ybt.arachn0de.ui.ProjectLockAction(!busy) {manager.captures.revoke();scope.launch {manager.lock()}}}
    val openSettings=remember { {settings=true} }
    SideEffect {onLockReady(lockAction);onSettingsReady(openSettings)}
    if(showChrome && !settings) Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.End) {
        TextButton(onClick={settings=true}) {Text("Seguridad")}
        if(showLock) TextButton(enabled=!busy,onClick=lockAction.run) {com.r0ybt.arachn0de.ui.OfficialIcon(com.r0ybt.arachn0de.R.drawable.official_bloquear); Text("Bloquear")}
    }
    if(showChrome && !settings && session.primary) Text(if(captureAllowed) "Capturas permitidas en esta sesión" else "Capturas bloqueadas",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.labelSmall)
    val page: @Composable (()->Unit)->Unit = remember(manager,session) { {close->
        androidx.activity.compose.BackHandler(enabled=!busy,onBack=close)
        Surface(Modifier.fillMaxSize().testTag("security-screen")) {
            Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=10.dp)) {
                com.r0ybt.arachn0de.ui.HeaderBar({},title="Seguridad",onBack={if(!busy) close()},trailingAction={TextButton(enabled=!busy,onClick=close) {Text("Cerrar")}})
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Bloqueo al pasar a segundo plano")
        for((value,label) in listOf(0L to "Inmediato",30000L to "30 segundos",60000L to "1 minuto",300000L to "5 minutos")) {
            TextButton(onClick={preferences.edit().putLong("background_timeout",value).apply();timeout=value}) {Text((if(timeout==value) "✓ " else "")+label)}
        }
        if(session.primary) Text("Capturas de pantalla",style=MaterialTheme.typography.titleSmall)
        if(session.primary) Text(if(captureAllowed) "Capturas permitidas en esta sesión" else "Capturas bloqueadas")
        if(session.primary) TextButton(enabled=!busy,onClick={
            val wasAllowed=captureAllowed
            manager.captures.revoke()
            if(!wasAllowed) {action="captures";notice=null}
        }) {Text(if(captureAllowed) "Bloquear capturas de pantalla" else "Permitir capturas de pantalla")}
        Text("Contraseñas",style=MaterialTheme.typography.titleSmall)
        TextButton(onClick={action="change";notice=null}) {Text("Cambiar contraseña")}
        if(session.primary && manager.canCreateSecondary) TextButton(onClick={action="secondary";notice=null}) {Text("Configurar contraseña señuelo")}
        Text("No hay recuperación sin contraseña. Los backups necesitan su propia contraseña. El bloqueo cierra el acceso al seguimiento Metro; su estimación guardada se recupera al desbloquear.")
        notice?.let {Text(it)}

                }
            }
        }
    }}
    SideEffect {onPageReady(page)}
    if(settings) page {if(!busy) settings=false}
    action?.let { command ->
        fun clear() {old="";next="";confirm="";action=null}
        AlertDialog(onDismissRequest={if(!busy) clear()},properties=androidx.compose.ui.window.DialogProperties(securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn),title={Text(if(command=="captures") "Permitir capturas de pantalla" else if(command=="change") "Cambiar contraseña" else "Contraseña señuelo")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            PasswordField(if(command=="captures") "Contraseña principal" else "Contraseña actual",old) {old=it}
            if(command!="captures") {
                PasswordField("Nueva contraseña",next) {next=it}
                PasswordField("Confirmar nueva contraseña",confirm) {confirm=it}
            }
            notice?.let {Text(it)}
        }},confirmButton={TextButton(enabled=!busy,onClick={
            if(command!="captures" && (next.length<8 || next!=confirm)) notice="Confirma la misma contraseña de al menos ocho caracteres."
            else {
                val previous=old.toCharArray();val replacement=next.toCharArray();clear();busy=true;notice=null
                scope.launch {
                    try {
                        if(command=="captures") manager.authorizeCaptures(session,previous) else if(command=="change") manager.changePassword(previous,replacement) else manager.createSecondary(previous,replacement)
                        notice=if(command=="captures") "Capturas permitidas en esta sesión." else "Configuración guardada."
                    } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                    catch(_:Exception) {notice=if(command=="captures") "No se autorizaron las capturas. Comprueba la contraseña y reintenta." else "No se pudo guardar. Verifica la contraseña actual y usa una contraseña distinta para cada almacén."}
                    finally {previous.fill('\u0000');replacement.fill('\u0000');busy=false}
                }.invokeOnCompletion {previous.fill('\u0000');replacement.fill('\u0000')}
            }
        }) {Text(if(command=="captures") "Autorizar" else "Guardar")}},dismissButton={TextButton(enabled=!busy,onClick={clear()}) {Text("Cancelar")}})
    }
}

/** Explicit export only; never resets or silently restores the older migration snapshot. */
@Composable private fun InitialRecoveryAction(manager:VaultManager,enabled:Boolean) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var show by remember {mutableStateOf(false)}
    var old by remember {mutableStateOf("")};var next by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    var message by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    val secrets=remember {arrayOfNulls<CharArray>(2)}
    fun clear() {secrets.forEach {it?.fill('\u0000')};secrets.fill(null);old="";next="";confirm=""}
    DisposableEffect(Unit) {onDispose {clear()}}
    val destination=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) {uri ->
        val source=secrets[0];val target=secrets[1];secrets.fill(null)
        if(uri==null || source==null || target==null) {source?.fill('\u0000');target?.fill('\u0000');busy=false}
        else scope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use {manager.exportInitialRecovery(source,target,it)}
                }
                message="Copia inicial cifrada guardada. No incluye cambios posteriores a la migración. Los datos actuales se conservan."
            } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(_:Exception) {message="No se pudo recuperar la copia inicial. Verifica la contraseña y el destino; no se han sustituido datos."}
            finally {source.fill('\u0000');target.fill('\u0000');busy=false}
        }.invokeOnCompletion {source.fill('\u0000');target.fill('\u0000')}
    }
    Column {
        TextButton(enabled=enabled && !busy,onClick={show=true;message=null}) {Text("Recuperar copia de migración")}
        message?.let {Text(it)}
    }
    if(show) AlertDialog(properties=androidx.compose.ui.window.DialogProperties(securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn),onDismissRequest={clear();show=false},title={Text("Exportar copia inicial")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Exporta el estado previo a la activación del cifrado conociendo su contraseña. Puede ser anterior a tus últimos cambios. No sustituye el almacén actual. Después puedes validarlo y restaurarlo en un almacén principal desde Backups.")
        PasswordField("Contraseña del almacén",old) {old=it}
        PasswordField("Contraseña del backup",next) {next=it}
        PasswordField("Confirmar contraseña del backup",confirm) {confirm=it}
        message?.let {Text(it)}
    }},confirmButton={TextButton(onClick={
        if(old.isEmpty() || next.length<8 || next!=confirm) message="Confirma la contraseña del backup de al menos ocho caracteres."
        else {
            secrets[0]=old.toCharArray();secrets[1]=next.toCharArray();old="";next="";confirm="";show=false;busy=true
            try {destination.launch("arachn0de-recuperacion-inicial.arachnode")}
            catch(_:Exception) {clear();busy=false;message="Android no pudo abrir el selector de archivos."}
        }
    }) {Text("Elegir destino")}},dismissButton={TextButton(onClick={clear();show=false}) {Text("Cancelar")}})
}
