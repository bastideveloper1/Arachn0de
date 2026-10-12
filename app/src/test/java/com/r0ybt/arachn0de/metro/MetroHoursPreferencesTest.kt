package com.r0ybt.arachn0de.metro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.security.*
import java.io.File
import java.util.UUID
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class MetroHoursPreferencesTest {
    @Test fun isolatedPreferencesPersistRestoreAndRejectInvalidBackupsWithoutChangingFormat()=runBlocking<Unit> {
        val base=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(base,Arachn0deDatabase::class.java).build()
        val secondaryDb=Room.inMemoryDatabaseBuilder(base,Arachn0deDatabase::class.java).build()
        val root=File(base.filesDir,"hours-test-${UUID.randomUUID()}").apply {mkdirs()}
        val access=SecureFiles.Session(UUID.randomUUID(),File(root,"main"),ByteArray(32))
        val other=SecureFiles.Session(UUID.randomUUID(),File(root,"secondary"),ByteArray(32) {1})
        fun context(session:SecureFiles.Session,database:Arachn0deDatabase)=VaultContext(base,session).apply {this.database=database}
        val primary=context(access,db);val secondary=context(other,secondaryDb).apply {this.primary=false}
        try {
            val prefs=primary.getSharedPreferences(MetroHoursConfig.NAME,0)
            assertEquals(MetroHoursConfig(),MetroHoursConfig.read(prefs))
            val custom=MetroHoursConfig(MetroHoursPeriod(420,1320),MetroHoursPeriod(480,1350),MetroHoursPeriod(540,1290))
            assertTrue(prefs.edit().putString("hours",custom.encode()).commit())
            assertEquals(custom,MetroHoursConfig.read(context(access,db).getSharedPreferences(MetroHoursConfig.NAME,0)))
            assertEquals(MetroHoursConfig(),MetroHoursConfig.read(secondary.getSharedPreferences(MetroHoursConfig.NAME,0)))
            val friday=LocalDateTime.parse("2026-10-09T22:10").atZone(ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
            assertTrue(MetroOperatingHours.warnings(friday,10).isEmpty())
            assertEquals(listOf(MetroOperatingHours.Warning.OUTSIDE),MetroOperatingHours.warnings(friday,10,custom))
            val holiday=LocalDateTime.parse("2026-10-12T08:00").atZone(ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
            assertEquals(listOf(MetroOperatingHours.Warning.OUTSIDE),MetroOperatingHours.warnings(holiday,10,custom))
            val data=BackupFixture.empty().copy(privatePreferences=db.privatePreferenceDao().all())
            val encoded=BackupJson.encode(data)
            assertEquals(18,org.json.JSONObject(String(encoded)).getInt("dataVersion"))
            val repo=BackupRepository(db,base,BackupAvatarFiles(base,BackupFixture::syncDirectory))
            assertTrue(prefs.edit().putString("hours",MetroHoursConfig().encode()).commit())
            repo.restore(BackupJson.decode(encoded));primary.reloadPreferences()
            assertEquals(custom,MetroHoursConfig.read(prefs))
            assertTrue(secondaryDb.privatePreferenceDao().all().isEmpty())
            val invalid=data.copy(privatePreferences=listOf(PrivatePreferenceEntity(MetroHoursConfig.NAME,EncryptedPreferences.encode(mapOf("hours" to "1380,360,390,1380,450,1380")))))
            assertTrue(runCatching {repo.restore(invalid)}.isFailure)
            assertEquals(custom,MetroHoursConfig.read(prefs))
            repo.restore(BackupFixture.empty());primary.reloadPreferences()
            assertEquals(MetroHoursConfig(),MetroHoursConfig.read(prefs))
        } finally {primary.clearMemory();secondary.clearMemory();access.close();other.close();db.close();secondaryDb.close();root.deleteRecursively()}
    }
    @Test fun boundsAndSameDayIntervalsAreValidated() {
        for(text in listOf("-1,1380,390,1380,450,1380","360,1440,390,1380,450,1380","360,360,390,1380,450,1380","1,2","bad")) {
            assertTrue(runCatching {MetroHoursConfig.decode(text)}.isFailure)
        }
    }
}
