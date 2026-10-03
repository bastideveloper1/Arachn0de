package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.domain.model.NodeProgressState
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: String,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Arachn0deColors.AccentSurface),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, color = Arachn0deColors.Accent, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.wrapContentHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    color = Arachn0deColors.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    color = Arachn0deColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun EmptyProjectsState(onCreateProject: () -> Unit) {
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
                text = "Todavía no hay proyectos",
                color = Arachn0deColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Crea tu primer proyecto para empezar a organizar ideas, objetivos y seguimiento.",
                color = Arachn0deColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCreateProject,
                colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.Primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuevo proyecto")
            }
        }
    }
}

@Composable
internal fun ProjectList(
    projects: List<Project>,
    projectProgressById: Map<String, NodeProgress>,
    onCreateProject: () -> Unit,
    onEdit: (Project) -> Unit,
    onDelete: (Project) -> Unit,
    onOpenProject: (Project) -> Unit,
    onReorderTo: (String, String) -> Unit = { _, _ -> },
    reorderBusy: Boolean = false,
    reorderError: String? = null,
    listState: LazyListState = rememberLazyListState(),
    projectAttentionById: Map<String, com.r0ybt.arachn0de.domain.model.AttentionSummary> = emptyMap(),
    onCopy: (Project, Boolean) -> Unit = { _, _ -> },
    canCopy: Boolean = true,
    canCopyDescendants: Boolean = true,
) {
    val order = projects.map { it.id }
    val drag = rememberDragReorderState(listState, "project:", mapOf(false to order), reorderBusy, reorderError) { source, target, _ ->
        onReorderTo(source, target)
    }
    val projectById = projects.associateBy { it.id }
    val renderedProjects = drag.orderFor(false, order).mapNotNull { projectById[it] }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().testTag("projects-list").then(drag.gestureModifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(renderedProjects, key = { "project:${it.id}" }, contentType = { "project" }) { project ->
            val dragging = drag.isDragging(project.id)
            ProjectCard(
                project = project,
                onCopy = { descendants -> onCopy(project,descendants) },
                canCopy = canCopy,
                canCopyDescendants = canCopyDescendants,
                progress = projectProgressById[project.id],
                attention = projectAttentionById[project.id],
                onOpen = { onOpenProject(project) },
                onEdit = { onEdit(project) },
                onDelete = { onDelete(project) },
                dragging = dragging,
                modifier = Modifier.animateItem(
                    fadeInSpec = null, fadeOutSpec = null,
                    placementSpec = if (dragging) null else androidx.compose.animation.core.spring(),
                ).then(drag.cardModifier(project.id)),
            )
        }

        item(key = "create-project", contentType = "action") {
            Button(
                onClick = onCreateProject,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.Primary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuevo proyecto")
            }
        }
    }
}

@Composable
internal fun ProjectCard(
    project: Project,
    progress: NodeProgress?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    attention: com.r0ybt.arachn0de.domain.model.AttentionSummary? = null,
    onCopy: (Boolean) -> Unit = {},
    canCopy: Boolean = true,
    canCopyDescendants: Boolean = true,
) {
    var showActions by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Arachn0deColors.Outline.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .zIndex(if (dragging) 1f else 0f)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Arachn0deColors.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = project.name.take(1).uppercase(Locale.getDefault()),
                    color = Arachn0deColors.OnPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = project.name,
                    color = Arachn0deColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                )
                if (project.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = project.description,
                        color = Arachn0deColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                        fontSize = 12.sp,
                    )
                }
                AttentionIndicator(attention)
                if (progress != null) {
                    val pending = progress.total - progress.completed
                    Spacer(modifier = Modifier.height(6.dp))
                    when (progress.state) {
                        NodeProgressState.NO_WORK -> {
                            Text(
                                text = "Contenedor vacío",
                                color = Arachn0deColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                        else -> {
                            Column {
                                Text(
                                    text = "${progress.percentage}% · $pending de ${progress.total} pendientes",
                                    color = Arachn0deColors.Accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { if (progress.total == 0) 0f else progress.percentage / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = Arachn0deColors.PathHighlight,
                                    trackColor = Arachn0deColors.ControlSurface,
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = { showActions = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opciones del proyecto", tint = Arachn0deColors.TextSecondary)
            }
        }
    }
    if (showActions) {
        ActionMenu(project.name,{ showActions = false }) {
            ActionMenuItem("Editar",Icons.Default.Edit,{ showActions = false; onEdit() },
                Modifier.semantics { contentDescription = "Editar proyecto" })
            ActionMenuItem("Copiar",Icons.Default.ContentCopy,{ showActions = false; onCopy(false) },enabled=canCopy)
            ActionMenuItem("Copiar con descendientes",Icons.Default.AccountTree,{ showActions = false; onCopy(true) },enabled=canCopy && canCopyDescendants)
            androidx.compose.material3.HorizontalDivider()
            ActionMenuItem("Eliminar",Icons.Default.Delete,{ showActions = false; onDelete() },
                Modifier.semantics { contentDescription = "Eliminar proyecto" },destructive=true)
        }
    }
}

internal fun formatProjectDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return formatter.format(timestamp)
}