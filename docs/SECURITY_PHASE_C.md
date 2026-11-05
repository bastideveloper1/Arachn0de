# Fase C de seguridad — cámara, recuperación y Pomodoro

## A. Resumen ejecutivo

SEG-04: la cámara externa se conserva. La fotografía devuelta se incorpora a un
adjunto privado cifrado **antes** de mostrar la confirmación. Solo después de
verificar el archivo completo se retira el JPEG externo. Confirmación y edición
utilizan el adjunto, sin exponer una URI de la bóveda a la cámara.

SEG-06: investigación terminada con conservación prudente. **No se implementa
eliminación automática**, futura ni retrospectiva: no hay prueba suficiente de
que todos los originales archivados estén representados en la copia lógica o en
los datos actuales. Esta es la condición de detención aprobada en la solicitud.
No se inspeccionaron ni eliminaron backups personales o históricos reales.

SEG-07: el checkpoint solo contiene información técnica del temporizador. Se
conservan mecanismo, notificaciones y funcionamiento con la bóveda bloqueada.
La rotación completa de claves queda aplazada, sin cambios criptográficos.

## B. SEG-04 — cámara

### Ciclo de vida

1. `TakePicture` solicita `ACTION_IMAGE_CAPTURE` con `EXTRA_OUTPUT`, mediante el
   proveedor separado `${applicationId}.photo-camera`, no exportado y con permisos
   temporales sobre una URI concreta. El destino es
   `cache/antisocial-camera/<UUID>.jpg`, fuera de la bóveda.
2. Una reserva durable `<UUID>.lease` identifica propietario (UUID de almacén),
   estado y proceso; no contiene títulos, personas, rutas absolutas ni claves.
3. El resultado satisfactorio revoca el acceso y registra `RETURNED`. Se aplica
   `PrivatePhoto.clean` y se importa por `AttachmentRepository.importGuestBytes`,
   con la reserva existente `camera-<UUID>` y el mutex común de archivos. Se conserva
   la deduplicación existente de fotografías y sus referencias compartidas.
4. El archivo privado usa `SecureFiles`, el mismo cifrado y formato de adjuntos.
   La fila confirmada en Room y su reserva durable se verifican mediante
   `verifyBackupAttachment`: autenticación completa, tamaño, SHA-256 y validación
   de contenido. Solo entonces se confirma `IMPORTED` y se elimina el JPEG.
5. La vista previa lee el adjunto cifrado. «Usar fotografía» transfiere una reserva
   a la galería o crea el avatar privado con los importadores existentes. No vuelve
   a utilizar el JPEG ni transmite rutas privadas a la cámara. La confirmación,
   botones, galerías y editores conservan su interfaz.
6. La reserva de cámara se libera tras una entrega satisfactoria. Un fallo del
   consumidor conserva la fuente para reintentar. Cancelar registra `DISCARD`,
   libera únicamente la reserva de cámara y realiza la limpieza habitual: las
   referencias confirmadas y las reservas de otros editores conservan el archivo.

Los cambios de estado reutilizan `DurableVaultFile` (staging, commit y recuperación),
sin crear otro protocolo criptográfico. La coordinación de importación/reintento
serializa las operaciones propias de cámara; la lectura o escritura externa no
mantiene ese mutex ni el bloqueo común de archivos.

### Interrupciones y limpieza

Se deja de interpretar un cambio de proceso como cancelación. `CAPTURING` sigue
reservado: una cámara externa puede seguir trabajando aunque Arachn0de haya muerto.
Las reservas no caducan. Las antiguas reservas que solo contienen un UUID de
proceso se leen de forma conservadora y no se reclasifican como descartables.
Los archivos desconocidos y los directorios ajenos no se barren.

`RETURNED` se reintenta al autenticar el almacén correspondiente, reutilizando una
importación ya confirmada si el proceso murió antes de actualizar la reserva.
`IMPORTED` conserva el adjunto y permite reintentar la retirada del JPEG; `DISCARD`
permite completar una cancelación interrumpida. Los fallos conservan el diario y
se reintentan al abrir otra sesión. Al volver a pulsar Cámara, una captura devuelta
o importada pendiente se ofrece en la confirmación existente antes de crear otra.
Una recreación con ID conservado vuelve a preparar su vista previa desde el adjunto.

Si Android pierde el resultado de una captura todavía `CAPTURING`, no es posible
probar que terminó ni eliminarla de manera segura. Sus bytes se conservan; no se
promete recuperar automáticamente un resultado que Android no entregó. Las
reservas históricas sin propietario también se conservan. Hay un límite de ocho
reservas nuevas con propietario para evitar acumulación ilimitada de nuevas
solicitudes; las reservas históricas ambiguas no consumen esa cuota. La lectura
mantiene el límite existente de 20 MiB; una cámara externa puede escribir más y
su salida se rechazará como fotografía, conservándola para cancelación/reintento.
No se garantiza un límite físico a las escrituras de esa otra aplicación.

La cancelación o eliminación usa unlink normal, sin afirmar sobrescritura segura
en flash. Revocar el permiso no retira bytes que la cámara ya leyó ni controla sus
archivos internos, copias o descriptores previamente abiertos.

### Backups

Las reservas y los JPEG no son datos confirmados: quedan fuera del snapshot lógico,
del backup Android/transferencia y, desde esta fase, del inventario de caché de la
migración inicial. No se borran al excluirlos. Los adjuntos reservados sin referencia
confirmada tampoco se exportan. Una fotografía aceptada y confirmada utiliza las
relaciones existentes: su archivo completo y metadatos sí se incluyen y restauran,
con integridad, reservas compartidas y eliminación después del commit.

## C. SEG-06 — backups automáticos históricos

### Inventario de mecanismos (por código, sin abrir datos personales)

| Mecanismo | Creación, ubicación y contenido | Protección y recuperación | Conservación actual |
| --- | --- | --- | --- |
| Recuperación inicial | `VaultManager.setup`, antes de activar el índice: `security-v1/stores/<UUID>/recovery.arachnode.enc`; snapshot lógico de la instalación de origen | Stream `SecureFiles`; `exportInitialRecovery` autentica la contraseña del almacén, valida el contenedor completo y exporta ANBACK01 con contraseña de backup | Se conserva; puede recuperarse aunque la base actual no abra |
| Originales de migración | `migration-original/{files,cache,databases,preferences}`; copia exacta de archivos inventariados, incluidos originales que el snapshot lógico no representa | Cifrado por archivo; inventario con tamaños/SHA-256, `source-cleanup.enc`; verificación antes de retirar cada origen tras activar el índice | Se conserva el archivo cifrado; no existe una clasificación durable de cuáles ya son prescindibles |
| Base de destino provisional | Copia SQLCipher de la base anterior durante la migración inicial; origen intacto hasta validación/activación | Verificación criptográfica, fingerprint tipado, integridad y claves foráneas; diario `pending` e índice durable | Staging de la operación, no un histórico manual |
| Backups manuales | Creación explícita y guardado por SAF; destino y nombre elegidos por el usuario | ANBACK01 con contraseña propia; contenedor/manifiestos y restauración transaccional | No se añade ninguna eliminación automática de estos destinos |
| Preparación de un backup manual | `cache/backups/backup-*.part`, `<UUID>.arachnode` y `.keep`; inspecciones/restauraciones con diarios | Cifrado de sesión y reserva existente hasta guardar/cancelar; recuperación propia | Temporales operativos, no equivalentes al documento manual de SAF |
| WAL/SHM, diarios y staging de restauración | Estado transaccional o recuperación de una operación | Protocolos existentes de Room y backup | No se reclasifican como backups históricos ni se barren |

Las migraciones Room posteriores se ejecutan en la cadena existente; **no hay un
backup lógico automático completo nuevo antes de cada upgrade**. La recuperación
inicial puede ser anterior a datos creados después. No se presenta como garantía
de recuperar hasta el último cambio tras cualquier futura migración.

### Política aprobada y motivo de detención

El mínimo seguro futuro sería una copia identificada por generación y operación,
validada/restaurada íntegramente antes de migrar, seguida de comparación completa
de datos, preferencias, relaciones y todos los archivos esperados, comprobaciones
de integridad y un commit durable. Solo un registro inequívoco de esa generación
permitiría marcar copias reemplazadas para una eliminación reintentable, sin tocar
copias manuales, desconocidas o necesarias para recuperar un fallo.

Actualmente se verifican la restauración de la copia y recuentos de algunas tablas,
además del fingerprint físico de la exportación SQLCipher. **Esto no demuestra que
el snapshot lógico sustituya todos los archivos de `migration-original`**, ni que
un estado actual íntegro conserve datos históricos que el usuario aún necesite.
Un nombre, antigüedad o apertura satisfactoria no resuelve esa ambigüedad.

Por la instrucción expresa «si existen dudas [...] detener la implementación de
eliminación automática», se detiene esa implementación. No se añade plazo, barrido,
marca retrospectiva, formato, ni código que elimine copias históricas. Una futura
política necesita primero esa prueba completa y clasificación por generación.
Los fallos de eliminación automática son inaplicables en esta fase porque no se
introduce dicha operación; no se afirma haber probado una política inexistente.

## D. SEG-07 — checkpoint de Pomodoro

### Datos reales

El mismo JSON, limitado a 4096 caracteres, se guarda como `checkpoint` en:

- `pomodoro_preferences`: preferencia de la bóveda dentro de `private_preferences`
  de Room, cifrada, exportada y restaurada con la bóveda.
- `pomodoro_clock_cache`: copia operativa en SharedPreferences de la aplicación,
  `MODE_PRIVATE`, normalmente `shared_prefs/pomodoro_clock_cache.xml`, dentro del
  sandbox. Permite alarmas y recuperación con la bóveda bloqueada.

Permisos observados en este AVD: raíz 0700 (UID/GID 10230/10230), cámara 2700
(UID/GID 10230/20230) y XML del checkpoint 0660 (UID/GID 10230/10230). Los
identificadores son propios de esta instalación, no valores universales. El
sandbox y la raíz privada restringen el acceso de otros UID; 0660 no se presenta
como cifrado. Evidencia: `permissions.txt`. El proveedor solo concede la URI del
JPEG, no las preferencias ni sus directorios padres.

| Campo | Tipo / ejemplo sintético | Significado |
| --- | --- | --- |
| `version` | entero 1, 2 o 3 | Versión interna; 1 no tiene apariencia; 3 añade `manualEnd` |
| `revision` | Long, 12 | Secuencia local, no identificador de tarea |
| `config.focus`, `short`, `long` | Int, 25/5/15 | Minutos, 1–180 |
| `config.sessions` | Int, 4 | Sesiones por ciclo, 1–12 |
| `config.ticking`, `sound` | Boolean | Preferencias técnicas |
| `config.appearance` | String, `SOLAR` | Enum de apariencia desde versión 2 |
| `session` | objeto o null | Fase técnica actual |
| `session.phase` | String, `FOCUS`, `SHORT_BREAK`, `LONG_BREAK` | Trabajo/descanso |
| `session.status` | String, `READY`, `RUNNING`, `PAUSED`, `FINISHED` | Estado |
| `session.number`, `cycle` | Int, 1/4 | Posición del ciclo |
| `session.total`, `remaining` | Long, 1500000/900000 | Milisegundos |
| `session.wall` | Long, 1800000000000 | Ancla de reloj civil |
| `session.elapsed` | Long, 100000 | Ancla monotónica local |
| `session.boot` | Int, 7 | Contador de reinicio Android |
| `session.manualEnd` | Boolean, true | Solo versión 3, fase detenida al llegar manualmente a cero |

El decodificador exige campos, tipos, enums y rangos exactos. No hay IDs de tareas,
títulos, notas, rutas, personas, claves, tokens ni capacidades de acceso a la bóveda.
El checkpoint no sirve para desbloquear ni reconstruir material criptográfico.

### Ciclo de vida y decisión

Se sobrescribe un único checkpoint al cambiar de estado; no hay histórico ni
escrituras por segundo. Al finalizar se conserva el estado técnico FINISHED con
restante cero. El reloj monotónico se usa dentro del mismo arranque y el reloj
civil permite recuperación tras reinicio; una fase vencida deja la siguiente lista,
sin iniciar trabajo automáticamente. Autenticar/restaurar hace prevalecer el
checkpoint privado sobre la copia operativa. No se añaden cifrado ni servicios.

El cache operativo no se añade al snapshot normal de una bóveda. Matiz histórico:
la migración inicial captura todos los namespaces XML de la instalación anterior;
si allí existía `pomodoro_clock_cache`, puede conservarse como preferencia histórica
cifrada o dentro del archivo de originales. No se elimina ni filtra retrospectivamente.
Android backup/transferencia están excluidos para toda la aplicación.

Riesgo residual aceptado: las fechas, duración y estado permiten inferir hábitos
si alguien obtiene acceso al sandbox o a una extracción del dispositivo. Eso no
se equipara a exposición de contenido privado o credenciales. Se conserva el
mecanismo aprobado, las notificaciones y el uso en segundo plano.

## E. Rotación de claves

Cambiar la contraseña valida la anterior y **reenvuelve la misma clave de datos**
con la protección existente. No vuelve a cifrar cada fila y archivo. Argon2id,
AES-GCM, HKDF y SQLCipher, sus formatos y las claves actuales no se modifican.

Una rotación completa sería una operación excepcional ante compromiso de la clave
de datos o una exigencia específica. Requiere recifrar base y archivos como una
sola generación verificable, con staging durable, diarios, verificación completa,
rollback y publicación transaccional. Interrumpir un recifrado sin ese protocolo
puede dejar claves mezcladas, adjuntos inaccesibles o perder la única copia viable.

Los backups portables tienen contraseña y material propios; cambiar la contraseña
de la bóveda no invalida copias anteriores. Rotar tampoco retira datos ya extraídos.
Los futuros dispositivos sincronizados necesitarían generaciones de clave,
autenticación y recuperación coordinadas. No se implementan rotación, sincronización
ni opciones visuales nuevas en esta fase.

## F. Cambios implementados

- `CameraCaptureFiles.kt`: estados durables, propietario, conservación de solicitudes
  no resueltas, límite de reservas y recuperación sin barridos por edad/proceso.
- `CameraPhotoImport.kt`: importación y verificación de adjuntos cifrados, reintento
  idempotente y cancelación mediante reservas existentes.
- `AttachmentRepository.kt`: permite reutilizar internamente la deduplicación
  existente, sin cambiar su algoritmo ni los demás importadores.
- `PrivatePhotoCamera.kt`: confirmación desde el adjunto privado y recuperación de
  su vista previa; no conserva JPEG para editar.
- `PeopleScreen.kt`, `PersonProfileModule.kt`, `PersonMemoryGallery.kt` y
  `PersonActions.kt`: entrega privada al editor y liberación solo cuando el
  consumidor informa éxito. Sin cambios visuales.
- `Arachn0deApplication.kt`: recuperación de capturas devueltas en el scope existente
  del almacén autenticado.
- `VaultManager.kt`: únicamente excluye `antisocial-camera` del inventario de caché
  de la migración inicial. No cambia bloqueo, contraseñas ni algoritmos.
- Pruebas de fotografía, nuevo `CameraPhotoImportTest`, pruebas instrumentadas de
  fase C y cámara sintética del APK de tests; contratos en arquitectura/backup.

## G. Pruebas, evidencias y limitaciones

Pruebas exclusivamente sintéticas, sin bóvedas ni backups personales. AVD nuevo
`PhaseCSynthetic`, Android 17/API 37, x86_64 con páginas de 16 KiB, SDK ya instalado,
sin descargar dependencias. La cámara del APK de tests ejecuta Java de Android
bajo un UID diferente. Se concedieron los permisos CAMERA/POST_NOTIFICATIONS en
ese dispositivo desechable. No se alteraron los permisos de un dispositivo real.

- **26/26 pruebas JVM dirigidas**, Robolectric API 28: nueve de importación de cámara,
  seis de fotografía, seis de Pomodoro, una de backup Pomodoro y cuatro de
  migración de archivos/diarios. Incluyen fallo antes de conceder la URI,
  autenticación fallida sin retirar la foto, borrado fallido y reintento,
  rechazo de importación/cancelación por otro almacén, cuota sin penalizar reservas
  históricas, deduplicación de confirmadas y aislamiento de editores no confirmados.
- **5/5 pruebas instrumentadas principales**: cámara por UID externo, revocación del
  permiso, avatar desde adjunto cifrado, galería/backup/restauración; base actual
  corrupta con recuperación inicial y conservación de archivos manuales,
  desconocidos y múltiples archivos de procedencia simulada; migración nativa,
  interrupción antes de activación y reintento; fingerprint de filas tipadas y
  rechazo de clave incorrecta. Los nombres simulados no se presentan como prueba
  de un clasificador de backups automáticos: no se implementa ese clasificador.
- **4 métodos instrumentados de recuperación** en ejecuciones separadas: preparación
  de captura, force-stop del proceso, recuperación con PID distinto y conservación
  de otra captura todavía CAPTURING; preparación de Pomodoro, reinicio real del
  AVD, observación del aviso 917 desde el host antes de instrumentar y recuperación
  del temporizador con contador de arranque distinto y bóveda bloqueada.

Las pruebas nativas suman nueve métodos distintos; no se declara aprobada toda la
suite del repositorio. El harness de interrupción/reinicio y su init script quedan
en `/tmp/arachnode-phase-c/native-src` y `native.gradle`; `check_boot.py` comprueba
la notificación real sin inyectar un broadcast de arranque. Las pruebas de cámara,
backup y UID externo permanecen también en el árbol `androidTest` del proyecto.

Evidencias: `/tmp/arachnode-phase-c/verified-complete-build.log`, XML en `jvm/`,
`native-instrumentation.txt`, `camera-before-interruption.txt`,
`camera-after-interruption.txt`, `pomodoro-before-reboot.txt`,
`pomodoro-after-reboot.txt`, `boot-check.log`, `boot-notification-result.json`,
`notifications-after-real-boot.txt`, `pomodoro-checkpoint-synthetic.xml` e inventario
`verification.json`.

Se conservan los fallos intermedios: asociación de FileProvider con el sandbox
Robolectric (se adjunta al contexto real de cada test), alias Android `/data/data`
(se canoniza primero la raíz), permiso CAMERA aún no concedido y runtime Kotlin
no disponible bajo el UID aislado del APK de tests (cámara sintética en Java).
La comprobación inmediata del aviso tras el primer reinicio no pasó. La repetición
parte de un lanzamiento normal y espera la entrega asíncrona; verifica tanto el
aviso real como el estado, sin cambiar Pomodoro ni retirar la aserción. No se afirma
una causa única para aquella observación inicial ni se cuentan fallos como éxitos.
Las pruebas JVM dirigidas cubren captura, errores, idempotencia, corrupción,
referencias compartidas, avatar desde fuente cifrada, backup/restauración,
Pomodoro, migración de archivos y diarios durables. La prueba original de cámara
se conserva y ahora exige preservar una reserva cuya cancelación no está probada.

No se ensaya con cámaras comerciales ni con fotografías reales. La cámara de tests
no demuestra el comportamiento de todas las aplicaciones externas. No se afirma
borrado forense, control de los residuos de otra aplicación ni cobertura de todos
los puntos posibles de caída o pérdida eléctrica.

## H. Riesgos residuales

- Salida externa sin cifrar mientras se captura y hasta su importación verificada.
- Solicitudes cuyo resultado se pierde y reservas históricas ambiguas se conservan;
  no hay caducidad que pueda borrar una fotografía pendiente.
- Captura inválida o demasiado grande puede requerir cancelación explícita.
- Archivos automáticos históricos conservados por falta de prueba de prescindibilidad.
- Checkpoint técnico en el sandbox y limitaciones de los avisos inexactos de Android.
  Android puede diferir el broadcast de arranque para una aplicación restringida
  en segundo plano; esta fase no evita restricciones del sistema.
  [Documentación oficial de Android](https://developer.android.com/topic/performance/background-optimization).
- Rotación completa aplazada por decisión expresa; no sustituye revocación de
  sesiones ni recupera datos que un atacante ya haya copiado.

## I. Compatibilidad

Se mantienen datos confirmados y relaciones, exportación/restauración históricas,
funcionamiento offline, cámara externa, edición, Pomodoro y notificaciones.
No cambian el formato exterior del backup ni el checkpoint 1–3. Las nuevas reservas
de cámara son estado operativo local excluido; no añaden tablas o columnas.
No se tocan Banco, Ahorro, Tragaperras, política de bloqueo, modo Manual o recientes.

## J. Estado del repositorio

Se preservan los cambios previos mediante inventario SHA-256 al comenzar la fase.
No hay cambios de versiones/dependencias, APK release, commits, push, tags ni
publicación. Se generan únicamente artefactos debug para verificar en un AVD nuevo.
`assembleDebug`, `assembleDebugAndroidTest`, las pruebas dirigidas y `git diff --check`
pasan. No se abren archivos históricos reales para decidir qué borrar.

No se extrajeron bases privadas, backups ni claves del AVD; únicamente registros
y metadatos de pruebas y el checkpoint técnico sintético. El AVD propio y el
servidor ADB independiente (5048) se detuvieron y se eliminó el almacenamiento
del dispositivo sintético. Las evidencias se conservan en `/tmp/arachnode-phase-c`.

## K. Recomendación de cierre

La fase C puede cerrarse en el alcance aprobado: SEG-04 mitigado y verificado,
SEG-07 aceptado sin cambios innecesarios, rotación aplazada y SEG-06 con conservación
prudente. La eliminación automática de históricos queda detenida por la condición
de seguridad expresamente aprobada; no se solicita eliminar ninguna copia ni hace
falta otra decisión para conservarlas. Implantar su depuración exigiría una tarea
futura que pruebe prescindibilidad y recuperación completas.

Las capturas cuyo resultado Android no entregó y los residuos privados de otras
cámaras siguen siendo límites explícitos, no garantías de borrado. No se continúa
con la auditoría de rendimiento.
