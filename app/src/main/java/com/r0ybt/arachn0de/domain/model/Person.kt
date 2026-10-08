package com.r0ybt.arachn0de.domain.model

/** A local person, independent of accounts or authentication. */
data class Person(val id: String, val name: String, val avatarFile: String? = null, val avatarZoom: Float = 1f, val avatarX: Float = 0f, val avatarY: Float = 0f)
