package com.r0ybt.arachn0de.data.local

import androidx.room.*
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import com.r0ybt.arachn0de.domain.model.ProjectPhoto

/** A node owner preserves a hidden project identity. Ownerless rows are durable cleanup work. */
@Entity(tableName = "project_photos", foreignKeys = [
    ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.SET_NULL),
    ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["nodeId"], onDelete = ForeignKey.SET_NULL),
], indices = [Index(value = ["projectId"], unique = true), Index(value = ["nodeId"], unique = true), Index("file")])
data class ProjectPhotoEntity(@PrimaryKey val id: String, val projectId: String?, val nodeId: String?,
    val file: String, val photoZoom: Float = 1f, val photoX: Float = 0f, val photoY: Float = 0f)

internal fun ProjectPhotoEntity.toPhoto() = ProjectPhoto(id, file, AvatarFraming(photoZoom, photoX, photoY))

data class ProjectWithPhoto(@Embedded val project: ProjectEntity, @Embedded(prefix = "photo_") val photo: ProjectPhotoEntity?)
internal fun ProjectWithPhoto.toProject() = project.toProject().copy(photo = photo?.toPhoto())
