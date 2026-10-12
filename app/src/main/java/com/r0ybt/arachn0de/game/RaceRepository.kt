package com.r0ybt.arachn0de.game

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.GameStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SavedRace(val session: RaceSession?, val revision: Long, val legacy: Boolean, val corrupt: Boolean)
class RaceRepository(private val database: Arachn0deDatabase) {
    fun observe() = database.gameStateDao().observe().map { row ->
        val raw=row?.payload.orEmpty(); val race=RaceCodec.decode(raw)
        SavedRace(race,row?.revision ?: 0,raw.isNotEmpty() && GameSessionCodec.decode(raw)!=null,raw.isNotEmpty() && !RaceCodec.validPayload(raw))
    }
    suspend fun start(session: RaceSession) = withContext(Dispatchers.IO) { database.withTransaction {
        RaceCodec.validate(session); val payload=RaceCodec.encode(session); require(RaceCodec.decode(payload)!=null)
        val previous=database.gameStateDao().get()?.revision ?: 0
        database.gameStateDao().save(GameStateEntity(payload=payload,revision=Math.addExact(previous,1)))
        currentCoroutineContext().ensureActive()
    } }
    suspend fun act(revision: Long, action: (RaceSession)->RaceSession): Boolean = withContext(Dispatchers.IO) { database.withTransaction {
        val row=database.gameStateDao().get() ?: return@withTransaction false
        if(row.revision!=revision) return@withTransaction false
        val session=RaceCodec.decode(row.payload) ?: return@withTransaction false
        val next=action(session); RaceCodec.validate(next)
        val payload=RaceCodec.encode(next); require(RaceCodec.decode(payload)!=null)
        database.gameStateDao().save(row.copy(payload=payload,revision=Math.addExact(revision,1)))
        currentCoroutineContext().ensureActive(); true
    } }
}
