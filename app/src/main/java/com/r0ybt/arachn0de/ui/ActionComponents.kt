package com.r0ybt.arachn0de.ui
import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Existing primary controls share contrast and interaction states; callers retain their geometry. */
@Composable
internal fun PrimaryControlButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val palette = com.r0ybt.arachn0de.ui.theme.Arachn0deColors
    Button(onClick=onClick, modifier=modifier, enabled=enabled,
        shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp), elevation=null,
        contentPadding=PaddingValues(4.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,palette.TextSecondary.copy(alpha=if(enabled) .8f else .35f)),
        colors=ButtonDefaults.buttonColors(containerColor=palette.SurfaceRaised,contentColor=palette.TextPrimary,
            disabledContainerColor=palette.Surface,disabledContentColor=palette.Disabled),content=content)
}

/** Small action vocabulary; navigation, state controls and links retain their own semantics. */
@Composable
internal fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
internal fun DestructiveAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier.heightIn(min = 48.dp).semantics { stateDescription = "Acción destructiva" }) {
        LucideIcon(Icons.Default.Delete, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp)); Text(label)
    }
}

/** Existing overflow uses a scrollable modal action list, including on compact phones. */
@Composable
internal fun ActionMenu(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
internal fun ActionMenuItem(label: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(text = { Text(label, color = color) }, leadingIcon = { LucideIcon(icon, null, tint = color) },
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp).semantics {
            role = Role.Button
            if (destructive) stateDescription = "Acción destructiva"
        })
}

/** Shared presentation vocabulary for card and station menus; callbacks and guards stay at the caller. */
internal data class ContextAction(val label:String,val icon:ImageVector,val onClick:()->Unit,
    val modifier:Modifier=Modifier,val enabled:Boolean=true,val destructive:Boolean=false,val priority:Boolean=false)
internal class ContextActions {
    val items=mutableListOf<ContextAction>()
    fun action(label:String,icon:ImageVector,onClick:()->Unit,modifier:Modifier=Modifier,
        enabled:Boolean=true,destructive:Boolean=false,priority:Boolean=false) {items+=ContextAction(label,icon,onClick,modifier,enabled,destructive,priority)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextActionMenu(title:String,onDismiss:()->Unit,header:(@Composable ()->Unit)?=null,bottomAligned:Boolean=false,inline:Boolean=false,showCancel:Boolean=true,content:ContextActions.()->Unit) {
    val actions=ContextActions().apply(content).items
    val priorityLabels=listOf("Copiar","Copiar este elemento","Mover a…","Copiar texto con descendientes")
    val priorities=actions.filter {(it.priority || it.label in priorityLabels) && !it.destructive}.sortedBy {priorityLabels.indexOf(it.label)}
    val destructive=actions.filter {it.destructive}
    val other=actions.filter {it !in priorities && !it.destructive}
    val organization=other.filter {it.label.startsWith("Convertir") || it.label in listOf("Seleccionar","Responsables","Compartir como imagen","Guardar imagen","Guardar como plantilla","Modo Sprint","Desactivar Modo Sprint","Cambiar estado","Nuevo elemento","Subir","Bajar")}
    val secondary=other.filter {it !in organization}
    val menuContent:@Composable ()->Unit={
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth >= 300.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale) 2 else 1
            Column(Modifier.fillMaxWidth().heightIn(max=if(inline) maxHeight else maxHeight*.85f)
                .then(if(inline) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal=12.dp)
                .then(Modifier.semantics {paneTitle=title}),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(!inline) Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if(other.isNotEmpty() || header!=null) Column(Modifier.then(if(inline) Modifier else Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()))
                    .then(Modifier.semantics {testTag="context-secondary"}),verticalArrangement=if(bottomAligned) Arrangement.Bottom else Arrangement.Top) {
                    header?.invoke()
                    if(secondary.isNotEmpty()) ContextActionRows(secondary,columns,bottomAligned=bottomAligned)
                    if(secondary.isNotEmpty() && organization.isNotEmpty()) HorizontalDivider(color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Outline,modifier=Modifier.padding(vertical=4.dp))
                    if(organization.isNotEmpty()) ContextActionRows(organization,columns,bottomAligned=bottomAligned)
                }
                if(priorities.isNotEmpty()) ContextActionRows(priorities,columns,emphasized=true,bottomAligned=bottomAligned)
                if(destructive.isNotEmpty()) HorizontalDivider(color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Outline,modifier=Modifier.padding(top=4.dp))
                ContextActionRows(destructive+(if(showCancel) listOf(ContextAction("Cancelar",androidx.compose.material.icons.Icons.Default.Close,onDismiss)) else emptyList()),columns)
            }
        }
    }
    if(inline) menuContent() else ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface,
        contentColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary,tonalElevation=0.dp) {menuContent()}

}

@Composable
internal fun ContextActionRows(actions:List<ContextAction>,columns:Int,emphasized:Boolean=false,bottomAligned:Boolean=false) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp,if(bottomAligned) Alignment.Bottom else Alignment.Top)) {
        actions.chunked(columns).forEach {row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=if(bottomAligned) Alignment.Bottom else Alignment.Top) {
                row.forEach {action ->
                    val color=if(action.destructive) MaterialTheme.colorScheme.error else com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary
                    val modifier=Modifier.weight(1f).heightIn(min=48.dp).then(action.modifier).semantics {
                        testTag="context-action:${action.label}"
                        if(action.destructive) stateDescription="Acción destructiva"
                    }
                    val content:@Composable RowScope.()->Unit = {
                        ContextActionIcon(action.icon,Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(action.label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                    }
                    if(emphasized) Button(action.onClick,modifier=modifier,enabled=action.enabled,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        contentPadding=PaddingValues(8.dp),elevation=null,
                        colors=ButtonDefaults.buttonColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.ControlSurface,contentColor=color),content=content)
                    else TextButton(action.onClick,modifier=modifier,enabled=action.enabled,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        contentPadding=PaddingValues(8.dp),colors=ButtonDefaults.textButtonColors(contentColor=color),content=content)
                }
            }
        }
    }
}

/** Keep these mappings local: other screens retain their existing icon presentation. */
@Composable
private fun ContextActionIcon(icon:ImageVector,modifier:Modifier) {
    val resource=when(icon.name.substringAfterLast('.')) {
        "LocationOn" -> com.r0ybt.arachn0de.R.drawable.lucide_navigation
        "Add" -> com.r0ybt.arachn0de.R.drawable.lucide_plus
        "ContentCopy","CopyAll" -> com.r0ybt.arachn0de.R.drawable.lucide_copy
        "DriveFileMove" -> com.r0ybt.arachn0de.R.drawable.lucide_folder_input
        "AccountTree" -> com.r0ybt.arachn0de.R.drawable.lucide_network
        "Delete" -> com.r0ybt.arachn0de.R.drawable.lucide_trash
        "Edit" -> com.r0ybt.arachn0de.R.drawable.lucide_pencil
        "History" -> com.r0ybt.arachn0de.R.drawable.lucide_clock
        "Image" -> com.r0ybt.arachn0de.R.drawable.lucide_image
        "Share" -> com.r0ybt.arachn0de.R.drawable.lucide_share_2
        "Bookmark" -> com.r0ybt.arachn0de.R.drawable.lucide_bookmark
        "SwapHoriz" -> com.r0ybt.arachn0de.R.drawable.lucide_arrow_left_right
        "Directions" -> com.r0ybt.arachn0de.R.drawable.lucide_navigation
        "CheckBox" -> com.r0ybt.arachn0de.R.drawable.lucide_list_checks
        "ArrowUpward","ArrowDownward" -> com.r0ybt.arachn0de.R.drawable.lucide_arrow_left
        else -> null
    }
    if(resource!=null) Icon(androidx.compose.ui.res.painterResource(resource),null,modifier.then(
        when(icon.name.substringAfterLast('.')) {
            "ArrowUpward" -> Modifier.rotate(90f)
            "ArrowDownward" -> Modifier.rotate(-90f)
            else -> Modifier
        }))
    else if(icon.name.substringAfterLast('.')=="PushPin") androidx.compose.material3.Icon(icon,null,modifier,tint=LocalContentColor.current)
    else LucideIcon(icon,null,modifier,tint=LocalContentColor.current)
}
