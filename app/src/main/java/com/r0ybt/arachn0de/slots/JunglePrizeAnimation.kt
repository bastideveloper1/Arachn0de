package com.r0ybt.arachn0de.slots

import android.graphics.Movie
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.r0ybt.arachn0de.ui.ContentGifView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val junglePrizeGifs=listOf("cereza","banana","guacamayo","plata","oro","rubi","esmeralda","premio_mayor")
internal fun junglePrizeGif(result:List<Int>,table:SavingsPayTable,units:Long):String? {
    val symbol=result.firstOrNull() ?: return null
    if(result.size!=3 || result.any {it!=symbol} || table.symbolCount!=8 || symbol !in 0..7 ||
        table.prizeUnits!=SavingsPayTable().prizeUnits || units!=table.prizeUnits[symbol]) return null
    return "premios/premios_${junglePrizeGifs[symbol]}.gif"
}

/** Bundled art only: reuse the existing software GIF view and release it on interruption. */
@Suppress("DEPRECATION")
@Composable internal fun JunglePrizeAnimation(path:String,modifier:Modifier,onReady:(Boolean)->Unit) {
    val context=LocalContext.current
    val view=remember(path,context) {ContentGifView(context)}
    val movie by produceState<Movie?>(null,path) {
        value=withContext(Dispatchers.IO) {context.assets.open("slots/jungle/$path").use {Movie.decodeStream(it)}}
    }
    val ready by rememberUpdatedState(onReady)
    DisposableEffect(view) {onDispose {view.release();ready(false)}}
    SideEffect {ready(movie!=null)}
    AndroidView(factory={view},modifier=modifier,update={it.load(movie);it.playing(movie!=null)})
}
