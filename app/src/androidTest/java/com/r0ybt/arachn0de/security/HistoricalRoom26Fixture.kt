package com.r0ybt.arachn0de.security

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** Test-only: builds a fresh historical schema, never downgrades a current database. */
internal object HistoricalRoom26Fixture {
    private const val SCHEMA="com.r0ybt.arachn0de.data.local.Arachn0deDatabase/26.json"
    private const val HASH="cad5834a029e4a89570c705f2628c1e54cd4e66f44368c306d45e5be41a5abc0"
    private const val IDENTITY="38afa7183ac982125a17210e879c2dc5"
    const val PROJECT="historical-project"
    const val PERSON="historical-person"
    const val AVATAR="11111111-1111-4111-8111-111111111111.png"
    const val ICON="22222222-2222-4222-8222-222222222222.png"
    data class Store(val id:UUID,val columns:Map<String,List<String>>,val rows:Map<String,List<List<String>>>,val image:ByteArray)
    private fun hex(bytes:ByteArray)=bytes.joinToString("") {"%02x".format(it)}
    fun rows(cursor:Cursor):List<List<String>> = cursor.use {c->buildList {
        while(c.moveToNext()) add((0 until c.columnCount).map {i->when(c.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> "null"
            Cursor.FIELD_TYPE_BLOB -> "blob:${hex(c.getBlob(i))}"
            Cursor.FIELD_TYPE_INTEGER -> "integer:${c.getLong(i)}"
            Cursor.FIELD_TYPE_FLOAT -> "float:${java.lang.Double.doubleToRawLongBits(c.getDouble(i))}"
            else -> "text:${c.getString(i)}"
        }})
    }}
    fun select(table:String,columns:List<String>)="SELECT ${columns.joinToString(",") {"`$it`"}} FROM `$table` ORDER BY ${columns.joinToString(",") {"`$it`"}}"
    fun create(base:Context,password:String,primary:Boolean,version:Int=26):Store {
        require(version in listOf(26,36,37))
        val schemaPath=if(version==26) SCHEMA else "com.r0ybt.arachn0de.data.local.Arachn0deDatabase/$version.json"
        val expectedHash=if(version==37) "084a31ed6d2b88fc68fbe6f632c262f75edf95e945a1ace7aa228ff4fbd89616" else if(version==26) HASH else "c6994e2c597c1959830904c0bca7b4c8fddd3506db8436f9f8e4c40cbe150913"
        val expectedIdentity=if(version==37) "abcfef8a4aedbf48b34b3698fa4015bd" else if(version==26) IDENTITY else "39944c7b7fe2dbdc0efd1a466ce02310"
        val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open(schemaPath).use {it.readBytes()}
        assertEquals(expectedHash,hex(MessageDigest.getInstance("SHA-256").digest(bytes)))
        val schema=JSONObject(String(bytes,Charsets.UTF_8)).getJSONObject("database")
        assertEquals(version,schema.getInt("version"));assertEquals(expectedIdentity,schema.getString("identityHash"))
        val entities=schema.getJSONArray("entities")
        val columns=(0 until entities.length()).associate {i->val entity=entities.getJSONObject(i);val fields=entity.getJSONArray("fields")
            entity.getString("tableName") to (0 until fields.length()).map {fields.getJSONObject(it).getString("columnName")}}
        if(version==26) {assertFalse("management_contexts" in columns);assertFalse("sprint_links" in columns)}
        if(version<37) assertFalse("management_layers" in columns)
        else assertTrue(columns.keys.containsAll(listOf("management_layers","sprint_stories","objective_work","local_identity")))
        assertFalse("gantt_annotations" in columns)
        val id=UUID.randomUUID();val root=File(base.filesDir,"security-v1/stores/$id").canonicalFile.apply {check(mkdirs())}
        val plain=File(base.cacheDir,"room$version-$id.db");assertFalse(plain.exists())
        val key=VaultCrypto.randomKey();val session=SecureFiles.Session(id,root,key);SecureFiles.register(session)
        val bitmap=Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888).apply {eraseColor(if(primary) android.graphics.Color.GREEN else android.graphics.Color.BLUE)}
        val image=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        try {
            val snapshot=SQLiteDatabase.openOrCreateDatabase(plain,null).use {db->
                db.execSQL("PRAGMA foreign_keys=ON")
                db.beginTransaction()
                try {
                    for(i in 0 until entities.length()) {
                        val entity=entities.getJSONObject(i);val name=entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",name))
                        val indices=entity.optJSONArray("indices") ?: org.json.JSONArray()
                        for(j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
                    }
                    val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    val label=if(primary) "MAIN_UPGRADE_SENTINEL" else "DECOY_UPGRADE_SENTINEL"
                    db.execSQL("INSERT INTO projects VALUES(?,?,?,?,?,?)",arrayOf(PROJECT,label,"Histórico ñ",0,1,2))
                    db.execSQL("INSERT INTO nodes VALUES('sprint',?,NULL,'Sprint','',0,0,1,2,NULL,NULL,'LAYER',NULL,NULL,'NONE',NULL,1,NULL)",arrayOf(PROJECT))
                    for((i,phase) in listOf("UNPLANNED","PLANNED","DOING","DONE","VALIDATED").withIndex())
                        db.execSQL("INSERT INTO nodes VALUES(?,?,'sprint',?,'Texto original',?,?,1,2,NULL,NULL,'ACTION',NULL,NULL,'HIGH',NULL,0,?)",arrayOf("task-$i",PROJECT,"$label · $phase",if(i>=3) 1 else 0,i,phase))
                    db.execSQL("INSERT INTO nodes VALUES('note',?,NULL,'Nota','Texto ñ',0,1,1,2,NULL,NULL,'NOTE',NULL,NULL,'NONE',NULL,0,NULL)",arrayOf(PROJECT))
                    db.execSQL("INSERT INTO persons VALUES(?,?,?,3.0,0.4,-0.2)",arrayOf(PERSON,if(primary) "Main person" else "Decoy person",AVATAR))
                    db.execSQL("INSERT INTO node_person VALUES('task-0',?)",arrayOf(PERSON));db.execSQL("INSERT INTO node_person VALUES('task-1',?)",arrayOf(PERSON))
                    db.execSQL("INSERT INTO technologies VALUES('technology','Kotlin',?)",arrayOf(ICON))
                    db.execSQL("INSERT INTO node_technologies VALUES('task-0','technology',0)");db.execSQL("INSERT INTO node_technologies VALUES('task-1','technology',0)")
                    db.execSQL("INSERT INTO project_technologies VALUES(?,'technology',0)",arrayOf(PROJECT))
                    db.execSQL("INSERT INTO tags VALUES('tag','Histórico','histórico')");db.execSQL("INSERT INTO node_tag VALUES('task-0','tag')")
                    db.execSQL("INSERT INTO node_events VALUES('event','task-3','COMPLETED',1234)")
                    db.execSQL("INSERT INTO node_sort_preferences(context,mode) VALUES(?,?)",arrayOf("$PROJECT:project-root",if(primary) "DUE_PRIORITY" else "PRIORITY"))
                    db.execSQL("INSERT INTO private_preferences VALUES('security_preferences',?)",arrayOf(EncryptedPreferences.encode(mapOf("background_timeout" to 30000L))))
                    // Version is assigned only to this freshly constructed, verified historical schema.
                    db.version=version;db.setTransactionSuccessful()
                } finally {db.endTransaction()}
                assertEquals(version,db.version)
                val tables=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",null).use {c->buildSet {while(c.moveToNext()) add(c.getString(0))}}
                assertEquals(columns.keys+setOf("room_master_table","android_metadata"),tables)
                db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42",null).use {assertTrue(it.moveToFirst());assertEquals(expectedIdentity,it.getString(0))}
                db.rawQuery("PRAGMA foreign_key_check",null).use {assertFalse(it.moveToFirst())}
                db.rawQuery("PRAGMA integrity_check",null).use {assertTrue(it.moveToFirst());assertEquals("ok",it.getString(0));assertFalse(it.moveToNext())}
                columns.mapValues {(table,fields)->rows(db.rawQuery(select(table,fields),null))}
            }
            File(root,"key.envelope").writeBytes(VaultCrypto.wrap(id,password.toCharArray(),key))
            SecureFiles.write(File(root,"identity.enc"),(if(primary) "primary" else "secondary").toByteArray())
            for(directory in listOf("avatars","technology-icons")) {
                val folder=File(root,"files/$directory").apply {check(mkdirs())}
                SecureFiles.write(File(folder,if(directory=="avatars") AVATAR else ICON),image)
            }
            // Same canonical derivation as VaultManager; only synthetic fixture keys are used.
            val salt=ByteBuffer.allocate(32).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()
            val derived=VaultCrypto.subkey(key,salt,"database-v1");val databaseKey=try {hex(derived).toByteArray(Charsets.US_ASCII)} finally {derived.fill(0)}
            val target=File(root,"databases/arachn0de.db").apply {check(parentFile!!.mkdirs())}
            try {SqlCipherMigration.copy(plain,target,databaseKey)} finally {databaseKey.fill(0)}
            assertFalse(target.inputStream().use {String(it.readBytes().take(16).toByteArray(),Charsets.US_ASCII)}.startsWith("SQLite format 3"))
            return Store(id,columns,snapshot,image)
        } finally {SecureFiles.revoke(session);key.fill(0);plain.delete();File(plain.path+"-journal").delete()}
    }
}
