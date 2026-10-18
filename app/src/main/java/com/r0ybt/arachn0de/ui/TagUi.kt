package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.data.repository.TagRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.launch

@Composable internal fun TagChips(tags: List<Tag>, compact: Boolean = false) {
    if (tags.isEmpty()) return
    if (!compact) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tags.take(2).forEach { SuggestionChip(onClick = {}, label = { Text(it.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 100.dp)) }) }
            if (tags.size > 2) SuggestionChip(onClick = {}, label = { Text("+${tags.size - 2}") })
        }
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        tags.take(2).forEach { tag ->
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), color = Arachn0deColors.ControlSurface) {
                Text(tag.name, fontSize = 12.sp, color = Arachn0deColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp).widthIn(max = 100.dp))
            }
        }
        if (tags.size > 2) Text("+${tags.size - 2}", fontSize = 12.sp, color = Arachn0deColors.TextSecondary)
    }
}
@Composable internal fun TagSelector(tags: List<Tag>, selected: Set<String>, onChange: (Set<String>) -> Unit,
    single: Boolean = false, repository: TagRepository? = null, enabled: Boolean = true, iconOnly:Boolean=false, singleAssignment:Boolean=!single) {
    var open by rememberSaveable { mutableStateOf(false) }
    if(iconOnly) IconButton(enabled=enabled,onClick={open=true},modifier=Modifier.size(48.dp)) {
        LucideIcon(Icons.Default.Label,"Etiquetas: "+tags.filter {it.id in selected}.joinToString {it.name}.ifEmpty {"Ninguna"},tint=if(selected.isEmpty()) Arachn0deColors.TextSecondary else Arachn0deColors.TextPrimary)
    } else OutlinedButton(enabled=enabled,onClick={open=true}) {
        Text("Etiquetas: "+tags.filter {it.id in selected}.let {picked->
            picked.take(2).joinToString {it.name}+(if(picked.size>2) " +${picked.size-2}" else "")
        }.ifEmpty {if(single) "Todas" else "Ninguna"},maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    if(open) TagPanelDialog(onClose={open=false}) {busy->
        TagPanel(TagState(tags),repository,selected,onChange,enabled=enabled,single=single,singleAssignment=singleAssignment,
            busyState=busy,onClose={open=false})
    }
}

/** The same shell is used for assignment and catalog administration; editors keep their own drafts. */
@Composable private fun TagPanelDialog(onClose:()->Unit,content:@Composable (MutableState<Boolean>)->Unit) {
    val busy=remember {mutableStateOf(false)}
    BottomModulesDialog(onDismissRequest={if(!busy.value) onClose()},
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max=maxHeight*.85f)) {content(busy)}
        }
    }
}

@Composable internal fun TagManager(repository:TagRepository,state:TagState,showAccess:Boolean=true,initiallyOpen:Boolean=false,onClose:()->Unit={}) {
    var open by rememberSaveable {mutableStateOf(initiallyOpen)}
    fun close() {open=false;onClose()}
    if(showAccess) TextButton(onClick={open=true}) {Text("Gestionar etiquetas")}
    if(open) TagPanelDialog(::close) {busy->
        TagPanel(state,repository,manage=true,busyState=busy,onClose=::close)
    }
}

private enum class TagPanelMode { BROWSE, SEARCH, NEW, EDIT, DELETE }

/** Only explicit chip/removal actions replace legacy associations; opening and closing never normalize them. */
@Composable internal fun TagPanel(initial:TagState,repository:TagRepository?,selected:Set<String> = emptySet(),
    onChange:(Set<String>)->Unit={},manage:Boolean=false,enabled:Boolean=true,single:Boolean=false,singleAssignment:Boolean=true,
    modifier:Modifier=Modifier,busyState:MutableState<Boolean>?=null,onCancel:(()->Unit)?=null,pendingSelection:Boolean=false,doneLabel:String="Listo",onClose:()->Unit) {
    val catalog=if(repository!=null) {
        val value by remember(repository) {repository.observe()}.collectAsState(initial=initial)
        value
    } else initial
    var modeName by rememberSaveable {mutableStateOf(TagPanelMode.BROWSE.name)}
    val mode=TagPanelMode.valueOf(modeName)
    var selection by rememberSaveable {mutableStateOf<String?>(null)}
    val tag=catalog.tags.firstOrNull {it.id==selection}
    var search by rememberSaveable {mutableStateOf("")}
    var name by rememberSaveable {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    val localBusy=busyState ?: remember {mutableStateOf(false)}
    var busy by localBusy
    val ready=enabled && !busy
    val scope=rememberCoroutineScope()
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(mode) {
        if(mode==TagPanelMode.BROWSE) {focus.clearFocus();keyboard?.hide()}
    }
    fun browse() {modeName=TagPanelMode.BROWSE.name;error=null}
    fun back() {
        if(!ready) return
        when {
            mode!=TagPanelMode.BROWSE->browse()
            manage && selection!=null->selection=null
            else->(onCancel ?: onClose)()
        }
    }
    androidx.activity.compose.BackHandler {back()}
    fun save() {
        val repo=repository ?: return
        if(!ready) return
        busy=true
        scope.launch {
            try {
                when(mode) {
                    TagPanelMode.NEW->{
                        val created=repo.create(name)
                        search=""
                        if(manage) selection=created.id
                        else onChange(if(single || singleAssignment) setOf(created.id) else selected+created.id)
                    }
                    TagPanelMode.EDIT->repo.rename(requireNotNull(tag).id,name)
                    TagPanelMode.DELETE->{repo.delete(requireNotNull(tag).id);selection=null}
                    else->return@launch
                }
                name="";browse()
            } catch(c:kotlinx.coroutines.CancellationException) {throw c}
            catch(e:Exception) {error=e.message ?: "No se pudo completar la operación."}
            finally {busy=false}
        }
    }
    val palette=com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette.current
    CompositionLocalProvider(com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette provides
        palette.copy(surface=palette.taskPanel,panel=palette.taskPanel)) {
        ModularPanelSurface(modifier.fillMaxWidth().testTag("tag-panel"),shadowElevation=0.dp) {
            ModularPanelLayout(fieldOwnsScroll=true,outlinedBody=false,headerDivider=false,compact=true,
                header={Text("Etiquetas",style=MaterialTheme.typography.titleMedium)},
                body={
                    if(!manage && singleAssignment && selected.size>1) Text(
                        "Este elemento conserva ${selected.size} etiquetas heredadas. Elige una para reemplazarlas o quítalas explícitamente. Cerrar no cambia las asociaciones.",
                        style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,modifier=Modifier.testTag("legacy-tags"))
                    if(manage && tag!=null) Text("Seleccionada: ${tag.name}",style=MaterialTheme.typography.bodySmall)
                    val query=search.trim().replace(Regex("\\s+")," ").lowercase(java.util.Locale.ROOT)
                    val visible=catalog.tags.filter {it.normalizedName.contains(query)}
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if(visible.isEmpty()) Text(if(query.isEmpty()) "Aún no hay etiquetas." else "Sin coincidencias.",style=MaterialTheme.typography.bodySmall)
                        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            visible.forEach {row->
                                val chosen=if(manage) selection==row.id else row.id in selected
                                TextButton(onClick={
                                    if(manage) {selection=if(selection==row.id) null else row.id;browse()}
                                    else {onChange(if(single || singleAssignment) setOf(row.id) else if(chosen) selected-row.id else selected+row.id);browse()}
                                },enabled=ready && mode in setOf(TagPanelMode.BROWSE,TagPanelMode.SEARCH),
                                    modifier=Modifier.heightIn(min=48.dp).testTag("tag-choice:${row.id}").semantics {this.selected=chosen},
                                    contentPadding=PaddingValues(horizontal=8.dp,vertical=2.dp),
                                    shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    colors=ButtonDefaults.textButtonColors(containerColor=if(chosen) Arachn0deColors.ControlSurface else Arachn0deColors.Surface,
                                        contentColor=if(chosen) Arachn0deColors.TextPrimary else Arachn0deColors.TextSecondary)) {
                                    Column {Text(row.name)
                                        if(manage) Text("${catalog.usage(row.id)} usos",style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                },actions={Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    error?.let {Text(it,color=Arachn0deColors.Destructive,style=MaterialTheme.typography.bodySmall)}
                    when(mode) {
                        TagPanelMode.NEW,TagPanelMode.EDIT->{
                            key(mode) {ModularTextField(name,{name=it},"Nombre de etiqueta",ready,singleLine=true)}
                            ModularPanelActions(cancel={PrimaryControlButton(enabled=ready,onClick={browse()}) {Text("Cancelar")}},
                                confirm={PrimaryControlButton(enabled=ready && name.isNotBlank(),onClick={save()}) {Text("Guardar")}})
                        }
                        TagPanelMode.DELETE->{
                            Text("¿Eliminar «${tag?.name.orEmpty()}»? Se quitará de ${tag?.let {catalog.usage(it.id)} ?: 0} elementos y plantillas. Los elementos se conservan.",style=MaterialTheme.typography.bodyMedium)
                            ModularPanelActions(cancel={PrimaryControlButton(enabled=ready,onClick={browse()}) {Text("Cancelar")}},
                                confirm={PrimaryControlButton(enabled=ready && tag!=null,onClick={save()}) {Text("Eliminar")}})
                        }
                        TagPanelMode.SEARCH->{
                            ModularTextField(search,{search=it},"Buscar etiquetas",ready,singleLine=true)
                            ModularPanelActions(cancel={PrimaryControlButton(enabled=ready,onClick={search="";browse()}) {Text("Limpiar")}},
                                confirm={PrimaryControlButton(enabled=ready,onClick={browse()}) {Text("Listo")}})
                        }
                        TagPanelMode.BROWSE->{
                            val options=if(manage && tag!=null) listOf(
                                ModularPanelOption("Editar",Icons.Default.Edit,"tag-edit",ready) {name=tag.name;modeName=TagPanelMode.EDIT.name},
                                ModularPanelOption("Eliminar",Icons.Default.Delete,"tag-delete",ready) {modeName=TagPanelMode.DELETE.name})
                            else buildList {
                                add(ModularPanelOption("Buscar",Icons.Default.Search,"tag-search",ready) {modeName=TagPanelMode.SEARCH.name})
                                if(repository!=null) add(ModularPanelOption("Nueva",Icons.Default.Add,"tag-new",ready) {name="";modeName=TagPanelMode.NEW.name})
                            }
                            ModularFieldMenu(options,columns=options.size,compact=true)
                            if(manage && tag!=null) PrimaryControlButton(enabled=ready,onClick={selection=null},modifier=Modifier.fillMaxWidth()) {Text("Cancelar selección")}
                            if(onCancel!=null && selected.isNotEmpty()) TextButton(enabled=ready,onClick={onChange(emptySet())}) {Text("Quitar etiqueta")}
                            ModularPanelActions(cancel={
                                if(onCancel!=null && pendingSelection) PrimaryControlButton(enabled=ready,onClick=onCancel) {Text("Cancelar")}
                                else if(!manage && onCancel==null) PrimaryControlButton(enabled=ready && selected.isNotEmpty(),onClick={onChange(emptySet())}) {
                                    Text(if(single) "Todas las etiquetas" else if(singleAssignment) "Quitar etiqueta" else "Quitar todas")
                                }
                            },confirm={PrimaryControlButton(enabled=ready,onClick={focus.clearFocus();keyboard?.hide();onClose()}) {Text(if(manage) "Cerrar" else doneLabel)}})
                        }
                    }
                }})
        }
    }
}

@Composable internal fun CardTagBadge(tags:List<Tag>,modifier:Modifier=Modifier) {
    if(tags.isEmpty()) return
    if(tags.size>1) {TagChips(tags,compact=true);return}
    Surface(modifier.widthIn(max=96.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=0.dp,topEnd=8.dp,bottomEnd=0.dp,bottomStart=6.dp),color=Arachn0deColors.Selection) {
        Row(Modifier.padding(horizontal=6.dp,vertical=3.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            LucideIcon(androidx.compose.material.icons.Icons.Default.Label,null,Modifier.size(14.dp),tint=Arachn0deColors.TextPrimary)
            Text(tags.single().name,fontSize=12.sp,color=Arachn0deColors.TextPrimary,
                maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}


// The opened element shares its action row with a bounded, single-line label.
@Composable internal fun InteriorTagBadge(tags:List<Tag>,modifier:Modifier=Modifier) {
    if(tags.isEmpty()) return
    Surface(modifier,shape=androidx.compose.foundation.shape.RoundedCornerShape(6.dp),color=Arachn0deColors.ControlSurface) {
        Text(tags.joinToString(" · ") {it.name},fontSize=12.sp,color=Arachn0deColors.TextSecondary,
            maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier=Modifier.padding(horizontal=6.dp,vertical=3.dp))
    }
}
