# Arachn0de — Dogfooding Sprint 4A

Versión conservada: **0.3.0 / versionCode 10**. Trabajo sobre el repositorio existente, preservando Sprints 1–3. Sin Release, firma, instalación, commit ni push.

## Implementación

- Seguimiento por etapas: Comenzar viaje, confirmar llegada a combinación/destino, Iniciar combinación y Comenzar siguiente línea. La estimación se limita al tramo ferroviario vigente; caminar y esperar no avanza por la siguiente línea. Se conservan identidad, duración total y registros de duración ferroviaria/transbordo. Varias combinaciones y paradas coincidentes comparten el mismo flujo.
- Pausar/Reanudar y Llegué comparten una fila cuando cabe, con iconos, jerarquía primaria/secundaria del tema y contexto visible. Con poco ancho pueden envolver sin reducir áreas táctiles.
- Deshacer llegada conserva etapa, progreso, pausa, anclas, asociaciones y sesión. No caduca; persiste al cerrar. El intervalo detenido no se convierte en avance ferroviario al deshacer. Tras comenzar otra línea se ofrece corregir estación explícitamente. Revisiones y transacciones evitan duplicados; transiciones idénticas no generan escrituras. La navegación espera la sesión observada después de empezar/deshacer, evitando carreras con el snapshot previo.
- Viajes usa tarjetas compactas con origen/destino, líneas, combinación, estado y posición esencial; activos primero. Tocar abre recorrido/seguimiento sin cambiar datos. Las acciones administrativas e historial permanecen en detalle.
- Indicador Metro en el nodo asociado, todos sus ancestros y Proyecto propietario; no marca ramas ajenas. Pulso suave activo/combinando, fijo pausado y espera, desaparece al finalizar. Respeta movimiento reducido. Tocar abre el seguimiento existente; varias entradas ofrecen selector. Preview asociado añade líneas, combinación, estado y posición.
- Cálculo de jerarquía/previews fuera del hilo principal y catálogo compartido; se reutiliza el servicio existente. No hay servicios ni relojes de seguimiento nuevos por tarjeta.

## Estados y persistencia

`RIDING → ARRIVED → TRANSFERRING → RIDING`, con confirmaciones explícitas. El destino termina la sesión; Deshacer puede reabrir esa misma sesión. El tiempo de la nueva línea usa una ancla nueva; el total conserva el comienzo original. Una corrección dentro del mismo tramo conserva su reloj de duración.

Room permanece en **27**, sin migración ni esquema nuevo. El payload JSON Metro pasa a **v3** y conserva lectores v1/v2. Los históricos derivan su etapa sin reescribirse al consultar ni inventar duraciones. El formato exterior del backup sigue 17/18/19 según sus datos. Control, registros y snapshot no recursivo de undo se exportan/restauran con validación estricta antes de reemplazar. Restaurar invalida la confianza en relojes locales tanto de la sesión como de su undo. No hay nuevos archivos persistentes ni cambios en su liberación.

SQLCipher, contraseñas principal/señuelo, separación de almacenes, archivos cifrados y tiempos de bloqueo no cambian. La prueba de aislamiento verifica seguimiento en combinación y backup cifrado, bloqueo, revocación y reapertura del almacén correcto.

## Archivos del sprint

Motor y persistencia: `metro/MetroStages.kt`, `MetroTracking.kt`, `MetroControlCodec.kt`, `MetroCodec.kt`, `MetroRepository.kt`, `backup/BackupRepository.kt`.

Interfaz: `metro/MetroActivity.kt`, `MetroScreen.kt`, `MetroRoutePreview.kt`, `MetroService.kt`, `ui/AppRoot.kt`, `ProjectScreen.kt`, `NodeComponents.kt`, `ProjectComponents.kt`.

Pruebas: `MetroStageEngineTest.kt`, `MetroStagePersistenceTest.kt`, `MetroStageUiTest.kt`; ajustes de expectativas e integración en `MetroTrackingTest.kt`, `MetroCardConsultationTest.kt`, `MetroServiceTest.kt`, `MetroRedesignUiTest.kt`, `security/VaultManagerTest.kt`.

Contrato documentado en `ARCHITECTURE.md` y `BACKUP_FORMAT.md`. Los demás cambios presentes en el repositorio pertenecen a trabajo anterior y se conservan.

## Validación y límites

Las pruebas dirigidas cubren una/múltiples líneas, llegada, undo, pausa antes de llegar, combinación, embarque, reapertura, idempotencia, identidad del reloj, tarjetas, consulta, propagación, retirada del indicador, selector y movimiento reducido, payloads históricos, backup corrupto y aislamiento de almacenes.

Los tests de UI y persistencia ejecutados son JVM/Robolectric. `MetroNativePersistenceTest` también es una prueba de host, no certificación instrumental de SQLCipher. La prueba Vault usa autenticación, envelopes, archivos y backup cifrados reales, con el driver Room sustituido solo en el test y fsync de directorios adaptado al host. No se instalaron ni ejecutaron pruebas en Android; AndroidTest se compila únicamente. No se afirma nueva certificación de la migración SQLCipher.

Se conserva la regla existente de **un viaje activo por almacén**. El selector de múltiples entradas se verifica con datos sintéticos sin ampliar esa restricción. Los viajes convertidos previamente en Nota/Capa mantienen consulta e indicadores de sus asociaciones; no se introducen asociaciones directas a Proyectos.

Pendiente comprobar en teléfono físico: recorrido real con varias combinaciones, notificación/servicio en segundo plano y pantalla bloqueada, recuperación tras muerte del proceso/reinicio, fuentes grandes y pantallas estrechas, temas y movimiento reducido, consumo de batería y SQLCipher nativo. Los relojes restaurados/reiniciados pueden requerir confirmar posición; no se oculta esa incertidumbre.

### Resultados ejecutados

**77 casos dirigidos distintos, todos aprobados**, consolidando los resultados de las ejecuciones y las repeticiones limitadas a correcciones:

| Clase | Casos aprobados |
|---|---:|
| MetroStageEngineTest | 9 |
| MetroStagePersistenceTest | 5 |
| MetroStageUiTest | 4 |
| MetroTrackingTest | 9 |
| MetroPersistenceTest | 10 |
| MetroNativePersistenceTest (host) | 4 |
| MetroCardConsultationTest | 8 |
| MetroRedesignUiTest | 4 |
| MetroUiTest | 3 |
| MetroServiceTest | 2 |
| SavedTemplatesTest | 9 |
| SavedTemplatesMigrationTest | 2 |
| SavedTemplatesUiTest | 4 |
| VaultManagerTest | 4 |

También pasaron las regresiones dirigidas de fotografías/backup (`Dogfooding2PersistenceTest`, 4), tarjeta de Proyecto (`CompactProjectCardTest`, 4), indicadores previos (`AttentionUiTest`, 3) y orden Capas primero (`NodeSortUiTest`, 1 caso seleccionado). No se ejecutó la suite completa.

`assembleDebug`, `assembleDebugAndroidTest`: **BUILD SUCCESSFUL**. `git diff --check`: aprobado. La última ejecución específica terminó correctamente en `/tmp/dogfood4a-final4.log`; los resultados de host están en `app/build/test-results/testDebugUnitTest/` y `app/build/reports/tests/testDebugUnitTest/`. Esa última selección contiene 15 casos; las 77 pruebas de la tabla corresponden a la unión de ejecuciones, sin contar repeticiones. Los resultados de las ejecuciones previas consolidadas están en `/tmp/dogfood4a-results-first/`, `/tmp/dogfood4a-results-stages/` y `/tmp/dogfood4a-results-final3/`.

Los fallos iniciales de fixtures (ruta sin combinación y fsync de directorio no implementado por Robolectric) se corrigieron solo en los tests. La carrera real de navegación tras comenzar/deshacer se corrigió en MetroScreen y se verificó mediante la interacción completa de UI.
