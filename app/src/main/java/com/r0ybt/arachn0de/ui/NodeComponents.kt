package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Description
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.domain.model.WorkState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.domain.model.NodeProgressState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.SemanticColors
import com.r0ybt.arachn0de.ui.theme.ContentTypography
import com.r0ybt.arachn0de.R

@Composable
internal fun NodeProgressCard(progress: NodeProgress) {
    if (progress.state == NodeProgressState.NO_WORK) return

    val pending = progress.total - progress.completed
    val progressFraction = if (progress.total == 0) 0f else progress.percentage / 100f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Progreso",
                    color = Arachn0deColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${progress.percentage}% · $pending de ${progress.total} pendientes",
                    color = Arachn0deColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Arachn0deColors.PathHighlight,
                trackColor = Arachn0deColors.ControlSurface,
            )
        }
    }
}

@Composable
internal fun NodeCard(
    node: Node,
    progress: NodeProgress?,
    hasChildren: Boolean,
    canToggleComplete: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onReorder: (Boolean, () -> Unit) -> Unit,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    onMove: () -> Unit = {},
    onResponsible: () -> Unit = {},
    responsiblePeople: List<com.r0ybt.arachn0de.domain.model.Person> = emptyList(),
    now: Long = 0L,
    attention: com.r0ybt.arachn0de.domain.model.AttentionSummary? = null,
    cardAlerts: com.r0ybt.arachn0de.domain.model.ProjectCardAlerts? = null,
    onMakeLayer: () -> Unit = {},
    onConvertToProject: (() -> Unit)? = null,
    onConvert: (() -> Unit) -> Unit = { done -> done() },
    canCopy: Boolean = true,
    onCopy: (Boolean) -> Unit = {},
    onRecurrence: (() -> Unit)? = null,
    tags: List<com.r0ybt.arachn0de.domain.model.Tag> = emptyList(),
    onHistory: (() -> Unit)? = null,
    selected: Boolean = false,
    selecting: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onAdvanceWorkState: (() -> Unit)? = null,
    onChangeWorkState: (() -> Unit)? = null,
    stateBusy: Boolean = false,
    metroRouteLabel: String? = null,
) {
    var editingTechnologies by remember { mutableStateOf(false) }
    var savingTemplate by remember { mutableStateOf(false) }
    if(editingTechnologies) EditOwnerTechnologies(node.id,false) {editingTechnologies=false}
    if(savingTemplate) SaveTaskTemplateDialog(node.id) {savingTemplate=false}
    var showContextMenu by remember { mutableStateOf(false) }
    val sprintPhase = node.workState
    val sprintFinal = sprintPhase != null && sprintPhase.next() == sprintPhase
    val checkMarked = if (sprintPhase != null) sprintFinal else node.isCompleted
    val checkEnabled = !stateBusy && (sprintPhase == null || !sprintFinal)
    val checkColors = completionControlColors(sprintPhase != null, node.isCompleted, checkEnabled)
    val noPending = node.isStructural && progress != null && !progress.hasPending
    val leadingColumnWidth = when {
        selecting || canToggleComplete -> 48.dp
        node.purpose == NodePurpose.NOTE || node.isStructural -> 36.dp
        else -> 0.dp
    }
    val borderColor = when (sprintPhase) {
        WorkState.UNPLANNED -> SemanticColors.SprintUnplanned
        WorkState.PLANNED -> SemanticColors.SprintPlanned
        WorkState.DOING -> SemanticColors.SprintDoing
        WorkState.DONE -> SemanticColors.SprintDone
        WorkState.VALIDATED -> SemanticColors.SprintValidated
        null -> Arachn0deColors.Outline.copy(alpha = if (noPending) 0.45f else 0.9f)
    }
    val borderWidth = if (sprintPhase == WorkState.DOING) 2.dp else 1.dp
    val rowAlpha = if (node.isCompleted || noPending) 0.92f else 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .zIndex(if (dragging) 1f else 0f)
            .semantics { onSelect?.let { select -> onLongClick("Seleccionar") { select(); true } }; this.selected = selected; if(selecting) stateDescription = if(selected) "Seleccionado" else "No seleccionado" }
            .clickable(onClick = if(selecting) ({ onSelect?.invoke(); Unit }) else onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (noPending) Arachn0deColors.Background else Arachn0deColors.Surface),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting || canToggleComplete) Spacer(Modifier.size(48.dp))
                    else if (node.purpose == NodePurpose.NOTE) {
                        Icon(Icons.Default.Description, contentDescription = "Nota", tint = Arachn0deColors.TextSecondary, modifier = Modifier.size(36.dp))
                    } else if (node.isStructural) {
                        Image(
                            painter = painterResource(id = R.drawable.cebolla_icon),
                            contentDescription = "Capa",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        if (noPending) Text("Sin pendientes", color = Arachn0deColors.TextSecondary, fontSize = 12.sp)
                        if (onRecurrence != null) TextButton(onClick = onRecurrence) { Text("↻ Recurrencia", color = Arachn0deColors.Primary, fontSize = 12.sp) }
                        Text(
                            text = node.title,
                            color = Arachn0deColors.PathHighlight,
                            fontSize = ContentTypography.CardTitle,
                            style = TextStyle(lineHeight = ContentTypography.CardTitleLine, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (node.isCompleted) TextDecoration.LineThrough else null,
                        )

                        if (node.description.isNotBlank()) {
                            AttachmentText(node.description, color = Arachn0deColors.TextSecondary, fontSize = ContentTypography.Description,
                                style = TextStyle(lineHeight = ContentTypography.DescriptionLine, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }

                        metroRouteLabel?.let {com.r0ybt.arachn0de.metro.MetroRoutePreview(node.id,it)}
                        ObligationIndicator(node)
                        if(node.isStructural && (attention?.overdue ?: 0)>0) AttentionIndicator(com.r0ybt.arachn0de.domain.model.AttentionSummary(overdue=attention!!.overdue))


                        if (node.isStructural && progress != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            if (progress.state == NodeProgressState.NO_WORK) {
                                Text(
                                    text = layerContentMessage(hasChildren, progress) ?: "No hay tareas por realizar",
                                    color = Arachn0deColors.TextSecondary,
                                    fontSize = ContentTypography.SmallMetadata,
                                )
                            } else {
                                Text(
                                    text = "${progress.percentage}% · ${progress.total - progress.completed} de ${progress.total} pendientes",
                                    color = Arachn0deColors.TextSecondary,
                                    fontSize = ContentTypography.SmallMetadata,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { progress.percentage / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Arachn0deColors.PathHighlight,
                                    trackColor = Arachn0deColors.ControlSurface,
                                )
                            }
                        } else if (node.isStructural) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (node.sprintMode) "Capa · Modo Sprint" else "Capa",
                                color = Arachn0deColors.TextSecondary,
                                fontSize = ContentTypography.SmallMetadata,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.r0ybt.arachn0de.metro.MetroActivityIndicator("node:${node.id}")
                        if(node.isStructural) CardSignals(cardAlerts?.futureDue ?: 0,cardAlerts?.dueToday ?: 0,cardAlerts?.highPriority ?: 0,"Tareas descendientes")
                        else NodeCardSignals(node,now)

                        if (node.isStructural) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Arachn0deColors.TextSecondary,
                            )
                        }

                        Box(
                            modifier = Modifier.size(48.dp).testTag("node-options:${node.id}").combinedClickable(
                                onClick = { if(selecting) onSelect?.invoke() else showContextMenu = true },
                                role = androidx.compose.ui.semantics.Role.Button,
                                onLongClickLabel = "Seleccionar",
                                onLongClick = { onSelect?.invoke() },
                            ), contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Más opciones",
                                tint = Arachn0deColors.TextSecondary,
                            )
                        }
                    }
                }
                CompactAssignments(node.id,responsiblePeople,Modifier.padding(start = 24.dp + leadingColumnWidth, end = 12.dp))
                if (sprintPhase != null || tags.isNotEmpty() ||
                    (node.effectivePriority != com.r0ybt.arachn0de.domain.model.Priority.NONE && node.effectivePriority != com.r0ybt.arachn0de.domain.model.Priority.HIGH) || (!node.isStructural && (node.startAt != null || node.dueAt != null))) Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp + leadingColumnWidth, end = 12.dp, top = 2.dp, bottom = 6.dp),
                ) {
                    if (sprintPhase != null || (node.effectivePriority != com.r0ybt.arachn0de.domain.model.Priority.NONE && node.effectivePriority != com.r0ybt.arachn0de.domain.model.Priority.HIGH) ||
                        (!node.isStructural && (node.startAt != null || node.dueAt != null))) FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!selecting && sprintPhase != null) {
                            val next = sprintPhase.next()
                            IconButton(onClick = { onAdvanceWorkState?.invoke() }, enabled = !stateBusy && sprintPhase != next && onAdvanceWorkState != null,
                                modifier = Modifier.size(48.dp).semantics {
                                    contentDescription = "Estado: ${sprintPhase.label}. " + if (sprintPhase == next) "Estado final." else "Activar para avanzar a ${next.label}."
                                    stateDescription = sprintPhase.label
                                }) { Text("${sprintPhase.ordinal + 1}/5", color = Arachn0deColors.TextPrimary) }
                        }
                        sprintPhase?.let { phase ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = borderColor.copy(alpha = 0.14f),
                                border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
                            ) {
                                Text(phase.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = when (phase) {
                                        WorkState.UNPLANNED, WorkState.PLANNED -> SemanticColors.SprintPlanned
                                        WorkState.DOING, WorkState.VALIDATED -> SemanticColors.SprintValidated
                                        WorkState.DONE -> Arachn0deColors.TextCompleted
                                    },
                                    fontSize = ContentTypography.Metadata, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if(node.effectivePriority != com.r0ybt.arachn0de.domain.model.Priority.HIGH) PriorityIndicator(node)
                        TaskDateIndicator(node, now, wrap = true)
                    }
                    if (tags.isNotEmpty()) FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        TagChips(tags, compact = true)
                    }
                }
            }
            if (selecting || canToggleComplete) Box(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selecting) androidx.compose.material3.Checkbox(selected, { onSelect?.invoke() })
                else if (canToggleComplete) {
                    IconButton(
                        onClick = onToggleComplete,
                        enabled = checkEnabled,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = if (sprintPhase != null) {
                                    if (sprintFinal) "Sprint validado: ${node.title}"
                                    else "Confirmar ${sprintPhase.label} y avanzar a ${sprintPhase.next().label}: ${node.title}"
                                } else if (node.isCompleted) "Marcar pendiente: ${node.title}" else "Completar: ${node.title}"
                                stateDescription = if (checkMarked) "Completada" else "Pendiente"
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (checkMarked) checkColors.fill else Arachn0deColors.ControlSurface),
                    ) {
                        if (checkMarked) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = checkColors.mark,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .border(2.dp, checkColors.border, RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showContextMenu) {
        ActionMenu(node.title, { showContextMenu = false }) {
            onSelect?.let { select -> ActionMenuItem("Seleccionar", Icons.Default.CheckBox, { showContextMenu = false; select() }) }
            onChangeWorkState?.let { change -> ActionMenuItem("Cambiar estado",Icons.Default.SwapHoriz,{ showContextMenu = false; change() }, enabled = !stateBusy) }
            if (metroRouteLabel==null && !hasChildren && node.purpose == NodePurpose.ACTION) ActionMenuItem("Metro / modo Viaje", Icons.Default.Directions, { showContextMenu=false;com.r0ybt.arachn0de.metro.MetroNavigation.open(node.id) })
            ActionMenuItem("Editar",Icons.Default.Edit,{ showContextMenu = false; onEdit() })
            ActionMenuItem("Editar tecnologías",Icons.Default.Code,{showContextMenu=false;editingTechnologies=true})
            if(!node.isStructural) ActionMenuItem("Guardar como plantilla",Icons.Default.Bookmark,{showContextMenu=false;savingTemplate=true})
            onHistory?.let { history -> ActionMenuItem("Historial",Icons.Default.History,{ showContextMenu = false; history() }) }
            if (!hasChildren) ActionMenuItem(if(node.purpose != NodePurpose.ACTION) "Convertir en tarea" else "Convertir en nota",
                Icons.Default.SwapHoriz,{ onConvert { showContextMenu = false } })
            if(node.isStructural) onConvertToProject?.let { promote -> ActionMenuItem("Convertir en proyecto", Icons.Default.AccountTree, { showContextMenu=false;promote() }, enabled=!stateBusy) }
            if(!node.isStructural) ActionMenuItem("Convertir en capa",Icons.Default.AccountTree,{ showContextMenu=false;onMakeLayer() })
            ActionMenuItem("Responsables",Icons.Default.People,{ showContextMenu = false; onResponsible() })
            ActionMenuItem("Mover a…",Icons.Default.DriveFileMove,{ showContextMenu = false; onMove() })
            ActionMenuItem("Copiar este elemento",Icons.Default.ContentCopy,{ showContextMenu = false; onCopy(false) },enabled=canCopy)
            if(node.isStructural) ActionMenuItem("Copiar con descendientes",Icons.Default.AccountTree,{ showContextMenu = false; onCopy(true) },enabled=canCopy)
            ActionMenuItem("Subir", Icons.Default.ArrowUpward, { onReorder(true) { showContextMenu = false } }, enabled = canMoveUp)
            ActionMenuItem("Bajar", Icons.Default.ArrowDownward, { onReorder(false) { showContextMenu = false } }, enabled = canMoveDown)
            HorizontalDivider()
            ActionMenuItem("Eliminar",Icons.Default.Delete,{ showContextMenu = false; onDelete() },destructive=true)
        }
    }
}
