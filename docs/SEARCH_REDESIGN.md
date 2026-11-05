# Buscador — panel modular y resultados contextuales

## Implementación

El buscador existente conserva el índice de ElementSearch, sus IDs, rutas,
normalización de mayúsculas/acentos y búsqueda por título o descripción. No se
crean entidades, preferencias persistentes, métricas de acceso ni permisos.

ElementSearchScreen se separa del archivo que también contiene Favoritos y
Captura rápida, sin reconstruir esas funcionalidades. Reutiliza
ModularPanelSurface, ModularPanelLayout, ModularPanelActions,
PrimaryControlButton, los controles Material existentes y BashGeneralScope.
La cabecera discreta tiene un control de 48 dp para plegar. Al minimizar queda
una barra compacta con control de expansión. Los criterios siguen en el estado
de sesión del padre y el estado plegado se restaura con rememberSaveable.

El panel se ajusta al contenido con un límite proporcional al espacio realmente
disponible, áreas seguras e IME; su cuerpo desplaza y sus acciones permanecen
fuera del desplazamiento. No cambia el sistema global de menús.

Secciones: Búsqueda, Ordenar por, Filtrar por y Etiqueta. El texto puede estar
vacío y tiene una acción para borrarlo. Ordenar usa un selector exclusivo.
Los cuatro filtros usan chips seleccionables con marca visible, colores del
tema y objetivos de 48 dp. Etiqueta reutiliza el selector desplegable y su
opción Todas; no incrusta una lista larga en el panel.

Cambios provisionales: texto con debounce de 180 ms; filtros y ordenación sin
espera artificial. Aplicar confirma inmediatamente el texto visible, oculta el
teclado, retira el foco y pliega. Restablecer limpia texto, cuatro filtros,
etiqueta y orden, y actualiza los resultados sin plegar automáticamente.

Mientras el panel está expandido los resultados pueden desplazarse, pero sus
acciones de apertura, favoritos y accesos contextuales están deshabilitadas.
También se impide activar una lista provisional mientras el cálculo no
corresponde a los criterios actuales. El indicador de actualización se dibuja
sin cambiar la altura de la lista. Plegar/expandir no borra criterios.

## Ordenación y filtros

- Todas: orden general del índice existente.
- Recientes: primero las claves en AccessState.recent, en su orden de visitas;
  después los demás resultados en el orden general. No usa fecha de creación
  ni modifica recibos de visita.
- Frecuentes: contadores de AccessState.counts descendentes; los empates y
  elementos sin contador disponible conservan el orden general.

Las dos últimas opciones antes excluían elementos sin historial. Ahora ordenan
el conjunto completo que coincide con los criterios. Se conservan los límites
y el historial existentes (20 recientes y hasta 200 contadores); no se inventan
métricas para elementos ausentes de esos registros.

Favoritos, Atención, Pagos, Recurrencias y Etiqueta siguen intersectándose.
Atención conserva AttentionPolicy y las hojas reales de la jerarquía. Pagos
conserva su significado de tareas ACTION con dato financiero y proyecto;
no se convierte silenciosamente en «solo impagados». Recurrencias conserva
las tareas materializadas con recibo de recurrencia: no crea resultados
virtuales para plantillas, ni materializa tareas al buscar.

## Contexto de resultados

Se conservan título, tipo/icono, ruta, favorito y apertura del elemento original.
Las filas tienen altura adaptable y límites de líneas, sin tarjetas nuevas.

Los responsables proceden de la proyección global existente de asignaciones de
la tarea; no se sustituyen por titulares de cuentas ni se infieren de plantillas.
Los abonos proceden de LocalBankPaid, que ya agrega únicamente operaciones no
anuladas. Cada tarea aparece una vez; varias personas no multiplican el importe.
Se prioriza el saldo pendiente ante un abono parcial, también cuando una tarea
se completó manualmente. Una tarea completada no se declara pagada sin evidencia
bancaria. Las fechas inexistentes y personas no asignadas se omiten.

La recurrencia muestra la próxima fecha futura con RecurrenceSchedule,
respetando ancla, intervalo, cursor, límite final y zona de la regla. Un
vencimiento histórico de la tarea se etiqueta Vence y no se usa como Próxima.
Reglas pausadas/finalizadas/eliminadas no reciben fechas activas inventadas.
Se muestra un motivo discreto de Atención usando la misma prioridad de su
política, sin cambiar sus reglas.

Los accesos de Atención, Pagos/informes y programaciones quedan junto a los
resultados, fuera del formulario, cuando corresponden al filtro o al conjunto
visible, incluso sin coincidencias cuando el filtro correspondiente sigue activo. Conservan las callbacks y rutas existentes. Los estados vacíos
distinguen No hay elementos y Sin coincidencias, con recuperación de criterios.

## Rendimiento e integridad

La normalización se prepara al cambiar el índice; no se repite por cada tecla.
La búsqueda, ordenación, criterios y metadatos se calculan en Dispatchers.Default.
La próxima fecha se calcula una vez por regla, no por cada ocurrencia histórica.
No hay consultas de responsables o movimientos por tarjeta: se reutilizan las
proyecciones globales de Personas/Banco. Un observador transaccional de reglas y
recibos vive solamente mientras se muestra el buscador. Es una lectura.

No se modifican SQLCipher, claves, esquema, Room, migraciones, backups,
versionName/versionCode, permisos, movimientos, saldos, cursores o historiales.
La navegación deliberada y Favoritos siguen usando sus operaciones habituales.

## Archivos

Producción, relativos a app/src/main/java/com/r0ybt/arachn0de/:

- access/SearchCriteria.kt: ordenaciones completas y búsqueda preparada.
- access/SearchContext.kt (nuevo): proyecciones y observación de solo lectura.
- ui/ElementSearchScreen.kt (nuevo): panel y estado del buscador extraídos.
- ui/SearchResultRow.kt (nuevo): contexto derivado y filas de resultados.
- ui/ElementAccessUi.kt: conserva Favoritos/Captura; permite deshabilitar
  Favoritos únicamente en este contexto, con el comportamiento anterior por defecto.
- ui/AppRoot.kt: orden inicial general y asignaciones existentes al buscador.

Pruebas: access/UnifiedSearchTest.kt, access/SearchContextTest.kt,
access/SearchReadIntegrityTest.kt, ui/UnifiedSearchUiTest.kt y un método
relacionado de access/ElementAccessUiTest.kt.

## Validación

Compilación principal y de pruebas correctas. 17 pruebas focalizadas distintas
aprobadas entre las tandas, sin suite general:

- UnifiedSearchTest: 4; texto con/sin acentos, títulos/descripciones, notas y
  borradores, filtros individuales/combinados, etiquetas, orden estable y
  equivalencia del índice preparado en 192 combinaciones adicionales.
- SearchContextTest: 5; parcial pendiente, ausencia de responsable/fechas,
  varios responsables sin multiplicación del importe, completado manual frente
  a pago real, recurrencia mensual vencida, próxima fecha futura, pausas/finales/
  eliminaciones y elegibilidad estructural de Atención/Pagos.
- SearchReadIntegrityTest: 1; abono real y recurrencia existentes consultados
  sin cambios posteriores en operaciones, saldos, asignaciones, tareas, reglas,
  recibos ni preferencias/historiales.
- UnifiedSearchUiTest: 6; plegar/expandir, Aplicar/Restablecer, chips y segmentos,
  texto/borrado, etiquetas, restauración de estado de sesión, estados vacíos,
  contexto de pagos, ruta de programaciones aun sin coincidencias, bloqueo de
  activaciones accidentales y alcance táctil.
- ElementAccessUiTest: 1 método relacionado; apertura del elemento anidado
  original, regreso a la consulta conservada y favorito independiente que no
  navega al elemento.

La matriz visual automatizada usa Charcoal, Dark y Green Bash, ancho 280 dp,
fuentes al 170 % y un viewport reducido hasta 300 dp. Se comprueban acciones
visibles/táctiles y desplazamiento interior, foco del campo y petición de
ocultar el teclado al aplicar. El viewport reducido y el controlador de teclado
de prueba no equivalen a un teclado Android físico.

Registros: /tmp/arachnode-search-compile.log,
/tmp/arachnode-search-tests-v2.log (10 pruebas de lógica/integridad y las primeras
5 de UI aprobadas) y /tmp/arachnode-search-ui-final.log (7 pruebas de interfaz/
navegación, resultado BUILD SUCCESSFUL). Las repeticiones se limitaron a errores
de preparación de pruebas y al ajuste posterior del contexto vacío.

Pendiente en teléfono: valoración visual final con títulos/personas reales,
IME y navegación gestual reales, orientación y tamaños de pantalla adicionales.
No se declara aprobación visual física. No se generó APK ni se cambió la versión;
no hay commit ni push.
