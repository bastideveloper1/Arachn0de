package com.r0ybt.arachn0de.data.local

import androidx.room.withTransaction

/** Stable existing SELF identity; select by insertion once, never by a visible sort order. */
internal suspend fun ensurePrincipalProfile(db:Arachn0deDatabase):String?=db.withTransaction {
    val current=db.cascadeRelationDao().identities().firstOrNull {it.slot=="SELF"}?.personId
    if(current!=null) return@withTransaction current
    db.personDao().firstInsertedId()?.also {db.cascadeRelationDao().save(LocalIdentityEntity(personId=it))}
}
