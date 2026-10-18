package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class MetroExpressTimingTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:MetroRepository
    @Before fun open() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context)}
    @After fun close() {db.close()}
    private suspend fun fixture():Pair<MetroNetwork,MetroRoute> {
        val net=repo.snapshot().preferences.planningNetwork
        val line=net.lines.getValue("L5")
        val stops=line.stations.indices.filter {net.stops(line.stations[it],line.id,MetroService.GREEN)}
        val pair=stops.zipWithNext().first {(a,b)->b>a+1 && line.express[a]=="V"}
        val ids=line.stations.subList(pair.first,pair.second+1)
        val route=MetroPlanner.plan(net,listOf(ids.first(),ids.last()),true,MetroRestrictions())!!
        assertTrue(route.steps.all {it.service==MetroService.GREEN && it.line=="L5"})
        return net to route
    }
    @Test fun provisionalKeepsEveryPhysicalEdgeAndNormalAndHistoricalTimesStayUnchanged()=runBlocking<Unit> {
        val (net,route)=fixture()
        assertNotNull(route.provisional)
        val skipped=route.steps.count {!net.stops(it.to,it.line,it.service)}
        assertTrue(skipped>0)
        assertEquals(route.steps.size*120_000L-skipped*15_000L,route.baseMillis)
        assertTrue(route.baseDurations.all {it>=105_000L})
        val legacy=route.copy(provisional=null,prediction=null)
        assertEquals(route.steps.size*120_000L,legacy.totalMillis)
        assertEquals(legacy,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(legacy)),net).plan)
        val normal=MetroPlanner.plan(net,route.stops,false,MetroRestrictions())!!
        assertNull(normal.provisional);assertEquals(normal.steps.size*120_000L,normal.totalMillis)
        val session=MetroTracking.start(route,MetroTime(1_000_000,100_000,1))
        val boundary=route.duration(0)
        val before=MetroTracking.position(session,MetroTime(1_000_000+boundary-1,100_000+boundary-1,1))
        val after=MetroTracking.position(session,MetroTime(1_000_000+boundary+1,100_000+boundary+1,1))
        assertEquals(0,before.step);assertEquals(1,after.step)
        assertEquals(2L,after.offset-before.offset)
        assertFalse(net.stops(route.steps[0].to,"L5",MetroService.GREEN))
    }
    @Test fun effectiveStopsProduceOneAccumulatedMeasurementWithoutServiceOrLegacyContamination()=runBlocking<Unit> {
        val (_,route)=fixture()
        val id=repo.savePlan(route)
        fun now(ms:Long)=MetroTime(10_000_000+ms,100_000+ms,1)
        suspend fun journey()=repo.snapshot().journeys.single()
        repeat(6) {i->
            val t=i*5_000_000L
            repo.begin(id,journey().row.revision,now(t))
            repo.confirmDoors(id,journey().row.revision,route.stops.first(),now(t+1_000))
            repo.confirmDoors(id,journey().row.revision,route.stops.last(),now(t+1_000+route.baseMillis-30_000),true)
        }
        val observations=repo.snapshot().observations
        assertEquals(6,observations.size);assertTrue(observations.all {it.exclusion==null && it.steps.size==route.steps.size})
        assertTrue(observations.all {it.millis==route.baseMillis-30_000})
        val adapted=MetroEstimates.adapt(route,observations)
        assertTrue(adapted.totalMillis<route.baseMillis)
        assertEquals(1,adapted.prediction!!.parts.size)
        assertEquals(6,adapted.prediction!!.parts.single().accepted)
        val legacy=route.copy(provisional=null)
        assertEquals(0,MetroLearning.estimate(legacy,0,legacy.steps.size,observations).accepted.size)
        for(service in listOf(MetroService.NORMAL,MetroService.RED)) {
            val other=route.copy(steps=route.steps.map {it.copy(service=service)})
            assertEquals(0,MetroLearning.estimate(other,0,other.steps.size,observations).accepted.size)
        }
        val reverse=route.copy(steps=route.steps.reversed().map {it.copy(from=it.to,to=it.from,direction=-it.direction)})
        assertEquals(0,MetroLearning.estimate(reverse,0,reverse.steps.size,observations).accepted.size)
        val latest=journey()
        repo.undoArrival(id,latest.row.revision,now(31_000_000))
        assertEquals("UNDONE",repo.snapshot().observations.last().exclusion)
    }
    @Test fun backupRoundTripKeepsBothModelsAndRejectsInvalidProvisionalBeforeReplacement()=runBlocking<Unit> {
        val (net,route)=fixture()
        val id=repo.savePlan(route)
        suspend fun revision()=repo.snapshot().journeys.first {it.row.id==id}.row.revision
        repo.begin(id,revision(),MetroTime(10_000_000,100_000,1))
        repo.confirmDoors(id,revision(),route.stops.first(),MetroTime(10_001_000,101_000,1))
        repo.confirmDoors(id,revision(),route.stops.last(),MetroTime(10_001_000+route.baseMillis,101_000+route.baseMillis,1),true)
        val originalEvents=repo.learningEvents()
        assertEquals(2,originalEvents.size)
        val legacy=route.copy(provisional=null,prediction=null)
        repo.savePlan(legacy)
        val before=repo.snapshot()
        val raw=MetroCodec.journey(MetroJourneyData(route))
        assertEquals(8,JSONObject(raw).getInt("version"))
        assertEquals(route,MetroCodec.journey(raw,net).plan)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val file=backup.create();val inspected=backup.inspect(file.inputStream())
        try {backup.restore(inspected)} finally {backup.discard(inspected)}
        assertEquals(originalEvents,repo.learningEvents())
        assertEquals(before.journeys.map {it.data.plan}.toSet(),repo.snapshot().journeys.map {it.data.plan}.toSet())
        val saved=backup.snapshot()
        val invalid=JSONObject(raw).apply {getJSONObject("plan").getJSONObject("expressTiming").getJSONArray("baseMillis").put(0,0)}.toString()
        assertTrue(runCatching {backup.restore(saved.copy(metroJourneys=saved.metroJourneys.mapIndexed {i,r->if(i==0) r.copy(payload=invalid) else r}))}.isFailure)
        assertEquals(saved.metroJourneys,backup.snapshot().metroJourneys)
        assertTrue(runCatching {MetroCodec.journey(JSONObject(raw).put("version",7).toString(),net)}.isFailure)
    }
}
