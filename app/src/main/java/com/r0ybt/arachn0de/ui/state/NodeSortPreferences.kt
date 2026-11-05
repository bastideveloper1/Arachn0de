package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.SortConfiguration
import com.r0ybt.arachn0de.domain.model.SortConfigurationCodec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver

/** Layer overrides fall back to the project root; app-owned choices persist locally. */
internal class NodeSortPreferences(initial: List<Pair<String, NodeSortMode>> = emptyList(), private val storage: android.content.SharedPreferences? = null, private val onWrite: ((String, NodeSortMode?) -> Unit)? = null, private val onLayersWrite:((String,Boolean,NodeSortMode)->Unit)?=null, private val onConfigWrite:((String,SortConfiguration)->Unit)?=null) {
    var loaded by mutableStateOf(onConfigWrite==null)
        private set
    private var entries by mutableStateOf(storage?.all?.mapNotNull { (key, value) ->
        NodeSortMode.entries.firstOrNull { it.name == value }?.let { key to it }
    } ?: initial.takeLast(MAX_CONTEXTS))
    var applicationHandler:((String,SortConfiguration,Set<String>?,(Throwable?)->Unit)->Unit)?=null
    fun apply(context:String,ids:Set<String>?,done:(Throwable?)->Unit) {
        applicationHandler?.invoke(context,configuration(context),ids,done) ?: done(IllegalStateException("No se pudo aplicar el orden."))
    }
    private val pendingConfigurations=mutableMapOf<String,SortConfiguration>()
    fun applied(context:String,value:SortConfiguration) {
        val updated=value.copy(appliedLayersFirst=value.layersFirst)
        pendingConfigurations[context]=updated;configurations=configurations+(context to updated)
    }
    fun reject(context:String,value:SortConfiguration,rows:List<com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity>) {
        if(pendingConfigurations[context]==value)pendingConfigurations.remove(context)
        replace(rows)
    }
    private var configurations by mutableStateOf(storage?.all?.mapNotNull {(key,value)->
        (value as? String)?.takeIf {it.startsWith(SortConfigurationCodec.PREFIX)}?.let {key to SortConfigurationCodec.decode(it)}
    }?.toMap() ?: emptyMap<String,SortConfiguration>())
    fun configuration(context:String):SortConfiguration {
        val rows=entries.associate {(key,mode)->key to (mode.name to (layerEntries[key] ?: false))}.toMutableMap()
        layerEntries.forEach {(key,value)->if(key !in rows)rows[key]="INHERIT" to value}
        configurations.forEach {(key,value)->rows[key]=SortConfigurationCodec.encode(value) to value.appliedLayersFirst}
        return SortConfigurationCodec.effective(rows,context)
    }
    fun owns(context:String)=configurations[context]?.let {!it.inherited} ?: entries.any {it.first==context}
    fun configure(context:String,value:SortConfiguration) {
        require(context.length in 1..256)
        if(onConfigWrite!=null)pendingConfigurations[context]=value
        configurations=configurations+(context to value)
        entries=entries.filterNot {it.first==context}
        storage?.edit()?.putString(context,SortConfigurationCodec.encode(value))?.apply()
        onConfigWrite?.invoke(context,value)
    }
    private var layerEntries by mutableStateOf(emptyMap<String,Boolean>())
    fun layersFirst(context:String)=configurations[context]?.appliedLayersFirst ?: layerEntries[context] ?: false
    fun setLayersFirst(context:String,enabled:Boolean) {
        require(context.length in 1..256)
        layerEntries=layerEntries+(context to enabled)
        onLayersWrite?.invoke(context,enabled,mode(context))
    }
    fun replace(rows: List<com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity>) {
        loaded=true
        val confirmed=rows.filter {it.mode.startsWith(SortConfigurationCodec.PREFIX)}.associate {it.context to SortConfigurationCodec.decode(it.mode)}
        pendingConfigurations.entries.removeAll {(context,value)->confirmed[context]==value}
        configurations=confirmed+pendingConfigurations
        layerEntries=rows.associate {it.context to it.layersFirst}
        entries = rows.filter {it.context !in pendingConfigurations}.mapNotNull { row -> NodeSortMode.entries.firstOrNull { it.name == row.mode }?.let { row.context to it } }
    }
    fun mode(context: String): NodeSortMode = entries.firstOrNull { it.first == context }?.second
        ?: entries.firstOrNull { it.first == context.substringBeforeLast(':') + ":project-root" }?.second
        ?: NodeSortMode.MANUAL
    fun inherit(context: String) {
        if(configurations.containsKey(context) || onConfigWrite!=null) {
            configure(context,configuration(context).copy(inherited=true,legacyLayersOverride=false));return
        }
        entries = entries.filterNot { it.first == context }
        storage?.edit()?.remove(context)?.apply()
        onWrite?.invoke(context, null)
    }
    fun set(context: String, mode: NodeSortMode) {
        require(context.length in 1..256)
        configurations=configurations-context;pendingConfigurations.remove(context)
        val retained = entries.filterNot { it.first == context }
        entries = (retained + (context to mode)).let { if (storage == null && onWrite == null) it.takeLast(MAX_CONTEXTS) else it }
        storage?.edit()?.putString(context, mode.name)?.apply()
        onWrite?.invoke(context, mode)
    }
    companion object {
        const val MAX_CONTEXTS = 64
        val Saver = listSaver<NodeSortPreferences, String>(
            save = { it.entries.flatMap { (context, mode) -> listOf(context, mode.name) } + listOf("__layers_first__") + it.layerEntries.flatMap {(context,enabled)->listOf(context,enabled.toString())} + (if(it.configurations.isEmpty())emptyList() else listOf("__hierarchical__")+it.configurations.flatMap {(context,value)->listOf(context,SortConfigurationCodec.encode(value))}) },
            restore = { values -> NodeSortPreferences(values.takeWhile {it!="__layers_first__"}.chunked(2).mapNotNull { pair ->
                if (pair.size != 2 || pair[0].length !in 1..256) null
                else NodeSortMode.entries.firstOrNull { it.name == pair[1] }?.let { pair[0] to it }
            }).apply {layerEntries=values.dropWhile {it!="__layers_first__"}.drop(1).takeWhile {it!="__hierarchical__"}.chunked(2).filter {it.size==2}.associate {it[0] to it[1].toBoolean()};configurations=values.dropWhile {it!="__hierarchical__"}.drop(1).chunked(2).filter {it.size==2}.associate {it[0] to SortConfigurationCodec.decode(it[1])} } },
        )
    }
}
