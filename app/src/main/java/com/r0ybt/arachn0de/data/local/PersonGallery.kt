package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.Period

@Entity(tableName="person_birthdays",foreignKeys=[ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE)])
data class PersonBirthdayEntity(@PrimaryKey val personId:String,val date:String)
@Entity(tableName="person_photos",foreignKeys=[ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE)],indices=[Index("personId")])
data class PersonPhotoEntity(@PrimaryKey val id:String,val personId:String,val file:String,val position:Int,val zoom:Float=1f,val x:Float=0f,val y:Float=0f)
@Dao interface PersonGalleryDao {
    @Query("SELECT * FROM person_photos ORDER BY position,id") fun observePhotos():Flow<List<PersonPhotoEntity>>
    @Query("SELECT * FROM person_photos ORDER BY position,id") suspend fun photos():List<PersonPhotoEntity>
    @Query("SELECT * FROM person_birthdays") fun observeBirthdays():Flow<List<PersonBirthdayEntity>>
    @Query("SELECT * FROM person_birthdays") suspend fun birthdays():List<PersonBirthdayEntity>
    @Upsert suspend fun save(photo:PersonPhotoEntity)
    @Upsert suspend fun save(birthday:PersonBirthdayEntity)
    @Query("DELETE FROM person_photos WHERE id=:id") suspend fun remove(id:String)
    @Query("DELETE FROM person_birthdays WHERE personId=:id") suspend fun removeBirthday(id:String)
}
internal fun validatePersonGallery(people:Set<String>,photos:List<PersonPhotoEntity>,birthdays:List<PersonBirthdayEntity>) {
    require(photos.map {it.id}.toSet().size==photos.size)
    require(birthdays.map {it.personId}.toSet().size==birthdays.size)
    birthdays.forEach {require(it.personId in people);val date=LocalDate.parse(it.date);require(date.toString()==it.date && date.year in 1..9999)}
    photos.forEach {require(it.id.isNotBlank() && it.id.length<=200 && it.personId in people && it.position in 0..3 && Regex("[a-f0-9-]{36}\\.png").matches(it.file));com.r0ybt.arachn0de.domain.model.AvatarFraming(it.zoom,it.x,it.y).validate()}
    photos.groupBy {it.personId}.values.forEach {require(it.size<=4 && it.map {p->p.position}.toSet().size==it.size)}
}
internal fun personAge(date:String,today:LocalDate=LocalDate.now()):Int? = runCatching {LocalDate.parse(date).takeIf {!it.isAfter(today)}?.let {Period.between(it,today).years}}.getOrNull()
internal object PersonGalleryMigration35To36:Migration(35,36) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE person_birthdays (personId TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE person_photos (id TEXT NOT NULL PRIMARY KEY, personId TEXT NOT NULL, file TEXT NOT NULL, position INTEGER NOT NULL, zoom REAL NOT NULL, x REAL NOT NULL, y REAL NOT NULL, FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_person_photos_personId ON person_photos(personId)")
    }
}
