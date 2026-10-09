# Metro de Santiago · Beta 1

Entrega para dogfooding del 9 de octubre de 2026. Se conserva Arachn0de **0.3.0 / versionCode 9**, applicationId `com.r0ybt.arachn0de` y la configuración de firma oficial. No se ejecutó release, commit, push, tag ni publicación.

## Funcionalidad disponible

- Acceso desde el menú de Arachn0de: **Metro de Santiago · Beta**. Acceso por tarea: menú contextual **Metro / modo Viaje**.
- Catálogo offline de L1, L2, L3, L4, L4A, L5 y L6: 126 estaciones físicas y 143 accesos por línea. Terminales, orden, conexiones y clasificaciones expresas R/V/C de L2, L4 y L5. No incluye líneas futuras.
- Explorador vertical con colores, selección de línea, inversión de dirección, Casa única, favoritos y pantalla completa. Tocar una estación ofrece origen, destino, parada, favorito, Casa, cierre, evitar y, durante seguimiento, **Estoy aquí**.
- Planificación Normal o Expresa seleccionada manualmente, con paradas reordenables. Dijkstra conserva dirección y servicio. Cada tramo físico cuesta 2 minutos, incluso sin detención; cada combinación o cambio de tren cuesta 4. Las instrucciones muestran línea, dirección, servicio y secuencia física completa. Permanencias no incluidas.
- Restricciones manuales persistentes: estación cerrada a pasajeros permite paso ferroviario; tramo interrumpido impide atravesar; evitar es preferencia y permite un recorrido que la atraviese si no existe una alternativa. Las propuestas conservan destinos pendientes, comparan rutas y requieren confirmación. No se reemplaza silenciosamente una sesión activa.
- Modo Viaje opcional asociado a la tarea existente, con vuelta a modo normal sin sustituir sus propiedades. Crear una tarea con varias paradas genera una sola tarea mediante `NodeRepository`. La tarea conserva estado, responsables, fechas, prioridad y demás datos; Llegué no la completa automáticamente.
- Una Persona viajera, preferentemente entre responsables existentes. Se reutiliza el avatar original y su encuadre; actualizarlo se refleja mediante el flujo existente de Personas. Sin avatar o sin Persona aparece un icono. No se duplican archivos.
- **Subí al tren** inicia una sesión durable; **METRO DETENIDO** pausa y **REANUDAR** excluye el tiempo detenido. Ambos controles están fijos fuera del mapa desplazable, también ampliado. Estoy aquí corrige hacia delante o atrás sin reiniciar ni cambiar la pausa. Entre estaciones es una aproximación del tramo, no una medición.
- Seguimiento temporal sin GPS ni red. Combinaciones y paradas intermedias esperan confirmación. Alcanzar el destino estimado no finaliza el viaje: **Llegué** guarda el resultado y detiene servicio/notificación.
- Foreground Service con notificación funcional de pausa, reanudación y apertura. Reapertura recupera la sesión de Room. Pantalla completa centra el avatar al abrir; el mapa lo sigue hasta que el usuario arrastra y ofrece Centrar avatar.
- Historial de pausas, correcciones, rutas anteriores y estadísticas de estimación original, duración real, pausa, diferencia y estaciones confirmadas. Sin aprendizaje automático ni cambios automáticos de parámetros.

## Archivos principales

| Área | Archivos |
| --- | --- |
| Catálogo y motor | `app/src/main/assets/metro/santiago-beta1.json`, `metro/MetroNetwork.kt` |
| Sesiones y formato | `metro/MetroTracking.kt`, `metro/MetroCodec.kt` |
| Persistencia | `metro/MetroRepository.kt`, `data/local/MetroEntities.kt`, `Arachn0deDatabase.kt` |
| Interfaz | `metro/MetroScreen.kt`, `ui/AppRoot.kt`, `NavigationChrome.kt`, `NodeComponents.kt`, pantallas de proyectos |
| Segundo plano | `metro/MetroService.kt` (clase MetroTrackingService), `MainActivity.kt`, `AndroidManifest.xml` |
| Backup | `backup/BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt` |
| Pruebas | `app/src/test/java/com/r0ybt/arachn0de/metro/` |

Los nombres de paquete abreviados de esta tabla están bajo `app/src/main/java/com/r0ybt/arachn0de/`.

## Migración y backup

Room **23→24** añade dos tablas y sus índices, sin borrar ni modificar tablas anteriores. Las referencias a tareas y Personas usan SET NULL al eliminar el original, conservando el viaje. Esquema exportado en `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/24.json`.

Datos de backup **v16**, contenedor v2 existente: catálogo completo, preferencias, restricciones, asociaciones, sesiones y todo el historial. Se admiten datos históricos v1–v15 sin Metro. Las restauraciones usan la transacción y protección de archivos existentes; un error conserva el estado previo. Suben las revisiones para invalidar acciones anteriores de notificaciones. Las sesiones importadas se marcan inciertas y necesitan confirmar posición. Sus duraciones utilizan reloj civil cuando el contador monotónico pertenece a otro dispositivo/restauración; la interfaz indica la posible incertidumbre.

No hay nuevos archivos privados ni multimedia Metro que liberar. Desactivar el modo Viaje conserva su historial; eliminar un viaje inactivo elimina sus datos Metro. Un seguimiento activo impide desactivar o eliminar el viaje.

## Validación realizada

- **27 ejecuciones Metro aprobadas**: seis de catálogo/rutas/integridad, seis temporales, ocho de persistencia/backup/referencias, dos de migración (Android 7 y 9), dos de servicio/notificación y tres de interfaz Compose.
- **136 ejecuciones de backup aprobadas**: formato actual y lectores históricos, validación y rollback. No se ejecutó la suite global de funcionalidades.
- **4 ejecuciones históricas de migración aprobadas**: importación del juego y conversiones proyecto/capa, preservando filas anteriores hasta Room 24.
- `:app:assembleDebug`: BUILD SUCCESSFUL. `git diff --check`: correcto.
- APK inspeccionada con `aapt`, `apksigner` y lectura ZIP UTF-8. Firma debug v2 válida, paquete/versión correctos, catálogo idéntico al asset fuente y las cinco imágenes del juego idénticas byte por byte: pantano.png, pastoseco.png, terrenocueva.png, paredcueva.png y araña.png.

## APK Beta

Ruta absoluta:

`/home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/Arachn0de-Metro-Beta1-debug-v0.3.0.apk`

Tamaño: **103.280.575 bytes (98,50 MiB)**.

SHA-256: `e2e7171a06252e83751cad0921a1f3cffaea8e5890c4b0033edd3c559bc7a431`.

También existe el archivo `.apk.sha256`. Original de Gradle: `app/build/outputs/apk/debug/app-debug.apk`.

La firma es **Android Debug**, no la oficial. Por ello esta Beta **no puede actualizar la instalación release**. No desinstalar ni borrar datos para superar esta diferencia: probar en un dispositivo/perfil separado o sobre una instalación debug compatible. La APK release anterior de `release-assets/Arachn0de-v0.3.0.apk` permanece separada y no contiene Metro.

## Fuentes y datos por revisar

Catálogo `santiago-2024-02-beta1`, con revisión documental 2026-10-09. Terminales, orden y conexiones se contrastaron con [Red: líneas y horarios](https://www.red.cl/mapas-y-horarios/metro/) y el [plano oficial de la red](https://www.red.cl/wp-content/uploads/2025/07/metrored_servicios_2023_07_19.pdf). Las clasificaciones se contrastaron con los tres planos oficiales publicados en [Metro: Ruta Expresa](https://www.metro.cl/el-viaje/ruta-expresa), incluida la extensión de L2 hasta Hospital El Pino.

Horario referencial expreso: días hábiles 06:00–09:00 y 18:00–21:00, con comienzo/término gradual. Punta tarifaria informativa: lunes a viernes 07:00–08:59 y 18:00–19:59, excluyendo festivos, según [tarjeta bip!](https://www.tarjetabip.cl/tarifas.php). No se incluyen precios ni pagos. Estos horarios no recalculan rutas.

No quedaron estaciones o imágenes sin incorporar. La información es un snapshot offline: no acredita cierres, operación excepcional o modificaciones posteriores de Metro. El parámetro de 2 minutos por tramo es inicial y debe evaluarse durante dogfooding; no es un horario oficial de llegada.

## Limitaciones y prueba física pendiente

Las pruebas JVM verifican persistencia, cálculos, acciones y estado Compose; **no acreditan políticas físicas de batería ni continuidad en un teléfono bloqueado**. Probar en el equipo real: bloqueo/pantalla apagada, cambio de aplicación, pausa desde notificación, cambios de orientación y tema, eliminación de proceso, reinicio, force-stop y restricciones del fabricante. También revisar permisos/notificación en Android 13 o superior y comportamiento del FGS en Android recientes.

El FGS usa `specialUse` para el temporizador iniciado por el usuario y declara su finalidad según la [documentación Android](https://developer.android.com/develop/background-work/services/fgs/service-types). No pide ubicación, no usa wake locks y actualiza la notificación cada 30 segundos. START_STICKY no garantiza ejecución tras force-stop o reinicio. El avance se reconstruye con anclas persistentes cuando se reabre; ante incertidumbre se congela hasta una confirmación manual. No se anuncia ubicación física detectada.

El árbol conserva los cambios previos del gestor y juego, más los de Metro. Hay archivos modificados y nuevos sin commit; los artefactos de `release-assets/` siguen ignorados por Git. No se enviaron archivos automáticamente.


## Revisión de base maestra r1

La revisión posterior corrige cuatro clasificaciones expresas de L5 y conserva los viajes, sesiones y preferencias históricos. La APK inicial y sus resultados anteriores permanecen descritos arriba; para dogfooding de la corrección usar `release-assets/Arachn0de-Metro-Beta1-r1-debug-v0.3.0.apk`. Comparación completa, 34 pruebas actuales, fuentes y compatibilidad en [METRO_MASTER_VALIDATION.md](METRO_MASTER_VALIDATION.md).
