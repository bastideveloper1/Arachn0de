package com.r0ybt.arachn0de.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

internal val HierarchyCardIconSize = 18.dp
internal val CardTitleAxis = HorizontalAlignmentLine { a, b -> minOf(a, b) }

/** Export the measured first line's center through the content column to its sibling icon. */
internal fun Modifier.cardTitleAxis(center: Int) = layout { measurable, constraints ->
    val title = measurable.measure(constraints)
    layout(title.width, title.height, mapOf(CardTitleAxis to center)) { title.placeRelative(0, 0) }
}

@Composable
internal fun HierarchyCardIcon(@DrawableRes resource: Int, description: String) {
    OfficialIcon(resource, description, size = HierarchyCardIconSize)
}
