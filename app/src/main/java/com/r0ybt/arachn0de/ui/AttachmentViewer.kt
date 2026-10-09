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
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        image = withContext(Dispatchers.IO) {
            repository.availableFiles()[id]?.let { runCatching { decodeAttachment(repository.localFile(it)) }.getOrNull() }
        }
        loaded = true
    }
    // Leave bitmap lifetime to the renderer/GC: recycling while a frame still owns it is unsafe.
    var zoom by remember(id) { mutableFloatStateOf(1f) }
    var offset by remember(id) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val state = rememberTransformableState { scale, pan, _ ->
        zoom = (zoom * scale).coerceIn(1f, 5f)
        val bitmap = image
        val fit = if (bitmap == null) 0f else min(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
        val boundX = ((bitmap?.width ?: 0) * fit * zoom - viewport.width).coerceAtLeast(0f) / 2
        val boundY = ((bitmap?.height ?: 0) * fit * zoom - viewport.height).coerceAtLeast(0f) / 2
        val moved = offset + pan
        offset = Offset(moved.x.coerceIn(-boundX, boundX), moved.y.coerceIn(-boundY, boundY))
    }
    BackHandler(onBack = onClose)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Arachn0deColors.Background).safeDrawingPadding().testTag("attachment-viewer")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                onEditName?.let { TextButton(onClick = it) { Text("Editar nombre") } }
                onRemove?.let { TextButton(onClick = it) { Text("Quitar") } }
                TextButton(onClick = onClose) { Text("Cerrar", color = Arachn0deColors.Accent) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged { viewport = it }.transformable(state), contentAlignment = Alignment.Center) {
                val bitmap = image
                if (bitmap != null) Image(bitmap.asImageBitmap(), "Imagen adjunta", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y })
                else Text(if (loaded) "Imagen no disponible" else "Cargando imagen…", color = Arachn0deColors.TextSecondary)
            }
        }
    }
}
