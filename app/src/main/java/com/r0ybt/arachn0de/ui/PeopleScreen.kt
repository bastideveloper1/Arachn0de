package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.filled.MoreVert

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.r0ybt.arachn0de.domain.model.PersonCategory
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.state.LoadState
import com.r0ybt.arachn0de.ui.state.PersonActions
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeopleScreen(repository: PersonRepository, initialPersonId:String?=null, initialSection:String="General", onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val actions = remember(repository, scope) { PersonActions(repository, scope) }
    val load = remember(repository) { LoadState() }
    var people by remember { mutableStateOf(emptyList<Person>()) }
    LaunchedEffect(repository, load.attempt) { load.collect(repository.observePeople()) { people = it } }
    var detailId by rememberSaveable { mutableStateOf(initialPersonId) }
    val profiles by remember(repository) {repository.observeProfiles()}.collectAsState(emptyList())
    val relations by remember(repository) {repository.observeRelations()}.collectAsState(emptyList())
    var category by rememberSaveable {mutableStateOf("")}
    var search by rememberSaveable {mutableStateOf("")}
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    val birthdays by remember(repository) {repository.observeBirthdays()}.collectAsState(initial=null as List<com.r0ybt.arachn0de.data.local.PersonBirthdayEntity>?)
    var birthday by rememberSaveable {mutableStateOf<String?>(null)}
    var isNew by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf<String?>(null) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var frameX by rememberSaveable { mutableFloatStateOf(0f) }
    var frameY by rememberSaveable { mutableFloatStateOf(0f) }
    var cropOpen by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    fun framing() = AvatarFraming(zoom, frameX, frameY)
    LaunchedEffect(editorId, pendingPhoto) { editorId?.let { repository.retainAvatar(pendingPhoto, it) } }
    LaunchedEffect(editorId, avatar) { editorId?.let { repository.retainAvatar(avatar, it) } }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val busy = actions.operation.busy
    fun discardDraft() {
        val file = avatar
        val owner = editorId
        editorId = null; cropOpen = false
        val pending = pendingPhoto; pendingPhoto = null
        scope.launch { repository.discardAvatar(file, owner); repository.discardAvatar(pending, owner) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && editorId != null) actions.import(uri) { imported ->
            pendingPhoto = imported; cropOpen = true
        }
    }
    BackHandler { if (!busy) { if (editorId != null) discardDraft() else if (deletingId != null) deletingId = null else onBack() } }
    fun edit(person:Person) {editorId=person.id;birthday=null;isNew=false;name=person.name;avatar=person.avatarFile;zoom=person.avatarZoom;frameX=person.avatarX;frameY=person.avatarY}
    LaunchedEffect(editorId,birthdays) {if(editorId!=null && !isNew && birthday==null && birthdays!=null) birthday=birthdays!!.firstOrNull {it.personId==editorId}?.date.orEmpty()}
    if(detailId!=null) Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {
        PersonDetailView(detailId!!,people,repository,::edit,initialSection) {if(initialPersonId!=null) onBack() else detailId=null}
    } else Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Personas",modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !busy, onClick = onBack) { Text("Volver") }
                GlobalChromeActions(enabled=!busy)
            }
            Button(enabled = !busy, onClick = { editorId = UUID.randomUUID().toString(); isNew = true; birthday=""; name = ""; avatar = null; zoom = 1f; frameX = 0f; frameY = 0f }) { Text("Nueva Persona") }
            OutlinedTextField(search,{search=it},label={Text("Buscar Personas")},modifier=Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(category.isEmpty(),{category=""},label={Text("Todas")})
                PersonCategory.entries.filter {c->relations.any {it.category==c.name}}.forEach {c->FilterChip(category==c.name,{category=c.name},label={Text(c.label)})}
            }
            val filtered=people.filter {p->(p.name.contains(search.trim(),true) || profiles.firstOrNull {it.personId==p.id}?.alias?.contains(search.trim(),true)==true) && (category.isEmpty() || relations.any {it.category==category && (it.sourceId==p.id || it.targetId==p.id)})}
            if(filtered.isEmpty()) Text(if(people.isEmpty()) "Aún no hay Personas. Crea la primera con Nueva Persona." else "No hay coincidencias.")
            BoxWithConstraints(Modifier.weight(1f)) {
                val columns=minOf(3,maxOf(1,(maxWidth.value/104f).toInt()))
                LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().testTag("people-gallery"),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(filtered,key={it.id}) {person->
                        var menu by remember {mutableStateOf(false)}
                        Card(Modifier.fillMaxWidth().testTag("person-card:${person.id}").clickable {detailId=person.id},shape=androidx.compose.ui.graphics.RectangleShape,colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
                            Column {
                                PersonPhoto(person)
                                Row(Modifier.fillMaxWidth().padding(start=6.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(person.name,Modifier.weight(1f).testTag("person-name:${person.id}"),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                    Box {TextButton(onClick={menu=true},contentPadding=PaddingValues(0.dp),modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("person-options:${person.id}")) {LucideIcon(androidx.compose.material.icons.Icons.Default.MoreVert,"Opciones de ${person.name}")}
                                        DropdownMenu(menu,{menu=false}) {
                                            DropdownMenuItem(text={Text("Editar Persona")},onClick={menu=false;edit(person)},enabled=!busy)
                                            DropdownMenuItem(text={Text("Eliminar")},onClick={menu=false;deletingId=person.id},enabled=!busy)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editorId?.let { id ->
        androidx.compose.ui.window.Dialog(onDismissRequest={if(!busy) discardDraft()},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
                Surface(Modifier.widthIn(max=560.dp).fillMaxWidth().fillMaxHeight(.8f),shape=MaterialTheme.shapes.large,color=Arachn0deColors.Surface) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(if(isNew) "Nueva Persona" else "Editar Persona",style=MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier=Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                    BirthdayField(birthday.orEmpty(),{birthday=it},enabled=!busy && birthday!=null)
                    Spacer(Modifier.height(8.dp))
                    PersonPhotoComparison(Modifier.widthIn(max = 280.dp).fillMaxWidth(), profile = true) { photoModifier ->
                        PersonPhoto(Person(id, name.ifBlank { "?" }, avatar, zoom, frameX, frameY),
                            photoModifier.then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))))
                    }
                    TextButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Seleccionar avatar") }
                    if (avatar != null) TextButton(enabled = !busy, onClick = { cropOpen = true }) { Text("Ajustar encuadre") }
                    if (avatar != null) TextButton(enabled = !busy, onClick = {
                        val old = avatar; val owner = editorId; avatar = null; zoom = 1f; frameX = 0f; frameY = 0f
                        scope.launch { repository.discardAvatar(old, owner) }
                    }) { Text("Quitar avatar") }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            TextButton(enabled=!busy,onClick={discardDraft()}) {Text("Cancelar")}
                            TextButton(enabled=!busy && name.isNotBlank() && birthday!=null,onClick={
                                actions.save(id,name,avatar,isNew,framing(),birthday=birthday) {editorId=null;scope.launch {repository.discardAvatar(avatar,id)}}
                            }) {Text("Guardar")}
                        }
                    }
                }
            }
        }
    }
    if (cropOpen) (pendingPhoto ?: avatar)?.let { file ->
        AvatarEditor(file, framing(), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onConfirm = { value ->
            val old = avatar; val owner = editorId
            pendingPhoto?.let { avatar = it; pendingPhoto = null; scope.launch { repository.discardAvatar(old, owner) } }
            zoom = value.zoom; frameX = value.x; frameY = value.y; cropOpen = false
        }, onCancel = {
            val pending = pendingPhoto; val owner = editorId; pendingPhoto = null; cropOpen = false
            scope.launch { repository.discardAvatar(pending, owner) }
        })
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingId = null }, containerColor = Arachn0deColors.Surface,
            title = { Text("Eliminar Persona") },
            text = { Text("¿Eliminar ${people.firstOrNull { it.id == id }?.name.orEmpty()}? Se eliminarán su ficha, relaciones y asignaciones, incluidas sus Tecnologías asociadas. Las demás Personas, el catálogo y los elementos se conservan.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { actions.delete(id) { deletingId = null } }) { Text("Eliminar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("Cancelar") } },
        )
    }
    OperationErrorDialog(actions.operation)
    LoadErrorDialog(load)
}
