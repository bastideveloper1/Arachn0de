package com.r0ybt.arachn0de.game

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val RACE_ASSET_FILES = Racer.entries.map { it.file } + RaceItem.entries.map { it.file } + listOf("sendero.png","sendero_con_caja.png")
/** Original PNGs remain untouched. At most twenty sampled images, no disk cache or private files. */
@Composable internal fun raceArt(): Map<String,ImageBitmap> {
    val assets=LocalContext.current.applicationContext.assets
    val art by produceState<Map<String,ImageBitmap>>(emptyMap(),assets) {
        value=withContext(Dispatchers.IO) { RACE_ASSET_FILES.associateWith { file ->
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            assets.open("game/$file").use { BitmapFactory.decodeStream(it,null,bounds) }
            require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096) { "Imagen inválida: $file" }
            var sample=1; while(maxOf(bounds.outWidth,bounds.outHeight)/sample>256) sample*=2
            val options=BitmapFactory.Options().apply { inSampleSize=sample }
            assets.open("game/$file").use { requireNotNull(BitmapFactory.decodeStream(it,null,options)).asImageBitmap() }
        } }
    }
    return art
}
@Composable internal fun RaceImage(art: Map<String,ImageBitmap>, file: String, description: String?, modifier: Modifier=Modifier) {
    art[file]?.let { Image(it,description,modifier,contentScale=ContentScale.Fit) }
}
