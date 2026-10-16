package com.r0ybt.arachn0de.sharing

import android.app.Application
import android.content.Intent
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.graphics.Color
import com.r0ybt.arachn0de.domain.model.DescriptionParser
import com.r0ybt.arachn0de.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class SharedWebLinksTest {
    @Test fun urlsRemainCompleteAndFormattingAndInternalImagesKeepTheirOwnActions() {
        val url="https://example.com/a__b__c?x=1&y=dos"
        val id="11111111-1111-1111-1111-111111111111"
        val source="**Negrita** *cursiva* __subrayado__ $url [[arachnode:image:$id|Foto]]"
        var web:String?=null;var image:String?=null
        val rendered=descriptionBody(DescriptionParser.parse(source),emptyMap(),Color.Blue,{image=it},{web=it})
        assertTrue(rendered.text.contains(url));assertTrue(rendered.text.contains("Negrita cursiva subrayado"))
        val links=rendered.getLinkAnnotations(0,rendered.length).map {it.item as LinkAnnotation.Clickable}
        assertEquals(2,links.size);assertNull(web);assertNull(image)
        links.first {it.tag==url}.let {it.linkInteractionListener!!.onClick(it)};assertEquals(url,web)
        links.first {it.tag==id}.let {it.linkInteractionListener!!.onClick(it)};assertEquals(id,image)
        assertTrue(rendered.spanStyles.any {it.item.fontWeight==androidx.compose.ui.text.font.FontWeight.Bold})
        assertEquals("$url",DescriptionParser.parse("__$url"+"__").joinToString("") {it.text})
    }
    @Test fun onlyExplicitWebLinksOpenBrowsableIntentAndReceivingNeverOpensAnything() {
        val context=RuntimeEnvironment.getApplication()
        openDescriptionWebLink(context,"javascript:alert(1)");assertNull(shadowOf(context).nextStartedActivity)
        openDescriptionWebLink(context,"file:///private");assertNull(shadowOf(context).nextStartedActivity)
        openDescriptionWebLink(context,"https://example.com/a?x=1")
        val intent=shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW,intent.action);assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("https://example.com/a?x=1",intent.dataString)
    }
}
