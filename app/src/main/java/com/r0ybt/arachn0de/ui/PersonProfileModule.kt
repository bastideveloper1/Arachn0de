package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

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
@Composable internal fun PersonProfileModule(person:Person,people:List<Person>,selfId:String?,repository:PersonRepository,onDelete:(()->Unit)?=null,onBanner:(()->Unit)?=null,onGuest:(()->Unit)?=null,onInfo:()->Unit={},displayName:String=person.name) {
    val scope=rememberCoroutineScope()
    val actions=remember(repository,scope) {PersonActions(repository,scope)}
    var stage by rememberSaveable(person.id) {mutableStateOf("menu")}
    val draft=rememberSaveable(person.id) {UUID.randomUUID().toString()}
    var avatar by rememberSaveable(person.id) {mutableStateOf(person.avatarFile)}
    var frame by rememberSaveable(person.id) {mutableStateOf(listOf(person.avatarZoom,person.avatarX,person.avatarY))}
    var held by rememberSaveable(person.id) {mutableStateOf(listOfNotNull(person.avatarFile))}
    var pending by rememberSaveable(person.id) {mutableStateOf<String?>(null)}
    var cropping by rememberSaveable(person.id) {mutableStateOf(false)}
    var pickerOpen by rememberSaveable(person.id) {mutableStateOf(false)}
    val busy=actions.operation.busy || pickerOpen
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
    val camera=rememberPrivatePhotoCamera(!busy,{actions.operation.report(it)}) {photo,done->
        actions.import(android.net.Uri.EMPTY,onResult=done,sourceFile=photo.file) {file->
            held=(held+file).distinct()
            scope.launch {repository.retainAvatar(file,draft);savePhoto(file,AvatarFraming())}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->
        pickerOpen=false
        if(uri!=null) actions.import(uri) {file->
            held=(held+file).distinct()
            scope.launch {repository.retainAvatar(file,draft);savePhoto(file,AvatarFraming())}
        }
    }
    fun pickPhoto() {
        if(pickerOpen || cropping || actions.operation.busy) return
        pickerOpen=true
        try {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
        catch(e:Exception) {pickerOpen=false;throw e}
    }
    BackHandler(enabled=stage!="menu") {if(!busy) stage="menu"}
    val threshold=with(androidx.compose.ui.platform.LocalDensity.current) {24.dp.toPx()}
    val gesture=Modifier.pointerInput(stage,busy) {
        var distance=0f
        detectVerticalDragGestures(onDragStart={distance=0f},onDragCancel={distance=0f},onDragEnd={
            if(!busy) {if(distance>threshold) stage="menu" else if(distance < -threshold) stage="photo"}
        },onVerticalDrag={change,amount->change.consume();distance+=amount})
    }
    TaskBottomPanel(enabled=true,panelKey=person.id,title="Inicio",editRequest=null,accessibilityLabel="Antisocial",ownsMainActions=true) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().heightIn(max=maxHeight*.65f).testTag("profile-inline-panel")) {
                if(stage=="photo") Column(Modifier.weight(1f,fill=false)) {
                    Box(Modifier.fillMaxWidth().height(24.dp).then(gesture).clickable(enabled=!busy,onClickLabel="Contraer opciones de Antisocial") {stage="menu"},contentAlignment=androidx.compose.ui.Alignment.BottomCenter) {
                        Box(Modifier.padding(bottom=2.dp).size(36.dp,4.dp).background(com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextSecondary.copy(alpha=.65f),androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                    }
                    ModularPanelDialog(inline=true,onDismissRequest={if(!busy) stage="menu"},compact=true,plainBody=true,showActions=false,
                        title={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("Foto",Modifier.weight(1f))
                            IconButton(enabled=!busy,onClick={stage="menu"}) {Icon(Icons.Default.ArrowBack,"Volver a Inicio")}
                        }},text={Column {
                            PersonPhoto(Person(person.id,displayName,avatar,frame[0],frame[1],frame[2]),Modifier.size(80.dp),framed=true)
                            ModularFieldMenu(listOf(
                                ModularPanelOption("Galería",Icons.Default.Image,"profile-change-photo",!busy) {pickPhoto()},
                                ModularPanelOption("Cámara",Icons.Default.CameraAlt,"profile-camera",!busy,camera),
                                ModularPanelOption("Ajustar encuadre",Icons.Default.Crop,"profile-frame",!busy && avatar!=null) {cropping=true},
                                ModularPanelOption("Quitar avatar",Icons.Default.HideImage,"profile-remove-avatar",!busy && avatar!=null) {savePhoto(null,AvatarFraming())}
                            ),compact=true)
                        }},confirmButton={})
                }
                ModularPanelSurface(Modifier.then(gesture)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)) {
                        AlternatingBottomBar {
                            val barActions=buildList {
                                add(BottomBarAction("Foto",Icons.Default.Image,!busy,"profile-photo") {stage=if(stage=="photo")"menu" else "photo"})
                                onBanner?.let {add(BottomBarAction("Banner",Icons.Default.Image,!busy,"profile-banner",run=it))}
                                add(BottomBarAction("Información",Icons.Default.Info,!busy,"profile-information",run=onInfo))
                            }
                            CompactBarActions(barActions,iconAbove=false,style=MaterialTheme.typography.labelLarge) {a->
                                TextButton(enabled=a.enabled,onClick=a.run,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag(a.tag)) {
                                    Icon(a.icon,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(a.label)
                                }
                            }
                        }
                        onGuest?.let {guest->
                            ModularFieldMenu(listOf(ModularPanelOption("Captura de invitado",Icons.Default.CameraAlt,"profile-guest",!busy,guest)),compact=true)
                        }
                        onDelete?.let {remove->
                            HorizontalDivider()
                            Text("Persona",style=MaterialTheme.typography.labelSmall)
                            ModularFieldMenu(listOf(ModularPanelOption("Eliminar perfil",Icons.Default.Delete,"person-delete-profile",!busy,remove)),compact=true)
                        }
                    }
                }
            }
        }
    }
    if(cropping) (pending ?: avatar)?.let {file->AvatarEditor(file,framing(),enabled=!busy,
        shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),onConfirm={savePhoto(file,it)},onCancel={if(!busy) {
            val discarded=pending;pending=null;cropping=false;avatar=person.avatarFile;frame=listOf(person.avatarZoom,person.avatarX,person.avatarY)
            discarded?.let {scope.launch {withContext(NonCancellable) {repository.discardAvatar(it,draft);held=held-it}}}
        }})}
    OperationErrorDialog(actions.operation)
}
