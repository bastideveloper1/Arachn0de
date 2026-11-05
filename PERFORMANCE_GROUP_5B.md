# Rendimiento — Grupo 5B: presentación de Tragaperras

## Cambios y alcance

La pantalla usa fondo negro, bloques carbón redondeados, bordes discretos,
iconos monocromáticos, tipografía Material del sistema y logotipo oficial.
Se mantienen la navegación global inferior y los módulos ajenos a Tragaperras.

- `slots/JungleSlots.kt`: cabecera, selector directo Tradicional/Tareas/Pago,
  tabla con los ocho símbolos originales, tres rodillos verticales sin marco
  ilustrado, bloque Créditos/GIRAR/Premio y herramientas estándar. Ajustes,
  Sonido, Historial, Estadísticas, Premios y Depositar reutilizan sus callbacks.
  Historial/Estadísticas pertenecen a Pago; no se inventan registros en otros
  modos. El selector conserva estados separados y se bloquea durante el giro.
- `slots/JunglePanel.kt`: las mismas confirmaciones y formularios existentes
  se presentan en una superficie estándar, sin sprites ni marco ornamental.
  Se conserva cierre, scroll, teclado, confirmación y animación de premio.
  La animación de confeti conserva su duración y usa tonos monocromáticos.
- `slots/SlotScreen.kt`: selección directa del modo y accesos a las páginas ya
  existentes de Historial/Estadísticas. Se retiran estado/importaciones de la
  fachada antigua que ya no dibujaban nada. El ciclo de giro y su propietario,
  resultados confirmados, depósito/retiro, audio y pausa siguen siendo los mismos.
- Retirados `SlotFacade.kt`, `SlotBottomMenu.kt` y `SlotLedDisplay.kt`: utilidades
  exclusivas de la fachada anterior sin consumidores de producción. Sus pruebas
  gráficas y dos casos de geometría/contraste obsoletos se retiran; no se alteran
  las pruebas de cálculos ni el motor.

La captura orienta la distribución, no los importes. Se conservan exactamente
los ocho símbolos y pagos actuales: 1, 2, 3, 4, 5, 7, 10 y 20 unidades; tablas
históricas de siete símbolos y tablas personalizadas mantienen sus propios
valores y el mismo mapeo de presentación. No se recomprime ni sustituye ningún
símbolo o GIF por una imagen nueva.

## Recursos y tamaño

Se comprobaron consumidores antes de retirar los recursos: las referencias de
producción restantes cargan únicamente los ocho símbolos y ocho GIF originales.
Los 16 PNG de la fachada anterior ya no tenían lectores activos; sus consumidores
restantes eran pruebas de esa fachada. No son archivos privados de usuario.

| Recursos PNG retirados | Archivos | Bytes |
| --- | ---: | ---: |
| Fachadas y símbolos anteriores sin consumidores | 16 | 36,714,732 |
| Fondos, carteles y paneles | 7 | 13,948,275 |
| Botones ilustrados | 11 | 30,668,774 |
| Marcos de Tareas | 3 | 7,315,194 |
| Marcos de Pago | 3 | 6,861,499 |
| **Total** | **40** | **95.508.474** |

Reducción de recursos: **91,083979 MiB**. Se conservan byte por byte los ocho PNG
`slots/jungle/iconos/` y los ocho GIF `slots/jungle/premios/`. Reducción neta
adicional de Kotlin de producción: **14.006 bytes**. No se mide reducción de APK:
no se genera ningún APK ni se modifican versión, dependencias o commits.

## Funcionamiento y datos conservados

Comparación SHA-256 con el estado inicial en `/tmp/group5b-before`: SlotMachine,
TaskSlotSession, SavingsRepository, SavingsMath, SavingsLedger, SavingsCodec,
SavingsBackup, SavingsPanel, SlotSounds, JunglePrizeAnimation y SlotTaskWindow
permanecen idénticos. También todos los archivos fuera del módulo, incluidos
los Grupos 1–5A, cifrado, autenticación, Room, backups, versión y navegación global.
No cambia la persistencia ni se borran datos privados. Los recursos retirados
son decoración empaquetada, no datos incluidos en backups. No es necesaria una
migración ni repetir suites de compatibilidad que siguen siendo aplicables.

Se conservan giro, posiciones visuales, duración y parada de rodillos,
resultados comprometidos, probabilidades, créditos, importes, premios, reservas,
compra de tiradas, depósitos/retiros, banco, conciliación, historial, estadísticas,
ajustes y sonido. Los GIF de premio y el visor compartido conservan sus archivos e implementación.
El selector cambia los mismos estados de sesión, sin nuevas preferencias.

## Validación focalizada

**Compilación correcta y seis pruebas distintas aprobadas.** La última ejecución
termina con BUILD SUCCESSFUL. Se compilan las fuentes de producción y unitarias;
solo se ejecutan los seis casos seleccionados, no una batería completa:

- Recursos: ocho símbolos originales decodificables, valores actuales iguales
  a la tabla de pagos del motor y ocho GIF accesibles. Pillow verificó además
  sus 208 fotogramas y dimensiones; todos conservan SHA-256 inicial.
- Tres modos: selección directa, estado activo, tres rodillos, GIRAR, sonido,
  accesos existentes, depósito exclusivo de Pago y salida.
- Tareas: tarjetas originales, selección independiente de Núcleos y estado READY.
- Bloqueos durante giro/segundo plano, con Premios accesible durante giro activo
  como antes; los controles esperan a que termine la carga de símbolos.
- Depósito: un ingreso de $1.000 y cinco tiradas, sin duplicar el movimiento.
- Retiro: un gasto, cierre del premio y saldo final correcto.

La primera ejecución aprobó cinco casos. El lector Movie de JVM devolvió null
para los GIF; se ajustó únicamente esa comprobación al soporte del arnés y se
verificaron los archivos con Pillow. También se corrigieron las condiciones
visuales de carga y acceso a Premios para igualar la disponibilidad original.
Se repitieron solo el caso de recursos y los dos casos afectados por esas
condiciones. No se repitieron depósito, retiro ni selección de Núcleos.

mergeDebugAssets correcto: 19 assets restantes con bytes idénticos a fuentes,
sin ninguno de los 40 PNG retirados en la salida. Sin referencias rotas a código
ni recursos retirados. La comparación inicial/final confirma que los archivos
ajenos a Tragaperras, incluidos todos los grupos anteriores, están intactos.

Evidencia: `/tmp/group5b-validation.log`, `/tmp/group5b-focused.log`, XML iniciales
y finales en `/tmp/group5b-results/` y lista única `passed.txt` en esa carpeta.

## Revisión manual pendiente

Comprobar en teléfono el aspecto con la cabecera/barra globales, texto grande,
pantalla pequeña, desplazamiento, animación real de rodillos/GIF y audio.
Las pruebas de JVM no acreditan reproducción física de GIF ni sonido. No se
midió una APK, rendimiento en hardware o consumo de RAM/batería. Sin fallos
pendientes en los seis casos seleccionados. No se modifica ningún dato privado,
versión o formato de backup; no se genera APK ni se crean commits.
