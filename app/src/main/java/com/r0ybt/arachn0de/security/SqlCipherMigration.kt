package com.r0ybt.arachn0de.security

import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.security.MessageDigest
import java.nio.ByteBuffer

/** Export alongside an immutable consistent source. Never rekey or overwrite the original. */
internal object SqlCipherMigration {
    private fun quote(name:String)="\"${name.replace("\"","\"\"")}\""
    fun copy(source:File,target:File,password:ByteArray) {
        require(source.isFile && !target.exists())
        require(source.canonicalFile!=target.canonicalFile)
        System.loadLibrary("sqlcipher")
        val original=SQLiteDatabase.openDatabase(source.path,byteArrayOf(),null,SQLiteDatabase.OPEN_READWRITE,null)
        try {
            original.execSQL("PRAGMA temp_store=MEMORY")
            val version=original.rawQuery("PRAGMA user_version",emptyArray<String>()).use { check(it.moveToFirst());it.getInt(0) }
            val before=fingerprint(original)
            original.execSQL("ATTACH DATABASE ? AS protected_copy KEY ?",arrayOf(target.path,String(password,Charsets.UTF_8)))
            try {
                original.rawQuery("SELECT sqlcipher_export('protected_copy')",emptyArray<String>()).use { check(it.moveToFirst()) }
                original.execSQL("PRAGMA protected_copy.user_version = $version")
            } finally { original.execSQL("DETACH DATABASE protected_copy") }
            check(MessageDigest.isEqual(before,fingerprint(original))) { "El origen cambió durante la exportación." }
            val reopened=SQLiteDatabase.openDatabase(target.path,password,null,SQLiteDatabase.OPEN_READONLY,null)
            try {
                reopened.execSQL("PRAGMA temp_store=MEMORY")
                reopened.rawQuery("PRAGMA cipher_integrity_check",emptyArray<String>()).use { check(!it.moveToFirst()) { "Integridad criptográfica incorrecta." } }
                reopened.rawQuery("PRAGMA integrity_check",emptyArray<String>()).use { check(it.moveToFirst() && it.getString(0)=="ok" && !it.moveToNext()) }
                reopened.rawQuery("PRAGMA foreign_key_check",emptyArray<String>()).use { check(!it.moveToFirst()) }
                check(MessageDigest.isEqual(before,fingerprint(reopened))) { "La copia no conserva todas las filas y el esquema." }
            } finally { reopened.close() }
        } finally { original.close() }
    }
    /** Stable row order and typed length framing retain IDs, NULLs, BLOBs and exact doubles. */
    private fun fingerprint(database:SQLiteDatabase):ByteArray {
        val hash=MessageDigest.getInstance("SHA-256")
        fun field(bytes:ByteArray) { hash.update(ByteBuffer.allocate(4).putInt(bytes.size).array());hash.update(bytes) }
        fun row(cursor:Cursor) {
            for(column in 0 until cursor.columnCount) {
                val type=cursor.getType(column);hash.update(type.toByte())
                field(when(type) {
                    Cursor.FIELD_TYPE_NULL->byteArrayOf()
                    Cursor.FIELD_TYPE_INTEGER->ByteBuffer.allocate(8).putLong(cursor.getLong(column)).array()
                    Cursor.FIELD_TYPE_FLOAT->ByteBuffer.allocate(8).putLong(java.lang.Double.doubleToRawLongBits(cursor.getDouble(column))).array()
                    Cursor.FIELD_TYPE_BLOB->cursor.getBlob(column)
                    else->cursor.getString(column).toByteArray(Charsets.UTF_8)
                })
            }
        }
        database.rawQuery("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type,name",emptyArray<String>()).use { while(it.moveToNext()) row(it) }
        val tables=database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",emptyArray<String>()).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
        for(table in tables) {
            field(table.toByteArray(Charsets.UTF_8))
            val columns=database.rawQuery("PRAGMA table_info(${quote(table)})",emptyArray<String>()).use { c -> buildList { while(c.moveToNext()) add(c.getString(1)) } }
            database.rawQuery("SELECT * FROM ${quote(table)} ORDER BY ${columns.joinToString(",",transform=::quote)}",emptyArray<String>()).use { c -> while(c.moveToNext()) row(c) }
        }
        return hash.digest()
    }
}
