# Banco/Pago unificado — implementación y verificación

**Actualización posterior:** la especificación definitiva de
[BANK_PAYMENT_CHECK.md](BANK_PAYMENT_CHECK.md) sustituye la regla anterior de no
completar al pagar. Los pagos totales nuevos ahora completan atómicamente; los
parciales mantienen el estado. No se actualizan estados ni importes históricos.

Autorización integral del usuario y aclaraciones: bloquear nuevas completaciones
con deuda CLP y correcciones que creen deuda en Completadas, conservando estados
históricos; elegir un Banco real si hay varios y crear «Banco» al confirmar el
primer pago desde tarjeta si no hay ninguno, sin crear cuentas ni personas.

## Estado de los requisitos

IMPLEMENTADO significa código integrado y prueba local aprobada cuando se indica.
La ejecución del motor SQLCipher en Android sigue pendiente: no hay dispositivo
ADB conectado. Compilar su prueba no acredita ejecutar la migración cifrada.

| # | Requisito | Estado / evidencia |
|---|---|---|
| 1 | Banco principal → Pagar sin cuentas | IMPLEMENTADO; prueba UI principal y prueba transaccional sin cuentas/personas |
| 2 | Cuenta → Pagar y descuento exacto | IMPLEMENTADO; UI cuenta, integración y saldos |
| 3 | Impedir superar saldo | IMPLEMENTADO; validación inmediata y transaccional |
| 4 | Pago de tarjeta con importe abre el formulario de Banco | IMPLEMENTADO; prueba AppRoot |
| 5 | Tarjeta preseleccionada | IMPLEMENTADO; no exige buscar de nuevo |
| 6 | Cambiar origen DIRECTO/cuenta desde tarjeta | IMPLEMENTADO; ambos cambios ejercitados en UI |
| 7 | Introducir importe cuando no existe | IMPLEMENTADO; editor original probado desde AppRoot |
| 8 | Abonos sucesivos | IMPLEMENTADO; secuencia 20.000 + 30.000 + 50.000 en integración y reapertura tras abono en UI |
| 9 | Pago total | IMPLEMENTADO; pendiente cero y finalización atómica según especificación definitiva |
| 10 | Conservar importe original | IMPLEMENTADO; aserciones del importe original tras pagos |
| 11 | Pendiente y total abonado | IMPLEMENTADO; operaciones/asignaciones existentes y resumen del formulario |
| 12 | Impedir superar deuda | IMPLEMENTADO; UI y transacción |
| 13 | Confirmación idempotente | IMPLEMENTADO; doble confirmación con el mismo UUID y rechazo de importe distinto |
| 14 | Rollback completo | IMPLEMENTADO; fallo tras insertar operación, incluido primer Banco creado transaccionalmente |
| 15 | Historial separado | IMPLEMENTADO; DIRECT en principal, movimientos reales en cuentas; DIRECT históricos conservan contexto almacenado |
| 16 | Estados según la especificación definitiva del check | IMPLEMENTADO; abonos parciales conservan estado, pagos totales nuevos completan atómicamente, estados históricos intactos |
| 17 | Migración histórica 54→55 | IMPLEMENTADO; esquema exportado real, todas las filas, autorreferencias, triggers, FKs y rollback probados en SQLite/Room; motor cifrado pendiente |
| 18 | Backups antiguos/nuevos | IMPLEMENTADO en Room/JSON; restauraciones anteriores a Banco, JSON 49/57/58 y moneda histórica; ejecución SQLCipher nativa pendiente |
| 19 | Conservar pagos bancarios existentes | IMPLEMENTADO; migración compara ambas direcciones de todas las filas; integración bancaria y backups |
| 20 | Navegación, cancelar, cerrar y reabrir los tres accesos | IMPLEMENTADO; pruebas de UI de Banco principal/cuenta/tarjeta |
| — | Ejecución nativa SQLCipher | PARCIAL en verificación: prueba escrita y compilada; no ejecutada sin dispositivo |

No hay un segundo formulario ni un segundo registro financiero. Los tres accesos
utilizan `BankPaymentEditor`, `BankPanel`, `BankDebtCard` y `confirmPayment`.
El selector antiguo «Desde saldo / Directamente» se retira. La tarjeta permite
cambiar origen; principal y cuenta lo fijan. No se predetermina una deuda ni se
distribuye un pago nuevo entre varias tarjetas. La corrección de una operación
histórica con varias asignaciones conserva sus destinos y permite editar sus
importes explícitamente en el mismo componente; no genera repartos nuevos.

«Corregir importe original» abre el editor existente; los guards impiden retirar
o reducir el importe por debajo de lo abonado. No se crean movimientos al editarlo.
Si una anulación/eliminación de cuenta dejaría nueva deuda en una Completada, se
rechaza y se exige cambiar su estado explícitamente. Un pago total nuevo completa
la tarjeta en la misma transacción; no se reabre ni se marca por verificar. Los marcadores históricos
`stateAt/stateCount` se conservan en migración/backup; los pagos nuevos usan
`stateAt=null`. Las obligaciones compartidas conservan sus limitaciones anteriores.
Las monedas históricas se muestran en sus unidades reales y se bloquean para
nuevos pagos incompatibles; no hay conversión automática.

## Persistencia

Room 54→55 admite `contextAccountId=null` exclusivamente para DIRECT y parejas
opcionales de identificador/nombre del responsable y del pagador en asignaciones.
La reconstrucción ocurre en la transacción de Room: staging exacto, sustitución
de las dos tablas, restauración de FKs/índices/triggers y comparación por EXCEPT en
ambas direcciones, más `foreign_key_check`. Las autorreferencias se retiran solo
de la tabla temporalmente sustituida y se reponen desde la copia íntegra.
Los importes, IDs, fechas, estados, anulaciones y relaciones originales se conservan.
La prueba de fallo al final de la migración verifica rollback a 54 y las filas exactas.

JSON 58 expresa los nuevos nulls explícitamente; los lectores 1–57 conservan sus
restricciones históricas. Snapshot/restauración usan las mismas cuatro colecciones
y el mismo reemplazo transaccional. No cambian contenedor, cifrado, archivos, ni
versión de aplicación. No se ejecuta migración sobre datos reales durante desarrollo.

## Archivos de esta intervención

Rutas relativas a `app/src/main/java/com/r0ybt/arachn0de/`, salvo indicación:

- `bank/BankSchema.kt`, `bank/BankMigration54To55.kt`, `bank/BankState.kt`
- `bank/BankRepository.kt`, `bank/BankPaymentSelection.kt`, `bank/BankInvariants.kt`
- `bank/BankAccountDeletion.kt`, `bank/BankJson.kt`
- `data/local/Arachn0deDatabase.kt`, `backup/BackupJson.kt`
- `ui/BankPaymentEditor.kt`, `ui/BankUi.kt`, `ui/CardPaymentPanel.kt`
- `ui/CoreBottomModule.kt`, `ui/NodeBottomModule.kt`
- `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/55.json`
- Tests JVM: `bank/BankUnifiedPaymentTest.kt`, `bank/BankDirectMigrationTest.kt`,
  `bank/BankPaymentPanelTest.kt`, `bank/BankPaymentUiTest.kt`,
  `bank/BankIntegrationTest.kt`, `bank/BankHierarchyTest.kt`,
  `ui/NavigationWorkTest.kt` bajo `app/src/test/java/com/r0ybt/arachn0de/`
- Android: `app/src/androidTest/java/com/r0ybt/arachn0de/bank/NativeBankDirectMigrationTest.kt`
- `ARCHITECTURE.md`, `BACKUP_FORMAT.md`,
  `docs/BANK_DIRECT_WITHOUT_ACCOUNT_PROPOSAL.md`, este informe.

## Pruebas y límites

Pruebas ejecutadas con filtros `:app:testDebugUnitTest --offline --tests ...`;
no se ejecutaron suites generales. Se actualizaron expectativas incompatibles
con la autorización (reparto automático y completar al pagar). El fixture de
migración antigua ahora crea un esquema 52 real, en vez de rebajar artificialmente
una base nueva que ya tenía columnas posteriores.

Resultado: 25 métodos focalizados aprobados (sin contar reintentos).
Cobertura local: BankUnifiedPaymentTest, BankDirectMigrationTest,
BankPaymentPanelTest, BankIntegrationTest, BankAccountDeletionTest,
BankHierarchyTest; dos métodos nuevos de NavigationWorkTest y dos métodos de
BankPaymentUiTest. Los reintentos se limitaron a fallos o nueva evidencia.

`NativeBankDirectMigrationTest` compila mediante
`:app:compileDebugAndroidTestKotlin --offline` sin ensamblar APK. Prepara una base
SQLCipher 54 aislada, conserva historia con correcciones, abre mediante Room 55,
registra DIRECT idempotente sin cuenta y restaura su backup. Falta ejecutarla en
Android físico; ADB devuelve una lista de dispositivos vacía. También queda el
dogfooding de teclado, gestos y tamaños de pantalla en dispositivo.

Sin cambio de versión de app, APK, commit, publicación ni intervención de Ponytail.
