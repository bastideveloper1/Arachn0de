package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="person_profiles",foreignKeys=[ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE)])
data class PersonProfileEntity(@PrimaryKey val personId:String,val alias:String="",val description:String="",val notes:String="",val phones:String="",val emails:String="",val addresses:String="",val links:String="",val qualities:String="",val skills:String="",val interests:String="",val availability:String="") {
    fun values()=listOf(alias,description,notes,phones,emails,addresses,links,qualities,skills,interests,availability)
}
@Entity(tableName="person_relations",foreignKeys=[
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["sourceId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["targetId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("sourceId"),Index("targetId"),Index(value=["relationKey"],unique=true)])
data class PersonRelationEntity(@PrimaryKey val id:String,val sourceId:String,val targetId:String,val category:String,val label:String,val inverseLabel:String,val meaning:String,val mode:String,val relationKey:String)

@Dao interface PersonProfileDao {
    @Query("SELECT * FROM person_profiles") fun observeProfiles():Flow<List<PersonProfileEntity>>
    @Query("SELECT * FROM person_relations ORDER BY category,id") fun observeRelations():Flow<List<PersonRelationEntity>>
    @Query("SELECT * FROM person_profiles") suspend fun profiles():List<PersonProfileEntity>
    @Query("SELECT * FROM person_relations ORDER BY category,id") suspend fun relations():List<PersonRelationEntity>
    @Upsert suspend fun save(profile:PersonProfileEntity)
    @Insert suspend fun insert(relation:PersonRelationEntity)
    @Update suspend fun update(relation:PersonRelationEntity):Int
    @Query("DELETE FROM person_relations WHERE id=:id") suspend fun deleteRelation(id:String)
}
internal object PersonProfileMigration33To34:Migration(33,34) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE person_profiles (personId TEXT NOT NULL PRIMARY KEY, alias TEXT NOT NULL, description TEXT NOT NULL, notes TEXT NOT NULL, phones TEXT NOT NULL, emails TEXT NOT NULL, addresses TEXT NOT NULL, links TEXT NOT NULL, qualities TEXT NOT NULL, skills TEXT NOT NULL, interests TEXT NOT NULL, availability TEXT NOT NULL, FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE person_relations (id TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, targetId TEXT NOT NULL, category TEXT NOT NULL, label TEXT NOT NULL, inverseLabel TEXT NOT NULL, meaning TEXT NOT NULL, mode TEXT NOT NULL, relationKey TEXT NOT NULL, FOREIGN KEY(sourceId) REFERENCES persons(id) ON DELETE CASCADE, FOREIGN KEY(targetId) REFERENCES persons(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_person_relations_sourceId ON person_relations(sourceId)")
        db.execSQL("CREATE INDEX index_person_relations_targetId ON person_relations(targetId)")
        db.execSQL("CREATE UNIQUE INDEX index_person_relations_relationKey ON person_relations(relationKey)")
    }
}
