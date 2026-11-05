# SEG-05 — cierre criptográfico y tarjetas compartidas

## Resultado y alcance

Corrección del hallazgo medio documentado en [la fase B](SECURITY_PHASE_B.md).
La entrega de una tarjeta deja de mantener el mutex común de archivos durante
escrituras a otra aplicación. El lector externo no conserva disponible la clave
principal ni impide la revocación. Se mantienen el modo Manual, la configuración
de autobloqueo, el comportamiento en recientes y las preferencias de protección
visual. No se modificaron primitivas criptográficas, contraseñas, SQLCipher,
versiones, dependencias ni formatos de backup. El esquema Room tampoco cambia.

## Investigación y decisión

El proveedor `ReportFileProvider.cardPipe` autenticaba y copiaba al pipe dentro
de `AttachmentRepository.fileOperations`. Un pipe lleno retenía ese mutex;
`VaultManager.lock` esperaba adquirirlo antes de cerrar `SecureFiles.Session`.
Por eso la sesión antigua todavía admitía `withKey` mientras esperaba. Los otros
proveedores ya separaban la preparación privada de la entrega externa y sirvieron
como referencia. Sus implementaciones no se sustituyeron.

Se evaluaron estas alternativas:

- Mantener el mutex y añadir un timeout: limita la espera, pero conserva su causa.
- Mantener un stream descifrador abierto: requiere prolongar recursos y reservas
  del archivo original y complica la revocación.
- Reutilizar staging cifrado A.1: viable, pero añade E/S, claves y limpieza para
  tarjetas cuyo tamaño ya está acotado.
- Instantánea autenticada en RAM y pipe no bloqueante: seleccionada por su menor
  ciclo de vida y por no introducir persistencia ni conservar el archivo fuente.

## Implementación

`CardShareDelivery` autentica y carga un único buffer de tamaño exacto bajo el
mutex común. Comprueba integridad también durante la copia; no publica bytes si
falla la autenticación. Después libera el mutex y entrega mediante `Os.poll`
(hasta 100 ms) y escrituras no bloqueantes de hasta 4096 bytes. El archivo cifrado
puede descartarse mientras se consume la instantánea. No se crean temporales en
texto claro ni un segundo formato cifrado.

Se admiten hasta cuatro entregas y 64 MiB agregados. El buffer se borra y su cuota
se libera al terminar, cancelar o fallar. Sesenta segundos sin escrituras con
progreso cancelan únicamente la entrega; no bloquean la bóveda. El escritor cierra
su propio descriptor para evitar carreras de reutilización de descriptores entre
hilos. El cierre no espera ese plazo ni al destinatario.

Al comenzar el bloqueo se desactiva el perfil para denegar nuevas URI. El proveedor
comprueba admisión antes de esperar el mutex y vuelve a comprobarla dentro.
Se conserva la finalización local de audio que protege los borradores existentes.
Después `Session.beginClosing` revoca nuevas adquisiciones y borra la clave antes
de esperar cancelaciones y el mutex de limpieza. `retain` y la revocación comparten
monitor; los recursos se cierran una sola vez fuera de ese monitor. El proveedor
de adjuntos también comprueba sesión abierta y perfil activo antes de consultar
sus datos. No se añadió un timeout al cierre de la bóveda.

La prueba adicional con actividad visible detectó una carrera preexistente entre
consultas de la UI y cierre de SQLCipher (`connection pool has been closed`). Se
reprodujo también restaurando únicamente el `VaultManager.lock` anterior, con la
misma instantánea desacoplada y los mismos datos sintéticos. Se conserva la prueba
ejecutable. Para cerrar sin esa carrera, el contexto de consultas Room pertenece
al `VaultContext`; el bloqueo cancela y espera ese trabajo antes de cerrar la
base. La admisión criptográfica ya está revocada durante esta espera local. Una
consulta iniciada que alcanza el hook de apertura tras revocarse la sesión recibe
cancelación, no un error no controlado de UI; se mantiene el rechazo de apertura.
No se modifican tablas, DAOs ni datos. El contexto también se cancela al liberar memoria.

Los bytes ya entregados al kernel o al destinatario no pueden revocarse. Borrar
buffers es una medida de ciclo de vida; las pruebas no constituyen una demostración
forense de ausencia de copias en toda la memoria del proceso.

## Verificación

Entorno: AVD desechable propio, Android 17/API 37, x86_64, páginas de 16 KiB;
datos, contraseña, imágenes y backups sintéticos, sin usar la bóveda personal.

- **16/16 pruebas JVM dirigidas**, incluyendo revocación antes de limpieza,
  100 carreras entre reserva y cierre, cuotas, corrupción entre las dos lecturas,
  recuperación de cuota, metadatos, rutas, permisos y generación de tarjetas.
- **6/6 pruebas instrumentadas permanentes** en `CardShareSessionTest`: lectores
  completo, sin lectura, lento, parcial y cerrado; mutex disponible; cierre antes
  de 1500 ms manteniendo los lectores abiertos; clave y nuevas reservas denegadas;
  salida del escritor; rechazo de una nueva URI antes de liberar un mutex ocupado;
  corrupción sin entrega; descarte de la fuente durante lectura; modo Manual y
  recientes con actividad real y `FLAG_SECURE`; cierre con actividad visible.
  La expiración de entrega usa reloj inyectado, sin esperar 60 segundos reales.
- **6/6 pruebas instrumentadas complementarias** del harness de fase B:
  SQLCipher/auxiliares, 15 escenarios de los tres proveedores, políticas y sesiones
  antiguas, autenticación/restauración de backup, staging/cancelación/reservas y
  revisión de archivos. Se adaptaron contraseña y marcador del entorno
  desechable. Sus dos preparaciones directas de adjuntos se envolvieron en el
  mutex común, igual que el repositorio real: sin él, la limpieza automática podía
  retirar un `.part` entre escrituras y validación. No se retiraron aserciones.
  Se repitieron con la implementación final.

Mediciones de cierre en la ejecución final (tarjeta sintética de aproximadamente
1,8 MiB, lector mantenido abierto durante cierre, revocación y salida del escritor):

| Destinatario | Fase B anterior | Corrección final |
| --- | ---: | ---: |
| Completo | — | 63 ms |
| Sin lectura | 1559 ms; clave antigua aún usable a los 1500 ms | 42 ms; revocada |
| Lento, 512 bytes/50 ms | 1546 ms; clave antigua aún usable a los 1500 ms | 45 ms; revocada |
| Parcial y cierra | — | 45 ms |
| Cierra sin leer | — | 47 ms |

Son mediciones de este entorno, no un límite universal de latencia. Además del
cierre completo, se verifica que la sesión deniegue claves y una nueva URI incluso
mientras otro propietario mantiene artificialmente ocupado el mutex de archivos.

Las aserciones de lectura real del pipe se ejecutan nativamente: Robolectric no
implementa `Os.poll`. Se conservaron sus comprobaciones de metadatos y permisos.
Una ejecución auxiliar de `SecurityMigrationTest` encontró una expectativa de
esquema 27 frente a 53; ese test y el esquema no fueron modificados en esta tarea.
No se declara aprobada toda la suite del repositorio ni se verificó un dispositivo
físico o todos los niveles de API.

Evidencias locales: `/tmp/arachnode-seg05/accepted-build.log`,
`complete-final-build.log`, `final-instrumentation.txt`, `final-events.jsonl`,
`regression-instrumentation.txt`, `regression-events.jsonl` y `verification.json`.
El harness complementario queda en `regression-src/` del mismo directorio.
La comparación del fallo de UI queda en `baseline-lock-build.log`,
`baseline-lock-visible.txt`, `ui-race-before-drain.txt` y
`ui-race-first-drain.txt`. El fallo de preparación del harness se conserva en
`regression-fixture-race.txt`; ninguna de estas ejecuciones fallidas se cuenta
como aprobada.

## Persistencia y entrega

No se incorporan datos que exportar o restaurar. La caché cifrada existente conserva
su exclusión regenerable, eliminación durable y recuperación; la instantánea no
reserva el archivo tras prepararlo. Se documentó el contrato en `ARCHITECTURE.md`
y `BACKUP_FORMAT.md`. No se alteró el flujo independiente de guardar mediante SAF.
Se generaron APK de depuración para verificar; no se realizaron commits, push,
tags, publicaciones ni cambios de versión.

Archivos de implementación: `ReportFileProvider.kt`, el nuevo
`CardShareDelivery.kt`, `SecureFiles.kt`, `VaultManager.kt`, `VaultContext.kt`,
la configuración del contexto de consultas en `Arachn0deDatabase.kt` y la admisión
en `AttachmentExportProvider.kt`. `CardShareFiles.kt` solo actualiza comentarios
sobre reservas. Las pruebas quedan en `SecureFilesTest.kt`,
`CardShareDeliveryTest.kt`, `CardShareFilesTest.kt` y `CardShareSessionTest.kt`.

Al terminar se detuvieron el AVD sintético y su servidor ADB independiente (5047),
y se eliminaron el AVD y el caché Gradle aislado. Se conservaron fuentes del harness,
registros, métricas y XML de pruebas en `/tmp/arachnode-seg05`; no se extrajo la base
privada ni el backup sintético del emulador.
