# Bloque 10 — Acta, objetivos y criterios

## Comportamiento implementado

En gestión activa, «Acta y objetivos» abre la planificación del contexto. Normal no muestra esta herramienta. El acta admite cinco campos opcionales y guardado parcial; título y descripción se leen del Proyecto o Capa actuales. Los objetivos y criterios tienen UUID independientes de su posición, edición y orden manual. No se crean Capas ni Tareas.

La validación de un criterio es explícita y guarda una fecha en milisegundos; repetir la validación no cambia la fecha. Revocar vuelve a pendiente y borra la fecha vigente. Editar textos conserva IDs, orden y validación existente: el usuario puede revocarla explícitamente. La aceptación del objetivo se calcula desde sus criterios: vacío significa «Sin criterios definidos», nunca aceptado. «Qué falta para cumplirlo» muestra pendientes o el mensaje correspondiente. Ninguna operación cambia completitud, fases o vínculos Sprint.

Eliminar un objetivo o criterio requiere confirmación en la interfaz. El objetivo elimina únicamente sus criterios; borrar definitivamente el propietario elimina su planificación por FK. Desactivar gestión conserva todos los datos y reactivar los recupera, también al cambiar metodología.

## Persistencia y respaldo

Room **29→30**, migración aditiva transaccional:

- `project_charters`: PK/FK `contextId`, propósito, alcance, exclusiones, entregables y condiciones generales de éxito.
- `management_objectives`: PK `id`, FK `contextId`, título, descripción y posición.
- `acceptance_criteria`: PK `id`, FK `objectiveId`, descripción, posición, validación y fecha nullable.

FK en cascada e invariantes de texto obligatorio, posición no negativa y coherencia validación/fecha. El respaldo además rechaza órdenes duplicados dentro del mismo propietario, referencias inválidas, IDs duplicados y tipos incorrectos. Órdenes con huecos siguen siendo válidos; crear o reordenar normaliza posiciones sin cambiar IDs.

JSON **22** contiene los tres conjuntos cuando existe planificación, incluso desactivada. Se leen JSON **1–22**; históricos sin planificación producen listas vacías. Sin estos datos se mantiene la elección de formatos anterior. Contenedores, cifrado, identidad de almacenes, medios y versiones comerciales no cambian. **Lectores antiguos no importan JSON 22; no hay downgrade de Room 30.** Ver [BACKUP_FORMAT.md](BACKUP_FORMAT.md).

Snapshot y restauración incluyen todos los registros dentro de la transacción existente; se valida antes de reemplazar. Conversión Proyecto → Capa → Proyecto captura y restituye planificación después del contexto y antes del commit, incluyendo contextos anidados y colisiones de IDs físicos. Los fallos y cancelaciones previos al commit revierten íntegramente el estado. No se añaden archivos privados ni exclusiones nuevas del backup. Los valores aún no guardados de formularios son temporales.

## Archivos de este bloque

- Nuevos: `data/local/PlanningEntities.kt`, `data/repository/PlanningRepository.kt`, `ui/PlanningUi.kt`.
- Integración: `Arachn0deDatabase.kt`, `ManagementRepository.kt`, `ProjectNestingRepository.kt`, `ManagementSection.kt`, `ProjectScreen.kt`.
- Respaldo: `BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt`.
- Esquema: `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/30.json`.
- Pruebas nuevas: `PlanningTest`, `PlanningMigrationTest`, `PlanningUiTest`, `PlanningVaultTest`. Expectativas de versión actual adaptadas en `ManagementMigrationTest`, `SprintLinksMigrationTest` y `SprintMigrationTest`.
- Documentación: `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe. Los cambios previos del checkout se conservan.

## Validación

La selección dirigida de persistencia, migración API 24/28, interfaz, gestión, conversiones, Sprint y respaldo pasó **94 casos**. Incluye lectura/restauración de cabeceras históricas JSON 1–21, JSON 21 con contexto real, JSON 22 con planificación, fallos de restauración/conversión, cancelación antes del commit, colisiones físicas, contextos anidados, reapertura, órdenes, aceptación vacía, revocación y eliminación por propietario. Las pruebas Compose verifican confirmaciones, reactivación y textos largos con fuente al 180 %.

La comprobación adicional `PlanningVaultTest` verifica sesiones principal/señuelo, reapertura y recuperación desde la envoltura autenticada de un respaldo con planificación, además del rechazo de un respaldo principal en el señuelo. En JVM sustituye únicamente la biblioteca nativa SQLCipher por Room SQLite, como las pruebas existentes de sesiones; no representa una prueba nativa en teléfono.

Esta prueba adicional pasó **1 caso**: **95 casos dirigidos aprobados en total** (16 nuevos y 79 regresiones pertinentes). No se ejecutó la suite global.

`git diff --check` pasó; también se revisaron espacios en los archivos nuevos. `:app:assembleDebug` terminó con **BUILD SUCCESSFUL**. No se generó ni firmó una APK oficial; la compilación utiliza la configuración Debug existente.

## Revisión física pendiente

Comprobar formularios con teclado/IME, fuente y accesibilidad propias del teléfono, navegación Atrás, tamaños táctiles y textos extensos. Verificar actualización con la firma habitual y migración SQLCipher en un entorno de prueba respaldado cuando se confirme la versión instalada. No se accedió a datos del teléfono, no se preparó una APK oficial y no se ejecutaron firma oficial, commit ni push.
