package com.r0ybt.arachn0de.domain.model

/** Persisted instants are UTC epoch milliseconds. Events have no inferred actor. */
enum class NodeEventType { CREATED, COMPLETED, REOPENED }
data class NodeEvent(val id: String, val nodeId: String, val type: NodeEventType, val occurredAt: Long)
