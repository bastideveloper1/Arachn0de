package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.domain.model.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Complete stored image. Cropping/framing remains only in compact avatar indicators. */
@Composable internal fun PersonPhoto(person:Person,modifier:Modifier=Modifier) {
    val storage=privateStorageContext(LocalContext.current)
    val density=LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainer).testTag("person-photo:${person.id}").semantics {contentDescription=person.name},contentAlignment=Alignment.Center) {
        val dimension=(maxWidth.value*density).toInt().coerceIn(96,2048)
        val loaded by produceState<Pair<android.graphics.Bitmap?,String>>(null to "Cargando",storage,person.id,person.avatarFile,dimension) {
            value=null to "Cargando"
            value=withContext(Dispatchers.IO) {
                if(person.avatarFile==null) null to "Sin fotografía"
                else try {val bitmap=AvatarStore(storage).readThumbnail(person.avatarFile,dimension);bitmap to if(bitmap==null) "Fotografía no disponible" else "Fotografía cargada"}
                catch(c:CancellationException) {throw c} catch(e:Exception) {null to "No se pudo cargar la fotografía"}
            }
        }
        val bitmap=loaded.first
        if(bitmap!=null) Image(bitmap.asImageBitmap(),person.name,Modifier.fillMaxSize().testTag("person-image:${person.id}"),contentScale=ContentScale.Fit)
        else Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.semantics {contentDescription="${person.name}: ${loaded.second}"}) {
            Text(person.name.take(1).uppercase(),style=MaterialTheme.typography.headlineMedium)
            if(person.avatarFile!=null && loaded.second!="Cargando") Text("Foto no disponible",style=MaterialTheme.typography.labelSmall)
        }
    }
}
