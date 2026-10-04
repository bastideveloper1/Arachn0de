package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*
import java.text.DateFormat
import java.util.Date

/** Compact pending work, preserving only ancestors needed to locate tasks. */
object PendingChatRenderer {
    fun render(tree: NodeTreeSnapshot, rootId: String?, project: Project? = null,
        people: Map<String,List<Person>> = emptyMap(), checkCancelled: () -> Unit = {}): String {
        val output=StringBuilder("Arachn0de — Pendientes\n\n")
        fun append(text:String) {
            if(output.length.toLong()+text.length>NodeExportSnapshot.MAX_TEXT_CHARS) throw ContextTooLargeException()
            output.append(text)
        }
        fun clean(text:String)=text.replace(Regex("[\\r\\n\\t]+")," ").trim()
        project?.let { append(clean(it.name)+"\n") }
        val stack=ArrayDeque<Pair<Node,Int>>()
        val roots=if(rootId!=null) listOf(tree.nodesById.getValue(rootId)) else tree.childrenOf(null).filter { it.projectId==project?.id }
        roots.asReversed().forEach { stack.addLast(it to 0) }
        var tasks=0
        while(stack.isNotEmpty()) {
            checkCancelled()
            val (node,depth)=stack.removeLast()
            val progress=tree.progressById.getValue(node.id)
            if(!progress.hasPending) continue
            val indent="  ".repeat(depth.coerceAtMost(5))
            val children=tree.childrenOf(node.id)
            if(children.isNotEmpty()) {
                append("$indent${clean(node.title)}\n")
                children.asReversed().forEach { stack.addLast(it to depth+1) }
            } else if(node.isCompletable && !node.isCompleted) {
                tasks++
                append("$indent• ${clean(node.title)}\n")
                val details=mutableListOf<String>()
                people[node.id].orEmpty().takeIf { it.isNotEmpty() }?.let { details.add("Responsables: "+it.joinToString(", ") { person -> clean(person.name) }) }
                node.dueAt?.let { details.add("Vence: "+DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it))) }
                if(node.effectivePriority!=Priority.NONE) details.add("Prioridad: "+when(node.effectivePriority) { Priority.HIGH->"Alta";Priority.MEDIUM->"Media";else->"Baja" })
                if(details.isNotEmpty()) append("$indent  ${details.joinToString(" · ")}\n")
            }
        }
        if(tasks==0) append("Sin pendientes\n")
        return output.toString()
    }
}
