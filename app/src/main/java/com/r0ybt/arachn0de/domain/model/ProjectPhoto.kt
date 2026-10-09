package com.r0ybt.arachn0de.domain.model

/** Portable private filename and normalized framing, shared by every project identity view. */
data class ProjectPhoto(val id: String, val file: String, val framing: AvatarFraming = AvatarFraming())
