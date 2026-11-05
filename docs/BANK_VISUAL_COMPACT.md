# Banco: compactación visual

Intervención de presentación del 24 de octubre de 2026. No modifica operaciones, cálculos, estados, recurrencias, responsables, persistencia, Room, migraciones, respaldos, cifrado ni CMR. No genera APK ni cambia versión.

## Cambios y componentes reutilizados

- `ui/AppRoot.kt`, `ui/BankUi.kt`: identifican los Núcleos Banco mediante el observador existente de raíces bancarias y una composición local, sin escribir datos.
- `ui/ProjectComponents.kt`: los Núcleos Banco conservan `GeneralCard`, identidad, skin, favorito, opciones y navegación; omiten indicadores y la altura mínima del encabezado habitual. Los Núcleos normales mantienen su distribución.
- `ui/BankUi.kt`: `BankAccountCard` combina cuenta y titular, hasta dos líneas según contenido; omite paréntesis si falta titular y mantiene saldo destacado. `BankPanel` permite usar la variante compacta del panel modular existente sin cambiar sus acciones inferiores.
- `ui/BankPaymentEditor.kt`: agrupa etiquetas y controles con separaciones compactas. `BankDebtCard` conserva el modelo, IDs, selección, reparto y skin; oculta UUID/ruta y muestra pendiente y asignación editable. La confirmación conserva las cantidades asignadas.
- `ui/NodeComponents.kt`: variante de `NodeCard` exclusiva del selector bancario, con checkbox de 48 dp junto al título y pie compacto. Usa el mismo `GeneralCard`, imagen, degradado, protección del contenido, colores y bordes. La distribución habitual no cambia fuera de este contexto.
- `ui/ObligationFields.kt`: el resumen exterior de abonos parciales muestra únicamente el pendiente. Sin abonos conserva el monto original; un pago completo no muestra una deuda pendiente.
- `ui/ProjectScreen.kt`: mantiene explícitamente el resumen completo dentro del detalle existente, con monto original, abonos y resto; el historial financiero permanece intacto.

## Pruebas

- `bank/BankVisualCompactTest.kt`: indicadores Banco/normal, titular vacío y nombres largos, alturas con skins, checkbox y título, UUID/ruta ocultos, tareas homónimas independientes y resumen exterior frente al detalle completo.
- `bank/BankPaymentUiTest.kt`: se adaptaron únicamente selectores de etiquetas al nombre combinado de cuenta y al campo «Asignado». Dos pruebas aprobadas: validación inmediata, selección/reparto manual, cancelación y pago desde saldo; pago directo sin cuenta y cancelación final sin escrituras.
- `bank/BankUiTest.kt`: mantenimiento de selectores para el nuevo nombre combinado; no se repite su suite de operaciones no modificadas.

Resultado: compilación de aplicación y tests correcta; cinco pruebas focalizadas aprobadas (tres visuales y dos flujos de pago). Los primeros intentos requirieron corregir una aserción no disponible en Compose y el orden de calificadores de Robolectric, únicamente en el test nuevo. La última ejecución de las tres pruebas visuales terminó con `BUILD SUCCESSFUL`; los dos flujos de pago ya aprobados no se repitieron. `git diff --check` correcto.

Se verificaron hashes de los archivos de producción contra el inicio de esta intervención: solo cambiaron los siete archivos de UI enumerados. El código validado coincide con la compilación; backend financiero, CMR, Room, manifest, configuración de versión y formato de respaldos se conservaron.

## Revisión pendiente en teléfono

Comprobar la imagen y el degradado de las skins reales, títulos de dos líneas, tamaño de fuente ampliado y desplazamiento con teclado abierto. La verificación automatizada de distribución no acredita una revisión visual en el dispositivo.
