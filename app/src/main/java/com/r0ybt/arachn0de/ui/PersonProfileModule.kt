package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.state.PersonActions
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Existing profile data and image lifecycle, presented one operation at a time. */
@Composable internal fun PersonProfileModule(person:Person,people:List<Person>,selfId:String?,repository:PersonRepository,onDelete:(()->Unit)?=null) {
    val scope=rememberCoroutineScope()
    val actions=remember(repository,scope) {PersonActions(repository,scope)}
    var stage by rememberSaveable(person.id) {mutableStateOf("menu")}
    val draft=rememberSaveable(person.id) {UUID.randomUUID().toString()}
    var avatar by rememberSaveable(person.id) {mutableStateOf(person.avatarFile)}
    var frame by rememberSaveable(person.id) {mutableStateOf(listOf(person.avatarZoom,person.avatarX,person.avatarY))}
    var held by rememberSaveable(person.id) {mutableStateOf(listOfNotNull(person.avatarFile))}
    var pending by rememberSaveable(person.id) {mutableStateOf<String?>(null)}
    var cropping by rememberSaveable(person.id) {mutableStateOf(false)}
    var replacingSelf by rememberSaveable(person.id) {mutableStateOf(false)}
    var pickerOpen by rememberSaveable(person.id) {mutableStateOf(false)}
    val busy=actions.operation.busy || pickerOpen
    val own=selfId==person.id
    fun framing()=AvatarFraming(frame[0],frame[1],frame[2])
    LaunchedEffect(person.id) {held.forEach {repository.retainAvatar(it,draft)}}
    LaunchedEffect(stage,person.avatarFile,person.avatarZoom,person.avatarX,person.avatarY) {
        if(!cropping && pending==null) {avatar=person.avatarFile;frame=listOf(person.avatarZoom,person.avatarX,person.avatarY)}
    }
    DisposableEffect(person.id,draft) {onDispose {
        val reserved=held.toList()
        scope.launch(NonCancellable) {reserved.forEach {repository.discardAvatar(it,draft)}}
    }}
    fun savePhoto(file:String?,newFrame:AvatarFraming) {
        actions.save(person.id,person.name,file,false,newFrame) {
            avatar=file;frame=listOf(newFrame.zoom,newFrame.x,newFrame.y);pending=null;cropping=false
            scope.launch {withContext(NonCancellable) {
                held.filter {it!=file}.forEach {repository.discardAvatar(it,draft)}
                held=listOfNotNull(file)
                file?.let {repository.retainAvatar(it,draft)}
            }}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->
        pickerOpen=false
        if(uri!=null) actions.import(uri) {file->
            held=(held+file).distinct();pending=file;cropping=true
            scope.launch {repository.retainAvatar(file,draft)}
        }
    }
    fun pickPhoto() {
        if(pickerOpen || cropping || actions.operation.busy) return
        pickerOpen=true
        try {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
        catch(e:Exception) {pickerOpen=false;throw e}
    }
    fun identify() {actions.save(person.id,person.name,person.avatarFile,false,AvatarFraming(person.avatarZoom,person.avatarX,person.avatarY),identity=!own) {replacingSelf=false}}
    BackHandler(enabled=stage!="menu") {if(!busy) stage="menu"}
    TaskBottomPanel(enabled=true,panelKey=person.id,title="Inicio",editRequest=null,accessibilityLabel="Antisocial") {
    BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
    Box(Modifier.fillMaxWidth().heightIn(max=maxHeight*.65f).testTag("profile-inline-panel")) {
    ModularPanelDialog(inline=true,onDismissRequest={if(!busy) stage="menu"},compact=true,plainBody=true,showActions=false,
        title={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(stage=="photo") "Foto" else "Inicio",Modifier.weight(1f))
            if(stage!="menu") IconButton(enabled=!busy,onClick={stage="menu"}) {Icon(Icons.Default.ArrowBack,"Volver a Inicio")}
        }},text={
            if(stage=="menu") Column {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(enabled=!busy,onClick={stage="photo"},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("profile-photo")) {
                        Icon(Icons.Default.Image,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Foto")
                    }
                    TextButton(enabled=!busy,onClick={if(!own && selfId!=null) replacingSelf=true else identify()},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("profile-self").semantics {
                            selected=own;contentDescription="Soy yo";stateDescription=if(own) "Activado" else "Desactivado"
                        },colors=ButtonDefaults.textButtonColors(
                            containerColor=if(own) com.r0ybt.arachn0de.ui.theme.Arachn0deColors.ControlSurface else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor=if(own) com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary else com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextSecondary)) {
                        Icon(if(own) Icons.Default.Person else Icons.Default.PersonOutline,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Soy yo")
                    }
                }
                onDelete?.let {remove->
                    HorizontalDivider()
                    Text("Persona",style=MaterialTheme.typography.labelSmall)
                    ModularFieldMenu(listOf(ModularPanelOption("Eliminar perfil",Icons.Default.Delete,"person-delete-profile",!busy,remove)),compact=true)
                }
            } else Column {
                PersonPhoto(Person(person.id,person.name,avatar,frame[0],frame[1],frame[2]),Modifier.size(80.dp),framed=true)
                ModularFieldMenu(listOf(
                    ModularPanelOption("Cambiar fotografía",Icons.Default.Image,"profile-change-photo",!busy) {pickPhoto()},
                    ModularPanelOption("Ajustar encuadre",Icons.Default.Crop,"profile-frame",!busy && avatar!=null) {cropping=true},
                    ModularPanelOption("Quitar avatar",Icons.Default.HideImage,"profile-remove-avatar",!busy && avatar!=null) {savePhoto(null,AvatarFraming())}
                ),compact=true)
            }
        },confirmButton={})
    }
    }
    }
    if(cropping) (pending ?: avatar)?.let {file->AvatarEditor(file,framing(),enabled=!busy,
        shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),onConfirm={savePhoto(file,it)},onCancel={if(!busy) {
            val discarded=pending;pending=null;cropping=false;avatar=person.avatarFile;frame=listOf(person.avatarZoom,person.avatarX,person.avatarY)
            discarded?.let {scope.launch {withContext(NonCancellable) {repository.discardAvatar(it,draft);held=held-it}}}
        }})}
    if(replacingSelf) AlertDialog(onDismissRequest={if(!busy) replacingSelf=false},title={Text("Cambiar mi perfil")},
        text={Text("${people.firstOrNull {it.id==selfId}?.name ?: "El perfil actual"} dejará de estar marcado como propio. ${person.name} será el único perfil marcado como «Soy yo».")},
        confirmButton={TextButton(enabled=!busy,onClick=::identify) {Text("Confirmar")}},dismissButton={TextButton(enabled=!busy,onClick={replacingSelf=false}) {Text("Cancelar")}})
    OperationErrorDialog(actions.operation)
}
