# Diagramas — Fase 2: modelos profesionales

## Implementación

20 modelos editables, agrupados en seis categorías:

| Categoría | Modelos |
| --- | --- |
| Usuarios | Inicio de sesión; Registro de usuario; Recuperar contraseña; Editar perfil |
| Contenido | Buscar contenido; Publicar artículo; Editar o eliminar contenido; Crear tema y responder en foro |
| Comercio | Buscar y seleccionar producto; Compra en línea |
| Desarrollo | CRUD completo; Función con condiciones; Arquitectura web; Solicitud HTTPS |
| Redes y organización | Red doméstica; Flujo entre departamentos |
| Bases de datos | Usuarios; Blog; Tienda; Foro |

La galería existente muestra categorías, nombre y descripción de hasta dos líneas, y miniaturas reales mediante el renderizador actual. Mantiene un botón de lienzo vacío. Los cinco ejemplos anteriores continúan disponibles en Desarrollo, además de los 20 modelos nuevos.

Cada selección ejecuta una fábrica que crea UUID nuevos para documento, nodos, conexiones y campos. El catálogo no se modifica ni se almacena como documentos del usuario. Se reutilizan edición, historial, guardado, reapertura y exportación existentes. No se crean imágenes estáticas ni adjuntos.

Los modelos incluyen decisiones y rutas alternativas, responsabilidades mediante encabezados editables, CRUD con cuatro ramas independientes, arquitectura estática, HTTPS sobre TCP/TLS (HTTP/1.1 o HTTP/2), topología LAN ramificada y un flujo entre departamentos con correcciones. Compra en línea exige confirmación válida de pago y representa confirmación de pedido/stock idempotente; las contraseñas se representan mediante hash, nunca en texto plano.

Los cuatro modelos ERD contienen exclusivamente tablas, campos, tipos libres y marcas informativas PK/FK. No añaden conexiones, cardinalidades, referencias entre columnas, SQL ni modificaciones del editor de tablas. Las marcas dibujan llaves pequeñas: PK con cabeza sólida y un diente; FK con cabeza hueca y dos dientes. Usan la tinta del documento y conservan los booleanos actuales. El mismo dibujo se utiliza en lienzo, miniaturas y exportaciones.

La barra lateral incorpora «Zoom y desplazamiento», enlazado al mismo modo PAN/MOVE que Opciones → Vista. No se añade otro estado o motor. Los modelos nuevos abren con un encuadre completo cuando su escala es legible; de lo contrario, muestran el comienzo a escala mínima 0,45. «Ajustar a pantalla» permite ver el conjunto; el zoom permite recorrer los modelos extensos.

## Compatibilidad y alcance

No cambian Room, SQLCipher, preferencias, namespaces, formato de respaldo ni versiones de aplicación. Los documentos utilizan el códec existente: versión 2 para conexiones con caras, o 3 cuando contienen tablas. Los ejemplos históricos y documentos anteriores mantienen sus lectores. Se verificó la recuperación de los 20 modelos y un documento histórico mediante el respaldo existente, incluyendo rechazo de referencias rotas sin reemplazar datos válidos.

No se modifica Wireframe. El componente compartido de galería recibe parámetros opcionales de número de líneas, con valores predeterminados que conservan su comportamiento anterior en otras pantallas. Sin nuevas dependencias, archivos privados ni ciclos de limpieza. No se generó APK oficial ni se intervino firma o credenciales.

## Archivos de esta fase

- `diagrams/DiagramModels.kt`: catálogo, composición explícita con nodos/puntos editables y encuadre inicial.
- `diagrams/DiagramScreen.kt`: categorías, modelos, descripciones y acceso lateral al zoom.
- `diagrams/DiagramRenderer.kt`: llaves PK/FK usando los datos actuales.
- `visual/ToolLibraryUi.kt`: límites opcionales de líneas para nombres y descripciones.
- `diagrams/DiagramModelsTest.kt`: validación de los 20 modelos, independencia, historial, guardado, renderizado, miniaturas, geometría, respaldos y llaves.
- `diagrams/DiagramUiTest.kt`: acceso por galería y sincronización del zoom; adaptación del acceso a ejemplos antiguos dentro de la galería ampliada.

## Validación y revisión

Se compilaron Kotlin de depuración y las pruebas. Las 11 comprobaciones focalizadas distintas terminaron superadas; el último ajuste repitió únicamente las dos verificaciones visuales afectadas, con `BUILD SUCCESSFUL`. Se ejecutaron comprobaciones focalizadas del catálogo completo, creación/edición/guardado/reapertura, UUID independientes, campos y conexiones, ausencia de superposición de nodos, decisiones ramificadas, renderizado PNG y miniaturas, compatibilidad histórica y respaldo/restauración. Se reutilizaron las pruebas de tablas, biblioteca cifrada y cámara/pellizco. Una prueba de interfaz verifica la sincronización bidireccional entre el acceso lateral y Opciones → Vista.

Durante la validación se corrigieron recorridos que atravesaban otros nodos y textos demasiado largos. Los desvíos se expresan como puntos de recorrido existentes; no se cambia el motor geométrico. Se inspeccionaron exportaciones reales de Inicio de sesión, Compra en línea, Red doméstica, CRUD y Blog ERD.

Pendiente: revisión visual física en Pixel 7a y con fuentes ampliadas, especialmente desplazamiento entre categorías, escala de apertura y comodidad al recorrer modelos grandes. Los modelos son puntos de partida conceptuales editables, no especificaciones exhaustivas. Las exportaciones visuales no sustituyen al respaldo editable.
