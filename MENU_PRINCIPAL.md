# Menú principal: inventario y reorganización

Inventario anterior (cada destino visible solo si su callback existe):
- Principales: Núcleos → onProjects, Buscar → onSearch, Antisocial → onPeople,
  Calendario → onCalendar, Captura rápida → onQuickCapture.
- Seguimiento: Borradores → onDrafts, Favoritos → onFavorites,
  Recurrencias → onRecurrences, Avisos → onAttention, Obligaciones → onObligations.
- Herramientas: Diagramas → onDiagrams, Wireframe → onWireframe,
  Pomodoro → onPomodoro, Metro → onMetro, Juego → onGame.
- Recursos: Tecnologías → onTechnologies, Etiquetas → onTags.
- Personalización: Apariencia → onAppearance.
- Ajustes: Seguridad y respaldos → onSecurity, o onBackups si falta Seguridad.
- Información: Acerca de → onAbout, versión actual (informativa).
- onMetroStatistics estaba conectado al destino real pero no se mostraba.

Distribución nueva:
- Inicio: Núcleos, Borradores, Favoritos, Atención (antes Avisos), Metro,
  Buscar, Antisocial y Calendario.
- Herramientas: Diagramas, Wireframe, Pomodoro, Captura rápida y Juego.
- Organizar: Tecnologías, Etiquetas, Obligaciones, Recurrencias y Estadísticas de Metro.
- Ajustes: Apariencia, Seguridad y respaldos, Acerca de y versión.

No se cambian callbacks, destinos, selección de Núcleos/Antisocial ni condiciones.
La captura rápida sigue oculta cuando el llamador no la permite. Seguridad mantiene
el fallback original; no se duplica el destino combinado. Navegar cierra primero.
Inicio se selecciona al abrir; solo una categoría activa, restaurable en memoria
privada como antes. El panel no sustituye ni recompone la navegación subyacente.
ThemeDrawerIdentity mantiene imágenes drawer, cabecera histórica, nombre, logo,
encuadre y recursos del tema. Es hermana de la lista central y no cambia con ella.
Solo en pantallas bajas se reduce su contenedor de 88 a 64 dp, sin deformar recursos.
Las categorías permanecen fuera del scroll; a ancho reducido/fuente grande usan
2×2 posiciones, con controles de al menos 48 dp. Sin nuevos datos persistentes.
