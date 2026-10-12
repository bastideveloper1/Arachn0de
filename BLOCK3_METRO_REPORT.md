# Bloque 3: Metro y seguimiento

## Conservado tras inspección

Ya existían tarjetas compactas de Viajes con apertura del detalle, seguimiento detenido en combinación, etapas manuales, cronómetro, historial, pausa/deshacer, mapa ampliable y retorno, indicadores vinculados en ancestros y adaptación de colores de estaciones. Se mantuvieron esas funciones.

## Cambios

- Las tarjetas añaden el nombre del elemento vinculado cuando existe, conservando origen, destino, estado y resumen; no inventan nombres ni asociaciones.
- Confirmar «Llegué» inicia inmediatamente el cronómetro de combinación, manteniendo explícito «Comenzar siguiente línea». Estimaciones no ejecutan esa transición. La pausa y estados históricos conservan sus controles de recuperación.
- Navegación separada del contenido por divisor del tema. Centrar avatar queda en una fila separada del icono de ampliación.
- Vibración previa opcional en Inicio, desactivada por defecto: una estación antes de combinación/destino estimados. Permiso normal VIBRATE, políticas Android de notificación, sin GPS ni permisos de ubicación. Marca durable antes de emitir para impedir repetición después de reabrir; no confirma llegada ni cambia el seguimiento.

Archivos: metro/MetroScreen.kt, MetroStages.kt, MetroCodec.kt, MetroService.kt, nuevo MetroArrivalAlert.kt; AndroidManifest.xml; pruebas MetroBlock3Test.kt, MetroStagePersistenceTest.kt, MetroStageUiTest.kt y MetroSprint4BUiTest.kt; ARCHITECTURE.md y BACKUP_FORMAT.md.

Sin migración Room ni incremento de aplicación; ajustes Metro payload v2 con lector v1 y default de vibración desactivada. El backup exterior conserva formato y transporta el payload completo. Aplicaciones anteriores pueden rechazar ajustes nuevos. Guardados no copia sesiones, marcas, historial ni identidad exclusiva. Sin cambios de seguridad, contraseñas o juego, sin firma/commit/push.

## Precisión y límites

Gruta de Lourdes se comprobó con el catálogo real y el mismo límite temporal de segmentos que las demás estaciones, sin excepciones ni correcciones artificiales. La prueba no reproduce tiempos reales del tren: el sistema estima por tiempo y admite corrección manual. No conoce la ubicación física.

El servicio comprueba avisos cada 30 segundos; pueden retrasarse por políticas Android. Registrar antes de vibrar evita duplicados, pero una interrupción entre ambos pasos puede omitir ese aviso. Dispositivos sin vibrador, permisos/políticas de notificación o modos de silencio pueden impedirlo. Verificación háptica en teléfono pendiente. No se renueva certificación nativa SQLCipher ni evidencia de release.

Manual: viaje real con combinación, Llegué/deshacer, cronómetro, siguiente línea, pausa, cierre y reapertura; comparar estimación temporal y estación real, corregir cuando corresponda. Comprobar mapa ampliado/retorno, centrar avatar, fuentes grandes y temas, tarjetas vinculadas/ancestros, Guardados y vibración activada/desactivada con pantalla apagada.

## Resultados

55 casos distintos aprobados: MetroStageUiTest (4), MetroPrecisionTest (5), MetroStagePersistenceTest (6), MetroSprint4BUiTest (6), MetroStationContrastTest (1), MetroStageEngineTest (9), MetroBlock3Test (3), SavedTemplatesTest (9), MetroPersistenceTest (10) y MetroServiceTest (2). Una expectativa anterior de controles en la misma fila se actualizó a separación vertical explícita; las reejecuciones dirigidas pasaron. Compilación assembleDebug y git diff --check correctos. No se ejecutaron suites globales ni firma oficial.
