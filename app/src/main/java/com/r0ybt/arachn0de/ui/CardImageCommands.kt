package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.launch

internal data class CardImageCommands(val busy:Boolean,val share:()->Unit,val save:()->Unit,val message:String?=null)

/** The pre-existing PNG workflow, shared by the exterior and interior menus. */
@Composable internal fun rememberCardImageCommands(nodeId:String):CardImageCommands {
    val shareContext=LocalContext.current
    val shareStorage=privateStorageContext(shareContext)
    val shareApp=shareContext.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
    val shareColors=com.r0ybt.arachn0de.report.CardShareColors(Arachn0deColors.Background.toArgb(),Arachn0deColors.TextPrimary.toArgb(),Arachn0deColors.PathHighlight.toArgb())
    val shareScope=rememberCoroutineScope()
    var sharing by remember(nodeId) {mutableStateOf(false)}
    var shareFailure by remember(nodeId) {mutableStateOf<String?>(null)}
    fun shareCard() {
        if(sharing || shareApp==null) return
        sharing=true;shareFailure=null
        shareScope.launch {
            var generated:java.io.File?=null
            val files=com.r0ybt.arachn0de.report.CardShareFiles(shareStorage)
            try {
                generated=files.generate({com.r0ybt.arachn0de.report.CardShareSource.snapshot(shareApp,shareStorage,nodeId)},shareColors)
                shareContext.startActivity(android.content.Intent.createChooser(files.shareIntent(generated),"Compartir tarjeta como imagen"))
            } catch(cancelled:kotlinx.coroutines.CancellationException) {
                generated?.let {file->kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {runCatching {files.discard(file)}}}
                throw cancelled
            }
            catch(_:Exception) {
                generated?.let {file->kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {runCatching {files.discard(file)}}}
                shareFailure="No se pudo compartir la imagen. Revisa que las fotografías estén disponibles y que haya espacio para una sola tarjeta."
            } catch(_:OutOfMemoryError) {shareFailure="No hay memoria suficiente para generar esta imagen. Los originales se conservan."}
            finally {sharing=false}
        }
    }
    val saveImage=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("image/png")
    ) {destination->
        sharing=false
        if(destination!=null && shareApp!=null) {
            sharing=true
            shareScope.launch {
                val files=com.r0ybt.arachn0de.report.CardShareFiles(shareStorage)
                var generated:java.io.File?=null
                try {
                    generated=files.generate({com.r0ybt.arachn0de.report.CardShareSource.snapshot(shareApp,shareStorage,nodeId,onlyPending=true)},shareColors)
                    files.save(generated,destination)
                } catch(cancelled:kotlinx.coroutines.CancellationException) {
                    runCatching {android.provider.DocumentsContract.deleteDocument(shareContext.contentResolver,destination)}
                    throw cancelled
                } catch(_:Exception) {
                    runCatching {android.provider.DocumentsContract.deleteDocument(shareContext.contentResolver,destination)}
                    shareFailure="No se pudo guardar la imagen. Revisa las fotografías y el espacio disponible en el destino."
                } catch(_:OutOfMemoryError) {
                    runCatching {android.provider.DocumentsContract.deleteDocument(shareContext.contentResolver,destination)}
                    shareFailure="No hay memoria suficiente para generar esta imagen."
                } finally {
                    generated?.let {file->kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {runCatching {files.discard(file)}}}
                    sharing=false
                }
            }
        }
    }

    return CardImageCommands(sharing,::shareCard,{
        if(!sharing && shareApp!=null) {
            shareFailure=null
            try {sharing=true;saveImage.launch("arachn0de-card.png")}
            catch(_:Exception) {sharing=false;shareFailure="No se pudo abrir el selector para guardar la imagen."}
        }
    },shareFailure)
}
