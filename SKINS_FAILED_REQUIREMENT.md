# Skins: diagnóstico de Failed requirement

## Evidencia y alcance

No había dispositivo conectado ni se proporcionaron las dos imágenes o un tema exportado. No se afirma que esos archivos concretos estén corruptos ni que se haya reproducido su contenido. Se reprodujeron dos fallos reales en las rutas utilizadas por el guardado, con trazas y datos sintéticos válidos.

1. **Límite acumulado de biblioteca.** Dos PNG válidos de 260×180, con 164.386 bytes por recurso, producen un tema de aproximadamente 436 KiB, dentro del máximo de 2 MiB. Al acumular temas con imágenes, guardar otro alcanza el límite existente de 4 MiB de biblioteca. La implementación anterior lanzaba literalmente:

   ```text
   java.lang.IllegalArgumentException: Failed requirement.
       at CustomThemeCodec.library(CustomTheme.kt:118)
       at CustomThemeCodec.library(CustomTheme.kt:122)
       ... AppearancePreferences.saveCustom ...
   ```

   Retirar las imágenes permitía guardar. Añadir una sola también puede superar el espacio restante. Esto reproduce el patrón descrito sin imágenes inválidas, coordenadas incorrectas ni cambios de etapa. Otros temas ya guardados pueden seguir funcionando. Imágenes con las mismas dimensiones pueden tener pesos PNG muy diferentes: la prueba compara detalle aleatorio y color uniforme, ambos válidos, con una diferencia superior a 20×. El origen por IA no se utiliza como criterio de validación.

   Traza anterior conservada localmente: `/tmp/theme-failed-requirement-before.txt`.

2. **Lectura de preferencias grandes.** En las pruebas con almacenamiento de bóveda y SQLite nativo, el acceso a la fila completa de `private_preferences` falló con:

   ```text
   android.database.sqlite.SQLiteBlobTooBigException:
   Row too big to fit into CursorWindow requiredPos=0, totalRows=1
       ... SQLiteCursor.fillWindow ...
       ... PrivatePreferenceDao_Impl.all ...
   ```

   Una biblioteca aceptada por sus límites podía quedar ilegible para reapertura, referencias de archivos o backup. La prueba de regresión conserva la consulta anterior como reproducción del fallo y comprueba la lectura íntegra corregida. Traza: `/tmp/theme-cursor-window-reproduction.txt`.

## Corrección

- Se mantienen 2 MiB por tema, 4 MiB por biblioteca y hasta 16 temas. No se incrementan límites ni se omiten validaciones.
- Guardar/Aplicar verifica primero los originales. Si falta espacio, reduce proporcionalmente **solo las vistas previas del tema que se está guardando**. Los otros temas permanecen intactos.
- Si una imagen histórica solo contiene su PNG inline, se conserva primero ese PNG exacto como original cifrado y durable, con la reserva del editor, antes de derivar una vista previa menor. Los recursos iguales del candidato comparten ese original. No se elimina ninguna asignación automáticamente.
- Los bytes completos, SHA-256, dimensiones originales, encuadre y coordenadas se conservan. El renderizador existente prioriza el original. Los PNG históricos con orientación EXIF distinta de identidad no se convierten automáticamente, para evitar introducir un giro que antes no se aplicaba.
- La publicación de originales reutiliza ThemeOriginalStore/ImageFileLifecycle: reserva durable antes de confirmar, commit de preferencias antes de liberar y limpieza de recursos sin referencias ni reservas. Cancelar o fallar no reemplaza los datos confirmados. Las reservas compartidas y la recuperación siguen vigentes.
- `private_preferences` se lee en transacciones mediante fragmentos de hasta 128 KiB de bytes. Se unen antes de decodificar UTF-8, preservando emojis y caracteres entre fragmentos. Las tablas, sus filas y el cifrado no cambian.
- Los errores de tamaño, nombre duplicado, imagen, huella, original ausente y encuadre muestran mensajes concretos. El editor registra la excepción completa con etiqueta `ThemeEditor`, etapa y metadatos acotados; no registra imágenes, base64 ni nombres del usuario.
- Las fallas al decodificar un original para preview quedan registradas y permiten utilizar su vista previa existente. Al guardar se sigue exigiendo integridad completa: no se confirma un tema perdiendo su original. La UI permite cambiar o quitar expresamente la imagen afectada y seguir trabajando.

## Persistencia y backup

No cambia Room 50, el esquema de tablas, JSON 43 de backup ni los schemas de temas admitidos 1–3. Una imagen histórica convertida durante un guardado que necesite espacio utiliza el schema 3 y el manifiesto de originales ya existentes; no introduce rutas absolutas ni un formato nuevo. Los originales se incluyen con sus bytes exactos en el backup cifrado y se validan antes de restaurar. Las reservas y diarios operativos siguen excluidos; no caducan por tiempo. No hay nuevas cachés persistentes ni barridos de almacenamiento.

## Archivos

Producción:

- `app/src/main/java/com/r0ybt/arachn0de/ui/CustomThemeEditor.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/theme/ThemeSavePreparation.kt` (nuevo)
- `app/src/main/java/com/r0ybt/arachn0de/ui/theme/CustomTheme.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/theme/ThemeOriginals.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/theme/ThemeImages.kt`
- `app/src/main/java/com/r0ybt/arachn0de/visual/CanvasImages.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/PrivatePreferenceEntity.kt`

Pruebas nuevas: `ui/theme/ThemeSaveRegressionTest.kt`, `ui/theme/ThemeSaveBudgetTest.kt`, `data/PrivatePreferenceReadTest.kt`. Pruebas ampliadas: `ui/CustomThemeUiTest.kt`. Documentación: este informe, ARCHITECTURE.md y BACKUP_FORMAT.md.

## Validación

**49 pruebas, 8 clases, cero fallos**, compilación Kotlin y `git diff --check` correctos. Log final: `/tmp/theme-save-final.log`.

```text
./gradlew testDebugUnitTest --offline \
  --tests '*theme.ThemeSaveRegressionTest' --tests '*theme.ThemeSaveBudgetTest' \
  --tests '*theme.ThemeOriginalsTest' --tests '*theme.ThemeImageImportTest' \
  --tests '*theme.CustomThemeTest' --tests '*ui.CustomThemeUiTest' \
  --tests '*data.PrivatePreferenceReadTest' --tests '*security.PrivatePreferencesBackupTest'
```

Se verificaron: combinaciones de recursos, imágenes con igual tamaño y diferente peso, formatos/orientación, dimensiones y encuadre inválidos, límites por tema/biblioteca, navegación entre etapas, guardar/aplicar/editar/reabrir, imagen ausente sin bloqueo global, originales intactos y cifrados, recursos compartidos, reemplazo, cancelación, fallo de guardado, recuperación, lectura de preferencias grandes y backup/restauración cifrada completa.

Pendiente comprobar las dos imágenes concretas y el logcat del dispositivo del usuario. Para distinguir otra causa, el log local registra `ThemeEditor` con etapas como `import-image`, `validate-save`, `save`, `apply` y `decode-original-preview`. No se ejecutó la suite global ni se generó APK, commit o push. Se conservaron los cambios anteriores del árbol.
