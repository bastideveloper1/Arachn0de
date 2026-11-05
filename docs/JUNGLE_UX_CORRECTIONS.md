# Correcciones de interfaz y caja de la tragaperras

Trabajo sobre el estado local existente, sin generar APK, cambiar versión, commit ni push.

## Contrato financiero final

La cuenta bancaria asociada es la caja de la máquina. CAJA abre directamente el depósito y compra de tiradas, no las estadísticas. El botón inferior con moneda hace la misma operación. El panel contiene cuenta, saldo, importe CLP, tiradas a recibir y DEPOSITAR; el icono X permite abandonar sin registrar compra. No hay una segunda confirmación, Cancelar/Cerrar duplicados ni un campo que confunda dinero con número de giros.

Conversión nueva fija: 1.000 CLP → 5 tiradas; 20.000 → 100; 40.000 → 200. La interfaz analiza CLP y el repositorio vuelve a validar el importe y la asociación. El ingreso y los créditos se guardan juntos dentro de una transacción. Se inspecciona la configuración/historia y, si es necesario, se añade una configuración para compras futuras sin reescribir las anteriores. Una confirmación repetida con el mismo ID no crea otro ingreso.

Las cinco tiradas no producen movimientos bancarios. El premio adjudicado queda reservado y pendiente; el saldo solo disminuye al pulsar RETIRAR. Ese es el único botón de acción financiera en el cartel del premio. Cerrar el cartel no cobra, no conserva como ahorro y no cancela el premio. El último retiro cierra el panel; otras reservas siguen consultables desde el indicador de premio. KEEP se conserva únicamente por compatibilidad histórica del libro; ya no se ofrece como acción.

Caso dirigido obligatorio: saldo inicial 40.000 → depósito 1.000, 5 créditos y saldo 41.000 → cinco giros, premio de 2.000 pendiente y saldo todavía 41.000 → RETIRAR una sola vez, saldo 39.000. Se comprueba la reserva, caja atribuida y movimientos en cada etapa, persistencia/restore pendiente, repetición del mismo ID y rechazo de un segundo cobro.

El error anterior consistía en introducir dinero en un campo que interpretaba cantidad de tiradas. El importe de 40.000 podía representar 40.000 créditos y consumir uno dejaba 39.999. No se inspeccionó ni modificó automáticamente la base privada del teléfono: no hay corrección masiva de compras, créditos o movimientos históricos. Su importe efectivamente registrado debe revisarse antes de cualquier corrección explícita.

## Presentación

- Rótulo de modo desplazado hacia el centro de la franja y textos de créditos/premio hacia sus zonas útiles.
- La modalidad se presenta como Pago. Su franja abre Opciones de Pago; estadísticas, historial y configuración están allí, separados del depósito.
- El material real de volumen contradice la descripción original de archivos: volumen2.png tiene el altavoz iluminado, volumen1.png es el apagado. El estado de sonido debe seleccionar el sprite por esa luz real, no por la antigua numeración descrita.
- Tareas prepara directamente el selector de Núcleo. Las tarjetas originales reciben todo el ancho/alto de la apertura, sin reducirlas para reservar columnas laterales. Las flechas se dibujan delante del marco y fuera del contenido principal; no hay que intuir áreas invisibles.
- Depósitos y premios usan menuemergente.png, sin editar sus bytes ni deformarlo; el interior desplaza y las acciones permanecen accesibles.
- Victoria: breve animación de partículas y sonido distinto, temporal, sin archivos nuevos. El mute y el cierre de sesión/lifecycle siguen deteniendo sonidos.

## Economía y compatibilidad

Tabla aprobada y prueba exacta: docs/SLOT_RTP_REVIEW.md. No se vuelve a ajustar el RTP ni los premios después de esa aprobación.

JSON 54 / SavingsCodec 4 añaden créditos por paquete y denominador por configuración. Las versiones 1–3 se leen explícitamente como un crédito por precio guardado y probabilidades sobre 10.000. Así se conserva el significado histórico de cada compra; no se reinterpretan sus cantidades con el paquete nuevo de cinco. El coste jugado se deriva en CLP enteros por crédito para estadísticas correctas entre versiones. Room, SQLCipher y movimientos anteriores no cambian. Se mantiene validación previa/rollback y el historial de decisiones antiguas.

## Archivos principales

slots/SavingsMath.kt, SavingsLedger.kt, SavingsCodec.kt, SavingsRepository.kt, SavingsPanel.kt; slots/JungleSlots.kt, JunglePanel.kt, SlotScreen.kt, SlotTaskWindow.kt, TaskSlotSession.kt, SlotSounds.kt; backup/BackupJson.kt. No hay PNG ni dependencias nuevos.

Validación terminada: compilación Kotlin principal y de pruebas correcta; 43 pruebas focalizadas distintas aprobadas entre las tandas financieras, matemáticas, de compatibilidad, Banco y controles. La tanda final de 16 pruebas pasó; su primer intento tuvo una colisión de archivos temporales de resultados de Gradle y se repitió únicamente esa tanda. Verificados depósito por cantidad, idempotencia, respaldo/restauración, premio pendiente y ejemplo $40.000 → $41.000 → $39.000. Los 31 PNG originales coinciden por SHA-256. No se inspeccionaron datos privados del teléfono ni se corrigieron cantidades históricas automáticamente. Revisión física pendiente de alineación final, lectura de tarjetas con skins/fuentes ampliadas y efecto sonoro en Android real.

## Rodillos independientes y premios: ajuste focalizado

Cada rodillo utiliza una permutación cíclica visual distinta (pasos 1/3/5 para ocho símbolos y 1/2/3 para los siete históricos). El índice visual del resultado se calcula por separado para mantener continuidad al iniciar y detener el giro. No utiliza aleatoriedad adicional ni cambia resultados, probabilidades, premios, RTP o persistencia. Las filas vecinas de tres símbolos centrales iguales son diferentes entre rodillos.

El panel de premios elimina también la X de Cerrar: solo muestra RETIRAR como acción. Se puede abandonar con Atrás o tocando fuera del panel, sin resolver el premio. Se conserva la operación bancaria transaccional existente.

Validación del ajuste: compilación correcta, cinco pruebas de SlotMachine y dos pruebas del panel de premios aprobadas. Repetida únicamente la prueba de abandono tras reforzarla para comprobar el cierre real tocando fuera: aprobada, sin cambios de saldo ni reserva.
