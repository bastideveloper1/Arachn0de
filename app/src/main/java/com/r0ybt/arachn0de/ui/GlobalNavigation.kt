package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import androidx.compose.ui.unit.dp

// Experimental presentation switch; actions and the previous presentation remain available.
internal const val EXPERIMENTAL_BOTTOM_GLOBAL_MENU=true
internal class GlobalMenuGate {
    private val blocked=mutableStateListOf<Any>()
    val enabled get()=blocked.isEmpty()
    fun block(token:Any) {blocked.add(token)}
    fun release(token:Any) {blocked.remove(token)}
}
internal data class GlobalNavigation(val menu: () -> Unit, val showLock: Boolean, val bottomAccess:Boolean=false,
    val gate:GlobalMenuGate?=null, val search:(()->Unit)?=null, val actions:GlobalBarActions?=null,val chromeVisible:Boolean=true)
internal val LocalGlobalNavigation = staticCompositionLocalOf<GlobalNavigation?> { null }
internal val LocalSecurityPage = staticCompositionLocalOf<(@Composable (()->Unit)->Unit)?> { null }
internal val LocalBackupsRestored = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalOpenSecurity = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Existing header slots: leading menu before Back/title, trailing lock at the right edge. */
@Composable internal fun GlobalChromeActions(enabled: Boolean = true, leading: Boolean = false) {
    val navigation = LocalGlobalNavigation.current ?: return
    if(!navigation.chromeVisible)return
    if(leading) {
        val token=remember {Any()}
        DisposableEffect(navigation.gate,enabled) {
            if(!enabled) navigation.gate?.block(token)
            onDispose {navigation.gate?.release(token)}
        }
    }
    BashMenuScope {
    Row {
        if(leading && !navigation.bottomAccess) IconButton(onClick = navigation.menu, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Menu,"Abrir menú")
        }
        if(!leading && navigation.showLock) HeaderLockButton()
    }
    }
}

/** Context actions register only while their screen is visible. */
internal class GlobalBarActions {
    val sorting = mutableStateMapOf<Any, GlobalSecondaryAction>()
    val searching = mutableStateMapOf<Any, () -> Unit>()
}

internal data class GlobalSecondaryAction(val filter:Boolean,val run:()->Unit)

@Composable internal fun RegisterGlobalSort(enabled:Boolean, filter:Boolean=false,onSort:()->Unit) {
    val actions=LocalGlobalNavigation.current?.actions
    val visible=LocalAudioFooterVisible.current
    val token=remember {Any()}
    val current by rememberUpdatedState(onSort)
    DisposableEffect(actions,visible,enabled,filter) {
        if(visible && enabled) actions?.sorting?.put(token,GlobalSecondaryAction(filter) {current()})
        onDispose {actions?.sorting?.remove(token)}
    }
}

/** Legacy contextual slots are now supplied by the shared fixed footer. */
@Composable internal fun GlobalMenuAccess(navigation:GlobalNavigation?=LocalGlobalNavigation.current) = Unit

@Composable internal fun GlobalBottomBar() {
    val navigation=LocalGlobalNavigation.current ?: return
    if(!navigation.chromeVisible || !navigation.bottomAccess || bottomModulesInDialog()) return
    val enabled=navigation.gate?.enabled!=false
    val sort=navigation.actions?.sorting?.values?.lastOrNull()
    val lock=LocalProjectLock.current
    val state=LocalBottomModulesState.current
    val hasContext=state?.contextBars?.isNotEmpty()==true
    if(hasContext && state?.globalBarSelected!=true)return
    NavigationBarFrame("global-bottom-bar") {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                CompactBarActions(listOf(
                    BottomBarAction("Bloquear",Icons.Default.LockOpen,lock?.enabled==true) {lock?.run?.invoke()},
                    BottomBarAction(if(sort?.filter==true)"Filtrar" else "Ordenar",
                        if(sort?.filter==true)Icons.Default.FilterAlt else Icons.Default.SwapVert,enabled && sort!=null) {sort?.run?.invoke()},
                    BottomBarAction("Buscar",Icons.Default.Search,enabled && navigation.search!=null) {navigation.search?.invoke()},
                    BottomBarAction("Menú",Icons.Default.Menu,enabled,"global-menu-access",run=navigation.menu)
                ))
            }
            if(hasContext)BottomBarSwitch(true) {state!!.globalBarSelected=false}
        }
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
                NavigationHeading("Respaldos",Modifier.weight(1f))
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

/** Omit the optional slot if it would enlarge the existing row or shrink a target below 48dp. */
@Composable internal fun globalMenuFits(width:androidx.compose.ui.unit.Dp,labels:List<String>,
    iconAbove:Boolean=false,style:androidx.compose.ui.text.TextStyle=MaterialTheme.typography.labelSmall,
    spacing:androidx.compose.ui.unit.Dp=0.dp,iconSpace:androidx.compose.ui.unit.Dp=22.dp,
    textPadding:androidx.compose.ui.unit.Dp=8.dp,verticalPadding:androidx.compose.ui.unit.Dp=8.dp,
    iconSizes:List<androidx.compose.ui.unit.Dp> = labels.map {if(iconAbove)18.dp else 0.dp}):Boolean {
    val density=androidx.compose.ui.platform.LocalDensity.current
    val measurer=androidx.compose.ui.text.rememberTextMeasurer()
    if(labels.isEmpty()) return false
    val oldCell=(width-spacing*(labels.size-1))/labels.size
    val newCell=(width-spacing*(labels.size+1)-57.dp)/labels.size
    if(newCell<48.dp) return false
    fun rowHeight(cell:androidx.compose.ui.unit.Dp,after:Boolean):Int {
        val textWidth=with(density) {(cell-textPadding-if(iconAbove)0.dp else iconSpace).toPx().let {
            (if(after) kotlin.math.floor(it).toInt()-1 else kotlin.math.ceil(it).toInt()).coerceAtLeast(1)
        }}
        val height=labels.indices.maxOf {index->
            measurer.measure(labels[index],style,constraints=androidx.compose.ui.unit.Constraints(maxWidth=textWidth)).size.height+
                with(density) {(verticalPadding+iconSizes[index]).roundToPx()}
        }
        return maxOf(with(density) {48.dp.roundToPx()},height)
    }
    return rowHeight(newCell,true)<=rowHeight(oldCell,false)
}
