package com.r0ybt.arachn0de.ui.state

import android.app.Application
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class EffortSortTest {
    private fun config(vararg rules:SortRule)=SortConfiguration(false,rules.toList()+SortCriterion.entries.filter {c->rules.none {it.criterion==c}}.map {SortRule(it)})
    private fun node(id:String,level:Int,position:Int=0,done:Boolean=false,verify:Boolean=false,due:Long?=null)=Node(id,"p",null,id,"",done,position,1,1,false,effort=level,awaitingVerification=verify,dueAt=due)
    @Test fun bothDirectionsPutUndefinedLastStableTiesAndCompletedLast() {
        val rows=listOf(node("undefined",0),node("five",5),node("one",1),node("tie1",3,1),node("tie2",3,2),node("done",5,done=true))
        for(reverse in listOf(false,true)) {
            val value=config(SortRule(SortCriterion.EFFORT,true,reverse))
            val result=rows.sortedWith(HierarchicalSort.comparator(value)).map {it.id}
            assertEquals(if(reverse)listOf("one","tie1","tie2","five","undefined","done") else listOf("five","tie1","tie2","one","undefined","done"),result)
        }
        val mixed=listOf(node("verify",5,verify=true),node("low",1,due=20),node("highLate",5,due=30),node("highEarly",5,due=10))
        assertEquals(listOf("highEarly","highLate","low","verify"),mixed.sortedWith(HierarchicalSort.comparator(config(SortRule(SortCriterion.STATE,true),SortRule(SortCriterion.EFFORT,true),SortRule(SortCriterion.DUE,true)))).map {it.id})
    }
    @Test fun historicalSevenRulesAndBackupsDefaultEffortToZeroWithoutRewrite() {
        val current=JSONObject(SortConfigurationCodec.encode(config(SortRule(SortCriterion.NAME,true))).removePrefix(SortConfigurationCodec.PREFIX))
        val array=current.getJSONArray("rules");array.remove(array.length()-1)
        val old=SortConfigurationCodec.PREFIX+current.toString()
        assertFalse(SortConfigurationCodec.decode(old).rules.single {it.criterion==SortCriterion.EFFORT}.enabled)
        val data=BackupData("test",1,listOf(ProjectEntity("p","Núcleo","",0,1,1)),listOf(NodeEntity("task","p",null,"Tarea","",false,0,1,1)),emptyList(),emptyList(),emptyMap(),nodeSortPreferences=listOf(NodeSortPreferenceEntity("p:project-root",old)))
        val bytes=BackupJson.encode(data)
        assertEquals(56,JSONObject(String(bytes)).getInt("dataVersion"))
        val decoded=BackupJson.decode(bytes)
        assertEquals(0,decoded.nodes.single().effort);assertEquals(old,decoded.nodeSortPreferences.single().mode)
        val fresh=config(SortRule(SortCriterion.EFFORT,true,true))
        assertEquals(fresh,SortConfigurationCodec.decode(SortConfigurationCodec.encode(fresh)))
    }
}
