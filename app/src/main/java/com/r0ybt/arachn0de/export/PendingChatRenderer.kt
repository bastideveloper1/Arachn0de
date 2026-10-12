package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*
import java.text.DateFormat
import java.util.Date

/** Compact pending work, preserving only ancestors needed to locate tasks. */
object PendingChatRenderer {
    fun render(tree: NodeTreeSnapshot, rootId: String?, project: Project? = null,
        people: Map<String,List<Person>> = emptyMap(), checkCancelled: () -> Unit = {}, descriptionText: (String) -> String = { it }, completeHierarchy:Boolean=false, onlyPending:Boolean=false): String {
        val output=StringBuilder(if(completeHierarchy) "Arachn0de — Jerarquía\n\n" else "Arachn0de — Pendientes\n\n")
        fun append(text:String) {
            if(output.length.toLong()+text.length>NodeExportSnapshot.MAX_TEXT_CHARS) throw ContextTooLargeException()
            output.append(text)
        }
        fun clean(text:String)=text.replace(Regex("[\\r\\n\\t]+")," ").trim()
        fun block(text:String,indent:String,prefix:String="") {
            if(text.length>NodeExportSnapshot.MAX_TEXT_CHARS) throw ContextTooLargeException()
            var first=true
            text.lineSequence().forEach {line->
                checkCancelled();append(indent);append(if(first) prefix else " ".repeat(prefix.length));append(line);append("\n");first=false
            }
        }
        project?.let {
            if(completeHierarchy) {
                block(it.name,"")
                if(it.description.length>NodeExportSnapshot.MAX_TEXT_CHARS) throw ContextTooLargeException()
                block(descriptionText(it.description),"")
            } else append(clean(it.name)+"\n")
        }
        val seen=hashSetOf<String>()
        val stack=ArrayDeque<Pair<Node,Int>>()
        val roots=if(rootId!=null) listOf(tree.nodesById.getValue(rootId)) else tree.childrenOf(null).filter { it.projectId==project?.id }
        roots.asReversed().forEach { stack.addLast(it to 0) }
        var tasks=0
        while(stack.isNotEmpty()) {
            checkCancelled()
            val (node,depth)=stack.removeLast()
            val progress=tree.progressById.getValue(node.id)
            if(!completeHierarchy && !progress.hasPending && !(node.id == rootId && node.isStructural)) continue
            if(completeHierarchy && !seen.add(node.id)) continue
            if(completeHierarchy && onlyPending && node.isCompletable && node.isCompleted) continue
            val indent="  ".repeat(depth.coerceAtMost(5))
            val children=tree.childrenOf(node.id)
            if(completeHierarchy) {
                val prefix=if(node.isStructural) "" else if(node.isCompletable) "[${if(node.isCompleted) "x" else " "}] " else "Nota: "
                block(node.title,indent,prefix)
                if(depth>5) append("$indent  Nivel ${depth+1}\n")
                if(node.sprintMode) append("$indent  Modo Sprint\n")
                if(node.description.isNotEmpty()) {
                    if(node.description.length>NodeExportSnapshot.MAX_TEXT_CHARS) throw ContextTooLargeException()
                    block(descriptionText(node.description),"$indent  ")
                }
                val details=mutableListOf<String>()
                if(node.isCompletable) {
                    node.workState?.let {details.add("Estado: ${it.label}")}
                    people[node.id].orEmpty().takeIf {it.isNotEmpty()}?.let {details.add("Responsables: "+it.joinToString(", ") {p->clean(p.name)})}
                    node.dueAt?.let {details.add("Vence: "+DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)))}
                    if(node.effectivePriority!=Priority.NONE) details.add("Prioridad: "+when(node.effectivePriority) {Priority.HIGH->"Alta";Priority.MEDIUM->"Media";else->"Baja"})
                }
                if(details.isNotEmpty()) block(details.joinToString(" · "),"$indent  ")
                children.asReversed().forEach {stack.addLast(it to depth+1)}
                continue
            }
            if(node.isStructural) {
                append("$indent${clean(node.title)}${if(node.sprintMode) " · Modo Sprint" else ""}\n")
                if(node.description.isNotEmpty()) append("$indent  ${descriptionText(node.description)}\n")
                children.asReversed().forEach { stack.addLast(it to depth+1) }
            } else if(node.isCompletable && !node.isCompleted) {
                tasks++
                append("$indent• ${clean(node.title)}\n")
                if(node.description.isNotEmpty()) append("$indent  ${descriptionText(node.description)}\n")
                val details=mutableListOf<String>()
                node.workState?.let { details.add("Estado: ${it.label}") }
                people[node.id].orEmpty().takeIf { it.isNotEmpty() }?.let { details.add("Responsables: "+it.joinToString(", ") { person -> clean(person.name) }) }
                node.dueAt?.let { details.add("Vence: "+DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it))) }
                if(node.effectivePriority!=Priority.NONE) details.add("Prioridad: "+when(node.effectivePriority) { Priority.HIGH->"Alta";Priority.MEDIUM->"Media";else->"Baja" })
                if(details.isNotEmpty()) append("$indent  ${details.joinToString(" · ")}\n")
            }
        }
        if(!completeHierarchy && tasks==0) append("Sin pendientes\n")
        return output.toString()
    }
}
