# Identidad institucional monocromática

Fuente maestra Android: docs/branding/originals/logo-1024.png. Los cinco PNG y el
GIF originales se conservan byte a byte; docs/branding/previous conserva los
recursos sustituidos. Los recursos son estáticos del paquete, sin archivos del
usuario, cachés propias ni necesidad de exportarlos en respaldos.

Launcher normal/redondo (densidades mdpi–xxxhdpi), foreground adaptativo y
monochrome usan el nuevo símbolo. El foreground ocupa el 60 % del lienzo y está
dentro del círculo seguro de 66/108 dp. Los XML previos a API26 usan la variante
legacy. Splash Android usa el foreground actual. Se conservan applicationId,
manifest, nombre, firma y autenticación.

Las referencias bw90_brand_logo, bw90_brand_mark y arachn0de_logo ahora contienen
el símbolo nuevo: encabezados, navegación, menú y documentos institucionales.
El logo no se recolorea. Las cabeceras personalizadas de skins siguen su ruta.

BlackWhite90 centraliza la paleta predeterminada: #121212, #242424, #303030,
#EEEEEE, #D0D0D0, #808080. Los roles semánticos de estados, errores, prioridad y
herramientas no se sustituyen. Colores particulares de skins permanecen en sus
mapas actuales. No se fuerza otra preferencia a los usuarios.

Se añaden Dark Grey, Pure Black, Black & White y Graphite a AppearanceTheme.
El selector recorre el catálogo existente. Las claves y serialización actuales
conservan el nombre del enum, incluidos backup/restauración y caché pública del
tema de desbloqueo; no cambian Room ni el formato JSON. Lectura de temas previos
sin modificaciones. Versiones antiguas de la app no conocen los nuevos temas.

AnimatedBrandLogo carga el GIF original local desde assets/branding mediante
Movie/ContentGifView, el reproductor existente. Se muestra sobre la contraseña,
centrado, hasta 184 dp de ancho y proporción 480:498. Reproduce en bucle únicamente
en RESUMED; retira el observador y libera el Movie de la vista al salir. Sin
red, almacenamiento adicional, validación de contraseña ni gestión de sesiones.

Validación focalizada: originales y GIF idénticos, zona segura y XML, contraste,
persistencia/reapertura, payload portátil de preferencias y catálogo histórico.
Revisión física pendiente: máscaras reales del launcher y caché de iconos,
fluidez/transparencia de GIF y pantalla de contraseña con teclado/tamaños de texto.
No APK ni modificación de versión o firma.

Resultado: compilación correcta y 3 pruebas focalizadas aprobadas, sin fallos.

## GIF transparente e indicador Atención

El usuario entregó logomovimientotransparente.gif (180 fotogramas, 480×498).
Se conserva íntegro en originals y sustituye el asset de desbloqueo, sin procesar
ni recomprimir. La versión opaca original permanece archivada. Atención ahora
muestra fondo de selección y borde cuando está activo; no depende únicamente
de que los tonos de icono sean distintos en paletas monocromáticas.
