package com.r0ybt.arachn0de.metro

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class MetroPlanCalculation(val route:MetroRoute?,val ready:Boolean)
private data class MetroScheduledRequest(val network:MetroNetwork,val stops:List<String>?,val departure:Long,val restrictions:MetroRestrictions)
/** Time-expanded search runs off the UI thread. A previous input's result is never actionable. */
@Composable internal fun scheduledMetroPlan(network:MetroNetwork,stops:List<String>?,departure:Long,restrictions:MetroRestrictions):MetroPlanCalculation {
    val request=MetroScheduledRequest(network,stops,departure,restrictions)
    val result by produceState<Pair<MetroScheduledRequest,MetroRoute?>?>(null,request) {
        val route=withContext(Dispatchers.Default) {stops?.let {MetroPlanner.planAt(network,it,departure,restrictions)}}
        value=request to route
    }
    val current=result?.takeIf {it.first==request}
    return MetroPlanCalculation(current?.second,current!=null)
}
