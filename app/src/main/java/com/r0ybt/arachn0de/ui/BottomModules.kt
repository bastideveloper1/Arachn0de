package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Presentation leases only. Audio and timer controllers continue to own their sessions. */
internal class BottomModulesState {
    var audioCompact by mutableStateOf(false)
    var pomodoroCompact by mutableStateOf(false)
    var globalBarSelected by mutableStateOf(false)
    val contextBars=mutableStateListOf<Any>()
    val contextualPanels = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()
    val foldedPanels = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()
    private val hosts = mutableStateListOf<Pair<Any, Int>>()
    fun enter(token: Any, depth: Int) { hosts.add(token to depth) }
    fun leave(token: Any) { hosts.removeAll { it.first === token } }
    val dialogActive get()=hosts.any {it.second>1}
    fun owns(token: Any) = hosts.lastOrNull { it.second == hosts.maxOfOrNull { h -> h.second } }?.first === token
    companion object {
        val Saver = listSaver<BottomModulesState, Boolean>(
            save = { listOf(it.audioCompact, it.pomodoroCompact,it.globalBarSelected) },
            restore = { BottomModulesState().apply { audioCompact=it[0];pomodoroCompact=it[1];globalBarSelected=it.getOrNull(2) ?: false } })
    }
}

internal val LocalBottomModulesActionScope = staticCompositionLocalOf<kotlinx.coroutines.CoroutineScope?> { null }
internal val LocalBottomModulesState = staticCompositionLocalOf<BottomModulesState?> { null }
internal val LocalBottomModulesContent = staticCompositionLocalOf<(@Composable (Boolean) -> Unit)?> { null }
internal val LocalBottomViewportHeight = staticCompositionLocalOf {androidx.compose.ui.unit.Dp.Unspecified}
private val LocalBottomModulesDepth = staticCompositionLocalOf { 0 }
@Composable internal fun bottomModulesInDialog() = LocalBottomModulesDepth.current>1

@Composable internal fun rememberBottomModulesState() = rememberSaveable(saver=BottomModulesState.Saver) { BottomModulesState() }

/** Nested windows take the same footer, rather than covering or duplicating it. */
@Composable private fun BottomModulesHost(content: @Composable (@Composable () -> Unit) -> Unit) {
    val state=LocalBottomModulesState.current
    val token=remember { Any() }
    val depth=LocalBottomModulesDepth.current+1
    val visible=LocalAudioFooterVisible.current
    DisposableEffect(state,visible) {
        if(visible) state?.enter(token,depth)
        onDispose { state?.leave(token) }
    }
    val footer=LocalBottomModulesContent.current
    val density=LocalDensity.current
    val compact=WindowInsets.ime.getBottom(density)>0 || LocalConfiguration.current.screenHeightDp<400
    CompositionLocalProvider(LocalBottomModulesDepth provides depth) {
        content {
            if(visible && state?.owns(token)==true) BashTraditionalModules { footer?.invoke(compact) }
        }
    }
}

/** Insets are already owned by AppSafeArea, or by the containing dialog window. */
@Composable internal fun BottomModulesFrame(modifier: Modifier=Modifier, content: @Composable ColumnScope.() -> Unit) {
    BottomModulesHost { footer ->
        BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
            CompositionLocalProvider(LocalBottomViewportHeight provides maxHeight) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth(),content=content)
                    footer()
                }
            }
        }
    }
}

/** Full-screen editors and bottom dialogs share the global footer and its height reservation. */
@Composable internal fun BottomModulesDialog(onDismissRequest: () -> Unit,
    properties: DialogProperties=DialogProperties(usePlatformDefaultWidth=false), content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest,properties=properties) {
        BottomModulesHost { footer ->
            val bottom=WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            Column(Modifier.fillMaxSize().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(bottom),contentAlignment=androidx.compose.ui.Alignment.Center) { content() }
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom))) { footer() }
            }
        }
    }
}

/** Material sheets use the same ownership rule; their own window supplies safe insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BottomModulesSheet(onDismissRequest: () -> Unit,
    sheetState: SheetState=rememberModalBottomSheetState(), containerColor: Color=Arachn0deColors.Surface,
    contentColor: Color=Arachn0deColors.TextPrimary, tonalElevation: androidx.compose.ui.unit.Dp=0.dp,
    content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest=onDismissRequest,sheetState=sheetState,sheetMaxWidth=androidx.compose.ui.unit.Dp.Unspecified,
        containerColor=containerColor,contentColor=contentColor,tonalElevation=tonalElevation) {
        BottomModulesHost { footer ->
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f,fill=false).fillMaxWidth(),content=content)
                footer()
            }
        }
    }
}

/** Exactly one edge per persistent module, without spacer bands or per-screen offsets. */
@Composable internal fun PersistentModuleBoundary(tag: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        HorizontalDivider(thickness=1.dp,color=Arachn0deColors.Outline)
        content()
    }
}

/** A folded editor occupies a real row above the persistent bars, never an overlay. */
@Composable internal fun FoldedBottomPanel(content: @Composable () -> Unit) {
    val state=LocalBottomModulesState.current ?: return
    val token=remember { Any() }
    val current by rememberUpdatedState(content)
    val visible=LocalAudioFooterVisible.current
    DisposableEffect(state,visible) {
        if(visible) state.foldedPanels.add(token to { current() })
        onDispose {state.foldedPanels.removeAll {it.first===token}}
    }
}

@Composable internal fun FoldedBottomPanels() {
    LocalBottomModulesState.current?.foldedPanels?.forEach { (token,content) -> key(token) {content()} }
}

/** Context controls occupy measured footer space instead of covering scrollable cards. */
@Composable internal fun ContextualBottomPanel(content:@Composable ()->Unit) {
    val state=LocalBottomModulesState.current
    val visible=LocalAudioFooterVisible.current
    val token=remember {Any()}
    val current by rememberUpdatedState(content)
    val currentVisible by rememberUpdatedState(visible)
    val sourceLocals by rememberUpdatedState(currentCompositionLocalContext)
    // Move the same composition back to its source while a dialog owns the footer.
    // This keeps editor drafts and the dialog itself alive during ownership transfer.
    val retained=remember {movableContentOf {CompositionLocalProvider(sourceLocals) {current()}}}
    DisposableEffect(state,visible) {
        if(visible) state?.contextualPanels?.add(token to {if(currentVisible && !state.dialogActive)retained()})
        onDispose {state?.contextualPanels?.removeAll {it.first===token}}
    }
    if(state==null && visible) NavigationBarFrame("contextual-bottom-bar") {retained()}
    else if(!visible || state?.dialogActive==true) {
        androidx.compose.ui.layout.Layout(content={retained()}) {children,constraints->
            children.forEach {it.measure(constraints.copy(minWidth=0,minHeight=0))}
            layout(0,0) {}
        }
    }
}

@Composable internal fun ContextualBottomPanels() {
    if(bottomModulesInDialog()) return
    LocalBottomModulesState.current?.contextualPanels?.forEach {(token,content)->
        key(token) {NavigationBarFrame("contextual-bottom-bar",content)}
    }
}
