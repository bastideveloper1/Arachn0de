package com.r0ybt.arachn0de.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.photos.*
import kotlinx.coroutines.*

internal class PrivatePhotoCaptureContract:ActivityResultContracts.TakePicture() {
    override fun createIntent(context:android.content.Context,input:android.net.Uri):android.content.Intent =
        super.createIntent(context,input).apply {
            clipData=android.content.ClipData.newRawUri("Captura fotográfica",input)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}

/** Shared external-camera capture and confirmation, without exposing any vault URI. */
@Composable internal fun rememberPrivatePhotoCamera(enabled:Boolean,onError:(String)->Unit,captureFiles:CameraCaptureFiles?=null,onPhoto:(CameraPhoto,(Boolean)->Unit)->Unit):()->Unit {
    val context=LocalContext.current
    val files=captureFiles ?: remember(context) {CameraCaptureFiles(context)}
    val session=(context.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value
    val importer=remember(session) {session?.let {CameraPhotoImport(files,it.attachments,it.access.id)}}
    val scope=rememberCoroutineScope()
    var photo by remember {mutableStateOf<CameraPhoto?>(null)}
    var id by rememberSaveable {mutableStateOf<String?>(null)}
    var confirming by rememberSaveable {mutableStateOf(false)}
    var processing by remember {mutableStateOf(false)}
    var preview by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    val latestPhoto by rememberUpdatedState(onPhoto)
    val latestError by rememberUpdatedState(onError)
    fun finish() {
        val old=id
        confirming=false;id=null;preview=null;photo=null
        old?.let {scope.launch(Dispatchers.IO+NonCancellable) {runCatching {if(importer!=null)importer.discard(it) else files.finish(it)}.onFailure {withContext(Dispatchers.Main) {latestError("La limpieza de la captura sigue pendiente; se reintentará al reiniciar.")}}}}
    }
    suspend fun preparePhoto(capture:String) {
            processing=true
                try {
                    photo=requireNotNull(importer).import(capture)
                    preview=withContext(Dispatchers.IO) {
                        val bytes=com.r0ybt.arachn0de.security.SecureFiles.input(requireNotNull(photo).file).use(PrivatePhoto::read)
                        try {
                            val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                            var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1024)sample*=2
                            val bitmap=requireNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply {inSampleSize=sample}))
                            PrivatePhoto.orient(bitmap,PrivatePhoto.sourceOrientation(bytes)).also {if(it!==bitmap)bitmap.recycle()}
                        } finally {bytes.fill(0)}
                    }
                    confirming=true
                } catch(c:CancellationException) {throw c}
                catch(_:Exception) {id=null;photo=null;preview=null;confirming=false;latestError("No se pudo preparar la fotografía privada. Usa JPEG o PNG de hasta 20 MiB; las conversiones tienen un límite de memoria adicional.")}
                finally {processing=false}
    }
    val camera=rememberLauncherForActivityResult(PrivatePhotoCaptureContract()) {success->
        val capture=id
        if(!success || capture==null) finish() else scope.launch {preparePhoto(capture)}
    }
    LaunchedEffect(importer) {
        val capture=id
        if(capture!=null && session!=null && withContext(Dispatchers.IO) {files.pending(session.access.id).any {it.first==capture && it.second.state in setOf("RETURNED","IMPORTED")}})preparePhoto(capture)
    }
    // Disposing/restarting a screen is not cancellation of an external camera request.
    // Durable reservations remain until an explicit cancel, successful handoff or recovery.
    if(confirming) ModularPanelDialog(onDismissRequest={if(!processing)finish()},compact=true,plainBody=true,
        title={Text("Confirmar fotografía")},text={preview?.let {Image(it.asImageBitmap(),"Fotografía capturada",Modifier.fillMaxWidth().heightIn(max=300.dp))}},
        dismissButton={TextButton(enabled=!processing,onClick=::finish) {Text("Cancelar")}},
        confirmButton={TextButton(enabled=enabled && !processing && photo!=null && preview!=null,onClick={
            if(id==null)return@TextButton
            processing=true
            try {latestPhoto(requireNotNull(photo)) {success->processing=false;if(success)finish()}}
            catch(_:Exception) {processing=false;latestError("No se pudo importar la captura. Se conserva para reintentar.")}
        }) {Text("Usar fotografía")}})
    fun launchCapture() {if(enabled && !processing && id==null) {
        processing=true
        scope.launch {
            var opening:String?=null
            try {
                val owner=requireNotNull(session).access.id
                val pending=withContext(Dispatchers.IO) {files.pending(owner).firstOrNull {it.second.state in setOf("RETURNED","IMPORTED")}}
                if(pending!=null) {id=pending.first;preparePhoto(pending.first)}
                else {
                    val capture=withContext(Dispatchers.IO) {files.prepare(owner)}
                    opening=capture;id=capture
                    camera.launch(files.uri(capture));opening=null
                }
            } catch(c:CancellationException) {throw c}
            catch(failure:Exception) {
                val failed=opening
                if(failed!=null) {
                    id=null
                    withContext(Dispatchers.IO+NonCancellable) {runCatching {
                        // launch threw: remove only this empty, never-dispatched output.
                        if(files.file(failed).length()==0L && (failure is android.content.ActivityNotFoundException || failure is SecurityException || failure is IllegalArgumentException)) files.finish(failed)
                        else if(files.file(failed).length()>0L) session?.access?.id?.let {files.returned(failed,it)}
                    }}
                }
                android.util.Log.w("CameraCapture","External camera launch failed",failure)
                latestError(when(failure) {
                    is android.content.ActivityNotFoundException->"No hay una aplicación de cámara disponible para tomar fotografías. Puedes usar Galería."
                    is SecurityException->"Android no permitió abrir la cámara. Revisa su permiso en Ajustes; las capturas pendientes se conservan."
                    else->"No se pudo preparar o abrir la cámara. Las capturas pendientes se conservan; puedes reintentar."
                })
            }
            finally {processing=false}
        }
    }}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(granted) launchCapture() else latestError("No se autorizó el uso de la cámara. Puedes seleccionar una fotografía desde Galería.")
    }
    return {if(enabled && !processing && id==null) {
        if(androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED) launchCapture()
        else permission.launch(android.Manifest.permission.CAMERA)
    }}
}
