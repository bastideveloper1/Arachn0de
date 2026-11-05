# Bloque 4: iconografía, colores y estados

Ajustes posteriores de insignia, cuadrícula y cinco colores de dificultad: [estado actual](POST_BLOCK_4_VISUAL_ADJUSTMENTS.md).

## Implementación

1. Dificultad: `TaskEffortMini` muestra una flama real de 16 dp y el nivel 1–5 dentro de `CompactCardIndicators`, en lugar de la fila de segmentos bajo el título. Cero conserva la flama inactiva, sin número. La flama es distinta de la bandera de prioridad. Se conservan `Node.effort`, ordenación, selector y guardado existentes; los controles de edición siguen permitiendo elegir o retirar un nivel.
2. Prioridad: se elimina el texto redundante de las tarjetas; se mantiene la bandera y los controles existentes. Los lugares que ya muestran metadatos no agregan otra bandera. `PriorityIndicator`, donde sigue siendo necesario, usa el color configurado o la prioridad de la skin, con valor monocromático por defecto. No cambian reglas de prioridad.
3. Por verificar: el selector de Apariencia ofrece una entrada independiente `verify`. Se aplica al ojo de la fila de metadatos, al indicador propio de la Tarea y a los iconos de verificación compartidos con el interior. El modo de dos estados no gana nuevos indicadores ni estados.
4. Identidad: título, descripción, Núcleo y Capa usan tinte del rol `icons` del tema. No se sobrescriben PNG, fotografías ni recursos de skins; logos y otros recursos oficiales no reciben este cambio de tinte.
5. Cumpleaños: la entrada `birthday` del mismo selector colorea la torta contextual, las filas de cumpleaños y sus marcadores en Calendario. No cambian fechas, fuentes de cumpleaños ni selección de días.
6. Ver urgentes/Atención: se elimina el círculo de selección y su borde. Solo cambia la intensidad del glifo de 24 dp (atenuado frente a encendido), conservando el control de 48 × 48 dp y su booleano de filtro. No se añade fondo ni geometría decorativa.

Los demás metadatos mantienen sus espacios activos/inactivos y su orden relativo. El resumen de contenedores y los conteos no cambian. No hay cambios de navegación, menú global, Banco, cálculos de Pagos ni herramientas especializadas.

## Componentes afectados

`TaskEffort`, `CompactCardIndicators`, `NodeComponents`, `PriorityUi`, `ProjectScreen` y `LucideIcons` integran la presentación de dificultad/prioridad. `PropertyIdentityIcon`, `OfficialIcons` y `LayerNavigator` aplican los tintes de identidad. `AppearanceScreen` y la lista admitida en `AppearancePreferences` incorporan las dos elecciones. `TaskStateControls` y `TaskViewSelector` consumen el color de verificación; `ContextCalendar`, `CalendarSpecialUi` y `CalendarScreen` consumen cumpleaños y el tratamiento de urgentes.

## Preferencias y compatibilidad

Las dos elecciones nuevas usan las claves opcionales existentes `indicator_color_verify` e `indicator_color_birthday` del namespace `appearance_preferences`. Solo se amplía la lista admitida por la personalización y su validación común; no se cambia Room, cifrado, codec, formato de backup ni ruta de restauración. Se exportan como las demás preferencias, se restauran en la misma transacción y se eliminan con Restablecer colores. Sin esas claves se usan los valores visuales por defecto. Lectores antiguos que no admitan estos identificadores los rechazan explícitamente en lugar de importarlos parcialmente.

## Validación dirigida

- Selector real de colores de ojo y cumpleaños, recarga de elecciones independientes y restablecimiento sin eliminar el tamaño de fuente.
- Restauración de ambas elecciones por el backup existente, conservación del estado previo ante fallo transaccional y respaldo histórico sin preferencias.
- Dificultad 1–5 mediante el selector original, guardado, retirada del nivel, reapertura del editor y ordenación existente.
- Icono de urgentes encendido/apagado con tema Graphite, tamaño del glifo, objetivo táctil y booleano del filtro sin cambios de geometría.
- Píxeles de los iconos originales: monocromáticos por defecto y colores de iconos/prioridad de skin respetados.
- Tarjeta real con dificultad en la fila de metadatos, sin barra ni texto redundante de prioridad, con fuente 1,8.

Doce pruebas dirigidas distintas pasaron. Se reutilizaron IndicatorColorsTest (tres métodos), TaskEffortUiTest, ContextCalendarUiTest (un método), PrivatePreferencesBackupTest (un método) y CardCompactionBlockThreeTest (cuatro métodos); IconographyBlockFourTest añade dos comprobaciones visuales focalizadas. La regresión de tarjetas mantiene resumen compacto, metadatos, estado propio Por verificar y compatibilidad con skins. Las pruebas visuales son automatizadas con Robolectric, sin revisión física en Android. No se genera ni firma un APK oficial.
