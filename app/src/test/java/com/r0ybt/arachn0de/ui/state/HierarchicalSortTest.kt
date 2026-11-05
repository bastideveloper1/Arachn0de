package com.r0ybt.arachn0de.ui.state

import android.app.Application
import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class HierarchicalSortTest {
    @Test fun favoritesGroupStablyAroundOtherCriteriaWithoutChangingStructuralRules() {
        val rows=listOf(node("b",1),node("a",0),node("fav",2),node("done",3,done=true),
            node("layer",4).copy(purpose=NodePurpose.LAYER),node("note",5).copy(purpose=NodePurpose.NOTE))
        val favorites=setOf("node:fav","node:done","node:note","node:layer")
        val byName=config(rule(SortCriterion.NAME))
        for(manual in listOf(false,true)) {
            val base=byName.copy(manual=manual)
            val order=rows.sortedWith(HierarchicalSort.comparator(base)).map {it.id}
            for(choice in FavoriteOrder.entries) {
                val result=rows.sortedWith(HierarchicalSort.comparator(base.copy(favorites=choice),favorites=favorites))
                assertEquals("done",result.last().id)
                val pendingOrder=order.filter {it!="done"}
                val expected=when(choice) {
                    FavoriteOrder.NONE->pendingOrder
                    FavoriteOrder.FIRST->pendingOrder.filter {"node:$it" in favorites}+pendingOrder.filter {"node:$it" !in favorites}
                    FavoriteOrder.LAST->pendingOrder.filter {"node:$it" !in favorites}+pendingOrder.filter {"node:$it" in favorites}
                }+"done"
                assertEquals(expected,result.map {it.id})
                assertEquals(expected,rows.reversed().sortedWith(HierarchicalSort.comparator(base.copy(favorites=choice),favorites=favorites)).map {it.id})
            }
        }
        val structural=byName.copy(layersFirst=true,favorites=FavoriteOrder.LAST)
        assertEquals("layer",rows.sortedWith(HierarchicalSort.comparator(structural,favorites=favorites)).first().id)
        val pinned=rows+node("pinned",6).copy(purpose=NodePurpose.LAYER,isPinned=true)
        assertEquals("pinned",pinned.sortedWith(HierarchicalSort.comparator(structural,favorites=favorites)).first().id)
    }
    private fun node(id:String,position:Int=0,done:Boolean=false,verify:Boolean=false,priority:Priority=Priority.NONE,due:Long?=null)=
        Node(id,"p",null,id,"",done,position,1,1,false,priority=priority,dueAt=due,awaitingVerification=verify)
    private fun config(vararg rules:SortRule)=SortConfiguration(false,rules.toList()+SortCriterion.entries.filter {key->rules.none {it.criterion==key}}.map {SortRule(it)})
    private fun rule(key:SortCriterion,reverse:Boolean=false)=SortRule(key,true,reverse)
    @Test fun combinedHierarchyChangesPrecedenceAndNeverPromotesCompletedTasks() {
        val nodes=listOf(node("low",priority=Priority.NONE),node("verify",verify=true,priority=Priority.HIGH),node("high",priority=Priority.HIGH),node("done",done=true,priority=Priority.HIGH))
        val state=config(rule(SortCriterion.STATE),rule(SortCriterion.PRIORITY))
        assertEquals(listOf("high","low","verify","done"),nodes.sortedWith(HierarchicalSort.comparator(state)).map {it.id})
        val priority=config(rule(SortCriterion.PRIORITY),rule(SortCriterion.STATE))
        assertEquals(listOf("high","verify","low","done"),nodes.sortedWith(HierarchicalSort.comparator(priority)).map {it.id})
        val reversed=config(rule(SortCriterion.STATE,true))
        assertEquals("verify",nodes.sortedWith(HierarchicalSort.comparator(reversed)).first().id)
        assertEquals(listOf("high","low","verify","done"),nodes.sortedWith(HierarchicalSort.comparator(reversed,stateCounts=nodes.associate {it.id to 2})).map {it.id})
    }
    @Test fun labelsUseNormalizedDirectionalKeyWithoutDuplicatesAndEmptyAlwaysLast() {
        val nodes=listOf(node("two"),node("none"),node("one"),node("accent"))
        val tags=mapOf("two" to listOf("Zeta","Desarrollo"),"one" to listOf("Finanzas"),"accent" to listOf("Ádmin"))
        val ascending=nodes.sortedWith(HierarchicalSort.comparator(config(rule(SortCriterion.TAG)),tags=tags))
        assertEquals(listOf("accent","two","one","none"),ascending.map {it.id})
        assertEquals(listOf("two","one","accent","none"),nodes.sortedWith(HierarchicalSort.comparator(config(rule(SortCriterion.TAG,true)),tags=tags)).map {it.id})
        assertEquals(nodes.size,ascending.map {it.id}.distinct().size)
    }
    @Test fun absentDatesAndPeopleRemainLastInEitherDirectionAndTiesKeepPositions() {
        val nodes=listOf(node("b",2,due=10),node("empty",0),node("a",1,due=10),node("late",3,due=20))
        for(reverse in listOf(false,true)) {
            val result=nodes.sortedWith(HierarchicalSort.comparator(config(rule(SortCriterion.DUE,reverse))))
            assertEquals("empty",result.last().id)
            assertTrue(result.indexOfFirst {it.id=="a"}<result.indexOfFirst {it.id=="b"})
            val people=nodes.sortedWith(HierarchicalSort.comparator(config(rule(SortCriterion.PERSON,reverse)),people=mapOf("a" to listOf("Zoe"),"b" to listOf("Ana"),"late" to listOf("Luis"))))
            assertEquals("empty",people.last().id)
            assertEquals(if(reverse)"a" else "b",people.first().id)
        }
    }
    @Test fun labelsStatePriorityCombineAndManualPreservesConfiguration() {
        val nodes=listOf(node("b",1,priority=Priority.HIGH),node("a",0),node("v",2,verify=true))
        val config=config(rule(SortCriterion.TAG),rule(SortCriterion.STATE),rule(SortCriterion.PRIORITY))
        assertEquals(listOf("b","a","v"),nodes.sortedWith(HierarchicalSort.comparator(config,tags=nodes.associate {it.id to listOf("Común")})).map {it.id})
        val manual=config.copy(manual=true)
        assertEquals(listOf("a","b","v"),nodes.sortedWith(HierarchicalSort.comparator(manual)).map {it.id})
        assertEquals(config.rules,manual.rules)
    }
    @Test fun legacyModesMapToEquivalentCriteriaAndAllDirectionsRoundTrip() {
        val expected=mapOf("DUE_ASC" to listOf(rule(SortCriterion.DUE)),"DUE_DESC" to listOf(rule(SortCriterion.DUE,true)),
            "DUE_PRIORITY" to listOf(rule(SortCriterion.DUE),rule(SortCriterion.PRIORITY)),"PRIORITY" to listOf(rule(SortCriterion.PRIORITY),rule(SortCriterion.DUE)),
            "CREATED_NEWEST" to listOf(rule(SortCriterion.CREATED)),"CREATED_OLDEST" to listOf(rule(SortCriterion.CREATED,true)))
        expected.forEach {(mode,rules)->assertEquals(rules,SortConfigurationCodec.decode(mode).rules.filter {it.enabled})}
        val value=SortConfiguration(false,SortCriterion.entries.reversed().map {SortRule(it,true,true)},true,true,true)
        assertEquals(value,SortConfigurationCodec.decode(SortConfigurationCodec.encode(value)))
        assertTrue(SortConfigurationCodec.decode("MANUAL").manual)
        assertTrue(runCatching {SortConfigurationCodec.decode("SORT1:{}")}.isFailure)
        assertTrue(runCatching {value.copy(rules=value.rules.drop(1))}.isFailure)
    }
    @Test fun inheritedConfigurationTracksCoreButOwnLayerSurvivesAndSaverKeepsAllRules() {
        val store=NodeSortPreferences()
        val root=config(rule(SortCriterion.TAG)).copy(layersFirst=true)
        store.configure("p:project-root",root)
        assertEquals(root.rules,store.configuration("p:nested").rules)
        assertTrue(store.configuration("p:nested").layersFirst)
        store.configure("p:own",config(rule(SortCriterion.DUE)))
        store.configure("p:project-root",config(rule(SortCriterion.NAME,true)))
        assertEquals(SortCriterion.DUE,store.configuration("p:own").rules.first().criterion)
        store.inherit("p:own")
        assertEquals(SortCriterion.NAME,store.configuration("p:own").rules.first().criterion)
        val saved=with(NodeSortPreferences.Saver) {(object:androidx.compose.runtime.saveable.SaverScope {override fun canBeSaved(value:Any)=true}).save(store)}!!
        val restored=NodeSortPreferences.Saver.restore(saved)!!
        assertEquals(store.configuration("p:own"),restored.configuration("p:own"))
        assertEquals(store.configuration("p:project-root"),restored.configuration("p:project-root"))
    }
    @Test fun staleDatabaseEmissionsDoNotOverwriteNewerAutosavedChoices() {
        val store=NodeSortPreferences(onConfigWrite={_,_->})
        val first=config(rule(SortCriterion.DUE));val latest=config(rule(SortCriterion.TAG,true))
        store.configure("p:project-root",first);store.configure("p:project-root",latest)
        store.replace(listOf(com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity("p:project-root",SortConfigurationCodec.encode(first))))
        assertEquals(latest,store.configuration("p:project-root"))
        store.replace(listOf(com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity("p:project-root",SortConfigurationCodec.encode(latest))))
        assertEquals(latest,store.configuration("p:project-root"))
    }
    @Test fun loadingPreservesLegacyLayerExceptionsUntilExplicitInheritanceAndRejectsMalformedFlags() {
        val store=NodeSortPreferences(onConfigWrite={_,_->})
        assertFalse(store.loaded)
        store.replace(listOf(com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity("p:project-root","DUE_ASC"),
            com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity("p:layer","INHERIT",true)))
        assertTrue(store.loaded)
        assertTrue(store.configuration("p:layer").layersFirst)
        assertTrue(store.configuration("p:layer").inherited)
        store.inherit("p:layer")
        assertFalse(store.configuration("p:layer").layersFirst)
        assertTrue(store.layersFirst("p:layer")) // Presentation stays as applied until Apply.
        assertEquals(SortCriterion.DUE,store.configuration("p:layer").rules.first().criterion)
        val valid=config(rule(SortCriterion.TAG))
        val json=org.json.JSONObject(SortConfigurationCodec.encode(valid).removePrefix(SortConfigurationCodec.PREFIX))
        json.put("manual","true")
        assertTrue(runCatching {SortConfigurationCodec.decode(SortConfigurationCodec.PREFIX+json)}.isFailure)
    }

}
