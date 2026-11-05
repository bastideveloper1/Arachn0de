# Tragaperras selvática

Integración sobre el módulo existente, sin APK ni cambio de versión de aplicación. Se conserva todo el árbol previo, incluidos Banco y la incorporación automática del saldo existente.

## Recursos originales

31 PNG copiados sin alterar bytes desde `/home/r0ybt/Descargas/casino 2` a `app/src/main/assets/slots/jungle`, conservando nombres y carpetas:

- Estructura (6): fondo.png, creditos_premios.png, premioscartel.png, menuemergente.png, barra_modo.png, divisor.png.
- Marcos (6): marco modo tareas/marcotareas1.png, marcotareas2.png, marcotareas3.png; marco modo pagos/marcopago1.png, marcopago2.png, marcopago3.png.
- Símbolos (8): iconos/1cereza.png, 2platano.png, 3guacamayo.png, 4Lingotes.png, 5lingotes.png, 6rubi.png, 7esmeralda.png, 8mascara.png.
- Controles (11): Botones inferior/panel_inferior.png; girar1/2, modo1/2, pagar1/2, salida1/2 y volumen1/2.png.

Los recursos clásicos siguen conservados para compatibilidad de la aplicación y empaquetados históricos; no se modifican ni borran. Los PNG nuevos son assets inmutables de la aplicación, no archivos importados del usuario; no requieren limpieza privada ni copias dentro del backup. La decodificación se hace fuera del hilo principal y se conserva en memoria durante la pantalla. No se modifican colores, alpha, contenido ni proporciones. El fondo cubre la pantalla; marcos y símbolos se ajustan proporcionalmente. Las aperturas tienen una base verde estable para evitar destellos blancos/negros al cambiar de marco. Tareas conserva SlotTaskWindow y las tarjetas originales con sus skins.

## Distribución y controles

Panel inferior horizontal original a todo el ancho, sin deformación. De izquierda a derecha: MODO, VOLUMEN, GIRAR, PAGAR (solo Ahorro), SALIR. GIRAR se centra exactamente en el ancho del panel. Los demás mantienen posición al cambiar modalidad. Áreas táctiles de al menos 48 dp. Las parejas de sprites comparten la misma caja y centro, con ajuste proporcional.

- GIRAR: segundo sprite únicamente durante contacto válido; salir del área, cancelación o liberación restaura el primero. Un giro por pulsación. Si el modo gratuito necesita una moneda inicial se reutiliza la inserción existente antes de girar. No hay créditos gratuitos en Ahorro.
- MODO: luz solo durante contacto; al soltar alterna Tradicional → Tareas → Ahorro. No cambia en pleno giro.
- PAGAR: exclusivamente en Ahorro; sprite 2 cuando hay premios pendientes cobrables, respaldo conciliado y ninguna animación pendiente. Abre el panel existente de premios: el movimiento bancario solo ocurre tras confirmar «Ya retiré el premio». El saldo libre por sí solo no es un premio que pueda cobrarse.
- SALIR: luz táctil y navegación existente. Un giro financiero ya confirmado permanece recuperable.
- VOLUMEN: sprite 1 sonido activado, 2 silenciado. No depende del contacto; conserva los sonidos existentes.

El centro del panel superior de créditos permite insertar moneda en los modos gratuitos o abrir compra/configuración en Ahorro. El panel de premio abre la información existente. Las flechas y tarjetas de Tareas conservan elección de Núcleo y apertura de tarea.

## Tabla autorizada

Tres símbolos iguales en la línea central. La máscara es el premio mayor, sin añadir comodines, multiplicadores o reglas especiales no solicitadas.

| Símbolo | Premio CLP | Probabilidad de combinación en Ahorro |
|---|---:|---:|
| Cerezas | 1.000 | 730/10.000 = 7,30 % |
| Plátanos | 2.000 | 300/10.000 = 3,00 % |
| Guacamayo | 3.000 | 200/10.000 = 2,00 % |
| Lingotes de plata | 4.000 | 120/10.000 = 1,20 % |
| Lingotes de oro | 5.000 | 60/10.000 = 0,60 % |
| Rubí rojo | 7.000 | 30/10.000 = 0,30 % |
| Esmeralda verde | 10.000 | 6/10.000 = 0,06 % |
| Máscara dorada | 20.000 | 1/10.000 = 0,01 % |

El usuario autorizó los importes/cantidad de símbolos del cartel y confirmó reducir cerezas de 7,50 a 7,30 % e incorporar máscara al 0,01 %, manteniendo las otras probabilidades. Coste predeterminado 1.000 CLP. RTP exacto 3/10 (30 %), ventaja 70 %, probabilidad total de premio 1447/10000 (14,47 %), frecuencia esperada 10000/1447 giros/premio. Premio esperado 300 CLP; varianza 109/125 unidades² = 872.000 CLP², desviación aproximada 933,81 CLP. Cada una de las 504 combinaciones perdedoras tiene probabilidad 2851/1680000. No se adapta el azar al saldo ni a resultados anteriores. Reserva mínima antes de cada giro: premio máximo de la tabla del crédito que se consume (20.000 CLP en la nueva).

Tradicional conserva giros uniformes independientes y créditos ficticios: la unidad nominal del cartel es 1.000 CLP, premios 1/2/3/4/5/7/10/20 créditos. No son retiros reales. La distribución uniforme de sus ocho símbolos tiene RTP ficticio 52/512; no se presenta como el RTP financiero de Ahorro. Tareas mantiene selección equiprobable de su rueda de tareas, ajena a la tabla de premios.

## Historial y compatibilidad

Las tablas guardadas de siete símbolos conservan arrays, probabilidades, precios, índices, premios y conceptos bancarios. El motor de presentación admite 7/8 posiciones según el crédito y no vuelve a sortear resultados recuperados. En la fachada nueva, los siete símbolos antiguos se representan con el símbolo selvático de su misma categoría: Cereza→Cerezas, Limón→Plátanos, Campana→Guacamayo, Estrella→Plata, Diamante→Oro, BAR→Rubí, Siete→Esmeralda. Los nombres originales se conservan en historial y conceptos financieros anteriores.

Al comprar desde la interfaz con una configuración anterior se abre la configuración de la tabla selvática antes de nuevas compras. Se requiere Guardar; no se revalorizan los créditos anteriores ni se sobreescriben configuraciones históricas. Si existe una tabla anterior o personalizada que difiere del cartel, se muestra su tabla real sobre menuemergente.png, identificada como tal, evitando prometer importes que no corresponden.

La ampliación de símbolos necesita únicamente compatibilidad del codec 3 y JSON 53: se admiten arrays de 7 u 8 elementos y se conserva lectura de codecs 1/2 y backups 1–52. Sin tablas/columnas nuevas, sin migración Room, sin cambios SQLCipher ni movimientos artificiales. Las asociaciones Banco/cuenta, capital asignado, créditos, reservas y decisiones de premios siguen el mismo libro y transacciones. El cambio mínimo de formato es necesario para conservar el octavo símbolo autorizado, sin perder historia.

## Archivos y validación

Producción: JungleSlots.kt; SlotScreen.kt, SlotMachine.kt, SlotTaskWindow.kt; SavingsMath.kt, SavingsLedger.kt, SavingsCodec.kt, SavingsRepository.kt, SavingsPanel.kt, SavingsBackup.kt; backup/BackupJson.kt. Se reutilizan SlotsSession, SlotSounds, TaskSlotSession, SlotTaskWindow, SavingsRepository, SavingsPanel y los repositorios/paneles de Banco.

Compilación final aprobada. 40 métodos focalizados aprobados: JungleSlotsTest (4), SavingsMathTest (2), SavingsOperationalTest (1), SavingsRepositoryTest (11), SavingsRenderingTest (1), SavingsUiTest (4), SlotMachineTest (4), SlotCreditsTest (4), TaskSlotSessionTest (5) y BankAccountDeletionTest (4).

El primer pase aprobó los 36 casos no visuales; los cuatro de interfaz fallaron antes de ejecutarse por crear un bitmap de prueba fuera de la configuración gráfica adecuada de Robolectric. Se corrigió la fixture con inicialización diferida/bitmap ARGB y renderizador nativo. El último pase ejecutó únicamente esos cuatro casos y compiló la composición final; todos aprobados. También se evita reconstruir el libro financiero durante cada fotograma y se ajusta la tabla anterior/personalizada al interior de su marco.

Simulación matemática: un millón de tiradas, RTP observado 29,8591 %, 144.299 premios, varianza observada 0,871462414719 unidades²; categorías Cerezas/Plátanos/Guacamayo/Plata/Oro/Rubí/Esmeralda/Máscara: 73.006 / 29.929 / 19.852 / 11.994 / 5.867 / 2.950 / 581 / 120. Todos dentro de los intervalos estadísticos definidos respecto a la tabla exacta.

Simulación operativa separada: 10.000 giros, 39 bloqueos/recargas, capital adicional 98.000 CLP, protegido final 8.514.000, libre 20.000, pendiente 0, caja 8.534.000. No ejecuta movimientos reales. Las pruebas de repositorio sí verifican pago bancario de máscara por 20.000, créditos previos de siete símbolos intactos, asignación del saldo existente, transacciones, rollback, conciliación, eliminación de cuentas, backup y restauración.

Hash SHA-256 comprobado para los 31 originales y sus copias. Todos idénticos; manifiesto, permisos, SQLCipher, Room y versión de aplicación intactos. Logs locales: /tmp/arachnode-jungle-compile.log, /tmp/arachnode-jungle-tests.log y /tmp/arachnode-jungle-ui-recheck.log. Composición de referencia de geometría en /tmp/jungle-layout-preview.png; no sustituye una captura de Compose ni la revisión en teléfono.

Pendiente revisión física: tamaños y recortes en teléfonos, nitidez, marcos al parpadear, tarjetas con skins/fuentes ampliadas, interrupción de gestos por Android y recuperación real tras cierre forzado. Sin APK, commit, push, dependencias nuevas ni cambio de versión.

## Corrección posterior de depósito y controles

El comportamiento vigente se documenta en JUNGLE_UX_CORRECTIONS.md y SLOT_RTP_REVIEW.md: CAJA abre depósito, cinco tiradas por $1.000, codec 4 / JSON 54, premios pendientes con RETIRAR y volumen2 iluminado. Las cifras anteriores de simulación describen la tabla histórica de coste $1.000 por giro, conservada para créditos antiguos.
