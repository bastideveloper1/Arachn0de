package com.r0ybt.arachn0de.domain.model

/** Closest explicit override wins. Resolving a mode never writes task status. */
object TaskStatePolicy {
    fun count(node:Node,nodes:Map<String,Node>,project:Project?):Int {
        if(node.projectId==null) return 2
        var cursor:Node?=node
        val visited=hashSetOf<String>()
        while(cursor!=null && visited.add(cursor.id)) {
            cursor.taskStateCount?.let {return it}
            cursor=cursor.parentId?.let(nodes::get)
        }
        return if(project?.verificationEnabled==true) 3 else 2
    }
    fun next(status:TaskStatus,count:Int)=when(status) {
        TaskStatus.PENDING->if(count==3) TaskStatus.VERIFY else TaskStatus.COMPLETED
        TaskStatus.VERIFY->TaskStatus.COMPLETED
        TaskStatus.COMPLETED->TaskStatus.PENDING
    }
}
