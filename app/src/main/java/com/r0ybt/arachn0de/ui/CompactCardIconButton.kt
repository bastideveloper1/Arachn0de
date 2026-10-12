package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

@Composable internal fun CompactCardIconButton(icon:ImageVector,description:String,onClick:()->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,selected:Boolean?=null,tint:Color=MaterialTheme.colorScheme.primary) {
    Box(modifier.size(48.dp).alpha(if(enabled) 1f else .38f).semantics {selected?.let {this.selected=it}}
        .clickable(enabled=enabled,role=Role.Button,onClickLabel=description,onClick=onClick),contentAlignment=Alignment.Center) {
        Surface(Modifier.size(28.dp),shape=RoundedCornerShape(6.dp),color=Arachn0deColors.ControlSurface,shadowElevation=0.dp,tonalElevation=0.dp) {
            Box(contentAlignment=Alignment.Center) {LucideIcon(icon,description,Modifier.size(18.dp),tint=tint)}
        }
    }
}
