# Alineación de Capas después de filtrar el calendario

## Continuación: distinguir jerarquía de sangría residual

Se precisó que las diferencias afectan al borde y ancho completos, no solo al
texto. La captura mencionada no está adjunta ni disponible como imagen en el
turno recibido; no se afirma haberla inspeccionado.

Se revisaron únicamente las ramas ya identificadas, la proyección de profundidad
(`ScopedNodeFilter`, `TaskViewProjection`) y su ordenación (`NodePresentationSort`).
Los hijos inmediatos del ámbito empiezan en profundidad 0; cada descendiente
incrementa 1. Ordenar conserva esa profundidad. En `ProjectScreen.kt:1001` el
padding de inicio reduce legítimamente el ancho 12 dp por nivel, hasta seis
niveles, manteniendo el borde derecho. La clave sigue siendo `node:<id>` al pasar
de vista filtrada a normal; la medida vuelve a calcularse con el modificador
de la rama activa, sin guardar un ancho por ID.

Una distinción verificable: plegar el calendario **no retira sus filtros**;
`Ver todas` sí limpia selección, atención y favoritos. Por tanto, después de
plegar es correcto que sigan apareciendo subcapas más estrechas e indentadas.
Esto reproduce una apariencia compatible con la descripción, pero no demuestra
que sea lo que mostraba la captura ni explica tarjetas hermanas desplazadas.

Se añadió y ejecutó únicamente el nuevo método
`NavigationWorkTest.innerLayersKeepOnlyLegitimateIndentation` (Robolectric,
411 × 1800 dp). No se repitió la prueba larga anterior. Escenario: vista interior
de una Capa, dos Capas hermanas, una subcapa y otra subcapa más profunda, con una
única tarea fechada. La prueba verifica:

- Capas hermanas alineadas en la vista normal.
- Tras filtrar, subcapas desplazadas exactamente 12 y 24 dp y ancho reducido en
  proporción, con borde derecho común.
- Plegar conserva el filtro y la sangría jerárquica.
- Desplegar y pulsar Ver todas elimina los descendientes de esa lista y recupera
  exactamente el borde y ancho originales de las hermanas, aun reutilizando IDs
  y claves y después de finalizar las animaciones.
- Filas de la base intactas antes/después.

Resultado: **aprobado**, sin cambio de producción. Solo se modificaron este informe
y `app/src/test/java/com/r0ybt/arachn0de/ui/NavigationWorkTest.kt`.

Para confirmar un defecto pendiente hacen falta la captura efectiva, identificar
si las tarjetas desplazadas comparten padre y confirmar si «volver» significó
plegar/cerrar el calendario o retirar el filtro con Ver todas (y si quedaban
otros filtros de tareas activos). La excepción de Material 3 del intento anterior
no se volvió a investigar ni se considera corregida en esta continuación.

Sin APK, cambios de versión, esquema o datos de usuario. Pendiente contraste
con la pantalla y los datos concretos del dispositivo.

## Resultado

No se reprodujo la desalineación horizontal persistente descrita desde la tercera
Capa. No se modificó código de producción ni se aplicaron reinicios, cambios de
claves o reparaciones especulativas.

## Revisión focalizada

- `ui/ProjectScreen.kt`: la lista normal renderiza los hijos inmediatos sin
  sangría. Los resultados de la proyección de tareas filtrada conservan sus
  antecesores y aplican `12.dp * minOf(depth, 6)` para expresar profundidad.
  Esa sangría pertenece a la rama filtrada, no a los datos de la tarjeta.
  Ambas ramas usan `node:<id>` y animación de colocación; por ello se midió
  expresamente el regreso tras finalizar la animación.
- `ui/NodeComponents.kt`: ambas ramas reutilizan `NodeCard`, con ancho completo;
  sus márgenes interiores no se guardan en función del filtro.
- `ui/DragReorder.kt`: el desplazamiento de arrastre modifica únicamente Y y se
  restablece cuando el elemento deja de arrastrarse. No explica por sí solo el
  desplazamiento horizontal comunicado.
- `ui/ContextCalendar.kt`: seleccionar o limpiar modifica filtros/ancla, sin
  escribir medidas o sangrías. El seguimiento de Hoy recientemente corregido
  tampoco modifica el diseño de las tarjetas.
- Las barras inferiores compartidas se alternan fuera de las filas de tarjetas;
  pueden variar el espacio vertical disponible, pero no añaden sangría a filas
  individuales. No se demostró una interacción causante del defecto.
- La rama revisada de Capas usa `LazyColumn`; no alterna entre lista y cuadrícula.
  No se extrapola este resultado a pantallas especiales.

## Prueba añadida y ejecutada

`app/src/test/java/com/r0ybt/arachn0de/ui/NavigationWorkTest.kt`:
`calendarCycleKeepsLayerAlignment`, con Robolectric y el componente real
`ProjectNodeScreen`, Room en memoria, ancho 411 dp y altura 1800 dp.

Lista de 18 Capas; las Capas tercera, cuarta y quinta tienen una subcapa y una
tarea con vencimiento en el día actual. Se midieron borde izquierdo y ancho de
las seis primeras tarjetas antes del filtro y después de dos ciclos completos.
El recorrido incluye selección de día, visualización de un resultado profundo,
cambio de período y Ver todas; finalmente alcanza la última Capa y comprueba
que el orden de los hijos en la base sigue intacto. Tolerancia de medida: 0,5 px.

Resultado final: **aprobado**, sin desplazamiento horizontal residual ni cambio
de ancho en las tarjetas medidas. No se ejecutaron suites completas.

## Hallazgo adicional y límites

El primer intento, con altura 891 dp, falló al volver mediante scroll al principio
después de medir tarjetas de la lista normal, antes de activar ningún filtro:
`IllegalStateException: visitAncestors called on an unattached node`, en
`androidx.compose.material3.internal.ChildSemanticsNode.applySemantics`.
La operación del test era `performScrollToIndex(0)`. No fue una aserción de
alineación fallida. La biblioteca Material 3 local usa ese nodo en IconButton y
Surface interactivo; la traza no identifica cuál de los controles era el origen.

Se amplió la altura del escenario para medir las seis tarjetas sin ese recorrido
inicial de reciclado; solo se repitió este test y aprobó. Esto **no acredita una
corrección de la excepción** ni permite atribuirla al calendario. Queda pendiente
aislarla en la altura original y contrastarla en Android físico.

La prueba cubre la lista de Capas en la raíz de un Núcleo, que comparte render con
las listas interiores; no reproduce los datos exactos de la Capa del usuario ni
acredita todas las variantes de tema/fuente, la restauración exacta de scroll
vertical o el aspecto de todas las Tareas/Notas/Núcleos. Una captura del descuadre
y saber si afectaba al borde completo o solo al contenido permitirían acotar el
caso pendiente. No se ha demostrado aún la causa visual original.

Archivos de esta intervención: este informe y la prueba añadida a
`NavigationWorkTest.kt`. Sin cambios de versión, esquema, datos de usuario ni APK.
