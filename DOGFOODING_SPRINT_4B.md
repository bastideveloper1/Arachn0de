# Arachn0de — Dogfooding Sprint 4B

Versión conservada: **0.3.0 / versionCode 10**. Se trabaja sobre Sprints 1–4A, conservando sus datos y funciones. Sin Release, firma oficial, instalación, commit, push ni cambios en el juego o temporizadores de seguridad.

## Contraste y navegación

Las tarjetas de estaciones usaban fondos de Apariencia (`Surface`/`SurfaceRaised`) sin especificar conjuntamente el color de contenido. Material podía recurrir al `LocalContentColor` heredado, que el contenedor Metro tampoco definía pese a pintar un fondo oscuro. Se establece el foreground del contenedor desde `onBackground` y cada tarjeta usa la pareja superficie/texto de su paleta. El énfasis y selección cambian superficie/borde; completado, posición, combinación y clasificación no sustituyen el texto por negro. Se conservan indicadores y colores informativos de líneas y Roja/Verde/Común.

La pestaña **Explorar → Líneas** conserva el explorador. El encabezado mantiene objetivos táctiles de 48 dp y una separación compacta del contenido. Las barras del sistema y el teclado continúan protegidos por `AppSafeArea` en MainActivity, sin introducir un segundo padding de insets.

## Planificar

Origen y Destino aparecen verticalmente, con etiqueta, iconos de selección/edición y textos Pendiente/Seleccionada/Modificando/Estación cerrada. Los campos siguen abriendo el selector existente. Agregar parada y Persona comparten FlowRow cuando cabe; se conservan orden, reordenación y eliminación de paradas, inversión, Casa y selección de Persona.

El selector reutiliza búsqueda y filtros existentes, ahora con `TextFieldState` y límite visual de una línea. No cambian dependencias ni el comportamiento de búsqueda. Los tests detectaron un bucle de medida en `LegacyTextFieldState` del campo anterior en Robolectric; la implementación con estado resuelve el caso sin forzar dimensiones, desactivar idleness ni modificar producción para simular resultados.

## Panel de horarios

La implementación inspeccionada muestra únicamente un texto literal con horarios de referencia y metadatos del catálogo. No determina servicios ni se conecta a un motor horario. Se oculta temporalmente el acceso al desplegable y se conserva `MetroSchedulePanel` íntegro para trabajo futuro. Normal/Expresa manuales continúan disponibles. No se añaden horarios automáticos ni información en tiempo real.

## Mapa y acciones contextuales

Expandir/Contraer es un único control con icono y descripción accesible junto al mapa. En seguimiento comparte la barra del mapa con Centrar avatar, separados por espacio y objetivos táctiles independientes. La vista ampliada oculta encabezados secundarios, conserva las acciones de la etapa y permite volver con el icono o Back. Se mantienen LazyListState, selección guardada y anclas del mismo viaje. La estación elegida conserva una indicación textual y semántica al cerrar su menú o ampliar/contraer.

En seguimiento, Estoy aquí es la acción principal con icono y acento del tema. No se muestra Usar como origen; Cambiar destino sustituye Usar como destino. La estimación entre estaciones sigue disponible bajo Corrección avanzada. En planificación se conservan origen, destino y parada. Corregir fuera de ruta reutiliza la propuesta existente, ahora con archivo del recorrido sustituido y embarque explícito.

## Cambio de destino y Sprint 4A

1. Cambiar destino abre StationPicker, indicando el destino actual. También puede proponerse desde una estación del mapa.
2. Una posición estimada, entre estaciones o incierta exige elegir la estación real. La selección no escribe datos.
3. El planificador oficial calcula el recorrido desde esa ubicación al nuevo destino; una confirmación muestra la ruta propuesta. Cancelar mantiene el viaje anterior.
4. Confirmar comprueba la revisión y vuelve a planificar dentro de la misma transacción del repositorio. Una ruta imposible, conflicto o payload inválido no deja cambios parciales.
5. Se conserva ID de viaje/sesión, origen original, inicio, asociaciones, pausas, eventos y recorridos anteriores con progresos y duraciones. Se actualizan el plan vigente, estaciones, líneas, direcciones, futuras combinaciones y estimación restante.
6. La ruta nueva queda **READY / Esperando embarque**. No hay avance ferroviario hasta Comenzar nueva etapa. Durante una pausa primero se reanuda; cambiar en combinación captura el tiempo transcurrido y mantiene el intervalo pendiente hasta embarcar, con estado Combinando y acción Comenzar siguiente línea. Si se vuelve a cambiar antes de embarcar, se conservan todos esos intervalos.
7. Un destino igual a la ubicación actual requiere confirmar Llegué para finalizar; también se admiten destinos anteriores en la ruta. Los recorridos sustituidos se consultan desde Historial del recorrido.

Se mantienen llegada, combinación manual, nueva línea, pausa, undo persistente, tarjetas compactas e indicadores jerárquicos. Una confirmación de posición en el mismo punto no abre silenciosamente una etapa que seguía esperando. Deshacer una llegada hecha durante una pausa tampoco descuenta dos veces ese intervalo de la duración ferroviaria. Tras cambiar ruta, el undo de una llegada anterior queda irreversible y se ofrece corrección explícita. Repetir la misma selección sobre READY no duplica archivos de recorridos ni revisiones.

La propuesta calcula el recorrido al destino nuevo; las paradas del recorrido anterior permanecen íntegramente en el historial. La confirmación muestra las paradas de la nueva ruta. No hay aprendizaje de tiempos ni cambios globales de estimación.

## Persistencia y seguridad

Room permanece en **27**, sin nuevas tablas, columnas ni migración. El payload Metro pasa a **v4**, con lectores v1–v3 y defaults vacíos para `routeArchives`. Los archivos de recorridos son instantáneas JSON internas, no archivos físicos: misma identidad, anclas de inicio y origen original, sin archivos anidados ni undo. Se comprueba su correspondencia con `routeHistory`. Cada snapshot conserva la ruta y sus metadatos, control y duraciones capturadas; los eventos globales permanecen intactos.

Exportación, restauración y validación transportan todos estos campos en el payload existente. Restaurar aplica relojes portables no confiables al control actual, undo y archivos del historial. Datos inválidos se rechazan antes de reemplazar. Se mantienen las versiones exteriores de backup 17/18/19 y sus límites existentes. Aplicaciones antiguas pueden rechazar payloads v4; no se promete que lean backups nuevos.

No se crean archivos privados nuevos ni cambian reservas, limpieza o cifrado. Los borradores de destino, búsqueda, selección y scroll son navegación/editor temporales; no son datos confirmados del backup. Un borrador de cambio puede cancelarse al recrear la pantalla, conservando siempre el viaje confirmado.

SQLCipher, principal/señuelo, separación de almacenes, archivos y backups cifrados permanecen intactos. Se prueba el viaje recalculado, historial y backup cifrado con bloqueo/reapertura de los almacenes; el driver de esa prueba de host sustituye SQLCipher únicamente en el fixture. No es certificación nativa.

## Archivos del sprint

Producción, en `app/src/main/java/com/r0ybt/arachn0de/metro/`:

- `MetroDestination.kt`: captura de historial y transición al recorrido nuevo.
- `MetroRepository.kt`: cambio atómico y control de revisión.
- `MetroTracking.kt`, `MetroStages.kt`: archivo al corregir ruta y espera de embarque.
- `MetroCodec.kt`, `MetroControlCodec.kt`: payload v4 y validación.
- `MetroActivity.kt`: actividad esperando embarque.
- `MetroTimeline.kt`: contraste y selección conservada.
- `MetroScreen.kt`: campos, navegación, mapa, contexto y confirmación de destino.

Pruebas nuevas: `MetroDestinationTest.kt`, `MetroStationContrastTest.kt`, `MetroSprint4BUiTest.kt`. Se adaptan `MetroPresentationTest.kt`, `MetroStagePersistenceTest.kt`, `MetroStageUiTest.kt`, `MetroUiTest.kt`, `MetroRedesignUiTest.kt` y `security/VaultManagerTest.kt` al flujo y formato actuales. Documentación: `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe.

## Validación física pendiente

Comprobar en teléfono contraste y fuentes grandes en todos los temas, teclados y selectores, pantallas estrechas/apaisadas y barras del sistema, selección/scroll al ampliar, notificación en segundo plano/pantalla bloqueada, varias combinaciones reales, cambios de destino durante pausa/transbordo y recuperación tras muerte del proceso/reinicio.

No se instalaron ni ejecutaron pruebas instrumentadas en un dispositivo. Compilar AndroidTest no equivale a ejecutarlas. La migración y recuperación nativas de SQLCipher no se certifican nuevamente con las pruebas de host de este sprint.

## Pruebas ejecutadas

**78 casos dirigidos distintos aprobados**, consolidando las ejecuciones y las repeticiones de correcciones; no se ejecutó la suite completa.

| Clase | Casos aprobados |
|---|---:|
| MetroDestinationTest | 11 |
| MetroStationContrastTest | 1 |
| MetroSprint4BUiTest | 6 |
| MetroStageEngineTest | 9 |
| MetroStagePersistenceTest | 5 |
| MetroStageUiTest | 4 |
| MetroTrackingTest | 9 |
| MetroUiTest | 3 |
| MetroRedesignUiTest | 4 |
| MetroPresentationTest | 5 |
| MetroServiceTest | 2 |
| MetroPersistenceTest | 10 |
| MetroCardConsultationTest | 8 |
| VaultManagerTest (caso Metro/almacenes) | 1 |

El caso de contraste calcula al menos 4,5:1 para nombres y Roja/Verde en las cinco paletas, combinando estados de énfasis. Otro test de UI comprueba el foreground heredado por las tarjetas reales en todos los temas, con estaciones Roja/Verde/Común, progreso y selección superpuestos. Los tests de UI usan Robolectric con gráficos nativos, SDK 28; siguen siendo pruebas de host.

Se verifican campos y Persona/paradas, Líneas, acciones contextualizadas, expansión/contracción y selección sin escrituras, cancelación y confirmación de destino con pausa, nueva línea con combinación, destino anterior/igual a ubicación, ruta imposible, revisiones obsoletas, idempotencia, corrección fuera de ruta, conservación de intervalos de transbordo, reapertura y backup/restauración. Corrupción de un archivo del historial se rechaza conservando el estado anterior; payloads v3 se leen sin reescribir. El fixture Vault comprueba backup cifrado y aislamiento con la ruta recalculada.

Las primeras ejecuciones encontraron el problema de medida del buscador legacy y problemas de sincronización/ambigüedad en los tests. El buscador se corrigió con TextFieldState y los tests esperan transiciones efectivas y seleccionan dentro del diálogo. La validación final no modifica políticas de idleness ni fuerza layouts. Las repeticiones se limitaron a las correcciones y sus regresiones pertinentes.

Resultados consolidados en `/tmp/4b-results1/`, `/tmp/4b-results3/`, `/tmp/4b-results4/`, `/tmp/4b-results-final/` y las ejecuciones posteriores. `/tmp/4b-final.log` contiene la selección amplia y `/tmp/4b-ui-final2.log` confirma las dos comprobaciones de UI corregidas. El conteo excluye repeticiones y el nombre anterior del test del panel, sustituido por la comprobación de su ocultación.

Comprobación final tras los ajustes de duración y combinación: **24 pruebas, cero fallos y cero errores**, con `assembleDebug` y `assembleDebugAndroidTest` en la misma ejecución: **BUILD SUCCESSFUL** (`/tmp/4b-compat-final.log`). La regresión de pausa/undo también está registrada en `/tmp/4b-duration-final.log`. `git diff --check` pasa. Se conserva **versionName 0.3.0 / versionCode 10**. No se generó Release, no se utilizó la firma oficial, no se instaló ni desinstaló la aplicación y no se hicieron commits ni push.
