package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class CreationDefaultsRepositoryTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    @Before fun setup()=runBlocking { val c=RuntimeEnvironment.getApplication();c.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(c);nodes=NodeRepository(db);db.projectDao().insert(ProjectEntity("p","Project","",0,1,1)) }
    @After fun close() { db.close();RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    @Test fun reopeningPreservesLayerOverridesAndInheritedValuesIndependently()=runBlocking {
        val layer=nodes.createNode("p",null,"Layer",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER);val child=nodes.createNode("p",layer.id,"Child",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val tag=nodes.tags.create("Tag");db.personDao().insert(PersonEntity("person","Ana",null))
        nodes.creationDefaults.save(DefaultsScope.Global,CreationDefaults(priority=DefaultValue.Own(Priority.MEDIUM),tags=DefaultValue.Own(setOf(tag.id))))
        nodes.creationDefaults.save(DefaultsScope.Project("p"),CreationDefaults(currency=DefaultValue.Own("EUR"),people=DefaultValue.Own(setOf("person"))))
        nodes.creationDefaults.save(DefaultsScope.Layer("p",layer.id),CreationDefaults(due=DefaultValue.Own(DefaultDate(DefaultDateKind.DAY_OF_MONTH,15)),dueTime=DefaultValue.Own(DefaultTime.Minute(1320))))
        nodes.creationDefaults.save(DefaultsScope.Layer("p",child.id),CreationDefaults(tags=DefaultValue.Own(emptySet()),obligation=DefaultValue.Own(false)))
        val expected=nodes.creationDefaults.resolve("p",child.id)
        db.close();db=Arachn0deDatabase.create(RuntimeEnvironment.getApplication());nodes=NodeRepository(db)
        assertEquals(expected,nodes.creationDefaults.resolve("p",child.id));assertEquals("EUR",expected.currency);assertEquals(Priority.MEDIUM,expected.priority);assertTrue(expected.tags.isEmpty());assertEquals(setOf("person"),expected.people)
        nodes.creationDefaults.reset(DefaultsScope.Layer("p",child.id));assertEquals(setOf(tag.id),nodes.creationDefaults.resolve("p",child.id).tags)
        nodes.creationDefaults.reset(DefaultsScope.Global);assertEquals(Priority.NONE,nodes.creationDefaults.resolve("p",child.id).priority);assertEquals("EUR",nodes.creationDefaults.resolve("p",child.id).currency)
    }
    @Test fun invalidReferencesRollbackConfigurationAndRelatedSets()=runBlocking {
        val scope=DefaultsScope.Global;val before=CreationDefaults(currency=DefaultValue.Own("USD"));nodes.creationDefaults.save(scope,before)
        for(bad in listOf(before.copy(tags=DefaultValue.Own(setOf("missing"))),before.copy(people=DefaultValue.Own(setOf("missing"))))) {
            assertTrue(runCatching { nodes.creationDefaults.save(scope,bad) }.isFailure);assertEquals(before,nodes.creationDefaults.configuration(scope).own)
        }
        assertTrue(runCatching { nodes.creationDefaults.save(DefaultsScope.Project("missing"),before) }.isFailure)
        val q=ProjectEntity("q","Other","",1,1,1);db.projectDao().insert(q);val node=nodes.createNode("q",null,"Other layer",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        assertTrue(runCatching { nodes.creationDefaults.save(DefaultsScope.Layer("p",node.id),before) }.isFailure)
    }
    @Test fun cascadesRemoveDeletedReferencesContextsAndDescendantsButKeepGlobal()=runBlocking {
        val layer=nodes.createNode("p",null,"Layer",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER);val child=nodes.createNode("p",layer.id,"Child",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER);val tag=nodes.tags.create("Tag");db.personDao().insert(PersonEntity("person","Ana",null))
        val config=CreationDefaults(tags=DefaultValue.Own(setOf(tag.id)),people=DefaultValue.Own(setOf("person")))
        for(scope in listOf(DefaultsScope.Global,DefaultsScope.Project("p"),DefaultsScope.Layer("p",layer.id),DefaultsScope.Layer("p",child.id))) nodes.creationDefaults.save(scope,config)
        nodes.tags.delete(tag.id);db.personDao().delete("person")
        assertTrue(nodes.creationDefaults.resolve("p",child.id).tags.isEmpty());assertTrue(nodes.creationDefaults.resolve("p",child.id).people.isEmpty())
        nodes.deleteNode(layer.id);assertEquals(setOf("G","P:p"),db.creationDefaultsDao().all().map { it.id }.toSet())
        db.projectDao().delete("p");assertEquals(listOf("G"),db.creationDefaultsDao().all().map { it.id })
        assertTrue(db.creationDefaultsDao().tags().isEmpty());assertTrue(db.creationDefaultsDao().people().isEmpty())
    }
    @Test fun defaultsDoNotMutateExistingNodesOrEventsWhenEditedOrMoved()=runBlocking {
        val layer=nodes.createNode("p",null,"Layer",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER);val task=nodes.createNode("p",null,"Task")
        val before=db.nodeDao().getById(task.id)!!;val events=db.nodeEventDao().all()
        nodes.creationDefaults.save(DefaultsScope.Layer("p",layer.id),CreationDefaults(priority=DefaultValue.Own(Priority.HIGH),due=DefaultValue.Own(DefaultDate(DefaultDateKind.TODAY))))
        assertEquals(before,db.nodeDao().getById(task.id));assertEquals(events,db.nodeEventDao().all())
        nodes.moveNode(task.id,layer.id)
        val moved=db.nodeDao().getById(task.id)!!;assertNull(moved.dueAt);assertEquals("NONE",moved.priority)
    }
}
