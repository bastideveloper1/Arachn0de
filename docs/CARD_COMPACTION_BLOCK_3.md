# Bloque 3 — compactación de tarjetas

## Resultado por tarea

1. Cantidad y progreso comparten una fila: barra, completadas/total y porcentaje. En vistas filtradas se conserva su contador propio junto al porcentaje; la descripción accesible detalla también el total y las completadas. Se usan los valores calculados existentes, sin cambios de criterios ni consultas nuevas. Sin tareas medibles no se muestra progreso; el contador de una vista filtrada se conserva si corresponde.
2. La barra tiene anchura flexible de 12–48 dp y la fila un límite de 220 dp. El texto abrevia las palabras y espacios cuando falta ancho; en casos extremos admite desplazamiento horizontal antes que perder cifras. La protección del fondo sigue midiendo el área visible del contenido. No se cambia el motor de imágenes, su encuadre ni su degradado.
3. Las tarjetas de lista ya omitían descripciones vacías. Se retiraron las invitaciones «Añadir descripción» y sus tarjetas vacías de las vistas interiores de Núcleo/Capa/Tarea y de Kanban. El contenido existente se sigue mostrando. Las acciones inferiores «Contenido → Descripción» y el editor siguen disponibles.
4. Los indicadores de metadatos mantienen tamaño visual uniforme de 16 dp y separación de 2 dp, sin distribuirse a lo largo del ancho. Los pertinentes siguen presentes cuando están inactivos, con los colores existentes del tema. Los botones de acción conservan sus tamaños. La recurrencia conserva la expansión táctil mínima de Compose, verificada en 48×48 dp, sin añadir una fila vacía de esa altura.
5. Adjuntos pasa delante de tareas pendientes y por verificar. Se conserva la secuencia relativa de los demás indicadores, sus estados, contadores y callbacks. El orden se aplica al resumen de tarjeta y a la lista compartida de indicadores activos del detalle.

## Archivos de implementación

Bajo `app/src/main/java/com/r0ybt/arachn0de/ui/`:

- `ContainerProgressLine.kt`: fila común de cantidades/progreso y adaptación al ancho.
- `NodeComponents.kt`: integra el contador de la vista de Capa en esa fila.
- `CompactCardIndicators.kt`: compactación y orden de adjuntos.
- `ProjectScreen.kt`: omite la tarjeta de descripción cuando está vacía.
- `ManagedKanbanUi.kt`: omite la descripción vacía en Kanban.

Núcleos reutiliza la fila compartida desde `ProjectCard`, sin reconstruir ese componente. No se modifica el menú global experimental, navegación, persistencia, cifrado, backups ni el cálculo de estados.

## Comprobaciones focalizadas

- `ContainerProgressLineTest`: actualización de progreso derivado, contenedores sin trabajo y exclusión de hojas.
- `CompactCardUniformityTest`: geometría común, botones y etiquetas; imágenes de diferentes proporciones con títulos cortos/largos; recurrencia sin aumentar la altura.
- `CardCompactionBlockThreeTest` (nuevo): tarjetas reales de Núcleos/Capas/Tareas, anchos 260/360 dp, letra normal/ampliada, con/sin skins y títulos cortos/largos; barra y cifras en una fila, sin invasión de acciones; orden de iconos, estados vacíos/activos y área táctil de recurrencia.
- Método focalizado añadido a `ContextCalendarIntegrationTest`: descripciones vacías sin tarjeta, texto existente visible y acciones inferiores de edición presentes.

No se ejecutan auditorías generales ni pruebas de persistencia o backups.

## Revisión física pendiente

Revisar legibilidad de la fila abreviada y de los iconos sobre skins reales, alcance táctil de recurrencia y desplazamiento horizontal con fuentes muy grandes, cifras extensas o tarjetas excepcionalmente estrechas. No se genera ni firma APK oficial.

## Resultados

Ocho pruebas distintas aprobadas en las ejecuciones focalizadas. La matriz de tarjetas cubre 48 combinaciones de tipo, ancho, fuente, skin y longitud del título. Se corrigió un desbordamiento de avance fraccional de glifos reservando un píxel físico para las cifras; la comprobación final confirma una sola línea sin desbordamiento del texto. También se verifica que el contador filtrado no se pierde cuando desaparece el progreso medible.

Las pruebas nuevas necesitaron consultar el árbol semántico no fusionado de las tarjetas y coincidencias parciales para el texto del contador; esas correcciones corresponden al test. No quedan fallos conocidos en las comprobaciones ejecutadas. La revisión física indicada arriba permanece pendiente.
