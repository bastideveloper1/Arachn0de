package com.r0ybt.arachn0de.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private fun Context.orientationActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.orientationActivity()
    else -> null
}

/** Destination-scoped exception to the manifest's portrait default; no timer or draft ownership. */
@Composable
internal fun ScreenOrientation(allowLandscape: Boolean = true) {
    val context = LocalContext.current
    val activity = remember(context) { context.orientationActivity() } ?: return
    DisposableEffect(activity) {
        onDispose {
            if (!activity.isChangingConfigurations && activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
    SideEffect {
        val requested = if (allowLandscape) ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (activity.requestedOrientation != requested) activity.requestedOrientation = requested
    }
}
