package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.R

/** Preserve the original silhouettes with the current theme icon color. */
@Composable
internal fun PropertyIdentityIcon(description:Boolean=false,modifier:Modifier=Modifier.size(16.dp)) {
    Image(painterResource(if(description) R.drawable.arachn0de_description else R.drawable.arachn0de_title),
        contentDescription=null,modifier=modifier,contentScale=ContentScale.Fit,
        colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Icon))
}
