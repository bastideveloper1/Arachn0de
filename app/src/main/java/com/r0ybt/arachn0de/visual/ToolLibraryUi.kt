package com.r0ybt.arachn0de.visual

import android.graphics.Bitmap
import com.r0ybt.arachn0de.domain.model.ToolGalleryPreference
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import com.r0ybt.arachn0de.ui.theme.role
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable internal fun rememberToolGalleryMode(tool:String):Pair<Boolean,(Boolean)->Unit> {
    val context=LocalContext.current.applicationContext
    val privateContext=(context as? com.r0ybt.arachn0de.Arachn0deApplication)?.privateContext ?: context
    val storage=remember(privateContext) {privateContext.getSharedPreferences(ToolGalleryPreference.NAME,0)}
    var grid by remember(storage,tool) {mutableStateOf(storage.all[tool] as? Boolean ?: true)}
    DisposableEffect(storage,tool) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->if(key==null || key==tool)grid=storage.all[tool] as? Boolean ?: true}
        storage.registerOnSharedPreferenceChangeListener(listener)
        onDispose {storage.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return grid to {next->if(runCatching {storage.edit().putBoolean(tool,next).commit()}.getOrDefault(false))grid=next}
}

/** Regenerable in-memory previews only. Eviction never recycles a bitmap still used by a visible card. */
internal class ToolPreviewCache(private val scope:com.r0ybt.arachn0de.security.PrivateCacheScope?=null) : AutoCloseable {
    private val cache=object:android.util.LruCache<String,Bitmap>(6*1024*1024) {override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    private val workers=Semaphore(2)
    private var closed=false
    init {scope?.register(this)}
    suspend fun get(key:String,render:()->Bitmap):Bitmap=withContext(Dispatchers.Default) {workers.withPermit {
        synchronized(cache) {
            checkOpen()
            cache.get(key)
        } ?: render().also {bitmap->synchronized(cache) {checkOpen();cache.put(key,bitmap)}}
    }}
    private fun checkOpen() {if(closed || scope?.isOpen==false)throw CancellationException("Almacén bloqueado.")}
    override fun close() {synchronized(cache) {closed=true;cache.evictAll()}}
}

@Composable internal fun ToolThumbnail(key:String,label:String,modifier:Modifier=Modifier,render:()->Bitmap) {
    val app=LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
    val session=app?.security?.current?.value?.access
    val cache=remember(session) {session?.toolPreviews ?: if(app==null)ToolPreviewCache() else null}
    val bitmap by produceState<Bitmap?>(null,key,cache) {
        try {value=cache?.get(key,render)} catch(e:CancellationException) {throw e} catch(_:Exception) {value=null}
    }
    Box(modifier,contentAlignment=Alignment.Center) {
        bitmap?.let {Image(it.asImageBitmap(),label,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
            ?: Icon(Icons.Default.Image,"Vista previa no disponible",Modifier.size(24.dp),tint=LocalAppearancePalette.current.muted)
    }
}

internal data class ToolLibraryItem(val id:String,val name:String,val subtitle:String="",val open:()->Unit,
    val preview:@Composable (Modifier)->Unit,val actions:List<Pair<String,()->Unit>> = emptyList())

@Composable internal fun ToolLibraryHeader(title:String,onBack:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        OutlinedButton(onClick=onBack,modifier=Modifier.heightIn(min=48.dp)) {Icon(Icons.AutoMirrored.Filled.ArrowBack,null,Modifier.size(20.dp));Spacer(Modifier.width(4.dp));Text("Volver")}
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable internal fun ToolDocumentGallery(items:List<ToolLibraryItem>,grid:Boolean,onGrid:(Boolean)->Unit,
    modifier:Modifier=Modifier,title:String="Mis diseños",onBack:(()->Unit)?=null,layoutToggle:Boolean=true,subtitleLines:Int=1,titleLines:Int=1) {
    val palette=LocalAppearancePalette.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            if(onBack!=null) IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver a mis diseños")}
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            if(layoutToggle) IconButton(onClick={onGrid(!grid)},modifier=Modifier.testTag("tool-gallery-layout")) {Icon(if(grid)Icons.Default.ViewList else Icons.Default.GridView,if(grid)"Ver lista" else "Ver cuadrícula")}
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val columns=if(!grid)1 else if(maxWidth>=600.dp)3 else if(maxWidth>=300.dp)2 else 1
            LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().testTag("tool-document-gallery"),
                contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(items.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {Text("Tus diseños aparecerán aquí al guardarlos.",style=MaterialTheme.typography.bodyMedium,color=palette.muted)}
                items(items,key={it.id}) {item->
                    var menu by remember(item.id) {mutableStateOf(false)}
                    OutlinedCard(onClick=item.open,modifier=Modifier.fillMaxWidth().testTag("tool-document:${item.id}"),
                        colors=CardDefaults.outlinedCardColors(containerColor=palette.surface)) {
                        if(grid) Column {
                            item.preview(Modifier.fillMaxWidth().height(104.dp).padding(6.dp))
                            Row(Modifier.fillMaxWidth().padding(start=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name,style=MaterialTheme.typography.titleSmall,maxLines=titleLines,overflow=TextOverflow.Ellipsis)
                                    if(item.subtitle.isNotEmpty())Text(item.subtitle,style=MaterialTheme.typography.labelSmall,color=palette.muted,maxLines=subtitleLines,overflow=TextOverflow.Ellipsis)
                                }
                                LibraryMenu(item,menu,{menu=it})
                            }
                        } else Row(Modifier.fillMaxWidth().padding(start=6.dp),verticalAlignment=Alignment.CenterVertically) {
                            item.preview(Modifier.width(72.dp).height(64.dp).padding(4.dp))
                            Column(Modifier.weight(1f).padding(8.dp)) {Text(item.name,style=MaterialTheme.typography.titleSmall,maxLines=titleLines,overflow=TextOverflow.Ellipsis);if(item.subtitle.isNotEmpty())Text(item.subtitle,style=MaterialTheme.typography.labelSmall,color=palette.muted,maxLines=subtitleLines,overflow=TextOverflow.Ellipsis)}
                            LibraryMenu(item,menu,{menu=it})
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun LibraryMenu(item:ToolLibraryItem,expanded:Boolean,onExpanded:(Boolean)->Unit) {
    if(item.actions.isEmpty())return
    Box {
        IconButton(onClick={onExpanded(true)},modifier=Modifier.size(48.dp).testTag("tool-document-menu:${item.id}")) {Icon(Icons.Default.MoreVert,"Acciones de ${item.name}",Modifier.size(20.dp))}
        DropdownMenu(expanded,onDismissRequest={onExpanded(false)}) {item.actions.forEach {(label,run)->DropdownMenuItem(text={Text(label)},onClick={onExpanded(false);run()})}}
    }
}

@Composable internal fun ToolCreationActions(enabled:Boolean,onBlank:()->Unit,onTemplate:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Lienzo vacío",Icons.Default.Add,onBlank),Triple("Crear desde plantilla",Icons.Default.Dashboard,onTemplate)).forEach {(label,icon,action)->
            OutlinedButton(onClick=action,enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) {
                Icon(icon,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text(label,style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable internal fun ToolLabeledButton(label:String,active:Boolean=false,enabled:Boolean=true,tag:String="",onClick:()->Unit,icon:@Composable ()->Unit) {
    val palette=LocalAppearancePalette.current
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.width(88.dp).heightIn(min=56.dp).testTag(tag).semantics {selected=active;if(!enabled)stateDescription="No disponible"},
        contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        colors=ButtonDefaults.outlinedButtonColors(containerColor=if(active)palette.selection else Color.Transparent,contentColor=palette.role("icons"))) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            icon();Text(label,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable internal fun ToolActionButton(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean=false,enabled:Boolean=true,onClick:()->Unit) {
    ToolLabeledButton(label,active,enabled,onClick=onClick) {Icon(icon,null,Modifier.size(22.dp))}
}

@Composable internal fun ToolMagnetIcon(tint:Color,modifier:Modifier=Modifier.size(22.dp)) {
    androidx.compose.foundation.Canvas(modifier) {
        val path=androidx.compose.ui.graphics.Path().apply {moveTo(size.width*.25f,size.height*.2f);lineTo(size.width*.25f,size.height*.55f);cubicTo(size.width*.25f,size.height*.95f,size.width*.75f,size.height*.95f,size.width*.75f,size.height*.55f);lineTo(size.width*.75f,size.height*.2f)}
        drawPath(path,tint,style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Square))
        drawLine(tint,androidx.compose.ui.geometry.Offset(size.width*.12f,size.height*.2f),androidx.compose.ui.geometry.Offset(size.width*.38f,size.height*.2f),2.dp.toPx())
        drawLine(tint,androidx.compose.ui.geometry.Offset(size.width*.62f,size.height*.2f),androidx.compose.ui.geometry.Offset(size.width*.88f,size.height*.2f),2.dp.toPx())
    }
}
