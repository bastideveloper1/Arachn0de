package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName="person_banners",foreignKeys=[
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=AttachmentFileEntity::class,parentColumns=["id"],childColumns=["attachmentId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("attachmentId")])
data class PersonBannerEntity(@PrimaryKey val personId:String,val attachmentId:String,val zoom:Float=1f,val x:Float=0f,val y:Float=0f)

internal fun validatePersonBanners(people:Set<String>,files:List<AttachmentFileEntity>,rows:List<PersonBannerEntity>) {
    require(rows.map {it.personId}.distinct().size==rows.size)
    val byId=files.associateBy {it.id}
    rows.forEach {
        require(it.personId in people)
        val file=requireNotNull(byId[it.attachmentId]) {"Original del banner ausente."}
        require(file.lifecycleState=="READY" && file.mimeType.startsWith("image/"))
        com.r0ybt.arachn0de.domain.model.AvatarFraming(it.zoom,it.x,it.y).validate()
    }
}
internal object PersonBannerMigration51To52:Migration(51,52) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE person_memory_images ADD COLUMN zoom REAL NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE person_memory_images ADD COLUMN x REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE person_memory_images ADD COLUMN y REAL NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE person_banners(personId TEXT NOT NULL PRIMARY KEY,attachmentId TEXT NOT NULL,zoom REAL NOT NULL,x REAL NOT NULL,y REAL NOT NULL,FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE,FOREIGN KEY(attachmentId) REFERENCES attachment_files(id) ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_person_banners_attachmentId ON person_banners(attachmentId)")
    }
}
