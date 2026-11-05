package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.ui.theme.*

@Composable internal fun ThemeDrawerIdentity(topInset:androidx.compose.ui.unit.Dp=0.dp,height:androidx.compose.ui.unit.Dp=88.dp) {
    OfficialIcon(R.drawable.bw90_brand_logo,"Logo de Arachn0de",size=96.dp,modifier=Modifier.fillMaxWidth().padding(top=topInset).height(height).testTag("drawer-logo"))
}
