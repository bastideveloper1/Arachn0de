package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** Minute ticks within the same local day do not rescan tasks. Cards only look up their counts. */
@Composable
internal fun rememberProjectCardAlerts(tree: NodeTreeSnapshot, now: Long, zoneId: String): State<Map<String, ProjectCardAlerts>> {
    val zone = TimeZone.getTimeZone(zoneId)
    val today = CalendarDates.localDay(now, zone)
    return produceState(emptyMap(), tree, today, zoneId) {
        value = withContext(Dispatchers.Default) { projectCardAlerts(tree, today, zone) }
    }
}
