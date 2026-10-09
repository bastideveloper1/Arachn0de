# Arachn0de — Dogfooding Sprint 3

Versión conservada: **0.3.0 / versionCode 10**. Se conservan íntegramente los Sprints 1 y 2. No se genera Release, firma, instala en dispositivos, hace commit/push ni elimina datos o recursos.

## Cambios implementados

Las tarjetas con un viaje vinculado muestran una vista previa compacta «Ver recorrido · origen → paradas → destino». Pulsarla consulta el viaje existente, sin abrir el editor, guardar, crear viajes ni iniciar seguimiento. El mismo componente aparece en el detalle del elemento y en los resultados filtrados normales. No se altera el resto de acciones de la tarjeta.

Se corrigió la solicitud de navegación: antes, el token podía seleccionar el viaje activo global y sustituir al asociado a la tarjeta. Ahora se resuelve la asociación por `nodeId` en el repositorio de la sesión actual. Una planificación pendiente no muestra ni permite corregir otro viaje activo. Las notificaciones sin `nodeId` mantienen el acceso al seguimiento global existente.

La consulta se integra en `MetroScreen`, sin crear otra Activity, repositorio, motor de rutas o sistema de seguimiento:

- **Pendiente:** plan guardado, nombres de estaciones, dirección, líneas, paradas y combinaciones, con acción explícita Iniciar seguimiento. Abrir no inicia nada.
- **Activo/pausado:** abre la pantalla de seguimiento existente con la misma sesión, tiempos, correcciones, pausa y estimador. Volver a entrar no reinicia ni duplica.
- **Finalizado:** muestra el último recorrido realizado, estado final, duración, pausas y última posición estimada registrada. Reiniciar requiere pulsar explícitamente Iniciar nuevo seguimiento.
- **Edición:** se conserva Editar en la tarjeta, con el formulario habitual que incluye el viaje. La consulta ofrece Editar viaje para abrir el planificador existente; en seguimiento está en Opciones de seguimiento. Consultar y editar son acciones diferentes, sin duplicar el menú de edición de tarjetas.

«Volver» y Atrás del sistema regresan al contexto guardado del Proyecto/Capa/Subcapa. La ampliación de la pantalla sigue cerrándose primero. La navegación usa el `SaveableStateHolder` y la ruta de elementos existentes, no reconstruye el árbol ni modifica posiciones.

## Componentes reutilizados

`MetroScreen`, `RouteSummary`, `RouteTimeline`, `MetroVerticalTimeline`, `TrackingMap`, `StationPicker`, planificador y correcciones existentes; `MetroNavigation`, `AppRoot`, `ProjectScreen` y su estado guardado. Nuevo `MetroRoutePreview` es únicamente el botón compartido de consulta. El estilo oscuro, tarjetas de estaciones y recorrido vertical se mantienen.

## Relaciones soportadas y límites

Las nuevas vinculaciones y el inicio de viajes asociados requieren una **tarea ACTION sin hijos**, conforme al modelo existente. No hay relación de viaje con un Proyecto; no se añade una asociación incompatible.

Una tarea convertida en Nota o Capa puede conservar un vínculo anterior. Esos viajes siguen siendo consultables: la vista previa no filtra por tipo y la consulta mantiene sus datos. El inicio se deshabilita cuando los metadatos del elemento indican que ya no cumple las condiciones; el repositorio conserva su validación definitiva. La planificación/guardado de nuevas asociaciones sigue sujeta a las restricciones existentes. La gestión desde Metro permanece disponible.

## Persistencia y seguridad

No se añade persistencia ni se cambia el esquema: **Room 27**, backup **JSON 17/18/19** según contenido y contenedor **v2**. No se modifica SQLCipher, claves, contraseña principal/señuelo, medios, backups, migraciones o identidades de almacenes. La consulta lee los mismos planes y sesiones persistidos; las escrituras de seguimiento y edición siguen utilizando la lógica y revisión transaccional existentes.

La observación y navegación usan el repositorio ligado al almacén autenticado. Bloquear la sesión conserva el descarte existente de solicitudes Metro pendientes. La prueba de aislamiento crea el mismo ID de nodo en ambos almacenes para verificar que sus viajes no se confunden, que el repositorio cerrado no puede leerse y que al volver al principal conserva sus datos.

## Archivos modificados en este sprint

- `app/src/main/java/com/r0ybt/arachn0de/metro/MetroScreen.kt`
- Nuevo `app/src/main/java/com/r0ybt/arachn0de/metro/MetroRoutePreview.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/NodeComponents.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ProjectScreen.kt`
- Nuevo `app/src/test/java/com/r0ybt/arachn0de/metro/MetroCardConsultationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/security/VaultManagerTest.kt`
- Este informe. El árbol contiene cambios anteriores que se conservaron; no todos los archivos de `git status` pertenecen a este sprint.

## Pruebas y compilaciones

**55 pruebas JVM dirigidas distintas aprobadas**, sin suite global:

| Selección | Casos |
|---|---:|
| MetroCardConsultationTest | 8 |
| MetroUiTest | 3 |
| MetroTrackingTest | 9 |
| MetroPersistenceTest | 10 |
| MetroNativePersistenceTest | 4 |
| MetroPresentationTest | 5 |
| MetroRedesignUiTest | 4 |
| VaultManagerTest | 4 |
| AttentionUiTest | 3 |
| CompactProjectCardTest | 4 |
| NodeSortUiTest: Capas primero | 1 |

Los casos nuevos verifican entrada desde tarjeta, Subcapa profunda, consulta pendiente, seguimiento activo/pausado/finalizado, restauración del estado de pantalla, progreso y correcciones conservados, ausencia de duplicación/escrituras al consultar, recorrido con parada intermedia y transbordo, dirección y nombres visibles, regreso con Atrás/Volver, separación de edición y acceso de notas/Capas convertidas. Las pruebas existentes cubren reapertura persistida, seguimiento, presentación y regresiones de Sprints 1/2.

`assembleDebug`, `assembleDebugAndroidTest` y `git diff --check`: aprobados. No se repiten suites extensas ni se generan APK Release.

Las pruebas con nombres Native en esta selección son **Robolectric en el host**, no instrumentación SQLCipher sobre Android. `VaultManagerTest` usa autenticación, envolturas y aislamiento reales con Room de prueba en lugar del driver nativo. **No se ejecutaron nuevas pruebas instrumentadas ni se instala ningún dispositivo**, respetando la restricción del sprint. No se presenta esta ejecución como una nueva certificación nativa SQLCipher.

## Pendientes y validación física

No quedan cambios funcionales pendientes dentro del modelo soportado. Revisar en teléfono físico la legibilidad con fuentes grandes, dimensiones táctiles, desplazamiento por estaciones, Atrás desde distintos niveles y servicio/notificación durante pausa o reentrada. El seguimiento continúa siendo estimado y offline, con las correcciones y advertencias existentes; no se añade GPS ni precisión nueva. Una eventual validación nativa posterior requerirá autorización separada para instalar/ejecutar en Android. No se solicita ninguna contraseña real.
