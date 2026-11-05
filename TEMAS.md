# Temas personalizados — Arachn0de 0.6.0/code20


## Editor simplificado vigente

Crear/editar tema tiene exactamente tres pasos: **Color**, **Imágenes opcionales**
y **Previsualizar/Guardar**. Color ofrece rojo, azul, verde, rosa, morado, café,
gris, dorado, amarillo y naranjo. Son variantes de acento sobre Charcoal: conservan
sus fondos oscuros, tipografía y colores de estado; ajustan acentos, botones,
bordes y superficies seleccionadas. No hay controles HSL, hexadecimal, roles,
degradados, puntos, ángulos ni intensidades en el editor.

Las cinco imágenes opcionales son independientes: encabezado del menú, Núcleos,
Capas, Tareas y Notas. Se eligen con SAF y se copian como PNG inline en el tema;
no se guardan rutas. La importación reutiliza `importCanvasImage` con límites
opcionales de 768 px/96 KiB por recurso y reducción automática proporcional. Los
llamadores existentes conservan los límites y comportamiento predeterminados.
Se mantiene el límite total de 2 MiB por tema y 4 MiB/16 temas por biblioteca;
excederlo informa un error y conserva el borrador y tema anteriores.

Ajustar/centrar reutiliza el mismo diálogo y geometría táctil de los avatares,
extendidos a rectángulos: arrastrar, pellizcar, Centrar, Restablecer y
Confirmar/Cancelar. No hay recorte destructivo, editor de píxeles ni archivos
intermedios. La vista de encuadre es una guía; las superficies se adaptan a la
altura real que tenga cada tarjeta. El encuadre es proporcional al excedente.

Las imágenes son decoración de las superficies originales. `ProjectCard` y
`NodeCard` indican su tipo a `GeneralCard`, que mantiene contenido, borde,
selección, acciones y dimensiones. El degradado de legibilidad usa el fondo del
tema a la izquierda y termina al 55 % del ancho: la derecha se dibuja sin velo,
opacidad global ni filtros de color. El encabezado no oscurece la imagen y mantiene
88 dp de zona útil para el logo, además del inset superior de Android. Las
fotografías propias de Núcleos siguen siendo elementos de identidad separados.
La previsualización compacta y desplazable usa esos componentes reales y bloquea
las acciones de los ejemplos. Guardar, aplicar, editar, duplicar y eliminar
reutilizan la administración existente.

Los temas antiguos no se transforman al abrir, renombrar o guardar. Conservan sus
colores, degradados y modos de identidad. Elegir una de las diez muestras es la
acción explícita que sustituye su combinación avanzada por la variante sencilla;
conserva sus imágenes. El encabezado antiguo aparece como imagen editable cuando
corresponde: ajustar/confirmar lo lleva explícitamente al fondo con logo; cancelar
conserva el valor anterior. Reemplazar o quitar elimina también la referencia de
encabezado anterior, para evitar que reaparezca después. Las paletas oficiales son inmutables.

### Persistencia y ciclo de vida actuales

El codec conserva los esquemas portables **1/2** y escribe **3** cuando una imagen
incluye metadatos opcionales de original. `images` conserva las cinco claves y el
PNG de preview, hash y encuadre existentes. Se validan también nombre relativo,
SHA-256, tamaño, MIME, dimensiones y EXIF del original antes de usar/restaurar.
Room permanece en 48. El backup usa JSON 41 solo cuando incluye originales y
conserva lectores anteriores; la envoltura cifrada v2 sigue vigente.

Los originales nuevos se almacenan cifrados, sin recomprimir, en un directorio
controlado y viajan íntegros por streaming. La exportación individual de temas
incluye sus bytes en un JSON identificado. Las imágenes antiguas siguen siendo
legibles y requieren nueva selección para recuperar detalle que ya se redujo.
Duplicar puede compartir el original inmutable, protegido por todas las referencias.
Guardar/descartar libera reservas mediante diario durable; reemplazo y eliminación
retiran archivos después del commit y solo sin otras referencias/reservas.
El borrador sigue en ThemeDraftMemory; los originales pendientes y sus reservas
no caducan. Los estados operativos no se exportan. Los bitmaps de preview/original
pertenecen al conjunto de cinco superficies y se liberan por GC al salir del ámbito.

### Validación dirigida de la simplificación

La selección verifica codec y compatibilidad 1/2, cinco PNG distintos con sus
encuadres y hash, importación local de los cinco destinos, archivos originales
intactos, presupuesto automático, contraste de las diez muestras, dibujante nativo
del desvanecimiento, persistencia/duplicación/eliminación y respaldo cifrado con
restauración de los cinco recursos. Inyecta fallo de escritura y corrupción antes
del reemplazo; conserva el estado anterior. También cubre cancelación, recreación,
arrastre, centrado, zoom cuadrado/rectangular, logo, medidas reales de tarjetas,
fuentes 200/300 %, controles accesibles y desplazamiento de la vista previa.

Las pruebas antiguas `AvatarEditorUiTest` (cuatro casos) buscan «Editar» antes de
abrir el encuadre, acción que la pantalla actual ya no expone de esa forma.
`AvatarPhoneFixTest.repeatedPickerAndCropActionsOpenOnlyOnceAndCanReopenAfterCancellation`
se detiene esperando «Opciones del perfil», también antes del encuadre. Se
registran esos cinco fallos de navegación anterior y no se modifican sus pantallas
ni pruebas en esta tarea. La selección pertinente comprueba directamente el
diálogo compartido, sus gestos y recreación, y la prueba vigente de renderizado
cuadrado en fotos apaisadas, verticales y cuadradas. No se ocultan esos fallos ni
se declara aprobada la suite global.

La captura PixelCopy de Robolectric agota su espera; la comprobación de píxeles
usa el DrawScope nativo que llama producción. Falta la inspección física en el
teléfono con fotografías propias, proveedor SAF, brillo y navegación asistida.
No se genera APK. PDF/DOCX y Diagramas no se modifican.

Archivos de esta simplificación: `CustomThemeEditor.kt`, `ThemeIdentity.kt`,
`ThemeImages.kt`, `ThemeSwatches.kt`, codec `CustomTheme.kt` y ámbito `Theme.kt`;
integraciones puntuales en `AppearanceScreen.kt`, `BashPresentation.kt`,
`ProjectComponents.kt` y `NodeComponents.kt`; reutilización rectangular en
`AvatarEditor.kt`/`AvatarFraming.kt` y límites opcionales de `CanvasImages.kt`.
Pruebas: `CustomThemeTest.kt`, `CustomThemeUiTest.kt` y caso rectangular de
`AvatarFramingTest.kt`. No se cambian los demás módulos ni la versión.

Resultado final: **29 casos dirigidos distintos aprobados**. La batería conjunta
terminó 28/28; las dos comprobaciones puntuales de contraste/medidas terminaron
2/2; la última ejecución, después de completar la transición del encabezado
histórico, terminó **13/13** (todos los casos de interfaz y el dibujante nativo,
incluido el nuevo caso de ajuste/eliminación del encabezado antiguo). Compilación
`:app:compileDebugKotlin` correcta y `git diff --check` limpio. Los cinco fallos
antiguos de navegación descritos arriba siguen pendientes fuera de este alcance.
Logs temporales: `/tmp/theme-simple-final-tests.log`,
`/tmp/theme-simple-compatibility-check.log` y `/tmp/theme-simple-final-ui.log`.
La ejecución completa de 28 casos conserva XML en `/tmp/theme-simple-28-results/`.
Se mantiene 0.6.0/code20, sin APK, commit ni push.

La documentación siguiente describe la implementación avanzada anterior, cuyo
formato se conserva para compatibilidad; sus controles ya no están disponibles.

## Antecedentes de implementación

## Uso

En Ajustes → Apariencia, el selector conserva los ocho temas oficiales e incorpora
las miniaturas de los personalizados. «Crear tema» abre un borrador; «Crear copia
personalizada» permite partir del tema seleccionado sin alterar el oficial.
Editar permite renombrar, cambiar descripción y base; Duplicar crea otra identidad.
Eliminar requiere confirmación y, si el tema está activo, selecciona Charcoal en la
misma escritura. Importar siempre crea otro UUID y resuelve nombres con sufijos.

El editor muestra una vista previa desplazable arriba y controles inferiores
minimizables en Colores, Degradados, Componentes, Identidad y Vista previa.
Guardar conserva el diseño; Aplicar lo guarda y selecciona. La vista previa es un
ámbito local: nunca cambia la apariencia activa. Volver/Cancelar conserva la
selección anterior y confirma el descarte si hay cambios. Restablecer cambios
recupera el diseño inicial. Los favoritos son una biblioteca independiente y su
guardado explícito no se revierte al descartar un tema.

## Propiedades y renderizado

Se admiten sobrescrituras dispersas: fondos principal/secundario/final, superficies,
tarjetas, elevación, panel común y de tareas, menú, encabezado, textos principal y
secundario, bordes, acentos, botones y su texto, iconos, controles, deshabilitados,
selección, prioridad, error, advertencia, éxito y fondo modal. Lo ausente hereda de
la base. HSL y hexadecimal RRGGBB/AARRGGBB comparten el selector de color existente;
confirmar/cancelar mantiene separados el valor previo y el ajuste temporal.

Los degradados LINEAR, RADIAL y ANGULAR usan shaders nativos, sin dependencias ni
animación permanente. Tienen 2–8 puntos ordenados, extremos 0/1, posiciones
interiores editables, ángulo 0–360 e intensidad 0–1 respecto de la superficie base.
Radial usa centro y radio hasta las esquinas; angular usa SweepGradient rotado.
Se soportan fondo, superficie, tarjetas compatibles, paneles, menú, encabezado,
botones primarios compartidos y superficie de acento. Heredar retira la
sobrescritura; elegir color sólido sustituye el degradado. Cinco muestras editables:
Carbón nocturno, Cian eléctrico, Verde terminal, Ámbar solar y Violeta profundo.
Las superficies son opacas; textos/iconos/fondo modal e imágenes pueden incorporar
alfa. No se imponen transparencias para simular degradados.

Los componentes Material que únicamente aceptan Color reciben los colores
semánticos; las superficies compatibles usan brushes. Las tarjetas principales de
Núcleos/Capas/Notas/Tareas, Personas, Calendario y Metro reutilizan GeneralCard.
Pomodoro respeta la paleta personalizada sin alterar sus estilos del reloj.
Los renderizadores de Wireframe, Diagramas y anotaciones conservan sus propios
modelos, fondos, imágenes y colores; el tema no entra en PNG/GIF/MP4.

Green Bash se determina por la base, nunca por el color verde. Conserva tipografía
monoespaciada, presentación terminal, cursores y separadores; sus ámbitos visuales
respetan las sobrescrituras personalizadas. Los ocho temas oficiales son inmutables.

## Identidad del menú

Original conserva el recurso habitual. Nombre muestra el nombre del tema. Imagen
usa un PNG normalizado con transparencia, SHA-256, escala 0,25–3, alineación X/Y
−1…1, espaciado 0–24 dp y ajuste Fit/Crop sin deformación. El encabezado recorta su
contenido dentro de los 88 dp existentes: la imagen no invade las opciones.
Seleccionar imágenes reutiliza el importador local acotado PNG/JPEG, con reducción
a 1024 px y máximo 1 MiB normalizado. No se cambia paquete, launcher ni identidad Android.

La vista previa contiene barra, miniatura del menú/identidad, tarjeta, selección
interactiva, botón, campo, textos, icono y panel. Los cambios no guardados sobreviven
a recreación de configuración; los PNG completos se retienen en un ViewModel y no
se serializan en el Bundle de Android. Son estado temporal de edición, excluido
del backup. Tras muerte del proceso se vuelve a los diseños confirmados; es
necesario Guardar para conservar un borrador de tema entre sesiones.

## Persistencia, backup y liberación

Se reutiliza AppearancePreferences en el namespace privado `appearance_preferences`
de EncryptedPreferences/SQLCipher. No se añaden tablas, columnas ni archivos de
imagen: Room sigue en 48 y el lector de backup en JSON 40. Las escrituras nuevas
validan toda la biblioteca antes del commit y se coordinan con la restauración
mediante el bloqueo común de archivos. Una revisión optimista impide que un editor
sobrescriba un tema modificado/restaurado mientras estaba abierto. Un fallo conserva
las preferencias confirmadas y permite reintentar.

Las claves opcionales `custom_themes`, `custom_theme` y `theme_favorites` viajan en
el snapshot oficial de preferencias, con imágenes PNG inline y SHA-256. No hay
rutas del dispositivo. Los respaldos sin esas claves restauran biblioteca/favoritos
vacíos y selección oficial histórica. La validación existente del namespace llama
al mismo codec antes de reemplazar datos; un activo inexistente, imagen dañada,
campo desconocido o recurso excesivo rechaza la restauración.

Cada tema es propietario de los bytes inline de su identidad. Duplicar copia esos
bytes; reemplazar/retirar/eliminar libera el valor anterior únicamente al confirmar
la escritura atómica. No existen archivos compartidos que borrar, rutas huérfanas
ni barrido de directorios. Cancelar descarta solo memoria del borrador. Una copia
mantiene sus bytes aunque se elimine el tema del que procede.

La única caché pública de desbloqueo conserva el nombre de la base oficial; no
recibe nombres personalizados, colores privados ni imágenes.

## Archivo portable

El selector SAF abre JSON y el selector de carpeta SAF recibe la exportación. El
usuario ve antes de exportar que el archivo es **legible** y contiene diseño e
imagen de identidad. No contiene otras preferencias, notas, claves ni datos de
módulos. No requiere Internet ni permisos de almacenamiento general.

Raíz cerrada del esquema 1: `kind=arachn0de-theme`, `schema`, `id`, `name`,
`description`, `base`, `colors`, `paints`, `header`. ARGB usa enteros con signo de
32 bits. Un paint contiene exactamente color o gradient; gradient incluye type,
angle, intensity y stops(position/color). Header incluye mode, image base64,
sha256, scale, x, y, spacing y crop. No hay referencias externas ni scripts.

Límites: archivo 2 MiB, biblioteca 16 temas/4 MiB, nombre 80 caracteres,
descripción 500, imagen normalizada 1 MiB/1024 px, favoritos 32. Se rechazan
campos desconocidos, tipos incorrectos, números no finitos, UUID inválidos,
roles desconocidos, nombres repetidos y puntos de degradado desordenados.

La exportación reutiliza el diario durable cifrado de SAF en un directorio separado
`theme-export-journal`: registra nombre UUID antes de crear, reserva la operación,
confirma solo tras cerrar el stream y elimina exclusivamente la salida parcial de
esa operación ante fallo/cancelación. Si el proveedor impide borrar, conserva el
diario y reintenta al abrir Apariencia incluso tras reiniciar. Archivos completados
son del usuario y nunca se limpian. Diario/reservas son operativos y se excluyen
del backup; no se elimina por antigüedad ni se recorre almacenamiento ajeno.

## Contraste y comprobaciones físicas

Se muestrean extremos e interiores de cada tramo de degradado; se comprueban texto,
texto secundario, iconos, deshabilitados, botones y estados críticos, además de la
diferenciación LAB de error/advertencia/éxito. Se informa, se propone blanco/negro o
recuperación de base, y se solicita aceptación explícita para guardar con avisos.
No se sustituyen elecciones silenciosamente. La identidad gráfica ocupa una zona
sin texto superpuesto; no se promete análisis semántico de una imagen arbitraria.

Pendiente en dispositivo: contraste percibido con imágenes y brillos distintos,
TalkBack, proveedores SAF reales que revocan permisos, suavidad con listas extensas,
teclado, rotación física y tamaños extremos. Las pruebas Robolectric no sustituyen
esas comprobaciones. No se genera APK ni release para esta tarea.

## Inventario de esta implementación

Producción nueva:

- `ui/CustomThemeEditor.kt`, `ui/ThemeIdentity.kt`, `ui/ThemeDraftMemory.kt`.
- `ui/theme/CustomTheme.kt`, `ui/theme/ThemePaints.kt`, `ui/theme/ThemeDocuments.kt`.
- `visual/VisualColorPicker.kt` (selector HSL extraído para reutilizarlo sin cambiar el formato de Diagramas).

Producción integrada, bajo `app/src/main/java/com/r0ybt/arachn0de/`:

- `ui/AppearanceScreen.kt`; `ui/theme/Appearance.kt`, `Theme.kt`, `Color.kt`.
- `ui/ActionComponents.kt`, `BashPresentation.kt`, `NavigationChrome.kt`, `LucideIcons.kt`.
- `ui/ModularPanel.kt`, `CoreBottomModule.kt`, `ProjectScreen.kt`.
- `ui/NodeComponents.kt`, `ProjectComponents.kt`, `CompactCardIndicators.kt`.
- `ui/PeopleScreen.kt`, `CalendarScreen.kt`, `PomodoroUi.kt`, `TaskDates.kt`, `AttentionScreen.kt`.
- `metro/MetroScreen.kt`.
- `diagrams/DiagramPalette.kt`, `DiagramExportJournal.kt` (adaptador de selector y diario SAF; sin cambios en renderizadores ni códecs de vídeo).

Pruebas nuevas: `ui/theme/CustomThemeTest.kt`, `ui/CustomThemeUiTest.kt`,
`ui/ThemeDraftMemoryTest.kt`. Se amplía `diagrams/DiagramExportTest.kt` y se
actualizan fixtures de `ui/AppearanceUiTest.kt`, `ui/AppearanceRestartUiTest.kt`
y `pomodoro/PomodoroAppearanceTest.kt` a las preferencias privadas, indicadores
compactos y reloj de prueba manual durante animación continua.

Documentación: este archivo, ARCHITECTURE.md y BACKUP_FORMAT.md. Los demás cambios
previos del árbol de trabajo se conservan y no forman parte de esta intervención.

La caché de validación de imágenes conserva hasta cuatro recursos exactos (máximo
5,6 MiB base64) y ningún bitmap; evita decodificaciones repetidas al manipular el
encuadre. No se confía solo en el hash declarado: también debe coincidir el string
inmutable completo previamente validado. Es regenerable y se excluye del backup.
El lector limita el anidamiento JSON antes de invocar el parser recursivo y rechaza
contenido adicional después del objeto portable.

## Validación final — 2026-10-19

`:app:compileDebugKotlin` y `:app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
Ejecución conjunta final: **178/178**, cero fallos, errores o casos omitidos.
Registro: `/tmp/custom-theme-verified-regressions.log`; informes XML/HTML en
`app/build/test-results/testDebugUnitTest/` y `app/build/reports/tests/testDebugUnitTest/`.

- Temas nuevos: **16/16** (modelo/administración/herencia, recursos y límites,
  contraste, shaders nativos de los tres tipos, backup cifrado completo/rollback/
  corrupción/histórico, editor/selección/descarte/restauración de estado,
  identidad/encuadre, fuente 200/300 %, prioridades y advertencias independientes,
  precedencia de indicadores y Bundle sin bytes de PNG).
- Diagramas: **55/55**, incluido el nuevo caso de JSON portable SAF, fallo de
  escritura, rollback, denegación de borrado y recuperación después de reinicio.
- Wireframe: **40/40**, incluidos los 16 casos de interfaz.
- Apariencia y Green Bash anteriores: **24/24**.
- El subconjunto compartido de la discrepancia 48/52 dp: **39/39**, manteniendo
  la expectativa exacta 48 dp y el caso multilínea al 300 %. Este subconjunto
  solapa parte de Wireframe; no se suman estas filas para obtener el total.
- También pasan las regresiones seleccionadas de cifrado/preferencias/backup,
  color de eventos, Pomodoro, anotaciones y panel de Núcleos.

La primera regresión amplia dio 172/176. Se localizaron cuatro fixtures/expectativas
anteriores a esta tarea: preparación de Apariencia en preferencias públicas en vez
del vault; snapshot antes del checkpoint asíncrono inicial de Pomodoro; textos de
prioridad/fecha que ya se sustituyeron por indicadores compactos; y espera de
reposo automático durante la animación continua de Pomodoro. Se adaptaron los
fixtures, se conservaron las comprobaciones de colores/formato/enlaces mediante
los renderizadores compartidos reales, y se controló el reloj de prueba. No se
cambió producción para recuperar etiquetas antiguas ni detener animaciones.
La prueba de tarjeta corregida también pasó aislada antes de la ejecución final.

`git diff --check` correcto. Permanecen versionName **0.6.0**, versionCode **20**,
Room **48** y lector JSON **1–40**. No se ejecutó assemble/package, no se generó APK,
no se hizo commit, push, tag ni publicación. Se conservan los cambios previos.

## Corrección del cierre al seleccionar imágenes

El diagnóstico con Logcat, comparación con Antisocial, corrección de Apariencia,
política de resolución por superficie y validaciones se documentan en
[TEMAS_IMAGENES_DIAGNOSTICO.md](TEMAS_IMAGENES_DIAGNOSTICO.md). No cambia el
formato de temas o respaldos, las cinco imágenes ni los controles simplificados.

## Pulido visual de skins: estado de trabajo

Se reutiliza el mismo dibujo de imágenes para las cuatro tarjetas y sus vistas
previas. No se cambian dimensiones, encuadres ni controles de las tarjetas sin
imagen. Se retira la opacidad calculada para toda la fotografía; únicamente el
lado izquierdo recibe el degradado. La interpolación de dibujo usa calidad alta.

El menú lateral se abre en una ventana de borde a borde, con barra de estado
transparente. La imagen comienza arriba; el logo y el cierre se colocan bajo el
inset superior, y los accesos respetan recortes laterales y el borde inferior.
El cierre conserva 48 dp y se integra en el encabezado en lugar de añadir una fila
vacía sobre la imagen. El logo se dibuja en gris neutro únicamente cuando hay un
tema personalizado; los recursos originales y los temas oficiales no se editan.
El editor conserva Color, Imágenes opcionales y Previsualizar/Guardar, con los
tres accesos visibles y acciones de imagen repartidas sin desplazamiento horizontal.

El usuario autorizó conservar originales y ampliar persistencia y respaldo. Las
imágenes nuevas guardan sus bytes exactos en `theme-originals/<UUID>.img`, cifrados
mediante SecureFiles. El sufijo privado es opaco; el MIME y las dimensiones se
validan contra el contenido. Se mantienen los límites de entrada de 20 MiB y
100 megapíxeles, sin reducir el original. El PNG pequeño histórico permanece
como vista de carga/compatibilidad; las superficies y el encuadre prefieren una
decodificación acotada del original, con orientación EXIF, sin sobrescribirlo.
La caché se limita al conjunto de las cinco superficies del tema activo.

ThemeImage añade metadatos opcionales de original en el schema 3. Los schemas 1/2
siguen legibles y no se reescriben al consultar. Una imagen antigua que ya fue
reducida no recupera detalle por sí sola: debe volver a seleccionarse del origen.
No cambian los tres pasos ni se añade un editor o controles avanzados.

El respaldo v2 usa JSON 41 solo cuando contiene originales de temas; conserva
lectores 1–40. El JSON sigue limitado a 16 MiB y las imágenes viajan por streaming
con tamaño y SHA-256, no en base64 dentro del manifiesto. La importación y
exportación individuales siguen usando JSON: los temas históricos conservan su
forma, y los nuevos incluyen un envoltorio identificado con el tema y sus bytes
originales. Importar valida y publica archivos cifrados nuevos antes del guardado
atómico de la biblioteca. El fallo conserva la biblioteca anterior.

La última referencia confirmada y las reservas de otros editores protegen el
original. Guardar/descartar libera las reservas mediante un diario durable;
reemplazar o eliminar solo retira archivos sin referencias después del commit.
La limpieza se reintenta al iniciar, tras cambios privados y periódicamente.
Reservas de editores interrumpidos no caducan; si el proceso muere antes de una
finalización explícita pueden conservar el original pendiente. Los diarios,
reservas y vistas decodificadas son estado operativo, no datos del backup.
Los originales confirmados sí son obligatorios. Room permanece en 48.
No se han cambiado versiones de la aplicación ni generado APK, firma, commit o push.

### Validación y archivos de esta intervención

Compilación final correcta (`compileDebugKotlin`), sin APK. Se aprobaron 72 casos
únicos en ejecuciones dirigidas: CustomThemeTest 12, ThemeImageImportTest 5,
CustomThemeUiTest 15, DrawerOrganizationTest 6, navegación integrada 1,
ThemeOriginalsTest 6, StorageLifecycleTest 11, BackupFormatTest 14 y
PrivatePreferencesBackupTest 2. La ejecución de originales/almacenamiento fue
48/48 y la comprobación final de cinco superficies/insets fue 3/3. Se corrigieron
el contexto/sincronización de los fixtures Robolectric y el cierre del editor sin
fuentes; no se ocultan fallos. Los mensajes de decoder ante entradas inválidas son
parte de esas pruebas. No se acredita validación táctil ni de barras en teléfono.

Código intervenido: ui/theme/{ThemeImages,ThemeImageImport,ThemeOriginals (nuevo),
ThemeDocuments,CustomTheme,Theme}.kt; ui/{ThemeIdentity,OfficialIcons,NavigationChrome,
AppRoot,CustomThemeEditor,AppearanceScreen}.kt; Arachn0deApplication.kt;
data/local/ImageFileLifecycle.kt; backup/{BackupAvatarFiles,BackupData,
BackupImageFiles,BackupJson,BackupRepository}.kt.
Pruebas: ui/theme/{CustomThemeTest,ThemeOriginalsTest (nuevo)}.kt;
ui/{CustomThemeUiTest,DrawerOrganizationTest}.kt.
Documentación: TEMAS.md, ARCHITECTURE.md y BACKUP_FORMAT.md.
Se conservan todos los cambios previos del repositorio.

Pendiente físico: apariencia con fotografías reales, brillo y distintos temas;
insets con notch/recortes y Android 15/16; encuadre/pinza; consumo de memoria en
un teléfono con cinco fuentes grandes; intercambio por SAF y restauración real.
Los bitmaps de pantalla se acotan a 2048 px, conservando intactos los bytes de
origen. Fuentes antiguas reducidas necesitan nueva selección. Reservas de editores
interrumpidos se conservan sin caducidad, según el contrato de archivos.
