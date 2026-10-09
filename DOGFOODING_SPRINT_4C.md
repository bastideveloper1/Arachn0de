# Arachn0de — Dogfooding Sprint 4C

Implementación sobre el estado existente de 4A/4B. Se mantiene **0.3.0 / versionCode 10**, applicationId, firma oficial y seguridad. No se generó Release, ni se usó el keystore oficial, ni se instaló, actualizó o desinstaló una aplicación, ni se hicieron commits o push. Los ensamblados Debug usan el procedimiento Debug existente solicitado para validar compilación.

## Auditoría y precisión

El modelo anterior asigna **2 minutos a cada segmento físico**, y 4 a cambios/combinaciones de la planificación. No hay mediciones fiables por segmento ni una tabla de detenciones. No se añadieron minutos arbitrarios ni se inventó una separación trayecto/detención. Los dos minutos siguen representando el total genérico; la UI lo explica. Esta limitación puede causar desfase real en recorridos largos y **no queda certificada precisión física** por este sprint.

El progreso utiliza diferencias absolutas de `elapsedRealtime`, sin incrementar contadores por cada refresco. Se comprueba un recorrido de 20 segmentos, con consultas repetidas y un salto largo sin refrescar, sin acumulación de errores de software. El avance continúa limitado a la etapa y no atraviesa automáticamente una combinación. Pausas no avanzan el tren; la siguiente etapa empieza con una nueva ancla y el transbordo no se suma al avance ferroviario.

Correcciones implementadas:

- Un salto del reloj civil ya no invalida un reloj monotónico válido del mismo arranque. Un reinicio, regresión monotónica o estado restaurado incierto siguen deteniendo la estimación hasta confirmar posición.
- «Estoy aquí» prioriza las apariciones de la estación dentro de la etapa activa cuando hay repeticiones. Reancla el progreso y su estimación restante, conserva el inicio, identidad, registros confirmados y reloj de duración real. Se prueban correcciones adelantadas, atrasadas y durante pausa.
- Se muestra la estimación restante de la etapa en las opciones. No es GPS ni una lectura de ubicación real.
- El cronómetro de combinación conserva su significado histórico de intervalo transcurrido, **incluidas las pausas**. Se indica explícitamente en la UI; no se reinterpretan registros antiguos. Permanencias en paradas y espera antes de abordar no se convierten en avance ferroviario.
- Crear un regreso expuso un fallo en la ordenación de viajes con sesiones junto a planes vacíos: se mezclaban `Long` e `Int` en el comparador. Ambos valores iniciales ahora son `0L`.

No se implementó aprendizaje de tiempos personales ni de combinaciones.

## Planificar regreso

Disponible en el detalle de un viaje guardado/finalizado y en Opciones de seguimiento del activo. La acción prepara **un borrador independiente**, sin escribir ni iniciar:

1. Origen = destino de la última ruta de ida, incluido un destino cambiado en 4B.
2. Destino = origen original de la sesión; si no hay sesiones, el del plan guardado.
3. Paradas intermedias vacías. Se explica que deben elegirse expresamente, no se invierten automáticamente.
4. Se recalcula con el motor y restricciones actuales; se determinan de nuevo sentido, líneas, colores y combinaciones.
5. Se conserva la Persona. Al guardar desde una tarea, el regreso es independiente: la asociación y todo el historial de ida quedan intactos. Puede crearse una nueva tarea mediante la acción explícita existente.
6. Guardar/revisar no reemplaza un seguimiento activo. La UI y repositorio impiden comenzar otro mientras el primero siga activo.

Origen/destino iguales, varias combinaciones, paradas de ida, restricciones que impiden una ruta, viaje activo/pausado y cambio de destino se cubren en pruebas. Repetir Guardar en el mismo borrador reutiliza la identidad guardada; el bloqueo de operación evita dobles guardados simultáneos. Un viaje generado desde una plantilla usa los mismos planes y asociaciones del editor existente, sin copiar sesiones de la plantilla.

## Ruta Expresa: reglas, fuentes e incertidumbre

Antes de modificar el motor se revisó `metro/santiago-beta1.json` y la revisión r1 existente: L2/L4/L5 ya contienen clasificaciones roja, verde y común. L1/L3/L4A/L6 no tienen Ruta Expresa. Se conservan todas las clasificaciones, incluida la corrección anterior de L5; no se reemplazan por una lista histórica de 2022.

Fuentes consultadas el 9 de octubre de 2026:

- [Transporte Informa RM, anuncio del 18 de abril de 2022](https://www.transporteinforma.cl/noticias/regresa-ruta-expresa-de-metro-de-santiago/): referencia oficial histórica de L2/L4/L5 y franjas de lunes a viernes 06:00–09:00 y 18:00–21:00. **No demuestra vigencia en 2026**.
- [Red Movilidad: Metro](https://www.red.cl/mapas-y-horarios/metro/): catálogo/red y horarios generales; no confirma las ventanas actuales de Ruta Expresa ni un servicio de tren concreto.
- [Metro: Ruta Expresa](https://www.metro.cl/el-viaje/ruta-expresa): intento de consulta denegado por robots.txt. La URL alternativa `metro-express` no fue accesible. Las referencias de mapas del catálogo y sus revisiones se mantienen; no se declara una nueva certificación oficial de sus colores.

Por ello, **no se activó un horario automático presentado como vigente**. Se incorporó «Simular horario de referencia», optativo, tanto en Planificar como en el editor de tareas. La elección manual Normal/Expresa sigue disponible; elegirla abandona la simulación. Fecha y hora se interpretan en `America/Santiago`, con calendario compatible con Android 7 sin dependencias nuevas. El panel operativo «Horario y tipo de servicio» permanece oculto.

El motor `planAt` evalúa cada segmento a su hora estimada de entrada/salida y cada siguiente línea después del coste de combinación; no solo la hora de salida inicial. Considera dirección (la referencia modela ambas), colores, fines de semana, cierres, evitación, interrupciones y datos ausentes. El proveedor acepta festivos conocidos, cubiertos en pruebas; **la app no dispone de un calendario oficial completo de festivos/excepciones** y lo advierte, por lo que un lunes festivo no puede considerarse operación confirmada.

Dentro de la franja, solo se aborda/cambia/baja en estaciones atendidas por el color; los pasos por estaciones sin detención se conservan y se identifican. Fuera, el escenario usa servicio normal. Una línea posterior puede requerir otro servicio. Un tramo que atraviese una frontera horaria sin información sobre la transición del tren no se autoriza: se busca una alternativa válida o se indica que no hay ruta comprobable. No se predice qué tren llega ni se fabrican esperas para atravesar una frontera; se rechazan ciclos usados para esperar. La búsqueda temporal tiene un horizonte conservador por subrecorrido, `2 × estaciones + 4 × líneas` minutos; no representa capacidad ni duración máxima de un viaje guardado. Una ruta excepcionalmente larga o que necesite esperar deliberadamente puede requerir revisión manual. El cálculo temporal del editor se ejecuta fuera del hilo UI y un resultado de otro borrador no permite guardar.

Las advertencias distinguen referencia, color recomendado y estado operativo desconocido. Antes de iniciar o embarcar una etapa programada se comprueba el escenario para el momento actual; si cambia el servicio, se requiere revisar la ruta. Cambiar destino en una ruta programada recalcula con la hora actual y conserva los archivos de recorrido de 4B. Una propuesta que cambie antes de su confirmación se rechaza, sin sustituir silenciosamente lo mostrado. Repetir el mismo destino esperando embarque no crea otro archivo aunque avance la hora y los servicios sigan iguales.

## Persistencia, backup y compatibilidad

Room sigue en **27**, sin tablas, columnas ni migraciones. El formato exterior de backup sigue siendo 17/18/19. No se agregan archivos, permisos ni dependencias.

El payload Metro admite **v1–v5**. Se emite v5 cuando un plan/sesión/historial contiene `departure`, el instante civil del escenario. Los planes manuales siguen emitiendo v4. v5 admite etapas normales y expresas dentro del mismo plan según sus horas; las rutas históricas conservan sus reglas y datos. Se validan entero no negativo/sin desbordamiento, geometría, continuidad, colores de embarque/bajada, cambios y coherencia con la referencia horaria. v1–v4 no admiten metadatos programados por conjetura. Las versiones anteriores de la app pueden rechazar v5.

La fecha se incluye en el mismo JSON de plan, rutas originales, archivos históricos y undo; el backup existente transporta todo. La restauración valida antes de reemplazar y aplica el tratamiento portable de relojes y posición incierta. Se prueba reapertura, backup/restauración con ruta recalibrada y destino cambiado, rechazo de fecha corrupta sin sustituir el estado anterior y backup cifrado en principal con aislamiento del señuelo. Los borradores sin guardar no se exportan. Las rutas guardadas conservan fecha, servicio e identidad. No hay bytes nuevos que gestionar ni cambios al contrato de liberación de archivos.

SQLCipher, principal/señuelo, claves, archivos cifrados, temporizadores y movimiento reducido se conservan. La prueba de Vault en host sustituye solo el driver SQLCipher del fixture, no la autenticación ni envolturas: se informa separadamente de la ejecución nativa.

## Archivos de 4C

Base `app/src/main/java/com/r0ybt/arachn0de/metro/`:

- Nuevos: `MetroReturn.kt`, `MetroSchedule.kt`, `MetroDepartureField.kt`, `MetroScheduledPlan.kt`.
- Ampliados: `MetroTracking.kt`, `MetroNetwork.kt`, `MetroCodec.kt`, `MetroRepository.kt`, `MetroStages.kt`, `MetroScreen.kt`, `MetroDraftFields.kt`.
- Pruebas nuevas: `MetroPrecisionTest.kt`, `MetroScheduleTest.kt`, `MetroReturnPersistenceTest.kt`, `MetroSprint4CUiTest.kt`.
- Regresiones adaptadas: `MetroTrackingTest.kt`, `MetroPresentationTest.kt`, `MetroRedesignUiTest.kt` y el caso Metro de `security/VaultManagerTest.kt`.
- Documentación: `ARCHITECTURE.md`, `BACKUP_FORMAT.md`, este informe. Las modificaciones anteriores de 4A/4B permanecen intactas.

## Validación nativa existente

`adb devices -l` encontró `emulator-5554` y un runner aislado **ya instalado**, `com.r0ybt.arachn0de.securityvalidation.test`, dirigido a `com.r0ybt.arachn0de.securityvalidation`. Se ejecutó su clase `NativeVaultTest`, sin instalar la APK nueva ni la aplicación principal:

```text
adb -s emulator-5554 shell am instrument -w -r -e class com.r0ybt.arachn0de.security.NativeVaultTest com.r0ybt.arachn0de.securityvalidation.test/androidx.test.runner.AndroidJUnitRunner
OK (4 tests), 68.766 segundos
```

Log: `/tmp/4c-native-existing.log`. Verifica en ese binario apertura/migración nativas, interrupción/recuperación, upgrade cifrado 26→27 y separación de almacenes. Los tests usan raíces UUID aisladas. **No ejecuta el código 4C compilado ni certifica nativamente su payload v5**: el runner existente corresponde a la compilación anterior. Validar el código nuevo nativamente requerirá instalar un binario de pruebas nuevo, acción excluida por este sprint. No se presenta una ejecución JVM como SQLCipher nativo.

## Pendientes antes del Release

- Prueba física Las Parcelas → Monte Tabor → Baquedano y otros recorridos largos: medir trayecto y detención reales. La exactitud de 2 minutos genéricos no está demostrada.
- Verificar oficialmente horarios vigentes, festivos, excepciones y reglas de transición/dirección. La simulación histórica no autoriza una operación real.
- Probar UI de fecha/hora, SDK 24, fuentes grandes, teclado, vuelta del segundo plano/pantalla bloqueada y reinicio con el código nuevo en teléfono.
- Ejecutar pruebas nativas del payload v5 y de recuperación de Metro con el binario nuevo cuando se autorice instalación de pruebas.
- Revisar llegada/undo, combinación prolongada, regreso activo/pausado, cambio de destino y mapa ampliado en condiciones reales.

No se modificó el juego, ni se eliminaron datos, ni se inició aprendizaje personalizado.

## Resultados finales de validación del código actual

Ejecución final `/tmp/4c-final.log`: **BUILD SUCCESSFUL, 1 min 3 s**, con `testDebugUnitTest`, `assembleDebug` y `assembleDebugAndroidTest`. Los XML de `app/build/test-results/testDebugUnitTest/` registran **95 casos, 0 fallos, 0 errores**. Se incluyen seis escenarios de horario en SDK 24 y 28 (12 ejecuciones); no se suman repeticiones anteriores. No se ejecutó la suite global.

| Clase | Casos aprobados |
|---|---:|
| MetroPrecisionTest | 5 |
| MetroScheduleTest (SDK 24 y 28) | 12 |
| MetroReturnPersistenceTest | 7 |
| MetroSprint4CUiTest | 2 |
| MetroDestinationTest | 11 |
| MetroPersistenceTest | 10 |
| MetroPresentationTest | 5 |
| MetroRedesignUiTest | 4 |
| MetroServiceTest | 2 |
| MetroSprint4BUiTest | 6 |
| MetroStageEngineTest | 9 |
| MetroStagePersistenceTest | 5 |
| MetroStageUiTest | 4 |
| MetroTrackingTest | 9 |
| MetroUiTest | 3 |
| VaultManagerTest (caso Metro cifrado/aislamiento) | 1 |

Se cubren seguimiento largo sin dependencia de refrescos, corrección adelantada/atrasada/repetida y pausa, relojes, combinación prolongada, fecha/día/color, ventanas atravesadas y llegada a otra línea, datos ausentes, regreso después de cambiar destino, conservación del vínculo de ida, bloqueo de otro seguimiento, reapertura, exportación/restauración, corrupción sin reemplazo, idempotencia y propuesta obsoleta. Las pruebas de interfaz son Robolectric con gráficos nativos SDK 28; no son ejecución en un teléfono. La consulta del emulador y sus cuatro pruebas nativas anteriores se documentan separadamente arriba.

Las ejecuciones intermedias corrigieron un error de compilación por precedencia de `if/else` con Elvis, dos fixtures (estación que no pertenecía al itinerario elegido y acceso al Activity desde la transacción IO), el fallo real del comparador y una aserción que debía desplazar la lista para alcanzar Iniciar viaje en pantalla pequeña. Se mantuvieron las comprobaciones y se repitieron sus regresiones. Logs intermedios: `/tmp/4c-tests1.log` a `/tmp/4c-tests4.log`, `/tmp/4c-final1.log`. Todos los escenarios de la selección final están aprobados.

`git diff --check` pasa. `app/build.gradle.kts` conserva **versionName 0.3.0 / versionCode 10**. Las pruebas locales y el runner nativo previo no certifican precisión real del Metro, operación vigente de Ruta Expresa ni validación nativa del código 4C. Se mantiene la necesidad de las comprobaciones físicas descritas antes de distribuir.
