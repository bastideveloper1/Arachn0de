package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PersonDetailView(initialId:String,people:List<Person>,repository:PersonRepository,onEditIdentity:(Person)->Unit,initialSection:String="General",onBack:()->Unit) {
    var path by rememberSaveable(initialId) {mutableStateOf(listOf(initialId))}
    var section by rememberSaveable {mutableStateOf(initialSection)}
    var editing by remember {mutableStateOf(false)}
    var editingRelation by remember {mutableStateOf<PersonRelationEntity?>(null)}
    var adding by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<PersonRelationEntity?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val self by remember(repository) {repository.observeSelf()}.collectAsState(initial=null)
    val scope=rememberCoroutineScope()
    val profiles by remember(repository) {repository.observeProfiles()}.collectAsState(initial=null)
    val relations by remember(repository) {repository.observeRelations()}.collectAsState(initial=null)
    val birthdays by remember(repository) {repository.observeBirthdays()}.collectAsState(emptyList())
    var birthdayEditor by remember {mutableStateOf(false)}
    var birthdayDraft by rememberSaveable {mutableStateOf("")}
    val id=path.last();val person=people.firstOrNull {it.id==id}
    val profile=profiles?.firstOrNull {it.personId==id} ?: PersonProfileEntity(id)
    fun back() {if(path.size>1) {path=path.dropLast(1);section="General"} else onBack()}
    BackHandler {if(!busy) back()}
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;scope.launch {try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar."} finally {busy=false}}}
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth()) {Text(person?.name ?: "Persona no disponible",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);TextButton(enabled=!busy,onClick={back()}) {Text("Volver")};GlobalChromeActions(enabled=!busy)}
        if(person==null) {Text("La Persona fue eliminada o aún se está cargando.");return@Column}
        if(profiles==null || relations==null) {Text("Cargando ficha…");return@Column}
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {listOf("General","Contacto","Perfil","Relaciones","Tecnologías","Proyectos y trabajo").forEach {s->FilterChip(selected=section==s,onClick={section=s},label={Text(s)},modifier=Modifier.padding(end=6.dp).heightIn(min=48.dp))}}
        if(section=="Tecnologías") PersonTechnologiesSection(id,repository)
        else if(section=="Proyectos y trabajo") PersonWorkSection(id,repository)
        else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(section=="General") {
                item {PersonPhotoHeader(person,profile,repository) {onEditIdentity(person)}}
                item {TextButton(enabled=!busy,onClick={perform {repository.identifySelf(if(self?.personId==id) null else id)}}) {Text(if(self?.personId==id) "Yo · Quitar identificación" else "Identificar como Yo")}}
                item {val birth=birthdays.firstOrNull {it.personId==id}?.date
                    if(birth!=null) Text("Nacimiento: $birth · ${personAge(birth)?.let {"$it años"} ?: "Edad no disponible"}")
                    TextButton(onClick={birthdayDraft=birth.orEmpty();birthdayEditor=true}) {Text(if(birth==null) "Añadir fecha de nacimiento" else "Editar fecha de nacimiento")}
                }
            }
            if(section!="Relaciones") {
                item {TextButton(enabled=!busy,onClick={editing=true}) {Text("Editar $section")}}
                val fields=profileFields(profile,section).filter {section!="General" || it.first=="Notas"}
                if(fields.all {it.second.isBlank()}) item {Text("Sin información adicional.")}
                items(fields.filter {it.second.isNotBlank()}) {(label,value)->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {Text(label,style=MaterialTheme.typography.labelLarge);Text(value)}}}
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
                                Row(Modifier.weight(1f).heightIn(min=48.dp).clickable(enabled=other!=null) {other?.let {path=path+it.id;section="General"}},verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
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
    if(birthdayEditor) Dialog(onDismissRequest={if(!busy) birthdayEditor=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.65f).padding(12.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Fecha de nacimiento",style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    BirthdayField(birthdayDraft,{birthdayDraft=it},enabled=!busy)
                }
                FlowRow {TextButton(enabled=!busy,onClick={birthdayEditor=false}) {Text("Cancelar")};TextButton(enabled=!busy,onClick={perform {repository.saveBirthday(id,birthdayDraft.trim());birthdayEditor=false}}) {Text("Guardar")}}
            }
        }
    }
    if(editing) PersonSectionEditor(profile,section,{editing=false}) {updated->perform {repository.saveProfile(updated);editing=false}}
    if(adding || editingRelation!=null) PersonRelationEditor(id,people,editingRelation,{adding=false;editingRelation=null}) {relation,isNew->perform {repository.saveRelation(relation,isNew);adding=false;editingRelation=null}}
    deleting?.let {r->AlertDialog(onDismissRequest={if(!busy) deleting=null},title={Text("Eliminar vínculo")},text={Text("Solo se eliminará esta relación y su presentación inversa. Ambas Personas se conservarán.")},confirmButton={TextButton(enabled=!busy,onClick={perform {repository.deleteRelation(r.id);deleting=null}}) {Text("Eliminar")}},dismissButton={TextButton(enabled=!busy,onClick={deleting=null}) {Text("Cancelar")}})}
    error?.let {AlertDialog(onDismissRequest={error=null},title={Text("No se pudo guardar")},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}
internal fun profileFields(p:PersonProfileEntity,section:String)=when(section) {
    "General"->listOf("Alias" to p.alias,"Descripción" to p.description,"Notas" to p.notes)
    "Contacto"->listOf("Teléfonos" to p.phones,"Correos" to p.emails,"Direcciones" to p.addresses,"Enlaces" to p.links)
    else->listOf("Cualidades" to p.qualities,"Habilidades" to p.skills,"Intereses" to p.interests,"Disponibilidad" to p.availability)
}
@Composable private fun PersonSectionEditor(p:PersonProfileEntity,section:String,onCancel:()->Unit,onSave:(PersonProfileEntity)->Unit) {
    val fields=profileFields(p,section)
    var values by rememberSaveable(p.personId,section) {mutableStateOf(fields.map {it.second})}
    Dialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false)) {Surface(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(12.dp),shape=MaterialTheme.shapes.large) {Column(Modifier.padding(12.dp)) {
        Text("Editar $section",style=MaterialTheme.typography.titleLarge)
        if(section=="Contacto") Text("Puedes escribir varias entradas, una por línea. Todos los campos son opcionales.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {fields.forEachIndexed {i,(label,_)->OutlinedTextField(values[i],{text->if(text.length<=16000) values=values.toMutableList().also {it[i]=text}},label={Text(label)},modifier=Modifier.fillMaxWidth(),minLines=if(label=="Alias") 1 else 2,maxLines=4)}}
        Row {TextButton(onClick=onCancel) {Text("Cancelar")};TextButton(onClick={onSave(when(section) {"General"->p.copy(alias=values[0],description=values[1],notes=values[2]);"Contacto"->p.copy(phones=values[0],emails=values[1],addresses=values[2],links=values[3]);else->p.copy(qualities=values[0],skills=values[1],interests=values[2],availability=values[3])})}) {Text("Guardar sección")}}
    }}}
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
    Dialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false)) {Surface(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(12.dp),shape=MaterialTheme.shapes.large) {Column(Modifier.padding(12.dp)) {
        Text(if(original==null) "Añadir relación" else "Editar vínculo",style=MaterialTheme.typography.titleLarge)
        Text("Origen: ${people.firstOrNull {it.id==source}?.name.orEmpty()}")
        if(stage==0) {
            OutlinedTextField(query,{query=it},label={Text("Buscar Persona relacionada")},modifier=Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f)) {items(people.filter {it.id!=source && it.name.contains(query,true)},key={it.id}) {p->TextButton(onClick={target=p.id;stage=1},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(p.name)}}}
        } else {
            TextButton(onClick={stage=0}) {Text("Persona relacionada: ${people.firstOrNull {it.id==target}?.name.orEmpty()}")}
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
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
        Row {TextButton(onClick=onCancel) {Text("Cancelar")};if(stage==1) TextButton(enabled=target.isNotEmpty() && label.isNotBlank() && (mode!="DIRECTED" || inverse.isNotBlank()),onClick={val l=label.trim();val inv=when(mode) {"DIRECTED"->inverse.trim();"RECIPROCAL"->l;else->""};onSave(PersonRelationEntity(original?.id ?: UUID.randomUUID().toString(),source,target,category,l,inv,meaning,mode,relationKey(source,target,category,l,inv,mode)),original==null)}) {Text("Guardar vínculo")}}
    }}}
}
