## Ajuste posterior a v0.22.0

La minibarra de las vistas previas es exclusivamente informativa. Tocar su área
abre la Tarea mediante la acción normal de la tarjeta. Se retira el acceso de
edición desde las acciones de la vista previa; el selector se conserva dentro
de la Tarea (NodeBottomModule). Niveles, colores, dimensiones y persistencia
permanecen iguales. La descripción siguiente corresponde al cierre original.

# Lote 5B — punto de cierre

Implementación terminada: esfuerzo manual 0–5 por Tarea, minibarra bajo el título,
selector inferior desde la barra o las acciones, guardado inmediato y retorno a 0
al tocar el nivel seleccionado. Colores 1–3 del tema y 4–5 naranja/rojo.
Ordenación EFFORT integrada en el sistema existente, ambas direcciones con 0 al
final, completadas al final, precedencia, herencia y aplicación explícita.

Persistencia: Room 54 (migración 53→54), JSON hasta 57, lectura histórica con
esfuerzo 0; conservación en movimientos, conversiones y restauración.

Verificación ya ejecutada: compilación correcta y pruebas focalizadas de niveles,
selector, reapertura, ordenación, herencia/Aplicar, migración, backup/restauración,
conversiones y rechazo de datos inválidos. Último registro:
/tmp/arachnode-effort-mixed-final.log: BUILD SUCCESSFUL (2 min 28 s).
No repetir estos casos sin cambios o fallos nuevos.

Pendiente exclusivamente validación física: legibilidad/contraste en temas claros,
oscuros, Black & White 90 y skins; tocar minibarra frente a abrir tarjeta,
desplazar y mantener pulsado/arrastrar; cierre normal del panel.
No hay teléfono conectado en la comprobación de cierre; solo emulator-5554.
No se acredita esta revisión física mediante pruebas JVM.

No se ha generado una nueva APK que contenga estos lotes. La oficial
Arachn0de-v0.21.0-code36.apk es anterior a ellos.
No se necesita continuar una parte conocida de implementación del Lote 5B;
si la revisión física descubre un fallo, corregir únicamente ese fallo.
