package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.filled.Lock
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import com.r0ybt.arachn0de.ui.theme.AppearanceTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.access.ElementResult

/** Original PNGs remain untouched. Decode only enough pixels for this icon's display size. */
@Composable
internal fun OfficialIcon(@DrawableRes resource: Int, description: String? = null, size: Dp = 36.dp,
    modifier: Modifier = Modifier.size(size)) {
    val resources = LocalContext.current.resources
    val pixels = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap = remember(resources, resource, pixels) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeResource(resources, resource, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= pixels) sample *= 2
        BitmapFactory.decodeResource(resources, resource, BitmapFactory.Options().apply {
            inSampleSize = sample; inScaled = false
        }).asImageBitmap()
    }
    val palette=LocalAppearancePalette.current
    val brand=resource==R.drawable.bw90_brand_logo || resource==R.drawable.bw90_brand_mark
    val filter=if(brand && (palette==AppearanceTheme.NightBlue.palette || palette==AppearanceTheme.DayGrey.palette)) {
        val low=palette.background;val high=if(palette.light) palette.text else palette.primary
        val rows=listOf(low.red to high.red,low.green to high.green,low.blue to high.blue).flatMap {(a,b)->listOf((b-a)*.2126f,(b-a)*.7152f,(b-a)*.0722f,0f,a*255)}+listOf(0f,0f,0f,1f,0f)
        ColorFilter.colorMatrix(ColorMatrix(rows.toFloatArray()))
    } else null
    Image(bitmap, description, modifier, contentScale = ContentScale.Fit,colorFilter=filter)
}

internal data class ProjectLockAction(val enabled: Boolean, val run: () -> Unit)
internal val LocalProjectLock = staticCompositionLocalOf<ProjectLockAction?> { null }
internal val LocalProjectActionsVisibility = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
internal val LocalQuickCaptureIndex = staticCompositionLocalOf<List<ElementResult>> { emptyList() }

@Composable internal fun HeaderLockButton() {
    val action=LocalProjectLock.current
    IconButton(onClick={action?.run?.invoke()},enabled=action?.enabled==true,
        modifier=Modifier.size(48.dp)) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Lock,"Bloquear",Modifier.size(24.dp),
            tint=androidx.compose.ui.graphics.Color(0xFFFF9800).copy(alpha=if(action?.enabled==true) 1f else .38f))
    }
}

@Composable internal fun ProjectLockButton(modifier: Modifier = Modifier.widthIn(min=80.dp).heightIn(min=48.dp), boxed: Boolean = false) {
    val action = LocalProjectLock.current
    PrimaryControlButton(onClick={action?.run?.invoke()},enabled=action?.enabled==true,modifier=modifier) {
        if(boxed) androidx.compose.foundation.layout.Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Box(Modifier.size(40.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.lucide_lock),null,Modifier.size(28.dp),
                    tint=if(action?.enabled==true) Arachn0deColors.TextPrimary else Arachn0deColors.Disabled)
            }
            androidx.compose.material3.Text("Bloquear",color=if(action?.enabled==true) Arachn0deColors.TextPrimary else Arachn0deColors.Disabled,style=androidx.compose.material3.MaterialTheme.typography.labelSmall)
        } else androidx.compose.material3.Text("Bloquear",color=if(action?.enabled==true) Arachn0deColors.TextPrimary else Arachn0deColors.Disabled,style=androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun ProjectQuickCaptureButton(modifier: Modifier = Modifier.size(48.dp), boxed: Boolean = false) {
    val repository = LocalElementAccess.current
    val index = LocalQuickCaptureIndex.current
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, enabled = repository != null, modifier = modifier.then(if(boxed) Modifier.clip(RoundedCornerShape(12.dp)).background(Arachn0deColors.ControlSurface) else Modifier)) {
        androidx.compose.material3.Text("Rápida",style=androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
    if (open && repository != null) QuickCaptureDialog(index, repository, { open = false })
}
