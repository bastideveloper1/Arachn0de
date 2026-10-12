package com.r0ybt.arachn0de.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="sprint_stories",primaryKeys=["sprintId","storyId"],foreignKeys=[
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["sprintId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["storyId"],onDelete=ForeignKey.CASCADE)
],indices=[Index(value=["storyId"],unique=true)])
data class SprintStoryEntity(val sprintId:String,val storyId:String)
@Entity(tableName="objective_work",primaryKeys=["objectiveId","nodeId"],foreignKeys=[
    ForeignKey(entity=ManagementObjectiveEntity::class,parentColumns=["id"],childColumns=["objectiveId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["nodeId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("nodeId")])
data class ObjectiveWorkEntity(val objectiveId:String,val nodeId:String)
@Entity(tableName="local_identity",foreignKeys=[
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE)
],indices=[Index(value=["personId"],unique=true)])
data class LocalIdentityEntity(@PrimaryKey val slot:String="SELF",val personId:String)
@Dao interface CascadeRelationDao {
    @Query("SELECT * FROM sprint_stories ORDER BY sprintId,storyId") suspend fun stories():List<SprintStoryEntity>
    @Query("SELECT * FROM sprint_stories ORDER BY sprintId,storyId") fun observeStories():Flow<List<SprintStoryEntity>>
    @Query("SELECT * FROM objective_work ORDER BY objectiveId,nodeId") suspend fun objectives():List<ObjectiveWorkEntity>
    @Query("SELECT * FROM objective_work ORDER BY objectiveId,nodeId") fun observeObjectives():Flow<List<ObjectiveWorkEntity>>
    @Query("SELECT * FROM local_identity ORDER BY slot") suspend fun identities():List<LocalIdentityEntity>
    @Query("SELECT * FROM local_identity WHERE slot='SELF'") fun observeSelf():Flow<LocalIdentityEntity?>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:SprintStoryEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:ObjectiveWorkEntity)
    @Upsert suspend fun save(row:LocalIdentityEntity)
    @Query("DELETE FROM sprint_stories WHERE sprintId=:sprint AND storyId=:story") suspend fun removeStory(sprint:String,story:String)
    @Query("DELETE FROM objective_work WHERE objectiveId=:objective AND nodeId=:node") suspend fun removeObjective(objective:String,node:String)
    @Query("DELETE FROM local_identity") suspend fun clearIdentity()
    @Query("DELETE FROM sprint_stories") suspend fun clearStories()
    @Query("DELETE FROM objective_work") suspend fun clearObjectives()
}
