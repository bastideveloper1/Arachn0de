package com.r0ybt.arachn0de.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
internal class ContentGifView(context:Context):View(context) {
    private var movie:Movie?=null
    private var started=0L
    private var running=false
    internal var drawnTime=0;private set
    init {setLayerType(LAYER_TYPE_SOFTWARE,null);contentDescription="GIF animado"}
    fun load(value:Movie?) {if(movie===value)return;movie=value;started=SystemClock.uptimeMillis();drawnTime=0;invalidate()}
    fun playing(value:Boolean) {running=value;if(value)invalidate()}
    fun release() {running=false;movie=null}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val image=movie ?: return
        if(running)drawnTime=((SystemClock.uptimeMillis()-started)%image.duration().coerceAtLeast(100)).toInt()
        image.setTime(drawnTime)
        val factor=minOf(width.toFloat()/image.width(),height.toFloat()/image.height())
        canvas.save();canvas.translate((width-image.width()*factor)/2,(height-image.height()*factor)/2);canvas.scale(factor,factor);image.draw(canvas,0f,0f);canvas.restore()
        if(running && isShown && windowVisibility==VISIBLE)postInvalidateDelayed(20)
    }
    override fun onWindowVisibilityChanged(visibility:Int) {super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE && running)invalidate()}
}

@Suppress("DEPRECATION")
@Composable internal fun AnimatedAttachment(row:AttachmentFileEntity,repository:AttachmentRepository,modifier:Modifier=Modifier) {
    var movie by remember(row.id) {mutableStateOf<Movie?>(null)}
    var failed by remember(row.id) {mutableStateOf(false)}
    LaunchedEffect(row.id) {
        movie=withContext(Dispatchers.IO) {
            runCatching {
                val bytes=com.r0ybt.arachn0de.audio.originalAttachmentBytes(repository,row)
                try {com.r0ybt.arachn0de.media.ContentGif.inspect(bytes);requireNotNull(Movie.decodeByteArray(bytes,0,bytes.size))} finally {bytes.fill(0)}
            }.getOrNull()
        }
        failed=movie==null
    }
    val context=androidx.compose.ui.platform.LocalContext.current
    val view=remember(row.id,context) {ContentGifView(context)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view,lifecycle) {
        fun sync()=view.playing(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer=LifecycleEventObserver {_,_->sync()};lifecycle.addObserver(observer);sync()
        onDispose {lifecycle.removeObserver(observer);view.release()}
    }
    if(failed)androidx.compose.material3.Text("GIF no disponible")
    else AndroidView(factory={view},modifier=modifier,update={it.load(movie)})
}
