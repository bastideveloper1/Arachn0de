package com.r0ybt.arachn0de.data.repository

import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.local.ManagementRoleEntity

/** Derived per project, before scope/search filters; never writes titles or identities. */
internal fun storyLabels(nodes:List<NodeEntity>,roles:List<ManagementRoleEntity>):Map<String,String> {
    val ids=roles.filter {it.role=="STORY"}.mapTo(hashSetOf()) {it.nodeId}
    return nodes.filter {it.id in ids}.groupBy {it.projectId}.values.flatMap {stories->
        stories.sortedWith(compareBy<NodeEntity> {it.createdAt}.thenBy {it.id})
            .mapIndexed {index,node->node.id to "HU${index+1}"}
    }.toMap()
}
internal fun storyDisplayTitle(id:String,title:String,labels:Map<String,String>):String = labels[id]?.let {label->
    val body=title.replace(Regex("^HU\\s*\\d+(?:\\s*[—–:\\-]\\s*|\\s+|$)",RegexOption.IGNORE_CASE),"").trim()
    if(body.isEmpty()) label else "$label — $body"
} ?: title
