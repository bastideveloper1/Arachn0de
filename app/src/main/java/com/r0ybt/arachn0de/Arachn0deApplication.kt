package com.r0ybt.arachn0de

import android.app.Application
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository

/** Process-scoped dependencies. An Activity never closes the shared database. */
class Arachn0deApplication : Application() {
    val database by lazy { Arachn0deDatabase.create(this) }
    val projectRepository by lazy { ProjectRepository(database.projectDao()) }
    val personRepository by lazy { com.r0ybt.arachn0de.data.repository.PersonRepository(database, com.r0ybt.arachn0de.data.local.AvatarStore(this)) }
    internal val backupRepository by lazy { com.r0ybt.arachn0de.backup.BackupRepository(database, this) }
    val nodeRepository by lazy { NodeRepository(database) }
}
