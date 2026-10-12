package com.r0ybt.arachn0de.domain.model

import com.r0ybt.arachn0de.data.local.PersonProfileEntity
import com.r0ybt.arachn0de.data.local.PersonRelationEntity
import java.util.Locale
import org.json.JSONArray

internal enum class PersonCategory(val label:String) {FAMILY("Familiares"),SOCIAL("Sociales"),WORK("Laborales"),ACADEMIC("Académicas"),CUSTOM("Personalizadas")}
internal enum class PersonRelationMode(val label:String) {DIRECTED("Dirigida con inversa"),RECIPROCAL("Recíproca"),ONE_WAY("No recíproca")}
internal data class RelationPreset(val label:String,val inverse:String,val mode:PersonRelationMode)
internal fun personPresets(category:PersonCategory)=when(category) {
    PersonCategory.FAMILY->listOf(RelationPreset("madre","hijo/a",PersonRelationMode.DIRECTED),RelationPreset("padre","hijo/a",PersonRelationMode.DIRECTED),RelationPreset("hijo/a","progenitor/a",PersonRelationMode.DIRECTED),RelationPreset("hermano/a","hermano/a",PersonRelationMode.RECIPROCAL),RelationPreset("pareja","pareja",PersonRelationMode.RECIPROCAL))
    PersonCategory.SOCIAL->listOf(RelationPreset("amigo/a","amigo/a",PersonRelationMode.RECIPROCAL),RelationPreset("conocido/a","conocido/a",PersonRelationMode.RECIPROCAL),RelationPreset("enemigo/a","",PersonRelationMode.ONE_WAY))
    PersonCategory.WORK->listOf(RelationPreset("compañero/a","compañero/a",PersonRelationMode.RECIPROCAL),RelationPreset("jefe/a","subordinado/a",PersonRelationMode.DIRECTED),RelationPreset("subordinado/a","jefe/a",PersonRelationMode.DIRECTED))
    PersonCategory.ACADEMIC->listOf(RelationPreset("profesor/a","estudiante",PersonRelationMode.DIRECTED),RelationPreset("estudiante","profesor/a",PersonRelationMode.DIRECTED),RelationPreset("compañero/a","compañero/a",PersonRelationMode.RECIPROCAL))
    PersonCategory.CUSTOM->emptyList()
}
/** Stable portable key: one stored edge; reversing a directed inverse does not create another edge. */
internal fun relationKey(source:String,target:String,category:String,label:String,inverse:String,mode:String):String {
    fun norm(s:String)=s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    fun role(s:String):String {
        val n=norm(s)
        return if(category=="FAMILY") when(n) {"madre","padre","progenitor/a"->"parent";"hijo/a"->"child";else->n} else n
    }
    val forward=mode=="ONE_WAY" || source<target
    return JSONArray(listOf(category,mode,if(forward) source else target,if(forward) target else source,role(if(forward) label else inverse),role(if(forward) inverse else label))).toString()
}
internal fun validatePersonDetails(people:Set<String>,profiles:List<PersonProfileEntity>,relations:List<PersonRelationEntity>) {
    require(profiles.map {it.personId}.distinct().size==profiles.size) {"Ficha duplicada."}
    profiles.forEach {require(it.personId in people);require(it.values().all {s->s.length<=16000}) {"Una sección supera el límite de texto."}}
    require(relations.map {it.id}.distinct().size==relations.size && relations.map {it.relationKey}.distinct().size==relations.size) {"La relación ya existe, incluida su inversa."}
    relations.forEach {r->
        require(r.id.isNotBlank() && r.id.length<=200 && r.sourceId in people && r.targetId in people && r.sourceId!=r.targetId) {"Selecciona dos Personas diferentes y existentes."}
        require(r.category in PersonCategory.entries.map {it.name} && r.mode in PersonRelationMode.entries.map {it.name})
        require(r.label.isNotBlank() && r.label==r.label.trim() && r.label.length<=200 && r.inverseLabel.length<=200 && r.meaning.length<=16000)
        require(when(r.mode) {"DIRECTED"->r.inverseLabel.isNotBlank() && r.inverseLabel==r.inverseLabel.trim();"RECIPROCAL"->r.inverseLabel==r.label;else->r.inverseLabel.isEmpty()}) {"La presentación inversa no corresponde al tipo de vínculo."}
        require(r.relationKey==relationKey(r.sourceId,r.targetId,r.category,r.label,r.inverseLabel,r.mode)) {"Identidad de relación inválida."}
    }
}
internal fun PersonRelationEntity.other(personId:String)=if(sourceId==personId) targetId else sourceId
internal fun PersonRelationEntity.presentation(personId:String)=if(sourceId==personId) "$label de" else if(mode=="ONE_WAY") "Vínculo entrante: $label (sin reciprocidad)" else "$inverseLabel de"
