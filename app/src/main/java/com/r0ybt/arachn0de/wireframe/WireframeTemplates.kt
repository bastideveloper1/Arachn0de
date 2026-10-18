package com.r0ybt.arachn0de.wireframe

internal data class WireTemplate(val id:String,val label:String,val category:String,val kind:WireKind,val width:Float,val height:Float) {
    fun element(format:WireFormat):WireElement {
        val circular=id in setOf("circle_button","fab","avatar","spinner") || kind in setOf(WireKind.CIRCLE,WireKind.SQUARE,WireKind.ICON)
        val text=when(category) {
            "Botones"->if(circular) "" else if(id=="destructive") "Eliminar" else "Continuar"
            "Entradas"->when(id) {"hint"->"Ej.: nombre@correo.cl";"password"->"••••••••";"search"->"Buscar";"multiline"->"Escribe aquí\nMás información";"select"->"Seleccionar";else->"Escribe aquí |"}
            else->when(id) {"title"->"Título";"subtitle"->"Subtítulo";"paragraph"->"Una breve descripción del contenido.\nOtra línea de texto para tu diseño.";"label","tag"->"Etiqueta";"badge"->"3";"chip"->"Opción";"card_text","card_image"->"Título\nDescripción del contenido";"empty"->"Todavía no hay contenido";"success"->"Cambios guardados";"warning"->"Revisa esta información";"error"->"No se pudo completar";"dialog","info"->"Título del diálogo\nInformación para continuar";"date"->"18 / 10 / 2026";"time"->"12 : 30";"counter"->"1";"side","dropdown","bottom_labels","tabs"->"Inicio\nBuscar\nPerfil";"desktop"->"Mi sitio\nInicio    Productos    Contacto";"context"->"Copiar\nDuplicar\nEliminar";"segmented"->"Día       Semana       Mes";"top","back","menu"->"Mi aplicación";"list","list_icon"->"Elemento de lista";"snackbar"->"Cambios guardados";else->if(kind.textCapable) label else ""}
        }
        return WireElement(kind=kind,template=id,w=(width/format.width).coerceAtMost(.9f),h=(height/format.height).coerceIn(.005f,.9f),text=text,
            fill=when {id=="destructive"->0xffc43b3b;id=="disabled"->0xffdddddd;category=="Botones" && id !in setOf("outline","text_button")->0xff315cbb;id in setOf("outline","text_button") || kind.textOnly || kind==WireKind.ICON->0x00ffffff;else->0xffeeeeee},
            ink=if(category=="Botones" && id !in setOf("outline","text_button","disabled")) 0xffffffff else 0xff222222,
            border=if(id=="outline") 3f else 0f,radius=if(id=="pill") 300f else 20f,font=if(id=="title") 56f else 32f,
            align=if(category=="Entradas" || kind.textOnly) "LEFT" else "CENTER",lockAspect=circular,icon=if(id=="search") "search" else "plus")
    }
}
internal object WireTemplates {
    val items=buildList {
        fun group(category:String,kind:WireKind,width:Float,height:Float,vararg entries:String) {
            entries.forEach {entry->val (id,label)=entry.split('=',limit=2);add(WireTemplate(id,label,category,kind,width,height))}
        }
        group("Botones",WireKind.BUTTON,400f,112f,"button=Botón rectangular","rounded=Botón redondeado","pill=Botón píldora","outline=Botón de contorno","text_button=Botón de texto","text_icon=Texto e icono","disabled=Botón desactivado","destructive=Acción destructiva")
        group("Botones",WireKind.CIRCLE_BUTTON,112f,112f,"circle_button=Botón circular","fab=Botón flotante")
        group("Entradas",WireKind.INPUT,640f,140f,"input=Campo simple","bordered=Campo con borde","filled=Campo relleno","labeled=Etiqueta superior","hint=Texto de ejemplo","search=Buscar con lupa","password=Contraseña","select=Campo desplegable","leading=Icono inicial")
        group("Entradas",WireKind.MULTILINE,640f,260f,"multiline=Campo multilínea")
        group("Navbar",WireKind.TOP_BAR,900f,140f,"top=Barra con título","back=Barra con botón atrás","menu=Barra con menú","toolbar=Barra de herramientas","desktop=Menú de escritorio")
        group("Navbar",WireKind.BOTTOM_BAR,900f,160f,"bottom=Barra inferior","bottom_labels=Iconos y etiquetas","tabs=Barra de pestañas")
        group("Navbar",WireKind.SIDE_MENU,400f,700f,"side=Menú lateral")
        group("Navbar",WireKind.CONTEXT_MENU,440f,320f,"dropdown=Menú desplegable")
        group("Contenedores",WireKind.CARD,640f,320f,"card=Tarjeta simple","card_text=Tarjeta con descripción","panel=Panel","section=Sección")
        group("Contenedores",WireKind.CARD,640f,500f,"card_image=Tarjeta con imagen")
        group("Contenedores",WireKind.CARD,640f,120f,"list=Fila de lista","list_icon=Fila con icono")
        group("Contenedores",WireKind.ROUND_BUTTON,240f,80f,"chip=Chip","tag=Etiqueta","badge=Insignia")
        group("Contenedores",WireKind.SEPARATOR,700f,12f,"separator=Separador")
        group("Selección",WireKind.CHECKBOX,400f,100f,"check_off=Checkbox desmarcado","check_on=Checkbox marcado","radio_off=Radio desmarcado","radio_on=Radio marcado","switch_off=Switch apagado","switch_on=Switch encendido")
        group("Selección",WireKind.SELECT,600f,120f,"slider=Slider","segmented=Selector segmentado","counter=Contador −/+","date=Selector de fecha","time=Selector de hora")
        group("Ventanas y estados",WireKind.DIALOG,720f,400f,"dialog=Confirmación","info=Información","error=Error","sheet=Modal inferior")
        group("Ventanas y estados",WireKind.CONTEXT_MENU,460f,320f,"context=Menú contextual")
        group("Ventanas y estados",WireKind.CARD,700f,140f,"snackbar=Aviso breve","success=Mensaje de éxito","warning=Advertencia")
        group("Ventanas y estados",WireKind.RECT,700f,32f,"progress=Barra de progreso")
        group("Ventanas y estados",WireKind.CIRCLE,120f,120f,"spinner=Indicador de carga")
        group("Ventanas y estados",WireKind.CARD,700f,360f,"empty=Estado vacío")
        group("Recursos visuales",WireKind.TITLE,700f,100f,"title=Título")
        group("Recursos visuales",WireKind.SUBTITLE,700f,80f,"subtitle=Subtítulo")
        group("Recursos visuales",WireKind.PARAGRAPH,700f,230f,"paragraph=Párrafo")
        group("Recursos visuales",WireKind.LABEL,400f,80f,"label=Etiqueta de texto")
        group("Recursos visuales",WireKind.ICON,100f,100f,"icon=Icono")
        group("Recursos visuales",WireKind.IMAGE,600f,400f,"image=Imagen")
        group("Recursos visuales",WireKind.PLACEHOLDER,600f,400f,"placeholder=Marcador de imagen")
        group("Recursos visuales",WireKind.CIRCLE,140f,140f,"avatar=Avatar circular")
        group("Recursos visuales",WireKind.LINE,700f,12f,"divider=Línea divisoria")
        group("Recursos visuales",WireKind.RECT,220f,60f,"pages=Indicador de página")
        listOf(WireKind.RECT,WireKind.SQUARE,WireKind.ROUND_RECT,WireKind.CIRCLE,WireKind.LINE,WireKind.TEXT,WireKind.IMAGE).forEach {kind->add(WireTemplate("shape_${kind.name}",kind.label,"Formas y texto",kind,if(kind in setOf(WireKind.CIRCLE,WireKind.SQUARE)) 180f else 600f,if(kind==WireKind.LINE) 12f else 180f))}
    }
}
internal fun WireDocument.insert(template:WireTemplate):WireDocument {
    require(elements.size<128) {"Máximo 128 elementos por prototipo."}
    val element=template.element(format)
    val positions=sequence {for(y in 0..12) for(x in 0..8) yield(x*.1f to y*.07f)}
    val position=positions.firstOrNull {(x,y)->x+element.w<=1 && y+element.h<=1 && elements.none {x<it.x+it.w && x+element.w>it.x && y<it.y+it.h && y+element.h>it.y}}
        ?: ((.04f*(elements.size%5)) to (.04f*(elements.size%7)))
    return copy(elements=elements+element.copy(x=position.first,y=position.second).bounded())
}

internal val WireElement.editableText:Boolean get()=kind.textCapable && template !in setOf("circle_button","fab","toolbar","bottom","slider","card")
