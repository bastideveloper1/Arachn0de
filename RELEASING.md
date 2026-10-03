# Publicar Arachn0de manualmente

Repositorio y canal oficial: https://github.com/bastideveloper1/Arachn0de.
Este documento prepara #30–32; no publica nada. #33–34 están pendientes.

## Versión y requisitos

Se prepara **Arachn0de v0.2.0 Beta**, `versionName = "0.2.0"`, `versionCode = 2`, desde la base `0.1.0 / 1`. No hay tags en el checkout inspeccionado.

En `app/build.gradle.kts`, usar MAJOR.MINOR.PATCH: PATCH para correcciones, MINOR para funcionalidades compatibles, MAJOR para cambios importantes/incompatibles. Incrementar siempre `versionCode`: nunca reutilizarlo ni disminuirlo, incluso para una Beta. La versión del menú viene de `BuildConfig.VERSION_NAME`.

Herramientas comprobadas en este equipo:

```bash
export JAVA_HOME=/opt/android-studio/jbr
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/home/r0ybt/Android/Sdk
```

`apksigner` y `aapt` están en `$ANDROID_HOME/build-tools/36.1.0/`. En otro equipo localizar las herramientas instaladas antes de adaptar estas rutas. Ejecutar todo desde la raíz del checkout.

## Crear la clave definitiva, una sola vez

Si ya existe una clave release definitiva, reutilizarla. No sustituirla por otra para una actualización. Si no existe, el usuario debe ejecutar manualmente:

```bash
install -d -m 700 "$HOME/.local/share/arachn0de/signing"
keytool -genkeypair -v \
  -keystore "$HOME/.local/share/arachn0de/signing/arachn0de-release.jks" \
  -storetype JKS -alias arachn0de-release \
  -keyalg RSA -keysize 3072 -validity 10000
chmod 600 "$HOME/.local/share/arachn0de/signing/arachn0de-release.jks"
```

Introducir contraseñas propias en los prompts de keytool y conservar el alias elegido. No incluir contraseñas en comandos, historial, documentos ni Git. Mantener la clave **fuera del repositorio**, junto con una copia de seguridad segura y sus credenciales: las futuras actualizaciones deben conservar la misma identidad de firma. Esta misión no crea la clave definitiva.

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
2. Construir Debug y Release:

```bash
./gradlew assembleDebug --console=plain
./gradlew assembleRelease --console=plain
```

3. El resultado esperado es **`app/build/outputs/apk/release/app-release.apk`**. No distribuir `app-debug.apk` ni archivos `*-unsigned.apk`. Verificar:

```bash
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.1.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
keytool -list -v -keystore "$ARACHNODE_KEYSTORE_PATH" -alias "$ARACHNODE_KEY_ALIAS"
```

La verificación debe finalizar correctamente. Comparar el SHA-256 del certificado de apksigner con el de keytool y con el certificado release previamente conservado. Confirmar package `com.r0ybt.arachn0de`, `versionName='0.2.0'`, `versionCode='2'` y ausencia de `application-debuggable`. No confundir el hash del certificado con el hash del APK.

4. Preparar un nombre neutral y checksums:

```bash
mkdir -p release-assets
cp app/build/outputs/apk/release/app-release.apk release-assets/Arachn0de-v0.2.0.apk
cd release-assets
sha256sum Arachn0de-v0.2.0.apk
sha256sum Arachn0de-v0.2.0.apk > SHA256SUMS.txt
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
git add .gitignore app/build.gradle.kts app/src/main/java/com/r0ybt/arachn0de/ui/NavigationChrome.kt ARCHITECTURE.md CHANGELOG.md RELEASING.md
git diff --cached
git commit -m "chore: prepare Arachn0de v0.2.0 beta"
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
git tag -a v0.2.0 -m "Arachn0de v0.2.0 Beta" HEAD
git rev-list -n 1 v0.2.0
git push origin v0.2.0
```

El commit mostrado para el tag debe coincidir exactamente con el commit del APK. Si `v0.2.0` ya existe, detenerse y verificarlo; no forzar su reemplazo.

## GitHub Release manual

Abrir https://github.com/bastideveloper1/Arachn0de/releases/new. Elegir el tag **existente** `v0.2.0`, título **Arachn0de v0.2.0 Beta**, marcar **pre-release** y pegar estas notas propuestas:

> Beta con mejoras de Capas de cebolla, Personas y responsables, fechas y Atención, Notas, creación múltiple y Calendario. Incluye Obligaciones con totales por moneda, períodos y Persona, informe PNG y copia de contexto estructurado. Distribución mediante APK Release firmado. La actualización desde la aplicación todavía no está disponible.

Adjuntar `release-assets/Arachn0de-v0.2.0.apk` y `release-assets/SHA256SUMS.txt`. Revisar tag, versión, notas y archivos antes de pulsar Publish release. Descargar manualmente los assets publicados y comprobar `sha256sum -c SHA256SUMS.txt`, firma e instalación en un entorno de prueba. No requiere token GitHub dentro de la aplicación.

Al terminar la sesión local:

```bash
unset ARACHNODE_KEYSTORE_PASSWORD ARACHNODE_KEY_PASSWORD
```

Preparar la siguiente versión según el tipo de cambio, con código Android **mayor que 2** y mayor que todos los códigos distribuidos; nueva entrada de changelog y nuevo tag. Conservar la misma clave release.

## Contrato futuro #33–34 y privacidad

El único canal de actualización será GitHub Releases de **bastideveloper1/Arachn0de**. La implementación futura necesitará versión publicada, tag, release notes, APK correcto, URL del asset y eventualmente SHA-256. Nada de ello se consulta desde la app en esta etapa.

**ARACHN0DE NUNCA REALIZA UNA ACTUALIZACIÓN SILENCIOSA.**

Flujo futuro obligatorio: nueva GitHub Release → detección → informar versión y cambios → usuario elige **Descargar / Ahora no** → descargar solo con autorización → usuario decide instalar → Android controla y autoriza la instalación. Sin descarga/instalación silenciosa, actualización obligatoria ni updater en background sin conocimiento del usuario.

La futura comprobación consultará solo metadatos necesarios de Releases. Nunca enviará Projects, Nodes, Notes, Personas, responsables, obligaciones, montos, estadísticas, contenido exportado ni información personal de Arachn0de. Sin analytics ni telemetría.

Referencias oficiales: [firma Android](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [GitHub Releases manuales](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).
