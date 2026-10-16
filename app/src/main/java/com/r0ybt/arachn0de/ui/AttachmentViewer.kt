package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.security.SecureFiles
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

internal fun decodeAttachment(file: java.io.File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    SecureFiles.decoded(file, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val source = SecureFiles.decoded(file, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val orientation = runCatching { SecureFiles.orientation(file) }.getOrDefault(1)
    val matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f, 1f) }; 8 -> setRotate(-90f)
        }
    }
    if (matrix.isIdentity) return source
    return try { Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true) }
    finally { source.recycle() }
}

@Composable
internal fun AttachmentViewer(id: String, repository: AttachmentRepository, onEditName: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, onClose: () -> Unit) {
    var image by remember(id) { mutableStateOf<Bitmap?>(null) }
    val files by repository.observeFiles().collectAsState(emptyList())
    val row=files.firstOrNull {it.id==id}
    if(row?.mimeType=="audio/aac") {AudioViewer(id,repository,onRemove=onRemove,onClose=onClose);return}
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        image = withContext(Dispatchers.IO) {
            repository.availableFiles()[id]?.let { runCatching { decodeAttachment(repository.localFile(it)) }.getOrNull() }
        }
        loaded = true
    }
    // Leave bitmap lifetime to the renderer/GC: recycling while a frame still owns it is unsafe.
    BackHandler(onBack = onClose)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Arachn0deColors.Background).safeDrawingPadding().testTag("attachment-viewer")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                AttachmentExportActions(row,repository,compact=true)
                onEditName?.let { TextButton(onClick = it) { Text("Editar nombre") } }
                onRemove?.let { TextButton(onClick = it) { Text("Quitar") } }
                TextButton(onClick = onClose) { Text("Cerrar", color = Arachn0deColors.Accent) }
            }
            ZoomableImage(image, loaded, "Imagen adjunta", Modifier.fillMaxWidth().weight(1f))
        }
    }
}


/** Shared image stage: Fit at rest, bounded zoom/pan, no file writes. */
@Composable internal fun ZoomableImage(image: Bitmap?, loaded: Boolean, description: String, modifier: Modifier = Modifier, controls: Boolean = false, carousel:Boolean=false) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun transform(scale: Float, pan: Offset) {
        zoom = (zoom * scale).coerceIn(1f, 5f)
        val bitmap = image
        val fit = if (bitmap == null) 0f else min(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
        val boundX = ((bitmap?.width ?: 0) * fit * zoom - viewport.width).coerceAtLeast(0f) / 2
        val boundY = ((bitmap?.height ?: 0) * fit * zoom - viewport.height).coerceAtLeast(0f) / 2
        val moved = offset + pan
        offset = Offset(moved.x.coerceIn(-boundX, boundX), moved.y.coerceIn(-boundY, boundY))
    }
    val state=rememberTransformableState {scale,pan,_->transform(scale,pan)}
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged {viewport=it}.transformable(state,canPan={ !carousel || zoom>1f })
            .testTag("zoomable-image").semantics {stateDescription="Zoom ${(zoom*100).toInt()} %"},contentAlignment=Alignment.Center) {
            val bitmap=image
            if(bitmap!=null) Image(bitmap.asImageBitmap(),description,contentScale=ContentScale.Fit,
                modifier=Modifier.fillMaxSize().graphicsLayer {scaleX=zoom;scaleY=zoom;translationX=offset.x;translationY=offset.y})
            else Text(if(loaded) "Imagen no disponible" else "Cargando imagen…",color=Arachn0deColors.TextSecondary)
        }
        if(controls) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
            TextButton(enabled=image!=null && zoom<5f,onClick={transform(1.5f,Offset.Zero)}) {Text("Ampliar")}
            TextButton(enabled=image!=null && zoom>1f,onClick={transform(1/1.5f,Offset.Zero)}) {Text("Alejar")}
            TextButton(enabled=image!=null,onClick={zoom=1f;offset=Offset.Zero}) {Text("Restablecer")}
        }
    }

}
