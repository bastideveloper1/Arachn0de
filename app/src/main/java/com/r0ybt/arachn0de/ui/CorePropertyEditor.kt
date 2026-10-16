package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.TitleLimits

/** One property, using the shared Antisocial panel primitives; the screen owns its draft. */
@Composable
internal fun CorePropertyEditor(field:String,value:String,onChange:(String)->Unit,enabled:Boolean,
    maxHeight:Dp,onCancel:()->Unit,onConfirm:()->Unit,titleLimit:Int=TitleLimits.PROJECT) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun finish(action:()->Unit) {focus.clearFocus();keyboard?.hide();action()}
    BackHandler {if(enabled) finish(onCancel)}
    val title=field=="title"
    val valid=!title || (value.isNotBlank() && TitleLimits.count(value)<=titleLimit)
    ModularPanelSurface(Modifier.fillMaxWidth().heightIn(max=maxHeight).testTag("core-property-editor")) {
        ModularPanelLayout(header={},headerDivider=false,outlinedBody=false,compact=true,
            actions={ModularPanelActions(cancel={
                PrimaryControlButton(enabled=enabled,onClick={finish(onCancel)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cancelar")}
            },confirm={
                PrimaryControlButton(enabled=enabled && valid,onClick={finish(onConfirm)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Confirmar")}
            })}) {
            ModularTextField(value,onChange,if(title) "Título" else "Descripción",enabled,singleLine=title)
            if(title) Text("${TitleLimits.count(value)} / $titleLimit",style=MaterialTheme.typography.labelSmall)
        }
    }
}
