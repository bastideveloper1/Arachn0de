package com.r0ybt.arachn0de.document

import com.r0ybt.arachn0de.domain.model.DescriptionParser.Format
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal Office Open XML package; images are embedded, never external links or rigid tables. */
internal object ReportDocx {
    private fun xml(s:String)=buildString {s.codePoints().forEach {c->when(c) {38->append("&amp;");60->append("&lt;");62->append("&gt;");34->append("&quot;");else->if(c==9||c==10||c==13||c in 32..0xd7ff||c in 0xe000..0xfffd||c in 0x10000..0x10ffff)appendCodePoint(c)}}}
    private fun run(text:String,formats:Set<Format> = emptySet())="<w:r><w:rPr>${if((Format.Bold in formats || Format.Title in formats))"<w:b/>" else ""}${if(Format.Title in formats)"<w:sz w:val=\"26\"/>" else ""}${if(Format.Highlight in formats)"<w:highlight w:val=\"yellow\"/>" else ""}${if(Format.Code in formats)"<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:shd w:fill=\"E8ECF0\"/>" else ""}${if(Format.Italic in formats)"<w:i/>" else ""}${if(Format.Underline in formats)"<w:u w:val=\"single\"/>" else ""}</w:rPr><w:t xml:space=\"preserve\">${xml(text)}</w:t></w:r>"
    private fun p(text:String,style:String="Normal")="<w:p><w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>${run(text)}</w:p>"
    private fun field(code:String,result:String)="<w:r><w:fldChar w:fldCharType=\"begin\" w:dirty=\"true\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> ${xml(code)} </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>${run(result)}<w:r><w:fldChar w:fldCharType=\"end\"/></w:r>"
    fun write(content:ReportContent,options:ReportOptions,output:OutputStream,check:()->Unit={}) {
        options.validate()
        val ids=content.images.keys.withIndex().associate {it.value to it.index+1}
        var drawingId=0;var imageIndex=0
        fun drawing(part:ReportPart.Image):String {
            val image=content.images.getValue(part.id);val full=options.images==ReportImageLayout.FULL
            val available=(ReportLayout.RIGHT-ReportLayout.MARGIN)
            var width=if(full)available else available*.30f;var height=width*image.height/image.width
            val maxHeight=if(full)ReportLayout.BOTTOM-ReportLayout.MARGIN-12f else 300f
            if(height>maxHeight) {width*=maxHeight/height;height=maxHeight}
            val cx=(width*12700).toLong();val cy=(height*12700).toLong();val id=++drawingId
            val left=options.images==ReportImageLayout.LEFT||(options.images==ReportImageLayout.AUTO&&imageIndex++%2==1)
            val graphic="""<wp:extent cx="$cx" cy="$cy"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:docPr id="$id" name="Imagen $id" descr="${xml(part.label)}"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$id" name="Imagen $id"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdImage${ids.getValue(part.id)}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>"""
            return if(full)"<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">$graphic</wp:inline></w:drawing></w:r>"
            else """<w:r><w:drawing><wp:anchor distT="0" distB="76200" distL="127000" distR="127000" simplePos="0" relativeHeight="$id" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="0"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="column"><wp:align>${if(left)"left" else "right"}</wp:align></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV>${graphic.substringBefore("<wp:docPr")}<wp:wrapSquare wrapText="bothSides"/>${graphic.substringAfter("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>")}</wp:anchor></w:drawing></w:r>"""
        }
        val document=buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>""")
            append(p(options.title,"Title"));if(options.subtitle.isNotBlank())append(p(options.subtitle,"Subtitle"));append(p("Fecha del informe: ${options.date}","Metadata"))
            if(options.cover)append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            if(options.index&&content.sections.size>1) {
                append(p("Índice","IndexTitle"));append("<w:p><w:r><w:fldChar w:fldCharType=\"begin\" w:dirty=\"true\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> TOC \\o &quot;1-9&quot; \\h \\z \\u </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r></w:p>")
                content.sections.drop(1).forEachIndexed {index,s->check();append("<w:p><w:pPr><w:pStyle w:val=\"TOC${s.depth.coerceIn(1,9)}\"/><w:tabs><w:tab w:val=\"right\" w:leader=\"dot\" w:pos=\"10140\"/></w:tabs></w:pPr><w:hyperlink w:anchor=\"section${index+1}\">${run("${s.number} ${s.title}")}</w:hyperlink><w:r><w:tab/></w:r>${field("PAGEREF section${index+1} \\h","—")}</w:p>")}
                append("<w:p><w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>")
            }
            content.sections.forEachIndexed {i,s->check()
                if(i>0)append("<w:p><w:pPr><w:pStyle w:val=\"Heading${s.depth.coerceIn(1,9)}\"/></w:pPr><w:bookmarkStart w:id=\"$i\" w:name=\"section$i\"/>${run("${s.number} ${s.title}")}<w:bookmarkEnd w:id=\"$i\"/></w:p>")
                s.metadata.forEach {append(p(it,"Metadata"))}
                var open=false;var floated=false
                fun openP() {if(!open) {append("<w:p><w:pPr><w:pStyle w:val=\"Normal\"/><w:contextualSpacing/></w:pPr>");open=true}}
                fun closeP(clear:Boolean=true) {if(open) {if(floated&&clear)append("<w:r><w:br w:type=\"textWrapping\" w:clear=\"all\"/></w:r>");append("</w:p>");open=false;floated=false}}
                s.parts.forEach {part->check();when(part) {
                    is ReportPart.Image->{closeP();openP();append(drawing(part));floated=options.images!=ReportImageLayout.FULL;if(!floated)closeP()}
                    is ReportPart.Text->{openP();part.text.split('\n').forEachIndexed {line,text->if(line>0)append("<w:r><w:br/></w:r>");append(run(text.replace("\r",""),part.formats))}}
                }};closeP()
            }
            append("""<w:sectPr><w:footerReference w:type="default" r:id="rIdFooter"/><w:pgSz w:w="11900" w:h="16840"/><w:pgMar w:top="880" w:right="880" w:bottom="960" w:left="880" w:header="360" w:footer="360" w:gutter="0"/></w:sectPr></w:body></w:document>""")
        }
        val styles=buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/><w:color w:val="202020"/><w:lang w:val="es-ES"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="80" w:line="276" w:lineRule="auto"/><w:widowControl/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>""")
            fun style(id:String,size:Int,bold:Boolean=false,outline:Int?=null) {append("<w:style w:type=\"paragraph\" w:styleId=\"$id\"><w:name w:val=\"$id\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/><w:pPr>${if(outline!=null)"<w:keepNext/><w:keepLines/><w:outlineLvl w:val=\"$outline\"/>" else ""}</w:pPr><w:rPr><w:sz w:val=\"$size\"/>${if(bold)"<w:b/>" else ""}</w:rPr></w:style>")}
            style("Title",48,true);style("Subtitle",26);style("Metadata",18);style("IndexTitle",32,true)
            for(level in 1..9) {style("Heading$level",if(level==1)32 else 26,true,level-1);append("<w:style w:type=\"paragraph\" w:styleId=\"TOC$level\"><w:name w:val=\"toc $level\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:ind w:left=\"${(level-1)*180}\"/></w:pPr></w:style>")}
            append("</w:styles>")
        }
        ZipOutputStream(output).use {zip->
            fun entry(name:String,text:String) {check();zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray(Charsets.UTF_8));zip.closeEntry()}
            entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/><Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/></Types>""")
            entry("_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdDocument" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/document.xml",document);entry("word/styles.xml",styles)
            entry("word/settings.xml","""<?xml version="1.0" encoding="UTF-8"?><w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:updateFields w:val="true"/></w:settings>""")
            entry("word/footer1.xml","""<?xml version="1.0" encoding="UTF-8"?><w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:jc w:val="center"/></w:pPr>${run("Página ")}${field("PAGE","1")}${run(" de ")}${field("NUMPAGES","1")}</w:p></w:ftr>""")
            entry("word/_rels/document.xml.rels",buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rIdSettings" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/><Relationship Id="rIdFooter" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""")
                ids.forEach {(_,id)->append("<Relationship Id=\"rIdImage$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image$id.png\"/>")};append("</Relationships>")
            })
            content.images.forEach {(key,image)->check();zip.putNextEntry(ZipEntry("word/media/image${ids.getValue(key)}.png"));zip.write(image.bytes);zip.closeEntry()}
        }
    }
}
