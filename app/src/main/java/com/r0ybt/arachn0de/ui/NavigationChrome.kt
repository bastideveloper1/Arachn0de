package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onProjects: (() -> Unit)? = null, projectsSelected: Boolean = false) {
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = DrawerWidthPolicy.fullWidth(maxWidth)
        Column(Modifier.fillMaxHeight().width(DrawerWidthPolicy.width(maxWidth))
            .testTag("navigation-drawer").semantics {
                paneTitle = "Menú principal"
                stateDescription = if(compact) "Menú a ancho completo" else "Menú lateral"
            }
            .background(Arachn0deColors.SurfaceRaised)
            // Blank parts of the sheet never activate the caller's outside scrim.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar menú")
                }
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.arachn0de_logo), "Logo de Arachn0de",
                    Modifier.size(112.dp).testTag("drawer-logo"), contentScale = ContentScale.Fit)
                Text("Arachn0de", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    color = Arachn0deColors.Accent)
            }
            Text("Principal", style = MaterialTheme.typography.labelLarge, color = Arachn0deColors.TextSecondary)
            fun navigate(action: () -> Unit) { onDismiss(); action() }
            onProjects?.let { NavigationDrawerItem(label = { Text("Proyectos") }, selected = projectsSelected,
                icon = { Icon(Icons.Default.Folder, null) }, onClick = { navigate(it) }, modifier = Modifier.heightIn(min = 48.dp)) }
            onAttention?.let { NavigationDrawerItem(label = { Text("Atención") }, selected = false,
                icon = { Icon(Icons.Default.NotificationsActive, null) }, onClick = { navigate(it) }, modifier = Modifier.heightIn(min = 48.dp)) }
            onCalendar?.let { NavigationDrawerItem(label = { Text("Calendario") }, selected = false,
                icon = { Icon(Icons.Default.CalendarMonth, null) }, onClick = { navigate(it) }, modifier = Modifier.heightIn(min = 48.dp)) }
            onObligations?.let { NavigationDrawerItem(label = { Text("Obligaciones") }, selected = false,
                icon = { Icon(Icons.Default.Payments, null) }, onClick = { navigate(it) }, modifier = Modifier.heightIn(min = 48.dp)) }
            onPeople?.let { NavigationDrawerItem(label = { Text("Personas") }, selected = false,
                icon = { Icon(Icons.Default.People, null) }, onClick = { navigate(it) }, modifier = Modifier.heightIn(min = 48.dp)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Aplicación", style = MaterialTheme.typography.labelLarge, color = Arachn0deColors.TextSecondary)
            onAbout?.let { NavigationDrawerItem(label = { Text("Acerca de") }, selected = false,
                icon = { Icon(Icons.Default.Info, null) }, onClick = { navigate(it) }, modifier = Modifier.heightIn(min = 48.dp)) }
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Arachn0deColors.TextSecondary)
        }
    }
}

@Composable
internal fun HeaderBar(onMenuClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp)
            .background(Arachn0deColors.SurfaceRaised, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))) {
            Icon(Icons.Default.Menu, "Abrir menú", tint = Arachn0deColors.Accent)
        }
        Spacer(Modifier.width(10.dp))
        Text("Arachn0de", color = Arachn0deColors.TextSecondary, fontSize = 16.sp,
            modifier = Modifier.weight(1f))
    }
}
