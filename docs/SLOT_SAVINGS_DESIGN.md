# Ahorro — implementación y validación

El usuario confirmó Banco más cuenta concreta y 0 < RTP ≤ porcentaje del fondo, con ahorro + fondo = 100 %. Implementación integrada y compilada, con 29 métodos focalizados aprobados. La comprobación visual y física en teléfono sigue pendiente.

## Funcionamiento implementado

- Tercer modo en el mismo menú y fachada clásica; panel superior de créditos abre configuración, compras, recargas, premios, estadísticas e historial. Botón rojo ejecuta los giros. Los otros modos conservan sus motores y reglas.
- Configuraciones versionadas por compra: precio y tabla de los créditos existentes no cambian al editar. Se exige coincidencia exacta entre RTP calculado y objetivo, y el objetivo no puede superar el porcentaje del fondo.
- Compra y recarga requieren «Ya deposité» y generan INCOME en la cuenta concreta, con conceptos diferenciados. Recargas no generan créditos ni ahorro por compras.
- Reparto por acumulador entero ponderado: suma de unidades compradas × porcentaje de ahorro de cada compra; el ahorro asignado es el suelo del acumulado dividido por 10.000. Se arrastra el resto entre configuraciones, sin redondear de nuevo cada compra ni crear dinero. Todo movimiento y reserva física sigue siendo múltiplo de 1.000 CLP.
- Antes de cada giro se exige un crédito y fondo libre que cubra el premio máximo de la tabla de ese crédito. También se exige que Banco respalde todos los fondos y que los vínculos de movimientos sigan íntegros. No se dibuja un resultado ni se consume crédito si está bloqueado.
- Consumo, resultado y reserva de premio se confirman en una transacción antes de animar. Un giro ya confirmado pero no presentado se reproduce al regresar sin nuevo consumo. Se registra el final de su presentación por separado.
- Premios pendientes se retiran mediante EXPENSE confirmado o se conservan pasando su reserva a ahorro protegido sin movimiento físico. Las decisiones son idempotentes.
- La cuenta seleccionada representa la caja: su saldo no atribuido se incorpora al fondo mediante ASSIGN, sin duplicar ingresos. Si un retiro externo disminuye el respaldo ya comprometido, se bloquea y se pide revisar Banco. Las compras/recargas/retiros propios no pueden anularse aisladamente desde Banco; las operaciones ajenas mantienen sus reglas. Un respaldo con un vínculo histórico anulado se conserva, pero bloquea juego hasta revisión, sin reparación automática.
- El cambio de cuenta se bloquea con créditos o fondos/reservas pendientes. No se implementan traslados automáticos de fondos ni nuevos modelos de máquina.

## Persistencia y respaldo

Sin nuevas tablas ni archivos, sin migración Room (sigue en 53). Se reutiliza private_preferences en la base SQLCipher: `slot_savings` contiene configuración, versiones y libro de eventos tipado. Créditos, fondos, premios y estadísticas se reconstruyen por reproducción validada del libro, evitando contadores incongruentes. Los movimientos físicos son exclusivamente los de Banco, referenciados por ID y verificados al exportar/restaurar. El backup JSON pasa a 51 cuando existe esta preferencia; se conservan lectores 1–50. El contenedor y cifrado existentes no cambian.

Límites de integridad: payload del módulo hasta 8 MiB; hasta 100.000 eventos dentro del presupuesto de registros del backup. No se borra ni caduca historial. Se reserva espacio para resolver premios pendientes antes de aceptar más operaciones nuevas. No se introducen archivos propios ni una limpieza adicional; imágenes y posibles adjuntos de la creación de Banco usan el flujo normal del proyecto, incluyendo su gestión de archivos y backup.

## Tabla base predeterminada

Coste de tirada: 1.000 CLP. Importes en unidades enteras de 1.000 CLP.

| Combinación | Premio CLP | Probabilidad exacta |
|---|---:|---:|
| Cereza ×3 | 1.000 | 750/10.000 = 7,50 % |
| Limón ×3 | 2.000 | 300/10.000 = 3,00 % |
| Campana ×3 | 3.000 | 200/10.000 = 2,00 % |
| Estrella ×3 | 4.000 | 120/10.000 = 1,20 % |
| Diamante ×3 | 5.000 | 60/10.000 = 0,60 % |
| BAR ×3 | 7.000 | 30/10.000 = 0,30 % |
| Siete ×3 | 10.000 | 6/10.000 = 0,06 % |

Se mantienen los índices reales de SlotMachine.symbols. Las probabilidades corresponden a COMBINACIONES: no se afirma que sean tres rodillos independientes uniformes. El muestreo elige una combinación ganadora con los pesos declarados o, al perder, una de las 336 combinaciones no ganadoras equiprobables. Esto mantiene transparente toda la distribución, sin resultados condicionados a premios anteriores, saldo o reservas. Cada combinación perdedora tiene probabilidad 4.267/1.680.000; las ganadoras más las perdedoras suman exactamente 1.

RTP = (750×1 + 300×2 + 200×3 + 120×4 + 60×5 + 30×7 + 6×10)/10.000 = 3/10 = 30 %.
Ventaja matemática: 7/10 = 70 %. Valor esperado del premio: 300 CLP por tirada. Probabilidad de premio: 733/5.000 = 14,66 %. Frecuencia esperada: 5.000/733 tiradas por premio. Varianza: 417/500 unidades² = 834.000 CLP². Desviación estándar aproximada: 913,24 CLP. Premio máximo y cobertura mínima requerida: 10.000 CLP antes de cada giro; la comprobación se repite antes de cada giro y no cubre anticipadamente todas las tiradas compradas.

## Archivos principales

- slots/SavingsMath.kt, SavingsLedger.kt, SavingsCodec.kt, SavingsRepository.kt, SavingsBackup.kt, SavingsPanel.kt.
- slots/SlotScreen.kt, SlotMachine.kt, SlotBottomMenu.kt, TaskSlotSession.kt (integración y presentación de resultados confirmados).
- ui/AppRoot.kt (entrada/salida contextual de Banco).
- bank/BankRepository.kt (protección específica de movimientos vinculados a Ahorro).
- backup/BackupData.kt, BackupJson.kt (versión 51, validación del libro y de enlaces).
- Tests SavingsMathTest, SavingsOperationalTest, SavingsRepositoryTest, SavingsUiTest y SavingsRenderingTest.

## Validación ejecutada

Compilación de producción y tests Kotlin mediante Gradle offline, sin instalar dependencias ni generar APK. Simulación matemática de 1.000.000 tiradas con semilla reproducible:

- RTP observado: 29,9897 %.
- Premios: 146.212 (14,6212 % de las tiradas).
- Varianza observada: 0,835582789391 unidades²; teórica: 0,834.
- Frecuencias por índice real BAR/Diamante/Estrella/Campana/Limón/Cereza/Siete: 3.020 / 6.074 / 11.967 / 20.062 / 29.889 / 74.605 / 595.
- Retorno, varianza, categorías y las 336 combinaciones perdedoras dentro de seis desviaciones estándar de sus valores esperados.

La simulación matemática no incluye bloqueos por fondos. La simulación operativa separada de 10.000 giros comprobó fondos suficientes antes de cada giro y la igualdad de caja después de cada evento: 8 bloqueos/recargas, 14.000 CLP de capital adicional, protegido final 8.484.000 CLP, libre 10.000 CLP, pendiente 1.000 CLP, caja atribuida 8.495.000 CLP. Esta simulación no escribe movimientos reales; las pruebas de repositorio sí utilizan Banco y Room de prueba.

29 métodos focalizados aprobados: SavingsMathTest (2), SavingsOperationalTest (1), SavingsRepositoryTest (6), SavingsUiTest (4), SavingsRenderingTest (1), SlotMachineTest (4), SlotCreditsTest (4), TaskSlotSessionTest (5), BankPaymentUiTest (2).

Cobertura: compra y confirmación, cancelación sin escritura, movimientos únicos, redondeo acumulado, créditos al reabrir, resultados/precios versionados, cobertura máxima y déficit, caja vacía, premios consecutivos/reservas, retirada y conservación, conciliación externa, prevención de anulación aislada, rollback ante fallo de escritura, backup JSON 51, restauración histórica y rechazo de datos corruptos sin sustituir datos anteriores. Los tests de interfaz comprueban además compra confirmada, configuración inválida y buscador inferior. Rendering comprueba los siete símbolos al finalizar y recuperar. Regresiones de Tradicional, Tareas y panel de pagos aprobadas.

El primer pase detectó dos aserciones de test desactualizadas: `version` en vez de `dataVersion` en el backup y ajuste a altura en vez de la fachada aprobada a ancho completo. Se corrigieron los tests sin cambiar código funcional; el segundo pase ejecutó únicamente estos casos y el nuevo test de representación. Logs locales: /tmp/arachnode-slot-savings.log y /tmp/arachnode-slot-savings-recheck.log.

Los hashes de producción coinciden con los insumos de compilación. Imágenes, sonidos, skins y manifiesto permanecen iguales al estado anterior. La versión de aplicación sigue 0.17.1/code32. No se genera APK, commit ni push.

## Comprobación manual pendiente

Fachada, símbolos centrales respecto a resultados guardados, gestos/animaciones y sonido; selección/creación de Banco y cuenta; confirmaciones físicas, paneles/teclado; cierre forzado del proceso durante giro/compra y recuperación en dispositivo cifrado; copia y restauración en otra instalación. Los movimientos físicos dependen de la confirmación del propietario: la aplicación no comprueba el contenido real de la caja.

## Corrección: el saldo existente de la cuenta sí está disponible

Este apartado sustituye la regla anterior de no atribuir saldo preexistente. Al elegir la cuenta, su saldo no atribuido se incorpora automáticamente al fondo libre. También se aplica a cuentas ya vinculadas y depósitos posteriores. No registra otro INCOME, no crea créditos y no aplica el reparto de compras ni cuenta como ahorro generado. Se contabiliza como capital asignado (`ASSIGN`), separado de recargas físicas, y se muestra en estadísticas/historial.

Se descuenta todo lo ya atribuido, incluidos ahorro protegido y premios pendientes; por tanto no vuelve a asignarlos al leer, reabrir o restaurar. Se respetan múltiplos de 1.000 CLP: por ejemplo, saldo 10.500 incorpora 10.000 y conserva 500 en Banco sin usarlos como cobertura. Las retiradas externas insuficientes y los vínculos históricos rotos siguen bloqueando los giros. Si la cuenta ya aporta 10.000 de cobertura y hay créditos comprados, no exige otra recarga para el premio máximo predeterminado.

Nuevo invariante: fondos = compras + recargas + capital asignado − retiros = protegido + libre + premios pendientes. Backup JSON 52 / codec 2; lectura histórica conservada, sin cambio Room ni archivos. Compilación correcta y 19 métodos focalizados aprobados (10 de repositorio Ahorro, 4 de interfaz, 1 simulación operativa y 4 de eliminación de cuentas). Se comprueban saldo preexistente, cuentas ya asignadas, no duplicación al reabrir/configurar/restaurar, ausencia de ingresos artificiales, exclusión de ahorro protegido/premios reservados, múltiplos enteros y compatibilidad histórica/rollback. Log: /tmp/arachnode-savings-account-balance.log. No se genera APK ni cambia versión de aplicación.
