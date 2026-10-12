# Bloque 9 — Identidad y modalidad reversible de gestión

Implementado y verificado sobre Room 28 / JSON 20. Este bloque no incorpora acta, objetivos, criterios ni las herramientas posteriores.

## Modelo

`management_contexts` contiene `id`, `enabled` y `methodology`. El ID es el de la raíz permanente de `conversion_roots`; una FK y la clave primaria impiden contextos huérfanos o duplicados. La raíz tiene exactamente un propietario Proyecto o Capa y conserva su identidad aunque cambie el ID físico por colisión. No se copian nombres, descripciones ni árboles.

Ausencia de contexto significa modalidad Normal. Activar crea el contexto y, solo si hace falta, su raíz; desactivar conserva ID y metodología. Ágil, Tradicional e Híbrida son configuraciones editables que no alteran tareas, completitud ni fases Sprint. La interfaz «Gestión del proyecto» está en el detalle del Proyecto y en las Capas que conservan una raíz de Proyecto convertido. Cancelar no escribe datos.

## Conversión y eliminación

Proyecto → Capa → Proyecto mantiene los contextos, incluidos los desactivados y anidados, sin fusionarlos. La conversión captura los contextos antes de reinsertar nodos, comprueba que sobrevivan todas sus raíces y restituye los contextos afectados por cascadas antes del commit. Cualquier fallo revierte la operación completa. También se comprobaron colisiones de IDs físicos y restauración de jerarquías anidadas.

Se mantienen los rechazos del Bloque 8 para conversiones con vínculos externos Sprint–Tarea. Activar/desactivar o cambiar metodología conserva los vínculos y fases existentes. Eliminar explícitamente el propietario definitivo, mediante los controles de borrado existentes, elimina su raíz y contexto por FK. Desactivar nunca elimina.

Las futuras tablas de herramientas deberán incluir sus datos dependientes en la captura y restitución transaccional de conversiones. La identidad estable no preserva automáticamente registros futuros frente a cascadas.

## Persistencia y respaldo

Migración aditiva **Room 28→29**: únicamente añade la tabla y sus invariantes; no crea contextos ni reescribe datos históricos. Los Proyectos existentes permanecen Normales.

**JSON 21** incluye `managementContexts` cuando existe cualquier contexto, activo o desactivado. Sin contextos se conserva la selección 17–20. El lector admite 1–21; los históricos inicializan la lista vacía. Lectores antiguos rechazan JSON 21. Snapshot/restauración incluyen raíces y contextos transaccionalmente, con validación previa de identidades, tipos, metodologías y propietarios. Se probaron rechazo de referencias inválidas y rollback con fallo SQL inyectado.

No cambian contenedor, cifrado, credenciales, SQLCipher ni aislamiento de sesiones. El repositorio utiliza exclusivamente su base de datos; no guarda configuración global. No hay archivos privados o medios nuevos, migraciones inversas ni cambios de versión comercial.

## Archivos

Bajo `app/src/main/java/com/r0ybt/arachn0de/`:

- `data/local/ManagementContextEntity.kt`: entidad, DAO, migración e invariantes.
- `data/local/Arachn0deDatabase.kt`: Room 29 y registro de migración.
- `data/local/ConversionEntities.kt`: observación de propietarios para la interfaz.
- `data/repository/ManagementRepository.kt`: activación, configuración y desactivación transaccionales.
- `data/repository/NodeRepository.kt`: acceso al repositorio de gestión de su base.
- `data/repository/ProjectNestingRepository.kt`: conservación transaccional durante conversiones.
- `backup/BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt`: validación, JSON 21, snapshot/restauración.
- `ui/ManagementSection.kt`, `ProjectScreen.kt`: interfaz compacta integrada.

Además: esquema `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/29.json`; pruebas `data/ManagementTest.kt`, `data/ManagementMigrationTest.kt`, `ui/ManagementUiTest.kt`; actualización de la versión esperada al abrir la base en `data/SprintMigrationTest.kt` y `data/SprintLinksMigrationTest.kt`; documentación `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe. Se conservaron los cambios previos del workspace.

## Validación

**65 pruebas dirigidas aprobadas**, sin fallos ni omisiones: ManagementTest, ManagementMigrationTest (SDK 24/28), ManagementUiTest, ProjectNestingTest, ProjectConversionBackupTest, SprintLinksTest, SprintLinksMigrationTest, SprintMigrationTest, SprintBackupTest y BackupFormatTest.

Cobertura: Normal por defecto, activación/desactivación/reactivación, tres metodologías, ID estable, árbol intacto, reinicio, contextos anidados, colisiones físicas, conversiones y rollback, eliminación del propietario, respaldo/restauración activo y desactivado, restauración anidada, importación histórica, huérfanos/duplicados/metodologías inválidas y compatibilidad con Sprint. Aislamiento comprobado con bases separadas de prueba; no se presenta como nueva acreditación criptográfica nativa.

`git diff --check` limpio. Compilación `:app:assembleDebug` exitosa. Sin firma oficial, commit, push ni cambios al juego. No se accedió al teléfono.

Pendiente: revisión física de teclado, fuentes grandes, textos largos y comodidad táctil. La evidencia nativa exigida por el protocolo de release debe renovarse antes de preparar una distribución oficial; este bloque genera únicamente Debug.
