## Preparación oficial — 0.25.0 / 40

Referencia real verificada: `release-assets/Arachn0de-v0.24.0-code39.apk`,
SHA-256 `ed6bf4453c308324eec8884eedaa618bee4242a746446677c54441ab1d7306cc`.
La versión del proyecto era 0.24.0/code39; se incrementa a 0.25.0/code40,
sin reutilizar los códigos ya generados ni reemplazar APK anteriores.

Incluye todo el estado actual, la simplificación y plantillas estructurales,
la depuración de temas y la corrección del arranque en frío por inicialización
circular de `AppearanceTheme`/`AppearanceKt`. Esta última se reprodujo con una
prueba aislada, se corrigió y se comprobó en el emulador hasta Contraseña y
Desbloquear. Se reutilizan las pruebas focalizadas aprobadas, sin repetir suites.
No se inicia el rediseño de navegación, Ordenar/Filtrar ni búsqueda contextual.

Se revisaron el contrato de persistencia y la lista de liberación: las plantillas
reutilizan `saved_templates`, payload v2 y backup existente con bytes completos;
conservan v1, IDs nuevos por instancia y diarios de recuperación. Las versiones
antiguas que solo aceptan payload v1 rechazan respaldos que contengan v2; está
documentado en BACKUP_FORMAT.md. No hay cambio de esquema Room ni de SQLCipher
durante este empaquetado. No se accede a datos del usuario ni se instala la release.

Procedimiento: `bash release-assets/preparar-apk-0.25.0.sh --check` y después
el mismo helper sin argumentos en terminal local. Preflight aprobado, incluido
certificado oficial SHA-256
`d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`.
Credenciales solicitadas nuevamente por `/dev/tty`, sin eco. No se detectaron
contraseñas en las configuraciones inspeccionadas ni credenciales heredadas;
solo se admite su entorno temporal de proceso, sin argumentos visibles,
configuration cache, daemon persistente o registro de secretos.

Entrega terminada el 2026-10-28 mediante el helper oficial, código de salida 0.
`assembleRelease` y `lintVitalRelease` correctos (9m50s). Contraseñas nuevas
introducidas interactivamente sin eco, sin argumentos visibles ni persistencia
detectada en los metadatos de firma generados. Certificado idéntico al oficial.

APK: `release-assets/Arachn0de-v0.25.0-code40.apk`, 293585394 bytes (279.98 MiB).
SHA-256: `0ca9b11026b68463df43bfe508c76420565e21864025f5759674a7840d26c4ea`.
Checksum adjunto: `.apk.sha256`. Paquete `com.r0ybt.arachn0de`, versión
0.25.0/code40, no depurable. Firma de la copia final verificada con apksigner.
ZIP/CRC, alineación 16 KiB, permisos, bibliotecas SQLCipher, 85 assets actuales,
logotipos y marcadores DEX recientes comprobados. Fuentes sin cambios durante
el build; APK anterior íntegra. Sin suites repetidas, instalación de la release,
commit, push, publicación ni borrado de entregas anteriores o datos del usuario.

## Preparación oficial — 0.24.0 / 39

Última entrega oficial: 0.22.0/code37. Code38 quedó apartado sin entrega;
se reserva code39 para no reutilizar una compilación anterior.
Se conserva todo el árbol de trabajo (incluidos archivos nuevos y recursos).
Fuentes revisadas: identidad, GIF transparente, temas, navegación, etiquetas,
Tecnologías, cumpleaños, buscador, Antisocial, listas, código y esfuerzo.
También incluidos: colores intensos de indicadores, botones deshabilitados sin
pintura superpuesta, títulos Núcleo igualados a Capa, icono/cierre de esfuerzo,
margen del cumpleaños y encabezado compacto sin texto redundante de prioridad.
Compilación y pruebas focalizadas del formato de código ya aprobadas;
no se repiten suites. Room54/JSON57 y contratos de backup conservados.
Firma: helper específico preparar-apk-0.24.0.sh, entrada oculta por /dev/tty;
no se detectaron contraseñas persistidas en las configuraciones de firma
inspeccionadas ni variables heredadas de credenciales. Sin caché de configuración.
Instantánea SHA-256 de fuentes registrada antes de compilar. Resultado final
pendiente de compilación release, firma y comprobaciones del artefacto.
Sin instalación automática, commit ni push.

Release finalizada y verificada el 2026-10-28: Arachn0de-v0.24.0-code39.apk,
293519858 bytes (279.92 MiB). SHA-256:
ed6bf4453c308324eec8884eedaa618bee4242a746446677c54441ab1d7306cc.
Compilación assembleRelease correcta; firma válida, certificado idéntico a
code37, applicationId correcto, no depurable, ZIP/CRC y alineación 16 KiB
correctos. Permisos idénticos a code37. Assets byte a byte, GIF transparente,
logotipos y marcadores de implementaciones recientes comprobados dentro del APK.
Instantánea de fuentes sin cambios durante el build. Contraseñas solicitadas
e introducidas interactivamente sin eco; no se incluyeron en archivos ni argumentos.
Actualización compatible por identidad, certificado y código; pendiente instalación
y comprobaciones visuales en teléfono. No se instaló ni se tocaron datos reales.

## Preparación oficial — 0.23.0 / 38

La primera compilación no entregada se apartó en release-assets/superseded/code38-opaque.
Se reconstruye esta versión aún no entregada con el GIF transparente original
aportado por el usuario y Atención con fondo/borde activo monocromático.
Los 180 fotogramas tienen transparencia en la esquina; prueba visual del estado
activo aprobada en los cinco temas monocromáticos. Nueva entrada interactiva de
contraseñas obligatoria, sin reutilizar las del proceso anterior.


Referencia: Arachn0de-v0.22.0-code37.apk, SHA-256
 eef397f3f7546f56fca497489a757e32d603feb84e38189362185e7b8f29b0df.
Código fuente actual comprobado: identidad monocromática, logotipos Android,
GIF de desbloqueo, cuatro temas, resaltador retirado, código compacto, esfuerzo
informativo, listas/IME, etiquetas directas, menús, buscador, cumpleaños, banner
y favoritos de Antisocial. Sin desarrollo adicional ni repetición de suites.
Persistencia y backup conservados: Room54/JSON57; los cambios recientes usan los
repositorios cifrados existentes y preferencias portátiles de apariencia.
Script revisado: descarta contraseñas/habilitación heredadas y solicita nuevas
credenciales en /dev/tty sin eco; Gradle no lee propiedades de firma almacenadas.
Sin configuration cache ni daemon persistente. Contraseñas solo en el entorno
temporal del proceso de firma, nunca en argumentos, informes o repositorio.
Verificaciones release/identidad/recursos/integridad pendientes. Sin instalación,
commit, push ni publicaciones. Fuentes registradas antes de compilar.

## Preparación final — 0.22.0 / 37

Referencia oficial: Arachn0de-v0.21.0-code36.apk. Se conserva todo el trabajo local.
Sin desarrollo adicional ni repetición de pruebas aprobadas. Lotes de acciones,
imágenes, descripciones, etiqueta única y progreso implementados. Lote 5B contiene
esfuerzo 0–5, panel inmediato y ordenación jerárquica con herencia/Aplicar.
Compilación debug y pruebas focalizadas aprobadas, incluidas migración 53→54,
backup JSON57/restauración histórica, conversiones y controles UI. No hay una
parte conocida sin compilar; pendientes validación física de colores/skins,
gestos y actualización sobre los datos reales. No se continúa funcionalidad.
Contrato revisado: nuevos campos exportados/restaurados con validación previa,
sin nuevos archivos; migración explícita preserva datos. No se modifican cifrado,
reglas financieras ni probabilidades. Firma oficial mediante helper existente,
contraseñas únicamente en terminal local sin eco. Sin instalación, commit o push.
Release terminada mediante el helper interactivo; contraseñas introducidas por el
usuario sin eco. Firma válida y coincidente con code36; package y versión de APK
confirmados, no depurable, ZIP/CRC sin errores y assets actuales idénticos a fuentes.
DEX incluye TaskEffort, EffortMigration53To54, setEffort y EFFORT.
APK: release-assets/Arachn0de-v0.22.0-code37.apk (294199826 bytes, 280.57 MiB).
SHA-256: eef397f3f7546f56fca497489a757e32d603feb84e38189362185e7b8f29b0df.
Actualización compatible por identidad/certificado/código con code36; no instalada
ni comprobada físicamente. Sin commit ni push.

## Release oficial — 0.21.0 / 36

Referencia disponible: Arachn0de-v0.20.0-code35.apk; SHA-256
263a7d2e2e4c174d5fcde2e1676cc2a8a1c8e36e021a81a6f4d0769e0447bc33.
Se conserva el árbol local completo. Reorganización de Opciones: conversiones
y alcances en el mismo panel, navegación Atrás; 2 pruebas UI focalizadas y
compilación aprobadas. Recurrencias investigada y conservada sin cambios:
lista reglas existentes; el mensaje orienta su creación cuando la lista está vacía.
Word y DOCX son una sola acción. Los alcances únicos se ejecutan directamente.
Compartir imagen no añade soporte de lote; PDF/DOCX conservan informes de
contenedores. No cambia el renderizador de imagen ni se repiten sus 17 controles.
Contrato de persistencia/backup revisado: esta reorganización no añade datos,
archivos persistentes, tablas ni migraciones. Se conservan cambios previos de
imágenes Banco y exportación. No se instala, publica ni distribuye la aplicación.
Entrega local: release-assets/Arachn0de-v0.21.0-code36.apk.
assembleRelease correcto (9 min 29 s). Firma apksigner válida y certificado
idéntico a code35: d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437.
applicationId com.r0ybt.arachn0de, versionName 0.21.0, code36 > code35,
no depurable ni instrumentación ni servicio lector de notificaciones ajenas.
ZIP/CRC, entradas únicas y zipalign 16 KiB correctos. 84 assets idénticos a las
fuentes (40 selváticos, 8 GIF); fuentes previas al build sin modificaciones.
Tamaño: 294150674 bytes (280.52 MiB).
SHA-256: dc867120a1a539b00d9e289f243c555119cee4cdadcd1c2e5be957e811923b81.
Compatibilidad por identificador, certificado y código con la oficial code35;
no se inspeccionó ni instaló en teléfono. La referencia anterior conserva su hash.
Contraseñas introducidas exclusivamente en terminal local sin eco.

## Preparación autorizada — 0.20.0 / 35

Referencia oficial: Arachn0de-v0.19.0-code34.apk, SHA-256
c3fe9eb100fa05238429d3219b3996936fbdb2347a96e7c02e789328fca30357.
Se conservan todos los cambios locales. Compilación e intercambio/backup de
imagen Banco aprobados (2 pruebas); indicadores y animaciones aprobados
(2 pruebas focalizadas cada uno). No se repiten suites.
Contrato de backup revisado: imagen opcional bank reutiliza bytes/originales,
reservas y restauración de temas; sin cambios Room ni operaciones financieras.
Se incluyen los 8 GIF y el marco de depósito originales con los assets históricos.
Firma oficial mediante helper existente, contraseñas sin eco en terminal local.
Entrega y verificación final pendientes. No se instala ni se manipulan datos.

## Release oficial — 0.19.0 / 34

Referencia oficial: release-assets/Arachn0de-v0.18.0-code33.apk.
SHA-256: 8cb651e7f6de7a9881887dddec43981c6cc1a37a3e0d4bbd742e0debf250c02a.
Salida: release-assets/Arachn0de-v0.19.0-code34.apk.
Se conserva el árbol local completo y se reutiliza el helper oficial, con la
misma clave/alias y certificado; contraseñas exclusivamente en terminal sin eco.
Solo cambian versión y referencias de empaquetado, sin ajustes funcionales.

Contrato de persistencia revisado: Room 53 y migraciones explícitas conservadas,
sin fallback destructivo; JSON 55 para Contenido/GIF y codec Ahorro 4 incluidos.
Se reutilizan pruebas focalizadas aprobadas de Ahorro, Contenido/GIF, buscador,
resaltador y la compilación del espaciado. No se repiten suites. Incluye todos
los recursos locales, incluidas las 31 imágenes selváticas originales.
No se instalan/desinstalan aplicaciones ni se tocan datos del teléfono.
Entrega completada: assembleRelease offline correcto (11 min 4 s).
Firma válida y certificado SHA-256 idéntico a code33:
d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437.
applicationId com.r0ybt.arachn0de; versionName 0.19.0; versionCode 34 > 33;
release no depurable, sin acceso a notificaciones ajenas ni instrumentación.
ZIP/CRC, ausencia de entradas duplicadas y zipalign 16 KiB correctos.
Los 75 assets coinciden byte a byte con las fuentes, incluidos 31 selváticos,
16 históricos de Tragaperras y los recursos de Carrera. Las 737 entradas de
fuentes/recursos registradas antes de compilar permanecen sin cambios.
APK: 273681050 bytes (261.00 MiB).
SHA-256: c3fe9eb100fa05238429d3219b3996936fbdb2347a96e7c02e789328fca30357.
La APK anterior conserva su hash. Compatible por identidad/certificado/código
con la actualización de la oficial code33; no se ensayó instalación en teléfono.

Los apartados siguientes son históricos.

## Preparación autorizada — 0.18.0 / 33

Última referencia verificada: `release-assets/Arachn0de-v0.17.1-code32.apk`,
SHA-256 `ce8b45c206be4e2aa9302baa28afad89bc0fb33ded1ec2638a62066291a31850`.
Certificado oficial SHA-256:
`d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`.
Salida: `release-assets/Arachn0de-v0.18.0-code33.apk`.

Se conserva el árbol local completo. Fuentes main idénticas a la compilación y
29 pruebas de Ahorro aprobadas, además de las validaciones UX previas documentadas.
Solo se cambia versión, notas y referencias del helper durante el empaquetado.
No se repiten suites aprobadas. Compilación release y comprobaciones del artefacto
mediante el helper oficial `bash release-assets/preparar-apk-0.3.0.sh`.
Keystore/alias/certificado sin cambios; contraseñas por /dev/tty sin eco.

Contrato revisado: Ahorro exporta/restaura preferencias cifradas y enlaces con
Banco mediante JSON 51, lectores históricos conservados, validación previa,
rollback y recuperación comprobados. Room continúa en 53 con migraciones
explícitas, sin fallback destructivo. No añade archivos propios; los traslados
conservan referencias/bytes y su rollback está probado. Detalles en
ARCHITECTURE.md, BACKUP_FORMAT.md, docs/SLOT_SAVINGS_DESIGN.md y
docs/UX_FUNCTIONAL_CORRECTIONS.md. Sin operaciones sobre datos del usuario.
Sin instalación, desinstalación, commit, push, tags ni publicación.
Entrega completada: helper oficial finalizado con código 0; assembleRelease y
lintVitalRelease correctos (8m24s). APK verificada con apksigner, firma v2 válida
y certificado oficial idéntico a code32. Paquete correcto, versión 0.18.0/code33,
no debuggable, sin instrumentación ni acceso a notificaciones ajenas, allowBackup
false. ZIP íntegro y zipalign con páginas de 16 KiB aprobado. Los 44 assets
coinciden byte a byte con fuentes, incluidos los 16 PNG de Tragaperras; los 125
recursos drawable/mipmap/raw incluyen todos los históricos. Clases de Ahorro,
Banco, backup, skins y sonidos presentes. Fuentes main sin cambios durante release.
La APK oficial anterior conserva su hash.
Tamaño: 204709842 bytes (195.23 MiB).
SHA-256: `8cb651e7f6de7a9881887dddec43981c6cc1a37a3e0d4bbd742e0debf250c02a`.
Misma identidad y código superior permiten instalar como actualización sobre la
release oficial anterior sin desinstalar. No se probó instalación física ni se
tocaron datos del teléfono. Resultado de Play Protect y revisión visual requieren
comprobación real. Sin commit, push ni publicación.

Los apartados siguientes son históricos.

## Preparación autorizada — 0.17.1 / 32

Referencia anterior: `release-assets/Arachn0de-v0.17.0-code31.apk`, certificado
`d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`,
SHA-256 `ab6d4cfb72213ff2f44ea9b2cfbc9c700b18a77eafe279c97be616696e312969`.
Salida nueva: `release-assets/Arachn0de-v0.17.1-code32.apk`. Mismo applicationId
`com.r0ybt.arachn0de`, keystore y alias oficiales; versiones anteriores intactas.

Incluye la retirada completa de detección CMR por notificaciones y todos los demás
cambios locales. Sin listener, acceso especial, controles ni reconocimiento CMR.
Compatibilidad histórica exclusivamente en backup/legacy: Room 53, JSON hasta 50,
validación y restauración conservadas, sin borrado de datos. Revisión del contrato
registrada en docs/CMR_REMOVAL.md y BACKUP_FORMAT.md. Banco, pagos, abonos y demás
permisos permanecen intactos. Los hashes de las fuentes coinciden con las seis
pruebas focalizadas ya aprobadas; no se repiten casos sin cambios funcionales.

Se reutiliza el helper oficial: `bash release-assets/preparar-apk-0.3.0.sh --check`
y después sin `--check` en terminal local, con contraseñas desde /dev/tty sin eco.
Solo se actualizan identificadores/referencia y se añade la comprobación obligatoria
de ausencia de acceso a notificaciones ajenas en el manifiesto de la APK. El helper
compila release y bloquea la entrega ante fallo de firma, versión, integridad,
recursos o manifiesto. No se modifican fuentes funcionales durante el empaquetado.
Entrega completada y verificada: helper oficial terminó con código 0. APK release
no debuggable, firma v2 válida, certificado idéntico al de code31, applicationId
correcto y code32 superior. Manifiesto final sin NotificationListenerService,
BIND_NOTIFICATION_LISTENER_SERVICE, PurchaseListener ni consulta del paquete CMR.
ZIP íntegro y zipalign 16 KiB aprobado; 44 assets y dos audios íntegros, inventario
drawable/mipmap histórico conservado, clases de skins, sonidos, Banca y backup
presentes. Fuentes funcionales idénticas a las seis pruebas focalizadas aprobadas;
solo cambió la versión. No se repitieron pruebas sin cambios funcionales.
Tamaño: 204644306 bytes (195.16 MiB).
SHA-256: `ce8b45c206be4e2aa9302baa28afad89bc0fb33ded1ec2638a62066291a31850`.
La APK anterior conserva su hash. Misma identidad y código superior permiten
actualización sin desinstalar; no se ejecutó instalación ni acceso a datos del
usuario. Play Protect y revisión física pendientes en teléfono.
Sin limpieza de datos, commit, push, tags ni publicación.

Los apartados siguientes son históricos.

## Preparación autorizada — 0.17.0 / 31

Referencia oficial: `Arachn0de-v0.16.0-code30.apk`, paquete
`com.r0ybt.arachn0de`, certificado SHA-256
`d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`.
SHA-256 anterior: `b57e5ed5c2f5aebaa34da5d372184b07b61adfa1c02d9a0247fe8eecfe716796`.
Se conserva sin sobrescribir. Salida: `release-assets/Arachn0de-v0.17.0-code31.apk`.

Incluye los cambios locales actuales, indicadores de Personas y recurrencias,
compactación visual de Banco y resumen de abonos parciales. Se reutilizan la
compilación y pruebas focalizadas aprobadas, incluidas las cinco de compactación;
no se repiten suites. CMR permanece congelado: `PurchaseIssuers.certificates`
vacío impide detección real. No se modifica código funcional durante el empaquetado.

Revisión de persistencia: Room permanece en 53, con migraciones explícitas;
backup JSON hasta 50 incluye las preferencias del detector, compatibilidad histórica,
validación previa y restauración transaccional. Su entrada temporal cifrada y
recuperación se documentan en ARCHITECTURE.md, BACKUP_FORMAT.md y docs/CMR_DETECTION.md,
con siete pruebas dirigidas aprobadas. Los cambios visuales no añaden datos.
No se accede a bases de usuario ni se realizan limpiezas, instalación o desinstalación.

Procedimiento oficial: `bash release-assets/preparar-apk-0.3.0.sh --check`, seguido
del mismo helper sin `--check` en terminal local. Mismo keystore y alias;
contraseñas leídas exclusivamente desde /dev/tty sin eco. Se actualizan solo los
identificadores de versión y la referencia oficial del helper, conservando firma
y verificaciones. Entrega completada: helper oficial terminó con código 0. APK release firmada
verificada mediante apksigner (v2), certificado idéntico a code30, paquete y versión
correctos, no debuggable, sin instrumentación y backup Android deshabilitado.
ZIP íntegro y zipalign 16 KiB aprobado. Los 44 assets y dos audios raw coinciden
byte a byte con los originales; recursos gráficos y clases de skins, sonidos,
migración de Banca y backup presentes. Fuentes funcionales sin cambios desde
el inicio del empaquetado, CMR con lista de emisores vacía y APK anterior intacta.
Tamaño: 204677282 bytes (195.20 MiB).
SHA-256: `ab6d4cfb72213ff2f44ea9b2cfbc9c700b18a77eafe279c97be616696e312969`.
Paquete/firma coincidentes y código superior permiten actualizar la versión
oficial anterior sin desinstalar. No se ejecutó instalación ni acceso a datos;
la actualización y revisión física en teléfono quedan a cargo del usuario.
No hacer commit, push, tag ni publicación.

Los apartados siguientes son históricos.

## Preparación autorizada — 0.16.0 / 30

Referencia oficial verificada: `Arachn0de-v0.15.0-code29.apk`, paquete
`com.r0ybt.arachn0de`, certificado SHA-256
`d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`,
SHA-256 del archivo `74e4cb07747767ed1dbee0d8c73c5e8fc38fe23a035ea91e4a82be1302d9bd55`.
Se conserva sin sobrescribir. Nueva salida: `release-assets/Arachn0de-v0.16.0-code30.apk`.

Incluye Banca, sus siete pruebas focalizadas aprobadas y la corrección de
transparencias de Tragaperras con su prueba aprobada. Las fuentes de Banca
corresponden al estado validado; la última compilación incluye el ajuste de Slots.
No se repiten suites generales. Room 53 conserva migraciones explícitas hasta
52→53; Banca añade tablas y el backup JSON 49 conserva sus cuatro registros,
validación previa y restauración transaccional. Los resultados previos de migración,
backup y recuperación siguen documentados en ARCHITECTURE.md/BACKUP_FORMAT.md.
El empaquetado no modifica persistencia ni toca la base de datos del usuario.

Se reutiliza `bash release-assets/preparar-apk-0.3.0.sh --check` y el mismo helper
sin `--check`, con idéntico keystore, alias, firma y comprobaciones. Se actualizan
solo versión, referencia anterior y recursos exigidos (incluido menú de Slots).
Contraseñas únicamente desde /dev/tty local sin eco; sin secretos en comandos,
archivos o registros nuevos. El helper compila release, verifica firma, identidad,
versión, ausencia de debug, integridad ZIP, SQLCipher y recursos originales antes
de copiar la entrega. Helper oficial finalizado correctamente. APK firmada verificada: 204627922 bytes
(195.15 MiB), SHA-256 `b57e5ed5c2f5aebaa34da5d372184b07b61adfa1c02d9a0247fe8eecfe716796`.
Certificado idéntico a code29, paquete/versiones correctos, release no depurable,
backup automático deshabilitado, sin instrumentación, ZIP íntegro y zipalign 16 KiB
aprobado. Los 44 assets originales, dos audios raw (resueltos mediante la tabla
de recursos, porque AAPT renombra sus rutas) y recursos drawable/mipmap están
incluidos; clases de Banca, migración, backup, skins y sonidos presentes en DEX.
Las fuentes/recursos main coinciden con el snapshot previo al empaquetado.
La APK anterior conserva su SHA-256; no se repitieron pruebas ni compilaciones
Debug y no se leyeron/escribieron datos de usuario.
No instalar, desinstalar, limpiar datos, hacer commit ni push. La actualización
real conservando datos y revisión visual de Banca requieren comprobación física.

Los apartados siguientes son históricos.

## Preparación autorizada — 0.15.0 / 29

Última APK oficial comprobada: 0.14.0/code28. SHA-256 conservado:
`7f369e28c05e8739b4de8e6e0c67dc5d77145b9e2f8cacedf35da314a067724a`.
Mismo paquete y certificado oficial. Se conserva todo el árbol de trabajo.
Incluye Tragaperras Tradicional/Tareas, sonidos sintéticos, créditos ficticios
de sesión y acceso directo en Inicio del menú. Incluye los demás cambios actuales.
Se reutilizan compilación y 13 pruebas focalizadas aprobadas; las pruebas visuales
fueron aprobadas por el usuario. El release compila los últimos ajustes de gestos,
Hoy y ubicación del acceso, sin repetir suites generales.

Procedimiento: `bash release-assets/preparar-apk-0.3.0.sh --check` y después
el mismo helper sin `--check` en terminal local. Contraseñas solo desde /dev/tty,
sin eco ni registros. Se usa un trabajador y compilación Kotlin no incremental
por los conflictos de caché previamente comprobados; misma firma y verificaciones.
El helper verifica además los 14 PNG de ambos modos byte a byte y el SHA anterior.
Los sonidos se sintetizan en código; no requieren archivos de audio adicionales.

Salida prevista: `release-assets/Arachn0de-v0.15.0-code29.apk` y `.apk.sha256`.
Firma y entrega pendientes hasta ejecutar el helper y verificar el artefacto.
El empaquetado no modifica cifrado, esquema, persistencia ni backup; las funciones
nuevas de perfiles ya incluyen migración y backup, con pruebas dirigidas registradas
en ARCHITECTURE.md. Captura de invitado sigue deshabilitada hasta su validación Android.
Sin instalación automática, commit ni push. Actualización con datos, privacidad al
regresar y respuesta de Favoritos requieren comprobación física.

Los apartados siguientes son históricos.

## Preparación autorizada — 0.14.0 / 28

Se conserva la APK oficial 0.13.0/code27: 167582727 bytes,
SHA-256: 0d2f9b9fa86e8d0902ffe48330b3eccdcea6b99aa0f4b73a661f3f0b9adf0a8a.
Certificado oficial SHA-256:
d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437.

Incluye el estado actual: buscador unificado, mejoras del calendario contextual,
Compartir y exportar con Markdown/JSON parcial, fotografía según encuadre, panel
compacto de Borradores y correcciones previas de tarjetas/encabezados. Se reutilizan
las validaciones focalizadas aprobadas; no se repiten suites. Durante el empaquetado
no cambian fuentes funcionales, persistencia, cifrado, formato de backup ni claves.

Procedimiento oficial: bash release-assets/preparar-apk-0.3.0.sh --check.
Después, el usuario ejecuta el mismo helper sin --check en su terminal local:
contraseñas interactivas sin eco, sin chat, argumentos ni registros de secretos.
El helper realiza assembleRelease y verifica paquete, versión, ausencia de debug,
certificado oficial, ZIP, SQLCipher ARM y recursos antes de copiar el resultado.

Salida prevista: release-assets/Arachn0de-v0.14.0-code28.apk y .apk.sha256.
Firma, tamaño e integridad finales pendientes hasta ejecutar y verificar realmente
ese procedimiento. No se declara la APK lista antes de ello. No instalar, borrar
datos, hacer commit ni push. Actualización conservando datos, teclado/temas, regreso
del segundo plano y respuesta de Favoritos siguen requiriendo revisión en teléfono.

Los apartados siguientes son históricos.

## Preparación autorizada — 0.13.0 / 27

Se conserva la APK oficial 0.12.0/code26 sin sobrescribir:
SHA-256 `fdc1c287ebc1a709450a6c34418c9751f894f2a6aa8e7d7791c1e9d1fd11250a`.
Se actualizan únicamente los identificadores del helper oficial; misma aplicación,
clave, alias y verificaciones. Incluye todos los cambios actuales, Contenido en
Núcleo/Capa/Tarea/Nota, retirada de accesos de plantillas, icono de Metro y nombres
compactos Tecnologías/Estados. No se modifican persistencia ni dependencias.
Se reutilizan las pruebas focalizadas aprobadas, sin suites adicionales.
Procedimiento: `bash release-assets/preparar-apk-0.3.0.sh --check` y después
el mismo helper sin `--check`, con contraseñas en terminal local sin eco.
Salida prevista: `release-assets/Arachn0de-v0.13.0-code27.apk` y su checksum.
Artefacto 0.13.0/code27 generado y verificado: 167582727 bytes; firma oficial
`d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`.
SHA-256 `0d2f9b9fa86e8d0902ffe48330b3eccdcea6b99aa0f4b73a661f3f0b9adf0a8a`.
La APK 0.12.0/code26 conserva su hash. Las correcciones posteriores de tarjetas y
captura de borradores no están incluidas en esta APK; requieren otro empaquetado autorizado.
Sin instalación automática, commit ni push. Comprobar actualización con datos,
menús, skins y respuesta al volver del segundo plano en el teléfono.

Los apartados siguientes son históricos.

## Preparación autorizada — 0.12.0 / 26

Preflight: última APK oficial verificada 0.11.0/code25, 167516542 bytes.
SHA-256 anterior: `b5c83ff67caf7ed6a0c7597bf24239722e31d0465dc1c65ab48a0f78a806bbbb`.
Se conserva sin sobrescribir. Mismo applicationId, clave, alias y certificado oficiales.
Se actualizan solo los identificadores de versión del helper existente; su procedimiento
no cambia: `bash release-assets/preparar-apk-0.3.0.sh --check`, después sin `--check`.
Contraseñas solo en terminal local, sin eco y sin registro. Mantener la terminal abierta al terminar.
Salida prevista: `release-assets/Arachn0de-v0.12.0-code26.apk` y `.apk.sha256`.
Incluye menú inferior, filtros contextuales, orden del calendario, respuesta de Favoritos,
conservación del tema al regresar y ajuste del degradado compartido. Se reutilizan las
compilaciones y pruebas focalizadas aprobadas; no se ejecutan suites generales.
No cambian cifrado, Room 50 ni backup JSON hasta 43 durante este empaquetado.
Artefacto 0.12.0/code26 verificado: release no debuggable, paquete y certificado oficiales,
ZIP íntegro, 88 PNG, 167516542 bytes (159.76 MiB).
SHA-256: `fdc1c287ebc1a709450a6c34418c9751f894f2a6aa8e7d7791c1e9d1fd11250a`.
La APK anterior conserva el SHA-256 registrado arriba. No se instaló en dispositivos.
La cabecera fija del Calendario principal es un cambio posterior y no está en esta APK.
Pantalla gris y latencia de Favoritos siguen pendientes de medición en teléfono;
la compilación no acredita su resolución. Actualización con datos y ergonomía requieren
revisión física. Sin instalación, ADB físico, commit ni push.
Los apartados siguientes son históricos.

## Preparación autorizada — 0.11.0 / 25

Última APK oficial: 0.10.0/code24, conservada sin sobrescribir.
SHA-256 anterior: `5254ff0a8ecbe0a4379996cd51437c61e2114cf048ee5f25bacfb91b6ea0aa14`.
Se reutiliza el helper oficial y su clave, alias y verificaciones existentes:
`bash release-assets/preparar-apk-0.3.0.sh --check` y después el mismo comando sin `--check`.
Contraseñas únicamente en la terminal local sin eco. Salida prevista:
`release-assets/Arachn0de-v0.11.0-code25.apk` y `.apk.sha256`.
Incluye calendario contextual y correcciones actuales; se reutilizan pruebas focalizadas aprobadas.
La prueba de eliminación del estado redundante pasó conservando la casilla funcional.
Room 50 y backup JSON hasta 43 sin cambios de persistencia en esta preparación.
Firma, integridad y entrega pendientes hasta verificar el artefacto real.
Actualización y conservación de datos requieren comprobación final en teléfono.
Sin instalación, limpieza de datos, commit ni push. Los apartados siguientes son históricos.

## Preparación autorizada — 0.10.0 / 24

El código y la última APK oficial local declaran 0.9.0/code23. La APK anterior
se verificó con el certificado oficial y coincide con su checksum SHA-256:
`39fce725ff6efa92ad911278b075c759d6d36e9beb0f43da5476914a7327144f`.
Se conserva sin modificar. Se prepara 0.10.0/code24 con el helper oficial existente,
el mismo applicationId, keystore y alias. Incluye el estado actual de Diagramas,
PDF, Núcleos y Skins. Room 50; backup JSON hasta 43; no cambia persistencia al empaquetar.

Ejecutar `bash release-assets/preparar-apk-0.3.0.sh --check` y después
`bash release-assets/preparar-apk-0.3.0.sh`. Contraseñas exclusivamente en terminal
local sin eco; no se guardan ni se introducen en el chat. La compilación y firma
release se realizan únicamente mediante ese helper, conservando sus verificaciones.
Salida prevista: `release-assets/Arachn0de-v0.10.0-code24.apk` y `.apk.sha256`.

Se reutilizan las validaciones dirigidas ya aprobadas, sin nuevas suites generales.
La última compilación Kotlin y la prueba focalizada de PDF pasaron. Las comprobaciones
pendientes y fallos de la batería interrumpida de Skins siguen documentados en
SKINS_CORRECCIONES_VISUALES.md; no se declaran resueltos ni aprobados al empaquetar.
La revisión del contrato de backup remite a las validaciones dirigidas existentes de
Borradores, selector de estados y originales de temas; no se introducen datos nuevos.
No declarar terminado hasta verificar firma, certificado, versión, ZIP y SHA-256.
La coincidencia de paquete y certificado y el código superior permiten actualización;
la conservación real de datos y las interacciones requieren comprobación en teléfono.
No se instala, desinstala ni limpian datos. Sin commit, push, tag ni publicación.

Los apartados siguientes son históricos.

## Preparación vigente — 0.9.0 / 23

Empaquetado de prueba autorizado del estado actual. La entrega oficial local
0.8.0/code22 ya existe y se conserva. Las funciones compatibles posteriores,
incluida la conservación de originales de skins, requieren MINOR: 0.9.0/code23.
Se mantienen applicationId, keystore y alias oficiales. Room 48; backup JSON
hasta 41, con compatibilidad histórica. No se implementan los ajustes PDF pendientes.

Procedimiento: `bash release-assets/preparar-apk-0.3.0.sh --check` y después
`bash release-assets/preparar-apk-0.3.0.sh`. Contraseñas solo en terminal local
sin eco. Salida: `release-assets/Arachn0de-v0.9.0-code23.apk` y `.apk.sha256`.
No declarar completado antes de verificar el artefacto firmado. La coincidencia
de identidad y certificado acredita compatibilidad de actualización con las APK
oficiales locales; conservación de datos e interacciones requieren prueba física.
Validación de esta preparación: preflight oficial aprobado, sintaxis Bash y
`git diff --check` correctos; compilación Debug Kotlin correcta y 38/38 pruebas
dirigidas (originales 6, ciclo de archivos 11, backup 14, preferencias 2,
Diagramas 5), sin fallos ni omitidas. Las APK anteriores conservan sus SHA-256.
Lint sigue en ejecución al registrar este estado. Terminal local de firma abierta;
la entrega todavía no está generada ni verificada. No declarar firma ni hash final.

Sin commit, push ni publicación. Los apartados siguientes son históricos.

## Preparación autorizada — 0.8.0 / 22

Preflight aprobado: referencias oficiales, sintaxis Bash/Python y diff sin errores.
APK 0.7.0/code21 preservada por SHA-256. Se abrió la terminal local del helper;
permanece esperando entrada sin eco. Todavía no existe la APK de código 22:
compilación, firma, verificación final de recursos y SHA-256 siguen pendientes.
No se repiten las suites dirigidas previamente aprobadas.

El usuario autoriza empaquetar el estado actual con el certificado oficial.
Se actualizan únicamente versión y helper de release; se conserva la APK
0.7.0/code21 y se incorpora como referencia de actualización. Las secciones
siguientes describen antecedentes históricos, incluida la autorización ya resuelta.

Ejecutar `bash release-assets/preparar-apk-0.3.0.sh --check` y después
`bash release-assets/preparar-apk-0.3.0.sh` desde la raíz del proyecto.
Las contraseñas se leen exclusivamente en la terminal local sin eco.
Salida: `release-assets/Arachn0de-v0.8.0-code22.apk` y su `.apk.sha256`.
Firma, versión, ZIP, recursos y compatibilidad de identidad se verifican antes
de distribuir. Room 48 y backup JSON 40 se conservan. Se reutilizan las pruebas
dirigidas ya aprobadas; no se repiten PDF/DOCX ni Temas. No hay commit/push.
La actualización física, el zoom PDF y las demás interacciones táctiles siguen
pendientes de validación en teléfono. No se acredita conservación física de datos
solo con la coincidencia de paquete y certificado.

## Estado actual — revisión PDF completada; siguiente APK pendiente

La APK oficial `release-assets/Arachn0de-v0.7.0-code21.apk` ya existe: se
comprobaron paquete, versión y certificado oficial. Se conserva junto a la APK
0.6.0/code20. El preflight oficial bloquea reconstruir la salida de código 21;
no sobrescribirla ni reutilizar su código. Las preparaciones siguientes son
antecedentes y sus afirmaciones de «todavía no generada» ya no describen ese archivo.

La revisión PDF pasó compilación y 12/12 pruebas dirigidas. Imágenes e índice
ya estaban implementados; se añadió zoom, desplazamiento y paginación táctil
a la vista previa, sin cambiar formatos. Ver EXPORTACION_DOCUMENTOS.md.

El estado de trabajo incluye funciones posteriores a esa APK, como la tabla
ERD (la APK de código 21 no contiene su tipo/catálogo). Por la regla MINOR, la
siguiente propuesta es 0.8.0/code22, pendiente de respuesta del usuario. No se
actualizaron versionName, versionCode ni el helper mientras falta esa decisión.

El keystore y las herramientas oficiales están disponibles. El helper exige
entrada local sin eco desde /dev/tty y las contraseñas no están configuradas en
el entorno de esta sesión. No solicitarlas en chat ni introducirlas en argumentos
o registros. No se compiló ni firmó una nueva APK release. No hubo commit/push.

## Preparación vigente — 0.7.0 / 21, informes y temas simplificados

El usuario autoriza empaquetar el estado actual para pruebas en su teléfono.
Las funcionalidades compatibles nuevas requieren MINOR: `versionName 0.7.0`,
`versionCode 21`. Se conserva intacta `Arachn0de-v0.6.0-code20.apk` y su checksum;
el helper la incorpora a las referencias oficiales de actualización.
No se modifican funcionalidades, Diagramas, identidad, clave, alias, Room 48
ni compatibilidad JSON 40. No se repiten pruebas PDF/DOCX ni Temas aprobadas,
no se renuevan evidencias nativas históricas y no se instala en ningún dispositivo.

Procedimiento oficial, desde la terminal local del usuario:

```bash
cd /home/r0ybt/AndroidStudioProjects/Arachn0de
bash release-assets/preparar-apk-0.3.0.sh --check
bash release-assets/preparar-apk-0.3.0.sh
```

El segundo comando lee las contraseñas desde `/dev/tty` sin eco, utilizando
exclusivamente el keystore oficial existente
`/home/r0ybt/.local/share/arachn0de/signing/arachn0de-release.jks`, alias
`arachn0de-release`. Enter en la segunda reutiliza la primera. No introducir
contraseñas en chat, argumentos, registros ni archivos. Esta sesión no dispone
de esas credenciales ni de la entrada interactiva del usuario: compilación,
firma y entrega permanecen pendientes hasta ejecutar el helper localmente.

El helper conserva `./gradlew assembleRelease --offline --console=plain
--no-configuration-cache --no-daemon --max-workers=2`. Solo tras verificar firma,
certificado oficial, paquete, versión 0.7.0 / 21, ausencia de debuggable e
instrumentación, allowBackup deshabilitado, ZIP, texturas y SQLCipher ARM,
copia la APK al destino y genera su SHA-256. No sobrescribe entregas anteriores.
La compatibilidad se comprueba contra referencias oficiales locales; no acredita
la versión o firma exactas instaladas en el teléfono.

Preflight 0.7.0 / 21 aprobado: 14 APK oficiales locales con paquete y certificado
coincidentes y códigos inferiores, incluida 0.6.0 / 20. Sintaxis Bash y tres bloques
Python correctos; `git diff --check` limpio. La APK 0.6.0 / 20 y su checksum
conservan sus SHA-256 previos. Revisión del contrato de backup realizada sobre las
validaciones ya aprobadas de imágenes/encuadres, restauración, corrupción y
rollback; no se repiten suites. Keystore disponible, sin credenciales locales
configuradas. No se ejecuta compilación ni firma sin entrada local de contraseñas.

Salida prevista, todavía no generada ni firmada:
`/home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/Arachn0de-v0.7.0-code21.apk`
y su `.apk.sha256`. No declarar completada la entrega sin verificar ambos.
Sin commit, push, tags ni publicación. Los apartados siguientes son históricos.

## Preparación vigente — 0.6.0 / 20, Wireframe y moldes de pantallas

Las nuevas funcionalidades compatibles requieren incrementar MINOR, según la
convención MAJOR.MINOR.PATCH: versionName 0.6.0 y versionCode 20. La APK oficial
local 0.5.1 / 19 y su checksum se conservan intactos y se añaden a las referencias
de compatibilidad del helper. Los apartados siguientes son antecedentes históricos.

Se empaqueta el estado actual del proyecto, incluidas las correcciones de Wireframe
y los 25 moldes editables. No se modifican funcionalidades, identidad, keystore,
alias ni persistencia. Las pruebas dirigidas ya realizadas no se repiten aquí.
No se renuevan evidencias nativas históricas ni se acredita la instalación física.

Ejecutar en la terminal local del usuario:

```bash
cd /home/r0ybt/AndroidStudioProjects/Arachn0de
bash release-assets/preparar-apk-0.3.0.sh --check
bash release-assets/preparar-apk-0.3.0.sh
```

El helper usa exclusivamente el keystore oficial existente
`/home/r0ybt/.local/share/arachn0de/signing/arachn0de-release.jks`, alias
`arachn0de-release`. Solicita ambas contraseñas mediante `/dev/tty` sin eco;
Enter en la segunda reutiliza la primera. No introducirlas en chat, argumentos,
registros ni archivos. Codex no dispone de la entrada interactiva del usuario.
La compilación y firma quedan pendientes de esa ejecución local.

Se conserva el comando oficial `./gradlew assembleRelease --offline --console=plain
--no-configuration-cache --no-daemon --max-workers=2`. Después se verifican firma,
certificado oficial, paquete, versión 0.6.0 / 20, ausencia de debuggable e
instrumentación, allowBackup deshabilitado, integridad ZIP, texturas y SQLCipher ARM.
Solo tras aprobar se copia la APK y se genera el checksum. Una salida ya existente
bloquea el procedimiento; no se sobrescriben APK anteriores.

Preflight 0.6.0 / 20 aprobado con las 13 referencias oficiales locales hasta
código 19. Sintaxis Bash y de los tres bloques Python correctas; `git diff --check`
aprobado. La APK de código 19 y su checksum conservan sus hashes anteriores.
No se ejecutó compilación ni firma: falta la entrada local de las contraseñas.

Salida prevista, todavía no generada ni verificada:
`/home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/Arachn0de-v0.6.0-code20.apk`
y su `.apk.sha256`. No declarar completada la entrega hasta verificar el artefacto.
No se instala, publica, hace commit ni push.

## Preparación vigente — 0.5.1 / 19, corrección de módulos inferiores

La APK local `release-assets/Arachn0de-v0.5.0-code18.apk` ya existe: declara
0.5.0 / 18 y su certificado coincide con el oficial
`d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`.
Se conserva. Esta intervención correctiva incrementa PATCH y versionCode,
según la convención del proyecto: 0.5.1 / 19.

La unificación global ya está implementada y sus seis pruebas dirigidas aprobaron;
no se repiten durante el empaquetado. No hay cambios de persistencia, modelos ni
formato de backup por esta corrección. Se conservan Pomodoro, fechas especiales
y cumpleaños con la cobertura y validaciones ya documentadas. Las comprobaciones
de teclado, orientación física y captura simultánea con sonidos quedan pendientes
en teléfono; no bloquean el empaquetado.

El helper histórico conserva la firma, alias y procedimiento existentes y verifica
también la APK de código 18. Nunca reemplaza el archivo de distribución de salida.
Comando desde la raíz del repositorio:

```bash
bash release-assets/preparar-apk-0.3.0.sh --check
bash release-assets/preparar-apk-0.3.0.sh
```

El segundo comando lee las dos contraseñas desde `/dev/tty`, sin eco. No incluirlas
en chat, argumentos, registros ni archivos del proyecto. El keystore oficial está
disponible; al preparar esta versión no hay contraseñas en el entorno ni en las
propiedades de firma configuradas. No se declara una APK nueva como firmada.
El helper compila con `./gradlew assembleRelease --offline --console=plain
--no-configuration-cache --no-daemon --max-workers=2` y comprueba certificado,
paquete, versión, ZIP, recursos, bibliotecas y ausencia de debuggable/instrumentación.
No ejecuta suites ni renueva evidencias históricas.

Validación de esta preparación: `--check` aprobado, incluidas las referencias
oficiales hasta código 18; sintaxis del helper y `git diff --check` correctos.
`:app:compileDebugKotlin` aprobado (35 s). No se repitieron las seis pruebas
dirigidas ya aprobadas. La firma sigue pendiente de entrada local de credenciales.

Salida prevista, pendiente de generar y verificar:
`release-assets/Arachn0de-v0.5.1-code19.apk` y su `.apk.sha256`.
La compatibilidad de actualización se verifica con las APK oficiales locales;
no acredita la versión instalada en el teléfono. No se instala, publica,
hace commit ni push.

Los apartados siguientes son antecedentes históricos; la preparación de código 18
allí descrita ya tiene una APK local, comprobada al preparar el código 19.

## Empaquetado local preparado — 0.5.0 / 18

La convención MAJOR.MINOR.PATCH requiere MINOR para las nuevas funcionalidades
compatibles. La última APK oficial local es 0.4.2 / 17; se conserva íntegramente.
No se cambian funciones, interfaz, almacenamiento, keystore, alias ni applicationId.

Ejecutar desde la terminal integrada propia, sin contraseñas en el comando:

```bash
cd /home/r0ybt/AndroidStudioProjects/Arachn0de
bash release-assets/preparar-apk-0.3.0.sh
```

El helper histórico se actualiza únicamente para la versión y referencias de este
empaquetado; no se automatiza un nuevo sistema de releases. Lee las contraseñas
desde /dev/tty sin eco; Enter en la segunda reutiliza la primera. No introducir
contraseñas en el chat. El terminal de herramientas no ofrece entrada directa
del usuario y las credenciales no están disponibles en el entorno ni en Gradle.

Compila mediante `./gradlew assembleRelease --offline --console=plain
--no-configuration-cache --no-daemon --max-workers=2`. Verifica certificado oficial,
paquete, versión, no-debuggable, ZIP, recursos y bibliotecas. No repite suites ni
renueva evidencias históricas de verificar-seguridad-release.py.

Salida prevista (todavía no generada):
`release-assets/Arachn0de-v0.5.0-code18.apk` y su `.apk.sha256`.
El helper impide reemplazar una APK previa con ese nombre. `--check` comprueba
los antecedentes y requisitos sin pedir contraseñas ni compilar.

Preflight aprobado para 0.5.0 / 18: once APK oficiales con certificado coincidente
y códigos inferiores. Sintaxis del helper y `git diff --check` correctos.
La compilación y la firma quedan pendientes de introducir las credenciales.
No se acredita la firma o versión instalada en el teléfono ni una actualización
física. Las secciones siguientes conservan antecedentes históricos.

# Publicar Arachn0de manualmente

## Preparación actual — 0.4.1 / 15, firma original mediante Android Studio

Se prepara código 15 porque ya existe una APK oficial local de código 14. Se
conservan versionName 0.4.1, applicationId com.r0ybt.arachn0de y el keystore
original. No se ha comprobado la versión exacta instalada en el teléfono.
No se modifican funciones, Room 40 ni JSON 32. Las pruebas aprobadas no se repiten.

Las credenciales no están disponibles en el entorno ni en las propiedades
externas de Gradle durante esta preparación. No hay una APK nueva firmada.
El build reconoce las propiedades de firma inyectadas por el asistente de
Android Studio además del mecanismo externo previo; no se incorporan secretos
al repositorio. La copia oficial de código 14 se conserva sin reemplazarla.

Completar en Android Studio:

1. Build → Generate Signed Bundle / APK → APK.
2. Módulo app. En Key store path seleccionar el archivo existente
   /home/r0ybt/.local/share/arachn0de/signing/arachn0de-release.jks.
3. En Key store password introducir la contraseña únicamente en el asistente.
   Key alias: arachn0de-release. Key password: contraseña original de la clave.
   No crear un keystore nuevo ni guardar contraseñas en archivos del proyecto.
   Dejar desmarcada la opción de recordar contraseñas si aparece.
4. Elegir Release. Usar como carpeta de destino
   /home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/code15.
   Al terminar, usar Locate para obtener la ruta exacta que indique Android Studio.
5. Verificar que la APK nueva declara 0.4.1 / 15, com.r0ybt.arachn0de y el
   certificado SHA-256 oficial
   d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437.
   Instalar como actualización sobre la aplicación existente, sin desinstalarla.

La salida habitual de Gradle es app/build/outputs/apk/release/app-release.apk;
el asistente puede copiarla a su carpeta de destino. Un archivo anterior en esa
ruta no acredita la nueva compilación: comprobar metadatos y firma antes de
entregarlo. En esta preparación no se generan checksums de una APK inexistente,
no se instala en ningún dispositivo y no se hacen commit ni push.

El helper histórico preparar-apk-0.3.0.sh está fijado a código 14 y a su preflight
anterior; no utilizarlo para esta preparación. Se conserva como antecedente,
sin ejecutar pruebas ni modificar la evidencia anterior. Las secciones siguientes
son históricas y no sustituyen el estado actual de código 15.

## Cierre actual — 0.4.1 / 14, preflight aprobado; firma manual pendiente

El código 13 ya tiene una APK oficial local. Se conserva ese archivo y se prepara código 14 para actualización, sin cambiar versionName, applicationId, clave ni alias. El helper habitual añade código 13 como referencia y mantiene todos sus controles y la lectura silenciosa desde /dev/tty. Salida prevista, todavía no generada: release-assets/Arachn0de-v0.4.1-code14-BlackWhite90.apk.

El usuario autorizó expresamente ADB solo en emulador para renovar las seis pruebas oficiales, con paquete aislado y datos sintéticos. Se utilizó emulator-5554 (API 37, páginas de 16384 bytes) y com.r0ybt.arachn0de.securityvalidation / código 14. El checkout temporal coincide con el oficial tras la normalización de applicationId prevista en el verificador. Aprobaron tres pruebas SQLCipher y las migraciones genuinas 26/36/37→38. Evidencia y logs renovados solo después de aprobar; los anteriores se conservan en release-assets/native-evidence-before-code14-20261012T234318Z/.

El comando oficial --check quedó aprobado con ocho referencias locales (códigos 5, 6, 7, 9, 10, 11, 12 y 13), mismo paquete y certificado oficial. La instalación exacta del teléfono sigue sin comprobarse. No se conectó un teléfono, no se instaló el paquete principal y no se solicitó ninguna contraseña de firma.

El siguiente paso corresponde al usuario, desde la raíz del proyecto en su propia terminal:

```bash
bash release-assets/preparar-apk-0.3.0.sh
```

El helper solicitará silenciosamente la contraseña del keystore y la clave; Enter en la segunda reutiliza la primera. No introducirlas en chat, argumentos o logs. Solo después construirá y verificará la APK oficial, la copiará al destino y generará su SHA-256. No se ejecuta la firma desde el agente ni se informa una APK nueva como generada. No modificar fuentes entre esta validación y la firma: invalidaría otra vez la evidencia.

## Preparación pendiente — 0.4.1 / 13 (Black & White 90)

Autorizada la versión correctiva 0.4.1 / código 13. No se encontraron artefactos ni reservas locales de esta numeración. Se conserva `com.r0ybt.arachn0de`, la clave y el certificado oficiales, Room 38 y JSON 30. El helper histórico mantiene todas sus protecciones y añade la APK oficial 0.4.0 / 12 como referencia de actualización.

Salida prevista: `release-assets/Arachn0de-v0.4.1-code13-BlackWhite90.apk`; no sobrescribir artefactos anteriores. No generada ni firmada todavía. Las seis pruebas nativas obligatorias aprobaron sobre este checkout en emulator-5554, exclusivamente con com.r0ybt.arachn0de.securityvalidation y datos sintéticos, según autorización expresa. Se renovaron la huella y los hashes de logs; la evidencia anterior permanece en release-assets/native-evidence-before-code13/. No se usó un teléfono físico ni se instaló el paquete principal.

Los comandos del helper y la lectura silenciosa de contraseñas permanecen iguales. La preparación 0.4.0 / 12 descrita a continuación es el registro de la release anterior.


## Preparación vigente — 0.4.0 / 12 (ajustes visuales y UX 1–6)

**Preferencia del usuario:** la instalación habitual se realiza enviándose la APK por
WhatsApp como Documento y abriéndola en el teléfono para actualizar. No utiliza ADB
como flujo habitual de instalación. Los comandos ADB de este documento son una vía
opcional de comprobación, no un requisito de su forma de instalar. La comprobación de
compatibilidad pendiente deberá resolverse con un método acordado antes de firmar;
el usuario autorizó comprobar los antecedentes locales y declarar la limitación de
la instalación desconocida. No asumir conexión ADB.

Se prepara `com.r0ybt.arachn0de`, versión **0.4.0**, código **12**, Room **38** y
backup lógico hasta JSON **30**. No se cambian identidad, clave, alias, `allowBackup`,
almacenamiento ni mecanismos de seguridad. La versión menor recoge Gestión de proyectos
integrada; el código supera todas las APK oficiales locales conservadas (hasta 11).
La clave oficial sigue en `~/.local/share/arachn0de/signing/arachn0de-release.jks`,
alias `arachn0de-release`, certificado SHA-256
`d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`.

**Firma autorizada expresamente para 0.4.0 / 12.** El usuario permite preparar la APK
sin conectar el teléfono por ADB. Se verifican previamente las seis referencias oficiales
locales (códigos 5, 6, 7, 9, 10 y 11): mismo applicationId y certificado oficial,
códigos inferiores a 12. La versión y firma exactas instaladas en el teléfono no se
han podido verificar; esta limitación no bloquea la preparación autorizada. No se
instala ni se modifica ningún dispositivo.

Las 39 pruebas dirigidas y seis nativas ya aprobaron y no se repiten. La evidencia
nativa del checkout se verifica con su huella y logs íntegros, incluidos los casos
SQLCipher y migraciones genuinas 26/36/37→38. Se conserva la evidencia anterior.

El helper conserva el nombre histórico `release-assets/preparar-apk-0.3.0.sh` y la
lectura silenciosa desde `/dev/tty`, utilizando la clave y alias oficiales habituales.
No escribir contraseñas en chat, argumentos, scripts ni registros. Ejecutar desde
la raíz del proyecto en una terminal local:

```bash
bash release-assets/preparar-apk-0.3.0.sh --check
bash release-assets/preparar-apk-0.3.0.sh
```

La variable `ARACHNODE_INSTALLED_APK` es opcional. Si se proporciona una copia local,
se comprueba también; no se solicita conexión ADB. Sin ella se informa la limitación
expresamente autorizada. El helper comprueba referencias oficiales antes de pedir
contraseñas y vuelve a verificar el artefacto generado antes de copiarlo.

APK oficial generada y verificada: `release-assets/Arachn0de-v0.4.0-code12.apk`
(153.37 MiB) y su `.apk.sha256`. Firma oficial válida; versión 0.4.0 / 12.
SHA-256: `89910b3acd81f2ddcb99d0b6665073d916d1f896acff0747629dae6b63a1611d`.
No se considera lista hasta firmar y verificar ZIP, certificado, identidad, versión,
ausencia de debuggable/instrumentación, recursos y bibliotecas ARM. No se cambian
políticas de almacenamiento ni backup. Se conservan APK anteriores y se bloquea una
salida ya existente. No se envía, instala ni publica automáticamente.

Después de verificar, compartir como Documento por WhatsApp y abrir en el teléfono
para actualizar. No desinstalar ni borrar datos para resolver una eventual firma
incompatible; la compatibilidad acreditada es con las referencias oficiales locales.

Los apartados siguientes son registros históricos; sus versiones y evidencias no
sustituyen las comprobaciones de 0.4.0 / 12.


## Estado después de la carrera táctica

Se conserva **0.3.0 / 10** y la clave oficial; este sprint no crea una release ni incrementa la versión. La APK de revisión del juego es `release-assets/Arachn0de-v0.3.0-code10-carrera-DEBUG.apk`, firmada con Debug: **no puede reemplazar la instalación oficial**. La APK oficial anterior se conserva.

La evidencia nativa se renovó sobre este checkout después de aprobar las tres pruebas oficiales SQLCipher en un paquete aislado del emulador. Otras dos pruebas nativas aprobaron la recuperación cifrada de una carrera con premio pendiente y la migración 26→27 de ambos almacenes con fotografías y respaldos. `--check` vuelve a aprobar sin contraseñas. Las advertencias de evidencia anterior en los apartados históricos describen esos sprints, no esta validación. Cualquier cambio posterior en fuentes o recursos vuelve a invalidar la huella.

El helper manual mantiene exactamente keystore, alias y lectura de contraseñas anteriores. Ahora verifica también los veinte PNG de la carrera junto a las cinco texturas históricas, comparando sus bytes con el APK. No se ha ejecutado la firma oficial. Para generarla manualmente se conserva el comando indicado más abajo; los resultados del juego y los límites de instalación se detallan en [GAME_TACTICAL_RACE.md](GAME_TACTICAL_RACE.md).


Preparación vigente para WhatsApp: **0.3.0 / 10**, `com.r0ybt.arachn0de`, con la firma oficial existente. El código 10 permite actualizar la APK 0.3.0 / 9. No cambia el esquema ni el formato de backup por este incremento.

Ejecutar desde la raíz del repositorio:

```bash
bash release-assets/preparar-apk-0.3.0.sh
```

El script reutiliza exclusivamente `~/.local/share/arachn0de/signing/arachn0de-release.jks` y el alias `arachn0de-release`. Solicita solo las dos contraseñas mediante la terminal, sin mostrarlas; Enter en la segunda reutiliza la primera. No solicita rutas ni alias, no crea claves y no guarda credenciales en archivos. Las variables de contraseñas se eliminan al finalizar. No ejecutar con trazas de shell, logs de depuración o build scans.

Antes de pedir contraseñas exige evidencia íntegra de tres pruebas nativas SQLCipher aprobadas y huella coincidente del código probado. Si falta evidencia, hay fallos o cambia el código, bloquea la distribución. `bash release-assets/preparar-apk-0.3.0.sh --check` comprueba esta preparación sin firmar ni solicitar contraseñas.

Tras compilar Release verifica firma y certificado oficial, identificador, 0.3.0 / 10, ausencia de debuggable e instrumentación, backup automático deshabilitado, integridad ZIP y bibliotecas SQLCipher ARM. Compara identidad, certificado y código con las APK oficiales anteriores. Comprueba bytes idénticos de los cinco PNG definitivos, incluido el nombre UTF-8 `araña.png`.

Solo después copia a `release-assets/Arachn0de-v0.3.0-code10-seguridad.apk` y genera su `.apk.sha256`. Conserva la APK 0.3.0 / 9. Estos archivos locales están ignorados por Git. No se ha ejecutado la firma durante esta preparación. Exportar y conservar un respaldo recuperable antes de instalar; actualizar sin desinstalar ni borrar datos. Ver [RELEASE_SECURITY_0.3.0_CODE10.md](RELEASE_SECURITY_0.3.0_CODE10.md) para resultados y límites de validación.

**Después del sprint de dogfooding 1:** la evidencia nativa conservada corresponde a la preparación anterior. El código de interfaz y guardado de proyectos cambió; el preflight debe bloquear la firma hasta una nueva validación nativa del checkout. No actualizar su huella sin ejecutar las pruebas. Este sprint no genera ni firma una nueva APK de distribución.

Los apartados de v0.2.4 siguientes documentan el procedimiento histórico; sus versiones y baterías de pruebas no son pasos adicionales para esta preparación.

Repositorio y canal oficial: https://github.com/bastideveloper1/Arachn0de.
Este documento prepara v0.2.4 Beta (Room 16 / Backup lógico 9); no autoriza commit, push, tag ni publicación. El updater interno ya fue validado físicamente en v0.2.1 → v0.2.2.

## Versión y requisitos

Se prepara **Arachn0de v0.2.4 Beta**, `versionName = "0.2.4"`, `versionCode = 6`, desde la Release instalada `0.2.3 / 5`. Existen los tags históricos v0.2.0, v0.2.1, v0.2.2 y v0.2.3.

En `app/build.gradle.kts`, usar MAJOR.MINOR.PATCH: PATCH para correcciones, MINOR para funcionalidades compatibles, MAJOR para cambios importantes/incompatibles. Incrementar siempre `versionCode`: nunca reutilizarlo ni disminuirlo, incluso para una Beta. La versión del menú viene de `BuildConfig.VERSION_NAME`.

Herramientas comprobadas en este equipo:

```bash
export JAVA_HOME=/opt/android-studio/jbr
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/home/r0ybt/Android/Sdk
```

`apksigner` y `aapt` están en `$ANDROID_HOME/build-tools/36.1.0/`. En otro equipo localizar las herramientas instaladas antes de adaptar estas rutas. Ejecutar todo desde la raíz del checkout.

## Conservar la clave oficial existente

Esta actualización debe reutilizar la keystore oficial y su alias. No crear otra clave ni sustituirla por Debug. Mantener la clave fuera del repositorio y respaldarla de forma segura. No imprimir ni guardar contraseñas en comandos, documentos o Git.

Una instalación Debug tiene otra firma: Android no permite reemplazarla directamente con Release del mismo applicationId. Probar inicialmente Release en un dispositivo/perfil separado; desinstalar la instalación anterior elimina sus datos locales. No hay migración entre identidades de firma implementada.

## Credenciales locales

Para la preparación vigente usar únicamente el helper indicado al inicio. La ruta y el alias oficiales se fijan automáticamente; las contraseñas se leen con `read -s` desde `/dev/tty` y se exportan solo para la ejecución. No colocarlas en `gradle.properties`, argumentos `-P`, documentación ni chat. Release falla si faltan las credenciales oficiales y nunca sustituye su firma por Debug.

## Construir, verificar y preparar assets

1. Revisar `git remote -v`: origin debe ser `https://github.com/bastideveloper1/Arachn0de.git`. Confirmar versión/código y actualizar CHANGELOG.md con cambios realmente implementados.
2. Validar primero tests, Debug y lint. La suite global `:app:testDebugUnitTest` verifica Debug sin requerir credenciales Release. Cualquier tarea que dependa de `preReleaseBuild` requiere las credenciales oficiales; no desactivar esa protección. Clasificar fallos sin ocultar los históricos. Si faltan las cuatro credenciales, detener la preparación del APK Release. Con la clave oficial configurada, construir:

```bash
./gradlew :app:testDebugUnitTest --continue --console=plain --no-configuration-cache
./gradlew assembleDebug --console=plain
./gradlew lintDebug --console=plain
git diff --check
# Solo con las cuatro credenciales oficiales disponibles:
./gradlew assembleRelease --console=plain --no-configuration-cache
```

3. El resultado esperado es **`app/build/outputs/apk/release/app-release.apk`**. No distribuir `app-debug.apk` ni archivos `*-unsigned.apk`. Verificar:

```bash
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.1.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
keytool -list -v -keystore "$ARACHNODE_KEYSTORE_PATH" -alias "$ARACHNODE_KEY_ALIAS"
```

La verificación debe finalizar correctamente. Comparar el SHA-256 del certificado de apksigner con el de keytool y con el certificado release previamente conservado. Confirmar package `com.r0ybt.arachn0de`, `versionName='0.2.4'`, `versionCode='6'` y ausencia de `application-debuggable`. No confundir el hash del certificado con el hash del APK.

4. Preparar un nombre neutral y checksums:

```bash
mkdir -p release-assets
cp app/build/outputs/apk/release/app-release.apk release-assets/Arachn0de-v0.2.4.apk
cd release-assets
sha256sum Arachn0de-v0.2.4.apk
sha256sum Arachn0de-v0.2.4.apk > SHA256SUMS.txt
sha256sum -c SHA256SUMS.txt
cd ..
```

El hash debe calcularse sobre el archivo exacto que se adjuntará. Si se reconstruye o reemplaza el APK, repetir copia y hash. `release-assets/` está ignorado: distribuir estos archivos como assets, no como código fuente.

## Commit, push y tag — acciones posteriores del usuario

Estos comandos NO se ejecutan durante la preparación. Inspeccionar primero:

```bash
git diff --check
git status --short
git diff
git branch --show-current
```

Confirmar la rama `main`, revisar que no haya secretos y añadir únicamente archivos revisados:

```bash
git add app/build.gradle.kts ARCHITECTURE.md CHANGELOG.md RELEASING.md RELEASE_NOTES_v0.2.4.md
git diff --cached
git commit -m "chore: prepare Arachn0de v0.2.4 beta"
git push origin main
```

Si esta preparación incluye correcciones de código/pruebas, revisar y añadir también esos archivos explícitamente; no usar un add indiscriminado. Después, comprobar checkout limpio y reconstruir Release desde ese commit exacto. Repetir verificación, copia y hash anteriores; si cambia código/versionado, crear un nuevo commit y volver a construir antes de etiquetar. No mover ni reutilizar tags publicados.

```bash
git status --short
git rev-parse HEAD
./gradlew assembleRelease --console=plain --no-configuration-cache
```

Una vez verificados los assets del commit:

```bash
git tag -a v0.2.4 -m "Arachn0de v0.2.4 Beta" HEAD
git rev-list -n 1 v0.2.4
git push origin v0.2.4
```

El commit mostrado para el tag debe coincidir exactamente con el commit del APK. Si `v0.2.4` ya existe, detenerse y verificarlo; no forzar su reemplazo.

## GitHub Release manual

Abrir https://github.com/bastideveloper1/Arachn0de/releases/new. Elegir el tag **existente** `v0.2.4`, título **Arachn0de v0.2.4 Beta**, marcar **pre-release** y usar el borrador revisado de `RELEASE_NOTES_v0.2.4.md`.
Adjuntar `release-assets/Arachn0de-v0.2.4.apk` y `release-assets/SHA256SUMS.txt`. Revisar tag, versión, notas y archivos antes de pulsar Publish release. Descargar manualmente los assets publicados y comprobar `sha256sum -c SHA256SUMS.txt`, firma e instalación en un entorno de prueba. No requiere token GitHub dentro de la aplicación.

Al terminar la sesión local:

```bash
unset ARACHNODE_KEYSTORE_PASSWORD ARACHNODE_KEY_PASSWORD
```

Preparar la siguiente versión según el tipo de cambio, con código Android **mayor que 6** y mayor que todos los códigos distribuidos; nueva entrada de changelog y nuevo tag. Conservar la misma clave release.

## Actualizador y privacidad

El único canal de actualización será GitHub Releases de **bastideveloper1/Arachn0de**. El actualizador usa metadatos de la Release, tag, notas, APK y checksum. No se modificó durante esta preparación.

**ARACHN0DE NUNCA REALIZA UNA ACTUALIZACIÓN SILENCIOSA.**

Flujo: nueva GitHub Release → detección → informar versión y cambios → usuario elige **Descargar / Ahora no** → descargar solo con autorización → usuario decide instalar → Android controla y autoriza la instalación. Sin descarga/instalación silenciosa, actualización obligatoria ni updater en background sin conocimiento del usuario.

La comprobación consulta solo metadatos necesarios de Releases. Nunca enviará Projects, Nodes, Notes, Personas, responsables, obligaciones, montos, estadísticas, contenido exportado ni información personal de Arachn0de. Sin analytics ni telemetría.

Referencias oficiales: [firma Android](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [GitHub Releases manuales](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).

## Control de cierre v0.2.4

La actualización desde v0.2.3 (`0.2.3 / 5`, Room 14) usa la cadena explícita
14→15 (LAYER) →16 (Modo Sprint). No se usa migración destructiva.
Backup lógico v9 conserva Capas vacías y estados Sprint y lee v1–v8.
Crear un backup antes de probar la actualización física; probar conservación de
datos y restore en un entorno adecuado antes de publicar.

El APK local anterior `release-assets/Arachn0de-v0.2.3.apk` verifica correctamente
con apksigner y declara `com.r0ybt.arachn0de`, `0.2.3 / 5`. Su certificado SHA-256
es `d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`.
Comparar el certificado del nuevo APK con esta referencia y con la clave
oficial conservada. Esto no acredita la instalación física del nuevo APK.

Si faltan credenciales, ejecutar localmente el bloque Bash de «Credenciales
locales» y después `./gradlew assembleRelease --console=plain
--no-configuration-cache`. No compartir contraseñas en el chat. Sin APK nuevo
firmado/verificado no generar ni publicar SHA256SUMS de v0.2.4.

## Lista de verificación de persistencia y backup

Antes de preparar los assets de un release, revisar el **Contrato de persistencia y backup** de ARCHITECTURE.md:

- [ ] Todas las tablas, columnas, preferencias, configuraciones, archivos y relaciones nuevos tienen exportación y restauración implementadas; las exclusiones temporales están documentadas.
- [ ] El respaldo incluye los bytes necesarios para reconstruir imágenes e iconos en una instalación vacía, sin rutas del dispositivo de origen.
- [ ] El formato está versionado y los respaldos anteriores siguen siendo legibles.
- [ ] Pasaron pruebas dirigidas de round-trip, integridad, archivos faltantes/corruptos y fallo de restauración sin pérdida del estado previo.
- [ ] Las migraciones necesarias preservan los datos existentes; se documentan límites y resultados de compilación/pruebas.

- [ ] Verificar el contrato de liberación de archivos: última referencia, reservas, cancelación, caché acotada y recuperación de limpieza pendiente.

Si hay datos nuevos que el backup no puede recuperar, la función no está terminada y no debe darse por lista para release. Esta revisión complementa las comprobaciones existentes; no modifica el protocolo de firma, versionado o publicación ni autoriza commit, push o tags.


## Metro Beta 1 (dogfooding, sin release automático)

Se conserva applicationId `com.r0ybt.arachn0de`, versión 0.3.0 / 9 y la configuración de firma oficial. Este sprint genera únicamente `assembleDebug`: una APK debug no puede actualizar una instalación con la firma release. No desinstalar ni borrar datos para resolver esa diferencia. Probar la Beta sobre una instalación debug compatible o en otro dispositivo/perfil; un futuro release firmado necesita autorización y seguir el protocolo anterior.

Antes de distribuir Metro, comprobar migración 23→24, backup v16 y restauración de versiones anteriores, acciones de notificación, pausa con pantalla bloqueada, corrección directa, Personas y regreso tras terminar el proceso. La verificación física de bloqueo, gestión de batería del fabricante, force-stop, reinicio y Android recientes requiere dogfooding en el equipo real; las pruebas JVM no acreditan esas políticas del dispositivo.

El APK release firmado existente en `release-assets/Arachn0de-v0.3.0.apk` precede a Metro. No confundirlo con la nueva Beta debug ni anunciar Metro como incluido en ese artefacto.


## Verificación adicional del sprint de seguridad (sin release)

Se conserva 0.3.0 / 9, applicationId y firma oficial. Room 26 añade preferencias privadas y el backup JSON 18 añade preferencias/identidad dentro de ANBACK01 autenticado. Los lectores históricos permanecen; una versión anterior de la app no puede abrir el almacén cifrado ni bajar Room 26. No probar un downgrade sobre datos nuevos.

Antes de una futura distribución autorizada, comprobar en Android real `NativeVaultTest`, migración de una copia representativa, contraseña/cambio/señuelo, bytes de bases/WAL/imágenes/temporales, interrupción/falta de espacio, round-trip de backup y regreso desde segundo plano/SAF. Las pruebas nativas compiladas pero no ejecutadas no acreditan la migración física ni el comportamiento del fabricante. Ver resultados reales y limitaciones en `SECURITY_SPRINT.md`.

Este sprint permite únicamente compilación debug y de su APK de pruebas, sin instalación, firma release, commit, push o publicación. El APK release anterior permanece anterior a este sprint.
