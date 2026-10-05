package com.r0ybt.arachn0de.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Meaning stays independent of theme identity, including monochrome Minimalist. */
object SemanticColors {
    val Error = Color(0xFFFF8C8C)
    val HighPriority = Color(0xFFFFA45B)
    val Upcoming = Color(0xFFFFA45B)
    val Attention = Color(0xFFFFA45B)
    val ImageLink = Color(0xFF83CFFF)
    val SprintUnplanned = Color(0xFF3A2C52)
    val SprintPlanned = Color(0xFFB9C6E8)
    val SprintDoing = Color(0xFF7E61FF)
    val SprintDone = Color(0xFF6A4AC6)
    val SprintValidated = Color(0xFFB99BFF)
    val SprintText = Color(0xFFB99BFF)
    val CompletedText = Color(0xFFE3DCF7)
}

/** Existing token names keep all callers on one composition-scoped appearance palette. */
object Arachn0deColors {
    val Background: Color @Composable get() = LocalAppearancePalette.current.background
    val BackgroundMiddle: Color @Composable get() = LocalAppearancePalette.current.middle
    val BackgroundEnd: Color @Composable get() = LocalAppearancePalette.current.end
    val Scrim: Color @Composable get() = LocalAppearancePalette.current.scrim
    val Surface: Color @Composable get() = LocalAppearancePalette.current.surface
    val SurfaceRaised: Color @Composable get() = LocalAppearancePalette.current.raised
    val TextPrimary: Color @Composable get() = LocalAppearancePalette.current.text
    val TextSecondary: Color @Composable get() = LocalAppearancePalette.current.muted
    val Accent: Color @Composable get() = LocalAppearancePalette.current.primary
    val Primary: Color @Composable get() = LocalAppearancePalette.current.solidPrimary
    val Outline: Color @Composable get() = LocalAppearancePalette.current.outline
    val ControlSurface: Color @Composable get() = LocalAppearancePalette.current.controls
    val PathHighlight: Color @Composable get() = LocalAppearancePalette.current.secondary
    val Selection: Color @Composable get() = LocalAppearancePalette.current.selection
    val AccentSurface: Color @Composable get() = LocalAppearancePalette.current.accentSurface
    val OnPrimary = Color.White
    val OnSolidPrimary: Color @Composable get() = LocalAppearancePalette.current.onSolidPrimary
    val ImageLink = SemanticColors.ImageLink
    val Destructive = SemanticColors.Error
    val Completed = SemanticColors.SprintDone
    val TextCompleted = SemanticColors.CompletedText
}
