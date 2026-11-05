# Diagramas: edición visual, documentos y flujos

Arachn0de **0.6.0/code20**, Room **48**, respaldo JSON **40**. Funcionamiento offline,
cifrado existente y sin dependencias nuevas. Este trabajo no genera APK ni publica
cambios.

## Interfaz modular y reutilización

Tres categorías permanentes: **Elementos · Conexiones · Opciones**. Seleccionar un
nodo abre únicamente su fila contextual; las herramientas abren subpaneles. Cuerpo
inferior máximo 176 dp o 24% de la altura útil, el menor, con scroll vertical; grupos
horizontales y herramientas 48×48 dp. Herramientas con tooltip, descripción semántica
y estado seleccionado. Temas actuales, incluido Green Bash, usan LocalAppearancePalette.
Atrás retrocede un nivel y luego minimiza. Confirmar vuelve un nivel; la flecha y el
arrastre descendente minimizan sin descartar cambios. Salir protege trabajo sin guardar.

Se reutilizan ModularPanelSurface, importCanvasImage, EditHistory, geometría viewport
compartida y adaptadores wireSmartAlign/wireCompositionLines. No se modifica Wireframe.
La selección múltiple conserva ocho acciones de alineación/distribución. Cuadrícula
visible y ajuste magnético son independientes; guías e imán también. Alinear requiere
dos nodos; distribuir, tres y espacio suficiente.

Elementos: rectángulo, redondeado, elipse, rombo, texto e importación PNG/JPEG.
Importar muestra imagen nueva y miniaturas de biblioteca. Imágenes pueden reemplazarse
sin perder identidad del nodo. Conexiones: puertos, números y selección por botón,
indicador o trayectoria. Numeración estable por UUID durante la sesión, incluso tras
eliminar/deshacer; no forma parte del documento ni de las exportaciones.
Trayectorias recta/ortogonal/curva; trazo continuo/discontinuo/punteado; flechas inicial,
final, ambas o ninguna; etiqueta, color, grosor y puntos intermedios editables.

La restauración de instancia conserva documento, cámara, selección y subpanel, pero
no sustituye Guardar ni conserva la pila de historial después de terminar el proceso.

## Diseño definitivo: ampliación de edición y documento

La estructura modular y el diario de exportación se conservan.

### Movimiento y límites: causa comprobada en código

El documento siempre tuvo tamaño finito (antes 1600×1000). Además existían dos
problemas de gesto: se buscaba el nodo después de superar el umbral táctil y un
resultado vacío activaba pan; el puntero se recortaba antes de calcular el delta.
Eso podía desplazar visualmente todos los nodos y producir desplazamientos erróneos
cerca del borde. Ahora el objetivo se captura en DOWN y el delta usa coordenadas sin
recortar. SELECT no hace pan al arrastrar en blanco. PAN es explícito; MULTI conserva
su movimiento de grupo. La selección contextual se actualiza al finalizar el gesto
para evitar cambios de altura del escenario durante el arrastre.

El papel tiene contorno contrastante y exterior distinto. Solo se limita el resultado
contra el documento real, no contra la pantalla. El borde afectado se ilumina y se
avisa una vez por gesto; el mensaje superpuesto no altera el tamaño del lienzo.
Formato muestra dimensiones lógicas, ampliación explícita, ajuste al papel y vista
previa de encuadre. Libre es finito, ampliable dentro de los límites seguros; no se
presenta como infinito. Ampliar conserva posiciones y tamaños físicos.

### Estilo, tamaño y duplicación

Nuevos documentos: fondo #0D131B; nodos #1D2B3B, borde #6984A4 de 1,5 unidades,
texto #DCEAF8 de 28 unidades y radio 12. Las plantillas nuevas usan nodos de 18%×6,5%,
separación vertical 10% u horizontal 22% y escala inicial 0,5 dp/unidad. Se evita
amplificar automáticamente las tres tarjetas hasta ocupar toda la vista. No se
reorganizan documentos guardados. Documentos históricos conservan su fondo, relleno,
texto, borde equivalente al ink histórico, fuente 48 y radio 20.

Hay ocho tiradores laterales/esquinas con zonas táctiles de tamaño de pantalla.
El comportamiento normal conserva el borde opuesto; Desde el centro es explícito.
Mantener proporciones viene activado para imágenes. Ajustar contenedor conserva la
fuente y redistribuye líneas; Escalar contenido modifica uniformemente fuente y
geometría, con fuente mínima 12. Relleno, borde, grosor, radio y texto son independientes.
PNG propios mantienen transparencia y proporción sin tarjeta obligatoria.

Duplicar conserva la acción sin enlace. **Duplicar y conectar** busca un espacio
libre y añade nodo+conexión en una única entrada del historial. La copia queda
seleccionada: repetir crea una cadena sin volver a seleccionarla. Dirección permite
Automática/Abajo/Derecha/Arriba/Izquierda. Automática aprovecha la dirección existente
o la proporción del documento. Se heredan color, grosor, trazo y flujo de la última
conexión utilizada/adyacente; el enlace nuevo apunta a la copia. OFF permanece OFF.
No se desplazan nodos existentes. Sin espacio se ofrece ampliar, cambiar dirección
o colocación manual. Las referencias permanecen como UUID.

### Reproducción y paleta

Animación global contiene Desactivado, Por tramo, Secuencial y Simultáneo, repetición,
habilitación de conexiones y construcción del recorrido por números/UUID.
Desactivado conserva configuraciones. Secuencial sigue relaciones del grafo;
raíces ambiguas y bifurcaciones requieren elección. En ciclos se utiliza cada arista
una vez por vuelta; se repite únicamente si está habilitado el bucle global. El cierre de un ciclo
atraviesa también el nodo compartido antes de iniciar otra vuelta.
El mismo objeto/imagen continúa entre tramos, con transición por el nodo compartido.
Pausa/reanudar conserva tiempo; reiniciar vuelve al comienzo.

Cada flujo admite Normal/Luminoso/Pulso, color de partícula y halo independiente,
intensidad, tamaño, velocidad, dirección, cantidad y repetición. Halo automático
usa el color de partícula. Es compacto (radio 1,9×núcleo), con núcleo brillante y pulso
suave. Las imágenes móviles conservan alpha/proporción y halo optativo.

Selector avanzado: HSL, hexadecimal RRGGBB/AARRGGBB, muestra previa, Aplicar/Cancelar,
colores rápidos y hasta 32 favoritos compartidos entre documentos. Guardar favorito
es explícito y persistente, incluso si luego se cancela el cambio documental.
Degradado lineal: 2..8 colores editables, dirección 0..360°, vista previa y cinco
preajustes: Azul nocturno, Cian eléctrico, Verde oscuro, Ámbar suave, Violeta profundo.
Disponible para fondo y relleno de figuras; exportaciones utilizan el mismo shader.

### Proporción, dimensiones y exportación

| Formato | Dimensiones lógicas iniciales | Alta calidad PNG/MP4 |
|---|---:|---:|
| Libre | 1600×1000, ampliable | Tamaño lógico sujeto a límites de salida |
| Cuadrado 1:1 | 1000×1000 | 1080×1080 |
| Vertical 9:16 | 900×1600 | 1080×1920 |
| Vertical 4:5 | 1000×1250 | 1080×1350 |
| Horizontal 16:9 | 1600×900 | 1920×1080 |

Cambiar formato solicita confirmación: conservar posiciones físicas o ajustar todo
uniformemente. Si no cabe, conservar rechaza antes de alterar el documento.
Los lados lógicos admiten 320..4096 con producto máximo 8 millones; mínimo físico de
nodo 8 px. No se confunde resolución de salida con escala del editor.

Compartir usa lado mayor 960 en PNG/MP4 y 320 en GIF. Alta calidad GIF usa 640.
Personalizado permite elegir lado mayor. Se muestran dimensiones y duración finales antes de
seleccionar carpeta; se redondean a números pares, conservando proporción con el
error de redondeo. Máximo de salida 1920 por lado y 2,1 millones de píxeles (GIF640).
El codec H.264 del dispositivo verifica dimensiones admitidas; no se presupone FullHD.
Duración 1..60 s, GIF 10 fps, MP4 20 fps, hasta 96 MiB, fotogramas deterministas y memoria
acotada. El modo Secuencial exporta al menos un recorrido completo; Una vez incluye además
la salida de todas las partículas escalonadas. Un recorrido mayor
se rechaza explícitamente. Elegir ruta ambigua es obligatorio antes de exportar.
Etiquetas de imagen fuera del papel se rechazan antes de exportar para evitar recorte
silencioso. Se mantiene cancelación, limpieza y recuperación cifrada de parciales.

Todos los campos confirmados y favoritos se respaldan/restauran mediante el
namespace cifrado existente; sin archivos de dispositivo nuevos ni migraciones.

## Recursos y recuperación de exportaciones

Biblioteca cifrada: hasta 32 documentos, 48 PNG compartidos y 32 colores favoritos,
3 MiB por namespace,512 KiB por documento, 12 recursos distintos por diagrama,
128 nodos, 192 conexiones y 64 partículas activas. PNG canónicos SHA-256 con todos sus
bytes en backup; sin rutas absolutas. Eliminar documento no elimina recursos de la
colección personal. Quitar un recurso exige ausencia de referencias confirmadas.
Reemplazo, eliminación y backup usan la transacción cifrada y el bloqueo de archivos
común; no hay barridos por antigüedad ni nuevos archivos privados de diseño.

PNG/GIF/MP4 públicos son derivados, no respaldos editables. La carpeta se elige con
SAF. El diario cifrado de exportación registra únicamente operaciones propias,
confirmación y limpieza pendiente. Cancelar o fallar elimina solo el parcial de su
operación; si el proveedor rechaza borrarlo, se reintenta al abrir Diagramas. No se
eliminan salidas completadas ni otros archivos de la carpeta. El diario y permisos
SAF son estado operativo local excluido del backup; no caducan pendientes.
GIF usa paleta RGB332 y alpha binario; MP4 compone transparencia sobre blanco. Ningún
formato conserva selección, tiradores, cuadrícula ni otros controles del editor.

## Validación y dispositivo

La discrepancia 48/52 dp quedó diagnosticada como una medición artificial del
texto en Robolectric Legacy. La prueba geométrica usa ahora Native y conserva
su expectativa de 48 dp; el componente de producción permanece intacto. Véase
la investigación y validación al final de este documento.

La validación física pendiente incluye ergonomía de tiradores, legibilidad de tarjetas
con distintas densidades/fuentes y reproducción/exportación H.264 con codecs y
proveedores SAF reales. Las pruebas Robolectric verifican gestos simulados y fotogramas;
no sustituyen la comprobación del codificador de cada teléfono.

## Archivos de esta intervención

Código en `app/src/main/java/com/r0ybt/arachn0de/diagrams/`:

- `DiagramModel.kt`, `DiagramDesign.kt`, `DiagramRepository.kt`.
- `DiagramManipulation.kt`, `DiagramCanvas.kt`, `DiagramViewport.kt`, `DiagramEditing.kt`.
- `DiagramAnimation.kt`, `DiagramRenderer.kt`, `DiagramExport.kt`.
- `DiagramScreen.kt`, `DiagramTools.kt`, `DiagramPropertyTools.kt`, `DiagramPalette.kt`.

Pruebas modificadas o añadidas en `app/src/test/java/com/r0ybt/arachn0de/diagrams/`:
`DiagramDesignTest.kt`, `DiagramTest.kt`, `DiagramUiTest.kt`, `DiagramVisualTest.kt`.
Documentación: `DIAGRAMAS.md`, `ARCHITECTURE.md`, `BACKUP_FORMAT.md`.
El diario de exportación y las pruebas de exportación/refinamiento existentes se
reutilizan; no se modifican funcionalidades ajenas al módulo.

## Resultados dirigidos — 2026-10-19 (antes de corregir la medición compartida)

- Compilación `:app:compileDebugKotlin`: correcta.
- Suite completa `com.r0ybt.arachn0de.diagrams.*`: **54/54 aprobadas** con el código
  final; incluye gestos, cadena seleccionada, historial, ocho tiradores, cuatro
  bordes reales, formatos, perfiles, HSL/hexadecimal, favoritos, degradados anidados,
  grafo, ciclos continuos, imágenes móviles y restauración cifrada con rollback.
  Registro: `/tmp/diagram-verified-final.log`.
- Comprobación adicional de motor/ciclos/exportación: **24/24**, en
  `/tmp/diagram-cycle-final.log`, incluida también en la suite final.
- Regresiones WireframeEditingTest, WireframeVisualGeometryTest, ImageAnnotationsTest,
  ImageAnnotationUiTest, BottomModulesLayoutTest y ModularPanelTest: **37/38**.
  El único fallo de esas clases es el compartido 48/52 dp citado arriba.
  Registro conjunto: `/tmp/diagram-design-regressions.log`. Esa ejecución conjunta
  incluía además un fallo del selector de color; quedó corregido y sus pruebas
  aisladas e integradas pasan en la ejecución final de 54 pruebas.
- El selector se estabilizó fijando la anchura de su ventana respecto a la pantalla,
  evitando medidas alternantes del contenido. Conserva tres sliders HSL, hexadecimal,
  vista previa, confirmación/cancelación y navegación anidada del degradado.
- `git diff --check`: sin errores. Versión 0.6.0/code20, Room48 y JSON hasta 40 intactos.
  Sin APK, commit, push, tags ni publicación.

## Corrección de la discrepancia 48/52 dp — 2026-10-19

Prueba: `ModularPanelTest.compactMenuUsesHorizontalContentAndFallsBackForLargeFonts`,
en `app/src/test/java/com/r0ybt/arachn0de/ui/ModularPanelTest.kt`. Mide la variante
compacta de `ModularFieldMenu` en `ui/ModularPanel.kt`.

**Diagnóstico antes de modificar el repositorio:** fallo aislado reproducido (0/1).
Una prueba temporal externa al proyecto comparó idéntico contenido con GraphicsMode
LEGACY y NATIVE, SDK 28, densidad 1 y fontScale 1:

| Medición | Legacy | Native |
|---|---:|---:|
| Texto «Cualidades», una línea, estilo labelSmall con lineHeight 16 sp | 36 dp | 16 dp |
| Icono | 20 dp | 20 dp |
| Padding vertical total | 16 dp | 16 dp |
| Botón compacto | 52 dp | 48 dp |

La altura natural respeta `max(48, max(altoTexto,20)+16)`. Legacy produce
`max(48,36+16)=52`; con las métricas nativas resulta `max(48,20+16)=48`.
La diferencia visible de 4 dp procede de superar el mínimo táctil con métricas
artificiales del texto, no de un padding nuevo ni del tamaño del icono. No se cambia
52 por 48 en la implementación ni se relaja la expectativa original.

Git sitúa tanto el botón compacto (mínimo 48, padding 8 e icono 20) como la aserción
exacta de 48 en `7a84f13` (2026-10-16, release 0.4.2/code17). Ambos ya estaban juntos
sin anotación Native en esa revisión. `6151d17` conserva esas medidas y la aserción;
no había diferencias locales en ModularPanel.kt ni ModularPanelTest.kt al iniciar
esta investigación. No procede de Diagramas ni de cambios pendientes de Wireframe.
El historial permite fechar el origen del caso de prueba; no conserva resultados
de ejecución que permitan afirmar cuándo se notificó por primera vez.

**Corrección:** GraphicsMode.NATIVE únicamente para esta prueba geométrica, igual
que otras pruebas de tipografía del archivo. Se mantiene la comprobación exacta
48 dp y el paso a una columna al 200 %. Se añade una prueba Native con fuente 300 %
y etiqueta multilínea, comprobando altura superior al mínimo y contenido dentro
del padding, para impedir una futura corrección que fuerce 48 y recorte texto.
No se modifica ningún componente de producción, exportador ni área táctil.

Alcance: los consumidores compactos compartidos incluyen Personas y Etiquetas;
Wireframe usa también el menú compartido en su variante no compacta. Diagramas
reutiliza la superficie del panel. Todos conservan su código y comportamiento.
Evidencia de medición: `/tmp/panel-measurement-evidence.xml`; registros aislados:
`/tmp/panel-before.log` y `/tmp/panel-after-isolated.log`.

### Resultado de la corrección

- Antes: prueba aislada **0/1**; batería previamente registrada **37/38**.
- Después: prueba original aislada **1/1**, conservando la expectativa exacta 48 dp.
- Batería original: **38/38**; con la nueva prueba de fuente 300 %, **39/39**.
- Diagramas: **54/54**. Interfaz adicional de Wireframe: **16/16**.
- Ejecución final conjunta: **109/109**, cero fallos y cero errores.
  `:app:compileDebugKotlin` y `:app:testDebugUnitTest` completados correctamente;
  registro `/tmp/panel-final-regressions.log` y XML en `app/build/test-results/testDebugUnitTest/`.
- Archivos modificados para esta corrección: únicamente `ui/ModularPanelTest.kt`
  (pruebas) y este `DIAGRAMAS.md`. No hay cambios de producción, versión, Room ni backup,
  ni APK, commit o push. Se conservan los cambios previos del repositorio.
- Riesgo de cambio visual/táctil en producción: ninguno introducido por esta corrección.
  Una comprobación visual en dispositivo con fuentes grandes sigue siendo opcional;
  las verificaciones Native no sustituyen una revisión sobre fuentes OEM concretas.


## Corrección táctil: conexiones y alineación

La cabecera existente deja visibles Deshacer, Imán y Cuadrícula con
objetivos de 48 dp, sin desplazamiento. Eliminar queda fijo en la barra inferior,
sin copias contextuales: opera sobre figura, grupo o conexión seleccionados y se
desactiva sin selección. Mover es el modo inicial y solo cambia la posición;
Redimensionar activa los ocho tiradores de tamaño. Ambos accesos quedan fijos
en la misma barra inferior, con objetivos de 48 dp. Al salir de Conexiones se
recupera Mover. Guardar y Volver siguen en la cabecera; el nombre se muestra allí si hay ancho. Vista previa y navegación
global conservan acceso en Opciones. No se añaden barras ni paneles.

Tocar Conexiones activa directamente CONNECT; tocar otra sección o volver a tocar
Conexiones sale del modo. Una figura seleccionada muestra cuatro puntos centrales
de sus caras. Arrastrar hasta otra figura crea la conexión al soltar, mostrando
una línea provisional. La conexión seleccionada muestra extremos arrastrables
para cambiar figura o cara sin sustituir su identidad ni estilos. Un destino
inválido, el mismo nodo o cancelar el gesto conserva el documento. Al salir
regresan los tiradores de redimensionamiento existentes.

Las guías aparecen automáticamente al mover/redimensionar; Imán determina si
hay corrección geométrica y Cuadrícula activa el ajuste independiente a la malla.
La tolerancia se convierte desde dp al espacio del documento usando el viewport
actual. Se comparan centros y bordes reales de las otras figuras en ambos ejes,
incluidas las superiores; los tiradores no participan. Redimensionar conserva
anclajes opuestos, proporción y centro según las opciones existentes. Si la
proporción acopla ejes, las guías magnéticas se comprueban contra el resultado
final para no indicar una coincidencia que dejó de existir.

Las conexiones añaden únicamente fromAnchor/toAnchor opcionales (TOP, BOTTOM,
LEFT, RIGHT). Ausencia/null conserva el cálculo automático histórico. Se escribe
versión documental 2 solo al usar anclajes, y se admiten versiones 1/2. Datos
inválidos abortan antes de guardar o restaurar. El codec compartido incorpora los
anclajes al guardado y al backup cifrado existente; no cambia Room 48 ni JSON 40.
No hay archivos nuevos, imágenes nuevas, preferencias confirmadas de herramientas
ni migraciones. Estado de arrastre/guías queda en memoria. Reasignar extremos
conserva el recorrido explícito si sigue siendo válido; si se rompe su continuidad,
se limpia la selección de recorrido y se reutiliza la resolución del grafo.

Se mantienen versión de app y funcionalidades ajenas. No hay APK, commit ni push.


Validación final de la corrección táctil: compilación Kotlin Debug y **13/13**
pruebas dirigidas, cero fallos/errores. Incluye esquema antiguo/anclajes, extremos
renderizados, reasignación con estilos y puntos conservados, historial, centros y
bordes superiores, redimensionamiento, tolerancias de zoom, malla independiente,
respaldo cifrado/restauración/rollback, cabecera a 320 dp, eliminación/deshacer,
cuatro caras, reasignación táctil de destino, retorno a redimensionar, números de
conexiones y movimiento sin desplazar el lienzo. Seleccionar figuras/conexiones
en CONNECT mantiene estable el panel; propiedades se abren explícitamente desde
la conexión numerada. Sin suite general ni pruebas adicionales tras aprobar.
Log temporal: `/tmp/diagram-touch-final.log`. `git diff --check` correcto.
Pendiente únicamente comprobación física táctil; no se genera APK.


## Propiedades contextuales de geometría

Se retira el acceso independiente «Tamaño y posición». «Propiedades» expande el
panel contextual existente con Mantener proporciones, Desde el centro y las
opciones Ajustar contenedor / Escalar contenido. Tipografía y escalado de texto
se muestran para figuras y texto, y para imágenes que tengan etiqueta. Las
imágenes sin etiqueta conservan sus datos, sin controles tipográficos inoperantes.
«Ajustes precisos» se despliega dentro de ese panel y conserva X, Y, ancho y alto
en porcentajes, sus pasos y límites anteriores. Se inicia cerrado por elemento.
Mover, Redimensionar y Eliminar conservan sus accesos en la barra inferior;
cambiar de modo no borra la selección. No cambia el modelo ni la persistencia.


## Diagramas: usabilidad y tabla ERD

Mover, Redimensionar, Conectar, Elementos, Opciones y Eliminar usan seis iconos
fijos de 48 dp en la barra inferior. Conectar activa el modo sin abrir panel;
Opciones ofrece etiquetas de texto y mantiene la lista existente de conexiones
del documento. Historial contenía solo Rehacer, ahora accesible directamente allí.
Vista previa en vivo queda en las acciones superiores. El candado reutiliza
GlobalChromeActions a la derecha de la cabecera de biblioteca y editor.
El primer Guardar solicita nombre; Guardar actualiza el documento, Guardar copia
crea un UUID distinto y Exportar documento produce PNG/GIF/MP4 mediante SAF.
El nombre sigue siendo editable posteriormente. Puntos de recorrido conserva
los mismos puntos intermedios, trayectorias y extremos reasignables.

El nodo TABLE añade una lista fields: id UUID, name (hasta 100 caracteres), type
opcional (hasta 64), pk y fk booleanos estrictos. Máximo 32 campos por tabla;
encabezado en text (hasta 100). Las marcas son visuales y no introducen relaciones
SQL: las conexiones siguen referenciando el UUID del nodo. Los campos pertenecen
al nodo; borrar/reemplazar la tabla los elimina en la misma transacción. Duplicar
conserva una copia independiente de sus valores. No se crean archivos ni recursos.

El codec escribe versión documental 3 solo cuando hay tablas, y mantiene 1/2 para
los documentos anteriores. Lee 1/2/3, valida IDs, tipos, límites y relaciones antes
de confirmar o restaurar. Los nodos históricos conservan fields vacío y su
representación. No cambian Room 48, JSON global 40 ni el contenedor de backup.
Los campos confirmados viajan íntegros en diagram_library, dentro del snapshot
cifrado y la restauración transaccional existentes. Una carga inválida se rechaza
sin sustituir el estado anterior. Sin rutas absolutas ni nuevos ciclos de archivos.

El renderer compartido representa encabezado, campos, tipos y marcas PK/FK en
edición y en exportación PNG/GIF/fotogramas MP4. Ajusta el texto a las filas de la
tabla; los gestos y anclajes existentes mueven/redimensionan/conectan el nodo.
Las ediciones no guardadas siguen siendo estado temporal de instancia; cancelar
el nombre del primer guardado no confirma un documento.

## Ajustes de usabilidad: biblioteca, conexiones y conjunto

La biblioteca distingue Plantillas, Diagramas guardados y Biblioteca personal de
iconos. Los iconos muestran nombres ordinales legibles sin cambiar sus claves por
contenido. Los documentos guardados tienen acciones Abrir, Guardar copia y
Eliminar; eliminar solicita confirmación y comprueba que la revisión no cambió.
La superficie usa los colores de la apariencia activa, incluidos Green Bash y
los temas personalizados. No se migran ni reescriben documentos al abrir la lista.

El anclaje automático elige una de las cuatro caras según la geometría y los
puntos de recorrido. Los anclajes explícitos siguen teniendo prioridad. El trazado
ortogonal respeta la orientación vertical/horizontal de los extremos; Curva sigue
siendo un estilo curvo deliberado. Flechas, partículas, tiempos y los renderizadores
de exportación continúan usando la misma trayectoria compartida.

Opciones contiene Mover conjunto y Centrar contenido. Mover conjunto es un modo
explícito: arrastrar desde el contenido o el espacio libre traslada todos los nodos
y los puntos manuales de todas las conexiones, sin cambiar cámara, tamaños,
escalas ni recursos. Terminar recupera Mover individual. Cada gesto confirmado es
una entrada del historial. Centrar contenido usa los límites visuales respecto al
lienzo exportable y traslada sin escalar; el límite físico del documento puede
acotar la traslación de contenido que ya ocupa todo el lienzo.

Guardar confirma la biblioteca cifrada existente con el documento completo:
nombre, nodos (incluidas tablas/campos), conexiones y anclajes, puntos de recorrido,
estilos, animación, formato/dimensiones, fondo y cámara. Los iconos importados se
conservan en esa biblioteca por su identificador de contenido; Guardar no exporta
un archivo PNG/GIF/MP4. Se solicita nombre en el primer guardado; Guardar copia
crea una identidad nueva. Se conserva la comprobación de revisiones concurrentes.

Exportar tiene acceso directo en la barra superior y conserva perfiles, tamaño,
duración y repetición. PNG y GIF están implementados; MP4/H.264 está disponible
desde API 26. La exportación usa el lienzo completo y sus proporciones, sin aplicar
el zoom, desplazamiento ni paneles del editor. No se modifica el codec MP4.

Pendiente de validación física: precisión del arrastre conjunto y de los extremos
con zoom, legibilidad visual en cada teléfono, selector SAF y reproducción del
MP4/H.264 en el dispositivo real. Las pruebas automatizadas no acreditan esos pasos.

Validación de este ajuste: `compileDebugKotlin` correcto. Se verificaron 77 casos
únicos de Diagramas: 49 de modelo, gestos, diseño, persistencia y exportación, y
28 de interfaz. Primera ejecución: 69/77; se actualizaron recorridos de UI que
esperaban accesos anteriores, el selector técnico de iconos y la navegación del
nuevo caso de conjunto. Repetición UI: 26/28, seguida de los dos restantes
aprobados (Eliminar inferior e Imán inicialmente desactivado). Comprobación final
del código: compilación y seis pruebas de conjunto/anclajes/exportación aprobadas.
No quedan fallos en los casos ejecutados. Las pruebas de UI incluyen todos los
temas oficiales y un tema personalizado; las de exportación decodifican PNG/GIF
y comprueban que la cámara no modifica el área exportada. No se acredita ejecución
del códec H.264 en teléfono ni reinicio físico de Android. No se generó APK ni
se modificó versión; sin firma, commit ni push.
