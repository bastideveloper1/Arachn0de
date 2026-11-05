package com.r0ybt.arachn0de.guest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import com.r0ybt.arachn0de.ui.BitmapFramingDialog
import kotlinx.coroutines.*

/** Non-exported, opaque, secure window. This Activity never obtains a vault/database handle. */
internal class GuestCaptureActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val id=intent.getStringExtra("capture") ?: return finish()
        setContent {MaterialTheme(colorScheme=darkColorScheme()) {Surface(Modifier.fillMaxSize(),color=androidx.compose.ui.graphics.Color(0xff202224)) {
            GuestCamera(id) {finish()}
        }}}
    }
    override fun onResume() {
        super.onResume()
        if((application as Arachn0deApplication).security.current.value!=null) finish()
    }
    @Composable private fun GuestCamera(id:String,close:()->Unit) {
        val queue=remember {GuestCaptureQueue(applicationContext)}
        val scope=rememberCoroutineScope()
        var ready by remember {mutableStateOf(false)}
        var accepted by remember {mutableStateOf(false)}
        var bytes by remember {mutableStateOf<ByteArray?>(null)}
        var bitmap by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
        var framing by remember {mutableStateOf(AvatarFraming())}
        var crop by remember {mutableStateOf(false)}
        var busy by remember {mutableStateOf(false)}
        var error by remember {mutableStateOf<String?>(null)}
        var permission by remember {mutableStateOf(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
        val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {permission=it}
        LaunchedEffect(id) {
            try {val pending=withContext(Dispatchers.IO) {queue.read(id)};accepted=pending.accepted;pending.bytes.fill(0);ready=true}
            catch(_:Exception) {error="No se pudo recuperar el destino cifrado. Vuelve al desbloqueo."}
        }
        BackHandler {if(!busy)close()}
        DisposableEffect(Unit) {onDispose {bytes?.fill(0)}}
        if(crop) BitmapFramingDialog(bitmap,framing,{framing=it;crop=false},{crop=false},"Encuadrar",enabled=!busy,stateKey=id)
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(accepted) {
                Spacer(Modifier.weight(1f))
                Text("✓ ¡Captura recibida!",style=MaterialTheme.typography.headlineSmall)
                Text("La fotografía está cifrada y pendiente de incorporación al perfil tras desbloquear.")
                Spacer(Modifier.weight(1f))
                TextButton(onClick=close,modifier=Modifier.heightIn(min=48.dp)) {Text("Desbloquear Arachn0de")}
            } else if(ready) {
                if(bytes==null) {
                    Text("Captura de invitado",style=MaterialTheme.typography.titleLarge)
                    if(!permission) Button(onClick={request.launch(Manifest.permission.CAMERA)}) {Text("Permitir cámara")}
                    else InMemoryCamera(Modifier.weight(1f).fillMaxWidth(),onImage={photo->
                        bytes=photo
                        val options=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                        android.graphics.BitmapFactory.decodeByteArray(photo,0,photo.size,options)
                        var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>2048) sample*=2
                        val decoded=android.graphics.BitmapFactory.decodeByteArray(photo,0,photo.size,android.graphics.BitmapFactory.Options().apply {inSampleSize=sample})
                        val exif=runCatching {android.media.ExifInterface(photo.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
                        val matrix=android.graphics.Matrix().apply {when(exif) {2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)}}
                        bitmap=decoded?.let {if(matrix.isIdentity) it else android.graphics.Bitmap.createBitmap(it,0,0,it.width,it.height,matrix,true)}
                        framing=AvatarFraming()
                    },onError={error=it})
                } else {
                    bitmap?.let {Image(it.asImageBitmap(),"Fotografía capturada",Modifier.weight(1f).fillMaxWidth(),contentScale=androidx.compose.ui.layout.ContentScale.Fit)}
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(enabled=!busy,onClick={bytes?.fill(0);bytes=null;bitmap=null}) {Text("Repetir")}
                        Button(enabled=!busy,onClick={crop=true}) {Text("Encuadrar")}
                        Button(enabled=!busy && bitmap!=null,onClick={busy=true;scope.launch {
                            try {withContext(Dispatchers.IO) {queue.accept(id,requireNotNull(bytes),framing)};accepted=true;bytes?.fill(0);bytes=null;bitmap=null}
                            catch(c:CancellationException) {throw c} catch(_:Exception) {error="No se pudo confirmar la captura. Reintenta; todavía no se ha confirmado el guardado."}
                            finally {busy=false}
                        }}) {Text(if(busy) "Guardando…" else "Aceptar")}
                    }
                }
            }
            error?.let {Text(it);TextButton(enabled=!busy,onClick=close) {Text("Volver al desbloqueo")}}
        }
    }
}

@Composable private fun InMemoryCamera(modifier:Modifier,onImage:(ByteArray)->Unit,onError:(String)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val deliver by rememberUpdatedState(onImage)
    val report by rememberUpdatedState(onError)
    val main=remember {Handler(Looper.getMainLooper())}
    var device by remember {mutableStateOf<CameraDevice?>(null)}
    var session by remember {mutableStateOf<CameraCaptureSession?>(null)}
    var reader by remember {mutableStateOf<ImageReader?>(null)}
    var texture by remember {mutableStateOf<TextureView?>(null)}
    var orientation by remember {mutableIntStateOf(0)}
    var active by remember {mutableStateOf(true)}
    var capturing by remember {mutableStateOf(false)}
    fun close() {session?.close();session=null;device?.close();device=null;reader?.close();reader=null;capturing=false}
    fun open(view:TextureView) {
        if(!active || device!=null || !view.isAvailable)return
        try {
            val manager=context.getSystemService(CameraManager::class.java)
            val id=manager.cameraIdList.firstOrNull {manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK} ?: manager.cameraIdList.first()
            val info=manager.getCameraCharacteristics(id)
            val sizes=requireNotNull(info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)).getOutputSizes(android.graphics.ImageFormat.JPEG)
            val size=sizes.filter {it.width.toLong()*it.height<=20_000_000}.maxByOrNull {it.width.toLong()*it.height} ?: sizes.minBy {it.width.toLong()*it.height}
            orientation=info.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val output=ImageReader.newInstance(size.width,size.height,android.graphics.ImageFormat.JPEG,2)
            output.setOnImageAvailableListener({r->r.acquireLatestImage()?.use {image->
                val buffer=image.planes[0].buffer
                if(buffer.remaining()>GuestCaptureQueue.MAX_BYTES) {capturing=false;report("La captura supera 20 MiB.")}
                else {val bytes=ByteArray(buffer.remaining());buffer.get(bytes);deliver(bytes)}
            }},main)
            reader=output
            manager.openCamera(id,object:CameraDevice.StateCallback() {
                override fun onOpened(camera:CameraDevice) {
                    if(!active || !view.isAvailable) {camera.close();return}
                    device=camera
                    val surface=Surface(view.surfaceTexture)
                    camera.createCaptureSession(listOf(surface,output.surface),object:CameraCaptureSession.StateCallback() {
                        override fun onConfigured(value:CameraCaptureSession) {
                            if(!active || device!==camera) {value.close();surface.release();return}
                            session=value
                            try {value.setRepeatingRequest(camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {addTarget(surface)}.build(),null,main)} catch(_:Exception) {report("No se pudo iniciar la cámara.")}
                        }
                        override fun onConfigureFailed(value:CameraCaptureSession) {surface.release();report("No se pudo configurar la cámara.")}
                        override fun onClosed(value:CameraCaptureSession) {surface.release()}
                    },main)
                }
                override fun onDisconnected(camera:CameraDevice) {camera.close();device=null;report("Cámara desconectada.")}
                override fun onError(camera:CameraDevice,error:Int) {camera.close();device=null;report("No se pudo abrir la cámara.")}
            },main)
        } catch(_:Exception) {close();report("La cámara no está disponible.")}
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_RESUME) {active=true;texture?.let {open(it)}}
            if(event==Lifecycle.Event.ON_PAUSE) {active=false;close()}
        }
        lifecycle.addObserver(observer)
        onDispose {active=false;lifecycle.removeObserver(observer);close()}
    }
    Column(modifier) {
        AndroidView(factory={TextureView(it).also {view->texture=view;view.surfaceTextureListener=object:TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface:SurfaceTexture,w:Int,h:Int) {open(view)}
            override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture,w:Int,h:Int) {}
            override fun onSurfaceTextureDestroyed(surface:SurfaceTexture):Boolean {close();return true}
            override fun onSurfaceTextureUpdated(surface:SurfaceTexture) {}
        }}},modifier=Modifier.weight(1f).fillMaxWidth())
        Button(enabled=session!=null && !capturing,onClick={try {
            val camera=requireNotNull(device);val target=requireNotNull(reader)
            val rotation=(context as? ComponentActivity)?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            val degrees=when(rotation) {Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
            val request=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {addTarget(target.surface);set(CaptureRequest.JPEG_ORIENTATION,(orientation-degrees+360)%360)}.build()
            capturing=true;session?.capture(request,object:CameraCaptureSession.CaptureCallback() {override fun onCaptureFailed(s:CameraCaptureSession,r:CaptureRequest,f:CaptureFailure) {capturing=false;report("No se pudo tomar la fotografía.")}},main)
        } catch(_:Exception) {capturing=false;report("No se pudo tomar la fotografía.")}},modifier=Modifier.heightIn(min=48.dp)) {Text("Tomar fotografía")}
    }
}
