package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Consume system bars, cutouts and IME once, including lateral cutouts in landscape. */
@Composable
internal fun AppSafeArea(
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Arachn0deColors.Background).windowInsetsPadding(insets)) {
        content()
    }
}
