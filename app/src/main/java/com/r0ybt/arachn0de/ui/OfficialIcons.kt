package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.r0ybt.arachn0de.ui.theme.role
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
    Image(bitmap, description, modifier, contentScale = ContentScale.Fit,
        colorFilter=if(resource in setOf(R.drawable.bw90_project,R.drawable.bw90_layer)) ColorFilter.tint(Arachn0deColors.Icon) else null)

}

internal data class ProjectLockAction(val enabled: Boolean, val run: () -> Unit)
internal val LocalProjectLock = staticCompositionLocalOf<ProjectLockAction?> { null }
internal val LocalProjectActionsVisibility = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
internal val LocalQuickCaptureIndexSource=staticCompositionLocalOf<kotlinx.coroutines.flow.StateFlow<List<ElementResult>?>?> {null}
@Composable internal fun quickCaptureIndex():List<ElementResult>? {
    val source=LocalQuickCaptureIndexSource.current
    return if(source!=null) source.collectAsState().value else LocalQuickCaptureIndex.current
}
internal val LocalQuickCaptureIndex = staticCompositionLocalOf<List<ElementResult>> { emptyList() }

internal fun headerLockTint(palette:com.r0ybt.arachn0de.ui.theme.AppearancePalette,enabled:Boolean)=palette.role(if(enabled) "icons" else "disabled")

@Composable internal fun HeaderLockButton(global:Boolean=false) {
    if(!global && LocalGlobalNavigation.current?.bottomAccess==true) return
    val action=LocalProjectLock.current
    IconButton(onClick={action?.run?.invoke()},enabled=action?.enabled==true,
        modifier=Modifier.size(48.dp).semantics {if(action!=null)stateDescription="Sesión desbloqueada"}) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.LockOpen,"Bloquear",Modifier.size(24.dp),
            tint=headerLockTint(LocalAppearancePalette.current,action?.enabled==true))
    }
}

@Composable internal fun ProjectLockButton(modifier: Modifier = Modifier.widthIn(min=80.dp).heightIn(min=48.dp), boxed: Boolean = false) {
    val action = LocalProjectLock.current
    PrimaryControlButton(onClick={action?.run?.invoke()},enabled=action?.enabled==true,modifier=modifier) {
        if(boxed) androidx.compose.foundation.layout.Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Box(Modifier.size(40.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.lucide_lock),null,Modifier.size(28.dp),
                    tint=if(action?.enabled==true) Arachn0deColors.Icon else Arachn0deColors.Disabled)
            }
            androidx.compose.material3.Text("Bloquear",color=if(action?.enabled==true) Arachn0deColors.TextPrimary else Arachn0deColors.Disabled,style=androidx.compose.material3.MaterialTheme.typography.labelSmall)
        } else androidx.compose.material3.Text("Bloquear",color=if(action?.enabled==true) Arachn0deColors.TextPrimary else Arachn0deColors.Disabled,style=androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun ProjectQuickCaptureButton(modifier: Modifier = Modifier.size(48.dp), boxed: Boolean = false) {
    val repository = LocalElementAccess.current
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, enabled = repository != null, modifier = modifier.then(if(boxed) Modifier.clip(RoundedCornerShape(12.dp)).background(Arachn0deColors.ControlSurface) else Modifier)) {
        androidx.compose.material3.Text("Rápida",style=androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
    if (open && repository != null) QuickCaptureDialog(quickCaptureIndex(), repository, { open = false })
}
