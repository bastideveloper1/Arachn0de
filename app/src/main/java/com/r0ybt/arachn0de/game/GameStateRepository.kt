package com.r0ybt.arachn0de.game

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SavedGame(val session: GameSession?, val revision: Long)
class GameStateRepository(private val database: Arachn0deDatabase) {
    fun observe() = database.gameStateDao().observe().map { row -> SavedGame(row?.payload?.let(GameSessionCodec::decode), row?.revision ?: 0) }
    suspend fun load(): SavedGame = withContext(Dispatchers.IO) {
        val row = database.gameStateDao().get(); SavedGame(row?.payload?.let(GameSessionCodec::decode), row?.revision ?: 0)
    }
    suspend fun start(session: GameSession) = withContext(Dispatchers.IO) { database.withTransaction {
        val payload = GameSessionCodec.encode(session); require(GameSessionCodec.decode(payload) != null)
        val previous = database.gameStateDao().get()?.revision ?: 0
        database.gameStateDao().save(GameStateEntity(payload = payload, revision = Math.addExact(previous, 1)))
    } }
    /** Compare-and-set covers stale animation callbacks, double taps, timers and bots together. */
    suspend fun act(revision: Long, action: (GameSession) -> GameSession): Boolean = withContext(Dispatchers.IO) { database.withTransaction {
        val row = database.gameStateDao().get() ?: return@withTransaction false
        if (row.revision != revision) return@withTransaction false
        val session = GameSessionCodec.decode(row.payload) ?: return@withTransaction false
        val next = try { action(session) } catch (_: IllegalArgumentException) { return@withTransaction false }
        val payload = GameSessionCodec.encode(next); require(GameSessionCodec.decode(payload) != null) { "Estado de partida inválido." }
        database.gameStateDao().save(row.copy(payload = payload, revision = Math.addExact(revision, 1)))
        currentCoroutineContext().ensureActive(); true
    } }
}
