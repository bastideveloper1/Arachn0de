# Temas personalizados: cierre al seleccionar imágenes

## 1. Causa y evidencia

Se identificó una excepción real de la aplicación instalada en el emulador
`emulator-5554` (0.7.0/code21). Logcat del 19 de octubre de 2026 registra dos
cierres a las 12:39 y 12:41, hora de Santiago. La última traza indica:

```text
FATAL EXCEPTION: main
Process: com.r0ybt.arachn0de, PID: 14115
java.lang.IllegalStateException: Required value was null.
    at com.r0ybt.arachn0de.ui.AppearanceScreenKt.AppearanceScreen(AppearanceScreen.kt:35)
```

`dumpsys activity exit-info com.r0ybt.arachn0de` clasifica ambas salidas como
`reason=4 (APP CRASH(EXCEPTION))`, no como terminación por falta de memoria.
La traza relevante se conservó en `/tmp/theme-picker-crash-evidence.log`.

La firma original de AppearanceScreen exigía
`checkNotNull(LocalAppearancePreferences.current)`. MainActivity, al pasar a
segundo plano, activa la protección de pantalla; Arachn0deTheme retira entonces
esa preferencia de los locales de Compose. El editor seguía compuesto detrás
de la protección. Su recomposición lanzaba la excepción, incluso sin procesar
los bytes de la imagen. El retorno del selector hacía visible el cierre y la
siguiente apertura pedía autenticación porque el proceso anterior había fallado.

La prueba `externalPickerBackgroundKeepsEditorWhenThemePreferencesAreTemporarilyHidden`
reprodujo antes de corregir la misma excepción y línea de código. Pasó después.
Esto confirma el defecto en la ruta observada en el emulador; falta verificar
la corrección instalada en el teléfono del usuario. No se atribuyen otros cierres
u OOM hipotéticos a esta causa sin sus correspondientes registros.

## 2. Comparación con Antisocial

| Punto | Antisocial | Temas antes | Temas corregidos |
| --- | --- | --- | --- |
| Selector | PickMultipleVisualMedia / Photo Picker | OpenDocument | PickVisualMedia, misma familia que Antisocial |
| URI y permisos | Callback Activity Result; lectura inmediata del proveedor | Callback Activity Result; lectura inmediata | Igual; sin guardar URI ni necesitar permiso persistente para una copia autónoma |
| Lectura | Importación en Dispatchers.IO hacia archivos cifrados | Copia completa del archivo en ByteArray antes de decodificar | Streams acotados, bounds, EXIF y decodificación con muestreo en IO |
| Resolución | Conserva original; valida con decode reducido a 512 px | 768 px y 96 KiB para cualquier superficie | Tamaño medido de cada superficie y densidad; límites históricos del formato |
| Persistencia | Adjuntos cifrados, reservas y referencias | PNG autónomo dentro del tema cifrado | Mismo formato, bytes y hash dentro del tema cifrado |
| Interfaz | No exige una preferencia visual nullable durante background | Apariencia exigía esa preferencia | Retiene la última referencia válida solo durante la vida de la composición autenticada |

No se cambió Antisocial, su almacenamiento, sus permisos ni su limpieza de archivos.

## 3. Corrección y componentes reutilizados

- AppearanceScreen acepta la ausencia temporal del local y conserva su última
  preferencia válida, incluso si la pantalla se compuso inicialmente sin ella.
  No crea un cache global ni altera MainActivity, VaultManager o los plazos de bloqueo.
- El Photo Picker reutiliza Activity Result y PickVisualMediaRequest existentes.
- Se reutilizan canvasImage, el codec, SHA-256, las preferencias cifradas, el
  respaldo transaccional, ThemeDraftMemory y BitmapFramingDialog.
- Las medidas se toman de las tarjetas y encabezado reales de la vista previa;
  no se cambian sus dimensiones ni se añade un editor de imágenes.
- Cancelar limpia únicamente el destino pendiente. Un error no sustituye el
  borrador anterior. Un fallo de memoria durante la importación se convierte en
  un mensaje recuperable, con liberación de bitmaps y streams en finally/use.

## 4. Calidad, memoria y compatibilidad

ThemeImageImport lee el proveedor por streams: no conserva simultáneamente una
copia completa del original y su ByteArray duplicado. Mantiene el límite existente
de 20 MiB de origen y 100 megapíxeles. La decodificación se muestrea antes de asignar
el bitmap completo; el mayor lado decodificado queda acotado aproximadamente al
siguiente escalón sobre la medida solicitada, por debajo de 2048 px.

La resolución deseada cubre la superficie medida, con 25% de margen de encuadre;
el encabezado considera los 88 dp reales y el ancho del menú (hasta 360 dp).
Las tarjetas usan sus propias medidas. Se respeta la orientación EXIF, el aspecto
y la transparencia. No se amplían originales pequeños. Se normaliza a PNG sin
pérdida de compresión; solo se reduce proporcionalmente por tamaño visual o por
presupuesto de bytes. No se recorta destructivamente.

El codec existente admite PNG de hasta 1024 px por lado. Se conserva ese contrato;
las importaciones nuevas usan hasta 288 KiB por imagen para que cinco recursos
independientes quepan en los 2 MiB del tema. La validación del candidato completo
sigue siendo obligatoria, incluidos recursos históricos que ya ocupen ese espacio.
No se reescriben temas guardados ni se cambian esquemas, Room o JSON de backup.

Encuadre, posicionamiento, centrado, vista previa, colores, estilos y degradados
siguen disponibles. La protección de la mitad izquierda de las tarjetas mantiene
la implementación anterior. No se altera la caducidad ni el bloqueo manual:
si vence el plazo configurado mientras el selector está abierto, la autenticación
sigue siendo necesaria. No hay excepción general de seguridad para selectores.

## 5. Archivos de esta intervención

Producción:
- app/src/main/java/com/r0ybt/arachn0de/ui/AppearanceScreen.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/CustomThemeEditor.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/theme/ThemeImageImport.kt (nuevo)

Pruebas:
- app/src/test/java/com/r0ybt/arachn0de/ui/CustomThemeUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/theme/ThemeImageImportTest.kt (nuevo)

Documentación: este informe y la referencia añadida a TEMAS.md.

## 6. Validación automatizada

Compilación Kotlin debug correcta y **36/36 pruebas aprobadas** en la ejecución
final (`/tmp/theme-picker-verified.log`, BUILD SUCCESSFUL). Desglose:

| Clase / selección | Pruebas | Fallos |
| --- | ---: | ---: |
| CustomThemeUiTest | 15 | 0 |
| CustomThemeTest | 12 | 0 |
| ThemeImageImportTest | 5 | 0 |
| ThemeDraftMemoryTest | 1 | 0 |
| VaultManagerTest, bloqueo automático y manual | 2 | 0 |
| PersonMemoryTest, originales y respaldo real de Antisocial | 1 | 0 |

Antes de corregir, la prueba del cierre fallaba con la misma excepción del
emulador. Pasó tras corregir Apariencia. En una ejecución intermedia pasaron
35/36: la simulación de OOM tenía un ContentProvider sin attachInfo. Se corrigió
esa inicialización del fixture; pasó aisladamente y después la batería completa.
No se ocultaron pruebas fallidas ni se modificó seguridad para hacerlas pasar.
Los mensajes del decoder al recibir bytes deliberadamente inválidos son esperados;
no hay fallos ni pruebas omitidas en la ejecución final.

La selección dirigida incluye las cinco superficies, cancelación, reintento tras
imagen inválida, PNG/JPEG/WebP, imagen JPEG de 4000×3000, transparencia, orientación
EXIF, selección repetida, resolución distinta por superficie, encuadre, degradado,
preferencias recargadas, copia/administración y respaldo cifrado con rechazo de
corrupción. También incluye bloqueo automático/manual y una prueba de respaldo
real de originales y referencias compartidas de Antisocial.

## 7. Validación pendiente en teléfono

No se generó ni instaló una APK corregida. La aplicación del emulador solo se usó
para obtener evidencia del fallo anterior. Las pruebas del código corregido son
Robolectric/JUnit; no sustituyen probar el retorno del selector nativo con imágenes
reales en el dispositivo del usuario.

Pendiente: seleccionar/cancelar repetidamente en las cinco superficies, comprobar
el encuadre táctil y la legibilidad, guardar y reabrir, verificar proveedores OEM,
formatos HEIC si el dispositivo los soporta, fotografías grandes y pico real de
memoria. También comprobar bloqueo manual y retorno tras superar el plazo automático.
Las pruebas de memoria inyectan un fallo; no miden el consumo máximo real del teléfono.

Para capturar una reproducción con el teléfono conectado por USB:

```bash
adb -d logcat -v threadtime AndroidRuntime:E ActivityManager:I '*:S' > "$HOME/arachn0de-temas-logcat.txt"
```

Reproducir el flujo y detener la captura con Ctrl+C. Consultar además:

```bash
adb -d shell dumpsys activity exit-info com.r0ybt.arachn0de
```

No se cambiaron versionName/versionCode (0.7.0/code21), cifrado ni autenticación.
No se generó APK ni se realizó commit, push o publicación.
