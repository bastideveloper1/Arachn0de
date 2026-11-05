# Bloque 2 — Iconos del menú principal

Alcance exclusivo: `AppIdentityDrawer` en `NavigationChrome.kt`, tanto en su
presentación normal como en panel experimental. No cambia las barras inferiores
ni los iconos internos de las pantallas de destino.

## Sustituciones

- Inicio: Núcleos, Borradores, Metro, Antisocial, Calendario, Tragaperras.
- Herramientas: Diagramas, Wireframe, Pomodoro.
- Organizar: Etiquetas, Pagos, Recurrencias, Estadísticas de Metro.
- Ajustes: Apariencia, Seguridad y respaldos, Acerca de.
- Categorías: Inicio, Herramientas, Organizar, Ajustes.
- Cierres del menú: superior e inferior normal y cierre del panel experimental.

21 SVG convertidos a `res/drawable/menu_*.xml`, con correspondencias en
`MainMenuIcons.kt`. Antisocial incluido conforme a la aclaración final del usuario.
«Estadísticas / Metro» corresponde a la entrada existente «Estadísticas de Metro».
No se crean entradas ni se cambia ningún nombre, destino, margen o área táctil.
Captura rápida conserva su icono porque no se recibió correspondencia para él.
No quedan funciones del bloque sin localizar.

## Verificación

- Comparación con originales: los 21 recursos conservan trazados, viewport de
  24 × 24 y grosor 1.75, sin escalado ni alteración de las proporciones.
- MainMenuIconsTest: correspondencias independientes del bloque 1 y renderizado
  de los 21 vectores en todos los temas oficiales a tamaños 18/24/28 y 240 px;
  tinte correcto y geometría dentro del viewport. La comprobación de bordes usa
  alta resolución para distinguir recorte de antialias a tamaño pequeño.
- MainMenuIconNavigationTest: callbacks originales de las 17 entradas, cierre
  antes de navegación, categorías exclusivas y accesibilidad con pantalla
  estrecha y fuente grande en temas oscuro y claro.

Pendiente: comprobación visual física en Android. No se genera APK ni se cambia
versión, formato persistido, preferencias o respaldos.
