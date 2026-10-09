# Sprint previo a release: Tecnologías, Valores predeterminados y Guardados

## Implementación

Las tecnologías de consulta solo abren información y no modifican asignaciones. Las tarjetas ocultan completamente la presentación vacía, muestran hasta tres tecnologías según el orden guardado y cuentan las restantes. Tecnologías y participantes comparten una fila adaptable en tarjetas, conservando los iconos, avatares y nombres consultables. El detalle de responsables y los selectores usan agrupaciones que envuelven los elementos según el ancho. La edición se abre explícitamente desde el menú o el formulario; quitar usa × y el arrastre mantenido sobre ≡ cambia el orden. El catálogo y las Personas no se reordenan.

La pantalla existente de Valores predeterminados conserva sus diez controles, herencia, validaciones, restablecimiento y persistencia. Agrupa tipo/obligación, moneda/prioridad, etiquetas/responsables y cada fecha/hora. `AdaptiveFormRow` usa dos columnas cuando caben, considerando la escala de fuente, y vuelve a una columna cuando es necesario. No agrega herencia de Tecnologías/Participantes ni cambia la creación de Proyectos.

Guardados se abre inmediatamente después del título del formulario de creación. Permite buscar, aplicar manualmente, editar/renombrar y eliminar con confirmación. Una tarea ofrece Guardar como plantilla en sus acciones. La identidad de la plantilla es nueva e independiente; borrar la tarea original no la elimina. Aplicar conserva abierto el formulario y no crea tareas, pagos, notificaciones ni sesiones. Un borrador con datos requiere confirmación; las referencias eliminadas se informan y se pueden omitir. Crear el elemento confirma sus asignaciones y el plan Metro en una transacción, dentro del protocolo existente de adjuntos.

Las plantillas guardan título, texto, propósito Tarea/Nota, prioridad, importe/moneda como configuración, personas, etiquetas, tecnologías ordenadas y plan Metro/viajero opcionales. Las paradas, origen/destino y servicio expreso del plan pueden editarse usando el selector de estaciones existente. No guardan finalización, progreso, eventos, historial financiero, auditoría, sesiones Metro, identidad original ni reglas recurrentes. Recurrencias conserva su motor.

Inicio/vencimiento admiten Sin fecha, Hoy, Mañana, En N días (1–3650), Día N del próximo mes (1–31), Primer día del próximo mes y Último día del mes, con hora local HH:mm. Se calculan al aplicar; los días inexistentes se ajustan al último día válido. Al crear desde una tarea con fecha se propone Hoy conservando la hora, con explicación visible y posibilidad de cambiar la regla.

## Persistencia e integridad

Room 24→25 es aditiva: agrega `position` (entero, default 0) a ambas relaciones de tecnologías y crea `saved_templates(id,name,payload)`. La migración asigna a los registros previos el orden del catálogo que mostraba la versión anterior, con desempate por ID. Las consultas ordenan por propietario, posición e ID. Las conversiones Proyecto↔Capa conservan las posiciones. La lectura del catálogo y sus relaciones se realiza en una transacción; los reintentos conservan el último snapshot confirmado.

El contenedor sigue en v2; JSON pasa de 16 a 17. Exporta/restaura posiciones y las plantillas en el snapshot y la transacción existentes. Los lectores 1–16 restauran Guardados vacío y reconstruyen el antiguo orden de tecnologías por nombre. Se validan IDs duplicados, tipos, límites, reglas de fecha, importes, listas de referencias sin duplicados y planes sin sesiones. Las referencias desaparecidas de plantillas son configuraciones pendientes de resolver, no relaciones Room inválidas: se filtran y se avisan antes de aplicar. La sustitución del backup valida antes de escribir y conserva el estado anterior ante fallos.

Las plantillas no crean archivos privados. Personas y Tecnologías conservan sus propietarios multimedia y su estrategia de backup/lifecycle. Las imágenes adjuntas al texto original se excluyen explícitamente de la plantilla; el texto restante se conserva. No se guardan rutas del dispositivo ni referencias huérfanas a archivos.

Archivos principales (base `app/src/main/java/com/r0ybt/arachn0de/`):

- Nuevos: `data/local/SavedTemplateEntity.kt` (entidad, DAO, migración); `templates/SavedTemplates.kt` (configuración, fechas, codec y repositorio); `ui/SavedTemplatesUi.kt`.
- Persistencia e integración: `Arachn0deApplication.kt`, `data/local/Arachn0deDatabase.kt`, `TechnologyEntities.kt`, `TechnologyDao.kt`, `data/repository/TechnologyRepository.kt`, `NodeRepository.kt`, `ProjectNestingRepository.kt`, `domain/model/CreationUndo.kt` y `ui/state/NodeActions.kt`, `EditorDraft.kt`.
- UI: `ui/TechnologyComponents.kt`, `PersonComponents.kt`, `CreationDefaultsScreen.kt`, `EditDialogs.kt`, `NodeComponents.kt`, `ProjectScreen.kt`. `metro/MetroScreen.kt` expone internamente el selector existente para reutilizarlo. `metro/MetroRepository.kt` extrae `MetroPlanPersistence` conservando la misma implementación, para que Guardados y Metro compartan la validación, identidades, revisiones y conservación de sesiones.
- Backup: `backup/BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt`; esquema `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/25.json`.

## Límites y distribución

Guardados no replica adjuntos de descripción ni copia reglas de Recurrencias. Las tecnologías ordenadas y los planes Metro se guardan mediante creación individual; el formulario exige desactivar Creación múltiple/Recurrencia o quitar esas propiedades antes de usar esas modalidades. Los responsables, etiquetas y configuración financiera siguen admitiendo las modalidades existentes. No se propagan las tecnologías de una edición individual al resto del grupo.

No cambia el catálogo Metro r1, las rutas históricas, los seguimientos ni sus migraciones. Se conservan versión de aplicación 0.3.0/9, firma oficial y cinco PNG del juego. No se prepara release, firma, instalación, commit ni push. La interacción física y la apariencia final siguen pendientes del dogfooding del usuario; las pruebas de interfaz usan Robolectric.

## Validación

**278 casos dirigidos distintos aprobados a lo largo de las tandas**, con compilación `assembleDebug` y `git diff --check` correctos. No se ejecutó la suite global.

- Backup: 136 casos del paquete `backup`, incluida compatibilidad histórica, restauración, medios, integridad, rollback y cancelación. Su ejecución está justificada por el cambio común del formato JSON a v17.
- Metro: 34 casos del paquete `metro`, incluidas las 143 entradas maestras, rutas, seguimiento, servicio, migración y backup mixto histórico/r1. La última tanda volvió a aprobar los 34 después de compartir la persistencia de planes.
- Guardados: 15 ejecuciones (9 de configuración/repositorio/fechas/backup/identidad/Metro, migración 24→25 en SDK 24/28 y 4 de interfaz a 320 dp).
- Tecnologías: 9 de repositorio y 7 de interfaz, incluido arrastre real, orden principal, contador oculto, consulta sin desasignación y eliminación explícita.
- Valores predeterminados: 6 de interfaz existente y 1 nuevo de distribución a 320 dp, preservando los diez controles.
- Regresiones restantes: 14 ejecuciones de Personas, 26 de Recurrencias, 6 de creación múltiple, 4 de alertas/tarjetas, 1 de interfaz de Personas, 6 del estado del borrador y 13 de edición de adjuntos.

Se corrigió un fixture que simulaba v15 conservando accidentalmente un campo de v17. La prueba de arrastre detectó que el selector debía contener sus propias filas y usar centros medidos en vez de alturas supuestas; se corrigió y aprobó el gesto. La última tanda aprobó 49 casos de Metro/Guardados y volvió a completar `assembleDebug`. Logs locales: `/tmp/arachnode-sprint-validation.log`, `/tmp/arachnode-sprint-ui-validation.log`, `/tmp/arachnode-sprint-gestures.log`, `/tmp/arachnode-sprint-drag-final.log`, `/tmp/arachnode-sprint-metro-final.log`.

APK únicamente debug: `app/build/outputs/apk/debug/app-debug.apk` (103.492.255 bytes). No se instala ni se copia a distribución. Las cinco imágenes del juego se compararon byte a byte con los originales del usuario; todas coinciden. El catálogo empaquetado de producción continúa en `santiago-2024-02-beta1-r1`.
