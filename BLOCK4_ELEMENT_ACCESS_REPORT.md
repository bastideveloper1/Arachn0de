# Bloque 4 — Búsqueda, Favoritos y captura rápida

## Implementado

- Accesos compactos Buscar, Favoritos y + Tarea dentro de productividad, conservando la creación completa.
- Búsqueda por título y descripción sin acentos ni distinción de mayúsculas, con normalización de títulos multilínea; resultados identificados por tipo y ruta jerárquica. Apertura de entidades originales por la navegación actual. Los datos observados actualizan resultados tras edición, movimiento o eliminación. Indexación y filtrado fuera del hilo de interfaz.
- Estrella accesible para marcar/desmarcar Proyectos, Capas y Tareas, lista central y persistencia independiente por almacén. No altera orden ni crea copias. La eliminación limpia referencias.
- Captura de título con descripción opcional y selector de destino; recuerda el último destino válido, permite cancelar sin escribir y reutiliza las validaciones del repositorio normal. ID estable por borrador y bloqueo durante guardado evitan duplicados por reintento/doble pulsación. La tarea queda editable con el editor existente.
- Recientes y Frecuentes separados de Favoritos, sin telemetría. Historial acotado a 20 recientes y 200 contadores, sin limitar entidades o favoritos.
- Diálogos de ancho explícito y listas desplazables para evitar oscilaciones de medición y mantener controles accesibles.

## Persistencia y decisiones

Se reutilizan IDs, observadores, navegación y creación existentes. La nueva fila `element_access` pertenece a `private_preferences` de la base protegida actualmente abierta; no hay preferencias públicas ni índice entre almacenes. Backup/restauración incluyen el payload completo y su validación semántica. Sin migraciones, tablas, medios ni cambios de versión (0.3.0 / 10). El movimiento conserva favoritos por identidad; las rutas se recalculan. Una conversión que elimina la identidad anterior depura su referencia en lugar de inventar una asociación.

No se implementa widget: el acceso interno satisface la captura rápida sin añadir exposición de datos protegidos en la pantalla de inicio. No se modifica Metro, juego, cifrado ni Sprint en este bloque. Se conservan los cambios previos presentes en el repositorio.

## Archivos de este bloque

- `app/src/main/java/com/r0ybt/arachn0de/access/AccessRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/access/ElementSearch.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ElementAccessUi.kt`
- Integración en `ui/AppRoot.kt`, `ui/ProjectScreen.kt`, `ui/ProjectComponents.kt`, `ui/NodeComponents.kt`.
- Limpieza en `data/repository/NodeRepository.kt` y `ProjectRepository.kt`; integridad en `backup/BackupData.kt`.
- `app/src/test/java/com/r0ybt/arachn0de/access/ElementAccessTest.kt`, `ElementAccessUiTest.kt` y ampliación dirigida de `security/VaultManagerTest.kt`.
- `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe.

## Validación

Compilación `:app:assembleDebug` y pruebas dirigidas `:app:testDebugUnitTest` completadas con BUILD SUCCESSFUL. 22 pruebas sin fallos: 4 de repositorio/búsqueda/backup, 4 de interfaz (apertura jerárquica, captura repetida, cancelación y fuentes grandes), 1 de almacenes protegidos/reinicio, 9 de Guardados y 4 de Personas. Comando ejecutado con JDK de Android Studio y dependencias offline.

Las pruebas comprueban búsqueda con acentos, mayúsculas, descripciones y saltos de línea; persistencia tras reapertura; movimiento y eliminación; captura idempotente y edición normal; restauración y rechazo antes de reemplazar; ausencia de accesos del principal al desbloquear señuelo. La comprobación final `git diff --check` pasa. No se generan firmas oficiales ni se realizan commits o push.

## Límites y comprobaciones en teléfono

Las pruebas son locales con Robolectric/Compose, no certifican nuevamente la migración nativa SQLCipher. Verificar en teléfono: buscar y abrir descendientes, mover/eliminar favoritos, reabrir y comprobar estrellas, capturar con doble pulsación y editar después, cambiar destino, probar ambos almacenes y un respaldo/restauración, temas claros/oscuros y fuentes grandes. La captura recuerda el último destino válido; cuando no existe, ofrece un destino disponible que puede cambiarse. Recientes registra aperturas de Proyecto y navegación a nodos, no cada consulta de un icono o dato accesorio.
