# Publicar Arachn0de manualmente

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
