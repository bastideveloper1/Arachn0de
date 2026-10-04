# Publicar Arachn0de manualmente

Repositorio y canal oficial: https://github.com/bastideveloper1/Arachn0de.
Este documento prepara v0.2.3 Beta; no autoriza commit, push, tag ni publicación. El updater interno ya fue validado físicamente en v0.2.1 → v0.2.2.

## Versión y requisitos

Se prepara **Arachn0de v0.2.3 Beta**, `versionName = "0.2.3"`, `versionCode = 5`, desde la Release instalada `0.2.2 / 4`. Existen los tags históricos v0.2.0, v0.2.1 y v0.2.2.

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

En Bash, configurar variables solo para la sesión:

```bash
export ARACHNODE_KEYSTORE_PATH="$HOME/.local/share/arachn0de/signing/arachn0de-release.jks"
export ARACHNODE_KEY_ALIAS=arachn0de-release
read -r -s -p 'Contraseña del keystore: ' ARACHNODE_KEYSTORE_PASSWORD
export ARACHNODE_KEYSTORE_PASSWORD
read -r -s -p 'Contraseña de la clave: ' ARACHNODE_KEY_PASSWORD
export ARACHNODE_KEY_PASSWORD
```

Alternativa: las mismas cuatro propiedades en **`$HOME/.gradle/gradle.properties`**, fuera del checkout, con permisos `600`. Las variables de entorno tienen precedencia. No colocarlas en el `gradle.properties` versionado ni en argumentos `-P`. Los caches y logs locales de Gradle deben tratarse como privados; evitar `--debug`, build scans y compartirlos con credenciales configuradas. Puede utilizarse `--no-configuration-cache` en los builds de Release para no almacenar su configuración.

Release comprueba presencia de las cuatro variables, ruta absoluta externa y archivo legible; errores de contraseña/alias también deben impedir la firma. Nunca se usa la clave Debug como sustituto ni se ofrece un APK sin firma. Debug no necesita estas credenciales. `.gitignore` protege keystores, archivos locales de firma y assets preparados; esto no protege secretos que ya estén tracked.

## Construir, verificar y preparar assets

1. Revisar `git remote -v`: origin debe ser `https://github.com/bastideveloper1/Arachn0de.git`. Confirmar versión/código y actualizar CHANGELOG.md con cambios realmente implementados.
2. Validar primero tests, Debug y lint. En este checkout, `test` ejecutó la suite Debug. Cualquier tarea que dependa de `preReleaseBuild` requiere las credenciales oficiales; no desactivar esa protección. Clasificar fallos sin ocultar los históricos. Si faltan las cuatro credenciales, detener la preparación del APK Release. Con la clave oficial configurada, construir:

```bash
./gradlew test --continue --console=plain --no-configuration-cache
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

La verificación debe finalizar correctamente. Comparar el SHA-256 del certificado de apksigner con el de keytool y con el certificado release previamente conservado. Confirmar package `com.r0ybt.arachn0de`, `versionName='0.2.3'`, `versionCode='5'` y ausencia de `application-debuggable`. No confundir el hash del certificado con el hash del APK.

4. Preparar un nombre neutral y checksums:

```bash
mkdir -p release-assets
cp app/build/outputs/apk/release/app-release.apk release-assets/Arachn0de-v0.2.3.apk
cd release-assets
sha256sum Arachn0de-v0.2.3.apk
sha256sum Arachn0de-v0.2.3.apk > SHA256SUMS.txt
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
git add app/build.gradle.kts ARCHITECTURE.md CHANGELOG.md RELEASING.md RELEASE_NOTES_v0.2.3.md
git diff --cached
git commit -m "chore: prepare Arachn0de v0.2.3 beta"
git push origin main
```

Después, comprobar checkout limpio y reconstruir Release desde ese commit exacto. Repetir verificación, copia y hash anteriores; si cambia código/versionado, crear un nuevo commit y volver a construir antes de etiquetar. No mover ni reutilizar tags publicados.

```bash
git status --short
git rev-parse HEAD
./gradlew assembleRelease --console=plain
```

Una vez verificados los assets del commit:

```bash
git tag -a v0.2.3 -m "Arachn0de v0.2.3 Beta" HEAD
git rev-list -n 1 v0.2.3
git push origin v0.2.3
```

El commit mostrado para el tag debe coincidir exactamente con el commit del APK. Si `v0.2.3` ya existe, detenerse y verificarlo; no forzar su reemplazo.

## GitHub Release manual

Abrir https://github.com/bastideveloper1/Arachn0de/releases/new. Elegir el tag **existente** `v0.2.3`, título **Arachn0de v0.2.3 Beta**, marcar **pre-release** y usar el borrador revisado de `RELEASE_NOTES_v0.2.3.md`.
Adjuntar `release-assets/Arachn0de-v0.2.3.apk` y `release-assets/SHA256SUMS.txt`. Revisar tag, versión, notas y archivos antes de pulsar Publish release. Descargar manualmente los assets publicados y comprobar `sha256sum -c SHA256SUMS.txt`, firma e instalación en un entorno de prueba. No requiere token GitHub dentro de la aplicación.

Al terminar la sesión local:

```bash
unset ARACHNODE_KEYSTORE_PASSWORD ARACHNODE_KEY_PASSWORD
```

Preparar la siguiente versión según el tipo de cambio, con código Android **mayor que 5** y mayor que todos los códigos distribuidos; nueva entrada de changelog y nuevo tag. Conservar la misma clave release.

## Actualizador y privacidad

El único canal de actualización será GitHub Releases de **bastideveloper1/Arachn0de**. El actualizador usa metadatos de la Release, tag, notas, APK y checksum. No se modificó durante esta preparación.

**ARACHN0DE NUNCA REALIZA UNA ACTUALIZACIÓN SILENCIOSA.**

Flujo: nueva GitHub Release → detección → informar versión y cambios → usuario elige **Descargar / Ahora no** → descargar solo con autorización → usuario decide instalar → Android controla y autoriza la instalación. Sin descarga/instalación silenciosa, actualización obligatoria ni updater en background sin conocimiento del usuario.

La comprobación consulta solo metadatos necesarios de Releases. Nunca enviará Projects, Nodes, Notes, Personas, responsables, obligaciones, montos, estadísticas, contenido exportado ni información personal de Arachn0de. Sin analytics ni telemetría.

Referencias oficiales: [firma Android](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [GitHub Releases manuales](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).
