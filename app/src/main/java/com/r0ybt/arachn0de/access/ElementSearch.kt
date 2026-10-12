package com.r0ybt.arachn0de.access

import com.r0ybt.arachn0de.domain.model.*
import java.text.Normalizer
import java.util.Locale

internal data class ElementResult(val key:String,val projectId:String,val nodeId:String?,val title:String,val description:String,val type:String,val path:String,val destination:Boolean)
internal object ElementSearch {
    fun normalize(text:String)=Normalizer.normalize(text,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace(Regex("\\s+")," ").trim()
    fun index(projects:List<Project>,nodes:List<Node>):List<ElementResult> {
        val byId=nodes.associateBy {it.id};val names=projects.associate {it.id to it.name}
        fun path(n:Node):String {val titles=mutableListOf<String>();val seen=hashSetOf<String>();var parent=n.parentId
            while(parent!=null && seen.add(parent)) {val p=byId[parent] ?: break;titles.add(p.title);parent=p.parentId}
            return (listOf(names[n.projectId].orEmpty())+titles.reversed()).joinToString(" › ")
        }
        return projects.map {ElementResult("project:${it.id}",it.id,null,it.name,it.description,"Proyecto",it.name,true)}+
            nodes.filter {it.projectId in names}.map {ElementResult("node:${it.id}",it.projectId,it.id,it.title,it.description,if(it.isStructural) "Capa" else if(it.purpose==NodePurpose.NOTE) "Nota" else "Tarea",path(it),it.canReceiveChildren)}
    }
    fun find(index:List<ElementResult>,query:String):List<ElementResult> {
        val q=normalize(query);return if(q.isEmpty()) index else index.filter {q in normalize(it.title)+" "+normalize(it.description)}
    }
}
