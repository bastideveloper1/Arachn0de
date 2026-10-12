package com.r0ybt.arachn0de.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PersonPhotoHeader(person:Person,profile:PersonProfileEntity,repository:PersonRepository,onEdit:()->Unit) {
    val all by remember(repository) {repository.observePhotos()}.collectAsState(emptyList())
    val photos=all.filter {it.personId==person.id}.sortedBy {it.position}
    var expandedDescription by rememberSaveable(person.id) {mutableStateOf(false)}
    var selected by rememberSaveable(person.id) {mutableStateOf<String?>(null)}
    var managing by rememberSaveable(person.id) {mutableStateOf(false)}
    var target by rememberSaveable(person.id) {mutableStateOf<String?>(null)}
    var pending by rememberSaveable(person.id) {mutableStateOf<String?>(null)}
    val draft=rememberSaveable(person.id) {UUID.randomUUID().toString()}
    LaunchedEffect(pending,draft) {pending?.let {repository.retainAvatar(it,draft)}}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;scope.launch {try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar la foto."} finally {busy=false}}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->if(uri!=null) perform {val imported=repository.importAvatar(uri);try {repository.retainAvatar(imported,draft);pending=imported} catch(e:Exception) {repository.discardAvatar(imported,draft);throw e}}}
    Card(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.width(112.dp)) {PersonPhoto(person,Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable(enabled = person.avatarFile != null) { selected="primary:${person.id}" });Text(person.name,modifier=Modifier.testTag("person-name:${person.id}"),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge)}
                Column(Modifier.weight(1f).padding(start=12.dp)) {if(profile.alias.isNotBlank()) Text(profile.alias,style=MaterialTheme.typography.titleMedium);TextButton(onClick=onEdit) {Text("Editar nombre y avatar")}}
            }
            if(profile.description.isNotBlank()) {
                Text(profile.description,maxLines=if(expandedDescription) Int.MAX_VALUE else 3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if(profile.description.length>160 || profile.description.count {it=='\n'}>2) TextButton(onClick={expandedDescription=!expandedDescription}) {Text(if(expandedDescription) "Resumir descripción" else "Leer descripción completa")}
            }
            if(photos.isNotEmpty()) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.testTag("person-photo-main").semantics {this.selected=true;contentDescription="Fotografía principal"}.border(2.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.large).clickable {selected="primary:${person.id}"}.padding(2.dp)) {PersonPhoto(person,Modifier.width(48.dp))}
                photos.forEach {photo->Box(Modifier.testTag("person-photo-${photo.id}").semantics {this.selected=selected==photo.id;contentDescription="Foto de referencia ${photo.position+1}"}.border(if(selected==photo.id) 2.dp else 0.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.large).clickable {selected=photo.id}.padding(2.dp)) {PersonPhoto(person.copy(avatarFile=photo.file),Modifier.width(48.dp))}}
            }
            TextButton(onClick={managing=true}) {Text("Fotos de referencia (${photos.size}/4)")}
        }
    }
    if(managing) AlertDialog(onDismissRequest={if(!busy && pending==null) managing=false},title={Text("Fotos de referencia")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
        Text("La foto principal conserva su avatar y encuadre. Selecciona una miniatura para consultar otra foto.")
        photos.forEachIndexed {index,photo->
            var menu by remember {mutableStateOf(false)}
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                PersonPhoto(person.copy(avatarFile=photo.file),Modifier.width(48.dp).clickable {selected=photo.id})
                Text("Foto ${index+1}",Modifier.weight(1f))
                Box {TextButton(enabled=!busy,onClick={menu=true}) {Text("⋮")};DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text("Reemplazar")},onClick={menu=false;target=photo.id;picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))})
                    DropdownMenuItem(text={Text("Ajustar encuadre")},onClick={menu=false;perform {repository.retainAvatar(photo.file,draft);target=photo.id;pending=photo.file}})
                    DropdownMenuItem(text={Text("Mover antes")},enabled=index>0,onClick={menu=false;perform {val ids=photos.map {it.id}.toMutableList();java.util.Collections.swap(ids,index,index-1);repository.reorderPhotos(person.id,ids)}})
                    DropdownMenuItem(text={Text("Mover después")},enabled=index<photos.lastIndex,onClick={menu=false;perform {val ids=photos.map {it.id}.toMutableList();java.util.Collections.swap(ids,index,index+1);repository.reorderPhotos(person.id,ids)}})
                    DropdownMenuItem(text={Text("Eliminar foto")},onClick={menu=false;perform {repository.removePhoto(photo.id);if(selected==photo.id) selected=null}})
                }}
            }
        }
        if(photos.size<4) TextButton(enabled=!busy,onClick={target=null;picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Añadir foto")}
    }},confirmButton={TextButton(enabled=!busy,onClick={managing=false}) {Text("Cerrar")}})
    pending?.let {file->val original=photos.firstOrNull {it.id==target && it.file==file};AvatarEditor(file,original?.let {AvatarFraming(it.zoom,it.x,it.y)} ?: AvatarFraming(),onConfirm={frame->perform {
        val old=photos.firstOrNull {it.id==target}
        val position=old?.position ?: (0..3).first {p->photos.none {it.position==p}}
        repository.savePhoto(PersonPhotoEntity(old?.id ?: UUID.randomUUID().toString(),person.id,file,position,frame.zoom,frame.x,frame.y),draft)
        pending=null
    }},onCancel={if(!busy) {pending=null;scope.launch(start=CoroutineStart.UNDISPATCHED) {withContext(NonCancellable) {repository.discardAvatar(file,draft)}}}},title="Encuadrar foto de referencia",shape=MaterialTheme.shapes.large)}
    selected?.let {id->PersonPhotoViewer(person,photos,id,busy,onSelect={selected=it},onAvatar={photo->perform {
        check(repository.setPhotoAsAvatar(person.id,photo.id)) {"La Persona ya no existe."};selected=null
    }},onClose={if(!busy) selected=null})}
    error?.let {AlertDialog(onDismissRequest={error=null},title={Text("No se pudo guardar")},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}
