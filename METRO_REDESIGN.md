# Arachn0de — Rediseño Metro

Implementación sobre el sprint anterior, conservando Room 25, respaldo 17, applicationId y versión 0.3.0 / 9. La referencia gráfica no estaba disponible; el usuario autorizó definitivamente continuar con la especificación textual y los componentes/tema existentes.

## Pantallas y componentes

- Inicio prioriza planificación, Casa, favoritos, consulta y viajes guardados/recientes. Horarios y restricciones quedan como funciones secundarias.
- Navegación única Inicio/Explorar/Planificar/Viajes, con selección visible y controles accesibles.
- Explorar ofrece las siete líneas, terminales y cantidades reales, dirección reversible, pantalla completa, combinaciones y señales de restricciones. El contexto permite origen/destino/parada/Casa/favoritos y corrección opcional durante seguimiento.
- Planificar ofrece búsqueda compartida, inversión, Casa/favoritos, vías ordenables, tren normal/expreso, Persona viajera, Buscar ruta, resumen, guardar/crear tarea e Iniciar viaje. El itinerario destaca combinaciones y cambios de línea.
- Viajes conserva planes independientes, tareas asociadas, activación/desactivación, edición, sesiones y estadísticas. El borrado requiere confirmación y se mantiene bloqueado durante seguimiento activo.
- Seguimiento destaca tramo estimado, última/próxima estación, dirección/tren, estaciones físicas pendientes hasta combinación/destino y tiempo aproximado. Los controles de pausa/reanudación/finalización se mantienen disponibles; opciones secundarias son desplegables.
- Selector con búsqueda sin tildes, filtro de línea, filas legibles, combinaciones y Casa/favoritos.
- Administración explícita de restricciones, con confirmaciones; tocar una estación y gestionar su tramo son áreas táctiles distintas.
- Creación de Viaje Metro en NodeDialog normal, heredando Proyecto/Capa/Subcapa. Mantiene propiedades normales; Guardados permite editar su plan sin copiar sesiones/historial. La edición posterior de itinerario abre Metro desde el formulario normal.

## Geometría y motor

`MetroVerticalTimeline` dibuja segmentos hasta los bordes reales de cada fila sin espacio entre items. La altura depende del contenido Compose; la separación de tarjetas está dentro de la fila y no corta la vía. Nodos alineados, círculos de estaciones comunes con rojo y verde y etiquetas completas Roja / Verde. El avatar interpolado utiliza los offsets y tamaños medidos del LazyList; el recorrido completado cambia de intensidad. No se fija la altura del tramo para simular avance. Desplazamiento manual suspende el seguimiento visual; Centrar avatar lo recupera.

`MetroPresentation.direction` calcula el terminal con el orden real de la línea y el siguiente segmento. Las transferencias muestran el terminal y servicio del siguiente tren; no reutilizan la dirección de la línea de salida. El conteo de visitas a estaciones físicas es tramos físicos + estación inicial; las transferencias no se cuentan dos veces y se distinguen las estaciones con detención expresa. Una estación visitada nuevamente cuenta como otra visita.

`MetroTracking` conserva relojes/anclas persistentes, pausa, correcciones, historial, replanificación y FGS/notificaciones offline. Sesiones nuevas automáticas, sin confirmación por estación/combinación; las permanencias intermedias no tienen duración estimada y el usuario puede pausar. Llegada y finalización siguen siendo explícitas. Sesiones v1 conservan su estado, con activación opcional del avance automático; reinicio/restauración/salto de reloj sigue requiriendo corrección de ubicación. Payload interno v2, preferencias v1, sin migración Room ni archivos nuevos.

`MetroSearch` usa NFD, eliminación de marcas, lowercase ROOT y espacios normalizados. Solo cambia claves de búsqueda, conserva nombres e IDs. Ranking exacto, prefijo, parcial y coincidencias por palabras. Todos los selectores, incluido Guardados, lo reutilizan.

## Validación

- **124 casos distintos dirigidos aprobados** entre las tandas de Metro, Guardados, Personas, Recurrencias, Tecnologías y restauración de borradores; no se ejecutó suite global. Los casos parametrizados de migración incluyen API 24/28.
- Metro: catálogo maestro, 143 accesos/126 estaciones, ambos sentidos, expresos auditados, vías, transferencias, búsqueda, conteos, avance automático sin ticks de UI, correcciones/pausa/reanudación, opt-in histórico, persistencia, transacciones, backup íntegro y rechazo de corrupción, servicio/notificaciones y restauración.
- UI: navegación y contexto, normalización y filtro, restricciones con confirmación, creación dentro de Capa, aplicación/edición de plantilla, formulario de edición posterior, avatar original verificando sus píxeles y corrección entre estaciones con la pausa conservada.
- Continuidad comprobada sobre píxeles de la vía atravesando los límites de filas de altura variable; centros/interpolación verificados geométricamente, 320 dp y escala de texto 1.6, con desplazamiento real. Inspección visual de ventanas renderizadas sobre Canvas nativo; PixelCopy/captureToImage no recibía fotogramas en este entorno.
- Capturas conservadas en [artifacts/metro-redesign](artifacts/metro-redesign): Inicio, Explorar, clasificación, Planificar, Viajes, Seguimiento, selector, estación, restricciones, tarea/plantilla/edición y avatar entre estaciones. Son fixtures de Robolectric, no capturas de celular ni avatares del usuario.
- `assembleDebug`: **BUILD SUCCESSFUL**. APK local `app/build/outputs/apk/debug/app-debug.apk`, 103.483.097 bytes. Las cinco imágenes del juego fueron verificadas byte por byte dentro de la APK debug, incluidos los bytes originales de `araña.png`.
- `git diff --check`: correcto. ApplicationId `com.r0ybt.arachn0de`, versión **0.3.0 / 9**, firma/protocolo oficiales sin cambios.

No se ejecutó firma, release, instalación, commit ni push. Las pruebas finales se registran en `/tmp/metro-redesign-{final,validation,close,ui-close,last,history}.log`; la última comprobación histórica repite únicamente las cinco pruebas del helper de presentación.

## Límites

Sin imagen de referencia no puede verificarse fidelidad a esa maqueta. Las capturas y pruebas Compose son de Robolectric API 28, pantalla de 320 dp; no acreditan pruebas físicas ni políticas de ahorro de batería de fabricantes. Se comprueba reconstrucción temporal sin ticks de UI, servicio/notificaciones, reapertura, pausa/correcciones y restauración. No se promete seguimiento tras force-stop/reinicio: conserva su recuperación incierta. Catálogo/horarios son offline y no confirman la operación real ni festivos; el servicio expreso se elige manualmente. El permiso INTERNET preexistente del actualizador no se modifica; Metro no requiere conexión/GPS ni añade permisos.
