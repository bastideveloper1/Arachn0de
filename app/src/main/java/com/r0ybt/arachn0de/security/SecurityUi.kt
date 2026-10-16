package com.r0ybt.arachn0de.security

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Backup
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
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
    MaterialTheme(colorScheme=com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette.current.materialColors()) {
        Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background,contentColor=Arachn0deColors.TextPrimary) {
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
                                colors=ButtonDefaults.buttonColors(containerColor=Arachn0deColors.SurfaceRaised,contentColor=Arachn0deColors.TextPrimary),
                                enabled=!busy && password.isNotEmpty(),onClick={
                                if(!configured.getOrThrow() && (password.length<8 || password!=confirm)) error="Confirma la misma contraseña de al menos ocho caracteres."
                                else {
                                    val secret=password.toCharArray();password="";confirm="";busy=true;error=null
                                    scope.launch {
                                        try { if(configured.getOrThrow()) manager.unlock(secret) else manager.setup(secret) }
                                        catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
                                        catch(failure:Exception) { error=if(failure is VaultInitialDataException) "No se pudieron preparar los Núcleos. Reintenta el desbloqueo." else if(manager.configured) "No se pudo abrir el almacén. Comprueba la contraseña o reintenta la recuperación pendiente." else "No se pudo completar la migración. Los originales se conservan; reintenta con la misma contraseña y espacio suficiente." }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SecurityControls(manager:VaultManager,session:VaultSession,showLock:Boolean=true,onLockReady:(com.r0ybt.arachn0de.ui.ProjectLockAction)->Unit={},showChrome:Boolean=true,onSettingsReady:(()->Unit)->Unit={},onPageReady:((@Composable (()->Unit)->Unit)->Unit)={}) {
    val captureAllowed by manager.captures.allowed.collectAsState()
    val scope=rememberCoroutineScope();var settings by remember {mutableStateOf(false)}
    var configuration by remember {mutableStateOf<String?>(null)}
    var action by remember {mutableStateOf<String?>(null)}
    var old by remember {mutableStateOf("")};var next by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)};var notice by remember {mutableStateOf<String?>(null)}
    val preferences=remember(session) {session.context.getSharedPreferences("security_preferences",0)}
    var persistentCapture by remember {mutableStateOf(false)}
    var persistentCaptureAllowed by remember(session) {mutableStateOf(preferences.getBoolean(ScreenCapturePreference.KEY,false))}
    var timeout by remember(session) {mutableLongStateOf(AutoLockPreference.read(preferences))}
    DisposableEffect(preferences,session) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
            if(key==AutoLockPreference.KEY) scope.launch {timeout=AutoLockPreference.read(preferences)}
            if(key==ScreenCapturePreference.KEY && session.primary) {
                persistentCaptureAllowed=preferences.getBoolean(ScreenCapturePreference.KEY,false)
                if(persistentCaptureAllowed) scope.launch {manager.restoreCapturePreference(session)}
                else scope.launch {
                    if(manager.current.value===session) manager.captures.protectBeforeTransition()
                }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        if(session.primary) scope.launch {manager.restoreCapturePreference(session)}
        onDispose {preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val lockAction=remember(busy,manager,scope) {com.r0ybt.arachn0de.ui.ProjectLockAction(!busy) {manager.captures.revoke();scope.launch {manager.lock()}}}
    val openSettings=remember { {configuration=null;settings=true} }
    SideEffect {onLockReady(lockAction);onSettingsReady(openSettings)}
    if(showChrome && !settings) Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.End) {
        TextButton(onClick={configuration=null;settings=true}) {Text("Seguridad y respaldos")}
        if(showLock) TextButton(enabled=!busy,onClick=lockAction.run) {com.r0ybt.arachn0de.ui.OfficialIcon(com.r0ybt.arachn0de.R.drawable.official_bloquear); Text("Bloquear")}
    }
    if(showChrome && !settings && session.primary) Text(if(captureAllowed) {if(persistentCaptureAllowed) "Capturas permitidas hasta volver a activar" else "Capturas permitidas en esta sesión"} else "Capturas bloqueadas",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.labelSmall)
    val page: @Composable (()->Unit)->Unit = remember(manager,session) { {close->
        val restored=com.r0ybt.arachn0de.ui.LocalBackupsRestored.current
        fun closePage() {configuration=null;close()}
        fun back() {if(configuration=="passwords" || configuration=="backups") configuration=null else closePage()}
        androidx.activity.compose.BackHandler(enabled=!busy,onBack=::back)
        Surface(Modifier.fillMaxSize().testTag("security-screen"),color=Arachn0deColors.Background) {
            Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=10.dp)) {
                if(configuration!="backups") com.r0ybt.arachn0de.ui.HeaderBar({},title=if(configuration=="passwords") "Contraseñas" else "Seguridad y respaldos",onBack={if(!busy) back()},trailingAction={TextButton(enabled=!busy,onClick=::closePage) {Text("Cerrar")}})
                if(configuration=="backups") Box(Modifier.weight(1f)) {com.r0ybt.arachn0de.ui.BackupsScreen(onBack={configuration=null},onRestored={configuration=null;close();restored()})}
                else if(configuration=="passwords") Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled=!busy,onClick={action="change";notice=null}) {Text("Cambiar contraseña")}
                    if(session.primary && manager.canCreateSecondary) TextButton(enabled=!busy,onClick={action="secondary";notice=null}) {Text("Configurar contraseña señuelo")}
                    Text("No hay recuperación sin contraseña. Los backups necesitan su propia contraseña.")
                    notice?.let {Text(it)}
                } else {
                    Text("El bloqueo cierra el acceso al seguimiento Metro; su estimación guardada se recupera al desbloquear.",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min=maxHeight).padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.Bottom)) {
                            com.r0ybt.arachn0de.ui.CleanupSettings(session.cleanup)
                            Text("Seguridad",style=MaterialTheme.typography.labelLarge,color=Arachn0deColors.TextSecondary)
                            Card(onClick={configuration="timeout"},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("security-timeout"),colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
                                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Lock,null,Modifier.size(20.dp));Column(Modifier.weight(1f)) {Text("Bloqueo automático");Text(AutoLockPreference.options.firstOrNull {it.first==timeout}?.second ?: "Inmediato",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)};Icon(Icons.Default.ChevronRight,null,Modifier.size(20.dp))
                                }
                            }
                            Card(onClick={configuration="captures"},enabled=!busy && session.primary,modifier=Modifier.fillMaxWidth().testTag("security-captures"),colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
                                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Screenshot,null,Modifier.size(20.dp));Column(Modifier.weight(1f)) {Text("Capturas de pantalla");Text(if(captureAllowed) {if(persistentCaptureAllowed) "Permitidas hasta volver a activar" else "Permitidas en esta sesión"} else "Bloqueadas",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)};Icon(Icons.Default.ChevronRight,null,Modifier.size(20.dp))
                                }
                            }
                            Card(onClick={configuration="passwords"},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("security-passwords"),colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
                                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Key,null,Modifier.size(20.dp));Text("Contraseñas",Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null,Modifier.size(20.dp))
                                }
                            }
                            Text("Respaldos",style=MaterialTheme.typography.labelLarge,color=Arachn0deColors.TextSecondary)
                            Card(onClick={configuration="backups"},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("security-backups"),colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
                                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Backup,null,Modifier.size(20.dp));Text("Gestionar respaldos",Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null,Modifier.size(20.dp))
                                }
                            }
                            notice?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                        }
                    }
                }
            }
        }
    }}
    if(configuration=="timeout" || configuration=="captures") ModalBottomSheet(onDismissRequest={if(!busy) configuration=null},containerColor=Arachn0deColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(configuration=="timeout") {
                Text("Bloqueo automático",style=MaterialTheme.typography.titleMedium)
                for((value,label) in AutoLockPreference.options) {
                    TextButton(enabled=!busy,onClick={preferences.edit().putLong(AutoLockPreference.KEY,value).apply();timeout=value;configuration=null},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text((if(timeout==value) "✓ " else "")+label)}
                }
            } else if(session.primary) {
                Text("Capturas de pantalla",style=MaterialTheme.typography.titleMedium)
                Text(if(captureAllowed) {if(persistentCaptureAllowed) "Capturas permitidas hasta volver a activar" else "Capturas permitidas en esta sesión"} else "Capturas bloqueadas")
                TextButton(enabled=!busy,onClick={
                    val wasAllowed=captureAllowed
                    manager.captures.revoke()
                    configuration=null
                    if(!wasAllowed) {persistentCapture=false;action="captures";notice=null}
                    else {
                        busy=true;notice=null
                        scope.launch {
                            try {manager.blockCaptures(session)}
                            catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                            catch(_:Exception) {notice="No se pudo guardar el bloqueo de capturas. Reintenta."}
                            finally {busy=false}
                        }
                    }
                },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(if(captureAllowed) "Bloquear capturas de pantalla" else "Permitir capturas de pantalla")}
            }
        }
    }
    SideEffect {onPageReady(page)}
    if(settings) page {if(!busy) settings=false}
    action?.let { command ->
        fun clear() {old="";next="";confirm="";action=null}
        AlertDialog(onDismissRequest={if(!busy) clear()},properties=androidx.compose.ui.window.DialogProperties(securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn),title={Text(if(command=="captures") "Permitir capturas de pantalla" else if(command=="change") "Cambiar contraseña" else "Contraseña señuelo")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            PasswordField(if(command=="captures") "Contraseña principal" else "Contraseña actual",old) {old=it}
            if(command=="captures") {
                TextButton(enabled=!busy,onClick={persistentCapture=false}) {Text((if(!persistentCapture) "✓ " else "")+"Esta sesión")}
                TextButton(enabled=!busy,onClick={persistentCapture=true}) {Text("Hasta volver a activar")}
            }
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
                        if(command=="captures") manager.authorizeCaptures(session,previous,persistentCapture) else if(command=="change") manager.changePassword(previous,replacement) else manager.createSecondary(previous,replacement)
                        notice=if(command=="captures") {if(persistentCapture) "Capturas permitidas hasta volver a activar." else "Capturas permitidas en esta sesión."} else "Configuración guardada."
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
