package com.r0ybt.arachn0de.data.local

import androidx.room.*

@Entity(tableName="creation_defaults",foreignKeys=[
    ForeignKey(entity=ProjectEntity::class,parentColumns=["id"],childColumns=["projectId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["projectId","id"],childColumns=["projectId","nodeId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("projectId"),Index(value=["projectId","nodeId"])])
data class CreationDefaultsEntity(@PrimaryKey val id:String, val projectId:String?, val nodeId:String?,
    val purpose:String?=null,val obligation:Boolean?=null,val currency:String?=null,val priority:String?=null,
    val tagsOverride:Boolean=false,val peopleOverride:Boolean=false,
    val startRule:String?=null,val startNumber:Int?=null,val startMinute:Int?=null,
    val dueRule:String?=null,val dueNumber:Int?=null,val dueMinute:Int?=null)
@Entity(tableName="creation_defaults_tag",primaryKeys=["defaultsId","tagId"],foreignKeys=[
    ForeignKey(entity=CreationDefaultsEntity::class,parentColumns=["id"],childColumns=["defaultsId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=TagEntity::class,parentColumns=["id"],childColumns=["tagId"],onDelete=ForeignKey.CASCADE)],indices=[Index("defaultsId"),Index("tagId")])
data class CreationDefaultsTagEntity(val defaultsId:String,val tagId:String)
@Entity(tableName="creation_defaults_person",primaryKeys=["defaultsId","personId"],foreignKeys=[
    ForeignKey(entity=CreationDefaultsEntity::class,parentColumns=["id"],childColumns=["defaultsId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE)],indices=[Index("defaultsId"),Index("personId")])
data class CreationDefaultsPersonEntity(val defaultsId:String,val personId:String)
@Dao interface CreationDefaultsDao {
    @Query("SELECT * FROM creation_defaults WHERE projectId IS NULL OR projectId=:projectId") suspend fun forProject(projectId:String):List<CreationDefaultsEntity>
    @Query("SELECT * FROM creation_defaults ORDER BY id") suspend fun all():List<CreationDefaultsEntity>
    @Query("SELECT * FROM creation_defaults_tag") suspend fun tags():List<CreationDefaultsTagEntity>
    @Query("SELECT * FROM creation_defaults_person") suspend fun people():List<CreationDefaultsPersonEntity>
    @Query("SELECT t.* FROM creation_defaults_tag t JOIN creation_defaults d ON d.id=t.defaultsId WHERE d.projectId IS NULL OR d.projectId=:projectId") suspend fun tagsForProject(projectId:String):List<CreationDefaultsTagEntity>
    @Query("SELECT p.* FROM creation_defaults_person p JOIN creation_defaults d ON d.id=p.defaultsId WHERE d.projectId IS NULL OR d.projectId=:projectId") suspend fun peopleForProject(projectId:String):List<CreationDefaultsPersonEntity>
    @Upsert suspend fun save(row:CreationDefaultsEntity)
    @Insert suspend fun insertTags(rows:List<CreationDefaultsTagEntity>)
    @Insert suspend fun insertPeople(rows:List<CreationDefaultsPersonEntity>)
    @Query("DELETE FROM creation_defaults_tag WHERE defaultsId=:id") suspend fun clearTags(id:String)
    @Query("DELETE FROM creation_defaults_person WHERE defaultsId=:id") suspend fun clearPeople(id:String)
    @Query("DELETE FROM creation_defaults WHERE id=:id") suspend fun delete(id:String)
    @Query("DELETE FROM creation_defaults") suspend fun clear()
}
