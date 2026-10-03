package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.r0ybt.arachn0de.domain.model.CalendarSnapshot
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** Group only on persisted-tree or zone changes; clock ticks only update row presentation. */
@Composable
internal fun rememberCalendar(tree: NodeTreeSnapshot, zoneId: String): State<CalendarSnapshot?> =
    produceState<CalendarSnapshot?>(null, tree, zoneId) {
        value = withContext(Dispatchers.Default) { CalendarSnapshot(tree, TimeZone.getTimeZone(zoneId)) }
    }
