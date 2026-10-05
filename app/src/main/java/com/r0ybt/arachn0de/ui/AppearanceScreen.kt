package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.*

@Composable
internal fun AppearanceScreen(onBack: () -> Unit, preferences: AppearancePreferences = checkNotNull(LocalAppearancePreferences.current)) {
    BackHandler(onBack = onBack)
    val settings = preferences.settings
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                Text("Apariencia", style = MaterialTheme.typography.titleLarge)
            }
            Text("Preview", style = MaterialTheme.typography.titleSmall)
            AppearancePreview()
            Text("Tema", style = MaterialTheme.typography.titleSmall)
            AppearanceTheme.entries.forEach { theme ->
                val selected = settings.theme == theme
                Row(Modifier.fillMaxWidth().testTag("appearance-theme:${theme.name}")
                    .selectable(selected, role = Role.RadioButton, onClick = { preferences.setTheme(theme) })
                    .background(if (selected) Arachn0deColors.AccentSurface else Arachn0deColors.Surface, RoundedCornerShape(10.dp))
                    .border(1.dp, if (selected) Arachn0deColors.Accent else Arachn0deColors.Outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected, onClick = null)
                    Box(Modifier.size(14.dp).background(theme.palette.primary, CircleShape))
                    Box(Modifier.size(14.dp).background(theme.palette.secondary, CircleShape))
                    Text(theme.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Tamaño de texto", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceTextSize.entries.forEach { size ->
                    FilterChip(settings.textSize == size, onClick = { preferences.setTextSize(size) },
                        label = { Text(size.label) }, modifier = Modifier.testTag("appearance-size:${size.name}"))
                }
            }
        }
    }
}

/** Drawn locally: no Node, repository, interaction, or database writes. */
@Composable
internal fun AppearancePreview() {
    Card(Modifier.fillMaxWidth().testTag("appearance-preview"),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Arachn0deColors.Outline)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.RadioButtonUnchecked, null, tint = Arachn0deColors.Accent, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Preparar versión Desktop", fontSize = ContentTypography.CardTitle, lineHeight = ContentTypography.CardTitleLine,
                    fontWeight = FontWeight.SemiBold, color = Arachn0deColors.PathHighlight, modifier = Modifier.testTag("appearance-preview-title"))
                Text("Revisar contrato de dominio", fontSize = ContentTypography.Description, lineHeight = ContentTypography.DescriptionLine,
                    color = Arachn0deColors.TextSecondary, modifier = Modifier.testTag("appearance-preview-description"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Prioridad alta", fontSize = ContentTypography.Metadata, color = SemanticColors.HighPriority)
                    Text("Hoy · Alex", fontSize = ContentTypography.Metadata, color = Arachn0deColors.TextSecondary)
                }
            }
        }
    }
}
