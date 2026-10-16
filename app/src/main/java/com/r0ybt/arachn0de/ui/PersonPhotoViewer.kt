package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.data.local.PersonPhotoEntity
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.*
import java.util.UUID

private data class ViewedPersonPhoto(val id:String,val file:String,val reference:PersonPhotoEntity?)

@Composable internal fun PersonPhotoViewer(person: Person, photos: List<PersonPhotoEntity>, selected: String,
    busy: Boolean, onSelect: (String) -> Unit, onAvatar: (PersonPhotoEntity) -> Unit, onClose: () -> Unit,
    repository:PersonRepository) {
    val images=(person.avatarFile?.let {listOf(ViewedPersonPhoto("primary:${person.id}",it,null))}.orEmpty()+
        photos.filter {it.file!=person.avatarFile}.map {ViewedPersonPhoto(it.id,it.file,it)}).distinctBy {it.file}
    val selectedFile=photos.firstOrNull {it.id==selected}?.file
    val initial=images.indexOfFirst {it.id==selected || it.file==selectedFile}.coerceAtLeast(0)
    val pager=rememberPagerState(initialPage=initial,pageCount={images.size})
    val photo=images.getOrNull(pager.currentPage)
    val storage=privateStorageContext(LocalContext.current)
    val scope=rememberCoroutineScope()
    val owner=rememberSaveable(person.id) {UUID.randomUUID().toString()}
    var exportFile by rememberSaveable(person.id) {mutableStateOf<String?>(null)}
    var exporting by rememberSaveable(person.id) {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val blocked=busy || exporting
    val download=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {uri->
        val file=exportFile
        scope.launch {
            try {if(uri!=null && file!=null) repository.exportAvatar(file,uri)}
            catch(c:CancellationException) {throw c}
            catch(e:Exception) {error="No se pudo guardar la copia de la fotografía."}
            finally {withContext(NonCancellable) {repository.discardAvatar(file,owner)};exportFile=null;exporting=false}
        }
    }
    LaunchedEffect(pager.currentPage,photo?.id) {photo?.let {if(it.id!=selected) onSelect(it.id)}}
    BackHandler {if(!blocked) onClose()}
    Dialog(onDismissRequest={if(!blocked) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(Arachn0deColors.Background).safeDrawingPadding().testTag("person-photo-viewer")) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("${person.name} · ${if(photo==null) 0 else pager.currentPage+1}/${images.size}",Modifier.weight(1f).padding(12.dp))
                TextButton(enabled=!blocked,onClick=onClose) {Text("Cerrar")}
            }
            VerticalPager(pager,Modifier.fillMaxWidth().weight(1f).testTag("person-photo-pager"),userScrollEnabled=!blocked,key={images[it].id}) {page->
                val file=images[page].file
                val loaded by produceState<Pair<android.graphics.Bitmap?,Boolean>>(null to false,storage,file) {
                    value=withContext(Dispatchers.IO) {runCatching {AvatarStore(storage).read(file)}.getOrNull() to true}
                }
                ZoomableImage(loaded.first,loaded.second,"Fotografía completa de ${person.name}",Modifier.fillMaxSize().padding(horizontal=12.dp).testTag("person-viewer-image"),controls=true,carousel=true)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(enabled=!blocked && pager.currentPage>0,onClick={scope.launch {pager.animateScrollToPage(pager.currentPage-1)}}) {Text("Anterior")}
                TextButton(enabled=!blocked && photo!=null,onClick={
                    val file=photo?.file ?: return@TextButton
                    exporting=true
                    scope.launch {
                        try {repository.retainAvatar(file,owner);exportFile=file;download.launch("fotografia.png")}
                        catch(c:CancellationException) {withContext(NonCancellable) {repository.discardAvatar(file,owner)};throw c}
                        catch(e:Exception) {withContext(NonCancellable) {repository.discardAvatar(file,owner)};exportFile=null;exporting=false;error="No se pudo abrir Guardar fotografía."}
                    }
                },modifier=Modifier.testTag("person-photo-save")) {Text("Guardar copia")}
                TextButton(enabled=!blocked && pager.currentPage<images.lastIndex,onClick={scope.launch {pager.animateScrollToPage(pager.currentPage+1)}}) {Text("Siguiente")}
            }
            TextButton(enabled=!blocked && photo?.reference!=null && person.avatarFile!=photo.file,onClick={photo?.reference?.let(onAvatar)},modifier=Modifier.fillMaxWidth()) {
                Text(if(busy) "Guardando…" else "Establecer como avatar principal")
            }
        }
    }
    error?.let {AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}
