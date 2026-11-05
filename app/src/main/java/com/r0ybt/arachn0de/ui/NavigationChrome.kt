package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.ui.theme.BashColors
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.ui.theme.*
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.BuildConfig

internal object DrawerWidthPolicy {
    fun fullWidth(available: Dp) = available < 600.dp
    fun width(available: Dp) = if(fullWidth(available)) available else 360.dp
}

/** Drawer window is edge-to-edge; only interactive content consumes safe insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppIdentityDrawer(onDismiss: () -> Unit, onPeople: (() -> Unit)? = null,
    onAttention: (() -> Unit)? = null, onCalendar: (() -> Unit)? = null,
    onObligations: (() -> Unit)? = null, onAbout: (() -> Unit)? = null,
    onMetro: (() -> Unit)? = null, onSlots: (() -> Unit)? = null, onAppearance: (() -> Unit)? = null, onProjects: (() -> Unit)? = null, projectsSelected: Boolean = false, onTechnologies: (() -> Unit)? = null,onTags:(()->Unit)?=null, onSearch:(()->Unit)?=null, onFavorites:(()->Unit)?=null,onDrafts:(()->Unit)?=null, onSecurity:(()->Unit)?=null, onBackups:(()->Unit)?=null, onQuickCapture:(()->Unit)?=null,onRecurrences:(()->Unit)?=null, onMetroStatistics:(()->Unit)?=null,peopleSelected:Boolean=false,onPomodoro:(()->Unit)?=LocalOpenPomodoro.current,onWireframe:(()->Unit)?=null,onDiagrams:(()->Unit)?=null,safeInsets:WindowInsets=WindowInsets.safeDrawing,experimentalSheet:Boolean=false) {
    BashMenuScope {
        BackHandler(onBack=onDismiss)
        var category by rememberSaveable {mutableStateOf("Inicio")}
        data class Entry(val label:String,val action:(()->Unit)?,val icon:androidx.compose.ui.graphics.vector.ImageVector,val tag:String="",val selected:Boolean=false)
        val entries=when(category) {
            "Inicio"->listOf(
                Entry("Núcleos",onProjects,Icons.Default.Folder,"drawer-projects",projectsSelected),
                Entry("Borradores",onDrafts,Icons.Default.Description),
                Entry("Metro",onMetro,Icons.Default.DirectionsSubway),
                Entry("Antisocial",onPeople,Icons.Default.Person,"drawer-antisocial",peopleSelected),
                Entry("Calendario",onCalendar,Icons.Default.CalendarMonth,"drawer-calendar"),
                Entry("Tragaperras",onSlots,Icons.Default.Casino,"drawer-slots"))
            "Herramientas"->listOf(Entry("Diagramas",onDiagrams,Icons.Default.Image,"drawer-diagrams"),
                Entry("Wireframe",onWireframe,Icons.Default.Image,"drawer-wireframe"),Entry("Pomodoro",onPomodoro,Icons.Default.Timer,"drawer-pomodoro"),
                Entry("Captura rápida",onQuickCapture,Icons.Default.Edit))
            "Organizar"->listOf(Entry("Etiquetas",onTags,Icons.Default.Label),
                Entry("Pagos",onObligations,Icons.Default.Receipt,"drawer-obligations"),Entry("Recurrencias",onRecurrences,Icons.Default.Refresh),
                Entry("Estadísticas de Metro",onMetroStatistics,Icons.Default.BarChart))
            else->listOf(Entry("Apariencia",onAppearance,Icons.Default.Palette),
                Entry("Seguridad y respaldos",onSecurity ?: onBackups,Icons.Default.Security),Entry("Acerca de",onAbout,Icons.Default.Info))
        }.filter {it.action!=null}
        val panel:@Composable (Modifier)->Unit={panelModifier->
        BoxWithConstraints(panelModifier) {
            val headerHeight=64.dp
            Column(Modifier.align(Alignment.BottomCenter).widthIn(max=600.dp).fillMaxWidth().heightIn(max=minOf(720.dp,maxHeight*.94f))
                .testTag("navigation-drawer").semantics {paneTitle="Menú principal";stateDescription="Panel inferior · $category"}
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart=16.dp,topEnd=16.dp))
                .appearancePaint("drawer",Arachn0deColors.Background)
                .pointerInput(Unit) {detectTapGestures {}}
                .windowInsetsPadding((if(experimentalSheet) WindowInsets(0) else safeInsets).only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom))) {
                Box(Modifier.fillMaxWidth()) {
                    ThemeDrawerIdentity(height=headerHeight)
                    if(!experimentalSheet) IconButton(onClick=onDismiss,modifier=Modifier.align(Alignment.TopEnd).padding(end=8.dp).size(48.dp)
                        .background(LocalAppearancePalette.current.surface,androidx.compose.foundation.shape.CircleShape)) {Icon(painterResource(R.drawable.menu_cerrar_menu),"Cerrar menú",tint=Arachn0deColors.Icon)}
                }
                val scroll=key(category) {rememberScrollState()}
                Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(scroll).testTag("drawer-sections")) {
                    @Composable fun entryItem(entry:Entry,modifier:Modifier=Modifier) {
                        AppDrawerItem(label={Text(entry.label,style=if(experimentalSheet) MaterialTheme.typography.labelMedium else LocalTextStyle.current)},selected=entry.selected,icon={
                            val resource=mainMenuIconResource(entry.label)
                            if(resource!=null) Icon(painterResource(resource),
                                if(entry.label=="Metro" || entry.label=="Tragaperras")entry.label else null,
                                Modifier.size(24.dp).then(when(entry.label) {
                                    "Tragaperras" -> Modifier.testTag("drawer-slots-icon")
                                    "Pagos" -> Modifier.testTag("drawer-obligations-icon")
                                    else -> Modifier
                                }),tint=Arachn0deColors.Icon)
                            else LucideIcon(entry.icon,null,Modifier.size(24.dp))
                        },onClick={onDismiss();entry.action?.invoke()},modifier=modifier.testTag(entry.tag.ifEmpty {"menu-item:${entry.label}"}))
                    }
                    if(experimentalSheet) entries.chunked(2).forEach {row->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach {entry->entryItem(entry,Modifier.weight(1f))}
                            if(row.size==1) Spacer(Modifier.weight(1f))
                        }
                    } else entries.forEach {entry->entryItem(entry)}
                    if(category=="Ajustes") Text("v${BuildConfig.VERSION_NAME}",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)
                }
                if(!experimentalSheet) IconButton(onClick=onDismiss,modifier=Modifier.align(Alignment.CenterHorizontally).size(48.dp).testTag("drawer-close-bottom")) {
                    Icon(painterResource(R.drawable.menu_cerrar_menu),"Cerrar menú inferior",tint=Arachn0deColors.Icon)
                }
                HorizontalDivider(color=Arachn0deColors.Outline,thickness=.5.dp)
                BoxWithConstraints(Modifier.fillMaxWidth().testTag("menu-categories")) {
                    val categories=listOf("Inicio" to Icons.Default.Home,"Herramientas" to Icons.Default.Build,"Organizar" to Icons.Default.Label,"Ajustes" to Icons.Default.Settings)
                    val columns=if(maxWidth-(if(experimentalSheet)48.dp else 0.dp)>=360.dp*androidx.compose.ui.platform.LocalDensity.current.fontScale) 4 else 2
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        categories.chunked(columns).forEach {row->Row(Modifier.fillMaxWidth()) {
                            row.forEach {(label,icon)->
                                val selected=category==label
                                Column(Modifier.weight(1f).heightIn(min=56.dp).selectable(selected=selected,role=androidx.compose.ui.semantics.Role.Tab,onClick={category=label})
                                    .testTag("menu-category:$label").padding(horizontal=4.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    Icon(painterResource(requireNotNull(mainMenuIconResource(label))),null,Modifier.size(18.dp),tint=if(selected) Arachn0deColors.Accent else Arachn0deColors.TextSecondary)
                                    Text(label,style=MaterialTheme.typography.labelSmall,color=if(selected) Arachn0deColors.Accent else Arachn0deColors.TextSecondary)
                                    Box(Modifier.padding(top=2.dp).width(20.dp).height(2.dp).background(if(selected) Arachn0deColors.Accent else androidx.compose.ui.graphics.Color.Transparent))
                                }
                            }
                        }}
                    }
                    if(experimentalSheet) IconToggleButton(checked=true,onCheckedChange={onDismiss()},
                        modifier=Modifier.size(48.dp).background(Arachn0deColors.Selection,androidx.compose.foundation.shape.CircleShape)
                            .border(1.dp,Arachn0deColors.Outline,androidx.compose.foundation.shape.CircleShape).testTag("drawer-close-bottom")) {Icon(painterResource(R.drawable.menu_cerrar_menu),"Cerrar menú global",tint=Arachn0deColors.Accent)}
                    }
                }
            }
        }
        }
        if(experimentalSheet) {
            BottomModulesSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
                containerColor=Arachn0deColors.Background,contentColor=Arachn0deColors.TextPrimary) {
                panel(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.8f))
            }
        } else BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(safeInsets.only(WindowInsetsSides.Top))
            .pointerInput(onDismiss) {detectTapGestures {onDismiss()}}) {
            panel(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max=maxHeight))
        }
    }
}

@Composable
internal fun HeaderBar(onMenuClick: () -> Unit, trailingAction: (@Composable () -> Unit)? = null, title: String = "Arachn0de", onBack:(()->Unit)?=null, backDescription:String="Volver", compactLock:Boolean=false,context:HeadingLocation?=LocalReturnHeading.current,showBrandMark:Boolean=true,showTitle:Boolean=true,onContextClick:(()->Unit)?=null,headingContent:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val navigation=LocalGlobalNavigation.current
        if(navigation?.bottomAccess!=true) IconButton(onClick = navigation?.menu ?: onMenuClick, modifier = Modifier.size(48.dp)) {
            LucideIcon(Icons.Default.Menu, "Abrir menú", tint = Arachn0deColors.Accent)
        }
        if(onBack!=null) IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) {LucideIcon(Icons.Default.ArrowBack,backDescription)}
        Spacer(Modifier.width(6.dp))
        if(showTitle && showBrandMark && title=="Arachn0de") OfficialIcon(R.drawable.bw90_brand_mark,"Identidad Arachn0de",size=32.dp,modifier=Modifier.width(64.dp).height(32.dp))
        if(headingContent!=null) Box(Modifier.weight(1f)) {headingContent()}
        else NavigationHeading(title,Modifier.weight(1f),
            style=MaterialTheme.typography.titleMedium.copy(fontSize=16.sp),color=Arachn0deColors.TextSecondary,context=context,titleTag="app-bar-title",showTitle=showTitle,onContextClick=onContextClick)
        trailingAction?.invoke()
        if(compactLock || navigation?.showLock==true) HeaderLockButton()
    }
}

@Composable private fun AppDrawerItem(label:@Composable ()->Unit,selected:Boolean,onClick:()->Unit,
    modifier:Modifier=Modifier,icon:(@Composable ()->Unit)?=null) {
    val bash=bashMenusEnabled()
    NavigationDrawerItem(label=label,selected=selected,onClick=onClick,modifier=modifier.heightIn(min=48.dp),icon=icon,
        colors=if(bash) NavigationDrawerItemDefaults.colors(
            selectedContainerColor=BashColors.Background,unselectedContainerColor=BashColors.Background,
            selectedIconColor=BashColors.Electric,unselectedIconColor=BashColors.Soft,
            selectedTextColor=BashColors.Electric,unselectedTextColor=BashColors.Terminal)
        else NavigationDrawerItemDefaults.colors(
            selectedContainerColor=androidx.compose.ui.graphics.Color.Transparent,unselectedContainerColor=androidx.compose.ui.graphics.Color.Transparent,
            selectedIconColor=Arachn0deColors.Accent,selectedTextColor=Arachn0deColors.Accent,
            unselectedIconColor=Arachn0deColors.Icon,unselectedTextColor=Arachn0deColors.TextPrimary))
}
