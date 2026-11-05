package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.Tag

// 48dp touch targets plus the card's existing 8dp top and bottom padding.
// Corner badges reserve their own measured clearance below.
internal val CompactCardMinHeight = 64.dp

/** The badge owns the actual corner; bottom-aligned 48dp actions always clear its measured height. */
@Composable
internal fun CompactCardHeader(tags: List<Tag>, stacked:Boolean=false, content: @Composable () -> Unit) {
    val minHeight=if(LocalBashPresentation.current) 48.dp else CompactCardMinHeight
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        Box(propagateMinConstraints = true) { content() }
        Box(Modifier.testTag("card-corner-tag")) { if (tags.isNotEmpty()) CardTagBadge(tags) }
    }) { slots, constraints ->
        val badge = slots[1].measure(constraints.copy(minWidth = 0, minHeight = 0,
            maxWidth = minOf(constraints.maxWidth, 96.dp.roundToPx())))
        val minimum = maxOf(minHeight.roundToPx(), badge.height + 48.dp.roundToPx() + 8.dp.roundToPx())
            .coerceAtMost(constraints.maxHeight)
        val body = slots[0].measure(constraints.copy(minHeight = maxOf(constraints.minHeight, minimum)))
        layout(body.width, body.height) {
            body.placeRelative(0, 0)
            badge.placeRelative(body.width - badge.width, 0)
        }
    }
}
