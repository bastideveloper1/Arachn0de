# UI/UX 3 — Indicadores visuales unificados

## Implementación

`CompactCardIndicators.kt` centraliza los datos derivados, el catálogo de iconos, su orden, colores, procedencia y componentes de vista previa y detalle.

La vista previa usa una sola fila horizontal de iconos de **16 dp**, separados por **6 dp**, sin fondos, bordes ni etiquetas. El ancho disponible y la escala de fuente determinan cuántos caben; **+N** representa el resto. No se reserva una fila vacía. Los indicadores siguen siendo informativos en la vista previa: pulsarlos abre la tarjeta mediante su acción exterior existente.

Orden común: Personas → Tecnologías → Notas → Tareas → Archivos → Vencimiento → Pendientes para hoy → Prioridad alta → Metro. Solo aparecen categorías pertinentes.

- **Azul:** asignaciones tradicionales, participantes gestionados y responsabilidades sobre nodos. Se deduplican por ID de Persona; no se interpreta una responsabilidad como trabajo realizado.
- **Morado:** Tecnologías, Notas, tareas descendientes o vinculadas a historias, contenido con adjuntos y Metro. Un vencimiento sin alerta también es morado.
- **Rojo:** trabajo pendiente con vencimiento dentro de las próximas **24 horas** o vencido, pendientes para hoy y prioridad **HIGH** del modelo actual. No se añade una prioridad «crítica» ni nuevas reglas. Un elemento completado no recibe rojo por su vencimiento. Las fechas propias de una Capa generan atención cuando tiene trabajo pendiente descendiente.

Gestión conserva la revisión opcional de UI/UX 2 y utiliza el icono de vínculo de 16 dp con el mismo morado. No existen reglas que obliguen a asociar historias a Objetivos/Sprint, por lo que no se inventan alertas por su ausencia.

## Consulta y navegación

En Proyecto → Capa → Tarea, la zona de consulta muestra los mismos iconos, orden y colores con áreas táctiles de **48 dp**. Cada categoría abre una explicación breve y una lista de registros asociados. Los elementos originales se abren por ID; Personas abre su ficha, Tecnologías permite consultar su recurso y acceder al catálogo existente. Los adjuntos llevan al contenido original que los contiene, conservando el visor existente. Los viajes configurados llevan al elemento asociado y Metro activo reutiliza su navegación actual y movimiento reducido.

La procedencia se distingue como **Propio**, **Descendiente** o **Relacionado**. Los vínculos Historia–Tarea aportan contenido; no se inventan alertas heredadas para una tarea externa que no sea descendiente.

Las fichas abiertas desde Búsqueda reciben la misma instantánea y directorio. No se cambia la navegación global, filtros, checks ni fases.

## Rendimiento y conservación

Una instantánea compartida se calcula en `Dispatchers.Default` a partir de los flujos existentes. No hay consultas por tarjeta, ni decodificación de originales para los iconos. La actualización temporal de las advertencias se acota al minuto; también se recalcula al cambiar sus datos. Se deduplican conjuntos de identificadores en las agregaciones. Las consultas de detalle usan listas perezosas.

Los datos de participación se observan de forma global mediante el repositorio existente, sin suscripciones independientes por tarjeta. No se persisten indicadores derivados.

Sin migraciones, cambios de respaldo, cifrado, bóvedas, estados persistentes o versiones. No se modificaron los siete PNG oficiales ni las reglas de Sprint, Backlog o Metro. Los parámetros añadidos a `MetroActivityIndicator` solo controlan tamaño y color de presentación, conservando sus valores predeterminados para otros usos.

## Limitaciones

- Las alertas heredadas representan descendientes físicos del ámbito, con independencia de que un filtro oculte su tarjeta. No se modifican filtros ni se marca trabajo como completado.
- No se propagan alertas externas a través de vínculos de historias o Sprint: una relación no convierte automáticamente una tarea en descendiente. Los proyectos siguen reflejando las tareas de su árbol.
- Los archivos se identifican por el contenido que los referencia; no se presentan conteos de archivos ni se introducen lecturas adicionales del almacenamiento.
- No se añaden indicadores para objetivos, KPI u otras categorías sin un contrato visual en este bloque.
- Pendiente revisión física de los temas, fuentes grandes y contraste sobre las superficies reales del teléfono. Los colores se prueban contra las superficies Material claras y oscuras.

## Archivos

- `ui/CompactCardIndicators.kt`: datos y componentes comunes.
- `ui/AppRoot.kt`: instantánea compartida, participación y disponibilidad desde Búsqueda.
- `ui/ProjectScreen.kt`: consulta y navegación contextual.
- `ui/ManagementSection.kt`: presentación de asociaciones opcionales.
- `metro/MetroActivity.kt`: parámetros visuales compatibles.
- `CompactCardFactsTest.kt`, `CompactPreviewUiTest.kt`: pruebas dirigidas de semántica y accesibilidad.

Sin APK oficial, commit ni push.

## Validación final

**31 pruebas dirigidas distintas aprobadas** entre las ejecuciones: datos/semántica (9), vistas previas y consulta (9), navegación global (9), Gestión (3) y botón compacto Metro (1). Cubren orden, contraste claro/oscuro, visibilidad, ausencia de falsas alertas, herencia y vínculos, deduplicación de Personas, árbol de 1.000 tareas, fila vacía, +N con fuentes grandes, consulta/navegación y conservación de los recorridos anteriores.

Compilación **Debug aprobada** (`:app:assembleDebug`). `git diff --check` limpio. SHA-256 de los siete PNG oficiales coincidente con el registro anterior. Se corrigió el fixture de la prueba de herencia para incluir su relación padre real; no se modificaron reglas de producción para hacerla pasar.
