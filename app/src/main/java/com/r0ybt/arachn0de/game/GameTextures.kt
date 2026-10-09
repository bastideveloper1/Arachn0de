package com.r0ybt.arachn0de.game

import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact filenames. Unicode araña.png belongs in assets/game; drawable names require ASCII. */
internal val GAME_TEXTURE_FILES = listOf("pantano.png", "pastoseco.png", "terrenocueva.png", "paredcueva.png", "araña.png")
@Composable
internal fun gameTextures(): Map<String, ImageBitmap> {
    val context = LocalContext.current.applicationContext
    val textures by produceState<Map<String, ImageBitmap>>(emptyMap(), context) {
        value = withContext(Dispatchers.IO) {
            GAME_TEXTURE_FILES.mapNotNull { filename -> runCatching {
                val resource = context.resources.getIdentifier(filename.removeSuffix(".png"), "drawable", context.packageName)
                fun stream() = if (resource != 0) context.resources.openRawResource(resource) else context.assets.open("game/$filename")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                stream().use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096)
                val options = BitmapFactory.Options().apply { inSampleSize = 1; while (bounds.outWidth / inSampleSize > 512 || bounds.outHeight / inSampleSize > 512) inSampleSize *= 2 }
                filename to requireNotNull(stream().use { BitmapFactory.decodeStream(it, null, options) }).asImageBitmap()
            }.getOrNull() }.toMap()
        }
    }
    return textures
}
