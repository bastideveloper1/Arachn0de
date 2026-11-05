package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.data.local.PrivateImageCache
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The common file lock protects this memory-only read from concurrent cleanup/restore. */
internal suspend fun readDescriptionThumbnail(repository:AttachmentRepository,file:AttachmentFileEntity):Bitmap? = withContext(Dispatchers.IO) {
    if(file.lifecycleState!="READY" || !file.mimeType.startsWith("image/")) return@withContext null
    AttachmentRepository.fileOperations.withLock {
        try {PrivateImageCache.read(repository.localFile(file),256,immutable=true,oriented=true)}
        catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) {null}
    }
}

@Composable internal fun DescriptionThumbnail(file:AttachmentFileEntity,repository:AttachmentRepository,label:String,onOpen:()->Unit) {
    val bitmap by produceState<Bitmap?>(null,repository,file) {value=readDescriptionThumbnail(repository,file)}
    Box(Modifier.fillMaxSize().testTag("description-thumbnail:${file.id}").clickable(onClickLabel="Abrir imagen original",onClick=onOpen),contentAlignment=Alignment.Center) {
        val image=bitmap
        if(image!=null) Image(image.asImageBitmap(),label,Modifier.fillMaxSize().testTag("description-thumbnail-image:${file.id}"),contentScale=ContentScale.Fit)
        else Text(label,style=MaterialTheme.typography.labelSmall,maxLines=2)
    }
}
