# Personas: ficha avanzada y relaciones — primera fase

## Funciones y acceso

Menú lateral → Personas → tocar una tarjeta o Ver ficha. El listado es buscable.
La ficha separa consulta de edición: General (nombre/avatar existentes, alias,
descripción, notas), Contacto (teléfonos, correos, direcciones y enlaces, multilínea),
Perfil (cualidades, habilidades, intereses, disponibilidad descriptiva) y Relaciones.
Editar General/Contacto/Perfil abre solo esa sección; Guardar sección confirma,
Cancelar conserva los datos. Editar nombre y avatar reutiliza el editor y ciclo
existentes. Crear Persona sigue requiriendo únicamente nombre.

Relaciones → Añadir relación → buscar Persona existente → categoría y tipo →
modalidad → Guardar vínculo. Hay presets familiares, sociales, laborales y académicos;
cualquier categoría permite escribir otro tipo y Personalizadas no presume
reciprocidad. Los modos son dirigida con inversa, recíproca y no recíproca. En el
último, la ficha de destino identifica un vínculo entrante sin inventar una inversa.
El origen se indica explícitamente al editar incluso desde su presentación inversa.

Tocar la Persona relacionada abre su ficha, y Volver retrocede por las fichas antes
de regresar al listado. Editar vínculo conserva su UUID; Eliminar vínculo exige
confirmación y conserva ambas Personas. Eliminar Persona avisa de la retirada de
ficha, relaciones y asignaciones; las otras Personas y los elementos se conservan.
Los accesos de responsables y Detalle de Persona en participantes abren esta misma
ficha. Los selectores de asignación siguen seleccionando, sin acciones nuevas en
indicadores compactos. Gestión de participantes no se duplica en la ficha global.

## Persistencia y recuperación

Room 33→34, aditiva: `person_profiles` y `person_relations`. No se reescriben Personas,
árboles, IDs, archivos ni encuadres existentes. Las relaciones son un registro con
presentación inversa calculada; claves únicas, validación transaccional y FK CASCADE
protegen duplicados, autorreferencias y borrados. No se crean archivos nuevos.

JSON 26 incluye perfiles y vínculos; lectores históricos 1–25 inicializan ambos
vacíos. Sin los nuevos datos se mantiene la selección histórica del JSON. Lectores
antiguos rechazan JSON 26; no existe downgrade de Room. Snapshot, staging de medios,
restauración atómica, aislamiento de bóvedas y seguridad permanecen en sus mecanismos
existentes. Detalles: BACKUP_FORMAT.md y ARCHITECTURE.md.

## Validación

96 casos dirigidos distintos aprobados tras corregir expectativas obsoletas de
versión en pruebas anteriores. 13 casos nuevos: siete de datos/backup, dos variantes
Android 24/28 de migración histórica auténtica 33→34, uno de backup cifrado/bóvedas y
tres de interfaz. Se conservan regresiones de Personas, encuadre, participantes,
formatos y rollback de backup, además de migraciones históricas de Sprint, vínculos,
gestión, planificación, Backlog, participantes e iteraciones.

Se comprobaron: campos opcionales y persistencia, inversas sin segundo registro,
duplicados inversos (también hijo/progenitor), personalizadas sin reciprocidad,
edición y eliminación de vínculos, borrado de Persona conservando la otra, IDs y
responsabilidades tradicionales, respaldo/restauración con avatares compartidos y
encuadres, fallo de restauración tras borrado con rollback de perfiles/vínculos y
sin huérfanos de esa operación, límites, consulta/edición/cancelación, navegación
Persona→Persona y regreso, búsqueda entre 100 Personas y fuente 1.6×.

`git diff --check` correcto; archivos nuevos sin espacios finales.
`:app:assembleDebug` final: BUILD SUCCESSFUL (53 s).
La prueba de bóvedas usa sesiones y archivos/backup cifrados reales con SQLCipher
sustituido únicamente por SQLite/Room en Robolectric. No certifica SQLCipher nativo
Room 34. Quedan pendientes revisión visual en teléfono y nueva validación nativa
sobre este código cuando se autorice preparar otro release. La evidencia anterior
queda invalidada por la nueva huella; no se renovó ni se alteró el protocolo.

## Archivos de esta fase

Producción (prefijo `app/src/main/java/com/r0ybt/arachn0de/`):
- Nuevos: `data/local/PersonProfileEntities.kt`, `domain/model/PersonRelations.kt`,
  `ui/PersonDetailUi.kt`.
- Integrados: `data/local/Arachn0deDatabase.kt`, `data/repository/PersonRepository.kt`,
  `backup/BackupData.kt`, `backup/BackupJson.kt`, `backup/BackupRepository.kt`,
  `ui/PeopleScreen.kt`, `ui/PersonComponents.kt`, `ui/ParticipationUi.kt`.
- Esquema nuevo: `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/34.json`.

Pruebas nuevas bajo `app/src/test/java/com/r0ybt/arachn0de/`:
`data/PersonEvolutionTest.kt`, `data/PersonEvolutionMigrationTest.kt`,
`data/PersonEvolutionVaultTest.kt`, `ui/PersonEvolutionUiTest.kt`.
Expectativas adaptadas en PersonRepositoryTest, BackupRepositoryTest, PlanningTest
(versión futura rechazada), IterationMigrationTest, SprintLinksMigrationTest,
ManagementMigrationTest, BacklogMigrationTest, ParticipationMigrationTest,
PlanningMigrationTest, SprintMigrationTest y el destino/colecciones vacías de
NativeVaultTest. No se ejecutaron nuevas pruebas nativas en esta fase.
Documentación: este informe, ARCHITECTURE.md y BACKUP_FORMAT.md.

## Límites de alcance y entrega

No se implementaron estadísticas, desempeño, informes, evaluación automática,
Tecnologías por Persona, Desktop ni LAN. No se cambió la versión comercial 0.3.0/10,
ni juego, Metro o navegación global. Se preservaron cambios previos del repositorio.
No hubo commit, push, acceso al teléfono ni generación/firma de APK oficial.
La APK oficial anteriormente entregada no contiene esta fase; el artefacto nuevo es
solo `app/build/outputs/apk/debug/app-debug.apk`, sin instalación automática.
