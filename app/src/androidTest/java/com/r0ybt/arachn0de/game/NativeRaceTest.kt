package com.r0ybt.arachn0de.game

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.arachn0de.security.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual SQLCipher and encrypted archives; isolated test storage only. */
@RunWith(AndroidJUnit4::class)
class NativeRaceTest {
    @Test fun encryptedRaceReopensRestoresPendingPrizeAndKeepsProductivity()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val root=File(app.filesDir,"native-race-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getApplicationInfo()=ApplicationInfo(app.applicationInfo).apply {dataDir=root.path}
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
            override fun getDatabasePath(name:String)=File(root,"databases/$name").apply {parentFile!!.mkdirs()}
        }
        val manager=VaultManager(context)
        try {
            manager.setup("race-native-fixture".toCharArray())
            var vault=manager.current.value!!
            val project=vault.projects.createProject("RACE_BACKUP_PRODUCTIVITY_SENTINEL")
            var race=RaceRules.begin(RaceRules.newGame(Racer.entries.toList(),setOf(0),3,452))
            race=race.copy(players=race.players.map { if(it.id==0)it.copy(tile=32,history=listOf(32),inventory=listOf(HeldRaceItem(RaceItem.EYE,1),HeldRaceItem(RaceItem.CLOAK,1))) else it },phase=RacePhase.MOVING,steps=3)
            race=RaceRules.step(race); assertEquals(RacePhase.PRIZE,race.phase)
            RaceRepository(vault.database).start(race)
            val revision=vault.database.gameStateDao().get()!!.revision
            val archive=vault.backups.create("race-portable-fixture".toCharArray())
            val bytes=SecureFiles.read(archive)
            val cipher=vault.context.getDatabasePath("arachn0de.db")
            manager.lock();manager.unlock("race-native-fixture".toCharArray());vault=manager.current.value!!
            assertEquals(race,RaceCodec.decode(vault.database.gameStateDao().get()!!.payload))
            assertFalse(String(cipher.readBytes(),Charsets.ISO_8859_1).contains("RACE_BACKUP_PRODUCTIVITY_SENTINEL"))
            assertFalse(String(bytes,Charsets.ISO_8859_1).contains("RABBIT"))
            RaceRepository(vault.database).start(RaceRules.newGame(Racer.entries.take(2),setOf(0),1,7))
            val inspection=vault.backups.inspect(bytes.inputStream(),"race-portable-fixture".toCharArray())
            vault.backups.restore(inspection);vault.backups.discard(inspection)
            assertEquals(race,RaceCodec.decode(vault.database.gameStateDao().get()!!.payload))
            assertEquals(listOf(project.id),vault.database.backupDao().projects().map {it.id})
            assertFalse(RaceRepository(vault.database).act(revision) { RaceRules.prize(it) })
            val current=vault.database.gameStateDao().get()!!.revision
            assertTrue(RaceRepository(vault.database).act(current) { RaceRules.prize(it,1) })
            val resumed=RaceCodec.decode(vault.database.gameStateDao().get()!!.payload)!!
            assertEquals(2,resumed.steps);assertEquals(2,resumed.actor.inventory[1].readyTurn)
        } finally {manager.lock();root.deleteRecursively()}
    }
}
