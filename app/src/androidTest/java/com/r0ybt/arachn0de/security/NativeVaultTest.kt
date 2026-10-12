package com.r0ybt.arachn0de.security

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only: no plaintext Room replacement or simulated SQLCipher engine. */
@RunWith(AndroidJUnit4::class)
class NativeVaultTest {
    private fun isolated():Pair<Context,File> {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val root=File(app.filesDir,"instrumented-security-${UUID.randomUUID()}").apply {mkdirs()}
        return object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getApplicationInfo()=ApplicationInfo(app.applicationInfo).apply {dataDir=root.path}
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
            override fun getDatabasePath(name:String)=File(root,"databases/$name").apply {parentFile!!.mkdirs()}
            override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?)=
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?,errorHandler:android.database.DatabaseErrorHandler?)=
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory,errorHandler)
            override fun deleteDatabase(name:String)=android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(name))
        } to root
    }
    @Test fun nativePagesExportMainMigrationAndIndependentSecondary()=runBlocking {
        val (context,root)=isolated();val manager=VaultManager(context)
        try {
            val original=Arachn0deDatabase.create(context)
            val project=ProjectRepository(original.projectDao(),original).createProject("NATIVE_PRIVATE_SENTINEL")
            val originalFile=File(context.filesDir,"test-private-image.bin").apply {writeBytes(byteArrayOf(1,2,3,4,5))}
            original.close()
            manager.setup("principal-native-123".toCharArray())
            val main=manager.current.value!!
            assertTrue(main.primary)
            assertEquals(listOf(project.id),main.database.backupDao().projects().map {it.id})
            assertFalse(originalFile.exists())
            assertArrayEquals(byteArrayOf(1,2,3,4,5),SecureFiles.read(File(main.context.filesDir,originalFile.name)))
            val cipher=main.context.getDatabasePath("arachn0de.db")
            assertFalse(String(cipher.readBytes(),Charsets.ISO_8859_1).contains("NATIVE_PRIVATE_SENTINEL"))
            assertFalse(cipher.inputStream().use {String(ByteArray(16).also {header->java.io.DataInputStream(it).readFully(header)},Charsets.US_ASCII)}.startsWith("SQLite format 3"))
            manager.createSecondary("principal-native-123".toCharArray(),"secondary-native-123".toCharArray())
            manager.lock()
            assertTrue(runCatching {android.database.sqlite.SQLiteDatabase.openDatabase(cipher.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {it.rawQuery("SELECT * FROM projects",null).use { c->c.moveToFirst() }}}.isFailure)
            manager.unlock("secondary-native-123".toCharArray())
            assertTrue(manager.current.value!!.database.backupDao().projects().isEmpty())
            manager.current.value!!.projects.createProject("Only secondary")
            manager.lock();manager.unlock("principal-native-123".toCharArray())
            assertEquals(listOf(project.id),manager.current.value!!.database.backupDao().projects().map {it.id})
            manager.lock()
            val recovery=java.io.ByteArrayOutputStream()
            manager.exportInitialRecovery("principal-native-123".toCharArray(),"portable-native-123".toCharArray(),recovery)
            manager.unlock("principal-native-123".toCharArray())
            val data=manager.current.value!!.backups.inspect(java.io.ByteArrayInputStream(recovery.toByteArray()),"portable-native-123".toCharArray())
            assertEquals(listOf(project.id),data.projects.map {it.id})
            manager.current.value!!.backups.restore(data)
            manager.current.value!!.backups.discard(data)
            assertEquals(listOf(project.id),manager.current.value!!.database.backupDao().projects().map {it.id})
        } finally {manager.lock();root.deleteRecursively()}
    }
    @Test fun encryptedVersion26UpgradePreservesBothStoreIdentitiesAndNewPhotosBackups()=runBlocking {
        val (context,root)=isolated();val manager=VaultManager(context)
        try {
        val primaryFixture=HistoricalRoom26Fixture.create(context,"main-upgrade-fixture",true)
        val secondaryFixture=HistoricalRoom26Fixture.create(context,"decoy-upgrade-fixture",false)
        File(context.filesDir,"security-v1/index").writeText(org.json.JSONArray(listOf(primaryFixture.id.toString(),secondaryFixture.id.toString())).toString())
        fun preserved(session:VaultSession,fixture:HistoricalRoom26Fixture.Store) {
            assertEquals(fixture.id,session.access.id)
            val sql=session.database.openHelper.readableDatabase
            assertEquals(38,sql.version)
            assertEquals(fixture.rows,fixture.columns.mapValues {(table,fields)->HistoricalRoom26Fixture.rows(sql.query(HistoricalRoom26Fixture.select(table,fields)))})
            sql.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
            sql.query("PRAGMA integrity_check").use {assertTrue(it.moveToFirst());assertEquals("ok",it.getString(0));assertFalse(it.moveToNext())}
            for(table in listOf("person_photos","person_birthdays","person_technologies","person_profiles","person_relations","sprint_links","management_contexts","project_charters","management_objectives","acceptance_criteria","management_roles","objective_epics","epic_stories","story_tasks","backlog_entries","management_participants","participant_roles","objective_responsibilities","node_responsibilities","iteration_plans","iteration_cycles","iteration_commitments","iteration_closures"))
                sql.query("SELECT COUNT(*) FROM `$table`").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
            assertArrayEquals(fixture.image,SecureFiles.read(File(session.context.filesDir,"avatars/${HistoricalRoom26Fixture.AVATAR}")))
            assertArrayEquals(fixture.image,SecureFiles.read(File(session.context.filesDir,"technology-icons/${HistoricalRoom26Fixture.ICON}")))
        }
            manager.unlock("main-upgrade-fixture".toCharArray())
            val reopened=manager.current.value!!;assertTrue(reopened.primary);preserved(reopened,primaryFixture)
            val mainProjectId=HistoricalRoom26Fixture.PROJECT
            assertEquals("MAIN_UPGRADE_SENTINEL",reopened.projects.getProject(mainProjectId)!!.name)
            val preference=reopened.database.nodeSortPreferenceDao().all().single();assertFalse(preference.layersFirst);assertEquals("DUE_PRIORITY",preference.mode)
            reopened.database.nodeSortPreferenceDao().save(preference.copy(layersFirst=true))
            val bitmap=android.graphics.Bitmap.createBitmap(1800,900,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.GREEN)}
            val source=File(context.cacheDir,"upgrade-source.png");source.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            val photo=reopened.photos.import(android.net.Uri.fromFile(source),"upgrade-editor")
            val framing=com.r0ybt.arachn0de.domain.model.AvatarFraming(3f,.4f,-.2f)
            reopened.photos.save(mainProjectId,photo,framing,"upgrade-editor")
            val bytes=SecureFiles.read(File(reopened.context.filesDir,"project-photos/$photo"))
            val thumbnail=com.r0ybt.arachn0de.data.local.AvatarStore(reopened.context,"project-photos").readThumbnail(photo,240)!!
            assertEquals(240,thumbnail.width)
            val cached=File(reopened.context.cacheDir,"project-thumbnails").listFiles()!!.first {it.name.endsWith(".png")}
            assertFalse(cached.readBytes().take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
            val exported=reopened.backups.create("portable-upgrade-fixture".toCharArray())
            val inspection=reopened.backups.inspect(SecureFiles.input(exported),"portable-upgrade-fixture".toCharArray())
            reopened.backups.restore(inspection);reopened.backups.discard(inspection)
            val restored=reopened.projects.getProject(mainProjectId)!!.photo!!
            assertEquals(framing,restored.framing);assertArrayEquals(bytes,SecureFiles.read(File(reopened.context.filesDir,"project-photos/${restored.file}")))
            assertTrue(reopened.database.nodeSortPreferenceDao().all().single().layersFirst)
            manager.lock();manager.unlock("decoy-upgrade-fixture".toCharArray())
            val decoy=manager.current.value!!;assertFalse(decoy.primary);preserved(decoy,secondaryFixture)
            assertEquals("DECOY_UPGRADE_SENTINEL",decoy.projects.getProject(HistoricalRoom26Fixture.PROJECT)!!.name)
            assertFalse(decoy.database.nodeSortPreferenceDao().all().single().layersFirst)
            assertNull(decoy.projects.getProject(HistoricalRoom26Fixture.PROJECT)!!.photo)
            manager.lock();manager.unlock("main-upgrade-fixture".toCharArray())
            val mainAgain=manager.current.value!!
            assertEquals(primaryFixture.id,mainAgain.access.id);assertTrue(mainAgain.primary)
            assertEquals("MAIN_UPGRADE_SENTINEL",mainAgain.projects.getProject(mainProjectId)!!.name)
            val person=mainAgain.database.personDao().get(HistoricalRoom26Fixture.PERSON)!!
            assertEquals("Main person",person.name);assertEquals(3f,person.avatarZoom,0f)
            assertEquals(.4f,person.avatarX,0f);assertEquals(-.2f,person.avatarY,0f)
            assertArrayEquals(primaryFixture.image,SecureFiles.read(File(mainAgain.context.filesDir,"avatars/${person.avatarFile}")))
            assertEquals(listOf("UNPLANNED","PLANNED","DOING","DONE","VALIDATED"),mainAgain.database.backupDao().nodes().filter {it.parentId=="sprint"}.sortedBy {it.position}.map {it.workState})
            assertEquals(listOf(false,false,false,true,true),mainAgain.database.backupDao().nodes().filter {it.parentId=="sprint"}.sortedBy {it.position}.map {it.isCompleted})
            mainAgain.database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
        } finally {manager.lock();root.deleteRecursively()}
    }
    @Test fun genuineRoom37Gantt30PreservesAndSeparatesVaults()=runBlocking {
        val (context,root)=isolated();val manager=VaultManager(context)
        try {
            val main=HistoricalRoom26Fixture.create(context,"gantt-main-fixture",true,37)
            val decoy=HistoricalRoom26Fixture.create(context,"gantt-decoy-fixture",false,37)
            File(context.filesDir,"security-v1/index").writeText(org.json.JSONArray(listOf(main.id.toString(),decoy.id.toString())).toString())
            var exportedMain:ByteArray?=null
            for((password,fixture) in listOf("gantt-main-fixture" to main,"gantt-decoy-fixture" to decoy)) {
                manager.unlock(password.toCharArray());val session=manager.current.value!!;val db=session.database;val sql=db.openHelper.readableDatabase
                assertEquals(38,sql.version)
                assertEquals(fixture.rows,fixture.columns.mapValues {(table,columns)->HistoricalRoom26Fixture.rows(sql.query(HistoricalRoom26Fixture.select(table,columns)))})
                assertTrue(db.ganttAnnotationDao().all().isEmpty())
                if(fixture==decoy) assertTrue(runCatching {session.backups.inspect(java.io.ByteArrayInputStream(exportedMain!!),"gantt-backup-fixture".toCharArray())}.isFailure)
                val id=session.nodes.management.configure(HistoricalRoom26Fixture.PROJECT,null,true,com.r0ybt.arachn0de.data.repository.ManagementMethodology.AGILE)!!.id
                session.nodes.management.gantt.save(id,"✅ Anotación ${fixture.id}",java.time.LocalDate.now().toEpochDay(),.37)
                val before=session.backups.snapshot();val json=com.r0ybt.arachn0de.backup.BackupJson.encode(before)
                assertEquals(30,org.json.JSONObject(String(json)).getInt("dataVersion"))
                val file=session.backups.create("gantt-backup-fixture".toCharArray())
                if(fixture==main) exportedMain=SecureFiles.input(file).use {it.readBytes()}
                val inspected=session.backups.inspect(SecureFiles.input(file),"gantt-backup-fixture".toCharArray())
                session.backups.restore(inspected);session.backups.discard(inspected)
                assertEquals(before.ganttAnnotations,db.ganttAnnotationDao().all());assertEquals(before.nodes,db.backupDao().nodes())
                sql.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                manager.lock();manager.unlock(password.toCharArray());assertEquals(before.ganttAnnotations,manager.current.value!!.database.ganttAnnotationDao().all());manager.lock()
            }
        } finally {manager.lock();root.deleteRecursively()}
    }
    @Test fun genuineRoom36CascadeBackup29PreservesBothVaults()=runBlocking {
        val (context,root)=isolated();val manager=VaultManager(context)
        try {
            val main=HistoricalRoom26Fixture.create(context,"cascade-main-fixture",true,36)
            val decoy=HistoricalRoom26Fixture.create(context,"cascade-decoy-fixture",false,36)
            File(context.filesDir,"security-v1/index").writeText(org.json.JSONArray(listOf(main.id.toString(),decoy.id.toString())).toString())
            var primaryBackup:ByteArray?=null
            suspend fun verify(password:String,fixture:HistoricalRoom26Fixture.Store,primary:Boolean) {
                manager.unlock(password.toCharArray());val session=manager.current.value!!
                assertEquals(primary,session.primary);assertEquals(fixture.id,session.access.id)
                if(!primary) assertTrue(runCatching {session.backups.inspect(java.io.ByteArrayInputStream(requireNotNull(primaryBackup)),"cascade-portable-fixture".toCharArray())}.isFailure)
                val db=session.database;val sql=db.openHelper.readableDatabase
                assertEquals(38,sql.version)
                assertEquals(fixture.rows,fixture.columns.mapValues {(table,fields)->HistoricalRoom26Fixture.rows(sql.query(HistoricalRoom26Fixture.select(table,fields)))})
                for(table in listOf("management_layers","sprint_stories","objective_work","local_identity")) sql.query("SELECT COUNT(*) FROM $table").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
                val project=HistoricalRoom26Fixture.PROJECT
                val management=session.nodes.management
                val id=management.configure(project,null,true,com.r0ybt.arachn0de.data.repository.ManagementMethodology.AGILE)!!.id
                val layers=db.managementLayerDao().all().associateBy {it.kind}
                assertEquals(layers.getValue("ACTA").nodeId,db.nodeDao().getById(layers.getValue("OBJECTIVES").nodeId)!!.parentId)
                assertEquals(layers.getValue("ACTA").nodeId,db.nodeDao().getById(layers.getValue("TEAM").nodeId)!!.parentId)
                val epic=management.cascade.create(id,layers.getValue("BACKLOG").nodeId,"Epic",com.r0ybt.arachn0de.data.repository.LayerRole.EPIC)
                val story=management.cascade.create(id,epic,"Shared story",com.r0ybt.arachn0de.data.repository.LayerRole.STORY)
                val task=session.nodes.createNode(project,story,"Shared task")
                val note=session.nodes.createNode(project,story,"Shared note",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.NOTE)
                val sprint=management.cascade.create(id,layers.getValue("SPRINTS").nodeId,"New Sprint",sprint=true)
                management.relations.addStory(sprint,story)
                session.nodes.setWorkState(task.id,com.r0ybt.arachn0de.domain.model.WorkState.DOING)
                val goal=management.planning.createObjective(id,"Goal").id
                management.relations.objective(goal,story,true)
                session.people.identifySelf(HistoricalRoom26Fixture.PERSON)
                val sourceImage=File(context.cacheDir,"cascade-attachment.png").apply {writeBytes(fixture.image)}
                val attachment=session.attachments.import(android.net.Uri.fromFile(sourceImage),"cascade-fixture-editor")
                session.attachments.associateNode(task.id,attachment.id)
                session.attachments.associateNode(note.id,attachment.id)
                val before=session.backups.snapshot()
                val encoded=com.r0ybt.arachn0de.backup.BackupJson.encode(before)
                assertEquals(29,org.json.JSONObject(String(encoded,Charsets.UTF_8)).getInt("dataVersion"))
                val decoded=com.r0ybt.arachn0de.backup.BackupJson.decode(encoded)
                // ByteArray equality is referential; runtime media paths are deliberately not JSON.
                assertEquals(before.copy(avatars=emptyMap(),technologyIcons=emptyMap(),attachmentContents=emptyMap()),decoded.copy(avatars=emptyMap(),technologyIcons=emptyMap(),attachmentContents=emptyMap()))
                assertEquals(before.avatars.keys,decoded.avatars.keys)
                before.avatars.forEach {(name,bytes)->assertArrayEquals(bytes,decoded.avatars.getValue(name))}
                assertEquals(before.technologyIcons.keys,decoded.technologyIcons.keys)
                before.technologyIcons.forEach {(name,bytes)->assertArrayEquals(bytes,decoded.technologyIcons.getValue(name))}
                val exported=session.backups.create("cascade-portable-fixture".toCharArray())
                if(primary) primaryBackup=SecureFiles.input(exported).use {it.readBytes()}
                val inspected=session.backups.inspect(SecureFiles.input(exported),"cascade-portable-fixture".toCharArray())
                val oldJson=org.json.JSONObject(String(com.r0ybt.arachn0de.backup.BackupJson.encode(inspected),Charsets.UTF_8)).put("dataVersion",28)
                for(field in listOf("managementLayers","sprintStories","objectiveWork","localIdentities")) oldJson.remove(field)
                val historical=com.r0ybt.arachn0de.backup.BackupJson.decode(oldJson.toString().toByteArray())
                assertTrue(historical.managementLayers.isEmpty());assertTrue(historical.sprintStories.isEmpty())
                // JSON carries metadata, while the container supplies verified attachment streams.
                // The v28 metadata references exactly the same IDs/blobs as this inspected container.
                session.backups.restore(historical.copy(attachmentContents=inspected.attachmentContents,imageContents=inspected.imageContents,inspectionDirectory=inspected.inspectionDirectory))
                assertEquals(before.nodes,session.backups.snapshot().nodes)
                session.backups.restore(inspected);session.backups.discard(inspected)
                val after=session.backups.snapshot()
                assertEquals(before.nodes,after.nodes);assertEquals(before.managementLayers,after.managementLayers)
                assertEquals(before.sprintStories,after.sprintStories);assertEquals(before.objectiveWork,after.objectiveWork);assertEquals(before.localIdentities,after.localIdentities)
                assertEquals(story,db.nodeDao().getById(note.id)!!.parentId);assertEquals("DOING",db.nodeDao().getById(task.id)!!.workState)
                val restoredAttachment=session.attachments.filesForNode(task.id).single()
                assertEquals(restoredAttachment.id,session.attachments.filesForNode(note.id).single().id)
                assertArrayEquals(fixture.image,SecureFiles.read(File(session.context.filesDir,"attachments/${restoredAttachment.storageName}")))
                val person=db.personDao().get(HistoricalRoom26Fixture.PERSON)!!
                assertEquals(3f,person.avatarZoom,0f);assertEquals(.4f,person.avatarX,0f);assertEquals(-.2f,person.avatarY,0f)
                assertArrayEquals(fixture.image,SecureFiles.read(File(session.context.filesDir,"avatars/${person.avatarFile}")))
                val icon=db.technologyDao().get("technology")!!.iconFile
                assertArrayEquals(fixture.image,SecureFiles.read(File(session.context.filesDir,"technology-icons/$icon")))
                sql.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                sql.query("PRAGMA integrity_check").use {assertTrue(it.moveToFirst());assertEquals("ok",it.getString(0))}
                manager.lock()
            }
            verify("cascade-main-fixture",main,true);verify("cascade-decoy-fixture",decoy,false)
            manager.unlock("cascade-main-fixture".toCharArray())
            assertEquals("Main person",manager.current.value!!.database.personDao().get(HistoricalRoom26Fixture.PERSON)!!.name)
            assertEquals(main.id,manager.current.value!!.access.id)
        } finally {manager.lock();root.deleteRecursively()}
    }
    @Test fun nativeSqlcipherPreservesTypedRowsAndRejectsWrongKey() {
        val (context,root)=isolated()
        try {
            val source=context.getDatabasePath("source.db")
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(source,null).use { db ->
                db.execSQL("CREATE TABLE typed(id INTEGER PRIMARY KEY, t TEXT, n REAL, b BLOB, z TEXT)")
                db.execSQL("INSERT INTO typed VALUES(?,?,?,?,?)",arrayOf(7,"ñ UTF-8",2.75,byteArrayOf(0,1,127),null))
                db.execSQL("PRAGMA user_version=25")
            }
            val target=context.getDatabasePath("protected.db")
            val entropy=VaultCrypto.randomKey()
            val key=try {entropy.joinToString("") {"%02x".format(it)}.toByteArray(Charsets.US_ASCII)} finally {entropy.fill(0)}
            try {
                val before=source.readBytes();SqlCipherMigration.copy(source,target,key)
                assertArrayEquals(before,source.readBytes())
                assertTrue(runCatching {net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(target.path,VaultCrypto.randomKey(),null,net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,null).use {db->db.rawQuery("SELECT * FROM typed",emptyArray<String>()).use {it.moveToFirst()}}}.isFailure)
                net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(target.path,key,null,net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,null).use {db ->
                    db.rawQuery("SELECT * FROM typed",emptyArray<String>()).use {c->assertTrue(c.moveToFirst());assertEquals("ñ UTF-8",c.getString(1));assertEquals(2.75,c.getDouble(2),0.0);assertArrayEquals(byteArrayOf(0,1,127),c.getBlob(3));assertTrue(c.isNull(4))}
                }
            } finally {key.fill(0)}
        } finally {root.deleteRecursively()}
    }
    @Test fun interruptedNativeMigrationRetainsSourceAndRetriesWithSamePassword()=runBlocking {
        val (context,root)=isolated()
        var interrupt=true
        val manager=VaultManager(context,sync={directory ->
            if(interrupt && directory.name=="migration-original") {
                interrupt=false;throw java.io.IOException("Injected pre-activation interruption")
            }
            com.r0ybt.arachn0de.backup.syncBackupDirectory(directory)
        })
        try {
            val original=Arachn0deDatabase.create(context)
            val project=ProjectRepository(original.projectDao(),original).createProject("Preserve interrupted migration")
            original.close()
            val source=File(context.filesDir,"original.bin").apply {writeBytes(byteArrayOf(7,8,9))}
            assertTrue(runCatching {manager.setup("retry-native-123".toCharArray())}.isFailure)
            assertNull(manager.current.value);assertFalse(manager.configured)
            assertArrayEquals(byteArrayOf(7,8,9),source.readBytes())
            val reopened=Arachn0deDatabase.create(context)
            try {assertEquals(listOf(project.id),reopened.backupDao().projects().map {it.id})} finally {reopened.close()}
            manager.setup("retry-native-123".toCharArray())
            assertEquals(listOf(project.id),manager.current.value!!.database.backupDao().projects().map {it.id})
            assertArrayEquals(byteArrayOf(7,8,9),SecureFiles.read(File(manager.current.value!!.context.filesDir,source.name)))
        } finally {manager.lock();root.deleteRecursively()}
    }

}
