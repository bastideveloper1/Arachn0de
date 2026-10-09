package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.r0ybt.arachn0de.game.GameSessionCodec
import kotlinx.coroutines.flow.Flow

/** Singleton with an empty payload as a durable 'no saved game' marker. */
@Entity(tableName = "game_state")
data class GameStateEntity(@PrimaryKey val id: Int = 1, val payload: String, val revision: Long = 0)
@Dao interface GameStateDao {
    @Query("SELECT * FROM game_state WHERE id = 1") suspend fun get(): GameStateEntity?
    @Query("SELECT * FROM game_state WHERE id = 1") fun observe(): Flow<GameStateEntity?>
    @Upsert suspend fun save(state: GameStateEntity)
}
class GameMigration22To23(private val legacy: String = "") : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE game_state (id INTEGER NOT NULL PRIMARY KEY, payload TEXT NOT NULL, revision INTEGER NOT NULL)")
        importLegacyGame(db, legacy)
    }
}
internal fun importLegacyGame(db: SupportSQLiteDatabase, legacy: String) {
    val payload = GameSessionCodec.decode(legacy)?.let(GameSessionCodec::encode) ?: ""
    db.execSQL("INSERT OR IGNORE INTO game_state(id, payload, revision) VALUES(1, ?, 0)", arrayOf(payload))
}
