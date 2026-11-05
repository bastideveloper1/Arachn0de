# Check de tarjetas con Pago — especificación definitiva integrada

Esta autorización sustituye la política anterior de no completar con un pago total.
No cambia Room 55, JSON 58, versión de aplicación ni datos históricos.

| Situación | Resultado del check |
|---|---|
| Sin importe | Acción de estado existente, con sus 2/3 estados configurados |
| Pendiente con deuda | Abre `CardPaymentPanel` → `BankPaymentEditor` sobre la lista, con tarjeta preseleccionada, pendiente precargado y DIRECTO como origen |
| Pendiente con deuda ya saldada | Completa directamente, sin registrar otro pago |
| Ya Completada | Mantiene la acción explícita de estado existente; no hay reparación retrospectiva de deudas históricas |

Los abonos parciales conservan el estado. Un pago que cubre exactamente el pendiente
inserta operación/asignación y después llama al escritor normal de estados para
marcar Completada, en la misma transacción. Esto se aplica a Banco principal,
cuentas, campo Pago y check. No se cambia la herencia de estados ni la transición
normal de tarjetas sin importe. No se crean eventos o pagos duplicados al repetir
una confirmación. Los eventos de estado usan el tipo y backup existentes.

Si falla registrar el pago o completar, se revierte toda la operación: saldos,
asignaciones, estado, eventos y un posible primer Núcleo Banco. Los marcadores de
asignaciones históricas se conservan al completar con un pago nuevo. Las reglas
anteriores de protección de monedas históricas y obligaciones compartidas no se
amplían en esta intervención.

`rememberPaymentCheck` resuelve el pendiente confirmado sin navegar. `NodeCard`
conserva su check y los resultados del buscador usan la misma ruta. Los checks de
resultados sin importe también ejecutan su acción de estado sobre la lista.
El panel no añade un menú de operaciones ni otro formulario. Solo el check precarga
el pendiente; el acceso existente desde el campo Pago conserva su edición secundaria.

La prueba de una lista larga detectó que trasladar el pie al diálogo aumenta el
viewport del fondo y puede ajustar su scroll al llegar al final. Se conserva el
índice/offset de la lista de origen y se restaura cuando vuelve su altura original.
Se mantienen la misma navegación, filtro, lista y orden. No se persisten estos datos.

## Archivos

Código en `app/src/main/java/com/r0ybt/arachn0de/`:

- `ui/PaymentCheck.kt` (nuevo), `ui/CardPaymentPanel.kt`, `ui/BankPaymentEditor.kt`.
- `ui/NodeComponents.kt`, `ui/ProjectScreen.kt`, `ui/ContextCalendarTaskCard.kt`, `ui/ProjectsScreen.kt`.
- `bank/BankRepository.kt` (finalización dentro de la transacción).

Pruebas JVM en `app/src/test/java/com/r0ybt/arachn0de/`:

- `ui/NavigationWorkTest.kt`: check desde lista, importe precargado, cancelación,
  parcial/total, fallo de finalización, cero duplicados, estados y scroll; búsqueda
  conserva consulta y resultados sin abrir el detalle.
- `bank/BankUnifiedPaymentTest.kt`: total, fallo de estado con rollback completo,
  herencia de 3 estados y conservación de asignaciones históricas.
- `bank/BankPaymentPanelTest.kt`, `bank/BankIntegrationTest.kt`,
  `bank/BankPaymentUiTest.kt`, `bank/BankAccountDeletionTest.kt`: expectativas de pago
  total actualizadas a la especificación definitiva.
- Documentación: `ARCHITECTURE.md`, `docs/BANK_PAYMENT_IMPLEMENTATION.md`, este archivo.

La migración y los formatos de backup no cambian ni se vuelven a ejecutar sus suites.
La prueba SQLCipher nativa anterior sigue pendiente de dispositivo; no se genera APK.

## Resultado de esta corrección

14 métodos focalizados aprobados mediante `:app:testDebugUnitTest --offline --tests ...`:

- Los siete métodos actuales de `BankUnifiedPaymentTest`, incluidos fallo al
  completar, rollback de Banco/eventos, reintento idempotente, herencia de 3 estados
  y conservación de marcadores históricos.
- `BankPaymentPanelTest.strictConfirmationKeepsStaleChecksRollbackAndAnnul`.
- `BankIntegrationTest.incomeTransferPaymentsDirectAndDifferentPeople` y
  `annulCorrectionStatesAmountAndHistoryGuards`.
- `BankPaymentUiTest.mainDirectPaymentWithoutAccountsAndFinalCancellationWritesNothing`.
- `BankAccountDeletionTest.deletingPaymentHistoryRestoresPendingDebtAndPreservesTask`.
- `NavigationWorkTest.listCheckOpensPaymentInPlacePrefillsRemainderAndCompletesOnlyAfterAtomicFullPayment`.
- `NavigationWorkTest.searchResultCheckPreservesQueryAndResultsWithoutEnteringTheCard`.

La prueba de lista confirma: sin importe conserva Por verificar en configuración de
3 estados; con deuda abre el formulario sin entrar al detalle; precarga 100.000 y,
tras abonar 30.000, precarga 70.000; cancelación intacta; fallo de finalización sin
pago ni estado; reintento total completa; pulsar después un check saldado no crea
otro pago. Verifica el scroll y las posiciones persistidas. La búsqueda conserva
su consulta y resultados al cancelar y al abonar, sin navegar al detalle.
`git diff --check` aprobado. Queda la comprobación física de teclado, gestos y
SQLCipher; no se ejecutaron suites generales ni migraciones sobre datos reales.
