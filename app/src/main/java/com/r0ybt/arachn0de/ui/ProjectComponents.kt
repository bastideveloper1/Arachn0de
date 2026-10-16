package com.r0ybt.arachn0de.ui
import androidx.compose.ui.layout.onSizeChanged

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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.r0ybt.arachn0de.domain.model.ProjectCardAlerts
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.ContentTypography
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
                fontSize = ContentTypography.ScopeTitle,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Crea tu primer proyecto para empezar a organizar ideas, objetivos y seguimiento.",
                color = Arachn0deColors.TextSecondary,
                fontSize = ContentTypography.Summary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

        }
    }
}

@Composable
internal fun ProjectList(
    projects: List<Project>,
    projectAlertsById: Map<String, ProjectCardAlerts>,
    onCreateProject: () -> Unit,
    onEdit: (Project) -> Unit,
    onDelete: (Project) -> Unit,
    onOpenProject: (Project) -> Unit,
    onReorderTo: (String, String) -> Unit = { _, _ -> },
    reorderBusy: Boolean = false,
    reorderError: String? = null,
    listState: LazyListState = rememberLazyListState(),
    onMoveInside: (Project) -> Unit = {},
    onCopy: (Project, Boolean) -> Unit = { _, _ -> },
    canCopy: Boolean = true,
    canCopyDescendants: Boolean = true,
    onPhoto: (Project) -> Unit = {},
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    projectTagsById:Map<String,List<com.r0ybt.arachn0de.domain.model.Tag>> = emptyMap(),
) {
    val pinnedOrder=projects.filter {it.isPinned}.map {it.id}
    val order = projects.filterNot {it.isPinned}.map { it.id }
    val drag = rememberDragReorderState(listState, "project:", mapOf(true to pinnedOrder,false to order), reorderBusy, reorderError) { source, target, _ ->
        onReorderTo(source, target)
    }
    val projectById = projects.associateBy { it.id }
    val renderedProjects = (drag.orderFor(true,pinnedOrder)+drag.orderFor(false, order)).mapNotNull { projectById[it] }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().testTag("projects-list").then(drag.gestureModifier),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom=bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(renderedProjects, key = { "project:${it.id}" }, contentType = { "project" }) { project ->
            val dragging = drag.isDragging(project.id)
            ProjectCard(
                project = project,
                tags = projectTagsById[project.id].orEmpty(),
                onMoveInside = { onMoveInside(project) },
                onPhoto = { onPhoto(project) },
                onCopy = { descendants -> onCopy(project,descendants) },
                canCopy = canCopy,
                canCopyDescendants = canCopyDescendants,
                alerts = projectAlertsById[project.id] ?: ProjectCardAlerts(),
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


    }
}

@Composable
internal fun ProjectCard(
    project: Project,
    alerts: ProjectCardAlerts,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    onMoveInside: () -> Unit = {},
    onCopy: (Boolean) -> Unit = {},
    canCopy: Boolean = true,
    canCopyDescendants: Boolean = true,
    onPhoto: () -> Unit = {},
    tags:List<com.r0ybt.arachn0de.domain.model.Tag> = emptyList(),
) {
    val cleanCompleted=completedCleanupLauncher(project.id)
    val pinScope=rememberCoroutineScope()
    val pinOperation=remember(project.id) {com.r0ybt.arachn0de.ui.state.OperationState(pinScope)}
    val pinApp=androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
    OperationErrorDialog(pinOperation)
    var showActions by remember { mutableStateOf(false) }
    var planning by remember {mutableStateOf(false)}
    var tagHeight by remember {mutableStateOf(24.dp)}
    val tagDensity = androidx.compose.ui.platform.LocalDensity.current
    if(planning) ProjectPlanningDialog(project.id) {planning=false}
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Arachn0deColors.Outline.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .zIndex(if (dragging) 1f else 0f)
            .testTag("project-card:${project.id}")
            .clickable(onClickLabel = "Abrir proyecto", onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp)) {
                ProjectIdentityIcon(project)
                if(project.isPinned) Icon(Icons.Default.PushPin,"Núcleo fijado",Modifier.size(14.dp).align(Alignment.BottomEnd).testTag("project-pin:${project.id}"),tint=Arachn0deColors.TextSecondary)
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
                    fontSize = ContentTypography.ProjectTitle,
                    fontWeight = FontWeight.SemiBold,
                    softWrap = true,
                    modifier = Modifier.testTag("project-title:${project.id}"),
                )
                CompactCardIndicators("project:${project.id}",modifier=Modifier.padding(top=4.dp,bottom=4.dp),hasOwnNotes=project.description.isNotBlank())

            }

            Column(modifier=if(tags.size==1) Modifier.padding(top=(tagHeight-8.dp).coerceAtLeast(0.dp)) else Modifier,horizontalAlignment=Alignment.End) {
                if(tags.size>1) CardTagBadge(tags)
                Row(verticalAlignment=Alignment.CenterVertically) {
            FavoriteToggle("project:${project.id}")
            IconButton(onClick = { showActions = true }, modifier = Modifier.size(48.dp)) {
                LucideIcon(Icons.Default.MoreVert, contentDescription = "Opciones del proyecto", tint = Arachn0deColors.TextSecondary)
            }
                }
            }
        }
        if(tags.size==1) CardTagBadge(tags,Modifier.align(Alignment.TopEnd).onSizeChanged {tagHeight=with(tagDensity) {it.height.toDp()}})
        }
    }
    if (showActions) {
        ContextActionMenu(project.name,{ showActions = false }) {
            action("Limpiar completadas",androidx.compose.material.icons.Icons.Default.Delete,{showActions=false;cleanCompleted()})
            action(if(project.isPinned) "Desfijar" else "Fijar",Icons.Default.PushPin,{
                pinOperation.submit("No se pudo cambiar la fijación.",{pinApp?.projectRepository?.setPinned(project.id,!project.isPinned) ?: false}) {showActions=false}
            },enabled=pinApp!=null && !pinOperation.busy)
            action("Planificación",Icons.Default.Settings,{showActions=false;planning=true})
            action("Fotografía", Icons.Default.Image, { showActions = false; onPhoto() })
            action("Editar",Icons.Default.Edit,{ showActions = false; onEdit() },
                Modifier.semantics { contentDescription = "Editar proyecto" })
            action("Convertir en capa",Icons.Default.AccountTree,{ showActions=false;onMoveInside() })
            action("Copiar",Icons.Default.ContentCopy,{ showActions = false; onCopy(false) },enabled=canCopy)
            action("Copiar texto con descendientes",Icons.Default.CopyAll,{ showActions = false; onCopy(true) },enabled=canCopy && canCopyDescendants)
            action("Eliminar",Icons.Default.Delete,{ showActions = false; onDelete() },
                Modifier.semantics { contentDescription = "Eliminar proyecto" },destructive=true)
        }
    }
}

@Composable
private fun ProjectAlerts(alerts: ProjectCardAlerts) {
    CardSignals(alerts.futureDue,alerts.dueToday,alerts.highPriority,"Tareas del proyecto")
}

internal fun formatProjectDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return formatter.format(timestamp)
}
