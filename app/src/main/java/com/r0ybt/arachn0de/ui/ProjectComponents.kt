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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AccountBalance
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
import androidx.compose.runtime.LaunchedEffect
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.r0ybt.arachn0de.domain.model.Priority
import com.r0ybt.arachn0de.domain.model.NodeProperty
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
    GeneralCard(
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
    GeneralCard(
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
    onContent:((Project)->Unit)?=null,
    header:(@Composable ()->Unit)?=null,
    additionalRows:androidx.compose.foundation.lazy.LazyListScope.()->Unit = {},
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
        if(header!=null) item(key="context-calendar") {header()}
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
                onContent = onContent?.let {open->({open(project)})},
                onDelete = { onDelete(project) },
                dragging = dragging,
                modifier = Modifier.animateItem(
                    fadeInSpec = null, fadeOutSpec = null,
                    placementSpec = if (dragging) null else androidx.compose.animation.core.spring(),
                ).then(drag.cardModifier(project.id)),
            )
        }


        additionalRows()
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
    onContent:(()->Unit)?=null,
    slotPreview: Boolean = false,
) {
    val bankCore=project.id in LocalBankRootIds.current
    val cleanCompleted=completedCleanupLauncher(project.id)
    val pinScope=rememberCoroutineScope()
    val pinOperation=remember(project.id) {com.r0ybt.arachn0de.ui.state.OperationState(pinScope)}
    val pinApp=androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
    OperationErrorDialog(pinOperation)
    var showActions by remember { mutableStateOf(false) }
    val favoriteAction=rememberFavoriteMenuAction("project:${project.id}",exterior=true)
    var ownPriority by remember(project.id) {mutableStateOf<Priority?>(null)}
    LaunchedEffect(pinApp,project.id,project.updatedAt) {
        ownPriority=pinApp?.let {app->withContext(Dispatchers.IO) {
            app.database.conversionDao().project(project.id)?.priority?.let(Priority::valueOf) ?: Priority.NONE
        }}
    }
    var saveStructure by remember(project.id) {mutableStateOf(false)}
    if(saveStructure) SaveStructurePanel(project.id,null,project.name) {saveStructure=false}
    var planning by remember {mutableStateOf(false)}
    val bashCursor=rememberBashTextCursor()
    var titleLineCenter by remember {mutableStateOf(0)}
    val titleIconCenter=with(androidx.compose.ui.platform.LocalDensity.current) {(HierarchyCardIconSize/2).roundToPx()}
    if(planning) ProjectPlanningDialog(project.id) {planning=false}
    GeneralCard(
        bashEntry = true,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
        modifier = modifier
            .fillMaxWidth()
            .then(if(LocalBashPresentation.current) Modifier else Modifier.border(1.dp, Arachn0deColors.Outline.copy(alpha = 0.85f), RoundedCornerShape(8.dp)))
            .zIndex(if (dragging) 1f else 0f)
            .testTag("project-card:${project.id}")
            .clickable(onClickLabel = "Abrir proyecto", onClick = onOpen),
    ) {
        val cardContent: @Composable ()->Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if(bankCore || LocalBashPresentation.current) 48.dp else CompactCardMinHeight)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(if(project.photo!=null) 48.dp else HierarchyCardIconSize).height(48.dp)
                .then(if(project.photo==null && !bankCore) Modifier.alignBy {titleIconCenter} else Modifier),
                contentAlignment=if(project.photo!=null) Alignment.Center else Alignment.TopCenter) {
                if(project.photo!=null) ProjectIdentityIcon(project)
                else HierarchyCardIcon(com.r0ybt.arachn0de.R.drawable.bw90_project,"Proyecto sin fotografía")
                if(project.isPinned) Icon(Icons.Default.PushPin,"Núcleo fijado",Modifier.size(14.dp).align(Alignment.BottomEnd).testTag("project-pin:${project.id}"),tint=Arachn0deColors.TextSecondary)
            }
            Spacer(modifier = Modifier.width(if(project.photo!=null) 12.dp else 8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if(project.photo==null && !bankCore) Modifier.alignBy(CardTitleAxis) else Modifier),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min=if(bankCore)48.dp else 0.dp)) {
                Text(
                    text = if(LocalBashPresentation.current) bashListing(cardEnumerationSpacing(project.name)+"/",true,null) else androidx.compose.ui.text.AnnotatedString(cardEnumerationSpacing(project.name)),
                    color = Arachn0deColors.TextPrimary,
                    fontSize = ContentTypography.CardTitle * if(slotPreview) 0.85f else 1f,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if(LocalBashPresentation.current) androidx.compose.ui.text.font.FontFamily.Monospace else null,
                    softWrap = true, minLines=if(bankCore && !slotPreview)2 else 1, maxLines=if(slotPreview)3 else 2, overflow=TextOverflow.Ellipsis,
                    modifier = Modifier.then(if(slotPreview && tags.isNotEmpty())Modifier.padding(end=96.dp) else if(bankCore) Modifier.padding(end=24.dp) else Modifier).testTag("project-title:${project.id}").then(bashCursor.modifier).cardTitleAxis(titleLineCenter),
                    onTextLayout={bashCursor.layout=it;titleLineCenter=((it.getLineTop(0)+it.getLineBottom(0))/2).toInt()},
                )
                    if(bankCore) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(14.dp)) {
                        Icon(Icons.Default.AccountBalance,"Núcleo Banco",Modifier.size(14.dp).align(Alignment.CenterStart).testTag("bank-core-indicator:${project.id}"),tint=androidx.compose.ui.graphics.Color(0xffd4af37))
                    }
                }
                if(!bankCore) CompactCardIndicators("project:${project.id}",modifier=Modifier.padding(top=4.dp,bottom=4.dp),hasOwnNotes=project.description.isNotBlank(),taskStateCount=if(project.verificationEnabled)3 else 2,ownPriority=ownPriority==Priority.HIGH)
                else FavoriteCardIndicator("project:${project.id}")
                ContainerProgressLine("project:${project.id}",LocalContainerProgress.current["project:${project.id}"])

            }

            if(!slotPreview) Column(Modifier.align(if(tags.isNotEmpty())Alignment.Bottom else Alignment.CenterVertically).widthIn(max=96.dp),horizontalAlignment=Alignment.End) {
                Row(verticalAlignment=Alignment.CenterVertically) {
            CompactCardIconButton(Icons.Default.MoreVert,"Opciones del proyecto",{showActions=true},
                tint=Arachn0deColors.TextSecondary)
                }
            }
        }
        }
        if(bankCore) {
            if(tags.isEmpty())cardContent() else CompactCardHeader(tags,content=cardContent)
        } else CompactCardHeader(tags,stacked=true,content=cardContent)
    }
    var shareExport by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    if(shareExport) ShareExportPanel(project.name,project.id,null,true,canCopy,canCopy && canCopyDescendants,onCopy) {shareExport=false}
    if (showActions) {
        ContextActionMenu(project.name,{ showActions = false },showCancel=false,groupConversions=true) {
            favoriteAction?.let {items+=it}
            if(!bankCore) action(if(ownPriority==Priority.HIGH) "Quitar urgencia" else "Marcar como urgente",Icons.Default.Flag,{
                var next=Priority.NONE
                pinOperation.submit("No se pudo cambiar la urgencia.",{
                    val app=pinApp ?: return@submit false
                    withContext(Dispatchers.IO) {app.database.withTransaction {
                        val baseline=corePropertyValues(app,project.id,NodeProperty.PRIORITY)
                        next=if(Priority.valueOf(baseline.single())==Priority.HIGH) Priority.NONE else Priority.HIGH
                        saveCoreMetadata(app,project.id,NodeProperty.PRIORITY,baseline,listOf(next.name))
                    }}
                },{ownPriority=next})
            },enabled=pinApp!=null && ownPriority!=null && !pinOperation.busy)
            action("Guardar como plantilla",Icons.Default.Description,{showActions=false;saveStructure=true},enabled=pinApp!=null)
            exportSpec=ElementOptionsExport(project.id,null,true,canCopy,canCopy && canCopyDescendants,onCopy)
            action("Compartir y exportar",Icons.Default.Share,{showActions=false;shareExport=true},enabled=pinApp!=null)
            action("Limpiar completadas",androidx.compose.material.icons.Icons.Default.Delete,{showActions=false;cleanCompleted()})
            action(if(project.isPinned) "Desfijar" else "Fijar",Icons.Default.PushPin,{
                pinOperation.submit("No se pudo cambiar la fijación.",{pinApp?.projectRepository?.setPinned(project.id,!project.isPinned) ?: false}) {showActions=false}
            },enabled=pinApp!=null && !pinOperation.busy)
            submenu("Configurar estados",Icons.Default.Settings,enabled=pinApp!=null) {close->TaskStateSettingsItems(project=project) {close();showActions=false}}
            action("Fotografía", Icons.Default.Image, { showActions = false; onPhoto() })
            if(onContent==null) action("Editar",Icons.Default.Edit,{ showActions = false; onEdit() },
                Modifier.semantics { contentDescription = "Editar proyecto" })
            action("Convertir en capa",Icons.Default.AccountTree,{ showActions=false;onMoveInside() })
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
