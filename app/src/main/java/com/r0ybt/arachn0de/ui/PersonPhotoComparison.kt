package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.BlackWhite90
import com.r0ybt.arachn0de.ui.theme.AppearanceTheme

/** Preview-only comparison. No preference, source-image transformation or definitive default. */
@Composable
internal fun PersonPhotoComparison(modifier: Modifier = Modifier, profile: Boolean = false,
    content: @Composable (Modifier) -> Unit) {
    var variant by rememberSaveable { mutableStateOf<String?>(null) }
    val shape: Shape = if(profile) RoundedCornerShape(10.dp) else RectangleShape
    val colors = when(variant) {
        "A" -> listOf(BlackWhite90.Border, BlackWhite90.Inactive, BlackWhite90.Text)
        "B" -> listOf(BlackWhite90.Copper, AppearanceTheme.HeavyGold.palette.primary)
        else -> null
    }
    BoxWithConstraints(modifier) {
    val frameWidth = if(constraints.hasBoundedHeight) minOf(maxWidth, (maxHeight - 56.dp).coerceAtLeast(48.dp)) else maxWidth
    Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Box(Modifier.width(frameWidth)
            .then(if(colors != null) Modifier.border(2.dp, Brush.linearGradient(colors), shape) else Modifier)
            .padding(4.dp).testTag("person-preview-frame:${variant ?: "none"}")) {
            content(Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = variant == "A", onClick = { variant = if(variant == "A") null else "A" },
                label = { Text("A · Gris") }, modifier = Modifier.testTag("person-border-A"))
            FilterChip(selected = variant == "B", onClick = { variant = if(variant == "B") null else "B" },
                label = { Text("B · Naranja-amarillo") }, modifier = Modifier.testTag("person-border-B"))
        }
    }
    }
}
