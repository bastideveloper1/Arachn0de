package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import com.r0ybt.arachn0de.data.repository.TaskStateConfiguration
import com.r0ybt.arachn0de.ui.state.OperationState

internal val LocalContainerProgress=staticCompositionLocalOf<Map<String,NodeProgress>> {emptyMap()}
@Composable internal fun VerificationMark(tint:Color= cardIndicatorColor("verify",true)) {
    Icon(painterResource(com.r0ybt.arachn0de.R.drawable.lucide_scan_eye),null,Modifier.size(22.dp),tint=tint)
}
@Composable internal fun ContainerWorkMark(progress:NodeProgress?) {
    if(progress==null || !progress.hasPending)return
    Box(Modifier.size(16.dp).testTag("work-summary:${progress.nodeId}"),contentAlignment=androidx.compose.ui.Alignment.Center) {
        Box(Modifier.size(6.dp).background(Arachn0deColors.Accent,CircleShape).semanticsWork("Tareas pendientes"))
    }
}

private fun Modifier.semanticsWork(label:String)=this.then(Modifier.semantics {contentDescription=label})

@Composable internal fun TaskStateSettingsItems(project:Project?=null,node:Node?=null,onClose:()->Unit) {
    val app=LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication ?: return
    val scope=rememberCoroutineScope();val op=remember {OperationState(scope)}
    val configuration=remember(app) {TaskStateConfiguration(app.database)}
    var confirmation by remember {mutableStateOf<TaskStateConfiguration.Preview?>(null)}
    val value=if(node!=null) node.taskStateCount else if(project?.verificationEnabled==true) 3 else 2
    (if(node==null) listOf(2,3) else listOf(null,2,3)).forEach {choice->
        DropdownMenuItem(text={Text(if(choice==null) "Heredar del contenedor" else "$choice estados")},
            modifier=Modifier.heightIn(min=48.dp).semantics {selected=value==choice},enabled=!op.busy,
            leadingIcon={if(value==choice) Icon(Icons.Default.Check,null,Modifier.size(20.dp)) else Spacer(Modifier.size(20.dp))},onClick={
                op.submit("No se pudo guardar la configuración.",{
                    val request=TaskStateConfiguration.Request(node?.projectId ?: requireNotNull(project).id,node?.id,choice)
                    val preview=configuration.preview(request)
                    if(preview.conversions.isEmpty()) configuration.apply(request,preview)
                    else {confirmation=preview;true}
                },{if(confirmation==null) onClose()})
            })
    }
    confirmation?.let {preview->
        AlertDialog(onDismissRequest={if(!op.busy) confirmation=null},title={Text("¿Cambiar a 2 estados?")},
            text={Text("${preview.conversions.size} ${if(preview.conversions.size==1) "tarea pasará" else "tareas pasarán"} de Por verificar a Pendiente. Este cambio quedará guardado y no se deshará al volver a 3 estados.")},
            confirmButton={TextButton(modifier=Modifier.testTag("task-state-conversion-confirm"),enabled=!op.busy,onClick={
                op.submit("El alcance pudo cambiar. Reabre el menú y revisa la conversión.",{configuration.apply(preview.request,preview)},{confirmation=null;onClose()})
            }) {Text("Confirmar")}},
            dismissButton={TextButton(modifier=Modifier.testTag("task-state-conversion-cancel"),enabled=!op.busy,onClick={confirmation=null}) {Text("Cancelar")}})
    }
    OperationErrorDialog(op)
}

/** All three representations remain visible; entering verification still obeys effective mode. */
@Composable internal fun TaskStatusItems(node:Node,onChange:((TaskStatus,()->Unit)->Unit)?=null,onClose:()->Unit) {
    val app=LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication ?: return
    val count by produceState<Int?>(null,node.id,node.taskStateCount) {value=app.nodeRepository.taskStateCount(node.id)}
    val scope=rememberCoroutineScope();val op=remember {OperationState(scope)}
    TaskStatus.entries.forEach {status->
        val allowed=count!=null && (status!=TaskStatus.VERIFY || count==3 || node.awaitingVerification)
        DropdownMenuItem(text={Text(status.label)},modifier=Modifier.heightIn(min=48.dp).semantics {selected=node.taskStatus==status},
            enabled=!op.busy && allowed,leadingIcon={
                when(status) {
                    TaskStatus.VERIFY->VerificationMark()
                    TaskStatus.COMPLETED->Icon(Icons.Default.CheckBox,null)
                    TaskStatus.PENDING->Icon(Icons.Default.CheckBoxOutlineBlank,null)
                }
            },trailingIcon={if(node.taskStatus==status) Icon(Icons.Default.Check,null)},onClick={
                if(onChange!=null)onChange(status,onClose) else op.submit("No se pudo cambiar el estado.",{app.nodeRepository.setTaskStatus(node.id,status)},onClose)
            })
    }
    OperationErrorDialog(op)
}
