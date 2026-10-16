package com.r0ybt.arachn0de

import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import com.r0ybt.arachn0de.ui.AppSafeArea
import com.r0ybt.arachn0de.ui.AppRoot
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.security.SecurityControls
import com.r0ybt.arachn0de.security.UnlockScreen

class MainActivity:ComponentActivity() {
    private var incomingConsumed=false
    private val handledShares=mutableSetOf<String>()
    private fun receiveShare(incoming:android.content.Intent) {
        if(!com.r0ybt.arachn0de.sharing.SHARE_ENABLED) return
        if(incoming.action!=android.content.Intent.ACTION_SEND && incoming.action!=android.content.Intent.ACTION_SEND_MULTIPLE) return
        incomingConsumed=true
        val supplied=runCatching {incoming.getStringExtra("com.r0ybt.arachn0de.RECEIVED_SHARE")}.getOrNull()
        if(supplied!=null && supplied in handledShares) return
        val id=java.util.UUID.randomUUID().toString()
        handledShares.add(id);incoming.putExtra("com.r0ybt.arachn0de.RECEIVED_SHARE",id)
        val manager=(application as Arachn0deApplication).security
        val expected=manager.current.value
        if(expected==null) {
            android.widget.Toast.makeText(this,"Desbloquea Arachn0de y vuelve a compartir el contenido.",android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val input=try {com.r0ybt.arachn0de.sharing.SharedContent.read(incoming)} catch(_:Exception) {
            android.widget.Toast.makeText(this,"Envío no admitido: texto de hasta 64 KiB o una imagen PNG/JPEG de hasta 20 MiB.",android.widget.Toast.LENGTH_LONG).show();return
        }
        lifecycleScope.launch {
            manager.foreground()
            if(manager.current.value!==expected) {
                android.widget.Toast.makeText(this@MainActivity,"Desbloquea Arachn0de y vuelve a compartir el contenido.",android.widget.Toast.LENGTH_LONG).show();return@launch
            }
            expected.scope.launch {try {
                expected.sharedCaptures.receive(id,input)
            } catch(c:CancellationException) {throw c} catch(_:Exception) {
                withContext(Dispatchers.Main) {android.widget.Toast.makeText(this@MainActivity,"No se pudo recibir el contenido. Usa texto de hasta 64 KiB o PNG/JPEG de hasta 20 MiB.",android.widget.Toast.LENGTH_LONG).show()}
            }}
        }
    }
    private var privateRegistry:androidx.compose.runtime.saveable.SaveableStateRegistry?=null
    override fun onSaveInstanceState(outState:Bundle) {
        val manager=(application as Arachn0deApplication).security
        if(manager.current.value!=null) manager.savedUi=privateRegistry?.performSave()
        // The private map remains in process memory; nothing is added to the Android Bundle.
        outState.putBoolean("share_consumed",incomingConsumed)
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        super.onPause()
    }
    override fun onStop() {
        if(!isChangingConfigurations) (application as Arachn0deApplication).security.background()
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { (application as Arachn0deApplication).security.foreground() }
    }
    override fun onDestroy() {
        (application as Arachn0deApplication).security.captures.detach(window,closingSession=!isChangingConfigurations)
        super.onDestroy()
    }
    override fun onNewIntent(intent:android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent);incomingConsumed=false
        receiveShare(intent)
        if(intent.getBooleanExtra("metro",false)) { com.r0ybt.arachn0de.metro.MetroNavigation.open();intent.removeExtra("metro") }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        (application as Arachn0deApplication).security.captures.attach(window)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(Color.TRANSPARENT))
        if(intent.getBooleanExtra("metro",false)) {com.r0ybt.arachn0de.metro.MetroNavigation.open();intent.removeExtra("metro")}
        val app=application as Arachn0deApplication
        incomingConsumed=savedInstanceState?.getBoolean("share_consumed",false) ?: false
        if(!incomingConsumed) receiveShare(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                app.security.current.collectLatest { session ->
                    if(session!=null) try { while(session.nodes.recurrence.materializeDue()) yield() }
                    catch(cancelled:CancellationException) {throw cancelled}
                    catch(_:Exception) {android.widget.Toast.makeText(this@MainActivity,"No se pudieron generar las recurrencias. Se reintentará al volver a abrir.",android.widget.Toast.LENGTH_LONG).show()}
                }
            }
        }
        setContent {
            val session by app.security.current.collectAsState()
            val backgrounded by app.security.backgrounded.collectAsState()
            val visible=session
            Arachn0deTheme(locked=visible==null || backgrounded) {
                AppSafeArea {
                    if(visible==null) UnlockScreen(app.security)
                    else key(visible.access.id) {
                        val initialProjects=remember(visible) {visible.initialProjects.also {visible.initialProjects=null}}
                        // Private editor/navigation state remains in live memory only. It is never
                        // handed to Android's persistent saved-instance-state Bundle while locked.
                        val registry=remember(visible) {SaveableStateRegistry(app.security.savedUi) {true}}
                        DisposableEffect(visible) {
                            privateRegistry=registry
                            onDispose {
                                // onSaveInstanceState captured providers before child disposal.
                                // Saving here would overwrite that map after providers unregister.
                                if(privateRegistry===registry) privateRegistry=null
                            }
                        }
                        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                            Box(Modifier.fillMaxSize()) {
                                Column(Modifier.fillMaxSize().then(if(backgrounded) Modifier.clearAndSetSemantics {} else Modifier)) {
                                    var projectActions by remember {mutableStateOf(false)}
                                    var lockAction by remember {mutableStateOf<com.r0ybt.arachn0de.ui.ProjectLockAction?>(null)}
                                    var securityPage by remember {mutableStateOf<(@androidx.compose.runtime.Composable (()->Unit)->Unit)?>(null)}
                                    var openSecurity by remember {mutableStateOf<(() -> Unit)?>(null)}
                                    CompositionLocalProvider(
                                        com.r0ybt.arachn0de.ui.LocalProjectLock provides lockAction,
                                        com.r0ybt.arachn0de.ui.LocalOpenSecurity provides openSecurity,
                                        com.r0ybt.arachn0de.ui.LocalSecurityPage provides securityPage,
                                        com.r0ybt.arachn0de.ui.LocalProjectActionsVisibility provides {projectActions=it},
                                    ) {
                                        SecurityControls(app.security,visible,showLock=!projectActions,onLockReady={lockAction=it},showChrome=false,onSettingsReady={openSecurity=it},onPageReady={securityPage=it})
                                        Box(Modifier.weight(1f)) {AppRoot(visible.projects,visible.nodes,initialProjects=initialProjects)}
                                    }
                                }
                                // Keep editors/reservations during the configured grace period.
                                // The opaque overlay prevents access until foreground verifies time.
                                if(backgrounded) androidx.compose.material3.Surface(Modifier.fillMaxSize().then(
                                    Modifier.pointerInput(Unit) {awaitPointerEventScope {while(true) {awaitPointerEvent().changes.forEach {it.consume()}}}})) {
                                    Box(contentAlignment=androidx.compose.ui.Alignment.Center) {androidx.compose.material3.Text("Arachn0de")}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
