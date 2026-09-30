package com.r0ybt.arachn0de.domain.model

/** Timestamps are milliseconds since the Unix epoch (UTC). */
data class Project(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
)
