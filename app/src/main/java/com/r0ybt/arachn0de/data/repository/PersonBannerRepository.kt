package com.r0ybt.arachn0de.data.repository

import com.r0ybt.arachn0de.data.local.*

/** Banner originals use the same reservations, encryption and reference counting as gallery images. */
internal class PersonBannerRepository(private val db:Arachn0deDatabase,private val attachments:AttachmentRepository) {
    fun observe()=db.personProfileDao().observeBanners()
    suspend fun save(row:PersonBannerEntity,draft:String)=attachments.saveGalleryDraft(draft,setOf(row.attachmentId)) {
        validatePersonBanners(db.backupDao().persons().mapTo(hashSetOf()) {it.id},db.attachmentDao().files(),listOf(row))
        db.personProfileDao().save(row)
    }
    suspend fun remove(person:String)=attachments.saveGalleryDraft("banner-remove:$person",emptySet()) {db.personProfileDao().removeBanner(person)}
}
