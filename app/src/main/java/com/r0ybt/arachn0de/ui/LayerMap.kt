package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.ExpandMore
import com.r0ybt.arachn0de.ui.state.LayerMapIndex
import com.r0ybt.arachn0de.ui.state.LayerMapRow
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
internal fun LayerMapTree(
    nodes: List<Node>,
    expandedIds: List<String>,
    onToggle: (String) -> Unit,
    currentPath: List<String>,
    onNavigateTo: (String) -> Unit,
) {
    val index = remember(nodes) { LayerMapIndex(nodes) }
    val expanded = remember(expandedIds) { expandedIds.toSet() }
    val rows = remember(index, expanded) { index.visibleRows(expanded) }
    val pathIds = currentPath.toList()
    val activePath = remember(pathIds) { pathIds.toSet() }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(360.dp).testTag("layer-map"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rows, key = { it.node.id }, contentType = { "map-row" }) { row ->
            LayerMapBranch(row, row.node.id in expanded, row.node.id in activePath, onToggle, onNavigateTo)
        }
    }
}

@Composable
internal fun LayerMapBranch(
    row: LayerMapRow,
    isExpanded: Boolean,
    isOnPath: Boolean,
    onToggle: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
) {
    val depth = row.depth
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(22.dp),
            ) {
                if (depth > 0) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .background(Arachn0deColors.Primary),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isOnPath) Arachn0deColors.PathHighlight else Arachn0deColors.Primary,
                        ),
                )
                if (row.hasChildren) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .background(Arachn0deColors.Primary),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateTo(row.node.id) },
                colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
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
                            color = if (isOnPath) Arachn0deColors.PathHighlight else Arachn0deColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (row.hasChildren) "Capa ${depth + 1}" else "Tarea",
                            color = Arachn0deColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }

                    if (row.hasChildren) {
                        IconButton(onClick = { onToggle(row.node.id) }, modifier = Modifier.testTag("expand:${row.node.id}")) {
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

@Composable
internal fun LayerTrailRoute(
    projectName: String,
    pathNodes: List<Node>,
    onProjectClick: () -> Unit,
    onLayerClick: (String) -> Unit,
) {
    val items = buildList {
        add(ProjectRouteItem(label = projectName, isRoot = true, nodeId = null))
        pathNodes.forEachIndexed { index, node ->
            add(ProjectRouteItem(label = node.title, isRoot = false, nodeId = node.id, depth = index + 1))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.nodeId?.let { "node:$it" } ?: "project-root" }) { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (item.isRoot) onProjectClick() else onLayerClick(requireNotNull(item.nodeId))
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(30.dp),
                ) {
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .background(Arachn0deColors.Primary),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Arachn0deColors.PathHighlight)
                            .border(2.dp, Arachn0deColors.BackgroundMiddle, RoundedCornerShape(50)),
                    )
                    if (index < items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(18.dp)
                                .background(Arachn0deColors.Primary),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(2.dp)
                        .background(Arachn0deColors.Primary),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp, max = 72.dp),
                    colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.isRoot) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = Arachn0deColors.Accent,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.cebolla_icon),
                                contentDescription = "Capa",
                                contentScale = ContentScale.Fit,

                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = if (item.isRoot) "Proyecto raíz" else "Capa ${item.depth}",
                                color = Arachn0deColors.TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.label,
                                color = Arachn0deColors.TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 17.sp,
                            )
                        }

                        if (!item.isRoot) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Arachn0deColors.PathHighlight,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class ProjectRouteItem(
    val label: String,
    val isRoot: Boolean,
    val nodeId: String? = null,
    val depth: Int = 0,
)

