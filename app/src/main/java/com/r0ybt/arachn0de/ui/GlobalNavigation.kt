package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class GlobalNavigation(val menu: () -> Unit, val showLock: Boolean)
internal val LocalGlobalNavigation = staticCompositionLocalOf<GlobalNavigation?> { null }
internal val LocalSecurityPage = staticCompositionLocalOf<(@Composable (()->Unit)->Unit)?> { null }
internal val LocalBackupsRestored = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalOpenSecurity = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Existing header slots: leading menu before Back/title, trailing lock at the right edge. */
@Composable internal fun GlobalChromeActions(enabled: Boolean = true, leading: Boolean = false) {
    val navigation = LocalGlobalNavigation.current ?: return
    Row {
        if(leading) IconButton(onClick = navigation.menu, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Menu,"Abrir menú")
        }
        if(!leading && navigation.showLock) ProjectLockButton()
    }
}

@Composable internal fun BackupsScreen(onBack: () -> Unit, onRestored: () -> Unit) {
    var busy by remember {mutableStateOf(false)}
    BackHandler(onBack=onBack)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                GlobalChromeActions(enabled=!busy, leading=true)
                TextButton(enabled=!busy,onClick=onBack) {Text("Volver")}
                Text("Respaldos",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                GlobalChromeActions(enabled=!busy)
            }
            BackupSection(onRestored,onBusyChange={busy=it})
        }
    }
}

/** Keep the source composed for state preservation, but suspend all of its BackHandlers while covered. */
@Composable internal fun NavigationContent(active: Boolean, content: @Composable () -> Unit) {
    val parent=checkNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current)
    val dispatcher=remember {androidx.activity.OnBackPressedDispatcher()}
    val enabled by rememberUpdatedState(active)
    val owner=remember(parent,dispatcher) {object:androidx.activity.OnBackPressedDispatcherOwner {
        override val lifecycle get()=parent.lifecycle
        override val onBackPressedDispatcher get()=dispatcher
    }}
    val callback=remember(parent,dispatcher) {object:androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if(dispatcher.hasEnabledCallbacks()) dispatcher.onBackPressed()
            else {
                isEnabled=false
                try {parent.onBackPressedDispatcher.onBackPressed()} finally {isEnabled=enabled}
            }
        }
    }}
    DisposableEffect(parent,callback) {
        parent.onBackPressedDispatcher.addCallback(parent,callback)
        onDispose {callback.remove()}
    }
    SideEffect {callback.isEnabled=active}
    CompositionLocalProvider(androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides owner,LocalAudioFooterVisible provides (active && LocalAudioFooterVisible.current),content=content)
}

internal val LocalStoryLabels=androidx.compose.runtime.staticCompositionLocalOf<Map<String,String>> {emptyMap()}
