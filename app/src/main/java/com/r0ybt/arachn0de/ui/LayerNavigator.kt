package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.ExpandMore
import com.r0ybt.arachn0de.ui.state.LayerHierarchyIndex
import com.r0ybt.arachn0de.ui.state.LayerHierarchyRow
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.R

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

@Composable
internal fun LayerNavigator(
    nodes: List<Node>,
    expandedIds: List<String>,
    onToggle: (String) -> Unit,
    currentNodeId: String?,
    onNavigateTo: (String) -> Unit,
    projectName: String,
    onHome: () -> Unit,
    onProject: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    movingId: String? = null,
    enabled: Boolean = true,
) {
    val index = remember(nodes) { LayerHierarchyIndex(nodes) }
    val expanded = remember(expandedIds) { expandedIds.toSet() }
    val excluded = remember(index, movingId) { movingId?.let(index::subtreeIds).orEmpty() }
    val rows = remember(index, expanded, excluded, movingId) {
        index.visibleRows(expanded).filter { it.node.id !in excluded && it.node.purpose == com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION && (movingId != null || it.hasChildren) && (movingId == null || it.node.obligation == null) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(360.dp).testTag("layer-navigator"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (movingId == null) item(key = "home", contentType = "destination") {
            NavigatorRoot("Proyectos", "Home", false, "navigator-home", onHome)
        }
        item(key = "project", contentType = "destination") {
            NavigatorRoot(projectName, "Proyecto raíz", currentNodeId == null, "navigator-project", onProject, enabled)
        }
        items(rows, key = { "node:${it.node.id}" }, contentType = { "layer" }) { row ->
            LayerDestination(row, row.node.id in expanded, row.node.id == currentNodeId, { if (enabled) onToggle(it) }, { if (enabled) onNavigateTo(it) }, enabled)
        }
    }
}

@Composable
private fun NavigatorRoot(title: String, subtitle: String, isCurrent: Boolean, tag: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(6.dp))
        Card(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.weight(1f).testTag(tag).semantics { selected = isCurrent }
                .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) Arachn0deColors.PathHighlight else Arachn0deColors.Outline, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = if (isCurrent) Arachn0deColors.AccentSurface else Arachn0deColors.Surface),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tag == "navigator-home") {
                    Image(
                        painter = painterResource(id = R.drawable.iconohome),
                        colorFilter = null,
                        contentDescription = "Inicio",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Arachn0deColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Arachn0deColors.TextSecondary, fontSize = 11.sp)
                    if (isCurrent) Text("ACTUAL", color = Arachn0deColors.PathHighlight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun LayerDestination(
    row: LayerHierarchyRow,
    isExpanded: Boolean,
    isCurrent: Boolean,
    onToggle: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
    enabled: Boolean = true,
) {
    val depth = row.depth
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.width(18.dp).padding(top = 14.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (depth > 0) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(28.dp)
                            .background(Arachn0deColors.Outline.copy(alpha = 0.75f)),
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("navigator-node:${row.node.id}")
                    .semantics { selected = isCurrent }
                    .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) Arachn0deColors.PathHighlight else Arachn0deColors.Outline, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { onNavigateTo(row.node.id) },
                colors = CardDefaults.cardColors(containerColor = if (isCurrent) Arachn0deColors.AccentSurface else Arachn0deColors.Surface),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.hasChildren) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Arachn0deColors.ControlSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.cebolla_icon),
                                contentDescription = "Capa",
                                contentScale = ContentScale.Fit,

                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(2.dp, Arachn0deColors.Accent, RoundedCornerShape(6.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.node.title,
                            color = if (isCurrent) Arachn0deColors.PathHighlight else Arachn0deColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isCurrent) Text("ACTUAL", color = Arachn0deColors.PathHighlight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (row.hasChildren) "Capa ${depth + 1}" else "Tarea",
                            color = Arachn0deColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }

                    if (row.hasChildren) {
                        IconButton(enabled = enabled, onClick = { onToggle(row.node.id) }, modifier = Modifier.testTag("expand:${row.node.id}")) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = if (isExpanded) "Contraer rama" else "Expandir rama",
                                tint = Arachn0deColors.PathHighlight,
                            )
                        }
                    }
                }
            }
        }

    }
}

