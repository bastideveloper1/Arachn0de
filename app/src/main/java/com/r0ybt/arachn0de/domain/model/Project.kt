package com.r0ybt.arachn0de.domain.model

/** Timestamps are milliseconds since the Unix epoch (UTC). */
data class Project(
    val id: String,
    val name: String,
    val description: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val photo: ProjectPhoto? = null,
) {
    constructor(id: String, name: String, description: String, createdAt: Long, updatedAt: Long) :
        this(id, name, description, 0, createdAt, updatedAt)
}
