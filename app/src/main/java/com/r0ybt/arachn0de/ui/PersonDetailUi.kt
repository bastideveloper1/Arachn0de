package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable internal fun PersonDetailDialog(id:String,onDismiss:()->Unit) {
    val repository=(LocalContext.current.applicationContext as Arachn0deApplication).personRepository
    val navigation=LocalPersonNavigation.current
    val globalNavigation=LocalGlobalNavigation.current
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        CompositionLocalProvider(LocalGlobalNavigation provides globalNavigation?.copy(menu={onDismiss();globalNavigation.menu()},showLock=true),LocalPersonNavigation provides navigation?.let {open->{destination:PersonDestination->onDismiss();open(destination)}}) {
        Surface(Modifier.fillMaxSize()) {PeopleScreen(repository,initialPersonId=id,onBack=onDismiss)}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun PersonDetailView(initialId:String,people:List<Person>,repository:PersonRepository,onEditIdentity:(Person)->Unit,initialSection:String="Inicio",onEditAvatar:(Person)->Unit=onEditIdentity,onNewPerson:(()->Unit)?=null,onDeletePerson:((Person)->Unit)?=null,onBack:()->Unit) {
    var path by rememberSaveable(initialId) {mutableStateOf(listOf(initialId))}
    var section by rememberSaveable {mutableStateOf(initialSection)}
    var editingSection by rememberSaveable {mutableStateOf<String?>(null)}
    var editingRelation by remember {mutableStateOf<PersonRelationEntity?>(null)}
    var adding by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<PersonRelationEntity?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val profiles by remember(repository) {repository.observeProfiles()}.collectAsState(initial=null)
    val relations by remember(repository) {repository.observeRelations()}.collectAsState(initial=null)
    val identity by remember(repository) {repository.observeSelf()}.collectAsState(initial=null)
    val publications by remember(repository) {repository.observePublications()}.collectAsState(initial=null)
    val favorites by remember(repository) {repository.observeFavorites()}.collectAsState(initial=null)
    val photos by remember(repository) {repository.observePhotos()}.collectAsState(initial=emptyList())
    val birthdays by remember(repository) {repository.observeBirthdays()}.collectAsState(initial=null)
    val sectionDrafts=rememberSaveableStateHolder()
    var sectionRequest by rememberSaveable {mutableIntStateOf(0)}
    var profileMenu by remember {mutableStateOf(false)}
    val id=path.last();val person=people.firstOrNull {it.id==id}
    var networkSearch by rememberSaveable(id) {mutableStateOf("")}
    var networkCategory by rememberSaveable(id) {mutableStateOf("")}
    var viewing by rememberSaveable(id) {mutableStateOf<String?>(null)}
    val network=people.filter {contact->contact.id!=id && (identity?.personId==id || relations.orEmpty().any {it.other(id)==contact.id && (it.sourceId==id || it.targetId==id)})}
    val profile=profiles?.firstOrNull {it.personId==id} ?: PersonProfileEntity(id)
    LaunchedEffect(id,section,profiles!=null,birthdays!=null) {
        editingSection=if(profiles!=null && birthdays!=null && section in listOf("General","Contacto","Perfil")) section else null
    }
    LaunchedEffect(section) {if(section!="Relaciones") {adding=false;editingRelation=null}}
    fun back() {if(section=="Red") section="Inicio" else if(path.size>1) {path=path.dropLast(1);section="Inicio"} else onBack()}
    BackHandler {if(!busy) back()}
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;scope.launch {try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar."} finally {busy=false}}}
    BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
    val identityHeight=(maxHeight*.4f).coerceAtLeast(96.dp)
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth()) {GlobalChromeActions(enabled=!busy, leading=true);TextButton(enabled=!busy,onClick={back()}) {Text("Volver")};Text(person?.name ?: "Persona no disponible",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Box {
            IconButton(enabled=!busy && person!=null && profiles!=null && birthdays!=null,onClick={profileMenu=true}) {Icon(Icons.Default.MoreVert,"Opciones del perfil")}
            DropdownMenu(profileMenu,{profileMenu=false}) {
                DropdownMenuItem(text={Text("Editar perfil")},onClick={profileMenu=false;person?.let(onEditIdentity)})
                if(onDeletePerson!=null) DropdownMenuItem(text={Text("Eliminar")},onClick={profileMenu=false;person?.let(onDeletePerson)},enabled=!busy)
            }
        };GlobalChromeActions(enabled=!busy)}
        if(person==null) {Text("La Persona fue eliminada o aún se está cargando.");return@Column}
        if(profiles==null || relations==null) {Text("Cargando ficha…");return@Column}
        if(section=="Inicio") PersonPhotoHeader(person,profile,birthday=birthdays.orEmpty().firstOrNull {it.personId==id}?.date,maximumHeight=identityHeight,
            onEditAvatar={viewing=person.avatarFile?.let {"primary:${person.id}"} ?: photos.firstOrNull {it.personId==id}?.id},
            publications=publications?.count {it.personId==id},networkCount=network.size,onNetwork={section="Red"})
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            val sections=listOf("Inicio" to Icons.Default.Home,"General" to Icons.Default.Info,"Contacto" to Icons.Default.Call,"Perfil" to Icons.Default.Person,
                "Relaciones" to Icons.Default.Groups,"Tecnologías" to Icons.Default.Code,"Proyectos y trabajo" to Icons.Default.Work)
            sections.forEach {(label,icon)->
                val visible=remember(label) {BringIntoViewRequester()}
                LaunchedEffect(section) {if(section==label) visible.bringIntoView()}
                Column(Modifier.width(IntrinsicSize.Max)) {
                    IconButton(enabled=!busy,onClick={section=label;sectionRequest++;editingSection=label.takeIf {it in listOf("General","Contacto","Perfil")}},modifier=Modifier.size(48.dp).bringIntoViewRequester(visible).testTag("person-section:$label").semantics {selected=section==label}) {
                        Icon(icon,label,Modifier.size(20.dp),tint=if(section==label) com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary else com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextSecondary)
                    }
                    if(section==label) HorizontalDivider(thickness=2.dp,color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if(section=="Inicio") PersonMemoryGallery(id,repository) {}
        else if(section=="Tecnologías") PersonTechnologiesSection(id,repository,openOnEntry=true,openRequest=sectionRequest)
        else if(section=="Proyectos y trabajo") PersonWorkSection(id,repository)
        else if(section=="Red") {
            val contacts=network.filter {contact->
                (contact.name.contains(networkSearch.trim(),true) || profiles.orEmpty().firstOrNull {it.personId==contact.id}?.alias?.contains(networkSearch.trim(),true)==true) &&
                (networkCategory.isEmpty() || relations.orEmpty().any {it.category==networkCategory && (it.sourceId==contact.id || it.targetId==contact.id)})}.sortedBy {it.id !in favorites.orEmpty()}
            Column(Modifier.fillMaxSize()) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(networkCategory.isEmpty(),{networkCategory=""},label={Text("Todas")})
                    PersonCategory.entries.filter {c->relations.orEmpty().any {it.category==c.name}}.forEach {c->FilterChip(networkCategory==c.name,{networkCategory=c.name},label={Text(c.label)})}
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("person-network-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(contacts.isEmpty()) item {Text("Sin contactos en esta Red.")}
                    items(contacts,key={it.id}) {contact->
                        Card(Modifier.fillMaxWidth().testTag("network-contact:${contact.id}").clickable {path=path+contact.id;section="Inicio"},shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),colors=CardDefaults.cardColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                PersonPhoto(contact,Modifier.size(64.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),framed=true)
                                Column(Modifier.weight(1f)) {
                                    Text(contact.name)
                                    profiles.orEmpty().firstOrNull {it.personId==contact.id}?.alias?.takeIf {it.isNotBlank()}?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                                    relations.orEmpty().filter {(it.sourceId==id && it.targetId==contact.id) || (it.targetId==id && it.sourceId==contact.id)}
                                        .forEach {Text(it.presentation(id),style=MaterialTheme.typography.bodySmall)}
                                }
                                val favorite=contact.id in favorites.orEmpty()
                                IconButton(enabled=!busy && favorites!=null,onClick={perform {repository.toggleFavorite(contact.id)}},modifier=Modifier.size(48.dp).testTag("person-favorite:${contact.id}").semantics {selected=favorite}) {
                                    Icon(if(favorite) Icons.Default.Star else Icons.Default.StarBorder,if(favorite) "Quitar de favoritos" else "Marcar favorito",Modifier.size(20.dp),tint=if(favorite) com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary else com.r0ybt.arachn0de.ui.theme.BlackWhite90.Inactive)
                                }
                                if(onDeletePerson!=null) Box {
                                    var menu by remember {mutableStateOf(false)}
                                    IconButton(onClick={menu=true},modifier=Modifier.size(48.dp).testTag("person-options:${contact.id}")) {Icon(Icons.Default.MoreVert,"Opciones de ${contact.name}")}
                                    if(menu) ContextActionMenu(contact.name,{menu=false}) {
                                        action("Editar perfil",Icons.Default.Edit,{menu=false;onEditIdentity(contact)},enabled=!busy && birthdays!=null)
                                        action("Eliminar",Icons.Default.Delete,{menu=false;onDeletePerson(contact)},enabled=!busy,destructive=true)
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(networkSearch,{networkSearch=it},label={Text("Buscar Personas")},singleLine=true,modifier=Modifier.fillMaxWidth())
                if(identity?.personId==id) onNewPerson?.let {create->Button(onClick=create,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Nueva Persona")}}
            }
        }
        else LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(section!="Relaciones") {
                if(section=="Perfil") item {
                    val birth=birthdays.orEmpty().firstOrNull {it.personId==id}?.date
                    Card(Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {Text("Fecha de nacimiento",style=MaterialTheme.typography.labelLarge);Text(birth ?: "Sin fecha")}
                        }
                    }
                }
                val fields=profileFields(profile.copy(alias=person.name),section)
                items(fields) {(label,value)->
                    Card(Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {Text(label,style=MaterialTheme.typography.labelLarge);Text(value.ifBlank {"Sin información"})}
                        }
                    }
                }
            } else {
                item {TextButton(enabled=!busy && people.size>1,onClick={adding=true}) {Text("Añadir relación")};if(people.size<2) Text("Crea otra Persona desde el listado para vincularla.")}
                val related=relations.orEmpty().filter {it.sourceId==id || it.targetId==id}
                if(related.isEmpty()) item {Text("Sin relaciones.")}
                PersonCategory.entries.forEach {category->
                    val group=related.filter {it.category==category.name}
                    if(group.isNotEmpty()) item {Text(category.label,style=MaterialTheme.typography.titleMedium)}
                    items(group,key={it.id}) {r->val other=people.firstOrNull {it.id==r.other(id)}
                        Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                            var menu by remember {mutableStateOf(false)}
                            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).heightIn(min=48.dp).clickable(enabled=other!=null) {other?.let {path=path+it.id;section="Inicio"}},verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    other?.let {PersonAvatar(it,48.dp,true)}
                                    Column(Modifier.padding(start=10.dp)) {Text(other?.name ?: "Persona no disponible");Text(r.presentation(id),style=MaterialTheme.typography.labelLarge)}
                                }
                                Box {TextButton(onClick={menu=true}) {Text("⋮")};DropdownMenu(menu,{menu=false}) {
                                    DropdownMenuItem(text={Text("Editar vínculo")},onClick={menu=false;editingRelation=r},enabled=!busy)
                                    DropdownMenuItem(text={Text("Eliminar vínculo")},onClick={menu=false;deleting=r},enabled=!busy)
                                }}
                            }
                            if(r.meaning.isNotBlank()) Text(r.meaning)
                        }}
                    }
                }
            }
        }
        }
        editingSection?.takeIf {profiles!=null && birthdays!=null}?.let {editorSection->sectionDrafts.SaveableStateProvider("$id:$editorSection") {
            PersonSectionEditor(person,profile,editorSection,birthdays.orEmpty().firstOrNull {it.personId==id}?.date.orEmpty(),busy,sectionRequest,
                {sectionDrafts.removeState("$id:$editorSection");editingSection=null}) {values->perform {
                val updated=when(editorSection) {
                    "General"->profile.copy(alias=values[0].trim(),description=values[1],notes=values[2])
                    "Contacto"->profile.copy(phones=values[0],emails=values[1],addresses=values[2],links=values[3])
                    else->profile.copy(qualities=values[0],skills=values[1],interests=values[2],availability=values[3])
                }
                require(repository.save(id,if(editorSection=="General") values[0] else person.name,person.avatarFile,isNew=false,
                    zoom=person.avatarZoom,x=person.avatarX,y=person.avatarY,profile=updated,
                    birthday=if(editorSection=="Perfil") values[4] else null)) {"La Persona ya no existe."}
                sectionDrafts.removeState("$id:$editorSection");editingSection=null
            }}
        }}
    }
    }
    if(person!=null) viewing?.let {selected->
        PersonPhotoViewer(person,photos.filter {it.personId==id},selected,busy,{viewing=it},
            {photo->perform {repository.setPhotoAsAvatar(id,photo.id)}},{viewing=null},repository)
    }
    if(section=="Relaciones" && (adding || editingRelation!=null)) key(id,editingRelation?.id) {PersonRelationEditor(id,people,editingRelation,{adding=false;editingRelation=null}) {relation,isNew->perform {repository.saveRelation(relation,isNew);adding=false;editingRelation=null}}}
    deleting?.let {r->AlertDialog(onDismissRequest={if(!busy) deleting=null},title={Text("Eliminar vínculo")},text={Text("Solo se eliminará esta relación y su presentación inversa. Ambas Personas se conservarán.")},confirmButton={TextButton(enabled=!busy,onClick={perform {repository.deleteRelation(r.id);deleting=null}}) {Text("Eliminar")}},dismissButton={TextButton(enabled=!busy,onClick={deleting=null}) {Text("Cancelar")}})}
    error?.let {AlertDialog(onDismissRequest={error=null},title={Text("No se pudo guardar")},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}
internal fun profileFields(p:PersonProfileEntity,section:String)=when(section) {
    "General"->listOf("Apodo" to p.alias,"Descripción" to p.description,"Notas" to p.notes)
    "Contacto"->listOf("Teléfonos" to p.phones,"Correos" to p.emails,"Direcciones" to p.addresses,"Enlaces" to p.links)
    else->listOf("Cualidades" to p.qualities,"Habilidades" to p.skills,"Intereses" to p.interests,"Disponibilidad" to p.availability)
}
@Composable private fun PersonSectionEditor(person:Person,p:PersonProfileEntity,section:String,birthday:String,busy:Boolean,menuRequest:Int,
    onCancel:()->Unit,onSave:(List<String>)->Unit) {
    val fields=profileFields(p.copy(alias=person.name),section)+(if(section=="Perfil") listOf("Fecha de nacimiento" to birthday) else emptyList())
    ModularTextFieldsEditor(editorKey="${p.personId}:$section",title="Editar $section",labels=fields.map {it.first},
        initialValues=fields.map {it.second},saveLabel="Guardar sección",onCancel=onCancel,inline=true,enabled=!busy,menuRequest=menuRequest,compactMenu=true,
        fieldIcon={index->antisocialFieldIcon(fields[index].first)},
        validField={index,value->section!="General" || when(index) {0->value.isNotBlank();1->value.length<=150 || value==p.description;else->true}},
        fieldEditor={index,value,change->
            if(section=="Perfil" && index==4) BirthdayField(value,change,enabled=!busy)
            else ModularTextField(value,change,fields[index].first,!busy,singleLine=section=="General" && index==0)
        },onSave=onSave)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PersonRelationEditor(current:String,people:List<Person>,original:PersonRelationEntity?,onCancel:()->Unit,onSave:(PersonRelationEntity,Boolean)->Unit) {
    val source=original?.sourceId ?: current
    var target by rememberSaveable {mutableStateOf(original?.targetId ?: "")}
    var category by rememberSaveable {mutableStateOf(original?.category ?: "FAMILY")}
    var label by rememberSaveable {mutableStateOf(original?.label ?: "")}
    var inverse by rememberSaveable {mutableStateOf(original?.inverseLabel ?: "")}
    var meaning by rememberSaveable {mutableStateOf(original?.meaning ?: "")}
    var mode by rememberSaveable {mutableStateOf(original?.mode ?: "ONE_WAY")}
    var stage by rememberSaveable {mutableIntStateOf(if(original==null) 0 else 1)}
    var query by rememberSaveable {mutableStateOf("")}
    ModularPanelDialog(onDismissRequest=onCancel,compact=true,plainBody=true,fieldOwnsScroll=true,
        foldLabel=if(original==null) "Añadir relación" else "Editar vínculo",
        title={Text(if(original==null) "Añadir relación" else "Editar vínculo")},
        text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Origen: ${people.firstOrNull {it.id==source}?.name.orEmpty()}")
            if(stage==0) {
                OutlinedTextField(query,{query=it},label={Text("Buscar Persona relacionada")},singleLine=true,modifier=Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f,fill=false)) {items(people.filter {it.id!=source && it.name.contains(query,true)},key={it.id}) {p->TextButton(onClick={target=p.id;stage=1},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(p.name,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Start)}}}
            } else {
                TextButton(onClick={stage=0}) {Text("Persona relacionada: ${people.firstOrNull {it.id==target}?.name.orEmpty()}")}
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Categoría")
                    FlowRow {PersonCategory.entries.forEach {c->FilterChip(selected=category==c.name,onClick={if(category!=c.name) {category=c.name;label="";inverse="";mode="ONE_WAY"}},label={Text(c.label)},modifier=Modifier.padding(end=4.dp).heightIn(min=48.dp))}}
                    val presets=personPresets(PersonCategory.valueOf(category))
                    if(presets.isNotEmpty()) {Text("Tipo de vínculo (o escribe otro)");FlowRow {presets.forEach {p->TextButton(onClick={label=p.label;inverse=p.inverse;mode=p.mode.name}) {Text(p.label)}}}}
                    Text("El origen es… respecto a la Persona relacionada.")
                    OutlinedTextField(label,{if(it.length<=200) label=it},label={Text("Nombre del vínculo")},modifier=Modifier.fillMaxWidth())
                    FlowRow {PersonRelationMode.entries.forEach {m->FilterChip(selected=mode==m.name,onClick={mode=m.name},label={Text(m.label)},modifier=Modifier.padding(end=4.dp).heightIn(min=48.dp))}}
                    if(mode=="DIRECTED") OutlinedTextField(inverse,{if(it.length<=200) inverse=it},label={Text("Nombre de la relación inversa")},modifier=Modifier.fillMaxWidth())
                    Text(when(mode) {"DIRECTED"->"La otra ficha mostrará la inversa calculada del mismo vínculo.";"RECIPROCAL"->"Ambas fichas muestran el mismo tipo de vínculo.";else->"No se supone reciprocidad. La otra ficha identifica el vínculo como entrante."})
                    OutlinedTextField(meaning,{if(it.length<=16000) meaning=it},label={Text("Significado o notas (opcional)")},modifier=Modifier.fillMaxWidth(),maxLines=4)
                }
            }
        }},
        dismissButton={TextButton(onClick=onCancel) {Text("Cancelar")}},
        confirmButton={if(stage==1) TextButton(enabled=target.isNotEmpty() && label.isNotBlank() && (mode!="DIRECTED" || inverse.isNotBlank()),onClick={val l=label.trim();val inv=when(mode) {"DIRECTED"->inverse.trim();"RECIPROCAL"->l;else->""};onSave(PersonRelationEntity(original?.id ?: UUID.randomUUID().toString(),source,target,category,l,inv,meaning,mode,relationKey(source,target,category,l,inv,mode)),original==null)}) {Text("Guardar vínculo")}})
}

private fun antisocialFieldIcon(label:String)=when(label) {
    "Apodo" -> androidx.compose.material.icons.Icons.Default.Person
    "Teléfonos" -> androidx.compose.material.icons.Icons.Default.Phone
    "Correos" -> androidx.compose.material.icons.Icons.Default.Email
    "Direcciones" -> androidx.compose.material.icons.Icons.Default.Place
    "Enlaces" -> androidx.compose.material.icons.Icons.Default.Link
    "Fecha de nacimiento" -> androidx.compose.material.icons.Icons.Default.DateRange
    "Disponibilidad" -> androidx.compose.material.icons.Icons.Default.Schedule
    "Intereses" -> androidx.compose.material.icons.Icons.Default.FavoriteBorder
    "Habilidades" -> androidx.compose.material.icons.Icons.Default.Build
    "Cualidades" -> androidx.compose.material.icons.Icons.Default.StarBorder
    "Notas" -> androidx.compose.material.icons.Icons.Default.Notes
    else -> androidx.compose.material.icons.Icons.Default.Subject
}
