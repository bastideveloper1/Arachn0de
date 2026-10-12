package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val objectiveHelpParagraphs=listOf(
    "Objetivo general" to "Es el resultado principal que pretende alcanzar el proyecto. Por ejemplo: Desarrollar una aplicación móvil para organizar proyectos personales.",
    "Objetivos específicos" to "Son resultados concretos que contribuyen al objetivo general. Por ejemplo: Implementar la creación y edición de proyectos; permitir el seguimiento del avance de tareas; validar la aplicación mediante pruebas de uso.",
    "Cómo redactarlos" to "Empieza con un verbo que describa un resultado, concreta qué se conseguirá e indica cómo podrás comprobarlo. Evita expresiones ambiguas como mejorar mucho. Define un alcance y, cuando sea útil, una fecha o un criterio verificable.",
    "Una estructura flexible" to "Puedes describir el resultado general en la descripción de Objetivos y crear tarjetas para los resultados específicos. No se exige un único objetivo general ni una cantidad fija de objetivos específicos. Las tareas y las historias vinculadas representan el trabajo para conseguirlos."
)
internal val responsibilityHelpParagraphs=listOf(
    "Roles generales del proyecto" to "Describen la función de una persona dentro del equipo del proyecto. Una persona puede tener varios roles.",
    "Responsabilidades sobre el trabajo" to "Describen su participación en un objetivo, historia, épica o tarea concreta. Estas asignaciones son independientes de los roles generales y no cambian automáticamente al editar el equipo.",
    "Lead o Líder" to "Coordina y orienta el trabajo.",
    "Ejecutor" to "Realiza el trabajo asignado.",
    "Supervisor" to "Supervisa su desarrollo.",
    "Validador" to "Comprueba el resultado. Asignar este rol no valida automáticamente criterios ni completa tareas.",
    "Personas y equipo" to "El catálogo de Personas pertenece a Arachn0de. Incorporar una persona la vincula a este equipo; quitarla retira sus roles y responsabilidades en este proyecto, conservando su ficha global. Los roles describen responsabilidades, no permisos de seguridad ni autoría de acciones."
)
@Composable internal fun ManagementHelpScreen(title:String,paragraphs:List<Pair<String,String>>,onDismiss:()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    paragraphs.forEach {(heading,body)->item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(heading,style=MaterialTheme.typography.titleMedium)
                        Text(body,style=MaterialTheme.typography.bodyLarge)
                    }}}
                }
                OutlinedButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) {Text("Volver")}
            }
        }
    }
}
