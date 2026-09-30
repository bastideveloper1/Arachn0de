package com.r0ybt.arachn0de.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = true)
abstract class Arachn0deDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        /** The application owner should retain one instance and close it when no longer needed. */
        fun create(context: Context): Arachn0deDatabase = Room.databaseBuilder(
            context.applicationContext,
            Arachn0deDatabase::class.java,
            "arachn0de.db",
        ).build()
    }
}
