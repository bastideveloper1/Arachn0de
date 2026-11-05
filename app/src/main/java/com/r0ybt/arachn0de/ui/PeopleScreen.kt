package com.r0ybt.arachn0de.ui
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
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
import com.r0ybt.arachn0de.data.local.PersonProfileEntity
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.state.LoadState
import com.r0ybt.arachn0de.ui.state.PersonActions
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeopleScreen(repository: PersonRepository, initialPersonId:String?=null, initialSection:String="Inicio", onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val actions = remember(repository, scope) { PersonActions(repository, scope) }
    val load = remember(repository) { LoadState() }
    var people by remember { mutableStateOf(emptyList<Person>()) }
    var peopleReady by remember {mutableStateOf(false)}
    LaunchedEffect(repository, load.attempt) { load.collect(repository.observePeople()) { people = it;peopleReady=true } }
    var detailId by rememberSaveable { mutableStateOf(initialPersonId) }
    val favorites by remember(repository) {repository.observeFavorites()}.collectAsState(initial=null)
    val profiles by remember(repository) {repository.observeProfiles()}.collectAsState(initial=null)
    val technologies by remember(repository) {repository.observeTechnologyCatalog()}.collectAsState(emptyList())
    val personTechnologies by remember(repository) {repository.observePersonTechnologies()}.collectAsState(emptyList())
    val relations by remember(repository) {repository.observeRelations()}.collectAsState(emptyList())
    var category by rememberSaveable {mutableStateOf("")}
    var search by rememberSaveable {mutableStateOf("")}
    var detailSection by rememberSaveable {mutableStateOf(initialSection)}
    var detailRequest by rememberSaveable {mutableIntStateOf(0)}
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    val birthdays by remember(repository) {repository.observeBirthdays()}.collectAsState(initial=null as List<com.r0ybt.arachn0de.data.local.PersonBirthdayEntity>?)
    val identityRows by remember(repository) {repository.observeSelf().map {listOfNotNull(it)}}.collectAsState(initial=null)
    var entryResolved by rememberSaveable {mutableStateOf(initialPersonId!=null)}
    LaunchedEffect(repository) {repository.ensurePrincipal()}
    var resolvedPrincipal by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(identityRows,peopleReady,people,entryResolved) {
        if(initialPersonId==null && identityRows!=null && peopleReady) {
            val principal=identityRows?.firstOrNull()?.personId?.takeIf {candidate->people.any {it.id==candidate}}
            if(!entryResolved || principal!=resolvedPrincipal) {
                detailId=principal;detailSection=initialSection;resolvedPrincipal=principal;entryResolved=true
            }
        }
    }
    var isNew by rememberSaveable { mutableStateOf(false) }
    var profileValues by rememberSaveable {mutableStateOf(List(9) {""})}
    var initialProfileValues by rememberSaveable {mutableStateOf(List(9) {""})}
    var birthday by rememberSaveable {mutableStateOf("")}
    var initialBirthday by rememberSaveable {mutableStateOf("")}
    var initialIdentity by rememberSaveable {mutableStateOf(listOf<String?>("",null,"1.0","0.0","0.0"))}
    var confirmDiscard by remember {mutableStateOf(false)}
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf<String?>(null) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var frameX by rememberSaveable { mutableFloatStateOf(0f) }
    var frameY by rememberSaveable { mutableFloatStateOf(0f) }
    var pickerOpen by remember {mutableStateOf(false)}
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
        editorId = null; cropOpen = false;pickerOpen=false
        val pending = pendingPhoto; pendingPhoto = null
        scope.launch { repository.discardAvatar(file, owner); repository.discardAvatar(pending, owner) }
    }
    val camera=rememberPrivatePhotoCamera(!busy && editorId!=null,{actions.operation.report(it)}) {photo,done->
        val owner=editorId
        if(owner==null) done(false) else actions.import(android.net.Uri.EMPTY,onResult=done,sourceFile=photo.file) {imported->
            if(editorId==owner) {val old=avatar;avatar=imported;zoom=1f;frameX=0f;frameY=0f;scope.launch {repository.discardAvatar(old,owner)}}
            else scope.launch {repository.discardAvatar(imported,owner)}
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pickerOpen=false
        val owner=editorId
        if (uri != null && owner != null) actions.import(uri) { imported ->
            if(editorId==owner && !cropOpen && pendingPhoto==null) {val old=avatar;avatar=imported;zoom=1f;frameX=0f;frameY=0f;scope.launch {repository.discardAvatar(old,owner)}}
            else scope.launch {repository.discardAvatar(imported,owner)}
        }
    }
    fun pickPhoto() {
        if(busy || pickerOpen || cropOpen || editorId==null) return
        pickerOpen=true
        try {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
        catch(e:Exception) {pickerOpen=false;throw e}
    }
    fun openCrop() {if(!busy && !pickerOpen && !cropOpen && avatar!=null) cropOpen=true}
    fun changed()=profileValues!=initialProfileValues || birthday!=initialBirthday ||
        listOf(name,avatar,zoom.toString(),frameX.toString(),frameY.toString())!=initialIdentity
    fun cancelEditor() {if(changed()) confirmDiscard=true else discardDraft()}
    BackHandler { if (!busy) { if (editorId != null) cancelEditor() else if (deletingId != null) deletingId = null else onBack() } }
    fun edit(person:Person) {
        if(editorId!=null || profiles==null || birthdays==null || identityRows==null) return
        detailId=person.id;detailSection="Información";detailRequest++
    }
    fun newPerson() {editorId=UUID.randomUUID().toString();isNew=true;name="";avatar=null;zoom=1f;frameX=0f;frameY=0f;profileValues=List(9) {""};initialProfileValues=profileValues;birthday="";initialBirthday="";initialIdentity=listOf(name,avatar,zoom.toString(),frameX.toString(),frameY.toString())}
    if(detailId!=null) Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {
        key(detailId,detailRequest) {PersonDetailView(detailId!!,people,repository,::edit,detailSection,onNewPerson=::newPerson,onDeletePerson={if(it.id!=identityRows?.firstOrNull()?.personId) deletingId=it.id}) {if(initialPersonId!=null || detailId==identityRows?.firstOrNull()?.personId) onBack() else detailId=null}}
    } else if(!entryResolved) Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {Text("Cargando perfil principal…")}
    else Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GlobalChromeActions(enabled=!busy, leading=true)
                TextButton(enabled = !busy, onClick = onBack) { Text("Volver") }
                NavigationHeading("Antisocial",Modifier.weight(1f))
                GlobalChromeActions(enabled=!busy)
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(category.isEmpty(),{category=""},label={Text("Todas")})
                PersonCategory.entries.filter {c->relations.any {it.category==c.name}}.forEach {c->FilterChip(category==c.name,{category=c.name},label={Text(c.label)})}
            }
            val filtered=people.filter {p->personVisibleName(p,profiles.orEmpty().firstOrNull {it.personId==p.id}).contains(search.trim(),true) && (category.isEmpty() || relations.any {it.category==category && (it.sourceId==p.id || it.targetId==p.id)})}
            if(filtered.isEmpty()) Text(if(people.isEmpty()) "Aún no hay Personas. Crea la primera con Nueva Persona." else "No hay coincidencias.")
            val technologyIdsByPerson=remember(personTechnologies) {personTechnologies.groupBy {it.personId}}
            val birthdayByPerson=remember(birthdays) {birthdays.orEmpty().associateBy {it.personId}}
            val calendarNow=com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow(emptyList(),System::currentTimeMillis,onRefresh={},dayOnly=true)
            val today=java.time.Instant.ofEpochMilli(calendarNow).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("people-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(filtered,key={it.id}) {person->
                    val visibleName=personVisibleName(person,profiles.orEmpty().firstOrNull {it.personId==person.id})
                    var menu by remember {mutableStateOf(false)}
                    val birth=birthdayByPerson[person.id]?.date?.let {runCatching {com.r0ybt.arachn0de.data.local.PersonBirthDate.parse(it)}.getOrNull()}
                    val assignedIds=technologyIdsByPerson[person.id].orEmpty().map {it.technologyId}.toSet()
                    val assigned=emptyList<com.r0ybt.arachn0de.data.local.TechnologyEntity>()
                    GeneralCard(Modifier.fillMaxWidth().testTag("person-card:${person.id}").clickable {detailId=person.id;detailSection=initialSection},
                        shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            PersonPhoto(person.copy(name=visibleName),Modifier.size(64.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),framed=true)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(personVisibleName(person,profiles.orEmpty().firstOrNull {it.personId==person.id}),Modifier.weight(1f).testTag("person-name:${person.id}"),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                    if(birth!=null && birth.day.monthValue==today.monthValue && birth.day.dayOfMonth==today.dayOfMonth)
                                        Icon(androidx.compose.material.icons.Icons.Default.Cake,"Cumpleaños hoy",Modifier.size(18.dp),tint=androidx.compose.ui.graphics.Color(0xFFD39BAE))
                                }
                                if(birth!=null) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                    LucideIcon(androidx.compose.material.icons.Icons.Default.CalendarMonth,null,Modifier.size(14.dp),tint=Arachn0deColors.TextSecondary)
                                    Text(birth.format(locale),style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,maxLines=1)
                                }
                                if(assigned.isNotEmpty()) Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {assigned.forEach {TechnologyIcon(it,24.dp)}}
                            }
                            PersonFavoriteButton(person.id,favorites,repository)
                            Box {
                                IconButton(enabled=!busy,onClick={menu=true},modifier=Modifier.size(48.dp).testTag("person-options:${person.id}")) {LucideIcon(androidx.compose.material.icons.Icons.Default.MoreVert,"Opciones de $visibleName")}
                                if(menu) ContextActionMenu(personVisibleName(person,profiles.orEmpty().firstOrNull {it.personId==person.id}),{menu=false}) {
                                    action("Información general",androidx.compose.material.icons.Icons.Default.Edit,{menu=false;edit(person)},enabled=!busy && profiles!=null && birthdays!=null && identityRows!=null)
                                    if(person.id!=identityRows?.firstOrNull()?.personId) action("Eliminar perfil",androidx.compose.material.icons.Icons.Default.Delete,{menu=false;deletingId=person.id},enabled=!busy,destructive=true)
                                }
                            }
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val menuFits=maxWidth>=360.dp*androidx.compose.ui.platform.LocalDensity.current.fontScale
                ContextualBottomPanel {
                AlternatingBottomBar {
                    Row(Modifier.fillMaxWidth().padding(top=8.dp).testTag("people-navigation-actions"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(search,{search=it},label={Text("Buscar Personas")},singleLine=true,modifier=Modifier.weight(1f))
                        Box(Modifier.weight(1f)) {
                            CompactBarActions(listOf(BottomBarAction("Nueva Persona",Icons.Default.Add,!busy,run=::newPerson)),iconAbove=false,style=MaterialTheme.typography.labelLarge) {a->
                                Button(enabled=a.enabled,onClick=a.run,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(a.label)}
                            }
                        }
                    }
                }
                }
            }
        }
    }
    editorId?.let { id ->
        BottomModulesDialog(onDismissRequest={if(!busy) cancelEditor()},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Nueva Persona",style=MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("person-profile-fields"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                PersonPhoto(Person(id,name.ifBlank {"?"},avatar,zoom,frameX,frameY),Modifier.size(80.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable(enabled=!busy && !pickerOpen && !cropOpen,onClickLabel="Cambiar fotografía") {pickPhoto()},framed=true)
                                Column(Modifier.weight(1f)) {
                                    TextButton(enabled=!busy && !pickerOpen && !cropOpen,onClick={pickPhoto()}) {Text("Galería")}
                                    TextButton(enabled=!busy && !pickerOpen && !cropOpen,onClick=camera) {Text("Cámara")}
                                    if(avatar!=null) TextButton(enabled=!busy && !pickerOpen && !cropOpen,onClick={openCrop()}) {Text("Ajustar encuadre")}
                                    if(avatar!=null) TextButton(enabled=!busy,onClick={
                                        val old=avatar;val owner=editorId;avatar=null;zoom=1f;frameX=0f;frameY=0f
                                        scope.launch {repository.discardAvatar(old,owner)}
                                    }) {Text("Quitar avatar")}
                                }
                            }
                            OutlinedTextField(name,{name=it},label={Text("Nombre")},modifier=Modifier.fillMaxWidth(),enabled=!busy,singleLine=true)
                            listOf("Nombre","Biografía","Teléfono","Correo electrónico","","Enlaces").forEachIndexed {index,label->
                                if(index==0 || index==4) return@forEachIndexed
                                OutlinedTextField(profileValues[index],{value->
                                    if(value.length<=if(index==1) maxOf(150,initialProfileValues[1].length) else 16000)
                                        profileValues=profileValues.toMutableList().also {it[index]=value}
                                },label={Text(label)},modifier=Modifier.fillMaxWidth(),enabled=!busy,
                                    singleLine=index==0,maxLines=if(index==0) 1 else 4,
                                    supportingText=if(index==1) {{Text("${profileValues[1].length}/150")}} else null)
                                if(index==1) {Text("Fecha de nacimiento",style=MaterialTheme.typography.labelLarge);BirthdayField(birthday,{birthday=it},enabled=!busy)}
                            }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            TextButton(enabled=!busy,onClick={cancelEditor()}) {Text("Cancelar")}
                            TextButton(enabled=!busy && profiles!=null && birthdays!=null && identityRows!=null && name.isNotBlank() && com.r0ybt.arachn0de.data.local.PersonBirthDate.valid(birthday) && (profileValues[1].length<=150 || profileValues[1]==initialProfileValues[1]),onClick={
                                val original=profiles.orEmpty().firstOrNull {it.personId==id} ?: PersonProfileEntity(id)
                                val updated=original.copy(givenName=name.trim(),description=profileValues[1],phones=profileValues[2],emails=profileValues[3],links=profileValues[5])
                                actions.save(id,name,avatar,isNew,framing(),birthday=if(birthday!=initialBirthday) birthday else null,
                                    profile=updated) {editorId=null;scope.launch {repository.discardAvatar(avatar,id)}}
                            }) {Text("Guardar")}
                        }
                    }
                }
            }
        }
    }
    if(confirmDiscard) AlertDialog(onDismissRequest={confirmDiscard=false},title={Text("¿Descartar cambios?")},
        text={Text("Los cambios del perfil no se han guardado.")},
        confirmButton={TextButton(enabled=!busy,onClick={confirmDiscard=false;discardDraft()}) {Text("Descartar cambios")}},
        dismissButton={TextButton(onClick={confirmDiscard=false}) {Text("Seguir editando")}})
    if (cropOpen) (pendingPhoto ?: avatar)?.let { file ->
        AvatarEditor(file, framing(), enabled=!busy && !pickerOpen, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onConfirm = { value ->
            if(!cropOpen || busy) return@AvatarEditor
            cropOpen=false
            val old = avatar; val owner = editorId
            pendingPhoto?.let { avatar = it; pendingPhoto = null; scope.launch { repository.discardAvatar(old, owner) } }
            zoom = value.zoom; frameX = value.x; frameY = value.y
        }, onCancel = {
            if(!cropOpen || busy) return@AvatarEditor
            cropOpen=false
            val pending = pendingPhoto; val owner = editorId; pendingPhoto = null
            scope.launch { repository.discardAvatar(pending, owner) }
        })
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingId = null }, containerColor = Arachn0deColors.Surface,
            title = { Text("Eliminar Persona") },
            text = { Text("¿Eliminar ${people.firstOrNull { it.id == id }?.name.orEmpty()}? Se eliminarán su ficha, relaciones y asignaciones, incluidas sus Tecnologías asociadas. Las demás Personas, el catálogo y los elementos se conservan.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { actions.delete(id) { deletingId = null;if(detailId==id) {detailId=null;entryResolved=false} } }) { Text("Eliminar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("Cancelar") } },
        )
    }
    OperationErrorDialog(actions.operation)
    LoadErrorDialog(load)
}
