# Preparación de distribución 0.3.0 / 10

Se conserva `com.r0ybt.arachn0de`, versionName 0.3.0 y la firma oficial; versionCode aumenta de 9 a 10. No cambia el esquema Room 26 ni el formato de backup JSON 18 / ANBACK01.

## Validación

Tres pruebas instrumentadas SQLCipher aprobadas en 46,462 segundos en el emulador Android API 37, x86_64, páginas 16 KiB, mediante un paquete aislado. Incluyen migración de Room con IDs y multimedia, apertura cifrada y rechazo de clave incorrecta, aislamiento de almacenes, exportación/restauración del respaldo de recuperación y reintento conservando los originales tras interrupción. Los datos de la aplicación instalada no se modificaron.

La primera ejecución detectó que ATTACH no podía crear el destino por faltar el indicador CREATE. Se corrigió sin cifrar el origen in situ. También se alineó la prueba tipada con la clave ASCII hexadecimal utilizada por producción y se rechazaron claves no canónicas para evitar conversiones UTF-8 silenciosas.

La evidencia local y la huella del código probado bloquean la distribución ante fallos, ausencia de resultados o cambios del código. El preflight sin contraseñas está aprobado; rechaza evidencia ausente, resultado fallido, cambios de código y log alterado. Compilaciones Debug y AndroidTest aprobadas y 30 pruebas JVM dirigidas de seguridad sin fallos. `git diff --check` aprobado. Los cinco PNG originales se conservan y el script verificará sus bytes dentro de la APK definitiva.

## Generación manual

```bash
cd /home/r0ybt/AndroidStudioProjects/Arachn0de
bash release-assets/preparar-apk-0.3.0.sh
```

Utiliza automáticamente `~/.local/share/arachn0de/signing/arachn0de-release.jks` y `arachn0de-release`; solicita solo contraseñas por terminal sin eco. Verifica el certificado oficial SHA-256 `d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437`, firma, configuración release, recursos y compatibilidad de identidad/firma/código con las APK anteriores. Entrega `release-assets/Arachn0de-v0.3.0-code10-seguridad.apk` y checksum conservando la APK código 9.

No se ejecutó la firma ni se generó una APK oficial nueva durante esta preparación. La validación final de Release y su certificado se ejecutará al introducir las contraseñas manualmente. No hubo commit, push, publicación, desinstalación ni limpieza de datos.

## Límites pendientes

Las pruebas nativas pasaron en emulador; queda pendiente probar la actualización y el uso en el teléfono físico con datos reales. Exportar, comprobar y conservar un respaldo antes de instalar. Actualizar sobre la instalación anterior sin desinstalar ni borrar datos. No se certifican políticas de batería ni todos los modelos/ABI por la ejecución x86_64. La compatibilidad de firma no reemplaza esta comprobación de datos reales.
