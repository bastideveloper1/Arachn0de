package com.r0ybt.arachn0de.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal data class CompletionControlColors(val fill: Color, val mark: Color, val border: Color)

/** Normal tasks follow appearance; Sprint keeps its existing semantic color treatment. */
@Composable
internal fun completionControlColors(sprint: Boolean, completed: Boolean, enabled: Boolean): CompletionControlColors {
    val fill = if (sprint) { if (completed) Arachn0deColors.Completed else Arachn0deColors.Primary } else Arachn0deColors.Accent
    val mark = if (sprint) Arachn0deColors.OnPrimary else MaterialTheme.colorScheme.onPrimary
    val border = if (sprint) Arachn0deColors.TextSecondary else Arachn0deColors.Accent
    // Sprint already has its final-state styling; preserve it when disabling the last step.
    val alpha = if (enabled || sprint) 1f else .38f
    return CompletionControlColors(fill.copy(alpha = alpha), mark.copy(alpha = alpha), border.copy(alpha = alpha))
}
