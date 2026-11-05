package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import com.r0ybt.arachn0de.ui.theme.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal object NavigationBarStyle {
    val margin=4.dp
    val padding=4.dp
    val target=48.dp
    val icon=18.dp
}

internal val navigationBarBackground:androidx.compose.ui.graphics.Color
    @Composable get() {
        val pureBlack=LocalAppearancePreferences.current?.settings?.theme==AppearanceTheme.PureBlack ||
            LocalAppearancePalette.current==AppearanceTheme.PureBlack.palette
        return if(pureBlack) Arachn0deColors.Surface else Arachn0deColors.ControlSurface
    }

@Composable internal fun NavigationBarSeparator() {
    VerticalDivider(Modifier.height(24.dp),thickness=.5.dp,color=Arachn0deColors.Outline)
}

@Composable internal fun NavigationBarFrame(tag:String,content:@Composable ()->Unit) {
    // A retained but hidden navigation row must not leave an empty second frame.
    Layout(modifier=Modifier.fillMaxWidth().padding(horizontal=NavigationBarStyle.margin),content={
    Surface(Modifier.fillMaxWidth().testTag(tag).clip(RoundedCornerShape(16.dp)),
        color=navigationBarBackground,shape=RoundedCornerShape(16.dp),
        border=BorderStroke(.5.dp,Arachn0deColors.Outline)) {content()}
    }) {children,constraints->
        val margin=2.dp.roundToPx()
        val child=children.single().measure(constraints.copy(minHeight=0,maxHeight=(constraints.maxHeight-margin*2).coerceAtLeast(0)))
        val height=if(child.height==0)0 else child.height+margin*2
        layout(constraints.maxWidth,height) {if(height>0)child.placeRelative(0,margin)}
    }
}

@Composable internal fun NavigationBarIcon(icon:ImageVector,modifier:Modifier=Modifier.size(NavigationBarStyle.icon),
    tint:androidx.compose.ui.graphics.Color=Arachn0deColors.Icon,label:String?=null) {
    val resource=blockOneIconResource(label) ?: when(icon.name.substringAfterLast('.')) {
        "Add"->com.r0ybt.arachn0de.R.drawable.lucide_plus
        "Layers"->com.r0ybt.arachn0de.R.drawable.lucide_layers
        "Description"->com.r0ybt.arachn0de.R.drawable.lucide_sticky_note
        "MoreVert"->com.r0ybt.arachn0de.R.drawable.lucide_ellipsis_vertical
        else->null
    }
    if(resource!=null) Icon(androidx.compose.ui.res.painterResource(resource),null,modifier,tint=tint)
    else LucideIcon(icon,null,modifier,tint=tint)
}

@Composable internal fun RowScope.NavigationBarAction(label:String,icon:ImageVector,enabled:Boolean=true,
    tag:String="",onClick:()->Unit) {
    TextButton(onClick=onClick,enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=NavigationBarStyle.target).testTag(tag).semantics {contentDescription=if(label=="Menú")"Abrir menú" else label},
        contentPadding=PaddingValues(NavigationBarStyle.padding),
        colors=ButtonDefaults.textButtonColors(contentColor=Arachn0deColors.TextSecondary)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            NavigationBarIcon(icon,Modifier.size(NavigationBarStyle.icon),tint=if(enabled)Arachn0deColors.Icon else Arachn0deColors.Disabled,label=label)
            Text(label,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center)
        }
    }
}

/** One header and one footer geometry for both sorting and filtering. */
@Composable internal fun OrderingPanel(onDismiss:()->Unit,title:String,body:@Composable ()->Unit,actions:@Composable RowScope.()->Unit) {
    ModularPanelDialog(onDismissRequest=onDismiss,compact=true,fieldOwnsScroll=true,plainBody=true,
        title={Text(title,Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.titleMedium)},
        text={Box(Modifier.padding(vertical=8.dp)) {body()}},confirmButton={},actionsContent={
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),content=actions)
        })
}

@Composable internal fun RowScope.OrderingPanelButton(label:String,tag:String="",enabled:Boolean=true,onClick:()->Unit) {
    PrimaryControlButton(onClick=onClick,enabled=enabled,
        modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag(tag)) {Text(label,style=MaterialTheme.typography.labelSmall)}
}
