package com.r0ybt.arachn0de.security

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.PersonAvatar
import com.r0ybt.arachn0de.ui.TechnologyIcon
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Arachn0deApplication::class)
class EncryptedMediaUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun existingAvatarAndIconComponentsReadCapturedEncryptedStoreAndCacheFailsClosed() {
        val app=compose.activity.application as Arachn0deApplication
        val id=UUID.randomUUID();val root=File(app.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val access=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(access)
        val context=VaultContext(app,access)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();context.database=db
        val session=VaultSession(access,context,db,true)
        val avatar=File(context.filesDir,"avatars/${UUID.randomUUID()}.png").apply {parentFile!!.mkdirs()}
        val icon=File(context.filesDir,"technology-icons/${UUID.randomUUID()}.png").apply {parentFile!!.mkdirs()}
        SecureFiles.write(avatar,BackupFixture.png());SecureFiles.write(icon,BackupFixture.png())
        SecureFiles.activeProfile=id
        try {
            compose.runOnUiThread {
                app.security.current.value=session
                compose.activity.setContent {Arachn0deTheme {Row {
                    PersonAvatar(Person("p","Avatar protegido",avatar.name))
                    TechnologyIcon(TechnologyEntity("t","Icono protegido",icon.name))
                }}}
            }
            compose.waitUntil(10000) {PrivateImageCache.sizeBytes()>0 && compose.onAllNodesWithText("A",useUnmergedTree=true).fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithText("I",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithContentDescription("Avatar protegido").assertExists()
            compose.onNodeWithContentDescription("Icono protegido").assertExists()
            assertEquals(android.graphics.Color.MAGENTA,PrivateImageCache.read(avatar)!!.getPixel(10,10))
            assertEquals(android.graphics.Color.MAGENTA,PrivateImageCache.read(icon)!!.getPixel(10,10))
            SecureFiles.activeProfile=null
            assertTrue(runCatching {PrivateImageCache.read(avatar)}.isFailure)
            SecureFiles.revoke(access)
            assertTrue(runCatching {PrivateImageCache.read(icon)}.isFailure)
        } finally {runBlocking {app.security.lock()};PrivateImageCache.clear();root.deleteRecursively()}
    }
}
