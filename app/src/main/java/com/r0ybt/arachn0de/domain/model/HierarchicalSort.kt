package com.r0ybt.arachn0de.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class SortCriterion(val label:String) {
    STATE("Estado"), PRIORITY("Prioridad"), DUE("Fecha de vencimiento"), CREATED("Fecha de creación"),
    PERSON("Responsable"), NAME("Nombre"), TAG("Etiqueta"), EFFORT("Esfuerzo")
}
data class SortRule(val criterion:SortCriterion,val enabled:Boolean=false,val reverse:Boolean=false)
enum class FavoriteOrder(val label:String) {
    NONE("Sin preferencia"), FIRST("Favoritos arriba"), LAST("Favoritos abajo");
    fun compare(a:Boolean,b:Boolean):Int=when(this) {
        NONE->0; FIRST->compareValues(!a,!b); LAST->compareValues(a,b)
    }
}
data class SortConfiguration(
    val manual:Boolean=true,
    val rules:List<SortRule> = SortCriterion.entries.map {SortRule(it)},
    val layersFirst:Boolean=false,
    val inherited:Boolean=false,
    val appliedLayersFirst:Boolean=false,
    val legacyLayersOverride:Boolean=false,
    val favorites:FavoriteOrder=FavoriteOrder.NONE
) {
    init {require(rules.size==SortCriterion.entries.size && rules.map {it.criterion}.toSet()==SortCriterion.entries.toSet())}
}

/** Lives in the existing preference row; old modes remain readable without rewriting user data. */
object SortConfigurationCodec {
    const val PREFIX="SORT1:"
    const val PROJECTS_CONTEXT="@projects"
    val legacy=setOf("MANUAL","DUE_ASC","DUE_DESC","DUE_PRIORITY","PRIORITY","CREATED_NEWEST","CREATED_OLDEST","INHERIT")
    fun encode(value:SortConfiguration):String=PREFIX+JSONObject().put("manual",value.manual).put("inherit",value.inherited)
        .put("layers",value.layersFirst).put("appliedLayers",value.appliedLayersFirst).put("legacyLayers",value.legacyLayersOverride)
        .put("rules",JSONArray(value.rules.map {JSONObject().put("key",it.criterion.name).put("on",it.enabled).put("reverse",it.reverse)}))
        .apply {if(value.favorites!=FavoriteOrder.NONE)put("favorites",value.favorites.name)}.toString()
    fun decode(value:String,layersFirst:Boolean=false):SortConfiguration {
        if(value.startsWith(PREFIX)) {
            require(value.length<=4096)
            val json=JSONObject(value.removePrefix(PREFIX))
            fun flag(row:JSONObject,key:String)=row.get(key) as? Boolean ?: error("Indicador de orden inválido.")
            val fields=setOf("manual","inherit","layers","appliedLayers","legacyLayers","rules")
            require(json.keys().asSequence().toSet()==fields+if(json.has("favorites"))setOf("favorites") else emptySet())
            val array=json.getJSONArray("rules");require(array.length() in (SortCriterion.entries.size-1)..SortCriterion.entries.size)
            val rules=(0 until array.length()).map {i->val row=array.getJSONObject(i)
                require(row.keys().asSequence().toSet()==setOf("key","on","reverse"))
                SortRule(SortCriterion.valueOf(row.getString("key")),flag(row,"on"),flag(row,"reverse"))}
            require(rules.map {it.criterion}.toSet().size==rules.size)
            val expected=if(array.length()==SortCriterion.entries.size)SortCriterion.entries.toSet() else SortCriterion.entries.filter {it!=SortCriterion.EFFORT}.toSet()
            require(rules.map {it.criterion}.toSet()==expected)
            val upgraded=if(array.length()<SortCriterion.entries.size)rules+SortRule(SortCriterion.EFFORT) else rules
            return SortConfiguration(flag(json,"manual"),upgraded,flag(json,"layers"),flag(json,"inherit"),flag(json,"appliedLayers"),flag(json,"legacyLayers"),
                if(json.has("favorites"))FavoriteOrder.valueOf(json.getString("favorites")) else FavoriteOrder.NONE)
        }
        require(value in legacy)
        fun rule(key:SortCriterion,reverse:Boolean=false)=SortRule(key,true,reverse)
        val active=when(value) {
            "DUE_ASC"->listOf(rule(SortCriterion.DUE))
            "DUE_DESC"->listOf(rule(SortCriterion.DUE,true))
            "DUE_PRIORITY"->listOf(rule(SortCriterion.DUE),rule(SortCriterion.PRIORITY))
            "PRIORITY"->listOf(rule(SortCriterion.PRIORITY),rule(SortCriterion.DUE))
            "CREATED_NEWEST"->listOf(rule(SortCriterion.CREATED))
            "CREATED_OLDEST"->listOf(rule(SortCriterion.CREATED,true))
            else->emptyList()
        }
        return SortConfiguration(value in setOf("MANUAL","INHERIT"),active+SortCriterion.entries.filter {key->active.none {it.criterion==key}}.map {SortRule(it)},layersFirst,value=="INHERIT",layersFirst,value=="INHERIT")
    }
    fun hasEffortRule(value:String):Boolean=value.startsWith(PREFIX) && JSONObject(value.removePrefix(PREFIX)).getJSONArray("rules").length()==SortCriterion.entries.size
    fun hasFavoriteOrder(value:String):Boolean=value.startsWith(PREFIX) && JSONObject(value.removePrefix(PREFIX)).has("favorites")
    fun effective(rows:Map<String,Pair<String,Boolean>>,context:String):SortConfiguration {
        val local=rows[context]?.let {decode(it.first,it.second)}
        if(context==PROJECTS_CONTEXT)return local ?: SortConfiguration()
        if(local!=null && !local.inherited)return local
        val root=context.substringBeforeLast(':')+":project-root"
        val parent=if(root!=context)rows[root]?.let {decode(it.first,it.second)} else null
        return (parent ?: SortConfiguration()).copy(inherited=root!=context,
            appliedLayersFirst=local?.appliedLayersFirst ?: false,
            layersFirst=if(local?.legacyLayersOverride==true)local.layersFirst else parent?.layersFirst ?: false,
            legacyLayersOverride=local?.legacyLayersOverride ?: false)
    }
}

object HierarchicalSort {
    fun normalized(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).trim()
    fun comparator(config:SortConfiguration,people:Map<String,List<String>> = emptyMap(),tags:Map<String,List<String>> = emptyMap(),stateCounts:Map<String,Int> = emptyMap(),favorites:Set<String> = emptySet()):Comparator<Node> {
        fun text(values:List<String>?,reverse:Boolean):String?=values.orEmpty().map(::normalized).filter {it.isNotBlank()}.let {if(reverse)it.maxOrNull() else it.minOrNull()}
        fun <T:Comparable<T>> optional(a:T?,b:T?,reverse:Boolean):Int=when {a==null && b==null->0;a==null->1;b==null->-1;reverse->b.compareTo(a);else->a.compareTo(b)}
        fun priority(n:Node)=if(n.isCompletable)when(n.priority) {Priority.HIGH->3;Priority.MEDIUM->2;Priority.LOW->1;Priority.NONE->0} else 0
        fun state(n:Node,reverse:Boolean):Int?=if(!n.isCompletable)null else if(n.isCompleted)2 else if(stateCounts[n.id]==2)0 else if(n.awaitingVerification) {if(reverse)0 else 1} else {if(reverse)1 else 0}
        val stable=compareBy<Node> {it.position}.thenBy {it.createdAt}.thenBy {it.id}
        return Comparator {a,b->
            var result=compareValues(a.isCompleted,b.isCompleted)
            if(result==0 && config.layersFirst)result=compareValues(!a.isStructural,!b.isStructural)
            if(result==0)result=compareValues(!(a.isStructural && a.isPinned),!(b.isStructural && b.isPinned))
            if(result==0)result=config.favorites.compare("node:${a.id}" in favorites,"node:${b.id}" in favorites)
            if(result==0 && !config.manual && !(config.layersFirst && a.isStructural && b.isStructural)) {
                for(rule in config.rules.filter {it.enabled}) {
                    result=when(rule.criterion) {
                        SortCriterion.EFFORT->optional(a.effort.takeIf {a.isCompletable && it>0},b.effort.takeIf {b.isCompletable && it>0},!rule.reverse)
                        SortCriterion.STATE->optional(state(a,rule.reverse),state(b,rule.reverse),false)
                        SortCriterion.PRIORITY->if(rule.reverse)compareValues(priority(a),priority(b)) else compareValues(priority(b),priority(a))
                        SortCriterion.DUE->optional(a.dueAt,b.dueAt,rule.reverse)
                        SortCriterion.CREATED->if(rule.reverse)compareValues(a.createdAt,b.createdAt) else compareValues(b.createdAt,a.createdAt)
                        SortCriterion.PERSON->optional(text(people[a.id],rule.reverse),text(people[b.id],rule.reverse),rule.reverse)
                        SortCriterion.NAME->optional(normalized(a.title),normalized(b.title),rule.reverse)
                        SortCriterion.TAG->optional(text(tags[a.id],rule.reverse),text(tags[b.id],rule.reverse),rule.reverse)
                    }
                    if(result!=0)break
                }
            }
            if(result==0)stable.compare(a,b) else result
        }
    }
}
