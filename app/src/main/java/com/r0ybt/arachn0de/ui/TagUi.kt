package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Label
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TagSelector(tags: List<Tag>, selected: Set<String>, onChange: (Set<String>) -> Unit,
    single: Boolean = false, repository: TagRepository? = null, enabled: Boolean = true, iconOnly:Boolean=false, singleAssignment:Boolean=!single) {
    var open by rememberSaveable { mutableStateOf(false) }
    val pickerSearch=rememberSaveable {mutableStateOf("")}
    val pickerError=remember {mutableStateOf<String?>(null)}
    val pickerScope=rememberCoroutineScope()
    if(iconOnly) IconButton(enabled=enabled,onClick={open=true},modifier=Modifier.size(48.dp)) {
        LucideIcon(androidx.compose.material.icons.Icons.Default.Label,"Etiquetas: "+tags.filter {it.id in selected}.joinToString {it.name}.ifEmpty {"Ninguna"},tint=if(selected.isEmpty()) Arachn0deColors.TextSecondary else Arachn0deColors.TextPrimary)
    } else
    OutlinedButton(enabled = enabled, onClick = { open = true }) { Text("Etiquetas: " + (tags.filter { it.id in selected }.let { picked -> picked.take(2).joinToString { it.name } + if (picked.size > 2) " +${picked.size - 2}" else "" }.ifEmpty { if (single) "Todas" else "Ninguna" }), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
    if (open) ModalBottomSheet(onDismissRequest={open=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Arachn0deColors.Surface,contentColor=Arachn0deColors.TextPrimary,tonalElevation=0.dp) {
        ModularPanelSurface {
            ModularPanelLayout(fieldOwnsScroll=true,header={Text("Etiquetas",style=MaterialTheme.typography.titleLarge)},
                actions={ModularPanelActions(cancel={
                    TextButton(onClick={onChange(emptySet())}) {Text(if(singleAssignment) "Quitar etiqueta" else if(single) "Todas las etiquetas" else "Quitar todas")}
                },confirm={FilledTonalButton(onClick={open=false},shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {Text("Listo")}
                })}) {
                TagPickerBody(tags,selected,onChange,single,repository,enabled,singleAssignment,pickerSearch,pickerError,pickerScope)

            }
        }
    }

}
@Composable internal fun ColumnScope.TagPickerBody(tags:List<Tag>,selected:Set<String>,onChange:(Set<String>)->Unit,
    single:Boolean=false,repository:TagRepository?=null,enabled:Boolean=true,singleAssignment:Boolean=true,
    searchState:MutableState<String>?=null,errorState:MutableState<String?>?=null,pickerScope:kotlinx.coroutines.CoroutineScope?=null) {
    val localSearch:MutableState<String> = searchState ?: rememberSaveable { mutableStateOf("") }
    val localError:MutableState<String?> = errorState ?: remember { mutableStateOf<String?>(null) }
    var search by localSearch
    var error by localError
    val scope = pickerScope ?: rememberCoroutineScope()
                OutlinedTextField(search, { search = it }, label = { Text("Buscar o crear etiqueta") }, singleLine = true,modifier=modularPanelFieldModifier())
                LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false)) {
                    items(tags.filter { it.normalizedName.contains(search.trim().lowercase(java.util.Locale.ROOT)) }, key = { it.id }) { tag ->
                        TextButton(onClick = { onChange(if (singleAssignment) (if(selected==setOf(tag.id)) emptySet() else setOf(tag.id)) else if (single) setOf(tag.id) else if (tag.id in selected) selected - tag.id else selected + tag.id) },
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp),
                            colors=ButtonDefaults.textButtonColors(containerColor=if(tag.id in selected) Arachn0deColors.ControlSurface else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor=if(tag.id in selected) Arachn0deColors.TextPrimary else Arachn0deColors.TextSecondary)) {
                            Text((if (tag.id in selected) "✓ " else "") + tag.name,Modifier.fillMaxWidth())
                        }
                    }
                }
                if (repository != null && search.isNotBlank()) TextButton(onClick = { scope.launch {
                    runCatching { repository.create(search) }.onSuccess { onChange(if (single || singleAssignment) setOf(it.id) else selected + it.id); search = "" }
                        .onFailure { error = it.message }
                } }) { Text("Crear y seleccionar") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
@Composable internal fun TagManager(repository: TagRepository, state: TagState,showAccess:Boolean=true,initiallyOpen:Boolean=false,onClose:()->Unit={}) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    var search by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if(showAccess) TextButton(onClick = { open = true }) { Text("Gestionar etiquetas") }
    if (open) AlertDialog(onDismissRequest = { open = false;onClose() }, title = { Text("Etiquetas globales") }, text = { Column {
        OutlinedTextField(search, { search = it }, label = { Text("Buscar") }, singleLine = true)
        LazyColumn(Modifier.heightIn(max = 320.dp)) { items(state.tags.filter { it.name.contains(search, true) }, key = { it.id }) { tag ->
            Column { Text("${tag.name} · ${state.usage(tag.id)} usos")
                Row { TextButton(onClick = { editing = tag; name = tag.name }) { Text("Renombrar") }
                    TextButton(onClick = { deleting = tag }) { Text("Eliminar") } }
            }
        } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { open = false;onClose() }) { Text("Cerrar") } })
    editing?.let { tag -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("Renombrar etiqueta") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { scope.launch {
            runCatching { repository.rename(tag.id, name) }.onSuccess { editing = null; error = null }.onFailure { error = it.message; editing = null }
        } }) { Text("Guardar") } }) }
    deleting?.let { tag -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminar etiqueta") },
        text = { Text("${tag.name}: se quitará de ${state.usage(tag.id)} elementos y plantillas. Los elementos se conservan.") },
        confirmButton = { TextButton(onClick = { scope.launch { runCatching { repository.delete(tag.id) }.onFailure { error = it.message }; deleting = null } }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
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
