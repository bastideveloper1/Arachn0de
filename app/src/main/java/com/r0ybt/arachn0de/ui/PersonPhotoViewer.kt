package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.data.local.PersonPhotoEntity
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ViewedPersonPhoto(val id:String,val file:String,val reference:PersonPhotoEntity?)

@Composable internal fun PersonPhotoViewer(person: Person, photos: List<PersonPhotoEntity>, selected: String,
    busy: Boolean, onSelect: (String) -> Unit, onAvatar: (PersonPhotoEntity) -> Unit, onClose: () -> Unit) {
    val images=(person.avatarFile?.let {listOf(ViewedPersonPhoto("primary:${person.id}",it,null))}.orEmpty()+
        photos.filter {it.file!=person.avatarFile}.map {ViewedPersonPhoto(it.id,it.file,it)}).distinctBy {it.file}
    val selectedFile=photos.firstOrNull {it.id==selected}?.file
    val photo=images.firstOrNull {it.id==selected} ?: images.firstOrNull {it.file==selectedFile}
    val storage=privateStorageContext(LocalContext.current)
    val loaded by produceState<Pair<android.graphics.Bitmap?,Boolean>>(null to false,storage,photo?.file) {
        value=null to false
        value=withContext(Dispatchers.IO) {photo?.let {runCatching {AvatarStore(storage).read(it.file)}.getOrNull()} to true}
    }
    val index=images.indexOf(photo)
    BackHandler(enabled=!busy,onBack=onClose)
    Dialog(onDismissRequest={if(!busy) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(Arachn0deColors.Background).safeDrawingPadding().testTag("person-photo-viewer")) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("${person.name} · ${if(index<0) 0 else index+1}/${images.size}",Modifier.weight(1f).padding(12.dp))
                TextButton(enabled=!busy,onClick=onClose) {Text("Cerrar")}
            }
            PersonPhotoComparison(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), profile = photo?.reference == null) { imageModifier ->
                key(photo?.file) {ZoomableImage(loaded.first,loaded.second,"Fotografía completa de ${person.name}",imageModifier.aspectRatio(1f).testTag("person-viewer-image"),controls=true)}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(enabled=!busy && index>0,onClick={onSelect(images[index-1].id)}) {Text("Anterior")}
                TextButton(enabled=!busy && index>=0 && index<images.lastIndex,onClick={onSelect(images[index+1].id)}) {Text("Siguiente")}
            }
            TextButton(enabled=!busy && loaded.first!=null && photo?.reference!=null && person.avatarFile!=photo.file,onClick={photo?.reference?.let(onAvatar)},modifier=Modifier.fillMaxWidth()) {
                Text(if(busy) "Guardando…" else "Establecer como avatar principal")
            }
        }
    }
}
