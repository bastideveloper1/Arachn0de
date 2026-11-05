package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Presentation only: neither switching nor hiding disposes the original controls. */
@Composable internal fun RetainedBarContent(visible:Boolean,content:@Composable ()->Unit) {
    Layout(content=content,modifier=if(visible)Modifier else Modifier.clearAndSetSemantics {}) {children,constraints->
        val measured=children.map {it.measure(constraints.copy(minHeight=0))}
        layout(constraints.maxWidth,if(visible)measured.maxOfOrNull {it.height} ?: 0 else 0) {
            if(visible)measured.forEach {it.placeRelative(0,0)}
        }
    }
}

@Composable internal fun BottomBarSwitch(global:Boolean,onClick:()->Unit) {
    IconButton(onClick=onClick,modifier=Modifier.size(48.dp).testTag("bottom-bar-switch").semantics {
        contentDescription=if(global)"Mostrar barra contextual" else "Mostrar barra global"
    }) {
        NavigationBarIcon(if(global)Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,label=if(global)"Cerrar menú" else "Abrir menú")
        // Icon-only control; the description belongs to its button semantics.
    }
}

@Composable internal fun AlternatingBottomBar(content:@Composable ()->Unit) {
    val state=LocalBottomModulesState.current
    val nav=LocalGlobalNavigation.current
    val visible=LocalAudioFooterVisible.current
    val token=remember {Any()}
    DisposableEffect(state,visible) {
        if(visible)state?.contextBars?.add(token)
        onDispose {state?.contextBars?.remove(token)}
    }
    val switchable=state!=null && nav?.bottomAccess==true && nav.chromeVisible && !state.dialogActive
    RetainedBarContent(!switchable || !state!!.globalBarSelected) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {content()}
            if(switchable) BottomBarSwitch(false) {state!!.globalBarSelected=true}
        }
    }
}

internal data class BottomBarAction(val label:String,val icon:ImageVector,val enabled:Boolean=true,
    val tag:String="",val selected:Boolean=false,val run:()->Unit)

/** Overflow reuses DropdownMenu; every visible or overflow target keeps its normal size. */
@Composable internal fun CompactBarActions(actions:List<BottomBarAction>,iconAbove:Boolean=true,
    style:TextStyle=MaterialTheme.typography.labelSmall,
    item:@Composable RowScope.(BottomBarAction)->Unit={a->NavigationBarAction(a.label,a.icon,a.enabled,a.tag,a.run)}) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val widths=actions.map {a->with(density) {measurer.measure(a.label,style).size.width.toDp()}+
        if(iconAbove)8.dp else 30.dp}
    var expanded by remember {mutableStateOf(false)}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        fun fits(count:Int,overflow:Boolean):Boolean = count==0 ||
            (maxWidth-(if(overflow)48.dp else 0.dp)-.5.dp*(count-1).coerceAtLeast(0))/count >=
                maxOf(48.dp,widths.take(count).maxOrNull() ?: 48.dp)
        val count=if(fits(actions.size,false))actions.size else (actions.size-1 downTo 0).first {fits(it,true)}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            actions.take(count).forEachIndexed {index,a->
                if(index>0)NavigationBarSeparator()
                item(a)
            }
            if(count<actions.size) Box {
                IconButton(onClick={expanded=true},modifier=Modifier.size(48.dp).testTag("bottom-bar-overflow")) {
                    Icon(Icons.Default.MoreVert,"Más acciones")
                }
                DropdownMenu(expanded,{expanded=false},modifier=Modifier.heightIn(max=320.dp)) {
                    actions.drop(count).forEach {a->DropdownMenuItem(text={Text(a.label)},enabled=a.enabled,
                        leadingIcon={NavigationBarIcon(a.icon,label=a.label)},modifier=Modifier.testTag(a.tag),
                        onClick={expanded=false;a.run()})}
                }
            }
        }
    }
}
