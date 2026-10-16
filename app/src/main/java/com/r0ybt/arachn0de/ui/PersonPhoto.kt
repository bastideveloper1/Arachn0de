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

/** Gallery/consultation photos stay complete; avatar surfaces opt into the shared crop geometry. */
@Composable internal fun PersonPhoto(person:Person,modifier:Modifier=Modifier, aspectRatio:Float=1f, framed:Boolean=false) {
    val storage=privateStorageContext(LocalContext.current)
    val density=LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(aspectRatio).background(MaterialTheme.colorScheme.surfaceContainer).testTag("person-photo:${person.id}").semantics {contentDescription=person.name},contentAlignment=Alignment.Center) {
        val dimension=(maxWidth.value*density).toInt().coerceIn(96,2048)
        val loaded by produceState<Pair<android.graphics.Bitmap?,String>>(null to "Cargando",storage,person.id,person.avatarFile,dimension,framed,person.avatarZoom) {
            value=null to "Cargando"
            value=withContext(Dispatchers.IO) {
                if(person.avatarFile==null) null to "Sin fotografía"
                else try {val store=AvatarStore(storage);val bitmap=if(framed) store.readFramedThumbnail(person.avatarFile,dimension,person.avatarZoom) else store.readThumbnail(person.avatarFile,dimension);bitmap to if(bitmap==null) "Fotografía no disponible" else "Fotografía cargada"}
                catch(c:CancellationException) {throw c} catch(e:Exception) {null to "No se pudo cargar la fotografía"}
            }
        }
        val bitmap=loaded.first
        if(bitmap!=null) {
            val imageModifier=Modifier.fillMaxSize().testTag("person-image:${person.id}")
            if(framed) FramedAvatar(bitmap,com.r0ybt.arachn0de.domain.model.AvatarFraming(person.avatarZoom,person.avatarX,person.avatarY),imageModifier,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            else Image(bitmap.asImageBitmap(),person.name,imageModifier,contentScale=ContentScale.Fit)
        }
        else Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.semantics {contentDescription="${person.name}: ${loaded.second}"}) {
            Text(person.name.take(1).uppercase(),style=MaterialTheme.typography.headlineMedium)
            if(person.avatarFile!=null && loaded.second!="Cargando") Text("Foto no disponible",style=MaterialTheme.typography.labelSmall)
        }
    }
}
