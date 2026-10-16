package com.r0ybt.arachn0de.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Meaning stays independent of theme identity, including light monochrome palettes. */
object SemanticColors {
    val Error = Color(0xFFFF8C8C)
    val HighPriority = Color(0xFFFFA45B)
    val Upcoming = Color(0xFFFFA45B)
    val Attention = Color(0xFFFFA45B)
    val ImageLink = Color(0xFF83CFFF)
    val SprintUnplanned = BlackWhite90.Inactive
    val SprintPlanned = BlackWhite90.Secondary
    val SprintDoing = BlackWhite90.Text
    val SprintDone = BlackWhite90.Selected
    val SprintValidated = BlackWhite90.Text
    val SprintText = BlackWhite90.Text
    val CompletedText = BlackWhite90.Secondary
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
    val Disabled: Color @Composable get() = if(LocalAppearancePalette.current.light) LocalAppearancePalette.current.muted else BlackWhite90.Inactive
    val TextSecondary: Color @Composable get() = LocalAppearancePalette.current.muted
    val Accent: Color @Composable get() = LocalAppearancePalette.current.primary
    val Primary: Color @Composable get() = LocalAppearancePalette.current.solidPrimary
    val Outline: Color @Composable get() = LocalAppearancePalette.current.outline
    val ControlSurface: Color @Composable get() = LocalAppearancePalette.current.controls
    val PathHighlight: Color @Composable get() = LocalAppearancePalette.current.secondary
    val Selection: Color @Composable get() = LocalAppearancePalette.current.selection
    val AccentSurface: Color @Composable get() = LocalAppearancePalette.current.accentSurface
    val OnPrimary: Color @Composable get() = LocalAppearancePalette.current.text
    val OnSolidPrimary: Color @Composable get() = LocalAppearancePalette.current.onSolidPrimary
    val ImageLink: Color @Composable get() = LocalAppearancePalette.current.semantic(SemanticColors.ImageLink)
    val Destructive: Color @Composable get() = LocalAppearancePalette.current.semantic(SemanticColors.Error)
    val Completed: Color @Composable get() = LocalAppearancePalette.current.semantic(SemanticColors.SprintDone)
    val TextCompleted: Color @Composable get() = LocalAppearancePalette.current.semantic(SemanticColors.CompletedText)
}

/** Light-theme status contrast; semantic roles remain unchanged. */
internal fun AppearancePalette.semantic(color:Color):Color = if(!light) color else when(color) {
    SemanticColors.Error->text
    SemanticColors.HighPriority,SemanticColors.Upcoming,SemanticColors.Attention->primary
    SemanticColors.ImageLink->primary
    SemanticColors.SprintDone->selection
    SemanticColors.SprintUnplanned->muted
    else->text
}
@Composable internal fun semanticColor(color:Color)=LocalAppearancePalette.current.semantic(color)
