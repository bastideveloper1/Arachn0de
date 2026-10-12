# Gantt integrado — Room 38

## Funcionalidad y acceso

En un Proyecto gestionado (o su Capa propietaria): **Gestión del proyecto → Gantt**. El botón Gantt aparece junto al acceso contextual a Gestión. **Opciones** permite seleccionar semana, mes, Sprint o todos los Sprints; orientación vertical/horizontal del lienzo y PNG; Tareas/Integrantes; escala diaria/semanal/mensual; integrante; detalle y zoom. Las flechas desplazan semana/mes. La vista predeterminada de teléfono usa una columna fija de nombres y un desplazamiento temporal compartido. La vista previa paginada del renderizador sigue disponible al desactivar «Vista de teléfono», y se conserva para exportar PNG.

Ambas orientaciones usan `GanttDocument`, con nodos originales, `NodeTreeSnapshot`, progreso Sprint existente y asignaciones/responsabilidades actuales. No crean copias de Historias, tareas ni Personas. Las tareas compartidas pueden aparecer bajo varios integrantes; los totales cuentan cada ACTION una vez. Se incluyen subtareas y Capas intermedias; el detalle puede contraerse. Tiempo consumido/restante, completitud y avance de fases son magnitudes separadas.

Semana y mes incluyen intervalos que se superponen al período y conservan los elementos sin fechas en un apartado propio. Un vencimiento sin inicio se representa como punto; los elementos sin fecha no reciben una artificial. Las fechas de Historias se editan desde **Opciones → Fechas · Historia**, usando los mismos `Node.startAt/dueAt`; el servicio valida contexto y tipo STORY antes de guardar. No altera las fechas de tareas, el árbol, los estados ni la planificación del Sprint. Sprint cerrado/en curso/previsto se toma del ciclo existente; una Capa no se presenta como tarea pendiente por carecer de fase.

El día actual y el período son explícitos. El progreso mostrado es **actual**, incluso al consultar fechas pasadas: no se reconstruye un historial inexistente. Los planes del Sprint y las fechas individuales se conservan independientes.

## Anotaciones

**Anotar**, o pulsación prolongada en la vista de lienzo, abre el editor contextual. Introducir texto y fecha; guardar. Consultar una anotación desde la fila inferior permite editarla, eliminarla o elegir **Mover** y arrastrarla; la posición se guarda al terminar. Opciones permite ocultarlas sin borrarlas.

Son textos libres independientes de las Notas normales. Persisten por UUID/contexto, día calendario (`epochDay`) y posición transversal normalizada (0–1), no por píxeles. Rotación/escala reutilizan esas coordenadas; cambios de agrupación pueden cambiar el contenido bajo la posición. Al salir y volver se conservan los datos guardados. No modifican estados ni aceptación.

## PNG real y límites

**PNG** exporta el documento renderizado, no una captura. Una página utiliza Crear documento; varias páginas solicitan una carpeta mediante SAF y escriben todas las partes numeradas con un identificador de lote. No requiere permisos nuevos. Las flechas de página muestran la distribución antes de exportar.

Incluye Proyecto, período, calendario, barras, progreso, hoy, leyenda y anotaciones visibles. Nombres/detalles completos y texto íntegro de anotaciones se conservan además en páginas de detalle. Los gráficos densos se dividen por tiempo y grupos; no debe compartirse solamente la primera parte si se requiere todo el contenido.

Cada bitmap se limita a cuatro millones de píxeles (aproximadamente 16 MiB ARGB), se genera secuencialmente y se libera. Máximo 3660 divisiones de calendario y 512 páginas de cuadrícula: ante exceso se pide reducir período/escala o filtrar integrantes. No se introduce caché privada persistente. Si falla la escritura, se elimina únicamente el documento parcial creado en esa operación; las partes ya completas permanecen y se informa exportación incompleta. Los PNG externos son productos derivados, excluidos del backup; las anotaciones originales sí se respaldan.

## DEMO aislado

`GanttDemo.create` reutiliza los repositorios y tipos actuales dentro de una única
transacción Room. Desde **Proyectos → Opciones → Crear proyecto de demostración**,
tras confirmar, genera «Demo — Gestión de proyectos · <identificador>», seis Personas
ficticias sin fotografías, Acta/Equipo, tres objetivos con criterios, tres épicas,
nueve Historias, 27 acciones, Notas, favoritos, prioridades, responsabilidades
compartidas y dos anotaciones. Tres Sprints: cerrado, activo y preparado, con metas
y fechas relativas a la creación; incluye tareas sin responsable y sin fechas.
No se asocian Personas reales ni se crea automáticamente al iniciar.

Cada instancia tiene IDs y nombre nuevos. Un error o cancelación revierte todos sus
registros, incluidas Personas. El proyecto usa el borrado normal: valida referencias
externas y retira su contexto e historial antes de borrar nodos con FK RESTRICT.
Las Personas ficticias permanecen en el catálogo global, igual que cualquier Persona,
y pueden eliminarse allí; la confirmación lo explica. No se crean archivos nuevos.
El arnés nativo histórico también puede seguir usando este mismo generador en su base
sintética. Sus resultados de release anteriores, conservados abajo, no acreditan UX 6.

## Persistencia y compatibilidad

Room **37→38**, aditiva: `gantt_annotations`, FK a contexto con CASCADE e índice. No cambia esquemas previos ni cifrado. IDs únicos; texto no vacío hasta 4000 caracteres; día 0–2932896; posición finita entre 0 y 1; contexto existente. Eliminación del contexto elimina sus anotaciones; desactivar gestión conserva el contexto y sus datos.

Backup **JSON 30** si hay anotaciones; sin ellas conserva la versión mínima requerida (29 para Gestión Cascada). Lectura histórica 1–29: colección vacía. Snapshot, validación y restauración incorporan la colección dentro del mecanismo transaccional existente. Una referencia inválida se rechaza antes de reemplazar datos. No se serializan rutas ni se duplican archivos. Bóvedas principal/señuelo siguen aisladas. Lectores antiguos que no admiten JSON 30 lo rechazarán; volver a una APK que solo conoce Room 37 no admite esta base 38. No se implementa migración inversa.

## Componentes modificados

Nuevos: `GanttAnnotations.kt`, `GanttRepository.kt`, `GanttModel.kt`, `GanttRenderer.kt`, `GanttDemo.kt`; pruebas `GanttTest`, `GanttUiTest`, `GanttNativeTest`; esquema 38. Adaptados: `Arachn0deDatabase`, `ManagementRepository`, `BackupData`, `BackupJson`, `BackupRepository`, `ProjectGanttUi`, `ManagementSection`, `HistoricalRoom26Fixture`, `NativeVaultTest`. La utilidad de proyección anterior permanece solo por compatibilidad de pruebas existentes; la UI usa el nuevo modelo común. Documentación: este informe, ARCHITECTURE, BACKUP_FORMAT y MANAGEMENT_CASCADE.

## Validación y pendientes

Resultados finales y artefactos se registran al final de este documento. No se repite la batería completa anterior de 107 pruebas ni se atribuye a esta entrega una nueva validación física. Las pruebas de UI cubren acceso Gantt, controles, cambio de perspectiva/orientación, anotación, navegación al ID original y fuente 1.6×. El arnés comprueba repositorios reales, persistencia, backup y SQLCipher; no automatiza todo el recorrido visual Personas → Acta → Kanban. Ese recorrido manual completo continúa pendiente.

Pendientes físicos: contraste y densidad en teléfono, manejo de gestos/scroll, accesibilidad TalkBack del Canvas, selección SAF y exportación en distintos fabricantes. El texto de etiquetas muy estrechas se envuelve; los detalles completos están en las páginas anexas. No se añadieron hitos, dependencias ni estadísticas profesionales. No se tocaron los siete PNG, Metro ni el juego.

La evidencia oficial anterior de release no se renueva con estas dos pruebas parciales. Un futuro release deberá ejecutar el protocolo nativo oficial completo sobre su checkout. Esta entrega no autoriza una APK oficial ni publicación.

## Resultados finales — 11 octubre 2026

11 pruebas JVM/Compose aprobadas: GanttTest (6), GanttUiTest (2), CascadeProjectionTest (3). `assembleDebug` aprobado; ejecución final Gradle 40 s. Dos pruebas nativas SQLCipher aprobadas (51.506 s), sobre el checkout de huella `31504828df29ad4dbe07111c0bb27bd9af6c4c8aa8216eb910d9d61dec0f3678`: migración del esquema 37 auténtico y preservación de todas sus tablas en ambas bóvedas, restauración JSON 30 y rechazo cruzado; DEMO/persistencia/restauración/exportación nativa. La compatibilidad JSON 29 y rechazo de anotaciones inválidas se comprobaron en GanttTest. No se repitieron los ensayos históricos 26/36 del cierre anterior.

APK Debug: `/home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/Arachn0de-gantt-room38-DEBUG.apk` · 0.3.0 / 11 · 174413605 bytes (166.33 MiB). SHA-256 `a1cba023d7de900484cc2520c8a90bde44760de1443a3b56cea02ea5d3eaa197`. Firma Debug verificada, certificado SHA-256 `2b77e47d7a03bccf1cc2edee3543d0b8b2a591e94b9875cbe30625a492b14883`. **No puede actualizar una instalación firmada oficialmente**; no se debe desinstalar ni borrar datos personales para probarla. No se accedió al teléfono.

13 PNG reales generados y decodificados en Android. Revisados visualmente los tres ejemplos principales y una página de detalles. Cada caso incluye varias partes; conservar el conjunto completo.

| Archivo | Dimensiones | Bytes |
| --- | --- | --- |
| [semana-tareas-vertical-1.png](release-assets/gantt-demo-room38/semana-tareas-vertical-1.png) | 1660×990 | 68958 |
| [semana-tareas-vertical-2.png](release-assets/gantt-demo-room38/semana-tareas-vertical-2.png) | 1000×990 | 48556 |
| [semana-tareas-vertical-3.png](release-assets/gantt-demo-room38/semana-tareas-vertical-3.png) | 1200×756 | 110537 |
| [sprint-integrantes-vertical-1.png](release-assets/gantt-demo-room38/sprint-integrantes-vertical-1.png) | 1440×1620 | 85598 |
| [sprint-integrantes-vertical-2.png](release-assets/gantt-demo-room38/sprint-integrantes-vertical-2.png) | 1680×1620 | 85228 |
| [sprint-integrantes-vertical-3.png](release-assets/gantt-demo-room38/sprint-integrantes-vertical-3.png) | 1200×1116 | 166011 |
| [todos-sprints-horizontal-1.png](release-assets/gantt-demo-room38/todos-sprints-horizontal-1.png) | 1020×1960 | 107935 |
| [todos-sprints-horizontal-2.png](release-assets/gantt-demo-room38/todos-sprints-horizontal-2.png) | 1020×1960 | 120684 |
| [todos-sprints-horizontal-3.png](release-assets/gantt-demo-room38/todos-sprints-horizontal-3.png) | 1020×1960 | 121177 |
| [todos-sprints-horizontal-4.png](release-assets/gantt-demo-room38/todos-sprints-horizontal-4.png) | 1020×1960 | 116089 |
| [todos-sprints-horizontal-5.png](release-assets/gantt-demo-room38/todos-sprints-horizontal-5.png) | 1200×1620 | 249822 |
| [todos-sprints-horizontal-6.png](release-assets/gantt-demo-room38/todos-sprints-horizontal-6.png) | 1200×1620 | 233139 |
| [todos-sprints-horizontal-7.png](release-assets/gantt-demo-room38/todos-sprints-horizontal-7.png) | 1200×228 | 26381 |

Evidencia dirigida: `release-assets/gantt-demo-room38/validation.json` y `native-validation.log`. `git diff --check` limpio. El árbol de trabajo ya contenía numerosos cambios previos; se conservaron. Sin commit, push, APK oficial ni acceso a datos personales.

## Validación UX 6

La interfaz y el generador actuales se documentan en
[MANAGEMENT_CASCADE.md](MANAGEMENT_CASCADE.md#sprint-ux-6--gantt-y-demostración-integral).
Debug y 45 casos dirigidos JVM/Compose aprobados, incluido el recorrido real completo,
restauración en base vacía, rollback y borrado del proyecto. La evidencia nativa y APK
historical indicadas arriba pertenecen a la extensión anterior; no se renovaron en UX 6.
Pendiente revisión visual en dispositivo y exportación con SAF real. Sin release.
