# Rendimiento — Grupo 3 (R07, R08, R13)

## Alcance y línea base

Inspección limitada a temporizadores, consumidores visuales, audio y sus mecanismos
existentes de segundo plano. Frecuencias obtenidas del código anterior, no de una
medición física de batería. Se conservan R10 y Grupo 2; no se repiten sus baterías
de 19 y 23 pruebas. No cambian versiones, formatos, SQLCipher, autenticación,
permisos, servicios, códecs ni dependencias. Sin APK ni commits.

| Mecanismo anterior | Activación y consumidor | Trabajo anterior | Intervención |
| --- | --- | --- | --- |
| Pomodoro raíz, 100 ms | Activity STARTED, incluso sin sesión | Leer reloj y grabación, comprobar transición y actualizar sonido | Observar cambios; esperar al vencimiento únicamente mientras corre |
| Pomodoro visual, 250 ms | Pantalla/barra habilitada, STARTED y RUNNING | Refrescar motor y contador visual | Solo representación; tampoco hay ticks en final manual |
| Metro raíz, 30 s | Composición raíz, también en segundo plano | Reloj y proyecciones jerárquicas | Suspender reloj/proyecciones sin consumidor visible y snapshots raíz fuera de STARTED |
| Metro Seguimiento, 1 s | RESUMED en seguimiento, aun sin viaje | Actualizar reloj y recomponer estimaciones | Solo con viaje activo sin pausa |
| Metro servicio, 30 s | Viaje activo y sesión accesible | Estimación, notificación y aviso durable de llegada | Se conserva |
| Audio reproductor, 500 ms | Mientras exista player, incluso pausado | Despertar; leer posición solo reproduciendo; guardar cada dos ticks activos | Esperar cambios cuando esté pausado/cargando; mantener 500 ms/1 s al reproducir |
| Audio grabador pausado, 50 ms | Captura pausada | Volver a comprobar flags | Espera de evento conflado de pausa/reanudación/finalización |

## R07 — Pomodoro

El observador foreground cancela y recalcula la espera cuando cambia el estado.
Sin sesión, en pausa, listo, finalizado o detenido en final manual no consulta
periódicamente el reloj. Espera la duración restante positiva, sin imponer un
mínimo que retrase los últimos milisegundos. Conserva el reintento de 100 ms si
el vencimiento no puede confirmarse. La pantalla/barra mantiene su cadencia
visual existente cuando corresponde, sin llamar repetidamente a `refresh()`.

La grabación, modo silencioso y No molestar actualizan el propietario de sonidos
mediante eventos. No cambian sonidos ni sus preferencias. Se mantienen las
alarmas de un solo disparo, caché recuperable y cronómetro de notificación del
sistema. El motor no depende de que permanezca abierta la pantalla Pomodoro.
Las alarmas existentes son **inexactas** (`setAndAllowWhileIdle`): no se atribuye
al cambio una garantía nueva de puntualidad física bajo Doze.

## R08 — Metro

El snapshot raíz se recoge mientras la Activity está STARTED y vuelve a leerse
al regresar. El reloj y las proyecciones jerárquicas dejan de trabajar en segundo
plano o en las superficies sin consumidor ya identificadas por Grupo 2.
Con viaje en marcha se mantiene una separación máxima de 30 s; sin viaje en
marcha basta el siguiente cambio de minuto. Este reloj también alimenta los
indicadores de fechas de tarjetas: por eso se conserva el tick de minuto cuando
hay tarjetas visibles. Se actualiza inmediatamente al volver a mostrarlas.

La pantalla de seguimiento deja de generar ticks de segundo si no hay viaje en
marcha; planificación/recorrido conservan su actualización de 30 s. La animación
del mapa ya estaba condicionada a visibilidad, fase y pausa, y no se modifica.
Los snapshots del repositorio ya eran reactivos a invalidaciones, no consultas
periódicas; no se presentan como consultas SQL eliminadas en cada tick.

El servicio mantiene su observación independiente, notificación, acciones,
registro durable del aviso y estimaciones de tiempo; los cambios visuales no
controlan el viaje real. No se modifican aprendizaje, Expreso, historial ni rutas.

**Riesgo previo:** `VaultManager.lock()` detiene Metro y revoca la sesión.
El bloqueo automático en segundo plano no exceptúa Metro. El servicio necesita
una sesión desbloqueada y se detiene si no existe; START_STICKY no permite por sí
solo recuperar las claves tras morir el proceso. Los checkpoints persisten,
pero no se garantiza continuidad del servicio después del bloqueo de la bóveda
o muerte del proceso. Resolverlo exigiría revisar seguridad/servicios y queda
fuera del alcance autorizado. No se ha probado muerte real del proceso.

## R13 — Audio

La posición visual se observa cada 500 ms únicamente durante reproducción. Pausa
y carga esperan emisiones de estado; cerrar cancela y espera el observador antes
del último guardado y de liberar el player. Se toma la posición actual al cerrar
o cambiar archivo. Pausa, seek, finalización y revocación conservan sus guardados.

Se mantiene el checkpoint activo de 1 s: ampliarlo aumentaría el intervalo de
posición recuperable perdida tras una terminación abrupta. El UPDATE añade una
condición sobre la posición actual de la fila, evitando escrituras e invalidaciones
por valores idénticos sin introducir una caché que pudiera quedar obsoleta tras
restaurar. No se promete recuperación exacta del último instante.

La captura pausada espera un canal conflado: reanudar o detener antes de empezar
a esperar no pierde la señal; la cancelación también libera la espera. Se
conservan drenaje, EOS, paquetes AAC, publicación cifrada, recuperación parcial,
límites y servicio/wake lock. No se altera el ciclo de vida de archivos.

## Validación

**20 pruebas distintas aprobadas:** seis de Pomodoro, seis de audio y ocho de
Metro (incluida la observación raíz). Compilación de producción correcta y última
ejecución con **BUILD SUCCESSFUL** mediante `:app:testDebugUnitTest`, offline.
Las repeticiones posteriores reutilizan producción y seleccionan exclusivamente
los casos cuyo arnés se corrigió; no se repiten las pruebas ya aprobadas.

- Pomodoro: cero lecturas periódicas del reloj sin sesión/en pausa/final manual;
  vencimiento sin ticks visuales y transición única con notificación; precisión
  elapsed/reinicio, ajuste, recuperación, controles de pantalla y barra compacta.
- Metro: controles de viaje y retorno de observación raíz al reanudar la Activity;
  una invalidación en segundo plano no produce consultas raíz de viajes.
  Servicio sin UI: notificación, pausa/reanudación, finalización, recreación y
  aviso único. Aprendizaje adaptado y dos regresiones del registro/tiempos Expreso.
  La prueba de ciclo de vida del observador y las del servicio son aisladas;
  no equivalen a probar todo un viaje real con la Activity suspendida.
- Audio: espera de pausa sin temporizador, señales anticipadas de reanudación/
  detención y cancelación; reproducción y checkpoint, pausa sin lecturas de
  posición, seek, cambio/cierre y bloqueo real de la sesión con liberación del
  player, estado vacío y rechazo de acceso posterior. La pausa se mantiene más
  que el intervalo anterior de 500 ms. Cinco solicitudes de posición (0, 64, 64,
  128, 128) producen exactamente **dos actualizaciones de fila**, contadas por un
  trigger de prueba. Bytes cifrados legibles al confirmar, fragmentos recuperados
  una sola vez, backup/restauración de audio y rechazo de corrupción.

Se adaptan los arneses: el doble de MediaPlayer tolera llamadas desde su
constructor y se avanza el reloj del Looper; el test de controles Pomodoro
controla el reloj de su halo animado; el test raíz evita consultar la Activity
desde composición. No se requiere corregir producción tras la primera selección.
El caso de posiciones idénticas se renombra por precisión, sin contarlo dos veces.

Registros: `/tmp/group3-validation.log` (17 aprobadas),
`/tmp/group3-recheck.log` (dos aprobadas adicionales),
`/tmp/group3-audio-check.log` (última aprobada). XML conservados por ejecución en
`/tmp/group3-results/`; lista única en `/tmp/group3-results/passed.txt`.
Comprobación focalizada de espacios correcta, incluyendo los archivos nuevos.

Los contadores automatizados son evidencia estructural de trabajo evitado; no
representan porcentajes de ahorro de batería. Room/Robolectric sustituyen partes
de Android y el backend nativo de SQLCipher; no prueban audio físico ni Doze.

## Comprobación pendiente en Pixel 7a

1. Pomodoro: inicio, pausa/reanudación y transición con pantalla/barra visibles;
   luego otra pantalla, otra app y teléfono bloqueado. Revisar notificaciones,
   sonido, silencioso/No molestar y exclusión durante grabación.
2. Metro: viaje activo, pausa, cambio de pantalla/app, regreso y teléfono
   bloqueado. Distinguir bloqueo de pantalla y bloqueo de bóveda; anotar ajustes
   de bloqueo automático. Verificar avisos, paradas, aprendizaje y Expreso.
   Muerte/reinicio real del proceso requiere comprobación separada.
3. Audio: reproducir/pausar/reanudar, seek, terminar, cambiar archivo y bloquear
   bóveda; grabar/pausar/reanudar/finalizar y escuchar el resultado sin silencio
   añadido ni truncamiento. Comprobar interrupción real de micrófono/audio focus
   y recuperación de los últimos fragmentos en un almacén de prueba.

## Archivos de esta intervención

Producción (prefijo `app/src/main/java/com/r0ybt/arachn0de/`):
`ui/AppRoot.kt`, `ui/PomodoroUi.kt`, `pomodoro/PomodoroController.kt`,
`metro/MetroScreen.kt`, `audio/AudioController.kt`, `audio/AudioCapture.kt` y
`data/local/AttachmentDao.kt`.

Pruebas nuevas (prefijo `app/src/test/java/com/r0ybt/arachn0de/`):
`pomodoro/PomodoroIdleTest.kt`, `audio/AudioCaptureWaitTest.kt`,
`audio/AudioIdleTest.kt` y `ui/MetroObserverLifecycleTest.kt`.
Se adapta únicamente el reloj del caso de controles existente en
`pomodoro/PomodoroUiTest.kt`. Documentación: este archivo. Las demás regresiones
existentes seleccionadas se ejecutan sin modificar sus fuentes.
