package com.r0ybt.arachn0de.antisocial

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PrincipalProfileTest {
    @Test fun existingIdentityAndFirstInsertedFallbackStayStableAndProtected()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("arachn0de.db");val db=Arachn0deDatabase.create(context)
        try {
            val repo=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
            db.personDao().insert(PersonEntity("z-first","Zeta",null));db.personDao().insert(PersonEntity("a-later","Alfa",null))
            db.cascadeRelationDao().save(LocalIdentityEntity(personId="a-later"))
            assertEquals("a-later",repo.ensurePrincipal())
            db.cascadeRelationDao().clearIdentity()
            assertEquals("z-first",repo.ensurePrincipal())
            repo.save("z-first","Último",null,isNew=false)
            repo.save("a-later","Primero",null,isNew=false)
            assertEquals("z-first",repo.ensurePrincipal())
            assertTrue(runCatching {repo.delete("z-first")}.isFailure)
            assertNotNull(db.personDao().get("z-first"))
            assertTrue(runCatching {repo.identifySelf("a-later")}.isFailure)
            val historical=PersonProfileEntity("z-first",givenName="Nombre existente",alias="Apodo histórico",familyName="Apellido",documentId="Documento",addresses="Dirección",qualities="Cualidades",skills="Habilidades",interests="Intereses",availability="Disponibilidad",notes="Notas")
            repo.saveProfile(historical)
            val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            val snapshot=backup.snapshot()
            val restored=BackupJson.decode(BackupJson.encode(snapshot))
            backup.restore(restored)
            assertEquals("z-first",repo.ensurePrincipal())
            assertEquals(historical,db.personProfileDao().profiles().single {it.personId=="z-first"})
            val changed=com.r0ybt.arachn0de.ui.updatePersonSectionField(historical,"Información",1,"Nueva biografía")
            repo.saveProfile(changed)
            assertEquals(historical.copy(description="Nueva biografía"),db.personProfileDao().profiles().single {it.personId=="z-first"})
            assertEquals("Nombre existente",com.r0ybt.arachn0de.ui.personVisibleName(com.r0ybt.arachn0de.domain.model.Person("z-first","Apodo",null),changed))
            assertTrue(repo.delete("a-later"))
            assertEquals("z-first",repo.ensurePrincipal())
        } finally {db.close()}
    }
    @Test fun newInstallAndHistoricalRestoreAcquireExactlyOnePrincipal()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("arachn0de.db");val db=Arachn0deDatabase.create(context)
        try {
            val repo=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
            assertNull(repo.ensurePrincipal())
            repo.save("first","Primera",null);repo.save("second","Segunda",null)
            assertEquals(listOf(LocalIdentityEntity(personId="first")),db.cascadeRelationDao().identities())
            val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            backup.restore(BackupFixture.empty().copy(persons=listOf(PersonEntity("old","Anterior",null),PersonEntity("later","Después",null))))
            assertEquals("old",repo.ensurePrincipal())
            assertEquals(2,db.backupDao().persons().size)
            assertEquals("old",BackupJson.decode(BackupJson.encode(backup.snapshot())).localIdentities.single().personId)
            val fields=com.r0ybt.arachn0de.ui.profileFields(PersonProfileEntity("old"),"Información").map {it.first}
            assertEquals(listOf("Nombre","Biografía","Cumpleaños","Teléfono","Correo electrónico","Enlaces"),fields)
            assertEquals(listOf("Inicio","Personas","Tecnologías","Proyectos"),com.r0ybt.arachn0de.ui.antisocialSections)
        } finally {db.close()}
    }
}
