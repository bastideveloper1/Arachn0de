package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.text.Normalizer
import java.util.Locale

/** Stable association IDs can later own photo markers without conflating two people with the same name. */
@Entity(tableName="person_memory_people",foreignKeys=[
    ForeignKey(entity=PersonMemoryEntity::class,parentColumns=["id"],childColumns=["memoryId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.SET_NULL)
],indices=[Index("memoryId"),Index("personId"),Index(value=["memoryId","personId"],unique=true)])
data class PersonMemoryPersonEntity(@PrimaryKey val id:String,val memoryId:String,val personId:String?,
    val label:String,val profileName:String="",val position:Int=0) {
    fun name(people:Map<String,String>)=personId?.let {people[it]} ?: profileName.ifBlank {label}
}
internal fun memoryPersonKey(name:String)=Normalizer.normalize(name.trim().replace(Regex("\\s+")," "),Normalizer.Form.NFC).lowercase(Locale.ROOT)
internal fun validateMemoryPeople(memories:Set<String>,people:Set<String>,rows:List<PersonMemoryPersonEntity>) {
    require(rows.map {it.id}.distinct().size==rows.size)
    rows.forEach {
        require(it.id.isNotBlank() && it.id.length<=200 && it.memoryId in memories)
        require(it.personId==null || it.personId in people) {"Perfil asociado ausente."}
        require(it.label.isNotBlank() && it.position>=0)
    }
    rows.groupBy {it.memoryId}.values.forEach {group->
        require(group.size<=200 && group.map {it.position}.distinct().size==group.size)
        val linked=group.mapNotNull {it.personId};require(linked.distinct().size==linked.size) {"Perfil duplicado en la publicación."}
    }
}
internal object PersonMemoryPeopleMigration47To48:Migration(47,48) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE person_memories ADD COLUMN peopleVisible INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE TABLE person_memory_people (id TEXT NOT NULL PRIMARY KEY,memoryId TEXT NOT NULL,personId TEXT,label TEXT NOT NULL,profileName TEXT NOT NULL,position INTEGER NOT NULL,FOREIGN KEY(memoryId) REFERENCES person_memories(id) ON DELETE CASCADE,FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX index_person_memory_people_memoryId ON person_memory_people(memoryId)")
        db.execSQL("CREATE INDEX index_person_memory_people_personId ON person_memory_people(personId)")
        db.execSQL("CREATE UNIQUE INDEX index_person_memory_people_memoryId_personId ON person_memory_people(memoryId,personId)")
    }
}
