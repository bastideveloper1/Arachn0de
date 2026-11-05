package com.r0ybt.arachn0de.ui
import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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

private val CardMetadataAxis=HorizontalAlignmentLine {a,b->minOf(a,b)}

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
    canDragReorder: Boolean = false,
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
    showOptions: Boolean = true,
    titleMaxLines: Int = 2,
    navigationTool: ManagementToolVisual? = null,
    footer: (@Composable ()->Unit)? = null,
    onContent:(()->Unit)?=null,
    taskViewCount:Int?=null,
    selectionOnly:Boolean=false,
    compactPaymentSelection:Boolean=false,
    onTaskStatusChange:((com.r0ybt.arachn0de.domain.model.TaskStatus,()->Unit)->Unit)?=null,
    slotPreview: Boolean = false,
    paymentListState:androidx.compose.foundation.lazy.LazyListState?=null,
) {
    val cleanCompleted=if(node.isStructural && node.projectId!=null) completedCleanupLauncher(node.projectId,node.id) else null
    val shareContext=LocalContext.current
    val shareStorage=privateStorageContext(shareContext)
    val shareApp=shareContext.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
    val shareScope=rememberCoroutineScope()
    val pinOperation=remember(node.id) {com.r0ybt.arachn0de.ui.state.OperationState(shareScope)}
    OperationErrorDialog(pinOperation)
    var editingTechnologies by remember { mutableStateOf(false) }
    if(editingTechnologies) EditOwnerTechnologies(node.id,false) {editingTechnologies=false}
    var showContextMenu by remember { mutableStateOf(false) }
    val favoriteAction=rememberFavoriteMenuAction("node:${node.id}",!stateBusy,exterior=true)
    val sprintPhase:WorkState? = null // Historic phases are retained in storage, without active Sprint controls.
    val sprintFinal = sprintPhase != null && sprintPhase.next() == sprintPhase
    val checkMarked = if (sprintPhase != null) sprintFinal else node.isCompleted
    val checkEnabled = !stateBusy && node.workState==null
    val checkColors = completionControlColors(sprintPhase != null, node.isCompleted, checkEnabled)
    val noPending = node.isStructural && progress != null && !progress.hasPending
    val compactIdentity = !selecting && !canToggleComplete && (node.isStructural || (navigationTool==null && node.purpose==NodePurpose.NOTE))
    val bashCursor=rememberBashTextCursor()
    var titleLineCenter by remember {mutableStateOf(0)}
    val titleIconCenter=with(androidx.compose.ui.platform.LocalDensity.current) {(HierarchyCardIconSize/2).roundToPx()}
    val leadingColumnWidth = when {
        selecting || canToggleComplete -> 48.dp
        node.isStructural -> HierarchyCardIconSize
        node.purpose == NodePurpose.NOTE -> if(compactIdentity) HierarchyCardIconSize else 36.dp
        else -> 0.dp
    }
    val borderColor = if(selectionOnly && selected) Arachn0deColors.Accent else when (sprintPhase) {
        WorkState.UNPLANNED -> com.r0ybt.arachn0de.ui.theme.semanticColor(SemanticColors.SprintUnplanned)
        WorkState.PLANNED -> com.r0ybt.arachn0de.ui.theme.semanticColor(SemanticColors.SprintPlanned)
        WorkState.DOING -> com.r0ybt.arachn0de.ui.theme.semanticColor(SemanticColors.SprintDoing)
        WorkState.DONE -> com.r0ybt.arachn0de.ui.theme.semanticColor(SemanticColors.SprintDone)
        WorkState.VALIDATED -> com.r0ybt.arachn0de.ui.theme.semanticColor(SemanticColors.SprintValidated)
        null -> Arachn0deColors.Outline.copy(alpha = if (noPending) 0.75f else 0.9f)
    }
    val borderWidth = if (sprintPhase == WorkState.DOING) 2.dp else 1.dp
    val rowAlpha = if (node.isCompleted) 0.92f else 1f

    GeneralCard(
        bashEntry = true,
        modifier = modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .then(if(node.isStructural && taskViewCount==0) Modifier.testTag("task-view-empty-layer:${node.id}") else Modifier)
            .then(if(LocalBashPresentation.current) Modifier else Modifier.border(borderWidth, borderColor, RoundedCornerShape(8.dp)))
            .zIndex(if (dragging) 1f else 0f)
            .semantics { onSelect?.let { select -> onLongClick("Seleccionar") { select(); true } }; this.selected = selected; if(selecting) stateDescription = if(selected) "Seleccionado" else "No seleccionado" }
            .clickable(enabled=!selectionOnly || onSelect!=null,onClick = if(selecting) ({ onSelect?.invoke(); Unit }) else onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if(node.isStructural && (!hasChildren || taskViewCount==0)) androidx.compose.ui.graphics.lerp(Arachn0deColors.Surface,Arachn0deColors.Background,.35f) else Arachn0deColors.Surface),
    ) {
        if(compactPaymentSelection) {
            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(selected,{onSelect?.invoke()},enabled=onSelect!=null,modifier=Modifier.size(48.dp))
                    Box(Modifier.weight(1f)) {
                    Text(cardEnumerationSpacing(com.r0ybt.arachn0de.data.repository.storyDisplayTitle(node.id,node.title,LocalStoryLabels.current)),
                        modifier=Modifier.testTag("node-title:${node.id}"),
                        color=Arachn0deColors.PathHighlight,fontSize=ContentTypography.CardTitle,fontWeight=FontWeight.SemiBold,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
                Box(Modifier.fillMaxWidth().padding(start=48.dp)) {
                    Column(Modifier,verticalArrangement=Arrangement.spacedBy(2.dp)) {footer?.invoke()}
                }
            }
            return@GeneralCard
        }
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                CompactCardHeader(if(tags.size==1) tags else emptyList(),stacked=true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min=if(LocalBashPresentation.current) 48.dp else CompactCardMinHeight)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting || canToggleComplete) Box(Modifier.size(48.dp)) {if(node.isStructural && node.isPinned) Icon(Icons.Default.PushPin,"Capa fijada",Modifier.size(14.dp).align(Alignment.BottomEnd),tint=Arachn0deColors.TextSecondary)}
                    else if(node.isStructural) {
                        Box(Modifier.width(HierarchyCardIconSize).height(48.dp).alignBy {titleIconCenter},contentAlignment=Alignment.TopCenter) {
                            HierarchyCardIcon(R.drawable.bw90_layer,"Capa")
                            if(node.isPinned) Icon(Icons.Default.PushPin,"Capa fijada",Modifier.size(14.dp).align(Alignment.BottomEnd).testTag("node-pin:${node.id}"),tint=Arachn0deColors.TextSecondary)
                        }
                    } else if (navigationTool != null) {
                        LucideIcon(navigationTool.icon, contentDescription=null, tint=MaterialTheme.colorScheme.primary, modifier=Modifier.size(36.dp))
                    } else if (node.purpose == NodePurpose.NOTE) {
                        Box(Modifier.size(HierarchyCardIconSize).alignBy {titleIconCenter}) {
                            NoteContentIcon(node.description.isNotBlank(),"Nota",Modifier.size(HierarchyCardIconSize),if(node.description.isNotBlank()) Arachn0deColors.TextPrimary else Arachn0deColors.TextSecondary)
                        }
                    } else if (node.isStructural) {
                        Image(
                            painter = painterResource(id = R.drawable.bw90_layer),
                            contentDescription = "Capa",
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Arachn0deColors.Icon),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(48.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(if(compactIdentity) 8.dp else 12.dp))

                    Column(
                        modifier = Modifier.weight(1f).alignBy(if(compactIdentity) CardTitleAxis else CardMetadataAxis),
                    ) {
                        Text(
                            text = if(LocalBashPresentation.current) bashListing(bashFileName(cardEnumerationSpacing(com.r0ybt.arachn0de.data.repository.storyDisplayTitle(node.id,node.title,LocalStoryLabels.current)),node.purpose),node.isStructural,null) else androidx.compose.ui.text.AnnotatedString(cardEnumerationSpacing(com.r0ybt.arachn0de.data.repository.storyDisplayTitle(node.id,node.title,LocalStoryLabels.current))),
                            color = Arachn0deColors.PathHighlight,
                            fontSize = ContentTypography.CardTitle * if(slotPreview) 0.85f else 1f,
                            style = TextStyle(fontFamily=if(LocalBashPresentation.current) androidx.compose.ui.text.font.FontFamily.Monospace else null,lineHeight = ContentTypography.CardTitleLine * if(slotPreview) 0.85f else 1f, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if(slotPreview) 3 else if(LocalBashPresentation.current) Int.MAX_VALUE else titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (node.isCompleted) TextDecoration.LineThrough else null,
                            modifier=Modifier.then(if(slotPreview && tags.size==1)Modifier.padding(end=48.dp) else Modifier).testTag("node-title:${node.id}").then(bashCursor.modifier).cardTitleAxis(titleLineCenter),
                            onTextLayout={bashCursor.layout=it;titleLineCenter=((it.getLineTop(0)+it.getLineBottom(0))/2).toInt()},
                        )

                        navigationTool?.let {Text(it.description,modifier=Modifier,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}


                        if(!selectionOnly) CompactCardIndicators("node:${node.id}",compactNodeFacts(node,now).copy(people=responsiblePeople.mapTo(hashSetOf()) {it.id},journeys=emptySet()),Modifier.padding(top=4.dp,bottom=4.dp).layout {measurable,constraints->
                            val placeable=measurable.measure(constraints)
                            layout(placeable.width,placeable.height,mapOf(CardMetadataAxis to placeable.height/2)) {placeable.placeRelative(0,0)}
                        },showTechnologies=node.isStructural,showContainedTasks=node.isStructural,effort=node.effort.takeIf {node.isCompletable},onRecurrence=onRecurrence,taskStateCount=if(node.isStructural) com.r0ybt.arachn0de.domain.model.TaskStatePolicy.count(node,LocalIndicatorDirectory.current.nodes,LocalIndicatorDirectory.current.projects[node.projectId]) else null)
                        if(!selectionOnly) Box(Modifier) {ObligationIndicator(node)}
                        if(node.isStructural && (attention?.overdue ?: 0)>0) Box(Modifier) {AttentionIndicator(com.r0ybt.arachn0de.domain.model.AttentionSummary(overdue=attention!!.overdue))}
                        if(node.isStructural) ContainerProgressLine("node:${node.id}",progress ?: LocalContainerProgress.current["node:${node.id}"],visibleTasks=taskViewCount)



                    }
                    if(!selectionOnly) Column(Modifier.align(if(tags.size==1)Alignment.Bottom else Alignment.CenterVertically),horizontalAlignment=Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if(!node.isStructural) TechnologyCardButton(node.id)

                            if (showOptions) Box(
                                modifier = Modifier.size(48.dp).testTag("node-options:${node.id}").combinedClickable(
                                    onClick = { if(selecting) onSelect?.invoke() else showContextMenu = true },
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onLongClickLabel = "Seleccionar",
                                    onLongClick = { onSelect?.invoke() },
                                ), contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Surface(Modifier.size(28.dp),shape=RoundedCornerShape(6.dp),color=Arachn0deColors.ControlSurface) {
                                    Box(contentAlignment=Alignment.Center) {LucideIcon(Icons.Default.MoreVert,"Más opciones",Modifier.size(18.dp),tint=Arachn0deColors.TextSecondary)}
                                }
                            }
                        }
                    }
                }
                }
                if (sprintPhase != null || tags.size>1 || footer!=null) Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp + leadingColumnWidth, end = 12.dp, top = 2.dp, bottom = 6.dp),
                ) {
                    if (sprintPhase != null ||
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
                                        WorkState.UNPLANNED, WorkState.PLANNED -> com.r0ybt.arachn0de.ui.theme.semanticColor(SemanticColors.SprintPlanned)
                                        WorkState.DOING, WorkState.VALIDATED -> com.r0ybt.arachn0de.ui.theme.semanticColor(SemanticColors.SprintValidated)
                                        WorkState.DONE -> Arachn0deColors.TextCompleted
                                    },
                                    fontSize = ContentTypography.Metadata, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if(node.effectivePriority != com.r0ybt.arachn0de.domain.model.Priority.HIGH) PriorityIndicator(node)
                    }
                    footer?.invoke()
                    if (tags.size>1) FlowRow(
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
                if (selecting) androidx.compose.material3.Checkbox(selected, { onSelect?.invoke() },enabled=!selectionOnly || onSelect!=null)
                else if (canToggleComplete) {
                    IconButton(
                        onClick = rememberPaymentCheck(node,paymentListState,onToggleComplete),
                        enabled = checkEnabled,
                        modifier = Modifier
                            .size(48.dp).testTag("task-state:${node.id}")
                            .semantics {
                                contentDescription = if (sprintPhase != null) {
                                    if (sprintFinal) "Sprint validado: ${node.title}"
                                    else "Confirmar ${sprintPhase.label} y avanzar a ${sprintPhase.next().label}: ${node.title}"
                                } else "Cambiar estado: ${node.title}"
                                stateDescription = if(sprintPhase!=null) {if(checkMarked) "Completada" else "Pendiente"} else node.taskStatus.label
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (checkMarked) checkColors.fill else Arachn0deColors.ControlSurface),
                    ) {
                        if(sprintPhase==null && node.awaitingVerification) VerificationMark()
                        else if (checkMarked) {
                            LucideIcon(
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

    var shareExport by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    var saveStructure by remember(node.id) {mutableStateOf(false)}
    if(saveStructure && node.projectId!=null) SaveStructurePanel(node.projectId,node.id,node.title) {saveStructure=false}
    if(shareExport) ShareExportPanel(node.title,node.projectId,node.id,node.isStructural,canCopy,canCopy,onCopy) {shareExport=false}
    if (showContextMenu) {
        val touchExploration=(androidx.compose.ui.platform.LocalContext.current.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
            as? android.view.accessibility.AccessibilityManager)?.isTouchExplorationEnabled==true
        val dragIsEquivalent=canDragReorder && !touchExploration &&
            androidx.compose.ui.platform.LocalInputModeManager.current.inputMode != androidx.compose.ui.input.InputMode.Keyboard
        ContextActionMenu(node.title, { showContextMenu = false }, bottomAligned = node.purpose == NodePurpose.ACTION && !node.isStructural,showCancel=false,groupConversions=true) {
            favoriteAction?.let {items+=it}
            if(node.isCompletable) action(if(node.priority==com.r0ybt.arachn0de.domain.model.Priority.HIGH) "Quitar urgencia" else "Marcar como urgente",Icons.Default.Flag,{
                pinOperation.submit("No se pudo cambiar la urgencia.",{shareApp?.nodeRepository?.togglePriority(node.id) ?: false})
            },enabled=shareApp!=null && !stateBusy && !pinOperation.busy)
            if(node.projectId!=null) action("Guardar como plantilla",Icons.Default.Description,{showContextMenu=false;saveStructure=true},enabled=!stateBusy)
            exportSpec=ElementOptionsExport(node.projectId,node.id,node.isStructural,canCopy,canCopy,onCopy)
            action("Compartir y exportar",Icons.Default.Share,{showContextMenu=false;shareExport=true},enabled=shareApp!=null && !stateBusy)
            cleanCompleted?.let {clean->action("Limpiar completadas",Icons.Default.Delete,{showContextMenu=false;clean()})}
            if(node.isStructural) action(if(node.isPinned) "Desfijar" else "Fijar",Icons.Default.PushPin,{
                pinOperation.submit("No se pudo cambiar la fijación.",{shareApp?.nodeRepository?.setPinned(node.id,!node.isPinned) ?: false}) {showContextMenu=false}
            },enabled=shareApp!=null && !stateBusy && !pinOperation.busy)
            if(node.projectId!=null && node.workState==null && (node.isStructural || node.isCompletable)) submenu("Configurar estados",Icons.Default.Settings,enabled=shareApp!=null && !stateBusy) {close->TaskStateSettingsItems(node=node) {close();showContextMenu=false}}
            onSelect?.let { select -> action("Seleccionar", Icons.Default.CheckBox, { showContextMenu = false; select() }) }
            if(onContent==null) action("Editar",Icons.Default.Edit,{ showContextMenu = false; onEdit() })
            onHistory?.let { history -> action("Historial",Icons.Default.History,{ showContextMenu = false; history() }) }
            if (!hasChildren) action(if(node.purpose != NodePurpose.ACTION) "Convertir en tarea" else "Convertir en nota",
                Icons.Default.SwapHoriz,{ onConvert { showContextMenu = false } })
            if(node.isStructural) onConvertToProject?.let { promote -> action("Convertir en proyecto", Icons.Default.AccountTree, { showContextMenu=false;promote() }, enabled=!stateBusy) }
            if(!node.isStructural) action("Convertir en capa",Icons.Default.AccountTree,{ showContextMenu=false;onMakeLayer() })
            action("Responsables",Icons.Default.People,{ showContextMenu = false; onResponsible() })
            action("Mover a…",Icons.Default.DriveFileMove,{ showContextMenu = false; onMove() })

            action("Eliminar",Icons.Default.Delete,{ showContextMenu = false; onDelete() },destructive=true)
        }
    }
}
