package com.r0ybt.arachn0de

import android.os.Bundle
import android.graphics.Color
import android.view.WindowManager
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
    private var privateRegistry:androidx.compose.runtime.saveable.SaveableStateRegistry?=null
    override fun onSaveInstanceState(outState:Bundle) {
        val manager=(application as Arachn0deApplication).security
        if(manager.current.value!=null) manager.savedUi=privateRegistry?.performSave()
        // The private map remains in process memory; nothing is added to the Android Bundle.
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        if(!isChangingConfigurations) (application as Arachn0deApplication).security.background()
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { (application as Arachn0deApplication).security.foreground() }
    }
    override fun onNewIntent(intent:android.content.Intent) {
        super.onNewIntent(intent)
        if(intent.getBooleanExtra("metro",false)) { com.r0ybt.arachn0de.metro.MetroNavigation.open();intent.removeExtra("metro") }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(Color.TRANSPARENT))
        if(intent.getBooleanExtra("metro",false)) {com.r0ybt.arachn0de.metro.MetroNavigation.open();intent.removeExtra("metro")}
        val app=application as Arachn0deApplication
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
                                    SecurityControls(app.security,visible)
                                    Box(Modifier.weight(1f)) {AppRoot(visible.projects,visible.nodes)}
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
