package com.r0ybt.arachn0de.bank

import com.r0ybt.arachn0de.data.local.*

/** Read-only diagnosis. Never repairs, moves or removes existing user records. */
internal object BankHierarchy {
    fun issues(rootId:String,accounts:List<BankAccount>,nodes:List<NodeEntity>):List<String> {
        val owned=accounts.filter {it.rootId==rootId}.associateBy {it.id}
        return buildList {
            nodes.filter {it.projectId==rootId || it.id in owned}.forEach {n->
                if(n.id !in owned || n.projectId!=rootId || n.parentId!=null || n.purpose!="LAYER")
                    add("${n.title} [${n.id}] · padre: ${n.parentId ?: n.projectId ?: "sin referencia"}")
            }
            owned.keys.filter {id->nodes.none {it.id==id}}.forEach {add("Cuenta sin elemento: $it")}
        }
    }
}
