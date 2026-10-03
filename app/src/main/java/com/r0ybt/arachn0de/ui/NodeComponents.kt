package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Description
import com.r0ybt.arachn0de.domain.model.NodePurpose
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
import com.r0ybt.arachn0de.R

@Composable
internal fun EmptyLayerState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Esta capa está vacía",
                color = Arachn0deColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Añade un elemento para empezar a construir la siguiente capa.",
                color = Arachn0deColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

        }
    }
}

@Composable
internal fun NodeProgressCard(progress: NodeProgress) {
    if (progress.state == NodeProgressState.NO_WORK) {
        Text("Contenedor vacío", color = Arachn0deColors.TextSecondary, fontSize = 12.sp)
        return
    }

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
    onConvert: (() -> Unit) -> Unit = { done -> done() },
    canCopy: Boolean = true,
    onCopy: (Boolean) -> Unit = {},
    onRecurrence: (() -> Unit)? = null,
    tags: List<com.r0ybt.arachn0de.domain.model.Tag> = emptyList(),
    onHistory: (() -> Unit)? = null,
    selected: Boolean = false,
    selecting: Boolean = false,
    onSelect: (() -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val completedTint = if (node.isCompleted) Arachn0deColors.Completed else Arachn0deColors.Primary
    val displayTextColor = if (node.isCompleted) Arachn0deColors.TextCompleted else Arachn0deColors.TextPrimary
    val rowAlpha = if (node.isCompleted) 0.92f else 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .border(1.dp, Arachn0deColors.Outline.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .zIndex(if (dragging) 1f else 0f)
            .semantics { onSelect?.let { select -> onLongClick("Seleccionar") { select(); true } }; this.selected = selected; if(selecting) stateDescription = if(selected) "Seleccionado" else "No seleccionado" }
            .clickable(onClick = if(selecting) ({ onSelect?.invoke(); Unit }) else onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) androidx.compose.material3.Checkbox(selected, { onSelect?.invoke() })
            else if (canToggleComplete) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = if (node.isCompleted) "Marcar pendiente: ${node.title}" else "Completar: ${node.title}"
                            stateDescription = if (node.isCompleted) "Completada" else "Pendiente"
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (node.isCompleted) completedTint else Arachn0deColors.ControlSurface),
                ) {
                    if (node.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Arachn0deColors.OnPrimary,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(2.dp, Arachn0deColors.TextSecondary, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            } else if (node.purpose == NodePurpose.NOTE) {
                Icon(Icons.Default.Description, contentDescription = "Nota", tint = Arachn0deColors.TextSecondary, modifier = Modifier.size(36.dp))
            } else if (hasChildren) {
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
                PriorityIndicator(node)
                TagChips(tags)
                if (onRecurrence != null) TextButton(onClick = onRecurrence) { Text("↻ Recurrencia", color = Arachn0deColors.Primary, fontSize = 12.sp) }
                Text(
                    text = node.title,
                    color = displayTextColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (node.isCompleted) TextDecoration.LineThrough else null,
                )

                if (node.description.isNotBlank()) {
                    Text(node.description, color = Arachn0deColors.TextSecondary, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                ObligationIndicator(node)
                TaskDateIndicator(node, now)
                if (hasChildren) AttentionIndicator(attention)

                if (responsiblePeople.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ResponsibleAvatars(responsiblePeople)
                }
                if (hasChildren && progress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (progress.state == NodeProgressState.NO_WORK) {
                        Text(
                            text = "Contenedor vacío",
                            color = Arachn0deColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    } else {
                        Text(
                            text = "${progress.percentage}% · ${progress.total - progress.completed} de ${progress.total} pendientes",
                            color = Arachn0deColors.TextSecondary,
                            fontSize = 11.sp,
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
                } else if (hasChildren) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Capa",
                        color = Arachn0deColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasChildren) {
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
    }

    if (showContextMenu) {
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            textContentColor = Arachn0deColors.TextSecondary,
            onDismissRequest = { showContextMenu = false },
            title = { Text(node.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    onSelect?.let { select -> TextButton(onClick = { showContextMenu = false; select() }) { Text("Seleccionar") } }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onEdit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Editar", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    onHistory?.let { openHistory -> TextButton(onClick = { showContextMenu = false; openHistory() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Historial", modifier = Modifier.fillMaxWidth())
                    } }
                    if (!hasChildren) {
                        TextButton(onClick = { onConvert { showContextMenu = false } }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (node.purpose == NodePurpose.NOTE) "Convertir en tarea" else "Convertir en nota", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(
                        onClick = { showContextMenu = false; onResponsible() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Responsables", modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = { showContextMenu = false; onMove() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Mover a…", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onDelete()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Eliminar", color = Arachn0deColors.Destructive, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(enabled = canCopy, onClick = { showContextMenu = false; onCopy(false) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Copiar este elemento", modifier = Modifier.fillMaxWidth())
                    }
                    if (hasChildren) TextButton(enabled = canCopy, onClick = { showContextMenu = false; onCopy(true) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Copiar con descendientes", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = { showContextMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancelar", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )
    }
}

