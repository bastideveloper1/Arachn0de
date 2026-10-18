# Metro: interfaz e informe de aprendizaje — 18 de octubre de 2026

## Alcance y evidencia

Análisis limitado al flujo de aprendizaje solicitado. Se leyó el código local de
MetroRepository, MetroLearning, MetroPrediction, MetroCalculation, MetroNetwork y
MetroTracking. No se cambió su algoritmo, reglas, registros, modelo ni backups.
No se dispone de la base privada del teléfono ni de todos los eventos del viaje
Monte Tabor–Las Parcelas. La captura descrita no permite establecer su causa
individual con certeza. Los ejemplos numéricos del pedido no se usaron como datos.

## Interfaz implementada

Metro → Opciones (abajo) → Aprendizaje. Pantalla dedicada con Resumen, Actividad,
Tramos y Detalles abajo. El acceso oculto del cajón de Ajustes se retiró para no
duplicarlo. La antigua ruta de estadísticas sigue siendo compatible internamente.
Menú inferior y Casa ya estaban corregidos: no se repitió ese trabajo.

Resumen diferencia planes conservados, viajes conservados con evidencia válida,
sesiones con eventos, intervalos aceptados/descartados y contextos. Los conteos de
intervalos se deduplican por ID, usando los resultados actuales del estimador
existente. Un contexto incluye versión del catálogo, recorrido, línea, sentido,
servicio y tipo de paso; un recorrido acumulado no equivale a sus segmentos.
Se distingue evidencia suficiente de selección efectiva por el adaptador actual.
El universo corresponde a rutas/eventos registrados, no a toda la red de Santiago.

Actividad muestra las últimas 50 sesiones con eventos, fechas y marca Hoy, junto a
planes sin eventos asociados. Los eventos sobreviven a viajes eliminados: una sesión
con evidencia puede no tener ya un plan visible. No se inventa una cola pendiente.
“Aporta/contribuyó con mediciones válidas actuales” indica pertenencia a la evidencia
aceptada actual, no garantiza que una predicción histórica usara esa medición.
La ventana puede retirar evidencia previamente útil; “sin contribución válida actual”
no significa “nunca fue utilizada”.

Tramos contiene un resumen por contexto; Detalles conserva predicciones originales,
cálculos, eventos (últimos 100 en pantalla) y descartes. Los detalles de cada contexto
muestran los IDs originales de inicio/fin y los intervalos usados por el estimador.
No se reclasifican estimaciones intermedias como mediciones reales.

## De un viaje a una medición

1. Guardar un plan crea un viaje; comenzar activa seguimiento y un reloj estimado.
   Ninguna de esas acciones registra retrospectivamente una apertura de puertas.
2. Estoy aquí invoca confirmDoors(DOORS); Llegué invoca ARRIVAL. Se capturan estación,
   índice de ruta, línea, sentido, servicio, reloj monotónico, dominio, reinicio,
   pausas y el offset/ETA que se estaba mostrando. Correcciones tienen otra marca.
3. MetroLearning.observations recorre eventos ordenados por sesión. Un único evento
   no produce intervalos: se requieren dos confirmaciones consecutivas compatibles.
4. Se calcula duración real mediante diferencia de elapsedRealtime, NO mediante ETA,
   offset previsto ni la diferencia entre tiempo estándar y tiempo restante.
5. Se descartan pares deshechos, con pausa, corrección/confirmación inválida, estaciones
   sin detención, dominio restaurado, reinicio, reloj no creciente, retroceso/ruta
   diferente, o cambios de contexto incompatibles. Una combinación simple tiene una
   regla explícita para poder medirse. Las pausas/reanudaciones forman barreras.
6. estimate selecciona el contexto exacto, deduplica intervalos, toma los últimos 100
   no excluidos y filtra duraciones fuera de [base/5, base*5]. Aplica mediana/MAD:
   tolerancia max(base*0,25; 3*1,4826*MAD). Con menos de cinco muestras acotadas no
   aplica ese filtro de atípicos. El resultado usa el promedio de las aceptadas.
7. Con menos de cinco aceptadas el peso es cero. Desde cinco, peso=min(1,n/30).
   Tiempo adaptado=base*(1-peso)+promedio*peso. Cinco muestras habilitan un ajuste,
   no sustituyen completamente el estándar. Un promedio igual a la base puede
   mantener el mismo tiempo incluso con aprendizaje habilitado.

## Por qué puede aparecer 0 aceptadas / 0 descartadas

Es compatible con una única llegada registrada, sin confirmación previa. Sin par
no existe intervalo que aceptar ni descartar. También puede consultarse otro contexto
(servicio/sentido/catálogo/recorrido) o una fotografía histórica fijada antes de reunir
muestras. Un intervalo acumulado entre estaciones distantes no se cuenta como una
medición individual de Monte Tabor–Las Parcelas.

Los 45 s mostrados al pulsar son ETA capturado. No representan por sí solos una
medición real ni justifican convertir 2 min en 1 min 15 s. Para diagnosticar el caso
real hacen falta los eventos originales de esa sesión, su orden, exclusiones, relojes
y contexto. No se puede afirmar que los viajes anteriores aportaron solo porque
aparezcan guardados. Viajes anteriores sin eventos fiables no pueden reconstruirse
retrospectivamente sin inventar observaciones.

## Estado actual frente a historia

La predicción de cada sesión se fija con su evidencia del momento; incorporar nuevas
observaciones no reescribe esa historia. MetroEstimates.adapt devuelve sin cambios
una ruta que ya tiene prediction. Para consultar aprendizaje actual, la proyección
retira esa predicción y adapta una copia con las observaciones actuales.
La pantalla anterior mezclaba ambos resultados en tarjetas repetidas. Es un problema
de presentación confirmado y corregido en esta tarea; no demuestra fallo del estimador.

## NORMAL, RED (ROJO) y GREEN (VERDE)

MetroNetwork.stops permite todas las estaciones para NORMAL. Para RED/GREEN permite
su color y las comunes C. El planificador conserva pasos físicos entre estaciones
adyacentes, pero solo permite embarcar, terminar o combinar donde el servicio puede
parar. Por ello Verde → Roja → Roja → Verde atraviesa las dos rojas sin exigir abrir
puertas allí. La UI indica “sin detención”; una confirmación de puertas en una estación
omitida queda excluida con NO_STOP.

El estándar actual asigna 2 min a CADA segmento ferroviario adyacente y 4 min a cambios
no ferroviarios; WAYPOINT tiene cero. No descuenta permanencia ni aplica una velocidad
especial por saltarse estaciones. Esa simplificación confirmada limita el estándar
expreso: reconocer correctamente las detenciones no equivale a modelar el ahorro
real de tiempo expreso. No se corrigió porque modificaría el cálculo solicitado.

Los contextos separan NORMAL, RED y GREEN, línea, dirección, versión de catálogo y
secuencia exacta. No se mezcla automáticamente evidencia normal con expresa, ni se
comparte una medición de ida con la vuelta. Esa separación también puede dejar
contextos con pocas muestras aunque existan muchos viajes generales.

Las confirmaciones válidas entre dos estaciones donde SÍ abre puertas pueden medir
un recorrido acumulado de varios segmentos, incluyendo estaciones omitidas. El
adaptador prefiere el recorrido continuo más largo con evidencia suficiente. Reparte
su tiempo entre segmentos proporcionalmente al estándar; esos tiempos intermedios
son inferencias, NO mediciones directas. Sin cinco muestras fiables conserva la base.

## Hallazgos y próximos pasos propuestos, sin implementar

Confirmado: mezcla visual de historia/actualidad y repeticiones en la pantalla antigua;
base ferroviaria uniforme de 2 min incluso en expreso; inexistencia de intervalo a
partir de una sola confirmación; separación estricta de contextos. No se ha confirmado
un defecto que impida globalmente aprender: el código dispone de pares, filtrado y
adaptación para mediciones directas y acumuladas. Tampoco se ha probado una corrupción
o contaminación de los registros reales del teléfono.

Recomendaciones para una tarea posterior autorizada:
- Examinar los eventos de la sesión concreta antes de cambiar reglas; distinguir
  ausencia de pares, exclusiones y consultas de otro contexto o fecha histórica.
- Guiar al usuario para confirmar puertas solo donde el tren se detiene; sugerir
  una confirmación de partida y otra de llegada, sin fabricar la primera desde Inicio.
- Mostrar progreso de evidencia por patrón expreso acumulado y mantener la separación
  de servicios/sentidos; explicar la interpolación de segmentos no medidos.
- Evaluar un estándar expreso con marcha y detención separadas y validarlo con muestras
  reales. No añadir un descuento arbitrario ni reutilizar muestras normales sin prueba.
- Si se necesita demostrar qué medición influyó en cada predicción histórica, diseñar
  trazabilidad de IDs de evidencia y su backup: los resúmenes históricos actuales
  conservan conteos/medias, no una lista completa de IDs por predicción.

## Comprobaciones de esta intervención

Tres pruebas dirigidas de MetroStatisticsUiTest aprobadas: contraste de predicción
histórica y aprendizaje actual con evidencia que sobrevive al borrado del viaje;
una sola llegada sin inventar intervalos ni contribuciones; pantalla vacía y regreso.
Las pruebas comparan registros antes/después de consultar la interfaz. Compilación
Kotlin final y git diff --check correctos. Los archivos de algoritmo, repositorio,
predicción y red no tienen cambios en esta intervención. Sin suite general ni APK.
Pendiente validación de legibilidad en teléfono y diagnóstico con los eventos reales
del viaje descrito; las pruebas usan datos sintéticos, no la base del usuario.

## Actualización posterior: Ruta Expresa provisional v1

Esta sección corresponde al encargo posterior de cálculo expreso y sustituye la
limitación de base uniforme descrita arriba SOLO para rutas expresas nuevas. El
informe inicial sigue describiendo correctamente los registros históricos.

### Cálculo y supuestos

El catálogo disponible describe orden y clasificación de estaciones, pero no
contiene distancias ni velocidades ni permanencias medidas. Se conserva cada arista
física. El modelo provisional descompone la antigua referencia de 120 s en 105 s
por avance y 15 s de margen por detención compatible. Si el servicio no abre puertas
en el destino de esa arista, se conservan 105 s; no se elimina la arista ni se restan
120 s. La reducción máxima provisional es 12,5 % por arista sin detención, elegida
como margen conservador explícito y no como una permanencia o velocidad observada.
Paradas comunes C y del color del tren conservan 120 s. NORMAL, cambios y waypoints
conservan sus bases anteriores. El planificador compara los nuevos costes con
resolución de 15 s y conserva los límites originales de horizonte temporal.

El nuevo modelo lleva sus bases por arista en la ruta. Una secuencia Verde–Roja–Roja–
Verde retiene las tres aristas y una base provisional de 105+105+120=330 s. Es un
ejemplo de la fórmula, no una medición del servicio real ni de una distancia concreta.

### Aprendizaje y visualización

Las predicciones nuevas agrupan segmentos hasta la siguiente parada efectiva del
mismo servicio/línea/sentido. Un par de confirmaciones de puertas compatibles genera
un único intervalo real acumulado. No hay capturas sintéticas en estaciones omitidas.
Con cinco muestras fiables del mismo contexto se aplica la inercia existente:
base*(1-peso)+promedio*peso, peso=min(1,n/30). Se conservan ventana de 100, mediana/MAD,
plausibilidad, pausas, reinicios, dominio de reloj y deshacer. Un recorrido más largo
con evidencia suficiente puede seguir seleccionándose como intervalo acumulado.
El reparto proporcional para representar posiciones intermedias sigue siendo una
inferencia gráfica, no nuevas observaciones individuales.

Los contextos incluyen servicio, línea, sentido, secuencia y catálogo; los nuevos
contextos expresos añaden /express-timing-v1. Los intervalos históricos conservan su
clave/base y no se trasladan al nuevo contexto automáticamente. NORMAL no recibe ese
sufijo ni muestras expresas. Esta separación deliberada exige reunir evidencia nueva
para el modelo nuevo, en vez de reclasificar silenciosamente observaciones antiguas.

Las estaciones sin detención siguen visibles, con nodo hueco, etiqueta y semántica
“Paso sin detención”. La animación usa el reloj de la sesión y las duraciones de todas
las aristas; cruzarlas no pausa, no confirma puertas y no registra paradas. Las filas
expresas tienen textos estables y el refresco visible es de 50 ms mientras se avanza;
pausa, correcciones y llegada siguen gobernadas por el controlador existente. NORMAL
conserva su cadencia y presentación previas. Aprendizaje distingue ROJO/VERDE/NORMAL,
base provisional, evidencia, ajuste y nombres atravesados, sin repetir sus segmentos
como mediciones directas.

### Persistencia y compatibilidad

Viaje interno v8 incorpora expressTiming v1 con baseMillis por arista. Se valida contra
la clasificación del catálogo histórico correspondiente antes de aceptar/restaurar.
Viajes v1–v7 sin el campo conservan exactamente el modelo anterior; el escritor conserva
su versión mínima. v8 se propaga también si solo hay un modelo nuevo en undo, archivo o
ruta histórica. Los lectores antiguos rechazan v8 explícitamente. Capturas originales
v1/v2 contienen la ruta v8 mediante su codec habitual; sus relojes, valores medidos y
exclusiones no se reescriben. El snapshot/backup existente incluye los payloads completos.
No se añaden tablas, migraciones, preferencias, archivos ni bytes externos.

### Propuesta futura — NO implementada: selección por horario

Diseñar un catálogo local versionado con fuentes oficiales verificables, fecha de
revisión, días/horarios, excepciones y reglas por línea/dirección. Consultarlo offline
para recomendar NORMAL, ROJO o VERDE compatibles con origen/destino. Separar horario
publicado de operación efectiva: coincidir con la franja no demuestra disponibilidad.
Debe admitir servicio preferido y elección manual permanente; una eventual selección
por defecto solo se activaría con consentimiento del usuario. Actualizar/validar el
catálogo sin alterar sesiones históricas ni mezclar evidencia entre servicios.
No se añadieron controles, preferencias ni lógica automática. El escenario de horario
referencial que ya existía continúa siendo explícito y no es esta propuesta futura.

### Validación dirigida de Ruta Expresa

Seis casos distintos aprobados: conservación de NORMAL/modelo histórico y continuidad
matemática al cruzar una estación omitida; intervalo acumulado con seis viajes,
separación de servicios/modelos/sentidos y deshacer; backup real de ambos modelos y
capturas originales con rechazo de corrupción; resumen visual sin intervalos ficticios;
rechazo de confirmaciones en estaciones sin detención; ventana y filtro robusto
existentes. Compilación Kotlin y diff correctos, sin suite global ni APK.
El refresco de 50 ms es local al mapa, sin replantear rutas ni generar capturas.
Pendiente: comprobación visual en dispositivo de continuidad, pausa/corrección y
fuentes grandes; calibración de los supuestos provisionales con mediciones expresas
reales. No se afirma que los 15 s sean una permanencia real verificada.
