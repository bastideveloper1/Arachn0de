package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.repository.ManagementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val charterSectionSuggestions=listOf("Propósito","Justificación","Alcance","Restricciones","Entregables","Criterios de éxito","Riesgos","Antecedentes","Recursos")

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CharterSectionEditor(context:String,repository:ManagementRepository,onDismiss:()->Unit) {
    var title by rememberSaveable {mutableStateOf("")}
    var content by rememberSaveable {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    Dialog(onDismissRequest={if(!busy) onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Añadir sección",style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Elige una sugerencia o escribe el título de tu propia sección.")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        charterSectionSuggestions.forEach {suggestion->FilterChip(selected=title==suggestion,enabled=!busy,onClick={title=suggestion},label={Text(suggestion)})}
                    }
                    OutlinedTextField(title,{title=it},label={Text("Título de la sección")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(content,{content=it},label={Text("Contenido")},enabled=!busy,minLines=5,modifier=Modifier.fillMaxWidth())
                    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.weight(1f)) {Text("Cancelar")}
                    Button(enabled=!busy && title.isNotBlank(),modifier=Modifier.weight(1f),onClick={busy=true;error=null;scope.launch {
                        try {repository.cascade.createSection(context,title,content);onDismiss()}
                        catch(c:CancellationException) {throw c}
                        catch(e:Exception) {error=e.message ?: "No se pudo guardar la sección."}
                        finally {busy=false}
                    }}) {Text("Guardar sección")}
                }
            }
        }
    }
}
