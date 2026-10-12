# Avisos de horario habitual del Metro

Referencia local consultada el **10 de octubre de 2026**:

- [Red Movilidad: horario general de Metro](https://www.red.cl/mapas-y-horarios/metro/?width=wide): lunes a viernes 06:00–23:00; sábados 06:30–23:00; domingos y festivos 07:30–23:00.
- [Gobierno de Chile: calendario nacional de festivos 2026](https://www.gob.cl/noticias/feriados-2026-revisa-cuantos-habra-y-cuales-son-irrenunciables/). Solo se incluyen las fechas nacionales de 2026, no los festivos regionales ajenos a Santiago. Para otros años, un inicio entre la apertura semanal y la apertura de festivo configurada muestra una advertencia condicional de festivo sin verificar.

La tabla está en `MetroOperatingHours`, separada del horario histórico de servicio expreso y del algoritmo de rutas. No necesita conexión ni permisos de red, ni se guarda en los datos de los viajes.

Se calcula la fecha y hora en `America/Santiago`, incluyendo su cambio estacional. Antes de la apertura o después de las 23:00 se avisa de inicio fuera del horario habitual. Las 23:00 exactas no se declaran fuera de horario: Red indica que aún se puede ingresar a esa hora.

El margen preventivo elegido por la aplicación es de **30 minutos antes del cierre**; no es una regla oficial ni garantía de último tren. Si la duración estimada existente supera el tiempo restante hasta el cierre configurado, se muestra además que el viaje podría finalizar después del cierre. Una llegada exactamente al cierre no se considera posterior. Fuera de horario se muestra solo ese aviso, evitando avisos redundantes.

Solo se presenta un bloque compacto en el resumen de planificación y en el detalle previo al inicio. Para una simulación con fecha se usa su inicio previsto; para un inicio real, la hora actual, refrescada cada 30 segundos y al regresar a la pantalla. No aparece en tarjetas, historial ni controles de seguimiento. Nunca interviene en la habilitación de acciones, estimaciones, restricciones locales o rutas de regreso.

No hay información en tiempo real, excepciones, extensiones especiales ni cierre de estaciones concretas. La hora orientativa de cierre de la red no equivale a la hora del último tren o a la posibilidad de completar una combinación. La interfaz indica estas limitaciones cuando hay un aviso.

## Horarios editables

«Horarios del Metro», en la cabecera de Metro, abre los controles de apertura y cierre de cada grupo de días. «Guardar» aplica los valores a los avisos; cancelar conserva los anteriores. Restablecer carga los valores oficiales en el editor y Guardar confirma el restablecimiento. La interfaz distingue valores oficiales de referencia y personalizados. Solo se admiten intervalos del mismo día, con apertura anterior al cierre; no se modela operación nocturna a través de medianoche.

Se guarda una preferencia `hours` en el namespace privado `metro_hours`, con seis enteros (apertura/cierre de lunes a viernes, sábado y domingo/festivo), en minutos 0–1439. Usa el contexto capturado del almacén, la tabla existente `private_preferences` y su transporte de backup genérico. No cambia esquema ni formato. La restauración valida el intervalo antes de sustituir datos; los backups antiguos sin la preferencia usan los valores oficiales. Restaurar recarga la caché y notifica la interfaz. No comparte valores entre principal y señuelo. No modifica el planificador ni el servicio expreso histórico.
