package com.r0ybt.arachn0de.ui

import android.graphics.Movie
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled public branding only; no credentials, decrypted files or network. */
@Suppress("DEPRECATION")
@Composable internal fun AnimatedBrandLogo(modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val movie by produceState<Movie?>(null,context) {
        value=withContext(Dispatchers.IO) {
            context.assets.open("branding/logomovimiento.gif").use {Movie.decodeStream(it)}
        }
    }
    val view=remember(context) {ContentGifView(context).apply {contentDescription="Logo animado de Arachn0de"}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view,lifecycle) {
        fun sync()=view.playing(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer=LifecycleEventObserver {_,_->sync()}
        lifecycle.addObserver(observer);sync()
        onDispose {lifecycle.removeObserver(observer);view.release()}
    }
    AndroidView(factory={view},modifier=modifier,update={it.load(movie)})
}
