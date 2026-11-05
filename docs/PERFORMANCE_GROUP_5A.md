# Rendimiento — Grupo 5A: retirada del Juego (R14)

## Alcance y resultado

Se retira Juego/Carrera táctica. Se conservan íntegros los cambios de los Grupos
1–4, Tragaperras y sus modos Tradicional/Tareas/PAGO/Ahorro, Banco, Metro,
Pomodoro, audio, fotografías, adjuntos, temas oficiales y seguridad. Sin cambios
de versión, dependencias, APK oficial ni commits. No se aplica el Grupo 5B.

## Inventario y dependencias

Los únicos accesos estaban en AppRoot y AppIdentityDrawer, con parámetros
propagados por ProjectsScreen y ProjectScreen. Se retiran estado showGame,
ruta game, proveedor de estado, cabecera y callbacks onGame/onOpenGame. Los
controles vecinos conservan orden, diseño y sus acciones. Herramientas mantiene
Diagramas, Wireframe, Pomodoro y Captura rápida; Tragaperras conserva su entrada.

Se eliminan pantallas, tablero, diálogos, reglas, bots, generador de mapas,
animaciones, repositorios jugables, cargadores de imágenes, arte y cachés
exclusivos. No se encontraron sonidos de archivo ni bibliotecas de Gradle
exclusivos que retirar. Los sprites procedían de game/ y de drawable-nodpi;
se comprobaron referencias R.drawable, XML, nombres dinámicos y consumidores
fuera del módulo antes de borrarlos. Ninguno era compartido con Tragaperras.
Los componentes compartidos de Compose, Material, Room, JSON, corrutinas y
seguridad se conservan. No se toca assets/slots ni sus subdirectorios.

Archivos de producción retirados del directorio `game/` (20):

- `GameAssets.kt`
- `GameBattleArt.kt`
- `GameBots.kt`
- `GameDefinitions.kt`
- `GameExpansion.kt`
- `GameFog.kt`
- `GameMapGenerator.kt`
- `GameModel.kt`
- `GameRules.kt`
- `GameSessionCodec.kt`
- `GameStateRepository.kt`
- `GameTextures.kt`
- `RaceAssets.kt`
- `RaceBoard.kt`
- `RaceCodec.kt`
- `RaceItemDialog.kt`
- `RaceModel.kt`
- `RaceRepository.kt`
- `RaceRules.kt`
- `RaceScreen.kt`

Cinco archivos con modelos/codecs se trasladan y reducen a
`backup/legacy/game/`: GameModel, GameDefinitions, GameSessionCodec, RaceModel
y RaceCodec. LegacyMapValidation conserva solo el validador del mapa histórico.
Las únicas referencias externas al lector son la validación de BackupData y la
migración histórica en GameStateEntity. Los seis archivos restantes modificados
de producción son esos dos consumidores y los cuatro componentes de navegación.

## Recursos retirados

Se retiran 51 PNG y el README exclusivo; no se recomprimen otros recursos.

`assets/game/` (25 PNG y README):

- `README.md`
- `araña.png`
- `avispas.png`
- `barricada.png`
- `caltrops.png`
- `capainvisible.png`
- `cocodrilo.png`
- `condor.png`
- `conejo.png`
- `dados.png`
- `dinamita.png`
- `dino.png`
- `gato.png`
- `ojorayo.png`
- `pantano.png`
- `paredcueva.png`
- `pastoseco.png`
- `perro.png`
- `puma.png`
- `rata.png`
- `roca.png`
- `sendero.png`
- `sendero_con_caja.png`
- `terrenocueva.png`
- `tiburon.png`
- `tortuga.png`

`res/drawable-nodpi/` (26 PNG exclusivos):

- `barrerahielo.png`
- `bosque.png`
- `caballero.png`
- `cazadora.png`
- `dados1.png`
- `dados2.png`
- `dados3.png`
- `dados4.png`
- `dados5.png`
- `dados6.png`
- `espinas.png`
- `estandarte.png`
- `fondo_cesped.png`
- `mago.png`
- `monje.png`
- `nigromante.png`
- `normal_arquero.png`
- `normal_caballero.png`
- `normal_mago.png`
- `normal_monje.png`
- `normal_nigromante.png`
- `normal_picaro.png`
- `picaro.png`
- `totem.png`
- `trampaoso.png`
- `zombie.png`

## Tamaño medido

Comparación con el estado inicial capturado en `/tmp/group5a-before`:

| Concepto | Bytes retirados | MiB |
| --- | ---: | ---: |
| 25 PNG de assets/game | 67.920.503 | 64,774039 |
| README exclusivo | 997 | 0,000951 |
| 26 PNG drawable-nodpi | 52.651.532 | 50,212414 |
| Total de recursos y README | 120.573.032 | 114,987404 |
| Kotlin de producción, reducción neta incluido lector conservado y navegación | 90.186 | 0,086008 |
| **Total neto de producción, fuentes y recursos** | **120.663.218** | **115,073412** |

Los 20 Kotlin originales sumaban 127.066 bytes antes de aislar compatibilidad.
Las cifras netas descuentan el código conservado; no suman documentación ni
pruebas. Se retiran también 13 archivos de pruebas exclusivamente jugables
(95.399 bytes), sustituidos por fixtures y pruebas del lector. La reducción real
de una APK todavía no está medida; estos bytes no son una promesa de ahorro de
APK, RAM ni batería. No se borran APK previas ni datos privados.

## Persistencia y compatibilidad

Se conservan game_state, DAO, migración 22→23 y preferencia histórica. No hay
migración nueva ni destructiva, limpieza privada o cambio de formato. BackupJson
y BackupRepository permanecen idénticos al inicio: captura transaccional,
staging, validación antes de sustituir, revisión local, exportación de texto
original, restauración y rollback conservan su comportamiento.

Los codecs v1–v4 mantienen límites de 256 KiB, parser sin duplicados, tipos,
referencias, fases, topología y zonas realizables. Se conservan los modelos y
las funciones de normalización de v1/v2 y validación de mapas/zonas que usan los
lectores, sin motor jugable. GameSessionCodec.encode permanece porque la
migración 22→23 lo necesita; RaceCodec ya no tiene encoder ni consumidor activo.
Los PNG retirados siempre fueron recursos estáticos; no son bytes personales
necesarios para restaurar backups. Restaurar una partida no reactiva el Juego.

La restauración de un backup anterior a datos v15 conserva la semántica previa:
ausencia de partida y marcador vacío durable; no se introduce ninguna limpieza
automática adicional. El texto corrupto de la preferencia histórica no se borra.

## Pruebas afectadas

Se adaptan expectativas y callbacks en DrawerOrganizationTest,
CalendarNavigationBlockTwoTest, ExperimentalMenuPresentationTest,
VisualActionsNavigationTest y GlobalNavigationUiTest. Se retira únicamente el
caso de la pantalla del Juego de GlobalNavigationUiTest.

Pruebas jugables retiradas:

- `game/GameCorrectionTest.kt`
- `game/GameExpansionPersistenceTest.kt`
- `game/GameExpansionTest.kt`
- `game/GameExpansionUiTest.kt`
- `game/GameExtendedPersistenceTest.kt`
- `game/GameGameplayTest.kt`
- `game/GameRulesTest.kt`
- `game/GameSessionCodecTest.kt`
- `game/GameStateMigrationTest.kt`
- `game/GameUiTest.kt`
- `game/NativeRaceTest.kt`
- `game/RacePersistenceTest.kt`
- `game/RaceRulesTest.kt`

Se añaden cinco fixtures JSON sintéticos v1/v2/v3/v3 con tablero/v4 y
LegacyGameBackupTest. No usan motores eliminados para generar partidas.

## Validación focalizada

**9 pruebas distintas aprobadas.** Producción y todas las fuentes unitarias
compilan; la última ejecución termina con **BUILD SUCCESSFUL**.

- Seis casos de LegacyGameBackupTest: v1/v2/v3/v4 y mapa guardado, normalización
  histórica, copia portable cifrada y restauración con otra clave en almacén
  vacío, conservación de productividad, texto original y estados pendientes,
  corrupción/duplicados/tipos/referencias/topología/tamaño, rollback ante fallo
  SQL, migración idempotente y backups anteriores a datos v15.
- Un caso de DrawerOrganizationTest comprueba los 18 destinos actuales y sus
  callbacks originales, cierre antes de navegar, categorías independientes,
  ausencia de Juego y conservación de Tragaperras.
- Dos casos de JungleSlotsTest comprueban decodificación de sus 31 recursos,
  geometría y cambio de modos, sonido y disponibilidad de PAGO. Código y todos
  los recursos de Tragaperras permanecen idénticos al estado inicial.
- Comparación SHA-256 con el estado inicial: todos los archivos de producción
  ajenos al Juego, versión, Grupos 1–4, temas oficiales, fotografías, adjuntos,
  Metro, Pomodoro, audio y seguridad intactos. BackupRepository, BackupJson y
  Arachn0deDatabase no cambian. Sin consumidores del código o recursos retirados.
- mergeDebugAssets y mergeDebugResources correctos: assets restantes con bytes
  idénticos a fuentes, ningún asset game/ ni símbolo R de los 26 drawables
  eliminados. El JAR de producción tampoco incluye clases del paquete jugable.
  Comprobación focalizada de espacios correcta.

Se ejecutó :app:testDebugUnitTest con filtros para esos nueve casos y tareas de
fusión de recursos, offline. La orden inicial tuvo el filtro --tests asociado a
la tarea incorrecta y se detuvo antes de compilar. En la ejecución de pruebas
se corrigieron exclusivamente un fixture v3 incompleto y expectativas de menú
anteriores al estado inicial del proyecto. Se repitieron solo los cuatro casos
fallidos; no se repitieron los cinco aprobados ni se recompiló producción para
esas correcciones. No se relajó ninguna validación de producción.

Evidencia: `/tmp/group5a-validation.log`, `/tmp/group5a-focused.log`,
`/tmp/group5a-check.py`, XML inicial/final en `/tmp/group5a-results/` y lista
única en `/tmp/group5a-results/passed.txt`.

## Límites y pendientes

No se ejecutó una suite general ni auditoría, pruebas instrumentales en teléfono,
instalación o empaquetado de APK. Los fixtures usan Room en JVM y archivos
cifrados reales; no acreditan ejecución física de SQLCipher ni una restauración
en el teléfono. Pendiente comprobación manual de navegación y apertura de
Tragaperras en dispositivo. No hay fallos pendientes en los casos seleccionados.

Las APK y helpers históricos se conservan como evidencia de sus releases. Sus
comprobaciones de imágenes del Juego corresponden a esas fuentes históricas;
al preparar otra versión habrá que adaptar su nueva copia al inventario ya
retirado, manteniendo sus controles. No se cambia el helper en este grupo.
La reducción real de APK y el comportamiento en hardware quedan sin medir.
