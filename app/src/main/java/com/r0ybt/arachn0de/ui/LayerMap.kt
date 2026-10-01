package com.r0ybt.arachn0de.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
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

internal data class LayerTreeNode(
    val node: Node,
    val children: List<LayerTreeNode> = emptyList(),
)

internal fun buildLayerTree(nodes: List<Node>): List<LayerTreeNode> {
    val byParent = nodes.groupBy { it.parentId }

    fun build(parentId: String?): List<LayerTreeNode> {
        return byParent[parentId].orEmpty()
            .sortedBy { it.position }
            .map { node ->
                LayerTreeNode(
                    node = node,
                    children = build(node.id),
                )
            }
    }

    return build(null)
}

@Composable
internal fun LayerMapTree(
    roots: List<LayerTreeNode>,
    currentPath: List<String>,
    onNavigateTo: (String) -> Unit,
) {
    val expandedById = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        roots.forEach { root ->
            LayerMapBranch(
                branch = root,
                depth = 0,
                currentPath = currentPath,
                expandedById = expandedById,
                onNavigateTo = onNavigateTo,
            )
        }
    }
}

@Composable
internal fun LayerMapBranch(
    branch: LayerTreeNode,
    depth: Int,
    currentPath: List<String>,
    expandedById: MutableMap<String, Boolean>,
    onNavigateTo: (String) -> Unit,
) {
    val isExpanded = expandedById[branch.node.id] ?: true

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
                            if (currentPath.contains(branch.node.id)) Arachn0deColors.PathHighlight else Arachn0deColors.Primary,
                        ),
                )
                if (branch.children.isNotEmpty() && depth < 4) {
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
                    .clickable { onNavigateTo(branch.node.id) },
                colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (branch.children.isNotEmpty()) {
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
                            text = branch.node.title,
                            color = if (currentPath.contains(branch.node.id)) Arachn0deColors.PathHighlight else Arachn0deColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (branch.children.isNotEmpty()) "Capa ${depth + 1}" else "Tarea",
                            color = Arachn0deColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }

                    if (branch.children.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Arachn0deColors.PathHighlight,
                        )
                    }
                }
            }
        }

        if (branch.children.isNotEmpty() && isExpanded) {
            branch.children.forEach { child ->
                LayerMapBranch(
                    branch = child,
                    depth = depth + 1,
                    currentPath = currentPath,
                    expandedById = expandedById,
                    onNavigateTo = onNavigateTo,
                )
            }
        }
    }
}

@Composable
internal fun LayerTrailRoute(
    projectName: String,
    pathNodes: List<Node>,
    currentPath: List<String>,
    onProjectClick: () -> Unit,
    onLayerClick: (Int) -> Unit,
) {
    val items = buildList {
        add(ProjectRouteItem(label = projectName, isRoot = true, nodeId = null))
        pathNodes.forEachIndexed { index, node ->
            add(ProjectRouteItem(label = node.title, isRoot = false, nodeId = node.id, depth = index + 1))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (item.isRoot) onProjectClick() else onLayerClick(index - 1)
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

