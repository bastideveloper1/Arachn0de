# Sprint UI/UX 2 — Jerarquía y gestión

## Inspección y cambios

La jerarquía Proyecto → Capa → Tarea ya usa identificadores originales y tarjetas con título, descripción resumida y detalle completo. Se conserva, junto con la navegación global del Sprint 1. No se modificaron los siete PNG oficiales.

La entrada contextual **Gestión del proyecto** conserva el PNG oficial. Su menú reúne **Objetivos**, **Backlog**, **Historias**, **Sprint**, **Kanban** y **Equipo** en filas que se adaptan al ancho. **Configuración** despliega metodología y desactivación. Objetivos conserva el acta; Sprint conserva planificación, ciclos e historial; Equipo conserva participantes y responsabilidades. No hay módulo global nuevo.

Cerrar una herramienta vuelve a Gestión. Abrir el original sigue usando su ID y su ubicación real. Cada herramienta tiene un ámbito de estado independiente por contexto; Backlog, Objetivos y Sprint conservan selección, filtros aplicables y desplazamiento mediante estado guardable. Las listas esperan la primera emisión de datos antes de restaurar su posición, evitando que una lista vacía provisional reinicie el desplazamiento.

Backlog resume descripciones a dos líneas y deja la ubicación completa en el detalle. Subir, Bajar y Retirar pasan al menú contextual de cada tarjeta; Abrir y Relaciones siguen accesibles. Objetivos y Sprint resumen las descripciones/metas de sus vistas previas y conservan el contenido completo en detalle. Las tarjetas existentes de Proyecto/Capa/Tarea mantienen su estructura compacta y sus controles. No se incorpora el futuro sistema semántico de colores ni acciones sobre indicadores.

## Asociaciones para revisar

`BacklogRecords.validate`, `BacklogRepository` e `IterationRepository` no exigen que una historia tenga Objetivo o Sprint. No se inventaron obligaciones basadas en la metodología.

Gestión muestra **Asociaciones · N** solo si existen historias sin alguna asociación. Al desplegarlo se identifican los elementos y se aclara que los vínculos son opcionales. Historias permite consultar y modificar sus relaciones existentes.

El cálculo es derivado: Objetivo → Épica → Historia; Historia → Tarea → Sprint (padre directo o vínculo Sprint–Tarea). Se deduplican historias por ID, se respetan los límites del contexto y no se toman fases o checks como validación de objetivos. No se persiste ningún estado calculado.

## Decisiones pendientes

**Completado del Proyecto:** `Project` y `ProjectEntity` no tienen un estado formal de cierre. El progreso existente procede del contenido. Mantener un Proyecto cerrado pese a nuevas tareas requiere definir un estado explícito y su persistencia/respaldo; no se implementa implícitamente en un sprint de interfaz. Ninguna operación nueva cambia checks, fases o estados automáticamente.

**Asociaciones obligatorias:** si se desean, primero debe aprobarse una política explícita por proyecto/metodología y su contrato. La revisión actual es informativa.

Pendiente revisión física en teléfono de legibilidad, fuentes grandes y recorridos con muchos registros. No se generó APK oficial.

## Archivos de este sprint

- `ui/ManagementSection.kt`: menú contextual, revisión y retorno a herramientas con estado independiente.
- `ui/BacklogUi.kt`: acceso directo a Historias, tarjetas y menú de orden/retirada, estado guardable.
- `ui/PlanningUi.kt`, `ui/IterationUi.kt`: resúmenes y estado guardable.
- `ui/StoryAssociationReview.kt`: cálculo sin persistencia.
- Pruebas: `ManagementUiTest`, `BacklogUiTest`, `PlanningUiTest`, `IterationUiTest`, `StoryAssociationReviewTest`.

Sin cambios de modelos, migraciones, Room 36, JSON 28, cifrado, bóvedas, reglas Sprint/Metro, Personas o versiones. Se conservan los cambios previos del repositorio; no hay commit ni push.

## Validación final

31 pruebas dirigidas distintas aprobadas entre las ejecuciones: asociaciones (4), Gestión (3), Backlog (2), Objetivos (2), Sprint/iteraciones (2), navegación global (9), tarjetas compactas (6) y Kanban (3). Incluyen retorno desde las herramientas, filtro conservado al reabrir Backlog, consulta de asociaciones opcionales sin modificar el registro, apertura del original, check exterior, textos/fuentes grandes y ausencia de barras vacías. Se adaptaron selectores a las etiquetas actuales, al menú contextual y a diálogos; el fixture de consulta de Persona usa ahora un registro guardado y el botón vigente Volver.

`:app:assembleDebug` aprobado. `git diff --check` aprobado. SHA-256 de los siete iconos oficiales coincidente con el registro previo. No se ejecutaron suites completas ni validación sobre datos reales.
