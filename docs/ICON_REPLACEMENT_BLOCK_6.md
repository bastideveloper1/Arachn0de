# Bloque 6 — Convertir en

Cuatro SVG convertidos a vectores Android:

| Control existente | Recurso |
| --- | --- |
| Convertir en... | conversion_convertir_en |
| Convertir en nota | conversion_nota |
| Convertir en tarea | conversion_tarea |
| Convertir en capa | conversion_capa |

Se asignan por etiqueta exacta en `contextActionIconResource`, dentro de
`ActionComponents.kt`. Se comparten en los menús exteriores de tarjetas y en las
opciones del elemento abierto, sin cambiar sus destinos o condiciones.
No se modifican iconos de identidad, creación ni indicadores de tarjetas.

Convertir en proyecto y Convertir en Borrador conservan su presentación anterior:
no se recibieron SVG para esas opciones. No se implementa ni anticipa el bloque 5.
Los bloques previos permanecen intactos.

Verificación focalizada:

- Comparación literal de geometría, viewport 24 × 24 y grosor 1.75 con los SVG.
- `ConversionIconsTest`: renderizado de los cuatro recursos a 18, 24, 28 y
  240 px en todos los temas oficiales, tinte y ausencia de recorte geométrico.
- `CardPresentationAdjustmentsTest.actionIconsAreDistinctAndSharedAcrossConversionLabels`:
  actualizada la expectativa anterior de un único icono para todas las
  conversiones; conserva las expectativas para proyecto y Borrador.
- `ConversionMenuIconsTest`: abrir submenú, callbacks de destinos, estado
  deshabilitado, acceso a Borrador, regreso y áreas táctiles.
  Usa callbacks de fixture, sin convertir datos reales.

Resultado: tres pruebas focalizadas aprobadas, sin fallos.

Pendiente: revisión visual física en Android. No cambia lógica, navegación,
almacenamiento, versión ni genera APK.
