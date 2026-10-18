package com.r0ybt.arachn0de.wireframe

internal data class WireScreen(val id:String,val label:String,val group:String,val format:WireFormat) {
    fun elements():List<WireElement> = WireScreenLayout(format).apply {build(id)}.elements
    fun preview()=WireDocument(name=label,format=format,elements=elements())
}

/** Screen IDs are catalog resources only: confirmed drawings contain ordinary editable elements. */
internal object WireScreens {
    val items=buildList {
        fun group(label:String,format:WireFormat,vararg screens:String) {
            screens.forEach {val (id,name)=it.split('=',limit=2);add(WireScreen(id,name,label,format))}
        }
        group("Acceso y formularios",WireFormat.MOBILE,"login=Inicio de sesión","register=Registro de usuario","recover=Recuperación de contraseña","contact=Formulario de contacto","wizard=Formulario por pasos")
        group("Organización y productividad",WireFormat.MOBILE,"dashboard=Dashboard con tarjetas","tasks=Lista de tareas","kanban=Tablero Kanban","calendar=Calendario","sidebar=Dashboard con menú lateral desplegable")
        group("Comercio y catálogos",WireFormat.MOBILE,"shop=Tienda con productos","catalog=Catálogo en cuadrícula","product=Detalle de producto","cart=Carrito de compras","filters=Catálogo con filtros")
        group("Contenido e información",WireFormat.MOBILE,"article=Artículo informativo","info=Ficha con imagen y descripción","profile=Perfil público","gallery=Galería","docs=Página de documentación")
        group("Escritorio y web",WireFormat.DESKTOP,"web=Página con barra de navegación superior","desktop=Dashboard de escritorio","admin=Panel de administración","landing=Landing page","columns=Distribución de dos columnas")
    }
}
internal fun WireDocument.insert(screen:WireScreen):WireDocument {
    require(format==screen.format) {"Este molde requiere formato ${screen.format.label}."}
    val parts=screen.elements()
    require(elements.size+parts.size<=128) {"El molde supera el límite de 128 elementos del dibujo."}
    return copy(elements=elements+parts)
}

private class WireScreenLayout(private val format:WireFormat) {
    val elements=mutableListOf<WireElement>()
    private val blue=0xff315cbbL;private val pale=0xffedf2fcL;private val grey=0xfff1f3f5L
    private fun part(id:String,x:Float,y:Float,w:Float,h:Float,text:String?=null,fill:Long?=null,ink:Long?=null,font:Float?=null) {
        val base=WireTemplates.items.first {it.id==id}.element(format)
        // Keep screen typography readable in narrow calendar/table cells without changing components.
        val requestedFont=font ?: base.font
        val screenFont=if(base.kind.textOnly && !id.startsWith("shape_")) {
            val unit=minOf(w*format.width/400f,h*format.height/112f).coerceIn(.35f,1.5f)
            (minOf(requestedFont,h*format.height*.72f)/unit).coerceIn(8f,240f)
        } else requestedFont
        elements+=base.copy(x=x,y=y,w=w,h=h,text=text ?: base.text,fill=fill ?: base.fill,ink=ink ?: base.ink,font=screenFont)
    }
    private fun title(text:String,y:Float,x:Float=.06f,w:Float=.88f,h:Float=.055f)=part("title",x,y,w,h,text,font=60f)
    private fun text(text:String,x:Float,y:Float,w:Float,h:Float=.045f,font:Float=40f)=part("paragraph",x,y,w,h,text,font=font)
    private fun box(x:Float,y:Float,w:Float,h:Float,color:Long=grey)=part("shape_ROUND_RECT",x,y,w,h,fill=color)
    private fun field(label:String,y:Float,id:String="bordered",x:Float=.08f,w:Float=.84f,h:Float=.065f)=part(id,x,y,w,h,label)
    private fun button(label:String,y:Float,x:Float=.08f,w:Float=.84f,h:Float=.065f)=part("rounded",x,y,w,h,label)
    private fun image(x:Float,y:Float,w:Float,h:Float)=part("placeholder",x,y,w,h,fill=0xffe3e8f0)
    private fun avatar(x:Float,y:Float,w:Float)=part("avatar",x,y,w,w*format.width/format.height)
    private fun header(label:String,id:String="top")=part(id,0f,0f,1f,if(format==WireFormat.MOBILE) .08f else .09f,label,fill=pale)
    private fun bottom()=part("bottom_labels",0f,.925f,1f,.075f,"Inicio\nAgenda\nPerfil",fill=pale,ink=blue)
    private fun product(x:Float,y:Float,w:Float,name:String,price:String,h:Float=.27f) {
        image(x,y,w,h*.60f)
        text(name,x,y+h*.65f,w,h*.15f,40f)
        text(price,x,y+h*.83f,w,h*.15f,44f)
    }
    private fun stat(x:Float,y:Float,w:Float,label:String,value:String,h:Float=.13f) {
        box(x,y,w,h,pale);text(label,x+.015f,y+.012f,w-.03f,h*.28f,36f)
        part("title",x+.015f,y+h*.43f,w-.03f,h*.42f,value,font=80f)
    }
    private fun task(y:Float,label:String,checked:Boolean=false) = part(if(checked) "check_on" else "check_off",.07f,y,.86f,.07f,label)
    fun build(id:String) {
        when(id) {
            "login"->{
                avatar(.40f,.10f,.20f);title("Bienvenido",.25f);text("Accede a tu espacio de trabajo",.08f,.32f,.84f)
                field("Correo electrónico",.42f);field("••••••••",.52f,"password")
                part("check_on",.08f,.62f,.84f,.06f,"Recordarme");button("Iniciar sesión",.72f)
                part("text_button",.08f,.83f,.84f,.06f,"¿Olvidaste tu contraseña?")
            }
            "register"->{
                header("Crear cuenta","back");title("Únete a nosotros",.12f)
                field("Nombre completo",.23f);field("Correo electrónico",.34f)
                field("Contraseña",.45f,"password");field("Confirmar contraseña",.56f,"password")
                part("check_off",.08f,.68f,.84f,.06f,"Acepto los términos de uso");button("Registrarme",.79f)
                text("¿Ya tienes cuenta? Inicia sesión",.08f,.89f,.84f)
            }
            "recover"->{
                header("Recuperar acceso","back");image(.30f,.15f,.40f,.20f)
                title("¿Olvidaste tu contraseña?",.42f,h=.08f)
                text("Te enviaremos un enlace para restablecer tu acceso.",.08f,.54f,.84f,.10f)
                field("Tu correo electrónico",.69f);button("Enviar enlace",.80f)
                part("text_button",.08f,.90f,.84f,.05f,"Volver al inicio de sesión")
            }
            "contact"->{
                header("Contacto","back");title("Conversemos",.12f)
                field("Tu nombre",.22f);field("Correo de respuesta",.32f);field("Asunto",.42f,"select")
                field("Escribe tu mensaje…",.53f,"multiline",h=.20f)
                button("Enviar mensaje",.79f);text("Nuestro equipo responderá a tu correo.",.08f,.89f,.84f)
            }
            "wizard"->{
                header("Nueva solicitud","back");part("progress",.08f,.12f,.84f,.015f,ink=blue)
                text("Paso 2 de 3 · Detalles",.08f,.16f,.84f);title("Cuéntanos tu idea",.24f)
                field("Nombre del proyecto",.35f);field("Tipo de proyecto",.45f,"select")
                field("Describe lo que necesitas",.56f,"multiline",h=.18f)
                part("outline",.08f,.84f,.38f,.065f,"Anterior");button("Siguiente",.84f,x=.54f,w=.38f)
            }
            "dashboard"->{
                header("Mi espacio","menu");field("Buscar en el proyecto",.12f,"search")
                stat(.06f,.23f,.42f,"Pendientes","12");stat(.52f,.23f,.42f,"Completadas","28")
                title("Tu próxima tarea",.41f);task(.51f,"Revisar propuesta");task(.62f,"Preparar presentación")
                task(.73f,"Enviar resumen",true);bottom()
            }
            "tasks"->{
                header("Mis tareas","menu");part("segmented",.06f,.12f,.88f,.06f,"Hoy       Semana       Todas")
                field("Buscar tareas",.22f,"search")
                listOf("Revisar pendientes","Llamar al equipo","Diseñar pantalla","Preparar entrega","Actualizar notas","Enviar reporte").forEachIndexed {i,t->task(.32f+i*.085f,t,i==0)}
                part("fab",.80f,.83f,.13f,.13f*format.width/format.height);bottom()
            }
            "kanban"->{
                header("Tablero del proyecto","menu")
                listOf("Pendiente","En proceso","Completado").forEachIndexed {i,status->
                    val y=.12f+i*.26f
                    box(.05f,y,.90f,.235f,if(i==1) pale else grey);text(status,.08f,y+.012f,.84f,.045f,48f)
                    part("card_text",.08f,y+.072f,.40f,.135f,listOf("Boceto inicial\nPrioridad alta","Revisión UX\nEquipo de diseño","Propuesta lista\nEntrega aprobada")[i],fill=0xffffffff)
                    part("card_text",.52f,y+.072f,.40f,.135f,listOf("Crear contenido\nNueva tarea","Prueba visual\nEn revisión","Resumen final\nCompletado")[i],fill=0xffffffff)
                };bottom()
            }
            "calendar"->{
                header("Calendario","menu");title("Octubre 2026",.12f)
                listOf("L","M","X","J","V","S","D").forEachIndexed {i,t->text(t,.06f+i*.127f,.22f,.115f,.04f,60f)}
                for(day in 1..31) {val index=day+2;val x=.06f+(index%7)*.127f;val y=.28f+(index/7)*.075f
                    if(day==18) box(x,y,.115f,.06f,pale)
                    text("$day",x,y,.115f,.06f,66f)
                }
                text("Próximos eventos",.06f,.75f,.88f);part("list_icon",.06f,.82f,.88f,.065f,"Reunión de equipo · 10:00");bottom()
            }
            "sidebar"->{
                header("Resumen del proyecto","menu");part("side",.03f,.12f,.30f,.76f,"Inicio\nProyectos\nEquipo\nAjustes",fill=pale)
                text("Vista general",.38f,.13f,.56f,.05f,54f);stat(.38f,.23f,.56f,"Tareas activas","18",.15f)
                stat(.38f,.43f,.56f,"Avance","72%",.15f);part("card_text",.38f,.65f,.56f,.20f,"Actividad reciente\nNueva entrega disponible")
            }
            "shop"->{
                header("Mi tienda");field("Buscar productos",.12f,"search");image(.06f,.23f,.88f,.17f)
                title("Novedades",.44f);product(.06f,.53f,.42f,"Mochila urbana","$39.990");product(.52f,.53f,.42f,"Audífonos","$24.990")
                bottom()
            }
            "catalog"->{
                header("Catálogo");field("Buscar en el catálogo",.12f,"search")
                for(row in 0..2) for(col in 0..2) {val n=row*3+col+1
                    product(.05f+col*.31f,.24f+row*.22f,.28f,"Producto $n","$${n*4990}",.19f)
                };bottom()
            }
            "product"->{
                header("Detalle de producto","back");image(.06f,.12f,.88f,.29f)
                title("Mochila para cada día",.45f);part("chip",.06f,.53f,.34f,.05f,"$39.990")
                text("Ligera, resistente y con espacio para lo esencial. Disponible en varios colores.",.06f,.62f,.88f,.11f)
                part("segmented",.06f,.76f,.54f,.06f,"S        M        L");part("counter",.65f,.76f,.29f,.06f,"1")
                button("Añadir al carrito",.87f)
            }
            "cart"->{
                header("Tu carrito","back")
                listOf("Mochila","Audífonos","Cuaderno").forEachIndexed {i,t->val y=.14f+i*.18f
                    image(.06f,y,.18f,.18f*format.width/format.height);text(t,.28f,y,.63f)
                    text("$${(i+1)*9990}",.28f,y+.055f,.30f);part("counter",.64f,y+.055f,.28f,.06f,"1")
                    part("separator",.06f,y+.14f,.88f,.005f)
                }
                text("Subtotal                    $59.940",.06f,.72f,.88f);text("Envío                         $3.990",.06f,.78f,.88f)
                button("Continuar al pago",.88f)
            }
            "filters"->{
                header("Explorar productos","back");field("Buscar",.12f,"search")
                listOf("Todo","Ropa","Hogar").forEachIndexed {i,t->part("chip",.06f+i*.30f,.23f,.27f,.05f,t)}
                field("Ordenar: menor precio",.32f,"select");text("Rango de precio",.06f,.42f,.88f)
                part("slider",.06f,.48f,.88f,.045f,ink=blue)
                product(.06f,.59f,.42f,"Producto filtrado","$12.990",.23f);product(.52f,.59f,.42f,"Otra opción","$18.990",.23f)
                button("Ver resultados",.88f)
            }
            "article"->{
                header("Lectura","back");title("Una idea que vale la pena compartir",.12f,h=.10f)
                avatar(.06f,.25f,.10f);text("Por Alex · 5 min de lectura",.20f,.26f,.74f)
                image(.06f,.35f,.88f,.22f);text("Una nueva perspectiva",.06f,.62f,.88f,.05f,52f)
                text("El comienzo de una buena historia invita a conocer más. Aquí puedes desarrollar la idea principal de tu artículo.",.06f,.70f,.88f,.12f)
                part("text_button",.06f,.88f,.88f,.065f,"Compartir artículo")
            }
            "info"->{
                header("Información","back");image(.06f,.12f,.88f,.29f)
                title("Conoce el proyecto",.46f);text("Una solución para el día a día",.06f,.54f,.88f)
                text("Describe aquí el propósito, las características y los beneficios. Utiliza esta ficha para comunicar lo esencial.",.06f,.63f,.88f,.13f)
                part("tag",.06f,.80f,.30f,.045f,"Información");button("Más detalles",.89f)
            }
            "profile"->{
                header("Perfil público","back");image(0f,.08f,1f,.20f);avatar(.39f,.23f,.22f)
                title("Alex Rivera",.37f);text("Diseño y tecnología · Santiago",.10f,.45f,.80f)
                stat(.06f,.53f,.27f,"Posts","24",.10f);stat(.365f,.53f,.27f,"Seguidores","180",.10f);stat(.67f,.53f,.27f,"Siguiendo","96",.10f)
                button("Seguir",.68f);part("tabs",.06f,.78f,.88f,.055f,"Publicaciones\nAcerca de\nFotos")
                part("list_icon",.06f,.86f,.88f,.055f,"Mi primera publicación");bottom()
            }
            "gallery"->{
                header("Galería","back");field("Buscar fotografías",.12f,"search")
                image(.06f,.24f,.42f,.23f);image(.52f,.24f,.42f,.15f);image(.52f,.43f,.42f,.24f);image(.06f,.51f,.42f,.16f)
                image(.06f,.71f,.88f,.17f)
            }
            "docs"->{
                header("Documentación","menu");field("Buscar en la guía",.12f,"search")
                box(.06f,.24f,.88f,.16f,pale);text("Contenido\n1. Instalación\n2. Primeros pasos",.09f,.26f,.82f,.12f)
                title("Instalación",.45f)
                text("Sigue estos pasos para comenzar a utilizar el proyecto.",.06f,.53f,.88f,.08f)
                box(.06f,.64f,.88f,.10f);part("shape_TEXT",.09f,.66f,.82f,.06f,"$ instalar proyecto",font=44f)
                text("Primeros pasos",.06f,.79f,.88f,.05f,50f);text("Crea tu primer espacio y comparte tus ideas.",.06f,.86f,.88f,.08f)
            }
            "web"->{
                header("Mi sitio\nInicio    Servicios    Contacto","desktop")
                title("Una solución para tu equipo",.18f,x=.05f,w=.53f,h=.11f)
                text("Organiza tus proyectos y comunica tus ideas con claridad.",.05f,.34f,.51f,.12f,48f)
                button("Conocer más",.48f,x=.05f,w=.23f,h=.08f);image(.65f,.17f,.30f,.38f)
                listOf("Planifica","Colabora","Comparte").forEachIndexed {i,t->part("card_text",.05f+i*.31f,.67f,.28f,.22f,"$t\nTodo lo que tu equipo necesita",fill=pale,font=36f)}
            }
            "desktop"->{
                header("Workspace\nBuscar    Equipo    Mi cuenta","desktop");part("side",.02f,.13f,.16f,.81f,"Resumen\nProyectos\nReportes\nAjustes",fill=pale)
                stat(.22f,.15f,.22f,"Tareas","128",.15f);stat(.47f,.15f,.22f,"Entregas","24",.15f);stat(.72f,.15f,.24f,"Avance","78%",.15f)
                box(.22f,.38f,.46f,.49f);text("Actividad semanal",.24f,.40f,.42f,.06f,48f)
                repeat(6) {i->val height=.10f+i*.035f;part("shape_RECT",.25f+i*.064f,.80f-height,.04f,height,fill=blue)}
                part("card_text",.72f,.38f,.24f,.49f,"Últimos movimientos\nNueva entrega\nDiseño aprobado\nRevisión pendiente",fill=pale)
            }
            "admin"->{
                header("Administración\nUsuarios    Permisos    Mi cuenta","desktop");part("side",.02f,.13f,.16f,.81f,"Usuarios\nContenido\nReportes\nAjustes",fill=pale)
                title("Usuarios del equipo",.15f,x=.22f,w=.74f,h=.08f);field("Buscar usuario",.28f,"search",x=.22f,w=.48f,h=.07f);button("Añadir usuario",.28f,x=.75f,w=.21f,h=.07f)
                box(.22f,.40f,.74f,.07f,pale)
                listOf("Nombre","Correo","Rol","Estado").forEachIndexed {i,t->text(t,.235f+i*.18f,.415f,.16f,.045f,52f)}
                repeat(5) {row->val y=.51f+row*.08f
                    listOf("Usuario ${row+1}","u${row+1}@equipo.cl",if(row==0) "Admin" else "Editor","Activo").forEachIndexed {i,t->text(t,.235f+i*.18f,y,.16f,.05f,44f)}
                    part("separator",.22f,y+.06f,.74f,.005f)
                }
            }
            "landing"->{
                text("Tu marca",.05f,.025f,.25f,.06f,64f);text("Inicio    Ventajas    Contacto",.40f,.03f,.37f,.055f,42f);button("Comenzar",.02f,x=.81f,w=.14f,h=.07f)
                title("Convierte tus ideas en proyectos",.18f,x=.16f,w=.68f,h=.13f)
                text("Una forma simple de organizar, visualizar y compartir lo que quieres construir.",.22f,.35f,.56f,.12f,52f)
                button("Empieza hoy",.50f,x=.38f,w=.24f,h=.09f)
                listOf("Rápido","Simple","Compartible").forEachIndexed {i,t->part("card_text",.055f+i*.31f,.69f,.27f,.22f,"$t\nDiseñado para tu día a día",fill=pale)}
            }
            "columns"->{
                header("Vista de proyecto\nResumen    Información    Contacto","desktop")
                box(.04f,.16f,.44f,.76f);box(.52f,.16f,.44f,.76f,pale)
                image(.06f,.20f,.40f,.30f);title("El proyecto",.55f,x=.06f,w=.40f,h=.09f)
                text("Presenta el propósito y las características principales de tu solución.",.06f,.69f,.40f,.15f,48f)
                title("Solicitar información",.21f,x=.55f,w=.38f,h=.09f)
                field("Nombre",.37f,x=.55f,w=.38f,h=.08f);field("Correo",.50f,x=.55f,w=.38f,h=.08f)
                field("Mensaje",.63f,"multiline",x=.55f,w=.38f,h=.12f);button("Enviar",.81f,x=.55f,w=.38f,h=.07f)
            }
            else->error("Molde desconocido: $id")
        }
    }
}
