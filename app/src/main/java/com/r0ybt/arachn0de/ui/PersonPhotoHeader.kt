package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Keep the stored link unchanged; supply a web scheme only to the external opener. */
internal fun personWebUri(link:String):android.net.Uri? {
    val value=link.trim()
    if(value.isBlank() || value.any {it.isWhitespace()}) return null
    if(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value) && !value.startsWith("http://",true) && !value.startsWith("https://",true)) return null
    val uri=android.net.Uri.parse(if(value.contains("://")) value else "https://$value")
    return uri.takeIf {it.scheme?.lowercase() in listOf("http","https") && !it.host.isNullOrBlank()}
}

@Composable internal fun PersonPhotoHeader(person:Person,profile:PersonProfileEntity,
    birthday:String?=null,maximumHeight:androidx.compose.ui.unit.Dp=320.dp,onEditAvatar:()->Unit,publications:Int?=null,networkCount:Int=0,onNetwork:(()->Unit)?=null) {
    var error by remember {mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val locale=LocalConfiguration.current.locales[0]
    val now=com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow(emptyList(),System::currentTimeMillis,onRefresh={},dayOnly=true)
    val today=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val birth=birthday?.let {runCatching {java.time.LocalDate.parse(it)}.getOrNull()}
    val monthDay=birth?.let {java.time.MonthDay.from(it)} ?: birthday?.let {runCatching {java.time.MonthDay.parse(it)}.getOrNull()}
    val web=profile.links.lineSequence().map {it.trim()}.firstOrNull {personWebUri(it)!=null}
    Card(Modifier.fillMaxWidth().heightIn(max=maximumHeight),shape=RoundedCornerShape(8.dp),colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                PersonPhoto(person,Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel="Ver fotografía",onClick=onEditAvatar),framed=true)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(person.name,style=MaterialTheme.typography.titleMedium,color=Arachn0deColors.TextPrimary)
                    if(monthDay!=null) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Cake,"Cumpleaños",Modifier.size(18.dp),tint=Arachn0deColors.TextSecondary)
                        Text((birth?.format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu",locale)) ?: monthDay.format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM",locale)))+(birth?.let {personAge(it.toString(),today)}?.let {" · $it años"} ?: ""),style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextPrimary)
                    }
                }
            }
            if(publications!=null) Row(Modifier.fillMaxWidth().testTag("person-statistics"),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                val statisticsStyle=MaterialTheme.typography.bodySmall.copy(fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                Text("$publications Publicaciones",style=statisticsStyle,color=Arachn0deColors.TextPrimary,
                    modifier=Modifier.weight(1f).testTag("person-publications"),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text("$networkCount Red",style=statisticsStyle,color=Arachn0deColors.TextPrimary,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                    modifier=Modifier.weight(1f).heightIn(min=48.dp)
                        .clickable(enabled=onNetwork!=null,role=androidx.compose.ui.semantics.Role.Button) {onNetwork?.invoke()}.wrapContentHeight().testTag("person-network"))
            }
            if(profile.description.isNotBlank()) Text(profile.description.take(150),style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)
            if(web!=null) Text(web,Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClickLabel="Abrir enlace web") {
                try {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,personWebUri(web)))} catch(e:Exception) {error="No se pudo abrir el enlace con una aplicación del dispositivo."}
            },style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextPrimary,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    error?.let {AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}
