# Banco: DIRECT sin cuenta — propuesta histórica sustituida

La autorización integral posterior y sus dos aclaraciones permiten la implementación
descrita en [BANK_PAYMENT_IMPLEMENTATION.md](BANK_PAYMENT_IMPLEMENTATION.md).
El texto siguiente conserva la propuesta anterior como antecedente; sus pendientes,
versiones y alcance no describen el estado actual del código.

Este documento no activa funciones ni autoriza migraciones. La Fase A solo cambia
etiquetas de Banco. CLP, la unificación del campo Pago de tarjetas y los estados
de las tareas quedan fuera de esta intervención.

## Alcance mínimo de esquema

La entrada solicitada es un Núcleo Banco existente: `rootId` puede seguir siendo
obligatorio y conservar su FK a `bank_roots`. No se necesita un Núcleo ficticio
ni hacer opcional esa columna para este flujo.

| Tabla | Campo | Cambio propuesto |
| --- | --- | --- |
| `bank_operations` | `contextAccountId` | Nullable únicamente para DIRECT sin cuenta. |
| `bank_allocations` | `sourcePersonId`, `sourcePersonName` | Nullable como pareja cuando el pagador DIRECT no está identificado. |

`bank_operations.sourcePersonId` ya es nullable. Su validación permitiría null
solo en DIRECT; el responsable de la obligación (`obligationPersonId` y los campos
`personId/personName` de la asignación) conserva las reglas actuales. No se amplía
el pago de obligaciones compartidas o sin responsable: ese alcance sigue separado.

Invariantes: sin cuenta de contexto implica DIRECT, sin origen ni destino bancario;
las demás operaciones requieren sus cuentas reales dentro del mismo Núcleo.
Un pagador no identificado se guarda como null, nunca como ID o persona ficticia.
Si se elige una persona, debe existir y se guarda su nombre histórico. Los pagos
desde cuenta derivan el pagador de su titular real. Las sumas de asignaciones,
saldo suficiente, pendiente no negativo y restricciones CLP se conservan.

## Migración propuesta

Partir del schema Room vigente (54 al preparar esta propuesta) y añadir una
migración explícita a la siguiente versión de esquema, sin cambiar la versión
de la aplicación. SQLite requiere reconstruir técnicamente ambas tablas para
relajar sus NOT NULL; no es una recreación de operaciones de negocio.

Copiar exactamente cada columna de cada fila histórica, conservar PKs, FKs,
índices, triggers, importes, atribuciones, fechas, anulaciones y correcciones.
No sustituir cuentas históricas por null. Ejecutar dentro de la transacción de
migración, comprobando igualdad completa de filas y `foreign_key_check` antes de
darla por válida. No desactivar integridad ni usar migración destructiva.
La secuencia de reconstrucción debe probar las FKs inmediatas y autorreferentes
tanto en SQLite/Room como en SQLCipher antes de aprobarla.

## Historial y eliminación

Reutilizar la lista y el detalle de operaciones existentes. En Banco principal,
mostrar los DIRECT de ese `rootId`, incluidos los históricos, como pagos directos
sin signo de cargo a una cuenta. Dentro de cuentas, mostrar los movimientos con
origen/destino real y excluir DIRECT de esa presentación. Es un cambio de consulta
visual: conservar sus antiguas cuentas de contexto almacenadas.

Los DIRECT nuevos sin cuenta no deben incluirse en la eliminación de ninguna
cuenta. Los DIRECT históricos mantienen sus dependencias reales: cualquier
eliminación mediante el mecanismo existente debe seguir mostrando y confirmando
explícitamente las operaciones afectadas. No desvincularlos ni borrarlos como
parte de la migración o de la nueva navegación.

## Escritura y navegación tras autorización

Reutilizar `BankPaymentEditor`, `BankPanel`, `BankChoice`, la previsualización y
`confirmPayment`. Mantener UUID por intento, confirmación idempotente, comparación
con snapshot vigente y transacción de operación/asignaciones.

- Banco principal → Pagar: DIRECT, sin cuenta, pagador real opcional sin selección
  predeterminada. La selección del responsable de las obligaciones no atribuye
  automáticamente la identidad del pagador.
- Cuenta → Operar → Pagar: cuenta y titular fijados por el contexto, sin volver a
  pedir cuenta ni modalidad. Mostrar nombre y saldo de esa cuenta.
- Retirar el selector de modalidad solo al integrar ambos caminos. Las
  correcciones de operaciones históricas deben conservar su tipo y referencias;
  el lugar desde donde se abren no debe convertir un DIRECT antiguo en PAYMENT.

La confirmación actual de Banco puede completar una tarea o marcarla por verificar.
No se modifica esa lógica en la Fase A. Antes de activar el nuevo camino debe
aclararse si «no modificar estados» significa conservar ese comportamiento o
impedir también sus transiciones automáticas; esto último sería otro cambio.

## Backup y validación focalizada pendiente

Versionar el JSON para expresar nuevos nulls (lector vigente 1–57); mantener
lectores históricos estrictos y el contenedor cifrado. Versionar únicamente el
formato de datos necesario, sin cambiar la versión de la aplicación.
Snapshot, exportación y restauración incluyen todas las operaciones y asignaciones,
también DIRECT sin cuenta. Validar antes de reemplazar y conservar el estado
anterior ante fallos.

Comprobar migración con igualdad completa del historial, saldos y pendientes;
restauración de backups anteriores a Banco y 49–57; round-trip de DIRECT con y sin
pagador; referencias inválidas; cuenta eliminada o saldo cambiado antes de confirmar;
confirmaciones duplicadas; rollback al fallar una asignación; separación del
historial; y exclusión de DIRECT sin cuenta al eliminar una cuenta. No ejecutar
suites generales. Ninguna de estas pruebas de la futura migración se ha ejecutado
ni puede darse por aprobada con las pruebas de etiquetas de la Fase A.
