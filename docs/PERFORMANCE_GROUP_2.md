# Rendimiento — Grupo 2 (R01, R03, R04, R05, R06)

## Línea base y límites

No hay dispositivos conectados por ADB. No se midieron latencias representativas
ni RAM del Pixel 7a. Los tres segundos comunicados por el usuario siguen siendo
un síntoma sin causa única demostrada. Las duraciones de Gradle y de Robolectric
no son tiempos de respuesta del teléfono.

La inspección focalizada confirmó antes de intervenir:

- R01: creación perezosa de preferencias mediante `runBlocking(IO)` desde UI;
  lectura/validación de apariencia durante composición; commits con el mismo
  monitor que los getters, que podían esperar a una escritura lenta.
- R03: índice transversal y borradores observados sin consumidor; asignaciones
  consultadas globalmente y de nuevo dentro del Núcleo.
- R04: una consulta de nodos más una de eventos completos por cada 500 IDs,
  aunque solo se necesitaba la fecha máxima de COMPLETED.
- R05: dos observaciones raíz (iteraciones combinadas y relaciones de historias)
  iniciadas sin contexto de Sprint activo.
- R06: superficie inicial vacía y preparación de creación sin señal visible.

## Intervenciones

### R01

Preparación en IO de las cuatro preferencias usadas al entrar en la interfaz:
apariencia, seguridad y ambas preferencias de calendario. La apariencia pertenece
a la sesión, se prepara antes de publicarla y expone su estado observable a Compose.
Las notificaciones se agrupan y recargan en IO. Cada lectura de apariencia usa
un snapshot de claves, conservando la normalización de temas históricos.

Los ajustes de apariencia, calendario y bloqueo automático conservan commit
durable y manejo de errores, ejecutado fuera de Main. El trabajo de escritura
iniciado no se cancela por abandonar la composición; una sesión revocada sigue
rechazando operaciones. Los getters siguen mostrando el último estado confirmado
mientras se escribe, sin esperar al disco. Los accesos a disco no retienen el monitor común del contexto. La biblioteca de apariencia se vacía
con el propietario R10 y rechaza publicaciones tras revocación.

La API SharedPreferences sigue siendo síncrona: no se convierte `apply()` en una
cola diferida ni se cambian SQLCipher, formatos, tablas o preferencias almacenadas.
Otros espacios de preferencias no precargados conservan su lectura perezosa y
no quedan cubiertos por esta intervención: solicitar un espacio nuevo desde Main
todavía puede bloquear. La API no convierte automáticamente esos accesos en IO.

### R03

Índice compartido mientras existen consumidores: búsqueda, destinos, captura,
borradores o una captura compartida visible. Al cerrarse el último consumidor se
detienen su origen y los borradores; al volver se exige una lectura fresca. Una
captura compartida pendiente continúa usando su cola y flujo existentes.

Los Núcleos reciben las asignaciones globales existentes, sin volver a consultar
la misma relación; la pantalla aislada conserva su observación local. La proyección de asignaciones recorre solo los IDs del Núcleo y se calcula en
Default; cambios ajenos no vuelven a preparar su vista de tareas si el resultado
no cambia. Se deduplican entidades antes de construir los snapshots de Núcleo,
globales y borradores: una invalidación Room sin cambios relevantes no reconstruye
árboles ni sus proyecciones. Las consultas Room siguen siendo reactivas.
Nodos globales, contadores,
directorio de indicadores y datos Metro con consumidores legítimos se
conservan. Solo las proyecciones visuales de Metro se suspenden en Diagramas,
Wireframe, Apariencia y Acerca de cuando no hay un selector/búsqueda superpuesto;
se recomputan con datos actuales al regresar. No cambian temporizadores,
servicios ni seguimiento, ni se condicionan las pantallas de juegos/Pomodoro.

### R04

Una consulta Room reactiva une nodos y eventos, filtra proyecto/COMPLETED y devuelve
`MAX(occurredAt)` por nodo. No materializa las columnas de nodos ni el historial
completo. Conserva el último completado histórico al reabrir, empates, movimiento
entre Núcleos y eliminación. No añade índices ni migraciones. Las emisiones con
el mismo mapa se deduplican; el filtrado manual ya no se recalcula por cambios de
una fecha que no utiliza.

### R05

Las observaciones de iteraciones y relaciones solo existen en la composición si
hay un consumidor de resumen Sprint. El contexto actual retirado no las activa.
No se borran datos ni se restauran interfaces históricas.

### R06

Durante la carga del Núcleo se muestran nombre, volver y progreso compacto.
Las operaciones de preparación/navegación usan el progreso existente. Los destinos
asíncronos muestran carga sin anunciar resultados vacíos definitivos. Se conservan
los controles de envío único y se protege la entrada repetida en una tarjeta.
Los menús que ya abrían directamente no añaden preparación ni animación.

## Validación

**23 pruebas distintas aprobadas**, sin suite general. Resultados por intervención:

| Hallazgo | Evidencia focalizada |
| --- | --- |
| R01 | Cuatro casos de preferencias: preparación sin lecturas SQL repetidas, persistencia/recarga, lectura durante commit/recarga lentos, fallo y revocación sin publicación tardía. Desbloqueo primario/secundario, fallo/cancelación, apariencia histórica y recreación de Activity correctos. Plegado durable impide cambios de modo concurrentes. |
| R03 | Un solo origen del índice con varios consumidores; sin suscripción oculto, actualización y reapertura fresca. AppRoot real no consulta borradores antes de buscar y refleja títulos nuevos al reabrir. Cambios en otro Núcleo no emiten otro snapshot del Núcleo visible; el progreso global sigue actualizándose. |
| R04 | Equivalencia con el algoritmo anterior sobre un historial sintético de 1200 eventos, empates y reapertura; reacción al volver a completar, trasladar y eliminar. Una consulta agregada devuelve dos filas. EXPLAIN usa `index_nodes_projectId_id` e `index_node_events_nodeId_occurredAt`; GROUP BY usa un temporal. Sin índices nuevos. |
| R05 | La apertura de Núcleo no consulta las cuatro tablas de iteraciones con contexto inactivo; la pantalla con asignaciones compartidas omite el JOIN local de responsables. |
| R06 | Núcleo con consulta detenida muestra título/progreso; creación lenta muestra progreso, deshabilita el segundo envío y prepara un solo formulario. Nuevo/plantilla conservan destinos de Núcleo y Capa. Carga de destinos se puede cerrar. |

R10 se conserva: pasa su caso de validación de cachés de imágenes/sonido y las
pruebas nuevas rechazan publicaciones de preferencias/apariencia después de la
revocación. No se vuelve a ejecutar su batería completa de 19 casos ni se afirma
haberla repetido. Los demás mecanismos de caché R10 no se modifican.

Registros locales: `/tmp/group2-round1.log` (tres casos terminados),
`/tmp/group2-round3.log` (18 casos aprobados y una prueba con etiqueta antigua),
`/tmp/group2-final-check.log` (dos casos finales aprobados), XML en
`/tmp/group2-results/` y lista única en `/tmp/group2-results/passed.txt`.
Se adaptan exclusivamente el Looper de la prueba de desbloqueo y la etiqueta
retirada Personalización → Ajustes en la regresión de apariencia.

La selección final terminó con **BUILD SUCCESSFUL**. La compilación de integración
del último ajuste, que mantiene el uso de borradores existentes, también terminó
con **BUILD SUCCESSFUL** (`:app:compileDebugKotlin`), registrada en
`/tmp/group2-integration.log`. No se genera APK, no se cambian versiones ni se
realizan commits. Comprobación de espacios focalizada correcta.

Beneficio estructural comprobado: menos observaciones y materialización de filas,
menos emisiones ajenas al Núcleo y getters que no esperan al disco. No hay medidas
físicas de pulsación/fotogramas/decoración ni evidencia para atribuir los tres
segundos a una causa única.

## Comprobación física preparada

Sin modificar datos personales, usar un almacén de prueba y un Núcleo de dos
tarjetas. Repetir en un almacén pequeño y otro grande, en frío y después de
visitar el mismo destino. Anotar por separadopulsación → respuesta visual,
pulsación → contenido utilizable y contenido utilizable → decoración terminada.

1. Desbloquear; comprobar apariencia guardada y primer contenido utilizable.
2. Abrir Núcleo, Capa y tarjeta; repetir pulsaciones rápidas sin duplicar ruta.
3. Abrir Crear, Nuevo y Desde plantilla; probar volver durante preparación.
4. Abrir búsqueda, cerrarla, cambiar/crear/mover contenido y volver a buscar.
5. Cambiar estado y responsables; verificar indicadores en Núcleo y Capa.
6. Guardar apariencia, calendario y bloqueo automático; reabrir y bloquear.
7. Comparar los tres intervalos con video/Perfetto y las trazas debug existentes.

`Arachn0deResponse` distingue preparación de preferencias, entrada de composición,
contenido preparado y preparación de creación. Estos hitos no prueban por sí
solos el primer fotograma ni que todas las imágenes hayan terminado de cargar.
No registran títulos, IDs ni contenido privado.

Posteriormente: medir en el Pixel antes de atribuir mejoras a una causa; estudiar
el coste global restante con esos datos. Los temporizadores y seguimiento quedan
para Grupo 3. No se implementan otras optimizaciones aquí.

## Archivos de producción

- `app/src/main/java/com/r0ybt/arachn0de/data/local/NodeEventEntity.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/repository/NodeRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/security/EncryptedPreferences.kt`
- `app/src/main/java/com/r0ybt/arachn0de/security/SecurityUi.kt`
- `app/src/main/java/com/r0ybt/arachn0de/security/VaultContext.kt`
- `app/src/main/java/com/r0ybt/arachn0de/security/VaultManager.kt`
- `app/src/main/java/com/r0ybt/arachn0de/security/VaultSession.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/AppRoot.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/AppearanceScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/CalendarSpecialUi.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ContextCalendar.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ContextualSearchPanel.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/DestinationExplorer.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ElementAccessUi.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/NavigationIndex.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/OfficialIcons.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/OperationProgress.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ProjectScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/SharedCaptureHost.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/theme/Appearance.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/theme/Theme.kt`

Pruebas nuevas: `CompletionTimesQueryTest`, `PreferenceResponsivenessTest`,
`NavigationIndexTest` y `NavigationWorkTest`.
`InitialUnlockDataTest` adapta su arnés para atender el Looper de Robolectric
mientras se ejecuta el desbloqueo, igual que las pruebas de bloqueo de R10.

Pruebas existentes ajustadas: `InitialUnlockDataTest.kt` (Looper) y
`AppearanceRestartUiTest.kt` (etiqueta vigente y espera del cambio confirmado).
Este documento es el único archivo de documentación añadido en Grupo 2.
