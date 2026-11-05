# Rendimiento — Grupo 6: R11 y R12

## Definiciones originales aportadas por el usuario

**R11 — Lectura de miniaturas serializada con operaciones de archivos.**
Gravedad media. ProjectThumbnailCache.kt:17. El bloqueo compartido abarca poda,
lectura, decodificación y generación de miniaturas. La poda enumera y ordena el
directorio en cada lectura. Medir contención y reducir el trabajo protegido
únicamente mediante reservas seguras. Amortizar la poda y preservar el contrato
de liberación de archivos. Validar espera, adquisición, liberación e integridad
concurrente.

**R12 — Trabajo repetido de Compose y proyecciones jerárquicas.**
Gravedad media. Candidatos: ProjectScreen.kt, TaskViewProjection.kt,
ActionComponents.kt y ModularPanel.kt. Posibles cálculos repetidos de contadores,
filtros, mapas, selección, ordenación, ancestros, agrupaciones y mediciones de
texto. Medir recomposiciones, tiempos y asignaciones. Optimizar únicamente los
cálculos dominantes demostrados. No introducir remember indiscriminadamente ni
declarar tipos estables sin garantizarlo.

## Estado inicial y correcciones

R11 seguía presente: toda generación retenía el mutex común. Se protege ahora
el original con una reserva durable de ImageFileLifecycle; el mutex común cubre
adquisición, metadatos y liberación, y la decodificación/generación se realizan
fuera. La liberación usa NonCancellable. Un mutex propio mantiene serializadas
las miniaturas y protege los derivados y sus partes contra la poda. No se aumenta
el número de decodificaciones simultáneas. La poda de lecturas se amortiza a un
minuto y se fuerza después de generar para conservar el límite de 64 MiB de
los derivados reconocidos. Los archivos desconocidos se conservan.

El propietario exclusivo project-thumbnail-reader permite recuperar reservas de
un lector interrumpido cuando el siguiente adquiere el mutex propio y no queda
otro lector activo. No existe caducidad por antigüedad ni se liberan reservas de
editores, backup u otros propietarios. La recuperación no elimina originales.
Si no se produce otra lectura, una reserva pendiente conserva el archivo.

R12 ya calculaba la proyección fuera del hilo principal mediante produceState.
Persistían recorridos de ancestros por cada tarea para contadores y verificación.
Se sustituyen por acumulación de subárboles en orden inverso y resolución de modos
en preorden, incluyendo los ancestros externos al ámbito seleccionado. Se conserva
el resultado completo del algoritmo anterior. No se cambian orden, filtros,
selección, estados ni reglas. No se añaden remember ni anotaciones de estabilidad.

El sondeo de menús no aporta evidencia para modificar ProjectScreen,
ActionComponents o ModularPanel: 22 composiciones y una sola proyección con
entrada constante; mediana de llamadas de composición 0,257 ms, seis mediciones
de texto cacheadas 0,165 ms y 7.472 bytes Java. Es un sondeo acotado del patrón
existente, no una medición de toda ProjectScreen, del frame completo o del teléfono.

## Mediciones antes/después

JVM, datos sintéticos, mediana de cinco lecturas/cálculos tras calentamiento:

| Medida | Antes | Después |
| --- | ---: | ---: |
| R11: espera del contendiente durante lectura fría | 1.459,46 ms | 7,01 ms |
| R11: generación fría completa | 1.472,44 ms | 1.624,17 ms |
| R11: lectura caliente con 1.000 entradas | 211,01 ms | 162,04 ms |
| R12: 2.000 tareas, profundidad 2 | 18,70 ms | 11,47 ms |
| R12: asignaciones Java, profundidad 2 | 2.085.336 bytes | 1.272.400 bytes |
| R12: 2.000 tareas, profundidad 64 | 43,01 ms | 6,14 ms |
| R12: asignaciones Java, profundidad 64 | 15.679.912 bytes | 1.300.376 bytes |

La imagen de R11 tiene 2048 px y sus originales/derivados están cifrados. No se
acelera la generación fría: se libera el bloqueo para otras operaciones. Hay
coste de reservas durables. Estos valores incluyen variaciones de JVM/JIT y no
son latencias de Android, percentiles de producción ni mediciones de batería,
RAM nativa o APK. No se infiere que una jerarquía profunda sea más rápida que una
plana a partir de ejecuciones con distinto calentamiento.

## Archivos modificados

- data/local/ProjectThumbnailCache.kt: reserva, exclusión propia y poda amortizada.
- data/local/AvatarStore.kt: propaga el sincronizador durable ya existente en las
  dos llamadas a la caché, sin cambiar el protocolo de producción.
- domain/model/TaskViewProjection.kt: acumulación y herencia en recorridos lineales.
- ARCHITECTURE.md y BACKUP_FORMAT.md: documentan las reservas operativas y la
  exclusión regenerable existente. Sin cambios de esquema, formato ni cifrado.
- Pruebas nuevas: data/Group6MeasurementTest.kt, data/ThumbnailLeaseTest.kt y
  ui/Group6ComposeMeasurementTest.kt bajo app/src/test/java/com/r0ybt/arachn0de/.
- Este informe. No se modifica el código de los demás módulos ni los Grupos 1–5B.

## Validación

**7 pruebas distintas aprobadas; compilación correcta, BUILD SUCCESSFUL.**
Se ejecutan únicamente los tres archivos de pruebas anteriores mediante filtros
:app:testDebugUnitTest, offline. Las dos mediciones iniciales se repiten para
comparar antes/después; no se repiten suites previas.

- Espera de adquisición, reserva durable y progreso de generación mientras una
  operación competidora posee el mutex común.
- Liberación normal e interrupción; protección de un editor independiente y de
  referencias confirmadas antes de permitir eliminación de la imagen sintética.
- Recuperación de reservas anteriores y fallo inyectado de sincronización,
  conservación de otros propietarios y reintento posterior.
- Cuatro lectores concurrentes, originales cifrados intactos, corrupción,
  ausencia/reemplazo, limpieza de partes, límite de disco y archivo desconocido.
- Comparación exacta con el algoritmo anterior en 192 combinaciones de árboles,
  ámbitos, filtros de estado y reglas heredadas/excepciones.
- Mediciones de tiempos y asignaciones y sondeo de recomposiciones/menús.

Evidencia: /tmp/group6-baseline.log, /tmp/group6-validation.log,
/tmp/group6-before/measurement.xml, fuentes anteriores en /tmp/group6-before/
y XML finales en /tmp/group6-results/. El diagnóstico de imagen no implementada
corresponde a la corrupción sintética probada; no hay fallos de pruebas.

## Resultado y límites

R11 y la parte demostrada de R12 quedan corregidos y validados. Los otros
candidatos de R12 se conservan al no justificar cambios con la evidencia obtenida.
Pendiente comprobar en teléfono los tiempos, la navegación y recuperación tras
cierre real del proceso. No se garantiza una mejora de todos los tiempos de
lectura ni del frame completo. Las reservas pendientes no se borran por edad.
No se eliminan datos privados ni se cambian backups, versiones, firma o recursos.
No se genera APK ni se crean commits.
