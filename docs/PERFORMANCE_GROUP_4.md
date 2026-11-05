# Rendimiento — Grupo 4: retirada de Skins (R02, R09)

## Resultado y alcance

Se elimina el sistema activo de Skins. Se conservan temas oficiales, fotografías,
adjuntos y formatos de backup. No se modifican versiones, Juego, Tragaperras,
autenticación, cifrado, esquema Room ni historial de migraciones. Sin APK oficial
ni commits. Se mantienen R10 y Grupos 2/3, salvo retirar sus consumidores y pruebas
que pertenecían exclusivamente a la función eliminada. No se repiten sus suites.

## Inventario y dependencias verificadas

- Exclusivos: `CustomThemeEditor`, `ThemeDraftMemory`, selección/biblioteca de
  Apariencia, importación de imágenes y documentos, intercambio de temas,
  muestras de color, preparación/reducción de previews, pinturas/degradados,
  máscaras de tarjetas y cabecera, carga de originales para dibujarlos y cachés
  de imágenes/biblioteca/validación de Skins. Se retiran sus rutas y controles.
- Compartidos: `ThemeImageBitmaps` también almacenaba fotografías de proyectos.
  Esa parte se conserva como `CardPhotoBitmaps`: mismo LRU de 4 MiB, coalescencia
  de cargas, propietario por sesión y rechazo de cargas tardías. No recicla
  bitmaps que una vista todavía pueda dibujar. `PrivateCacheScope`, miniaturas,
  `PrivateImageCache`, avatares y el sistema general de adjuntos se conservan.
- Temas oficiales: se conservan catálogo, identificadores históricos, paletas,
  tipografía, tamaños, indicadores y preferencias. CMD mantiene su presentación
  terminal verde, separadores ASCII, cursor, navegación y recursos. Las ramas de
  decoración de Skins dejan paso exactamente a sus fallbacks oficiales previos.
- Los recursos usados por el editor eran compartidos con otros módulos. No se
  elimina ningún recurso gráfico empaquetado ni recurso de CMD/fotografías.
- Los sonidos personalizados ligados a Skins dejan de seleccionarse; se conserva
  el sonido oficial de completado y su interruptor. Pomodoro y audio del Grupo 3
  mantienen sus mecanismos. Los bytes históricos de sonidos siguen en el backup.

## Datos existentes y compatibilidad

No hay migración destructiva ni barrido de datos privados. Se mantienen cifrados
`custom_themes`, `custom_theme`, `theme_favorites` y los originales históricos
`theme-originals`, incluidas reservas pendientes sin caducidad. No se importan
nuevos originales de Skins ni se ejecuta su antiguo barrido periódico.

La apariencia activa no reconstruye la biblioteca ni decodifica Base64 de Skins.
Si existe una selección histórica, muestra **Charcoal**. Elegir un tema oficial
retira solo esa selección; no borra biblioteca, archivos ni otras preferencias.
Los valores opacos históricos todavía forman parte del espacio de preferencias
cifrado: no se afirma haber eliminado todos sus bytes de memoria.

`backup/legacy/LegacySkins.kt` y `LegacySkinOriginals.kt` conservan exclusivamente
modelos, lectura/validación de schemas 1–4, serialización para remapear referencias
y comprobaciones de integridad que necesitan los respaldos. No tienen paleta ni
renderizador, importador, editor o caché visual. El backup mantiene manifiestos,
bytes exactos, verificación de archivos, staging y transacciones existentes. La
restauración puede cambiar nombres portables de originales como antes, pero no
reactiva la función ni pierde los adjuntos normales. Payloads inválidos siguen
fallando antes de sustituir datos.

El diario compartido de exportaciones conserva recuperación de operaciones
históricas: al desbloquear puede retirar solo documentos incompletos identificados
por su propio diario, con el protocolo anterior. No elimina exportaciones
completadas ni permite iniciar nuevas exportaciones de temas. No se eliminan
respaldos existentes ni se barren originales/reservas retirados.

## Reducción comprobada

Frente a la copia del estado anterior conservada en `/tmp/group4-before`, los
cambios de producción eliminan **78.508 bytes UTF-8 y 947 líneas netas de Kotlin**,
incluyendo el código conservado/movido al lector heredado y la caché de fotos.
Se retiran diez archivos de producción y se añaden tres, con ajustes en sus
consumidores. No son cifras de APK instalada, RAM o batería; no se han medido
esos ahorros en el Pixel. Los archivos privados históricos no se cuentan como
espacio recuperado porque se conservan.

## Validación focalizada

**21 pruebas distintas aprobadas.** Producción, fuentes de pruebas unitarias e
instrumentales compilan. La última ejecución termina con **BUILD SUCCESSFUL**.
Solo se repitieron casos afectados por ajustes del arnés; las 20 pruebas aprobadas
no se volvieron a ejecutar para resolver el fixture portátil pendiente.

- Cuatro fixtures nuevos: apariencia sin decodificación de Skins, schemas
  históricos 1/2 y copia sin Skin, originales de schema 3 con dos superficies
  compartidas y adjunto normal, restauración en otra clave, nombres portables,
  corrupción/ausencia de archivos y rollback tras fallo de transacción.
- Schema 4 y sonidos históricos: regresión de backup existente; sonido oficial,
  silencio y deduplicación siguen funcionando.
- Interfaz sin accesos de Skins; selección, persistencia y tamaños oficiales;
  CMD con colores verdes/separadores y menú compartido; Diagramas bajo todos los
  temas oficiales.
- Fotografías: tres casos del LRU conservado; carga tardía tras revocación,
  miniaturas cifradas aisladas, preferencias tras revocación, selección/edición/
  cancelación/reapertura, conservación del original panorámico, encuadre compartido
  con recreación y exportación normal/ZIP con bytes privados preservados.

Compilaciones y pruebas: `:app:testDebugUnitTest` y
`:app:compileDebugAndroidTestKotlin`, offline. Los fixtures usan archivos realmente
cifrados y sustituyen SQLCipher nativo por Room de JVM; no acreditan pruebas
físicas. El caso instrumental `PublicAnimationInstrumentedTest` conserva las
comprobaciones normales de animación, proveedor, ZIP y respaldo, y compila, pero
no se ejecuta en un dispositivo.

El arnés se ajustó para retirar dos referencias residuales, acortar nombres de
clases generadas y proporcionar contraseña sintética y gráficos nativos al caso
que valida MIME de originales. No se relajó ninguna validación de producción.
Tras la compilación solo se corrigió un comentario del lector trasladado.

Evidencia local: `/tmp/group4-validation.log`, `/tmp/group4-tests.log` (incluye
compilación instrumental), `/tmp/group4-focused.log` (20 aprobadas),
`/tmp/group4-portable-native.log` (caso final aprobado), XML conservados en
`/tmp/group4-results/` y lista única en `/tmp/group4-results/passed.txt`.
Comprobación focalizada de espacios y referencias correcta.

La búsqueda de referencias confirma ausencia de APIs activas del editor,
selección de Skin, proveedores de imágenes, borradores y cachés exclusivas. Las
referencias de lectura heredada quedan en backup/legacy y en la validación de
preferencias de backups. No se accede a esos lectores desde composición.
Las pruebas exclusivas del sistema eliminado se retiran; los casos compartidos
de encuadre se trasladan a `BitmapFramingSharedTest`, y los casos mixtos conservan
sus verificaciones normales usando paletas oficiales.

## Pendiente en Pixel 7a

- Actualizar conservando un almacén con una Skin antigua; comprobar Charcoal y
  seleccionar/reabrir CMD y otros temas, incluidos los efectos verdes.
- Restaurar una copia histórica con Skin y fotografías normales en un almacén
  de prueba; verificar fotografías, encuadres, miniaturas y adjuntos, y volver a
  respaldar. Los originales de Skins se conservan sin una interfaz para editarlos.
- Comprobar fotografías de Núcleos, Capas/Tareas/Notas y Personas, edición,
  exportación/compartición y bloqueo/desbloqueo reales. No se usan datos personales
  para pruebas destructivas.
- Medir RAM y tiempo real antes de atribuir una magnitud física a la eliminación.

## Archivos

Prefijo común de las listas siguientes:
`app/src/{main,test}/java/com/r0ybt/arachn0de/`. También se actualizan
`ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe.

### Producción

Retirados: `cleanup/ImageCleanupChanges.kt`, `ui/CustomThemeEditor.kt`, `ui/ThemeDraftMemory.kt`, `ui/theme/CustomTheme.kt`, `ui/theme/ThemeDocuments.kt`, `ui/theme/ThemeImageImport.kt`, `ui/theme/ThemeImages.kt`, `ui/theme/ThemeOriginals.kt`, `ui/theme/ThemeSavePreparation.kt`, `ui/theme/ThemeSwatches.kt`.

Añadidos: `backup/legacy/LegacySkinOriginals.kt`, `backup/legacy/LegacySkins.kt`, `ui/theme/CardPhotoBitmaps.kt`.

Ajustados: `Arachn0deApplication.kt`, `backup/BackupData.kt`, `backup/BackupRepository.kt`, `completion/CompletionFeedback.kt`, `completion/CompletionSounds.kt`, `diagrams/DiagramExportJournal.kt`, `security/SecureFiles.kt`, `ui/ActionComponents.kt`, `ui/AppearanceScreen.kt`, `ui/BankUi.kt`, `ui/BashPresentation.kt`, `ui/CompactCardIconButton.kt`, `ui/CompactCardIndicators.kt`, `ui/ContainerProgressLine.kt`, `ui/CoreBottomModule.kt`, `ui/ElementAccessUi.kt`, `ui/ModularPanel.kt`, `ui/NavigationBarStyle.kt`, `ui/NavigationChrome.kt`, `ui/NodeComponents.kt`, `ui/PomodoroUi.kt`, `ui/ProjectComponents.kt`, `ui/ProjectPhotoComponents.kt`, `ui/TagUi.kt`, `ui/TaskStateControls.kt`, `ui/ThemeIdentity.kt`, `ui/theme/Appearance.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/ThemePaints.kt`.

### Pruebas

Retirados: `ui/CustomThemeUiTest.kt`, `ui/ThemeDraftMemoryTest.kt`, `ui/theme/CardImageProtectionUiTest.kt`, `ui/theme/CustomThemeTest.kt`, `ui/theme/ThemeImageImportTest.kt`, `ui/theme/ThemeOriginalsTest.kt`, `ui/theme/ThemeSaveBudgetTest.kt`, `ui/theme/ThemeSaveRegressionTest.kt`.

Añadidos: `backup/LegacySkinRetirementTest.kt`, `ui/BitmapFramingSharedTest.kt`, `ui/SkinRetirementUiTest.kt`.

Ajustados: `access/ElementAccessUiTest.kt`, `bank/BankVisualCompactTest.kt`, `completion/CompletionSoundTest.kt`, `data/PrivatePreferenceReadTest.kt`, `diagrams/DiagramExportTest.kt`, `diagrams/DiagramUiTest.kt`, `photos/PublicImageExportTest.kt`, `security/PreferenceResponsivenessTest.kt`, `security/PrivateSessionCachesTest.kt`, `status/StatusSelectorUiTest.kt`, `status/TaskStateCardUiTest.kt`, `ui/CalendarNavigationBlockTwoTest.kt`, `ui/CardCompactionBlockThreeTest.kt`, `ui/CompactCardUniformityTest.kt`, `ui/DrawerOrganizationTest.kt`, `ui/HierarchyIdentityUiTest.kt`, `ui/IconographyBlockFourTest.kt`, `ui/IndicatorColorsTest.kt`, `ui/theme/CardPhotoMemoryTest.kt`.


### Instrumentación

Se adapta `app/src/androidTest/java/com/r0ybt/arachn0de/photos/PublicAnimationInstrumentedTest.kt` para conservar la cobertura de fotografías normales sin invocar el sistema retirado.
