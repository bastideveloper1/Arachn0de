package com.r0ybt.arachn0de.ui.theme

import org.junit.Assert.*
import org.junit.Test

class AppearanceColdStartTest {
    @Test fun enumCanInitializeBeforeFileFacadeAsOnLockedStartup() {
        // A separate loader preserves the cold-start order regardless of other tests.
        val parent=javaClass.classLoader!!
        val cold=object:ClassLoader(parent) {
            override fun loadClass(name:String,resolve:Boolean):Class<*> {
                if(!name.startsWith("com.r0ybt.arachn0de.ui.theme."))return super.loadClass(name,resolve)
                synchronized(this) {
                    val loaded=findLoadedClass(name) ?: parent.getResourceAsStream(name.replace('.','/')+".class")!!.use {
                        val bytes=it.readBytes();defineClass(name,bytes,0,bytes.size)
                    }
                    if(resolve)resolveClass(loaded)
                    return loaded
                }
            }
        }
        val themes=Class.forName("com.r0ybt.arachn0de.ui.theme.AppearanceTheme",true,cold)
        assertTrue(themes.enumConstants.any {(it as Enum<*>).name=="Arachn0de"})
        val facade=Class.forName("com.r0ybt.arachn0de.ui.theme.AppearanceKt",true,cold)
        val selectable=facade.getDeclaredMethod("getSelectableAppearanceThemes").invoke(null) as List<*>
        val names=selectable.map {(it as Enum<*>).name}
        assertTrue("Arachn0de" in names);assertTrue("PureBlack" in names)
        assertFalse(names.any {it in setOf("DarkGrey","BlackAndWhite","Graphite")})
    }
}
