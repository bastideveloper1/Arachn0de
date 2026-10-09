package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Same geometry for the editor, list, selectors and responsible avatars. */
@Composable
internal fun FramedAvatar(bitmap: Bitmap, framing: AvatarFraming, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier.clip(shape)) {
        drawFramedAvatar(image, framing)
    }
}

internal fun DrawScope.drawFramedAvatar(image: ImageBitmap, framing: AvatarFraming) {
    val g = framing.geometry(image.width.toFloat(), image.height.toFloat(), size.width)
    clipRect { drawImage(image, dstOffset = IntOffset(g.left.roundToInt(), g.top.roundToInt()), dstSize = IntSize(g.width.roundToInt().coerceAtLeast(1), g.height.roundToInt().coerceAtLeast(1))) }
}

@Composable
internal fun AvatarEditor(file: String, initial: AvatarFraming, onConfirm: (AvatarFraming) -> Unit, onCancel: () -> Unit, directoryName: String = "avatars", title: String = "Encuadrar avatar",
    shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    val context = LocalContext.current
    val storageContext=privateStorageContext(context)
    val bitmap by produceState<Bitmap?>(null, file, directoryName) { value = withContext(Dispatchers.IO) { runCatching { AvatarStore(storageContext, directoryName).read(file) }.getOrNull() } }
    var zoom by rememberSaveable(file) { mutableFloatStateOf(initial.zoom) }
    var x by rememberSaveable(file) { mutableFloatStateOf(initial.x) }
    var y by rememberSaveable(file) { mutableFloatStateOf(initial.y) }
    fun set(value: AvatarFraming) { zoom = value.zoom; x = value.x; y = value.y }
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            bitmap?.let { image ->
                FramedAvatar(image, AvatarFraming(zoom, x, y), Modifier.size(240.dp).semantics { contentDescription = "Vista previa del avatar" }.pointerInput(file, image) {
                    detectTransformGestures { centroid, pan, factor, _ ->
                        set(AvatarFraming(zoom, x, y).transform(image.width.toFloat(), image.height.toFloat(), size.width.toFloat(), pan.x, pan.y, factor, centroid.x, centroid.y))
                    }
                }, shape = shape)
                Text("Arrastra la fotografía o pellizca para ajustar el zoom.")
                Row {
                    TextButton(onClick = { set(AvatarFraming(zoom, x, y).transform(image.width.toFloat(), image.height.toFloat(), 240f, 0f, 0f, 1 / 1.2f)) }, enabled = zoom > 1f) { Text("Alejar") }
                    TextButton(onClick = { set(AvatarFraming(zoom, x, y).transform(image.width.toFloat(), image.height.toFloat(), 240f, 0f, 0f, 1.2f)) }, enabled = zoom < 5f) { Text("Ampliar") }
                }
                Text("Zoom: ${"%.2f".format(zoom)}×")
                Row {
                    TextButton(onClick = { x = 0f; y = 0f }) { Text("Centrar") }
                    TextButton(onClick = { set(AvatarFraming()) }) { Text("Restablecer") }
                }
            } ?: Text("No se pudo cargar la fotografía.")
        }
    }, confirmButton = { TextButton(enabled = bitmap != null, onClick = { onConfirm(AvatarFraming(zoom, x, y)) }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } })
}
