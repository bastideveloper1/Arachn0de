package com.r0ybt.arachn0de.data.repository

import com.r0ybt.arachn0de.data.local.NodeResponsibilityEntity
internal enum class WorkOwnership(val label:String) { ALL("Todas"), MINE("Mis tareas"), OTHERS("De otros"), UNASSIGNED("Sin responsable") }
internal fun workResponsibilities(managed:List<NodeResponsibilityEntity>,traditional:List<Pair<String,String>>):Map<String,Set<String>> =
    (managed.map {it.nodeId to it.personId}+traditional).groupBy({it.first},{it.second}).mapValues {it.value.toSet()}
internal fun matchesOwnership(node:String,filter:WorkOwnership,self:String?,responsible:Map<String,Set<String>>):Boolean {
    val people=responsible[node].orEmpty()
    return when(filter) {WorkOwnership.ALL->true;WorkOwnership.MINE->self!=null && self in people;WorkOwnership.OTHERS->people.isNotEmpty() && (self==null || self !in people);WorkOwnership.UNASSIGNED->people.isEmpty()}
}
