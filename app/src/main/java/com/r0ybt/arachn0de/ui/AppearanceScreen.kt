package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.LocalFireDepartment

import androidx.compose.material.icons.filled.Flag

import kotlinx.coroutines.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.*

@Composable
internal fun AppearanceScreen(onBack: () -> Unit, preferences: AppearancePreferences? = LocalAppearancePreferences.current) {
    // MainActivity hides the appearance local while the external picker covers the app.
    // Keep this composition's preferences, not a new global/session cache. A real lock
    // removes the authenticated composition and its retained reference as before.
    val sessionPreferences=remember {mutableStateOf(preferences)}
    SideEffect {if(preferences!=null)sessionPreferences.value=preferences}
    val available=preferences ?: sessionPreferences.value ?: return
    AppearanceScreenContent(onBack,available)
}

@Composable private fun AppearanceScreenContent(onBack:()->Unit,preferences:AppearancePreferences) {
    BackHandler(onBack = onBack)
    val settings = preferences.settings
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    fun perform(action:suspend()->Unit) {if(busy)return;busy=true;scope.launch {try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo completar la operación."} finally {busy=false}}}
    fun configure(action:()->Unit) {perform {withContext(kotlinx.coroutines.Dispatchers.IO+NonCancellable) {action()}}}

    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlobalChromeActions(leading=true)
                IconButton(onClick = onBack) { LucideIcon(Icons.AutoMirrored.Filled.ArrowBack,"Volver",tint=androidx.compose.material3.LocalContentColor.current) }
                NavigationHeading("Apariencia",Modifier.weight(1f))
                GlobalChromeActions()
            }
            OperationProgress(busy)
            Text("Preview", style = MaterialTheme.typography.titleSmall)
            AppearancePreview()
            Text("Tema", style = MaterialTheme.typography.titleSmall)
            selectableAppearanceThemes.forEach { theme ->
                val selected = settings.theme == theme
                Row(Modifier.fillMaxWidth().testTag("appearance-theme:${theme.name}")
                    .selectable(selected, enabled=!busy, role = Role.RadioButton, onClick = { configure {preferences.setTheme(theme)} })
                    .background(if (selected) Arachn0deColors.AccentSurface else Arachn0deColors.Surface, RoundedCornerShape(10.dp))
                    .border(1.dp, if (selected) Arachn0deColors.Accent else Arachn0deColors.Outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected, onClick = null)
                    // Preview the actual backgrounds; primary/secondary may be near-white text colors.
                    Box(Modifier.size(14.dp).background(theme.palette.background, CircleShape))
                    Box(Modifier.size(14.dp).background(theme.palette.surface, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(5.dp).background(theme.palette.primary, CircleShape))
                    }
                    Text(theme.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Sonido al completar tareas",Modifier.weight(1f))
                Switch(settings.completionSoundEnabled,onCheckedChange={enabled->scope.launch {
                    try {preferences.setCompletionSoundEnabled(enabled)} catch(e:Exception) {error=e.message ?: "No se pudo guardar el sonido."}
                }},enabled=!busy)
            }
            Text("Orientación del selector de estados", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                com.r0ybt.arachn0de.ui.theme.StatusSelectorDirection.entries.forEach {direction->
                    FilterChip(settings.statusSelectorDirection==direction,enabled=!busy,onClick={configure {preferences.setStatusSelectorDirection(direction)}},label={Text(direction.label)})
                }
            }
            Text("Tamaño de texto", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceTextSize.entries.forEach { size ->
                    FilterChip(settings.textSize == size, enabled=!busy,onClick = { configure {preferences.setTextSize(size)} },
                        label = { Text(size.label) }, modifier = Modifier.testTag("appearance-size:${size.name}"))
                }
            }
            IndicatorColorSettings(preferences,busy) {action->configure(action)}
        }
    }
    error?.let {message->AlertDialog(onDismissRequest={error=null},text={Text(message)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}

/** Drawn locally: no Node, repository, interaction, or database writes. */
@Composable
internal fun AppearancePreview() {
    GeneralCard(Modifier.fillMaxWidth().testTag("appearance-preview"),
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
                    LucideIcon(Icons.Default.Flag,"Prioritario",Modifier.size(16.dp),tint=cardIndicatorColor("high",true))
                    Text("Hoy · Alex", fontSize = ContentTypography.Metadata, color = Arachn0deColors.TextSecondary)
                }
            }
        }
    }
}

@Composable internal fun IndicatorSettingIcon(name:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tint:Color) {
    when(name) {
        "verify"->Icon(androidx.compose.ui.res.painterResource(com.r0ybt.arachn0de.R.drawable.lucide_scan_eye),null,Modifier.size(20.dp),tint=tint)
        "birthday","effort"->Icon(if(name=="birthday") Icons.Default.Cake else Icons.Default.LocalFireDepartment,null,Modifier.size(20.dp),tint=tint)
        else->LucideIcon(icon,null,Modifier.size(20.dp),tint=tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorColorSettings(preferences:AppearancePreferences,busy:Boolean,configure:((()->Unit))->Unit) {
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var effortOpen by rememberSaveable {mutableStateOf(false)}
    var previewLevel by rememberSaveable {mutableIntStateOf(1)}
    Text("Indicadores",style=MaterialTheme.typography.titleSmall)
    val indicators=customizableCardIndicators+CardIndicator("effort",Icons.Default.LocalFireDepartment,"Dificultad",IndicatorTone.CONTENT)
    Column(Modifier.fillMaxWidth().testTag("indicator-settings-grid"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        indicators.chunked(2).forEach {pair->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                pair.forEach {indicator->
                    val color=if(indicator.name=="effort")effortIndicatorColor(previewLevel) else cardIndicatorColor(indicator.name,true)
                    Surface(onClick={if(indicator.name=="effort")effortOpen=true else selected=indicator.name},
                        modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=56.dp).testTag("indicator-setting:${indicator.name}")
                            .semantics {stateDescription=preferences.settings.indicatorColors[if(indicator.name=="effort")"effort_$previewLevel" else indicator.name]?.label ?: "Color original"},
                        color=Arachn0deColors.ControlSurface,shape=RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            IndicatorSettingIcon(indicator.name,indicator.icon,color)
                            Text(indicator.label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,minLines=2)
                        }
                    }
                }
                if(pair.size==1) Spacer(Modifier.weight(1f))
            }
        }
    }
    TextButton(enabled=!busy,onClick={configure(preferences::resetIndicatorColors)},modifier=Modifier.testTag("indicator-reset")) {Text("Restablecer colores")}
    if(effortOpen) BottomModulesSheet(onDismissRequest={effortOpen=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Arachn0deColors.Panel,contentColor=Arachn0deColors.TextPrimary) {
        Column(Modifier.fillMaxWidth().padding(12.dp).testTag("effort-colors-panel"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Dificultad",style=MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                for(level in 1..5) IconButton(onClick={previewLevel=level;selected="effort_$level"},
                    modifier=Modifier.weight(1f).height(48.dp).testTag("effort-color-level:$level")
                        .semantics {contentDescription="Color de dificultad $level"}) {TaskEffortMini("color-level:$level",level)}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(enabled=!busy,onClick={configure(preferences::resetEffortColors)},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("effort-colors-reset")) {Text("Restablecer dificultad")}
                TextButton(onClick={effortOpen=false},modifier=Modifier.heightIn(min=48.dp)) {Text("Cerrar")}
            }
        }
    }
    selected?.let {name->
        ModularPanelDialog(onDismissRequest={selected=null},
            title={Text(if(name.startsWith("effort_")) "Dificultad · ${name.removePrefix("effort_")}" else customizableCardIndicators.first {it.name==name}.label)},
            text={Column {
                IndicatorColor.entries.forEach {color->
                    Row(Modifier.fillMaxWidth().testTag("indicator-color:${color.name}")
                        .selectable(preferences.settings.indicatorColors[name]==color,enabled=!busy,role=Role.RadioButton,onClick={configure {preferences.setIndicatorColor(name,color)};selected=null})
                        .padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(20.dp).background(color.color,CircleShape).border(1.dp,Arachn0deColors.Outline,CircleShape))
                        Text(color.label)
                    }
                }
            }},confirmButton={TextButton(onClick={selected=null}) {Text("Cerrar")}})
    }
}
