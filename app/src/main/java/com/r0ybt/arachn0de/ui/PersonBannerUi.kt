package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonBannerRepository
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.*
import java.util.UUID

@Composable internal fun rememberBannerBitmap(file:AttachmentFileEntity?):Bitmap? {
    val storage=privateStorageContext(LocalContext.current)
    val bitmap by produceState<Bitmap?>(null,storage,file?.id) {
        value=withContext(Dispatchers.IO) {try {file?.let {PrivateImageCache.read(AttachmentStore(storage).file(it.storageName),1024,oriented=true)}} catch(c:CancellationException) {throw c} catch(e:Exception) {android.util.Log.w("PersonBanner","Cannot decode banner",e);null}}
    }
    return bitmap
}
@Composable internal fun PersonBannerSurface(row:PersonBannerEntity?,files:List<AttachmentFileEntity>) {
    val bitmap=rememberBannerBitmap(files.firstOrNull {it.id==row?.attachmentId})
    val shape=RoundedCornerShape(topStart=12.dp,topEnd=12.dp)
    Box(Modifier.fillMaxWidth().testTag("person-banner-preview").aspectRatio(3f).clip(shape).background(Arachn0deColors.ControlSurface)) {
        if(bitmap!=null && row!=null) FramedAvatar(bitmap,AvatarFraming(row.zoom,row.x,row.y),Modifier.fillMaxSize(),shape=shape,filterQuality=androidx.compose.ui.graphics.FilterQuality.High)
    }
}
@Composable internal fun PersonBannerEditor(person:String,current:PersonBannerEntity?,files:List<AttachmentFileEntity>,galleryIds:List<String>,legacyFiles:List<String> = emptyList(),attachmentRepository:com.r0ybt.arachn0de.data.repository.AttachmentRepository?=null,onClose:()->Unit) {
    val app=LocalContext.current.applicationContext as Arachn0deApplication
    val attachments=attachmentRepository ?: app.attachmentRepository
    val repository=remember(app.database,attachments) {PersonBannerRepository(app.database,attachments)}
    val owner=rememberSaveable(person) {"banner-${UUID.randomUUID()}"}
    var chosen by rememberSaveable(person) {mutableStateOf(current?.attachmentId)}
    var framing by remember {mutableStateOf(current?.let {AvatarFraming(it.zoom,it.x,it.y)} ?: AvatarFraming())}
    var crop by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun run(action:suspend ()->Unit) {if(busy)return;busy=true;scope.launch {try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar el banner."} finally {busy=false}}}
    fun close()=run {attachments.discardDraft(owner);onClose()}
    androidx.activity.compose.BackHandler {if(!busy && !crop)close()}
    LaunchedEffect(owner) {
        busy=true
        try {current?.attachmentId?.let {attachments.retainGallery(owner,setOf(it))}}
        catch(c:CancellationException) {throw c}
        catch(_:Exception) {error="La imagen del banner ya no está disponible. Selecciona otra fotografía."}
        finally {busy=false}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->if(uri!=null)run {chosen=attachments.importGallery(uri,owner).id;framing=AvatarFraming()}}
    val all by remember(attachments) {attachments.observeFiles()}.collectAsState(emptyList())
    val available=(files+all).distinctBy {it.id}
    val bitmap=rememberBannerBitmap(available.firstOrNull {it.id==chosen})
    if(crop) BitmapFramingDialog(bitmap,framing,{framing=it;crop=false},{crop=false},"Encuadrar banner",shape=RoundedCornerShape(8.dp),enabled=!busy,aspect=3f,stateKey=chosen)
    else TaskBottomPanel(enabled=true,panelKey="banner:$person",title="Banner",editRequest=null,accessibilityLabel="Banner",ownsMainActions=true) { NavigationBarFrame("person-banner-panel") { ModularPanelDialog(inline=true,onDismissRequest={if(!busy)close()},title={Text("Banner")},compact=true,plainBody=true,text={
        PersonBannerSurface(chosen?.let {PersonBannerEntity(person,it,framing.zoom,framing.x,framing.y)},available)
        TextButton(enabled=!busy,onClick={try {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))} catch(_:Exception) {error="No se pudo abrir el selector de fotografías. El banner actual se conserva."}}) {Text("Elegir imagen")}
        TextButton(enabled=!busy && bitmap!=null,onClick={crop=true}) {Text("Encuadrar")}
        if(current!=null) TextButton(enabled=!busy,onClick={run {repository.remove(person);attachments.discardDraft(owner);onClose()}}) {Text("Quitar banner")}
        error?.let {Text(it,color=Arachn0deColors.Destructive)}
    },dismissButton={TextButton(enabled=!busy,onClick={close()}) {Text("Cancelar")}},confirmButton={TextButton(enabled=!busy && chosen!=null,onClick={run {
        repository.save(PersonBannerEntity(person,requireNotNull(chosen),framing.zoom,framing.x,framing.y),owner);onClose()
    }}) {Text("Guardar")}}) } }
}
