# Bloque 3 — Contenido y edición

## Alcance

Se convierten los 14 SVG a `res/drawable/editing_*.xml`, con sus trazados originales,
viewport 24 × 24, grosor y rellenos originales. `ContentEditingIcons.kt` contiene
las correspondencias. No cambia el texto, modelo de datos ni comportamiento de
ningún control.

`ContextActionRows` / `ContextActionMenu` admiten una opción de presentación
`cardEditingIcons`, desactivada por defecto. Solo se activa en los paneles de
Contenido/Opciones de `CoreBottomModule` y en las opciones del elemento abierto
de `ProjectScreen`. Los menús exteriores de las tarjetas mantienen sus iconos.
No se modifica LucideIcon ni PropertyIdentityIcon globalmente.

## Correspondencias localizadas

- Configuración de estados: «Estados», distinto de la acción «Cambiar estado».
- Título, Descripción, Planificación, Esfuerzo y Responsables: propiedades de
  Contenido, cuando el tipo de tarjeta las admite.
- Fotografía: control existente de contenido del Núcleo.
- Valores iniciales: Núcleos y Capas que admiten esta configuración.
- Editar: opción existente del elemento abierto en contextos especializados
  (por ejemplo, Kanban), sin añadir nuevos controles.
- Favorito off/on: «Marcar favorito» / «Quitar de Favoritos» en los controles
  interiores; conserva el estado confirmado y el callback existentes.
- Prioridad off/on: «Común» / «Prioritario» en Contenido. Los formularios de
  edición de tarjeta/Núcleo reutilizan los SVG de Responsables y de Prioridad
  activada en los lugares que ya mostraban esos iconos. No se añade un icono
  al estado off del chip que antes no tenía icono.
- Recurrencia off: «Sin recurrencia». `onRecurrence` procede de una regla
  existente en `recurrenceByNode`; «Regla de recurrencia» conserva su icono
  anterior. El acceso general «Recurrencias» también permanece intacto.

No quedan SVG entregados sin correspondencia. El SVG de Recurrencia activada
no se ha recibido y no se sustituye. Los indicadores exteriores de favorito,
prioridad, esfuerzo, título/descripción y recurrencia permanecen intactos.
Los bloques 1 y 2 no se modifican.

## Verificaciones focalizadas

- Comparación literal de los 14 trazados y viewports con los SVG de origen.
- `ContentEditingIconsTest`: estados on/off, ausencia de sustitución para
  recurrencia activa y funciones ajenas, recursos de los bloques anteriores;
  renderizado a tamaños 18/24/28 y 240 px en todos los temas oficiales.
- `ContentEditingControlsTest`: callbacks de los controles, cambios consecutivos
  de Favorito/Prioridad, conservación al alternar Contenido/Opciones, recurrencia
  deshabilitada/activa y altura táctil mínima existente.

Resultado: tres pruebas focalizadas aprobadas, sin fallos.

Pendiente: comprobación visual física en Android. No se genera APK ni se cambia
versión. No se modifica el editor de descripciones ni su prototipo aislado.
