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
    BottomModulesDialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        CompositionLocalProvider(LocalGlobalNavigation provides globalNavigation?.copy(menu={onDismiss();globalNavigation.menu()},showLock=true),LocalPersonNavigation provides navigation?.let {open->{destination:PersonDestination->onDismiss();open(destination)}}) {
        Surface(Modifier.fillMaxSize()) {PeopleScreen(repository,initialPersonId=id,onBack=onDismiss)}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun PersonDetailView(initialId:String,people:List<Person>,repository:PersonRepository,onEditIdentity:(Person)->Unit,initialSection:String="Inicio",onEditAvatar:(Person)->Unit=onEditIdentity,onNewPerson:(()->Unit)?=null,onDeletePerson:((Person)->Unit)?=null,onBack:()->Unit) {
    var path by rememberSaveable(initialId) {mutableStateOf(listOf(initialId))}
    var section by rememberSaveable {mutableStateOf(antisocialSection(initialSection))}
    LaunchedEffect(section) {if(section=="Tecnologías")section="Inicio"}
    var editingInfo by rememberSaveable(initialId) {mutableStateOf(initialSection in listOf("Información","General","Contacto","Perfil"))}
    var editingRelation by remember {mutableStateOf<PersonRelationEntity?>(null)}
    var relationAction by rememberSaveable {mutableStateOf<String?>(null)}
    var networkActions by rememberSaveable {mutableStateOf(false)}
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
    val id=path.last();val person=people.firstOrNull {it.id==id}
    var networkSearch by rememberSaveable(id) {mutableStateOf("")}
    var networkCategory by rememberSaveable(id) {mutableStateOf("")}
    var viewing by rememberSaveable(id) {mutableStateOf<String?>(null)}
    val network=people.filter {it.id!=id}
    val app=LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication
    val banners by remember(app.database) {app.database.personProfileDao().observeBanners()}.collectAsState(emptyList())
    val bannerFiles by remember(app.attachmentRepository) {app.attachmentRepository.observeFiles()}.collectAsState(emptyList())
    val memoryImages by remember(app.database) {app.database.personMemoryDao().observeImages()}.collectAsState(emptyList())
    var editingBanner by rememberSaveable(id) {mutableStateOf(false)}
    val profile=profiles?.firstOrNull {it.personId==id} ?: PersonProfileEntity(id)
    val editingSection=if(editingInfo && section=="Inicio" && profiles!=null && birthdays!=null) "Información" else null
    LaunchedEffect(id,section) {relationAction=null;networkActions=false;if(section!="Personas") {adding=false;editingRelation=null}}
    fun back() {if(editingInfo) editingInfo=false else if(section!="Inicio") section="Inicio" else if(path.size>1) {path=path.dropLast(1);section="Personas"} else onBack()}
    BackHandler {if(!busy) back()}
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;scope.launch {try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar."} finally {busy=false}}}
    BoxWithConstraints(Modifier.fillMaxSize().padding(top=14.dp)) {
    val identityHeight=(maxHeight*.4f).coerceAtLeast(96.dp)
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp)) {GlobalChromeActions(enabled=!busy, leading=true);TextButton(enabled=!busy && !editingBanner,onClick={back()}) {Text("Volver")};NavigationHeading("Antisocial · "+(person?.let {personVisibleName(it,profile)} ?: "Persona no disponible"),Modifier.weight(1f));PersonFavoriteButton(id,favorites,repository);GlobalChromeActions(enabled=!busy)}
        if(person==null) {Text("La Persona fue eliminada o aún se está cargando.");return@Column}
        if(profiles==null || relations==null) {Text("Cargando ficha…");return@Column}
        val tabs:@Composable ()->Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal=if(section=="Inicio") 0.dp else 14.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            val sections=antisocialSections.map {label->label to when(label) {"Inicio"->Icons.Default.Home;"Personas"->Icons.Default.Groups;"Tecnologías"->Icons.Default.Code;else->Icons.Default.Work}}
            sections.forEach {(label,icon)->
                val visible=remember(label) {BringIntoViewRequester()}
                LaunchedEffect(section) {if(section==label) visible.bringIntoView()}
                Column(Modifier.width(IntrinsicSize.Max)) {
                    IconButton(enabled=!busy && !editingBanner,onClick={section=label;editingInfo=false;sectionRequest++},modifier=Modifier.size(48.dp).bringIntoViewRequester(visible).testTag("person-section:$label").semantics {selected=section==label}) {
                        Icon(icon,label,Modifier.size(20.dp),tint=if(section==label) com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary else com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextSecondary)
                    }
                    if(section==label) HorizontalDivider(thickness=2.dp,color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary)
                }
            }
        }
        }
        if(section!="Inicio") tabs()
        Box(Modifier.weight(1f).fillMaxWidth().then(if(section=="Tecnologías") Modifier else Modifier.padding(horizontal=14.dp))) {
        if(section=="Inicio") PersonMemoryGallery(id,repository) {item(key="person-profile-header") {
        Box(Modifier.fillMaxWidth()) { PersonPhotoHeader(person.copy(name=personVisibleName(person,profile)),profile,principal=identity?.personId==id,birthday=birthdays.orEmpty().firstOrNull {it.personId==id}?.date,maximumHeight=identityHeight,banner=banners.firstOrNull {it.personId==id},files=bannerFiles,
            onEditAvatar={viewing=person.avatarFile?.let {"primary:${person.id}"} ?: photos.firstOrNull {it.personId==id}?.id},
            publications=publications?.count {it.personId==id},networkCount=network.size,onNetwork={section="Personas"},onBanner={editingBanner=true}) }
            PersonContactRows(profile,birthdays.orEmpty().firstOrNull {it.personId==id}?.date)
            tabs()
        }}
        else if(section=="Tecnologías") PersonTechnologiesSection(id,repository,openOnEntry=true,openRequest=sectionRequest,contentModifier=Modifier.padding(horizontal=14.dp))
        else if(section=="Proyectos") PersonWorkSection(id,repository)
        else if(section=="Personas") {
            val contacts=network.filter {contact->
                (!networkActions || contact.id in favorites.orEmpty()) &&
                personVisibleName(contact,profiles.orEmpty().firstOrNull {it.personId==contact.id}).contains(networkSearch.trim(),true) &&
                (networkCategory.isEmpty() || relations.orEmpty().any {it.category==networkCategory && (it.sourceId==contact.id || it.targetId==contact.id)})}.sortedBy {it.id !in favorites.orEmpty()}
            Column(Modifier.fillMaxSize()) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(networkCategory.isEmpty(),{networkCategory=""},label={Text("Todas")})
                    PersonCategory.entries.filter {c->relations.orEmpty().any {it.category==c.name}}.forEach {c->FilterChip(networkCategory==c.name,{networkCategory=c.name},label={Text(c.label)})}
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("person-network-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(contacts.isEmpty()) item {Text("Sin otras personas.")}
                    items(contacts,key={it.id}) {contact->
                        Card(Modifier.fillMaxWidth().testTag("network-contact:${contact.id}").clickable {path=path+contact.id;section="Inicio"},shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),colors=CardDefaults.cardColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                PersonPhoto(contact.copy(name=personVisibleName(contact,profiles.orEmpty().firstOrNull {it.personId==contact.id})),Modifier.size(64.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),framed=true)
                                Column(Modifier.weight(1f)) {
                                    Text(personVisibleName(contact,profiles.orEmpty().firstOrNull {it.personId==contact.id}))
                                    relations.orEmpty().filter {(it.sourceId==id && it.targetId==contact.id) || (it.targetId==id && it.sourceId==contact.id)}
                                        .forEach {Text(it.presentation(id),style=MaterialTheme.typography.bodySmall)}
                                }
                                PersonFavoriteButton(contact.id,favorites,repository)

                            }
                        }
                    }
                }
                OutlinedTextField(networkSearch,{networkSearch=it},label={Text("Buscar Personas")},singleLine=true,modifier=Modifier.fillMaxWidth())
            }
        }
        }
        if(section=="Inicio" && editingBanner) PersonBannerEditor(id,banners.firstOrNull {it.personId==id},bannerFiles,
        memoryImages.filter {image->publications.orEmpty().any {it.id==image.memoryId && it.personId==id}}.mapNotNull {it.attachmentId},legacyFiles=photos.filter {it.personId==id}.map {it.file}+listOfNotNull(person?.avatarFile)) {editingBanner=false}
        if(section=="Inicio" && !editingInfo && !editingBanner) key(id) {PersonProfileModule(person,people,identity?.personId,repository,onDeletePerson?.takeIf {identity?.personId!=id}?.let {remove->{remove(person)}},onBanner={editingBanner=true},onGuest=if(com.r0ybt.arachn0de.guest.GuestCaptureStart.VERIFIED && app.security.current.value?.primary==true) ({perform {com.r0ybt.arachn0de.guest.GuestCaptureStart.begin(app,id)}}) else null,onInfo={editingInfo=true},displayName=personVisibleName(person,profile))}
        editingSection?.let {sectionDrafts.SaveableStateProvider("$id:Información") {
            TaskBottomPanel(enabled=true,panelKey="$id:information",title="Información",editRequest=null,ownsMainActions=true) {
                PersonSectionEditor(person,profile,"Información",birthdays.orEmpty().firstOrNull {it.personId==id}?.date.orEmpty(),busy,sectionRequest,{editingInfo=false}) {index,value,done->perform {
                    val updated=if(index==2) profile else updatePersonSectionField(profile,"Información",index,value)
                    require(repository.save(id,if(index==0) value else person.name,person.avatarFile,isNew=false,
                        zoom=person.avatarZoom,x=person.avatarX,y=person.avatarY,profile=updated,birthday=if(index==2) value else null)) {"La Persona ya no existe."}
                    done()
                }}
            }
        }}
        if(section=="Personas") {
            if(adding || editingRelation!=null) key(id,editingRelation?.id) {PersonRelationEditor(id,people.map {it.copy(name=personVisibleName(it,profiles.orEmpty().firstOrNull {p->p.personId==it.id}))},editingRelation,
                {if(!busy) {adding=false;editingRelation=null}}) {relation,isNew->perform {repository.saveRelation(relation,isNew);adding=false;editingRelation=null}}}
            else ModularPanelSurface(Modifier.fillMaxWidth()) {
                ModularPanelLayout(compact=true,outlinedBody=false,headerDivider=false,showActions=false,
                    header={Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text(relationAction ?: "Relaciones",Modifier.weight(1f))
                        if(relationAction!=null) IconButton(onClick={relationAction=null}) {LucideIcon(Icons.Default.ArrowBack,"Volver a Relaciones",tint=androidx.compose.material3.LocalContentColor.current)}
                    }},actions={}) {
                    BackHandler(enabled=relationAction!=null) {relationAction=null}
                    val related=relations.orEmpty().filter {it.sourceId==id || it.targetId==id}
                    if(networkActions) {
                        TextButton(onClick={networkActions=false}) {Text("Volver a Personas")}
                        Text("Favoritos",style=MaterialTheme.typography.titleSmall)

                    } else if(relationAction==null) ModularFieldMenu(listOfNotNull(
                        ModularPanelOption("Añadir relación",Icons.Default.Add,"person-add-relation",!busy && people.size>1) {adding=true},
                        ModularPanelOption("Editar vínculo",Icons.Default.Edit,"person-edit-relation",!busy && related.isNotEmpty()) {relationAction="Editar vínculo"},
                        ModularPanelOption("Eliminar vínculo",Icons.Default.Delete,"person-delete-relation",!busy && related.isNotEmpty()) {relationAction="Eliminar vínculo"},
                        ModularPanelOption("Favoritos",Icons.Default.StarBorder,"person-network-favorites",!busy) {networkActions=true},
                        onNewPerson?.let {ModularPanelOption("Nueva Persona",Icons.Default.PersonAdd,"person-network-add",!busy,it)}
                    ),compact=true)
                    else ModularFieldMenu(related.map {relation->
                        ModularPanelOption((people.firstOrNull {it.id==relation.other(id)}?.let {personVisibleName(it,profiles.orEmpty().firstOrNull {p->p.personId==it.id})} ?: "Persona")+" · "+relation.presentation(id),Icons.Default.Groups,"person-relation:${relation.id}",!busy) {
                            if(relationAction=="Editar vínculo") editingRelation=relation else deleting=relation
                            relationAction=null
                        }
                    },columns=1,compact=true)
                }
            }
        }

    }
    }
    if(person!=null) viewing?.let {selected->
        PersonPhotoViewer(person,photos.filter {it.personId==id},selected,busy,{viewing=it},
            {photo->perform {repository.setPhotoAsAvatar(id,photo.id)}},{viewing=null},repository)
    }
    deleting?.let {r->AlertDialog(onDismissRequest={if(!busy) deleting=null},title={Text("Eliminar vínculo")},text={Text("Solo se eliminará esta relación y su presentación inversa. Ambas Personas se conservarán.")},confirmButton={TextButton(enabled=!busy,onClick={perform {repository.deleteRelation(r.id);deleting=null}}) {Text("Eliminar")}},dismissButton={TextButton(enabled=!busy,onClick={deleting=null}) {Text("Cancelar")}})}
    error?.let {AlertDialog(onDismissRequest={error=null},title={Text("No se pudo guardar")},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}
/** Each field has one section and the same order in consultation and editing. */
internal val antisocialSections=listOf("Inicio","Personas","Proyectos")
internal fun antisocialSection(value:String)=when(value) {"Relaciones","Red","Personas"->"Personas";"Proyectos y trabajo","Proyectos"->"Proyectos";"Tecnologías"->"Inicio";else->"Inicio"}
internal fun personVisibleName(person:Person,profile:PersonProfileEntity?)=profile?.givenName?.takeIf {it.isNotBlank()} ?: person.name
internal fun profileConsultationFields(p:PersonProfileEntity,section:String)=profileFields(p,section).filter {it.second.isNotBlank()}
internal fun profileFields(p:PersonProfileEntity,section:String)=listOf("Nombre" to p.givenName,"Biografía" to p.description,"Cumpleaños" to "","Teléfono" to p.phones,"Correo electrónico" to p.emails,"Enlaces" to p.links)
internal fun updatePersonSectionField(p:PersonProfileEntity,section:String,index:Int,value:String):PersonProfileEntity=when(index) {
    0->p.copy(givenName=value);1->p.copy(description=value);3->p.copy(phones=value);4->p.copy(emails=value);5->p.copy(links=value);else->error("Campo desconocido")
}
@Composable private fun PersonContactRows(profile:PersonProfileEntity,birthday:String?) {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val fields=listOf("Teléfono" to profile.phones,"Correo electrónico" to profile.emails)
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {fields.filter {it.second.isNotBlank()}.forEach {(label,value)->Text("$label: $value",style=MaterialTheme.typography.bodySmall)}}
}
@Composable private fun PersonSectionEditor(person:Person,p:PersonProfileEntity,section:String,birthday:String,busy:Boolean,menuRequest:Int,onClose:()->Unit,
    onSave:(Int,String,()->Unit)->Unit) {
    val fields=profileFields(p.copy(givenName=personVisibleName(person,p)),section).mapIndexed {index,pair->if(index==2) pair.first to birthday else pair}
    ModularTextFieldsEditor(editorKey="${p.personId}:information:$menuRequest",title="Información",labels=fields.map {it.first},showMenuTitle=true,
        initialValues=fields.map {it.second},saveLabel="",onCancel=onClose,inline=true,enabled=!busy,menuRequest=menuRequest,compactMenu=true,
        menuFooter={TextButton(onClick=onClose) {Text("Volver a Inicio")}},
        fieldIcon={index->antisocialFieldIcon(fields[index].first)},
        validField={index,value->when(index) {0->value.isNotBlank();1->value.length<=150 || value==p.description;2->PersonBirthDate.valid(value);else->true}},
        fieldEditor={index,value,change->if(index==2) BirthdayField(value,change,enabled=!busy) else ModularTextField(value,change,fields[index].first,!busy,singleLine=index==0)},onFieldSave=onSave,onSave={})
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
    ModularPanelDialog(inline=true,onDismissRequest=onCancel,compact=true,plainBody=true,fieldOwnsScroll=true,
        title={Text(if(original==null) "Añadir relación" else "Editar vínculo")},
        text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            BackHandler {if(stage==1 && original==null) stage=0 else onCancel()}
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
        confirmButton={if(stage==1) TextButton(enabled=target.isNotEmpty() && label.isNotBlank() && (mode!="DIRECTED" || inverse.isNotBlank()),onClick={val l=label.trim();val inv=when(mode) {"DIRECTED"->inverse.trim();"RECIPROCAL"->l;else->""};onSave(PersonRelationEntity(original?.id ?: UUID.randomUUID().toString(),source,target,category,l,inv,meaning,mode,relationKey(source,target,category,l,inv,mode)),original==null)}) {Text("Confirmar")}})
}

private fun antisocialFieldIcon(label:String)=when(label) {
    "Nombre"->Icons.Default.Person
    "Teléfono"->Icons.Default.Phone
    "Correo electrónico"->Icons.Default.Email
    "Enlaces"->Icons.Default.Link
    "Cumpleaños"->Icons.Default.DateRange
    else->Icons.Default.Subject
}
