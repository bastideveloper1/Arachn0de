# Metro 1 — Viajes independientes

## Contrato reutilizado

Los viajes ya se almacenan por ID propio en `metro_journeys`, separados de las tareas desde Room 24. `nodeId` es opcional y su FK utiliza `ON DELETE SET NULL`. Metro 1 completa sus operaciones y navegación; no traslada ni reescribe viajes existentes. Room permanece en 36 y el respaldo admite hasta JSON 28. No hay nueva migración, ni cambio de formato ni procesamiento masivo. La reapertura conserva las filas existentes; las migraciones históricas se verifican desde sus esquemas reales.

Se conserva el contrato actual de una tarea por viaje y un viaje por tarea. Para cambiar una asociación se debe desvincular primero; no se transfieren vínculos silenciosamente. Varias tareas por viaje exigirían estudiar un contrato distinto en otro bloque.

## Accesos

- Menú → Metro → Planificar → Guardar viaje: guarda sin crear una tarea. La opción de crear tarea sigue disponible.
- Metro → Viajes → viaje: abre el original. Desde Recorrido se puede editar, duplicar y consultar historial; Opciones del viaje → Eliminar viaje solicita confirmación.
- Durante un seguimiento, Detalle del viaje permite consultar las opciones sin finalizarlo.
- Proyecto → Capa, si corresponde → Tarea → Vincular viaje: busca y selecciona un viaje independiente existente.
- Tarea → Viaje abre por ID. Vínculo Metro → Abrir viaje / Desvincular gestiona la asociación; desvincular solicita confirmación y conserva ruta, sesiones e historial.

## Conservación y concurrencia

Vincular y desvincular cambian únicamente `nodeId` y la revisión. El payload del plan y de las sesiones permanece idéntico. Las operaciones comprueban la revisión y se ejecutan en una transacción; un vínculo inválido o concurrente se rechaza sin cambios. El inicio de seguimiento no depende de la presencia ni del estado estructural de una tarea asociada.

Duplicar crea un ID independiente y copia el plan, viajero y habilitación. No copia sesiones ni historial: el seguimiento y los registros permanecen exclusivamente en el original. Eliminar una tarea conserva el viaje mediante la FK existente. Eliminar un viaje no elimina su tarea; la asociación desaparece junto con el registro. Un viaje activo no puede borrarse: hay que finalizar primero, lo que conserva su registro, y después confirmar expresamente la eliminación.

## Respaldo y bóvedas

La exportación y restauración existentes ya incluyen todas las filas de viajes, vinculados e independientes, y sus payloads completos. Se mantienen lectores históricos y validación de referencias antes de reemplazar datos. La restauración sigue marcando los relojes del seguimiento como inciertos/portables, sin crear una sesión nueva ni perder sus eventos. No cambia SQLCipher, la identificación de bóvedas ni el rechazo de respaldos de otra bóveda.

Las pruebas JVM de aislamiento utilizan sesiones reales de bóveda y archivos de respaldo cifrados, sustituyendo exclusivamente SQLCipher por SQLite en memoria de prueba. No constituyen una validación nativa SQLCipher ni acceden al teléfono.

## Alcance pendiente

Metro 2 podrá reorganizar el listado y sus acciones. Se mantiene el diseño actual y no se alteran estaciones, cálculo de rutas, etapas, temporizadores ni recursos oficiales. Queda revisión física de legibilidad y uso del selector con muchos viajes.

## Validación realizada

36 casos dirigidos aprobados entre las ejecuciones de MetroPersistenceTest (15), MetroMigrationTest (4, con SDK 24/28), MetroServiceTest (2), MetroIndependenceVaultTest (1), MetroIndependenceUiTest (2), GlobalNavigationUiTest (9) y MetroUiTest (3). Cubren viajes independientes, vínculo y desvínculo activos, revisiones obsoletas y conflictos atómicos, copia sin sesión, borrados seguros, reapertura, Room histórico 23/24 → 36 y segunda apertura, respaldos históricos/nuevos, aislamiento, notificaciones, pausa/reanudación, recuperación y navegación de interfaz.

Las últimas pruebas de interfaz aprobaron junto con `assembleDebug` (BUILD SUCCESSFUL). `git diff --check` correcto y los siete PNG oficiales mantienen sus SHA-256 previos. No se generó APK oficial, no se cambió la versión de aplicación y no se accedió al teléfono.
