package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
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
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.BuildConfig

internal object DrawerWidthPolicy {
    fun fullWidth(available: Dp) = available < 600.dp
    fun width(available: Dp) = if(fullWidth(available)) available else 360.dp
}

/** Lives inside AppSafeArea: available constraints already exclude safe drawing insets. */
@Composable
internal fun AppIdentityDrawer(onDismiss: () -> Unit, onPeople: (() -> Unit)? = null,
    onAttention: (() -> Unit)? = null, onCalendar: (() -> Unit)? = null,
    onObligations: (() -> Unit)? = null, onAbout: (() -> Unit)? = null,
    onMetro: (() -> Unit)? = null, onGame: (() -> Unit)? = null, onAppearance: (() -> Unit)? = null, onProjects: (() -> Unit)? = null, projectsSelected: Boolean = false, onTechnologies: (() -> Unit)? = null,onTags:(()->Unit)?=null, onSearch:(()->Unit)?=null, onFavorites:(()->Unit)?=null, onSecurity:(()->Unit)?=null, onBackups:(()->Unit)?=null, onQuickCapture:(()->Unit)?=null,onRecurrences:(()->Unit)?=null) {
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = DrawerWidthPolicy.fullWidth(maxWidth)
        val pinPrimary = maxHeight >= 400.dp
        Column(Modifier.fillMaxHeight().width(DrawerWidthPolicy.width(maxWidth))
            .testTag("navigation-drawer").semantics {
                paneTitle = "Menú principal"
                stateDescription = if(compact) "Menú a ancho completo" else "Menú lateral"
            }
            .background(Arachn0deColors.Background)
            // Blank parts of the sheet never activate the caller's outside scrim.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .then(if(pinPrimary) Modifier else Modifier.verticalScroll(rememberScrollState())).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    LucideIcon(Icons.Default.Close, contentDescription = "Cerrar menú")
                }
            }
            OfficialIcon(R.drawable.bw90_brand_logo,"Logo de Arachn0de",size=96.dp,modifier=Modifier.fillMaxWidth().height(88.dp).testTag("drawer-logo"))
            fun navigate(action: () -> Unit) { onDismiss(); action() }
            onSearch?.let { NavigationDrawerItem(label={Text("Buscar")},selected=false,icon={LucideIcon(Icons.Default.Search,null,Modifier.size(24.dp))},onClick={navigate(it)},modifier=Modifier.heightIn(min=48.dp).testTag("drawer-search")) }
            onProjects?.let { NavigationDrawerItem(label = { Text("Proyectos") }, selected = projectsSelected,
                icon = { OfficialIcon(R.drawable.bw90_project,size=24.dp) }, onClick = { navigate(it) }, modifier = Modifier.heightIn(min = 48.dp)) }
            onMetro?.let { NavigationDrawerItem(label = { Text("Metro") }, selected = false,
                 onClick = { navigate(it) }, modifier = Modifier.heightIn(min = 48.dp)) }
            Column(Modifier.fillMaxWidth().then(if(pinPrimary) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier).testTag("drawer-sections")) {
            if(onFavorites!=null || onAttention!=null || onObligations!=null || onCalendar!=null || onRecurrences!=null) DrawerSection("Seguimiento") {
                onRecurrences?.let {NavigationDrawerItem(label={Text("Recurrencias")},selected=false,icon={LucideIcon(Icons.Default.Refresh,null,Modifier.size(24.dp))},onClick={navigate(it)})}
                onFavorites?.let {NavigationDrawerItem(label={Text("Favoritos")},selected=false,icon={LucideIcon(Icons.Default.Star,null,Modifier.size(24.dp))},onClick={navigate(it)})}
                onAttention?.let {NavigationDrawerItem(label={Text("Avisos")},selected=false,icon={LucideIcon(Icons.Default.NotificationsActive,null,Modifier.size(24.dp))},onClick={navigate(it)})}
                onObligations?.let {NavigationDrawerItem(label={Text("Obligaciones")},selected=false,icon={Icon(painterResource(R.drawable.lucide_receipt),null,Modifier.size(24.dp).testTag("drawer-obligations-icon"),tint=Arachn0deColors.TextPrimary)},onClick={navigate(it)},modifier=Modifier.heightIn(min=48.dp).testTag("drawer-obligations"))}
                onCalendar?.let {NavigationDrawerItem(label={Text("Calendario")},selected=false,icon={LucideIcon(Icons.Default.CalendarMonth,null,Modifier.size(24.dp))},onClick={navigate(it)})}
            }
            HorizontalDivider(color=Arachn0deColors.Outline,thickness=.5.dp)
            if(onPeople!=null || onTechnologies!=null || onTags!=null) DrawerSection("Recursos") {
                onPeople?.let {NavigationDrawerItem(label={Text("Personas")},selected=false,icon={LucideIcon(Icons.Default.Groups,null,Modifier.size(24.dp))},onClick={navigate(it)})}
                onTechnologies?.let {NavigationDrawerItem(label={Text("Tecnologías")},selected=false,icon={LucideIcon(Icons.Default.Code,null,Modifier.size(24.dp))},onClick={navigate(it)})}
                onTags?.let {NavigationDrawerItem(label={Text("Etiquetas")},selected=false,icon={LucideIcon(Icons.Default.Label,null,Modifier.size(24.dp))},onClick={navigate(it)})}
            }
            HorizontalDivider(color=Arachn0deColors.Outline,thickness=.5.dp)
            if(onSecurity!=null || onBackups!=null || onAppearance!=null) DrawerSection("Ajustes") {
                onSecurity?.let {NavigationDrawerItem(label={Text("Seguridad")},selected=false,icon={LucideIcon(Icons.Default.Security,null,Modifier.size(24.dp))},onClick={navigate(it)})}
                onBackups?.let {NavigationDrawerItem(label={Text("Respaldo")},selected=false,icon={LucideIcon(Icons.Default.Backup,null,Modifier.size(24.dp))},onClick={navigate(it)})}
                onAppearance?.let {NavigationDrawerItem(label={Text("Apariencia")},selected=false,icon={LucideIcon(Icons.Default.Palette,null,Modifier.size(24.dp))},onClick={navigate(it)})}
            }
            if(onQuickCapture!=null || onAbout!=null || onGame!=null) HorizontalDivider(Modifier.padding(vertical=4.dp))
            onQuickCapture?.let {NavigationDrawerItem(label={Text("Captura rápida")},selected=false,onClick={navigate(it)})}
            onAbout?.let {NavigationDrawerItem(label={Text("Acerca de")},selected=false,icon={LucideIcon(Icons.Default.Info,null,Modifier.size(24.dp))},onClick={navigate(it)})}
            onGame?.let {NavigationDrawerItem(label={Text("Juego")},selected=false,onClick={navigate(it)})}
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Arachn0deColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun DrawerSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
            .testTag("drawer-section:$title")
            .semantics { stateDescription=if(expanded) "Expandida" else "Contraída" }
            .clickable(role=androidx.compose.ui.semantics.Role.Button) { expanded=!expanded }
            .padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=Arachn0deColors.TextSecondary)
            LucideIcon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null,Modifier.size(20.dp),tint=Arachn0deColors.TextSecondary)
        }
        if(expanded) Column(Modifier.padding(start=8.dp),content=content)
    }
}

@Composable
internal fun HeaderBar(onMenuClick: () -> Unit, trailingAction: (@Composable () -> Unit)? = null, title: String = "Arachn0de", onBack:(()->Unit)?=null, backDescription:String="Volver") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val navigation=LocalGlobalNavigation.current
        IconButton(onClick = navigation?.menu ?: onMenuClick, modifier = Modifier.size(48.dp)) {
            LucideIcon(Icons.Default.Menu, "Abrir menú", tint = Arachn0deColors.Accent)
        }
        if(onBack!=null) IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) {LucideIcon(Icons.Default.ArrowBack,backDescription)}
        Spacer(Modifier.width(6.dp))
        if(title=="Arachn0de") OfficialIcon(R.drawable.bw90_brand_mark,"Identidad Arachn0de",size=32.dp,modifier=Modifier.width(64.dp).height(32.dp))
        Text(title, color = Arachn0deColors.TextSecondary, fontSize = 16.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("app-bar-title"))
        trailingAction?.invoke()
        if(navigation?.showLock==true) ProjectLockButton()
    }
}
