# Validación de la base maestra de Metro

Revisión del 9 de octubre de 2026 sobre el módulo existente. No se reconstruyó el módulo, no se cambió la versión de aplicación 0.3.0 / 9 ni la firma oficial, y no se modificaron datos reales del usuario.

## Resultado de la comparación completa

| Línea | Entradas | Clasificación |
| --- | ---: | --- |
| L1 | 27 | Normal |
| L2 | 26 | 9 rojas, 8 verdes, 9 comunes |
| L3 | 21 | Normal |
| L4 | 23 | 7 rojas, 7 verdes, 9 comunes |
| L4A | 6 | Normal |
| L5 | 30 | 9 rojas, 8 verdes, 13 comunes |
| L6 | 10 | Normal |
| Total | **143** | **79 accesos con clasificación expresa** |

Las 143 entradas representan **126 estaciones físicas**, porque las combinaciones reutilizan el mismo ID en ambas líneas. Orden, terminales y todas las combinaciones coinciden con la transcripción. No se crearon estaciones duplicadas ni conexiones nuevas.

## Discrepancias corregidas

| Estación L5 | Beta anterior | Base maestra / revisión r1 |
| --- | --- | --- |
| Rodrigo de Araya | Verde | **Roja** |
| Carlos Valdovinos | Roja | **Verde** |
| Camino Agrícola | Verde | **Roja** |
| San Joaquín | Roja | **Común** |

Las demás 75 clasificaciones expresas ya coincidían. La revisión del catálogo pasa de `santiago-2024-02-beta1` a `santiago-2024-02-beta1-r1`; no es un cambio de versión de la aplicación.

Diferencias de escritura, conservadas sin cambiar identidades: U.L.A. → Unión Latinoamericana; Pdte. Pedro Aguirre Cerda → Presidente Pedro Aguirre Cerda; Chile España → Chile-España; Parque O'Higgins → Parque O’Higgins. Son abreviaciones/puntuación, no estaciones diferentes.

Hernando de Magallanes y Los Dominicos siguen operativas en el catálogo base. No hay cierres temporales incrustados como permanentes. Los cierres que el usuario haya registrado manualmente se conservan.

## Contraste de las referencias gráficas

Se comparó toda la transcripción aportada con el catálogo y se revisaron los planos oficiales disponibles de [L2](https://www.metro.cl/images/mapa_ruta_expresa_l2.png?v=2024020601), [L4](https://www.metro.cl/images/mapa_ruta_expresa_l4.png?v=2024020601) y [L5](https://www.metro.cl/images/mapa_ruta_expresa_l5_2024.png?v=2024020601). El plano de L2 se presenta en dirección opuesta a la lista y se comprobó en ese orden inverso.

Hay una inconsistencia entre referencias de Metro: la tabla HTML de horarios de [Ruta Expresa](https://www.metro.cl/el-viaje/ruta-expresa) consultada durante la implementación anterior todavía muestra las cuatro clasificaciones antiguas de L5; el plano gráfico de L5 coincide con la base maestra del usuario. Para esta corrección se priorizó la transcripción y el plano gráfico, sin afirmar operación en tiempo real.

En este mensaje se adjuntó la transcripción, **no los archivos originales de las capturas del usuario**. Por tanto, el contraste visual realizado corresponde a los planos oficiales disponibles; no se afirma haber inspeccionado capturas que no fueron adjuntadas.

## Compatibilidad y datos guardados

Las preferencias guardan un catálogo histórico. Cambiar solamente el asset habría dejado la clasificación incorrecta en instalaciones con preferencias ya guardadas; sustituir ese snapshot habría podido invalidar rutas y sesiones antiguas.

`MetroCatalogRevision` resuelve ambas revisiones de referencia de forma determinista y offline. Comprueba una huella SHA-256 de todas las estaciones y líneas antes de aplicar las cuatro correcciones. El explorador y los cálculos nuevos utilizan r1, incluso si las preferencias conservan el catálogo original. Las rutas y sesiones antiguas se validan según su revisión original y no se reescriben, reinician ni recalculan automáticamente. Una alternativa que afecta al viaje debe confirmarse mediante el flujo existente.

Se conservan el ID de cada estación, Casa, favoritos, restricciones, personas, avatares, responsables, rutas, pausas, correcciones y estadísticas. Un simple cambio del nombre de revisión no marca una ruta físicamente idéntica como afectada.

Room permanece en **24** y el formato de backup en **v16**: no hay tablas, columnas, campos persistentes ni archivos privados nuevos. El snapshot de catálogo del backup más la revisión de cada ruta permiten reconstruir ambas definiciones mediante la regla fija de compatibilidad; no se necesita Internet ni el asset de una instalación anterior. Esta regla histórica debe conservarse en futuras versiones. Una revisión desconocida o una huella distinta no se interpreta por aproximación. La Beta anterior no conoce r1 y puede rechazar explícitamente un backup con rutas nuevas, sin sustituir datos.

La restauración conserva el protocolo existente: validación previa, transacción, recuperación de archivos, invalidación de revisiones antiguas de acciones y confirmación de posición para sesiones importadas.

## Pruebas ejecutadas

`JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests 'com.r0ybt.arachn0de.metro.*' :app:assembleDebug --console=plain`

**34 ejecuciones aprobadas**, sin fallos:

- 6 de base maestra: todas las 143 entradas en orden, nombres normalizados, conexiones, 79 clasificaciones, 272 recorridos normales de una arista en ambos sentidos, cambios rojo/verde en L2/L4/L5, recorrido completo expreso por L5, múltiples combinaciones/paradas y ausencia de cierres permanentes.
- 6 de motor: costes, desempates, restricciones, recorridos físicos completos, paradas y validación de payloads.
- 9 de persistencia/backup: incluye un viaje histórico activo y pausado junto a un viaje nuevo r1; preferencias y payload histórico permanecen intactos, y ambos se restauran tras eliminar la base de pruebas. También referencias a tareas y avatares originales, cancelación, rollback y acciones concurrentes.
- 6 temporales: pausa, reanudación, correcciones hacia delante/atrás, aproximación entre estaciones, reloj/reinicio y cambios de recorrido.
- 2 de migración histórica de Metro, 2 de servicio/notificación y 3 de interfaz Compose, incluida pantalla completa, estado restaurado y apertura desde notificación.

La compilación debug terminó con **BUILD SUCCESSFUL** y `git diff --check` fue correcto. No se ejecutó una suite global ajena a esta corrección.

La nueva APK incluye el catálogo r1 exacto y las cinco imágenes originales del juego, comprobadas byte por byte. Firma debug v2 válida; applicationId `com.r0ybt.arachn0de`, versión 0.3.0 / 9. No hay permisos ni consultas externas nuevos durante el uso.

## Archivos de esta corrección

- `app/src/main/assets/metro/santiago-beta1.json`: cuatro clasificaciones y revisión del catálogo.
- `metro/MetroCatalogRevision.kt`: compatibilidad entre revisiones sin escribir datos de usuario.
- `metro/MetroNetwork.kt`: selección de revisión de planificación, validación de pertenencia de estación a línea y comparación física de rutas.
- `metro/MetroCodec.kt`: resolución de la revisión propia de cada ruta y acceso a la red de planificación.
- `metro/MetroScreen.kt`: utiliza la referencia corregida; mantiene los componentes y controles existentes.
- `app/src/test/resources/metro/santiago-master-2026-10-09.json`: fixture independiente derivado de la transcripción.
- `metro/MetroMasterValidationTest.kt`, `MetroPersistenceTest.kt`: comparación completa y continuidad de backups/sesiones.

Los archivos Kotlin abreviados se encuentran bajo `app/src/main/java/com/r0ybt/arachn0de/metro/` o bajo su paquete de pruebas correspondiente.

## APK debug corregida

`/home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/Arachn0de-Metro-Beta1-r1-debug-v0.3.0.apk`

**103.280.575 bytes (98,50 MiB)**.

SHA-256: `9f8dbfb95c64b993b985815675999be6e7ebeafdde33c19ae0781c49c17bf4b7`.

Se conserva la APK Beta anterior por separado. Esta APK debug no actualiza una instalación con firma release; no se deben desinstalar aplicaciones ni borrar datos para superar la diferencia de firmas. No se ejecutó firma oficial, commit, push ni envío automático.

Los horarios expresos y punta tarifaria siguen como información separada, con selección manual del servicio. Las tarifas de las capturas no se incorporaron a la interfaz ni se presentaron como vigentes. Permanecen las limitaciones Android y las comprobaciones físicas pendientes indicadas en METRO_BETA.md.
