package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName="person_memories",foreignKeys=[ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE)],indices=[Index("personId")])
data class PersonMemoryEntity(@PrimaryKey val id:String,val personId:String,val date:String?,val description:String,val position:Int,@ColumnInfo(defaultValue="0") val isPinned:Boolean=false)
@Entity(tableName="person_memory_images",foreignKeys=[
    ForeignKey(entity=PersonMemoryEntity::class,parentColumns=["id"],childColumns=["memoryId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=AttachmentFileEntity::class,parentColumns=["id"],childColumns=["attachmentId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=PersonPhotoEntity::class,parentColumns=["id"],childColumns=["legacyPhotoId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("memoryId"),Index("attachmentId"),Index("legacyPhotoId")])
data class PersonMemoryImageEntity(@PrimaryKey val id:String,val memoryId:String,val attachmentId:String?,val legacyPhotoId:String?,val position:Int,val date:String?=null,val description:String="")
@Dao interface PersonMemoryDao {
    @Query("SELECT * FROM person_memories ORDER BY isPinned DESC,position,id") fun observeMemories():Flow<List<PersonMemoryEntity>>
    @Query("SELECT * FROM person_memory_images ORDER BY position,id") fun observeImages():Flow<List<PersonMemoryImageEntity>>
    @Query("SELECT * FROM person_memories ORDER BY isPinned DESC,position,id") suspend fun memories():List<PersonMemoryEntity>
    @Query("SELECT * FROM person_memory_images ORDER BY position,id") suspend fun images():List<PersonMemoryImageEntity>
    @Upsert suspend fun save(row:PersonMemoryEntity)
    @Upsert suspend fun images(rows:List<PersonMemoryImageEntity>)
    @Query("DELETE FROM person_memory_images WHERE memoryId=:id") suspend fun clearImages(id:String)
    @Query("DELETE FROM person_memories") suspend fun clear()
    @Query("DELETE FROM person_memories WHERE personId=:id") suspend fun removeOwner(id:String)
    @Query("DELETE FROM person_memories WHERE id=:id") suspend fun remove(id:String)
    @Query("DELETE FROM person_memory_images WHERE legacyPhotoId=:id") suspend fun removeLegacy(id:String)
    @Query("DELETE FROM person_memories WHERE NOT EXISTS(SELECT 1 FROM person_memory_images WHERE memoryId=person_memories.id)") suspend fun removeEmpty()
    @Query("INSERT OR IGNORE INTO person_memories(id,personId,date,description,position) SELECT id,personId,NULL,'',position FROM person_photos WHERE NOT EXISTS(SELECT 1 FROM person_memory_images WHERE legacyPhotoId=person_photos.id)") suspend fun legacyMemories()
    @Query("INSERT OR IGNORE INTO person_memory_images(id,memoryId,attachmentId,legacyPhotoId,position,date,description) SELECT id,id,NULL,id,0,NULL,'' FROM person_photos WHERE NOT EXISTS(SELECT 1 FROM person_memory_images WHERE legacyPhotoId=person_photos.id)") suspend fun legacyImages()
}
internal fun validatePersonMemories(people:Set<String>,memories:List<PersonMemoryEntity>,images:List<PersonMemoryImageEntity>,files:List<AttachmentFileEntity>,legacy:List<PersonPhotoEntity>) {
    fun date(value:String?) {if(value!=null) {val d=LocalDate.parse(value);require(d.year in 1..9999 && d.toString()==value)}}
    require(memories.map {it.id}.toSet().size==memories.size && images.map {it.id}.toSet().size==images.size)
    val grouped=images.groupBy {it.memoryId}
    val owners=memories.associateBy {it.id};val attachments=files.associateBy {it.id};val old=legacy.associateBy {it.id}
    memories.forEach {require(it.id.isNotBlank() && it.id.length<=200 && it.personId in people && it.position>=0);require(it.description.length<=16000);date(it.date);require(grouped[it.id].orEmpty().isNotEmpty()) {"Entrada fotográfica vacía."}}
    images.forEach {image->
        val owner=requireNotNull(owners[image.memoryId]);require(image.id.isNotBlank() && image.id.length<=200 && image.position>=0 && image.description.length<=16000);date(image.date)
        require((image.attachmentId!=null)!=(image.legacyPhotoId!=null))
        image.attachmentId?.let {require(attachments[it]?.lifecycleState=="READY") {"Fotografía original ausente."}}
        image.legacyPhotoId?.let {require(old[it]?.personId==owner.personId) {"Fotografía histórica de otra Persona."}}
    }
    grouped.values.forEach {rows->require(rows.map {it.position}.distinct().size==rows.size);require(rows.map {it.attachmentId ?: "legacy:${it.legacyPhotoId}"}.distinct().size==rows.size)}
}
internal object PersonMemoryMigration39To40:Migration(39,40) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE person_memories (id TEXT NOT NULL PRIMARY KEY,personId TEXT NOT NULL,date TEXT,description TEXT NOT NULL,position INTEGER NOT NULL,FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_person_memories_personId ON person_memories(personId)")
        db.execSQL("CREATE TABLE person_memory_images (id TEXT NOT NULL PRIMARY KEY,memoryId TEXT NOT NULL,attachmentId TEXT,legacyPhotoId TEXT,position INTEGER NOT NULL,date TEXT,description TEXT NOT NULL,FOREIGN KEY(memoryId) REFERENCES person_memories(id) ON DELETE CASCADE,FOREIGN KEY(attachmentId) REFERENCES attachment_files(id) ON DELETE RESTRICT,FOREIGN KEY(legacyPhotoId) REFERENCES person_photos(id) ON DELETE RESTRICT)")
        listOf("memoryId","attachmentId","legacyPhotoId").forEach {db.execSQL("CREATE INDEX index_person_memory_images_$it ON person_memory_images($it)")}
        db.execSQL("INSERT INTO person_memories SELECT id,personId,NULL,'',position FROM person_photos")
        db.execSQL("INSERT INTO person_memory_images SELECT id,id,NULL,id,0,NULL,'' FROM person_photos")
    }
}

internal object PersonMemoryPinMigration42To43:Migration(42,43) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE person_memories ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}
