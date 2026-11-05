# Arachn0de — Fase B: auditoría dinámica de seguridad

Fecha: 2026-10-26. Alcance: árbol de trabajo presente al iniciar B, conservando
los cambios previos de A/A.1 y del proyecto. Esta fase no implementa correcciones.

## A. Resumen ejecutivo

**SEG-05 se reprodujo como problema de disponibilidad en el proveedor de tarjetas.**
Un destinatario que abre una tarjeta y no consume, o consume lentamente, mantiene
el mutex común y retrasa el cierre criptográfico. En ambos casos el cierre seguía
pendiente a los 1500 ms y la sesión anterior todavía aceptaba `withKey`; se liberó
al cerrar el extremo lector controlado. No se imprimió el contenido de ninguna
clave. La UI ya había perdido la sesión y las nuevas URI estaban denegadas: no se
demostró un bypass de autenticación ni exfiltración nueva después del bloqueo.

En los escenarios ejecutados se verificaron SQLCipher real, rechazo de apertura
sin clave/con clave incorrecta, protección de contenido de WAL, cierre normal y
revocación de referencias, integridad de backups, aislamiento entre dos almacenes,
protección visual de Activity/visor/recientes, exportaciones y recuperación tras
interrupciones. Esto **no constituye certificación de toda la aplicación**.

Se completaron **14 métodos instrumentados distintos con resultados válidos**,
que incluyen 15 escenarios de consumidores y tres recuperaciones tras terminación
real del proceso. Que el harness termine correctamente no significa que todas las
protecciones hayan pasado: el método de compartición registra precisamente SEG-05.
Las tres preparaciones detenidas deliberadamente no se cuentan como tests aprobados.
No se repitieron las 64 pruebas JVM de A.1 ni se suman a esta cifra.

Hay una observación de política: en recientes, Android mantuvo la Activity en
`RESUMED`, `backgrounded=false` y la sesión abierta con timeout configurado en 0.
La miniatura estaba negra. Inicio sí provocó bloqueo y reapertura protegida.
No se presenta esa diferencia de ciclo de vida como vulnerabilidad confirmada.

**No se modificó ni corrigió código de producción.**

## B. Entorno

- AVD nuevo `BSynthetic`, en `/tmp/arachnode-phase-b-emulator`; se leyó únicamente la configuración del Pixel_7a para reutilizar el dispositivo/imagen. No se abrió su userdata.
- Android **17**, API **37**, páginas de **16384 bytes**, imagen instalada x86_64 y KVM. Datos nuevos y dos bóvedas exclusivamente sintéticas.
- SQLCipher observado: **4.19.0 community**. `cipher_memory_security=1`, `temp_store=2`, `integrity_check=ok`.
- Android SDK/Emulator existentes, Gradle wrapper 9.6 offline, AndroidJUnitRunner, ActivityScenario, UiAutomation, ContentResolver y pipes reales, PdfRenderer y MediaMetadataRetriever.
- Pillow, ffmpeg/ffprobe y herramientas X11 ya instaladas. No se instalaron ni descargaron componentes/dependencias.
- Proxy/DNS de arranque sin salida útil y modo avión antes de cargar datos sintéticos; ADB aislado en puerto 5047, emulador 5580/5581. Se aceptó el diálogo USB normal de ese AVD; no se deshabilitó su autenticación ni las protecciones de la app.
- Bóveda con Núcleo, Capa, ocho Tareas, Nota, descripciones extensas, etiqueta, adjuntos PNG sintéticos y una obligación ficticia en una Tarea (12345 CLP). No se usaron cuentas ni operaciones bancarias reales.

Evidencia: [entorno](/tmp/arachnode-phase-b/device.txt),
[eventos y mediciones](/tmp/arachnode-phase-b/events.jsonl),
[inventario de tests](/tmp/arachnode-phase-b/test-index.json),
[comparación de fuentes](/tmp/arachnode-phase-b/source-verification.json).
No se extrajeron bases, backups privados ni claves del AVD; solo métricas,
capturas sintéticas y salidas públicas deliberadas.

## C. Matriz de pruebas

La confianza describe únicamente el escenario observado. “Alta” no extiende el
resultado a otras API, dispositivos, formatos o puntos de interrupción.

| ID | Protección | Resultado | Evidencia | Confianza |
|---|---|---|---|---|
| B-SQL-01 | SQLCipher, WAL, SHM, checkpoint y reapertura | Protección verificada en contenido y aperturas negativas; SHM sin canarios, no se afirma que toda su estructura esté cifrada | `SQL_*`, `priority-tests.txt`, método `aSqlCipherAndAuxiliaryFiles` | Alta |
| B-SHARE-01 | Compartición y cierre | SEG-05 confirmado en card/unread y card/slow; no reproducido en report/attachment | 15 eventos `SHARE` y `SHARE_RECOVERY` | Alta |
| B-LOCK-01 | Revocación de recursos y autobloqueo | DB, stream y ruta antiguos rechazados; timeout 0 bloquea; Manual conserva sesión como se espera | `LOCK`, método `cStaleResourcesAndAutomaticLock` | Alta |
| B-BACKUP-01 | Confidencialidad, autenticidad e integridad | Sin contraseña, contraseña errónea y alteración rechazadas; datos/hashes y política local restaurados | `BACKUP`, `backup-staging-tests.txt`, `final-capture-backup-formats.txt` | Alta |
| B-TEMP-01 | Staging cifrado, reservas, cancelación y errores | Round-trip autenticado, canario ausente del ciphertext, reserva viva preservada y exclusión del snapshot activo; limpieza tras error/cancelación | `STAGING`, `final-active-stage.txt` | Alta |
| B-DISK-01 | Copias SQLite y residuos en almacenamiento | Tres archivos .db rechazan SQLite convencional; copia inicial envuelta en cifrado; sin canarios legibles en 57 archivos | `DISK_INVENTORY`, `final-inventory.txt` | Alta para ese inventario |
| B-CRASH-01 | Muerte durante staging y transacción SQL | Staging/cámara recuperados; INSERT sin commit revertido; Nota confirmada e integridad preservadas | `crash-stage-*`, `CRASH_STAGE_RECOVERED` | Alta en esa barrera |
| B-CRASH-02 | Muerte durante backup por streaming | Parcial cifrado rechazado; datos originales intactos; retención y limpieza por edad esperadas | `crash-backup-*`, `CRASH_BACKUP_RECOVERED` | Alta en esa barrera |
| B-CRASH-03 | Muerte durante restauración antes del commit | Nota anterior preservada, archivos referenciados completos y staging/diario retirados | `crash-restore-*`, `CRASH_RESTORE_RECOVERED` | Alta en esa barrera |
| B-EXPORT-01 | DOCX y ZIP Markdown/JSON | Medio visible negro, sin original oculto/metadatos; CRC/XML, hashes y tamaños correctos; ciphertext privado intacto | `EXPORT_DOCX_ZIP`, `public-evidence/public.docx` | Alta |
| B-ISO-01 | Dos almacenes independientes | Secundario sin filas del principal; URI/ruta/backup principal y permiso de capturas rechazados | `ISOLATION`, `ui-export-isolation-tests.txt` | Alta |
| B-CAPTURE-01 | FLAG_SECURE, visor, pantalla bloqueada, vídeo y recientes | Ventanas protegidas y capturas negras; control autorizado visible; Inicio bloquea; recientes conserva RESUMED | `CAPTURES`, `RECENTS_STATE`, `final-lifecycle-capture.txt`, imágenes/vídeo | Alta para esas ventanas |
| B-PDF-01 | PDF nativo | Representación negra visible, ningún píxel del original rojo recuperable en la página | `PublicExportPdfTest`, `ui-export-isolation-tests.txt` | Alta |
| B-FORMATS-01 | PNG/JPEG/GIF/WebP/APNG | Preparación/render nativos y fallos seguros; WebP/GIF animados decodificables | Test existente `androidDecoderAcceptsSanitizedAnimationsAndCorruptionFailsClosed`, `final-capture-backup-formats.txt` | Alta para esas fixtures |

## D. Hallazgos

### B-01 / SEG-05 — consumidor de tarjeta retrasa el cierre criptográfico

- **Gravedad:** media, disponibilidad y prolongación de la vida de la clave en memoria.
- **Estado:** problema de disponibilidad reproducible y confirmado; no corregido.
- **Descripción:** `ReportFileProvider.cardPipe` mantiene `AttachmentRepository.fileOperations` mientras escribe al pipe. La escritura se bloquea por contrapresión del destinatario. `VaultManager.lock` espera ese mismo mutex antes de `session.close()`.
- **Impacto observado:** el mutex no se obtiene en 350 ms; `lock()` no termina en 1500 ms; `current` ya es null pero el `SecureFiles.Session` anterior todavía acepta acceso a su clave. Al cerrar el lector, el cierre termina y el acceso se revoca. Importación/backup y otras operaciones que necesiten el mismo mutex también dependen de su liberación; esto último se infiere del mutex compartido, no se midió su latencia en paralelo.
- **Condiciones:** destinatario autorizado con tarjeta suficientemente grande y descriptor abierto sin drenar el pipe. Se usó PNG de 1822727 bytes; consumidor controlado por el harness, sin app externa desconocida.
- **Reproducción:** crear tarjeta cifrada con nombre válido en `cache/card-shares`; abrir URI `/reports/v1/<store>/arachn0de-card-<uuid>.png`; no leer o leer 512 B/50 ms; esperar que el escritor tome el mutex; llamar `lock` en un hilo independiente; comprobar timeout, estado del acceso anterior y denegación de URI nueva; cerrar lector; comprobar fin del cierre y revocación.
- **Evidencia:** tabla de F, stacks `private-card-share` y booleanos en `events.jsonl`. No se volcaron claves; `withKey` devuelve solo si quedaba una clave no nula/no borrada.
- **Límite:** se impuso un máximo de espera y se cerró el consumidor; no se dejó una prueba esperando indefinidamente. Se demostró dependencia del lector y retraso mayor al límite, no una duración infinita medida.
- **Recomendación:** preparar/autenticar bajo reserva y liberar el mutex antes de depender del consumidor, conservando referencias y reservas del contrato. Vincular la entrega al cierre de sesión con cancelación/aborto acotados y prueba de regresión con lector retenido. No basta con quitar el mutex sin proteger el ciclo de vida de archivos.

No se confirmó acceso externo nuevo al contenido después del bloqueo: en los
15 escenarios las URI nuevas fueron denegadas. Los bytes que ya recibió un
consumidor autorizado no pueden revocarse retroactivamente.

### B-OBS-02 — recientes conserva Activity RESUMED y sesión abierta

- **Gravedad:** informativa; posible diferencia entre expectativa de usuario y política de pérdida de foco.
- **Estado:** comportamiento observado, no vulnerabilidad confirmada.
- **Descripción:** con autobloqueo 0, abrir recientes mantuvo `RESUMED`, `backgrounded=false` y sesión abierta. La miniatura estaba negra. Enviar a Inicio sí cerró la sesión y la reapertura mostró una ventana protegida.
- **Condiciones/reproducción:** abrir MainActivity sintética, configurar solo en esa bóveda timeout 0, abrir recientes, registrar estado/flags; enviar Inicio y esperar cierre.
- **Impacto:** recientes no garantiza cierre criptográfico si Android no notifica el paso a segundo plano que utiliza la implementación. No se demostró fuga visual ni bypass de autorización por este hecho.
- **Evidencia:** `RECENTS_STATE` y `CAPTURES`, más `public-evidence/recents.png`.
- **Recomendación:** decidir/documentar si “Inmediato” debe incluir recientes/pérdida de foco aunque Android mantenga RESUMED. Si se exige, preparar una corrección separada y pruebas para diálogos, permisos, cámara y otras transiciones legítimas. No se cambió el ciclo de vida durante esta auditoría.

### Observaciones que no se clasifican como vulnerabilidades

- SHM de 32768 B presente durante WAL: no contenía los canarios; su existencia no demuestra exposición de filas ni se afirma cifrado total de sus metadatos.
- La tercera `.db` era `migration-original/databases/arachn0de.db`, con envoltura de cifrado de archivos; no una copia SQLite legible. No abrió como SQLite convencional.
- El parcial de backup cifrado reciente se conservó menos de 24 h como establece la política; al simular edad mayor se retiró.
- La cámara externa necesita salida transitoria sin cifrar en el sandbox Android y con permisos URI. Se simuló esa salida en el directorio privado y se comprobó su liberación al reiniciar; no se declara insegura únicamente por existir.
- El control de captura fue autorizado expresamente mediante la API existente, con contraseña sintética y sin persistir el permiso. Se volvió a bloquear antes del resto de comprobaciones. No es una fuga.

## E. SQLCipher

La sesión autenticada insertó y leyó diez nodos con canarios únicos; realizó
actualizaciones, checkpoint FULL, cierre y reapertura. `journal_mode=wal`.

| Archivo/estado | Bytes | Resultado observado |
|---|---:|---|
| Principal abierto, antes de checkpoint | 1036288 | Sin canario UTF-8/UTF-16; SQLite sin clave y SQLCipher con clave aleatoria rechazados |
| WAL abierto | 585072 | Sin canarios; 142 páginas reportadas al checkpoint |
| SHM abierto | 32768 | Sin canarios legibles; no se equipara a cifrado de todos sus campos |
| Principal tras checkpoint/cierre | 1163264 | Sin canarios; reapertura autenticada conserva diez nodos |
| WAL/SHM después de cierre | Ausentes | Se retiraron al cerrar la base |
| Rollback journal | No observado | No se verificó su cifrado en un modo de journal diferente |

Checkpoint: busy=0, 142/142 páginas. Pragma de integridad `ok`, almacenamiento
temporal `MEMORY` (2), seguridad de memoria SQLCipher activada (1). El inventario
final encontró dos bases vivas y una copia inicial con envoltura cifrada; ninguna
abrió como SQLite convencional y no existía `databases/arachn0de.db` legado fuera
de los almacenes. El escaneo de cadenas es evidencia complementaria, no la prueba
única de cifrado. No se auditaron todos los tamaños de sort/temp spill ni modos
SQLite posibles.

## F. SEG-05: mediciones

Tiempo en milisegundos. `lock total` en los casos bloqueados incluye el momento
en que el test cerró deliberadamente el lector tras detectar el límite.
El proveedor responde a `openFile` entregando el descriptor; esa respuesta rápida
no significa que el escritor haya terminado.

| Proveedor | Consumidor | openFile | lock total | Mutex ocupado >350 ms | Clave anterior utilizable al vencer 1500 ms |
|---|---|---:|---:|---|---|
| card | complete | 4 | 38 | No | No |
| card | unread | 1 | 1559 | Sí | Sí |
| card | slow | 1 | 1546 | Sí | Sí |
| card | partial | 2 | 60 | No | No |
| card | closed | 1 | 63 | No | No |
| report | complete | 1 | 39 | No | No |
| report | unread | 1 | 37 | No | No |
| report | slow | 1 | 52 | No | No |
| report | partial | 1 | 36 | No | No |
| report | closed | 1 | 89 | No | No |
| attachment | complete | 155 | 44 | No | No |
| attachment | unread | 156 | 37 | No | No |
| attachment | slow | 191 | 48 | No | No |
| attachment | partial | 141 | 43 | No | No |
| attachment | closed | 134 | 39 | No | No |

`complete`: lectura íntegra con comparación de bytes públicos; `unread`: descriptor
retenido; `slow`: 512 B/50 ms; `partial`: lee 512 B y cierra; `closed`: cierra sin
leer. Los consumidores son controlados en el mismo proceso mediante ContentResolver
y descriptores kernel reales; no prueban el modelo de permisos entre UID distintos.
Se verificaron cierre/revocación tras liberar al lector y rechazo de nuevas URI.
No se extrapolan estas latencias a un teléfono físico ni se afirma que otros
payloads/rutas no puedan tener problemas.

## G. Backups

Procedimiento: backup real de la bóveda mediante `create`, obtención del contenedor
portable en RAM dentro del AVD, inspección sin contraseña, con contraseña incorrecta,
con un byte alterado y con contraseña válida. Se exige que los rechazos no cambien
las filas. Tras restaurar se comparan todas las filas de nodos y hashes de adjuntos.

- Contenedor `ANBACK01`; última prueba: 11054597 bytes, diez nodos y siete adjuntos.
- No contenía el canario como cadena legible; pruebas negativas de autenticación complementan esa inspección.
- Las tres inspecciones no autorizadas/alteradas fallaron. No se restauró una candidata inválida.
- Fuente de backup con captura true/timeout 60000, receptor false/0: se conservó la decisión local false/0 (SEG-03).
- El secundario rechazó un backup del principal incluso con la contraseña correcta del archivo.
- Durante interrupción de backup se pausó el stream productivo `writeSnapshot` + `EncryptedBackup` hacia un `.part` privado; no se parcheó `create`. Tras matar el proceso, el parcial cifrado de 65596 B fue rechazado y no alteró los datos. Se comprobó retención reciente y limpieza por la regla de 24 h simulando la fecha del archivo; no se esperaron 24 h reales.
- En restauración se usó el callback existente de sincronización para pausar después del staging de adjuntos y antes del commit. Al reiniciar se conservaron la Nota anterior y los adjuntos confirmados; no quedaron archivos sin referencia ni diario.

No se ensayaron todas las posiciones de byte, truncamientos o fallos post-commit.
Las contraseñas eran exclusivamente de test; las claves generadas no se imprimieron.

## H. Temporales

Se observaron finalización, excepción de salida, cancelación de coroutine, reserva
viva durante snapshot y muerte real del proceso. El staging público estaba cifrado,
su lectura autenticada devolvía la fixture y su reserva sobrevivía al snapshot.
El snapshot activo no enumeró sus archivos ni token exclusivo. Cerrarlo, cancelar
o fallar retiró los derivados; la fuente cifrada no cambió. Al reiniciar se perdió
la operación efímera y se limpió el archivo cifrado pendiente.

El inventario final recorrió 57 archivos bajo los datos del paquete sintético,
sin seguir enlaces fuera de esa raíz y sin extraer sus bytes al host: ningún
canario privado apareció en claro. Es una fotografía del escenario, no una prueba
universal de ausencia de todo dato sensible en toda circunstancia. La copia
inicial se identificó por la envoltura de stream y su rechazo por SQLite; no se
reabrió por separado con descifrado completo en esta prueba. Su autenticidad
completa no se declara verificada únicamente por esa cabecera.

Cámara: se escribió una imagen sintética mediante el destino de `CameraCaptureFiles`
y se mató el proceso con su reserva pendiente. La recuperación del siguiente
proceso eliminó esa salida no confirmada. Permisos observados: raíz del paquete
700, files 771, cache 2771 y `cache/antisocial-camera` 2700; la raíz 700 y el sandbox
Android restringen otras identidades. No se ejecutó una app de cámara externa real.

Las capturas autorizadas y el DOCX en `cache/b-audit` son artefactos públicos
**del harness**, no residuos generados por los flujos privados del producto.
La copia de migración y los backups pendientes se evaluaron como archivos cifrados,
no como fallos por su presencia.

## I. Capturas y bloqueo

En MainActivity real, con preferencia de captura false, `FLAG_SECURE` estaba activo
y el área de aplicación resultó completamente negra en la captura. Se usó un
control positivo autorizado mediante la API normal: permiso efímero, flag retirado
y contenido visible. Después se revocó el permiso.

Se invocó el `AttachmentViewer` real desde el harness temporal para comprobar su
ventana, sin cambiar su implementación ni simular una política segura distinta.
Las dos ventanas de la app tenían `FLAG_SECURE`; la captura del visor y el fotograma
de grabación estaban negros. Antes de probar el ciclo de vida se recreó la Activity
para restaurar su composición normal. No se usa esa inyección para afirmar que se
recorrió la navegación completa del usuario hasta el adjunto.

- Píxeles no negros muestreados: principal protegido 0; control autorizado 240818; visor protegido 0; vídeo 0; pantalla bloqueada 0.
- Pillow comprobó también todos los píxeles del recorte protegido de las tres imágenes: rango RGB 0–0; el control autorizado tenía píxeles visibles.
- `screenrecord --time-limit 2` produjo H264 1080×2400 con un fotograma para la pantalla estática. MediaMetadataRetriever y ffmpeg lo decodificaron negro en el área protegida. No equivale a cubrir todas las transiciones de una grabación en movimiento.
- Recientes: miniatura negra, Activity RESUMED, sesión abierta y backgrounded=false; observación B-OBS-02.
- Inicio: cierre automático con configuración 0 y reapertura de Activity bloqueada/protegida.
- API de sesión: después del cierre normal, DB, stream y ruta antiguos rechazados. Se leyó el default de 30000 ms; se probaron dinámicamente las opciones 0 y Manual, no una espera real de 30 s.

No se verificaron dinámicamente todos los diálogos sensibles, PiP, multiwindow,
las ventanas de un visor externo ni las variantes de fabricantes.

## J. Exportaciones

En B se verificó DOCX nativo: su ZIP pasa CRC, sus XML/.rels se parsean y el medio
incrustado es el PNG negro visible, sin `arNd` ni canario de metadatos del original
rojo. El adjunto editable privado conservó sus bytes y ciphertext. Los dos ZIP
Markdown/JSON contenían la copia pública y el manifiesto JSON describía exactamente
su tamaño/hash. El test PDF existente ejecutó PdfDocument/PdfRenderer y comprobó
la representación visible sin píxeles del original oculto.

Se reutilizó además el test nativo existente de preparación/render de PNG, JPEG
con ICC/EXIF, GIF normal y Plain Text, WebP animado y APNG con/sin default, rechazo
de corrupción y decodificación animada WebP/GIF. La coincidencia de todos los
fotogramas RGBA, duración, alpha, repetición y controles de APNG/WebP, y las rutas
de exportación/importación de temas, conservan la evidencia específica previa de
[A.1](SECURITY_PHASE_A1.md). **No se contabiliza esa evidencia anterior como una
nueva ejecución B de todas las rutas de temas.**

Durante B también hubo compartición normal real de PNG por los tres proveedores,
con comparación de bytes y denegación tras bloquear. No se verificó en esta fase
una app receptora de otro UID ni el borrado de un documento parcial en un proveedor
SAF externo. La excepción/cancelación de exportación sí se probó en el escritor y
su staging con un destinatario sintético controlado.

## K. Limitaciones y metodología

Para cada ID se registró preparación, procedimiento, expectativa, observación,
evidencia y alcance. Resumen reproducible por familias:

| IDs | Preparación y procedimiento | Expectativa | Observación y límite |
|---|---|---|---|
| SQL/DISK | Bóveda nueva, canarios, escrituras y actualizaciones, inspección dentro del guest, aperturas negativas, checkpoint/reapertura | Aperturas sin autorización rechazadas, datos íntegros sin filas en claro | Confirmado; WAL presente, rollback journal no observado; SHM y ausencia de cadenas no se usan como única prueba criptográfica |
| SHARE/LOCK | PNG >1 MiB, descriptor real y cinco consumidores por proveedor; mutex 350 ms, cierre 1500 ms, limpieza final acotada | El lector no debe controlar el cierre criptográfico | Card/unread y slow incumplen ese límite; URI nueva denegada, recuperación al cerrar lector; no espera infinita ni receptor entre UID |
| BACKUP/ISO | Backup verdadero, contraseña ausente/errónea, bit alterado, restore válido y almacén secundario | Rechazo sin cambios, hashes íntegros y política local propia | Confirmado para esas candidatas; no fuzzing exhaustivo |
| TEMP/CRASH | Barreras de hasta 30 s en código de test, force-stop desde host, nuevo proceso y recovery | Sin fuentes dañadas ni derivados en claro persistentes | Tres barreras recuperadas; no cubre todos los puntos post-commit ni pérdida eléctrica del host |
| CAPTURE | MainActivity, permiso bloqueado, control autorizado/revocado, visor real, screenshot, vídeo, recientes e Inicio | Área privada no capturable con bloqueo; reautorización tras cierre | Capturas negras con control visible; Home cierra, recientes no notifica background en el estado observado; no todos los diálogos/dispositivos |
| EXPORT/PDF/FORMATS | Imagen editable roja con resultado negro, documento nativo/ZIPs, fixtures A.1 | Solo representación pública y manifiesto coherente, nunca fallback privado | Confirmado en recorridos descritos; temas y comparación frame-a-frame remiten a A.1 |

Otros límites: solo Android 17/API 37 en emulador, sin dispositivo físico/API 24;
no auditoría forense de RAM ni demostración de borrado físico de todas las copias
de clave; no cámara externa real; no cambio de usuarios Android; no auditoría de
criptografía matemática, contraseñas ni red. No se verificó el texto de todos
los errores en UI: se documentaron los rechazos/excepciones de las API probadas. Un archivo cifrado existente, una
captura autorizada o una salida pública deliberada no se clasifican como fuga.

Hubo cinco fallos preliminares del harness que se conservaron en los logs: dos por
normalización inválida de la etiqueta sintética, uno por pedir un tiempo de vídeo
posterior al único frame, uno por asumir que recientes produciría el cierre de la
sesión y uno por suponer exactamente dos archivos .db. Se corrigieron únicamente
las fixtures/expectativas o se añadió instrumentación para registrar el comportamiento;
la observación de recientes no se descartó. No se cambió producción para aprobarlos.
No se interpreta un test inicial fallido como vulnerabilidad sin investigar su causa.

## L. Próximas acciones

1. **Corregir SEG-05 en una tarea autorizada separada.** Separar el bloqueo común de la entrega al consumidor, preservar reservas/referencias, abortar entregas al cerrar sesión y exigir un límite medible con lector que no drena.
2. **Acordar la semántica de recientes/pérdida de foco.** Si el producto requiere cierre aunque Android conserve RESUMED, diseñar esa política y probar las transiciones legítimas antes de cambiarla.
3. Ampliar la regresión con un receptor sintético de otro UID y un proveedor SAF controlado, incluyendo revocación de grants y limpieza de salidas parciales.
4. Cubrir interrupciones después del commit, antes/después de cada diario, cámara externa real y ventanas de contraseña/exportación no recorridas.
5. Repetir los escenarios críticos en un teléfono físico y API mínima soportada, sin convertir los resultados actuales en una garantía universal.

No se ejecutó ninguna de esas correcciones ni se avanzó a otra fase.

## M. Confirmación y reproducción

**Único archivo añadido al repositorio:** `docs/SECURITY_PHASE_B.md` (este informe).
Los archivos de producción, tests existentes, manifiesto, Gradle, versiones,
dependencias, Room y formato de backup permanecen iguales a la instantánea inicial.
La comprobación hash está en `source-verification.json`; el diff global previo no
se revirtió. No hubo commits, push, tags, release ni publicaciones.

Se generaron APK debug de aplicación y tests. Instrumentación nueva exclusivamente
fuera del repositorio:

- `/tmp/arachnode-phase-b/src/com/r0ybt/arachn0de/security/PhaseBDynamicTest.kt`: SQL, 15 consumidores, bloqueo, backups, staging y disco.
- `/tmp/arachnode-phase-b/src/com/r0ybt/arachn0de/security/PhaseBRecoveryTest.kt`: barreras y recuperación tras tres muertes de proceso.
- `/tmp/arachnode-phase-b/src/com/r0ybt/arachn0de/security/PhaseBCaptureTest.kt`: ventana real, visor, control autorizado, capturas/vídeo y ciclo de vida.
- `/tmp/arachnode-phase-b/src/com/r0ybt/arachn0de/security/PhaseBExportIsolationTest.kt`: DOCX/ZIP y almacén secundario.
- `/tmp/arachnode-phase-b/init.gradle`: añade únicamente ese sourceSet androidTest en memoria durante la invocación.
- `/tmp/arachnode-phase-b/run_interruptions.py`: arranca cada barrera, espera un ready exacto, termina solo el paquete de test y ejecuta recovery con límites de tiempo.

Para reproducir, usar otro AVD vacío con la imagen instalada y guardarlo fuera del
AVD habitual; red desactivada y servidor ADB aislado. La fixture se niega a abrir
un almacén configurado sin su marca de propiedad sintética. Compilar sin descargas:

```bash
GRADLE_USER_HOME=<cache-aislada> ./gradlew --offline --no-daemon   -I /tmp/arachnode-phase-b/init.gradle   :app:assembleDebug :app:assembleDebugAndroidTest
```

Instalar ambos APK debug en el dispositivo temporal. Los tamaños y tiempos son
observaciones de esta ejecución, no valores universales. Ejecutar `PhaseBDynamicTest`
antes de crear el secundario; después `PhaseBExportIsolationTest` y captura. Para
las tres muertes usar el driver, sin ejecutar las preparaciones por sí solas ni
sin el watchdog. El timeout de preparación es 30 s; la recuperación es otra
invocación y requiere la credencial sintética del harness. Para el inventario
final de tres archivos .db, repetir `fOnDiskInventoryAndNoPlainDatabaseCopies`
después de crear el secundario; repetir `dBackupAuthenticityAndLocalSecurity`
después del adjunto editable reproduce el backup de siete adjuntos. No aplicar este
procedimiento a un AVD o almacén con datos personales.

Logs y artefactos se conservan en `/tmp/arachnode-phase-b/`: `priority-tests.txt`,
`backup-staging-tests.txt`, `ui-export-isolation-tests.txt`,
`final-capture-backup-formats.txt`, `final-lifecycle-capture.txt`,
`final-inventory.txt`, `final-active-stage.txt`, `crash-*-prepare.txt`,
`crash-*-recovery.txt`, `interruption-results.json`, `events.jsonl`,
`directory-permissions.txt`, `device.txt`, `test-index.json`, fuentes del harness
y `public-evidence/`. Los fallos preliminares siguen identificables; el informe
los distingue de los resultados válidos y de las interrupciones deliberadas.

La instancia BSynthetic, su servidor ADB 5047 y los procesos temporales de
compilación se cerraron; se eliminaron solo su userdata y caché Gradle aislada.
Se conservaron los logs, fuentes de test y artefactos públicos de auditoría.
No se modificó el AVD habitual ni se mantuvieron copias de bases/backups del guest.
