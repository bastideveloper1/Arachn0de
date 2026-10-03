package com.r0ybt.arachn0de.domain.model

/** A local person, independent of accounts or authentication. */
data class Person(val id: String, val name: String, val avatarFile: String? = null)
