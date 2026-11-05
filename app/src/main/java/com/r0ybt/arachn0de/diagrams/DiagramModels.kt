package com.r0ybt.arachn0de.diagrams

/** Factories, never saved prototypes: every selection receives fresh document, node and field IDs. */
internal data class DiagramModel(val id:String,val name:String,val category:String,val description:String,val create:()->DiagramDocument)
internal object DiagramModels {
    val categories=listOf("Usuarios","Contenido","Comercio","Desarrollo","Redes y organización","Bases de datos")
    val items=listOf(
        model("login","Inicio de sesión",0,"Validaciones, sesión y rutas de error",::login),
        model("register","Registro de usuario",0,"Registro y verificación de correo",::register),
        model("reset","Recuperar contraseña",0,"Enlace temporal y actualización segura",::reset),
        model("profile","Editar perfil",0,"Consulta, permisos y actualización",::profile),
        model("search","Buscar contenido",1,"Filtros, consulta y resultados",::search),
        model("publish","Publicar artículo",1,"Borrador, publicación y permisos",::publish),
        model("edit","Editar o eliminar contenido",1,"Edición y eliminación confirmada",::edit),
        model("forum","Crear tema y responder en foro",1,"Categorías, permisos y respuestas",::forum),
        model("product","Buscar y seleccionar producto",2,"Catálogo, filtros y disponibilidad",::product),
        model("checkout","Compra en línea",2,"Stock, pago confirmado y pedido",::checkout),
        model("crud","CRUD completo",3,"Cuatro operaciones independientes",::crud),
        model("function","Función con condiciones",3,"Validación, alternativas y retorno",::function),
        model("architecture","Arquitectura web",3,"Componentes y responsabilidades",::architecture),
        model("https","Solicitud HTTPS",3,"DNS, TCP/TLS y aplicación",::https),
        model("network","Red doméstica",4,"Internet y dispositivos de la LAN",::network),
        model("workflow","Flujo entre departamentos",4,"Aprobación, ejecución y revisión",::workflow),
        model("erd-users","Usuarios",5,"Usuarios, perfiles y sesiones") {erd("Usuarios",listOf(
            table("Usuarios","id:INT:P","correo:TEXT","hash_contrasena:TEXT","activo:BOOLEAN","creado:DATE"),
            table("Perfiles","id:INT:P","usuario_id:INT:F","nombre:TEXT","biografia:TEXT","avatar:TEXT"),
            table("Sesiones","id:TEXT:P","usuario_id:INT:F","token_hash:TEXT","expira:DATE","revocada:BOOLEAN")))},
        model("erd-blog","Blog",5,"Artículos, categorías y comentarios") {erd("Blog",listOf(
            table("Usuarios","id:INT:P","nombre:TEXT","correo:TEXT","hash_contrasena:TEXT"),
            table("Artículos","id:INT:P","autor_id:INT:F","categoria_id:INT:F","titulo:TEXT","contenido:TEXT","publicado:BOOLEAN"),
            table("Categorías","id:INT:P","nombre:TEXT","descripcion:TEXT"),
            table("Comentarios","id:INT:P","articulo_id:INT:F","autor_id:INT:F","contenido:TEXT","creado:DATE")))},
        model("erd-store","Tienda",5,"Productos, pedidos y sus detalles") {erd("Tienda",listOf(
            table("Usuarios","id:INT:P","nombre:TEXT","correo:TEXT","direccion:TEXT"),
            table("Productos","id:INT:P","nombre:TEXT","precio:DECIMAL","stock:INT","activo:BOOLEAN"),
            table("Pedidos","id:INT:P","usuario_id:INT:F","estado:TEXT","total:DECIMAL","creado:DATE"),
            table("Detalles de pedido","id:INT:P","pedido_id:INT:F","producto_id:INT:F","cantidad:INT","precio_unitario:DECIMAL")))},
        model("erd-forum","Foro",5,"Usuarios, categorías, temas y respuestas") {erd("Foro",listOf(
            table("Usuarios","id:INT:P","nombre:TEXT","correo:TEXT","hash_contrasena:TEXT"),
            table("Categorías","id:INT:P","nombre:TEXT","descripcion:TEXT"),
            table("Temas","id:INT:P","categoria_id:INT:F","autor_id:INT:F","titulo:TEXT","creado:DATE"),
            table("Respuestas","id:INT:P","tema_id:INT:F","autor_id:INT:F","contenido:TEXT","creado:DATE")))},
    )
    private fun model(id:String,name:String,category:Int,description:String,create:()->DiagramDocument)=DiagramModel(id,name,categories[category],description,create)
}

/** Use an overview when readable; otherwise start at the first component, with direct fit/zoom available. */
internal fun openDiagramModel(model:DiagramModel,widthDp:Float,heightDp:Float):DiagramDocument {
    val d=model.create()
    if(model.id.startsWith("basic:"))return d
    val overview=fitDiagram(d,widthDp,heightDp)
    if(overview.scale>=.45f)return d.copy(camera=overview)
    val first=d.nodes.first {it.shape!=DiagramShape.TEXT}
    return d.copy(camera=DiagramCamera(first.x+(widthDp/2-24)/(d.width*.45f),first.y+(heightDp/2-24)/(d.height*.45f),.45f))
}

/** Small authoring helper using the existing normalized nodes and explicit graph edges. No layout engine. */
private class ModelPage(val name:String,val rows:Int,val columns:Int=4) {
    val width=1600;val height=rows*160+140
    val nodes=linkedMapOf<String,DiagramNode>();val edges=mutableListOf<DiagramEdge>()
    private val step=1500f/columns
    fun node(id:String,text:String,col:Int,row:Int,decision:Boolean=false) {
        nodes[id]=DiagramNode(text=text,x=(50+col*step)/width,y=(100+row*160f)/height,w=(step-70)/width,h=105f/height,
            shape=if(decision)DiagramShape.DIAMOND else DiagramShape.ROUND,font=28f,fill=0xff20242a.toInt(),border=0xff888888.toInt(),ink=0xffeeeeee.toInt())
    }
    fun headings(vararg names:String) {names.forEachIndexed {i,text->nodes["heading$i"]=DiagramNode(shape=DiagramShape.TEXT,text=text,x=(50+i*step)/width,y=20f/height,w=(step-70)/width,h=50f/height,font=30f,ink=0xffeeeeee.toInt())}}
    fun edge(a:String,b:String,label:String="",arrow:Boolean=true) {
        val from=nodes.getValue(a);val to=nodes.getValue(b)
        val sameRow=kotlin.math.abs(from.y-to.y)<.0001f
        val sideA=if(sameRow) {if(from.x<to.x)DiagramAnchor.RIGHT else DiagramAnchor.LEFT} else if(from.y<to.y)DiagramAnchor.BOTTOM else DiagramAnchor.TOP
        val sideB=if(sameRow) {if(from.x<to.x)DiagramAnchor.LEFT else DiagramAnchor.RIGHT} else if(from.y<to.y)DiagramAnchor.TOP else DiagramAnchor.BOTTOM
        edges+=DiagramEdge(from=from.id,to=to.id,label=label,route=DiagramRoute.ORTHOGONAL,fromAnchor=sideA,toAnchor=sideB,endArrow=arrow,color=0xffbbbbbb.toInt())
    }
    // Explicit bends authored for a model, using existing editable edge points.
    fun route(a:String,b:String,from:DiagramAnchor,to:DiagramAnchor,vararg bends:Pair<Double,Double>) {
        val i=edges.indexOfFirst {it.from==nodes.getValue(a).id&&it.to==nodes.getValue(b).id}
        edges[i]=edges[i].copy(route=DiagramRoute.STRAIGHT,fromAnchor=from,toAnchor=to,points=bends.map {(col,row)->DPoint(((50+col*step+(step-70)/2)/width).toFloat(),((152.5+row*160)/height).toFloat())})
    }
    fun chain(vararg ids:String) {ids.toList().zipWithNext().forEach {(a,b)->edge(a,b)}}
    fun done()=DiagramDocument(name=name,nodes=nodes.values.toList(),edges=edges,page=DiagramPage(width=width,height=height)).also(DiagramCodec::validate)
}
private fun login():DiagramDocument=ModelPage("Inicio de sesión",9).apply {
    headings("Frontend","Backend","Base de datos","Errores")
    node("input","Introducir\ncredenciales",0,0);node("valid","Validar campos",0,1);node("fields","¿Campos\nválidos?",0,2,true)
    node("request","Solicitud HTTPS",1,2);node("validate","Validar solicitud",1,3);node("query","Consultar usuario",2,3)
    node("user","¿Existe y está\nhabilitado?",2,4,true);node("hash","Verificar contraseña\ncontra hash",1,4);node("correct","¿Contraseña\ncorrecta?",1,5,true)
    node("session","Crear sesión",1,6);node("response","Respuesta autenticada",1,7);node("access","Mostrar acceso",0,7)
    node("localError","Mostrar errores\nde campos",0,3);node("invalid","Solicitud inválida",3,3);node("denied","Error de acceso\ngenérico",3,5);node("showError","Frontend muestra\nerror",3,7)
    chain("input","valid","fields");edge("fields","request","Sí");edge("fields","localError","No");chain("request","validate");edge("validate","query","Válida");edge("query","user")
    edge("validate","invalid","Inválida");edge("user","hash","Sí");edge("user","denied","No");edge("hash","correct");edge("correct","session","Sí");edge("correct","denied","No");chain("session","response","access");chain("invalid","denied","showError")
    route("validate","invalid",DiagramAnchor.RIGHT,DiagramAnchor.LEFT,1.5 to 3.0,1.5 to 2.5,2.5 to 2.5,2.5 to 3.0)
}.done()
private fun register():DiagramDocument=ModelPage("Registro de usuario",9).apply {
    headings("Frontend","Backend","Base de datos","Correo / errores")
    node("form","Formulario de registro",0,0);node("valid","¿Campos válidos?",0,1,true);node("bad","Corregir campos",0,2)
    node("check","Consultar correo",1,1);node("available","¿Correo disponible?",2,1,true);node("duplicate","Correo duplicado",3,1)
    node("hash","Generar hash seguro\nde contraseña",1,2);node("create","Crear usuario\npendiente",1,3);node("store","Guardar en BDD",2,3)
    node("token","Generar token\nde verificación",1,4);node("mail","Enviar correo",3,4);node("confirm","Abrir y confirmar\nenlace",3,5)
    node("verify","¿Token válido\ny vigente?",2,5,true);node("expired","Enlace inválido\no vencido",3,6);node("activate","Activar cuenta\nen BDD",2,6);node("result","Mostrar resultado",2,7)
    edge("form","valid");edge("valid","bad","No");edge("valid","check","Sí");edge("check","available");edge("available","duplicate","No");edge("available","hash","Sí");chain("hash","create","store","token","mail","confirm","verify");edge("verify","expired","No");edge("verify","activate","Sí");edge("activate","result")
}.done()
private fun reset():DiagramDocument=ModelPage("Recuperar contraseña",8).apply {
    headings("Solicitud","Backend","Recuperación","Resultado")
    node("request","Solicitar recuperación",0,0);node("generic","Respuesta genérica:\nsi existe, llegará correo",0,1)
    node("lookup","Buscar cuenta\ninternamente",1,0);node("exists","¿Cuenta habilitada?",1,1,true);node("stop","Sin envío; misma\nrespuesta pública",1,2)
    node("token","Generar token temporal\ny guardar su hash",2,1);node("mail","Enviar enlace",2,2);node("open","Abrir enlace",2,3);node("valid","¿Token válido\ny vigente?",2,4,true)
    node("error","Mostrar enlace\ninválido o vencido",3,4);node("new","Introducir y validar\ncontraseña nueva",1,4);node("hash","Actualizar hash\nde contraseña",1,5);node("invalidate","Invalidar token\ny sesiones previas",2,5);node("done","Confirmar cambio",2,6)
    edge("request","generic");edge("request","lookup");edge("lookup","exists");edge("exists","stop","No");edge("exists","token","Sí");chain("token","mail","open","valid");edge("valid","error","No");edge("valid","new","Sí");chain("new","hash","invalidate","done")
}.done()
private fun profile():DiagramDocument=ModelPage("Editar perfil",7).apply {
    headings("Frontend","Backend","Datos","Errores")
    node("request","Solicitar perfil",0,0);node("auth","¿Sesión válida?",1,0,true);node("query","Consultar perfil",2,0);node("form","Presentar formulario",0,1)
    node("edit","Modificar datos",0,2);node("validate","¿Cambios válidos?",1,2,true);node("permission","¿Puede editar\neste perfil?",1,3,true);node("update","Actualizar en BDD",2,3)
    node("reply","Devolver respuesta",1,4);node("done","Confirmar cambios",0,4);node("unauth","Pedir autenticación",3,0);node("bad","Mostrar validación",3,2);node("denied","Acceso denegado",3,3);node("failed","Error de guardado",3,4)
    edge("request","auth");edge("auth","query","Sí");edge("auth","unauth","No");chain("query","form","edit","validate");edge("validate","bad","No");edge("validate","permission","Sí");edge("permission","denied","No");edge("permission","update","Sí");edge("update","failed","Fallo");chain("update","reply","done")
    route("auth","unauth",DiagramAnchor.BOTTOM,DiagramAnchor.BOTTOM,1.0 to 0.5,3.0 to 0.5)
    route("permission","denied",DiagramAnchor.TOP,DiagramAnchor.TOP,1.0 to 2.5,3.0 to 2.5)
}.done()
private fun search():DiagramDocument=ModelPage("Buscar contenido",6).apply {
    headings("Frontend","Backend","Datos / índice","Respuesta")
    node("input","Introducir búsqueda",0,0);node("receive","Recibir consulta",0,1);node("valid","¿Consulta válida?",0,2,true);node("bad","Mostrar validación",0,3)
    node("request","Enviar solicitud",1,2);node("filters","Procesar filtros\ny paginación",1,3);node("query","Consultar BDD\no índice",2,3);node("results","¿Hay resultados?",2,4,true)
    node("prepare","Preparar resultados",3,3);node("empty","Preparar estado vacío",3,4);node("reply","Devolver al frontend",3,5);node("display","Mostrar resultados\no estado vacío",2,5)
    chain("input","receive","valid");edge("valid","bad","No");edge("valid","request","Sí");chain("request","filters","query","results");edge("results","prepare","Sí");edge("results","empty","No");edge("prepare","reply");chain("empty","reply","display")
    route("prepare","reply",DiagramAnchor.LEFT,DiagramAnchor.LEFT,2.5 to 3.0,2.5 to 5.0)
}.done()
private fun publish():DiagramDocument=ModelPage("Publicar artículo",7).apply {
    headings("Editor","Comprobaciones","Persistencia","Resultado")
    node("open","Abrir editor",0,0);node("content","Introducir contenido",0,1);node("valid","¿Contenido válido?",1,1,true);node("bad","Corregir contenido",0,2)
    node("permission","¿Tiene permiso?",1,2,true);node("denied","Permisos insuficientes",3,2);node("action","¿Borrador o publicar?",1,3,true)
    node("draft","Preparar borrador",0,4);node("public","Preparar publicación",2,4);node("store","Almacenar contenido\ny estado en BDD",1,5);node("done","Confirmar resultado",0,6);node("error","Error: conservar\ncontenido del editor",3,5)
    chain("open","content","valid");edge("valid","bad","No");edge("valid","permission","Sí");edge("permission","denied","No");edge("permission","action","Sí");edge("action","draft","Borrador");edge("action","public","Publicar");edge("draft","store");edge("public","store");edge("store","done","Guardado");edge("store","error","Fallo")
}.done()
private fun edit():DiagramDocument=ModelPage("Editar o eliminar contenido",7).apply {
    headings("Selección","Autorización","Edición","Eliminación")
    node("select","Seleccionar publicación",0,0);node("identity","Identificar usuario",1,0);node("permission","¿Tiene permiso?",1,1,true);node("denied","Acceso denegado",0,1)
    node("action","¿Editar o eliminar?",1,2,true);node("edit","Modificar contenido",2,2);node("valid","¿Cambios válidos?",2,3,true);node("bad","Corregir cambios",1,3);node("update","Actualizar en BDD",2,4)
    node("confirm","¿Confirmar eliminación?",3,2,true);node("cancel","Cancelar sin cambios",3,3);node("delete","Eliminar en BDD",3,4);node("done","Mostrar resultado",2,5);node("error","Mostrar fallo\nsin confirmar éxito",3,6)
    chain("select","identity","permission");edge("permission","denied","No");edge("permission","action","Sí");edge("action","edit","Editar");chain("edit","valid");edge("valid","bad","No");edge("valid","update","Sí");edge("action","confirm","Eliminar");edge("confirm","cancel","No");edge("confirm","delete","Sí");edge("update","done","Éxito");edge("delete","done","Éxito");edge("update","error","Fallo");edge("delete","error","Fallo")
    route("action","confirm",DiagramAnchor.TOP,DiagramAnchor.TOP,1.0 to 1.5,3.0 to 1.5)
    route("confirm","delete",DiagramAnchor.RIGHT,DiagramAnchor.RIGHT,3.5 to 2.0,3.5 to 4.0)
    route("update","error",DiagramAnchor.RIGHT,DiagramAnchor.LEFT,2.5 to 4.0,2.5 to 6.0)
}.done()
private fun forum():DiagramDocument=ModelPage("Crear tema y responder en foro",8).apply {
    headings("Autor","Validaciones","Foro / BDD","Resultados")
    node("category","Seleccionar categoría",0,0);node("topic","Redactar tema",0,1);node("valid","¿Tema válido?",1,1,true);node("bad","Corregir tema",1,0)
    node("permission","¿Puede crear tema?",1,2,true);node("denied","Permisos insuficientes",3,2);node("store","Guardar tema",2,2);node("show","Visualizar tema",2,3)
    node("reply","Redactar respuesta",0,3);node("replyValid","¿Respuesta válida?",1,4,true);node("replyBad","Corregir respuesta",0,4);node("replyPermission","¿Puede responder?",1,5,true);node("replyDenied","Respuesta no permitida",0,5)
    node("publish","Guardar y publicar\nrespuesta",2,5);node("done","Mostrar confirmación",2,6);node("error","Error de guardado",3,6)
    chain("category","topic","valid");edge("valid","bad","No");edge("valid","permission","Sí");edge("permission","denied","No");edge("permission","store","Sí");chain("store","show","reply","replyValid");edge("replyValid","replyBad","No");edge("replyValid","replyPermission","Sí");edge("replyPermission","replyDenied","No");edge("replyPermission","publish","Sí");edge("publish","done","Éxito");edge("publish","error","Fallo")
    route("permission","denied",DiagramAnchor.TOP,DiagramAnchor.TOP,1.0 to 1.5,3.0 to 1.5)
}.done()
private fun product():DiagramDocument=ModelPage("Buscar y seleccionar producto",7).apply {
    headings("Cliente","Backend","Catálogo","Resultado")
    node("search","Buscar productos",0,0);node("filters","Aplicar filtros",0,1);node("request","Enviar solicitud",1,1);node("query","Consultar catálogo",2,1);node("found","¿Hay resultados?",2,2,true)
    node("empty","Mostrar estado vacío",3,2);node("list","Presentar productos",1,2);node("select","Seleccionar producto",0,2);node("details","Solicitar detalles",0,3);node("load","Consultar ficha\ny disponibilidad",1,3)
    node("available","¿Disponible?",2,3,true);node("yes","Mostrar ficha\ny opción de compra",2,4);node("no","Mostrar sin stock",3,3);node("fail","Mostrar error\ny permitir reintentar",3,5)
    chain("search","filters","request","query","found");edge("found","empty","No");edge("found","list","Sí");chain("list","select","details","load","available");edge("available","yes","Sí");edge("available","no","No");edge("query","fail","Fallo de consulta")
    route("query","fail",DiagramAnchor.RIGHT,DiagramAnchor.RIGHT,3.5 to 1.0,3.5 to 5.0)
}.done()
private fun checkout():DiagramDocument=ModelPage("Compra en línea",10).apply {
    headings("Comprador","Tienda / backend","Datos / pedido","Pago / errores")
    node("cart","Revisar carrito\ny productos",0,0);node("valid","¿Carrito válido?",0,1,true);node("bad","Corregir carrito",0,2);node("stock","Consultar stock",1,1);node("available","¿Stock disponible?",2,1,true);node("short","Informar stock\ninsuficiente",3,1)
    node("identity","Identificar comprador",1,2);node("attempt","Crear intento único\ny reservar stock",1,3);node("pay","Solicitar pago\ncon clave idempotente",2,3);node("provider","Proveedor procesa\nel pago",3,3)
    node("confirm","Validar confirmación\ndel proveedor",3,4);node("approved","¿Pago aprobado\ny confirmado?",2,4,true);node("reject","Rechazar / pendiente:\nno marcar pagado",3,5)
    node("order","Confirmar pedido\nStock: transacción\nidempotente",2,6);node("notify","Notificar pedido",1,6);node("result","Mostrar compra\nconfirmada",0,6);node("release","Liberar reserva\nsi pago rechazado",3,6)
    node("failure","Fallo al procesar\nReconciliar el\nintento único",2,8);node("pending","Mostrar pendiente\nEvitar un cobro\nduplicado",3,8)
    edge("cart","valid");edge("valid","bad","No");edge("valid","stock","Sí");edge("stock","available");edge("available","short","No");edge("available","identity","Sí");chain("identity","attempt","pay","provider","confirm","approved");edge("approved","reject","No / pendiente");edge("reject","release","Rechazado");edge("approved","order","Sí");chain("order","notify","result");edge("order","failure","Fallo");edge("failure","pending")
}.done()
private fun crud():DiagramDocument=ModelPage("CRUD completo",6).apply {
    node("request","Solicitud de datos",1,0);node("op","¿Qué operación?",1,1,true)
    listOf("Create","Read","Update","Delete").forEachIndexed {i,label->
        node("validate$i","¿$label válido\ny autorizado?",i,2,true)
        node("data$i",when(i){0->"Crear registro";1->"Consultar registros";2->"Actualizar registro";else->"Eliminar registro"},i,3)
        node("result$i","Resultado / error\nde $label",i,4)
        edge("op","validate$i",label);edge("validate$i","data$i","Sí");edge("data$i","result$i")
        edge("validate$i","result$i","No")
        route("validate$i","result$i",DiagramAnchor.LEFT,DiagramAnchor.LEFT,(if(i==0)-.45 else i-.5) to 2.0,(if(i==0)-.45 else i-.5) to 4.0)
    }
    node("response","Respuesta al cliente",1,5);edge("request","op");repeat(4) {edge("result$it","response")}
}.done()
private fun function():DiagramDocument=ModelPage("Función con condiciones",7).apply {
    headings("Entrada","Procesamiento","Alternativas","Salida")
    node("input","Parámetros de entrada",0,0);node("valid","¿Entrada válida?",0,1,true);node("error","Retornar error\nde validación",0,2);node("condition","¿Cumple condición?",1,1,true)
    node("normal","Procesar ruta principal",1,2);node("alternate","Procesar alternativa",2,1);node("second","¿Requiere ajuste?",1,3,true);node("adjust","Aplicar ajuste",2,3);node("result","Preparar resultado",1,4);node("failed","Capturar error\nde procesamiento",3,4);node("return","Retornar resultado\no error controlado",2,5)
    edge("input","valid");edge("valid","error","No");edge("valid","condition","Sí");edge("condition","normal","Sí");edge("condition","alternate","No");chain("normal","second");edge("second","adjust","Sí");edge("second","result","No");edge("adjust","result");edge("alternate","result");edge("normal","failed","Excepción");edge("result","return");edge("failed","return")
    route("alternate","result",DiagramAnchor.RIGHT,DiagramAnchor.RIGHT,2.5 to 1.0,2.5 to 4.0)
    route("normal","failed",DiagramAnchor.RIGHT,DiagramAnchor.RIGHT,1.5 to 2.0,1.5 to 2.5,3.5 to 2.5,3.5 to 4.0)
}.done()
private fun architecture():DiagramDocument=ModelPage("Arquitectura web",6).apply {
    headings("Cliente","Acceso / API","Aplicación","Infraestructura")
    node("browser","Navegador",0,0);node("front","Frontend",0,2);node("api","API HTTPS",1,2);node("auth","Autenticación\ny autorización",1,0)
    node("business","Lógica de negocio",2,2);node("services","Servicios internos",2,4);node("db","Base de datos",3,1);node("storage","Almacenamiento\nde archivos",3,3);node("external","Servicios externos",3,5)
    edge("browser","front","Ejecuta",false);edge("front","api","Solicitud / respuesta",false);edge("api","auth","Verifica identidad",false);edge("api","business","Invoca",false);edge("business","db","Lee / escribe",false);edge("business","storage","Archivos",false);edge("business","services","Usa",false);edge("services","external","Integra",false)
}.done()
private fun https():DiagramDocument=ModelPage("Solicitud HTTPS",9).apply {
    headings("Navegador","Red: TCP / TLS","Servidor / aplicación","Respuesta")
    node("url","Introducir URL HTTPS",0,0);node("dns","Resolver DNS\nsi no está en caché",1,0);node("tcp","Establecer TCP\nsi no hay conexión",1,1);node("tls","Negociar TLS\ny validar certificado",1,2)
    node("secure","¿Canal seguro?",1,3,true);node("tlsError","Detener: error\nde conexión / TLS",0,3);node("http","Enviar HTTP/1.1 o HTTP/2\nsobre TLS",1,4);node("receive","Servidor recibe\nsolicitud",2,4);node("process","Procesar aplicación",2,5);node("data","Consultar datos\nsi corresponde",2,6)
    node("generate","Generar respuesta\ny código de estado",3,6);node("send","Enviar respuesta\npor canal seguro",3,7);node("display","Presentar resultado\no error HTTP",2,7)
    chain("url","dns","tcp","tls","secure");edge("secure","tlsError","No");edge("secure","http","Sí");chain("http","receive","process","data","generate","send","display")
}.done()
private fun network():DiagramDocument=ModelPage("Red doméstica",7).apply {
    node("internet","Internet",1,0);node("isp","ISP",1,1);node("ont","ONT / módem",1,2);node("router","Router / puerta de enlace",1,3);node("wifi","Punto de acceso Wi-Fi",0,4);node("switch","Switch LAN",2,4)
    node("phone","Teléfono",0,6);node("laptop","Laptop",1,5);node("pc","PC",2,6);node("nas","NAS / servidor",3,5);node("printer","Impresora",3,6)
    listOf("internet" to "isp","isp" to "ont","ont" to "router","router" to "wifi","router" to "switch","wifi" to "phone","wifi" to "laptop","switch" to "pc","switch" to "nas","switch" to "printer").forEach {(a,b)->edge(a,b,if(a=="wifi")"Wi-Fi" else "Enlace",false)}
    route("switch","printer",DiagramAnchor.RIGHT,DiagramAnchor.RIGHT,3.5 to 4.0,3.5 to 6.0)
}.done()
private fun workflow():DiagramDocument=ModelPage("Flujo entre departamentos",8).apply {
    headings("Solicitante","Coordinación","Operaciones","Calidad")
    node("request","Crear solicitud",0,0);node("receive","Recibir y registrar",1,0);node("evaluate","Evaluar solicitud",1,1);node("approve","¿Aprobada?",1,2,true);node("reject","Comunicar rechazo",0,2)
    node("assign","Asignar responsable",2,2);node("execute","Ejecutar trabajo",2,3);node("review","Revisar resultado",3,3);node("ok","¿Cumple requisitos?",3,4,true);node("correction","Solicitar correcciones",2,4);node("fix","Aplicar correcciones",2,5);node("close","Cerrar solicitud",3,6);node("notify","Notificar cierre",0,6)
    chain("request","receive","evaluate","approve");edge("approve","reject","No");edge("approve","assign","Sí");chain("assign","execute","review","ok");edge("ok","correction","No");chain("correction","fix","review");edge("ok","close","Sí");edge("close","notify")
    route("fix","review",DiagramAnchor.RIGHT,DiagramAnchor.RIGHT,3.5 to 5.0,3.5 to 3.0)
}.done()
private fun table(name:String,vararg fields:String)=name to fields.toList()
private fun erd(name:String,tables:List<Pair<String,List<String>>>):DiagramDocument {
    val nodes=tables.mapIndexed {i,(title,fields)->DiagramNode(shape=DiagramShape.TABLE,text=title,x=.04f+(i%2)*.49f,y=.04f+(i/2)*.49f,w=.43f,h=.40f,font=30f,
        fill=0xff20242a.toInt(),ink=0xffeeeeee.toInt(),border=0xff888888.toInt(),fields=fields.map {raw->val parts=raw.split(':');DiagramField(name=parts[0],type=parts[1],primaryKey=parts.getOrNull(2)=="P",foreignKey=parts.getOrNull(2)=="F")})}
    return DiagramDocument(name=name,nodes=nodes,page=DiagramPage(width=1600,height=1400)).also(DiagramCodec::validate)
}
