package com.r0ybt.arachn0de.metro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun MetroCalculationDialog(lines:List<String>,onDismiss:()->Unit) {
    var help by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Ver cálculo")},text={
        LazyColumn(Modifier.fillMaxWidth().testTag("metro-calculation"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(lines) {Text(it,style=MaterialTheme.typography.bodySmall)}
            item {TextButton(onClick={help=!help},modifier=Modifier.heightIn(min=48.dp)) {Text("Cómo ayudar al aprendizaje")}}
            if(help) items(MetroCalculation.help) {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text("Cerrar")}})
}
