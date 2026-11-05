package com.r0ybt.arachn0de.slots

import com.r0ybt.arachn0de.domain.model.*
import kotlin.random.Random

internal data class SlotCandidates(val projects:List<Project>,val nodes:List<Node>) {
    fun pools():Map<String,List<Node>> {
        val byId=nodes.associateBy {it.id}
        return projects.associate {project->project.id to nodes.filter {node->
            node.projectId==project.id && node.isCompletable &&
                (node.taskStatus==TaskStatus.PENDING ||
                    (node.taskStatus==TaskStatus.VERIFY && TaskStatePolicy.count(node,byId,project)==3))
        }.sortedWith(compareBy<Node> {it.position}.thenBy {it.id})}.filterValues {it.isNotEmpty()}
    }
}

/** Read-only, ephemeral selection. A frozen wheel gives every candidate one equally likely stop. */
internal class TaskSlotSession(private val random:Random=Random.Default) {
    val machine=SlotMachine(random)
    var selectedProjectId:String?=null;private set
    var projects:List<Project> = emptyList();private set
    var pools:Map<String,List<Node>> = emptyMap();private set
    var wheel:List<Node> = emptyList();private set
    var target=0;private set
    private var distance=0
    var message:String?=null;private set
    fun refresh(data:SlotCandidates) {
        pools=data.pools()
        projects=data.projects.filter {it.id in pools}
        if((machine.phase==SlotMachine.Phase.EMPTY || machine.phase==SlotMachine.Phase.FINISHED || selectedProjectId==null) && selectedProjectId !in pools) selectedProjectId=projects.firstOrNull()?.id
    }
    fun prepareSelection() {
        if(projects.isNotEmpty() && machine.canInsert()) {if(machine.insert())machine.inserted()}
    }
    fun choose(delta:Int) {
        prepareSelection()
        if(machine.phase!=SlotMachine.Phase.READY || projects.isEmpty())return
        val found=projects.indexOfFirst {it.id==selectedProjectId}
        val index=if(found>=0)found else if(delta>0)-1 else 0
        selectedProjectId=projects[Math.floorMod(index+delta,projects.size)].id
        message=null
    }
    fun insert():Boolean {
        if(pools.isEmpty())return false
        message=null
        return machine.insert()
    }
    fun spin():Boolean {
        val candidates=pools[selectedProjectId].orEmpty()
        if(candidates.isEmpty()) {message="Este Núcleo ya no tiene tareas disponibles. Selecciona otro.";return false}
        if(!machine.spin())return false
        wheel=candidates.toList()
        target=random.nextInt(wheel.size)
        distance=6*wheel.size+target
        message=null
        return true
    }
    fun position():Float {
        if(machine.phase==SlotMachine.Phase.FINISHED)return target.toFloat()
        val t=(machine.elapsed/4200f).coerceIn(0f,1f)
        return distance*(1f-(1f-t)*(1f-t)*(1f-t))
    }
    fun result():Node? {
        if(machine.phase!=SlotMachine.Phase.FINISHED)return null
        val id=wheel.getOrNull(target)?.id ?: return null
        return pools.values.asSequence().flatten().firstOrNull {it.id==id}
    }
    fun at(index:Int):Node=wheel[Math.floorMod(index,wheel.size)]
}

/** Kept by the navigation owner while the selected task is open; never written to storage. */
internal class SlotsSession {
    val traditional=SlotMachine(creditsEnabled=true)
    val tasks=TaskSlotSession()
    val savingsMachine=SlotMachine()
    var savingsAnimation:String?=null
    var savingsMode=false
    var tasksMode=false
    var soundEnabled=true
}
