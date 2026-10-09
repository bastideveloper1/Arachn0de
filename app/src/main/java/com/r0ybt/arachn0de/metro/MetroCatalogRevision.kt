package com.r0ybt.arachn0de.metro

import java.security.MessageDigest

/**
 * A reference-data correction, never a rewrite of saved preferences, routes or sessions.
 * Only the two exact known network definitions participate. Unknown snapshots are not guessed.
 * Retain both definitions: v16 backups can contain old and corrected journeys together.
 */
internal object MetroCatalogRevision {
    const val ORIGINAL = "santiago-2024-02-beta1"
    const val CORRECTED = "santiago-2024-02-beta1-r1"
    private const val ORIGINAL_HASH = "4fd8d59773c4e9a022ac5880e0e01bd67e052ae0d8ca27508692f5424db9922c"
    private const val CORRECTED_HASH = "7f5a0c9ee705a692c48ba0b1862f80b70dc8f28f7a619108ee848034639f8c5a"
    private val originalClasses = mapOf("rodrigo-de-araya" to "V", "carlos-valdovinos" to "R", "camino-agricola" to "V", "san-joaquin" to "R")
    private val correctedClasses = mapOf("rodrigo-de-araya" to "R", "carlos-valdovinos" to "V", "camino-agricola" to "R", "san-joaquin" to "C")

    private fun fingerprint(net: MetroNetwork): String {
        val rows = net.stations.toSortedMap().values.map { "station:${it.id}=${it.name}" } +
            net.lines.toSortedMap().values.map { "line:${it.id}=${(it.color and 0xffffff).toString(16).uppercase().padStart(6,'0')}|${it.stations.joinToString(",")}|${it.express.joinToString(",")}" }
        return MessageDigest.getInstance("SHA-256").digest(rows.joinToString("\n").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private fun replace(net: MetroNetwork, version: String, classes: Map<String,String>): MetroNetwork {
        val line = net.lines.getValue("L5")
        val revised = line.copy(express = line.stations.mapIndexed { i,id -> classes[id] ?: line.express[i] })
        return net.copy(version = version, lines = net.lines + (line.id to revised))
    }
    fun forPlanning(net: MetroNetwork): MetroNetwork =
        if (net.version == ORIGINAL && fingerprint(net) == ORIGINAL_HASH) replace(net,CORRECTED,correctedClasses) else net

    /** Each historical route is validated against its own revision, including its old stops. */
    fun forRoute(snapshot: MetroNetwork, version: String): MetroNetwork {
        if (version == snapshot.version) return snapshot
        val revised = when {
            snapshot.version == ORIGINAL && version == CORRECTED && fingerprint(snapshot) == ORIGINAL_HASH -> replace(snapshot,CORRECTED,correctedClasses)
            snapshot.version == CORRECTED && version == ORIGINAL && fingerprint(snapshot) == CORRECTED_HASH -> replace(snapshot,ORIGINAL,originalClasses)
            else -> throw IllegalArgumentException("Revisión de catálogo Metro desconocida: $version")
        }
        return revised
    }
}
