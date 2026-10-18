package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*

internal val LocalBashPresentation=staticCompositionLocalOf { false }
internal val BashBackground=BashColors.Background
private data class BashBase(val palette:AppearancePalette,val typography:Typography,val colors:ColorScheme,val text:androidx.compose.ui.text.TextStyle,val contentColor:Color)
private val LocalBashBase=staticCompositionLocalOf<BashBase?> {null}


/** A presentation scope, never a navigation destination or an alternative data source. */
@Composable internal fun BashGeneralScope(content:@Composable ()->Unit) {
    val enabled=LocalAppearancePreferences.current?.settings?.bashTheme==true
    BashPresentationScope(enabled,content)
}

@Composable internal fun BashPresentationScope(enabled:Boolean,content:@Composable ()->Unit) {
    if(!enabled) {content();return}
    ScreenOrientation(LocalAudioFooterVisible.current)
    val original=LocalAppearancePalette.current
    val context=androidx.compose.ui.platform.LocalContext.current
    val activity=remember(context) {generateSequence(context) {(it as? android.content.ContextWrapper)?.baseContext}.filterIsInstance<android.app.Activity>().firstOrNull()}
    val visible=LocalAudioFooterVisible.current
    SideEffect {if(visible) activity?.let {
        val bars=androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView)
        bars.isAppearanceLightStatusBars=false;bars.isAppearanceLightNavigationBars=false
    }}
    DisposableEffect(activity,visible,original.light) {
        onDispose {if(visible) activity?.let {
            val bars=androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView)
            bars.isAppearanceLightStatusBars=original.light;bars.isAppearanceLightNavigationBars=original.light
        }}
    }
    val palette=BashColors.palette(original)
    val t=MaterialTheme.typography
    val base=BashBase(original,t,MaterialTheme.colorScheme,LocalTextStyle.current,LocalContentColor.current)
    val mono=t.copy(
        displayLarge=t.displayLarge.copy(fontFamily=FontFamily.Monospace),displayMedium=t.displayMedium.copy(fontFamily=FontFamily.Monospace),displaySmall=t.displaySmall.copy(fontFamily=FontFamily.Monospace),
        headlineLarge=t.headlineLarge.copy(fontFamily=FontFamily.Monospace),headlineMedium=t.headlineMedium.copy(fontFamily=FontFamily.Monospace),headlineSmall=t.headlineSmall.copy(fontFamily=FontFamily.Monospace),
        titleLarge=t.titleLarge.copy(fontFamily=FontFamily.Monospace),titleMedium=t.titleMedium.copy(fontFamily=FontFamily.Monospace),titleSmall=t.titleSmall.copy(fontFamily=FontFamily.Monospace),
        bodyLarge=t.bodyLarge.copy(fontFamily=FontFamily.Monospace),bodyMedium=t.bodyMedium.copy(fontFamily=FontFamily.Monospace),bodySmall=t.bodySmall.copy(fontFamily=FontFamily.Monospace),
        labelLarge=t.labelLarge.copy(fontFamily=FontFamily.Monospace),labelMedium=t.labelMedium.copy(fontFamily=FontFamily.Monospace),labelSmall=t.labelSmall.copy(fontFamily=FontFamily.Monospace))
    CompositionLocalProvider(LocalBashPresentation provides true,LocalAppearancePalette provides palette,LocalBashBase provides base) {
        MaterialTheme(colorScheme=palette.materialColors(),typography=mono) {
            ProvideTextStyle(mono.bodyMedium,content)
        }
    }
}

/** Traditional mode keeps the existing card presentation. */
@Composable internal fun GeneralCard(modifier:Modifier=Modifier,shape:Shape=CardDefaults.shape,
    colors:CardColors=CardDefaults.cardColors(),elevation:CardElevation=CardDefaults.cardElevation(),
    border:BorderStroke?=null,bashEntry:Boolean=false,onClick:(()->Unit)?=null,enabled:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    if(LocalBashPresentation.current) {
        Column(modifier.then(if(onClick!=null) Modifier.clickable(enabled=enabled,onClick=onClick) else Modifier)) {
            content()
            if(bashEntry) BashAsciiSeparator()
        }
    } else if(onClick!=null) Card(onClick=onClick,enabled=enabled,modifier=modifier,shape=shape,colors=colors,elevation=elevation,border=border,content=content)
    else Card(modifier=modifier,shape=shape,colors=colors,elevation=elevation,border=border,content=content)
}

internal fun bashFileName(title:String,purpose:NodePurpose) = title + when(purpose) {
    NodePurpose.LAYER -> "/"
    NodePurpose.NOTE -> ".note"
    else -> ".task"
}
internal fun bashListing(title:String,directory:Boolean):AnnotatedString = buildAnnotatedString {
    append(if(directory) "drwx------ " else "-rw------- ")
    withStyle(SpanStyle(color=if(directory) BashColors.Electric else BashColors.Terminal)) {append(title)}
}

/** Paths are display strings only; ancestry comes from the existing parent IDs. */
internal fun bashLocation(project:String,node:Node?,nodes:Map<String,Node>):String {
    val ancestors=mutableListOf<String>()
    val seen=hashSetOf<String>()
    var current=if(node?.isStructural==true) node else node?.parentId?.let(nodes::get)
    while(current!=null && current.projectId==node?.projectId && seen.add(current.id)) {
        ancestors.add(current.title);current=current.parentId?.let(nodes::get)
    }
    return (listOf("~","General",project)+ancestors.asReversed()).joinToString("/")
}
internal fun bashQuote(value:String)="'"+value.replace("'","'\\''")+"'"

@Composable internal fun BashPrompt(path:String,command:String) {
    if(!LocalBashPresentation.current) return
    Text("$path $ $command",Modifier.fillMaxWidth().padding(vertical=4.dp).testTag("bash-prompt").semantics {stateDescription="Salida visual; permisos decorativos; no ejecuta comandos"},
        style=MaterialTheme.typography.bodyMedium,color=BashColors.Terminal,softWrap=true)
}

@Composable internal fun BashNodeProperties(node:Node) {
    if(!LocalBashPresentation.current) return
    Column(Modifier.fillMaxWidth().testTag("bash-properties:${node.id}")) {
        Text("id=${node.id}")
        Text("status=${if(node.isCompleted) "completed" else "pending"}")
        Text("priority=${node.effectivePriority.name.lowercase(java.util.Locale.ROOT)}")
        node.workState?.let {Text("work_state=${it.label}")}
        node.startAt?.let {Text("start=${formatTaskDate(it)}")}
        node.dueAt?.let {Text("due=${formatTaskDate(it)}")}
        node.obligation?.let {Text("payment=${Money.format(it,androidx.compose.ui.platform.LocalConfiguration.current.locales[0])}")}
    }
}

/** Persistent modules keep their normal layout and typography, with the selected global colors. */
@Composable internal fun BashTraditionalModules(content:@Composable ()->Unit) {
    val themedContent:@Composable ()->Unit={
        BashMenuScope {
            if(bashMenusEnabled()) ProvideTextStyle(LocalTextStyle.current.copy(color=BashColors.Soft),content)
            else content()
        }
    }
    val base=LocalBashBase.current
    if(base==null) {themedContent();return}
    CompositionLocalProvider(LocalBashPresentation provides false,LocalBashBase provides null,
        LocalAppearancePalette provides base.palette,LocalContentColor provides base.contentColor) {
        MaterialTheme(colorScheme=base.colors,typography=base.typography) {ProvideTextStyle(base.text,themedContent)}
    }
}

/** One line of glyphs after the entry and all its original controls. */
@Composable private fun BashAsciiSeparator() {
    val measurer=androidx.compose.ui.text.rememberTextMeasurer()
    val style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace)
    val glyph=measurer.measure("-",style).size.width.coerceAtLeast(1)
    val density=androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=2.dp)) {
        val count=(with(density) {maxWidth.roundToPx()}/glyph).coerceAtLeast(1)
        Text("-".repeat(count),Modifier.fillMaxWidth().testTag("bash-entry-separator"),
            color=BashColors.Separator,style=style,maxLines=1,softWrap=false)
    }
}

@Composable internal fun bashMenusEnabled()=LocalBashPresentation.current || LocalAppearancePreferences.current?.settings?.bashTheme==true

/** Shared navigation adopts colors only; unrelated screens retain their own presentation. */
@Composable internal fun BashMenuScope(content:@Composable ()->Unit) {
    val enabled=bashMenusEnabled()
    if(!enabled) {content();return}
    val palette=BashColors.palette(LocalAppearancePalette.current)
    CompositionLocalProvider(LocalAppearancePalette provides palette,LocalContentColor provides BashColors.Soft) {
        MaterialTheme(colorScheme=palette.materialColors(),content=content)
    }
}
