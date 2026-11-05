package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*

/** Human-readable context only. These paths are never used for filesystem access. */
internal object TaskPlainTextRenderer {
    const val SEPARATOR="────────────────────"

    fun render(tree:NodeTreeSnapshot,ids:List<String>,projects:Map<String,Project>,
        checkCancelled:()->Unit={},descriptionText:(String)->String={it}):String {
        val output=StringBuilder()
        fun append(value:String) {
            if(output.length.toLong()+value.length>NodeExportSnapshot.MAX_TEXT_CHARS) throw ContextTooLargeException()
            output.append(value)
        }
        ids.forEachIndexed {index,id->
            checkCancelled()
            val node=tree.nodesById.getValue(id)
            val parents=ArrayDeque<String>()
            var parent=node.parentId
            var pathSize=0L
            while(parent!=null) {
                checkCancelled()
                val container=tree.nodesById.getValue(parent)
                pathSize+=container.title.length.toLong()+1
                if(pathSize>NodeExportSnapshot.MAX_TEXT_CHARS) throw ContextTooLargeException()
                parents.addFirst(container.title)
                parent=container.parentId
            }
            if(index>0) append("\n\n$SEPARATOR\n\n")
            append(node.projectId?.let {projects.getValue(it).name} ?: "Borradores");append("/")
            parents.forEach {append(it);append("/")}
            append("\n");append(node.title)
            if(node.purpose==NodePurpose.ACTION) {
                append("\nEstado: ")
                append(node.workState?.label ?: node.taskStatus.label)
            }
            if(node.description.isNotBlank()) {
                append("\n│ ");append(descriptionText(node.description))
            }
        }
        return output.toString()
    }

    /** The existing preorder, including notes and containers, is retained for copying branches. */
    fun branch(tree:NodeTreeSnapshot,rootId:String?,projectId:String,onlyPending:Boolean=false,checkCancelled:()->Unit={}):List<String> {
        val stack=ArrayDeque<String>()
        val roots=if(rootId!=null) listOf(tree.nodesById.getValue(rootId)) else tree.childrenOf(null).filter {it.projectId==projectId}
        roots.asReversed().forEach {stack.addLast(it.id)}
        val ids=mutableListOf<String>()
        while(stack.isNotEmpty()) {
            checkCancelled()
            val node=tree.nodesById.getValue(stack.removeLast())
            if(onlyPending && node.isCompletable && node.isCompleted) continue
            ids.add(node.id)
            tree.childrenOf(node.id).asReversed().forEach {stack.addLast(it.id)}
        }
        return ids
    }
}
