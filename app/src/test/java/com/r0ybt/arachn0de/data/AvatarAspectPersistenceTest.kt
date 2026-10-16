package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class AvatarAspectPersistenceTest {
    @Test fun landscapePortraitAndSquareKeepCropAndOriginalBytesAfterReopen()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        var db=Arachn0deDatabase.create(context)
        val store=AvatarStore(context,syncDirectory=BackupFixture::syncDirectory)
        try {
            val people=PersonRepository(db,store)
            val originals=mutableMapOf<String,ByteArray>()
            listOf(240 to 120,120 to 240,160 to 160).forEachIndexed {index,(w,h)->
                val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.MAGENTA)}
                val source=java.io.File(context.cacheDir,"aspect$index.png");source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                val name=people.importAvatar(Uri.fromFile(source));originals[name]=java.io.File(context.filesDir,"avatars/$name").readBytes()
                people.save("person$index","Persona $index",name,zoom=2.2f,x=-.7f,y=.4f)
            }
            db.close();db=Arachn0deDatabase.create(context)
            val reopened=PersonRepository(db,store).observePeople().first()
            assertEquals(3,reopened.size)
            reopened.forEach {person->
                assertEquals(2.2f,person.avatarZoom);assertEquals(-.7f,person.avatarX);assertEquals(.4f,person.avatarY)
                val file=checkNotNull(person.avatarFile);assertArrayEquals(originals.getValue(file),java.io.File(context.filesDir,"avatars/$file").readBytes())
                val image=checkNotNull(store.read(file));val geometry=AvatarFraming(person.avatarZoom,person.avatarX,person.avatarY).geometry(image.width.toFloat(),image.height.toFloat(),96f)
                assertTrue(geometry.left<=0 && geometry.top<=0);assertTrue(geometry.left+geometry.width>=96f && geometry.top+geometry.height>=96f)
            }
        } finally {db.close()}
    }
}
