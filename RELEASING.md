## Empaquetado local preparado — 0.4.2 / 16

Se recupera el helper histórico, sin cambiar keystore, alias, certificado,
applicationId ni configuración Gradle. Ejecutar desde la terminal integrada de
VS Code, sin contraseñas en el comando:

```bash
cd /home/r0ybt/AndroidStudioProjects/Arachn0de
bash release-assets/preparar-apk-0.3.0.sh
```

Lee las dos contraseñas originales desde /dev/tty sin eco. Enter en la segunda
reutiliza la primera. La compilación conserva --offline y --no-configuration-cache;
se añade --no-daemon para no reutilizar un daemon de Gradle con las credenciales.
La terminal de herramientas de Codex no ofrece entrada directa del usuario: usar
la terminal integrada propia, no escribir contraseñas en el chat.

El empaquetado actual no invoca verificar-seguridad-release.py: ese verificador
se conserva intacto para código 14 / Room 38 y no es un requisito de compilación
de cada ajuste visual. Su huella abarca todo app/src/main, por lo que usarlo como
bloqueo universal exigía renovar pruebas incluso tras cambios de interfaz.
No se modifican evidencias, huellas ni resultados para aparentar nuevas pruebas.
La aprobación histórica no acredita SQLCipher nativo ni migraciones del checkout
actual. Esta preparación verifica el artefacto y su compatibilidad de identidad y
firma, sin certificar conservación de datos en una instalación real.
Se mantienen la comprobación del certificado de la clave original, compatibilidad
con releases anteriores y todas las verificaciones finales del APK.

`--check` aprobado para 0.4.2 / 16: nueve referencias oficiales verificadas,
sin solicitar contraseñas ni compilar. No hay APK nueva todavía.

Tras introducir las contraseñas, el mismo comando compila Release, verifica
firma oficial, paquete, versión, no-debuggable, ZIP, recursos y bibliotecas, y crea
release-assets/Arachn0de-v0.4.2-code16.apk y su .apk.sha256. Imprime ruta absoluta,
tamaño y SHA-256. No hay todavía una APK nueva generada ni se acredita actualización
en teléfono. Las secciones siguientes conservan los antecedentes históricos.

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
