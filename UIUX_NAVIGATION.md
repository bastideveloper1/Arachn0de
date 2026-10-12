# Sprint UI/UX 1 — navegación global

## Implementado

- Menú lateral compacto: identidad pequeña, Buscar y Favoritos al principio; organización y recursos (Proyectos, Atención, Calendario, Obligaciones, Personas, Tecnologías, Etiquetas, Metro); aplicación (Seguridad, Respaldos, Captura rápida cuando no está disponible en la barra contextual, Apariencia, Acerca de y Juego). Todos los destinos anteriores permanecen accesibles. Los siete PNG oficiales no se modificaron; sus SHA-256 se verificaron antes y después.
- Búsqueda a pantalla completa, con título/descripción, resultados por identidad y ubicación, Favoritos, Recientes y Frecuentes. Conserva el mismo índice de Proyectos, Capas, Tareas y Notas; no amplía los tipos buscables. No queda el panel ni la barra superior antigua de Buscar/Favoritos/+Tarea.
- Consulta, filtro y posición de búsqueda sobreviven al regreso desde resultados y a la restauración de estado de la pantalla. La lista espera a que el índice y los resultados estén disponibles para evitar que una lista vacía transitoria reinicie el desplazamiento.
- El resultado abre `ProjectNodeScreen` con los identificadores reales y los mismos repositorios. Usa un estado de pantalla independiente del origen; no crea copias de Proyectos o elementos. Atrás y la flecha vuelven a la búsqueda; cerrar la búsqueda devuelve al contexto anterior, incluida la capa o ficha que estaba abierta. Buscar desde Respaldos también regresa a Respaldos.
- La pantalla de origen permanece compuesta, con sus estados, pero sus manejadores de Atrás se suspenden mientras está cubierta. `NavigationContent` encamina el retroceso únicamente al contenido activo y conserva el comportamiento anterior cuando no hay una superposición.
- Cabeceras existentes: menú y bloqueo se incorporan en su propia fila; Ordenar, Filtros y Opciones del proyecto/elemento pasan a la cabecera del proyecto. No se agrega una segunda barra permanente. Metro mantiene menú y bloqueo también en carga/error y mapa ampliado. En Juego solo se conecta el acceso global a su cabecera; no se cambian reglas, sesiones ni controles del juego.
- Seguridad se abre desde el menú mediante el mismo diálogo y callback de la sesión. Su antiguo bloque superior desaparece en la aplicación, conservando bloqueo inmediato en cabeceras o en la barra inferior. Contraseñas, autorizaciones de capturas, tiempos de bloqueo y cierre de bóvedas mantienen sus operaciones existentes.
- Respaldos aloja `BackupSection` en una pantalla propia, conservando exportación, restauración y recuperación. El encabezado deshabilita salir o abrir el menú durante operaciones en curso. Acerca de conserva actualizaciones y enlaces; el acceso antiguo a backup permanece para llamadas independientes al componente sin navegación global.
- Barra inferior sin cambios: Bloquear → Nuevo elemento → Captura rápida → Capas de cebolla. No se modifica `OfficialIcons.kt`, los PNG, la lógica de captura ni el navegador de capas.

## Compatibilidad y alcance

Solo cambios de navegación y presentación. No hay tablas, migraciones, preferencias nuevas, cambios de formato de respaldo o versión. Los nuevos estados son de interfaz, mediante `rememberSaveable` y los mecanismos existentes de estado de pantalla. No se cambia la identidad de los elementos, relaciones, seguridad criptográfica, reglas de Personas, Proyecto, Gestión, Sprint, Backlog o Metro. No se implementa un nuevo sistema semántico de indicadores de tarjetas.

## Archivos

- `ui/AppRoot.kt`, `ui/GlobalNavigation.kt`, `ui/NavigationChrome.kt`, `ui/ElementAccessUi.kt`: navegación, búsqueda, menú y retroceso.
- `MainActivity.kt`, `security/SecurityUi.kt`: reubicación del acceso al diálogo de seguridad, con sus callbacks existentes.
- `ui/ProjectScreen.kt`: consolidación de controles de cabecera y retorno de búsqueda.
- Cabeceras de PeopleScreen, PersonDetailUi, TechnologiesScreen, AttentionScreen, CalendarScreen, ObligationsScreen, AppearanceScreen y AboutScreen.
- `ui/BackupSection.kt`: exposición de su estado ocupado al contenedor visual, sin cambiar operaciones o formato.
- Cabeceras de `metro/MetroScreen.kt` y `game/RaceScreen.kt`: únicamente accesos de navegación global.
- Prueba nueva `GlobalNavigationUiTest.kt`; pruebas existentes adaptadas a las ubicaciones actuales, semántica combinada de tarjetas y accesos existentes.

## Validación

58 pruebas dirigidas únicas aprobadas en ejecuciones por grupos y repeticiones de los casos corregidos:

| Clase | Casos |
| --- | ---: |
| GlobalNavigationUiTest | 9 |
| ElementAccessUiTest | 4 |
| OfficialIconsUiTest | 4 |
| DogfoodingChromeMoveTest | 3 |
| VisualActionsNavigationTest | 7 |
| ManagementUiTest | 1 |
| AttentionUiTest | 3 |
| DogfoodingPresentationTest | 5 |
| NodeSortUiTest | 10 |
| RecoveryUxTest | 4 |
| ScreenCaptureUiTest | 3 |
| MetroNativeUiTest | 5 |

Incluyen retroceso físico y visual, contexto de origen, restauración de consulta/filtro/desplazamiento, ausencia de barras duplicadas, diálogo real de Seguridad, destinos, creación, copia, ordenación, filtros, navegación de capas y funcionamiento de captura/Metro. Pruebas de interfaz mediante Robolectric; no instalación en teléfono. `git diff --check` y hashes de los siete PNG sin diferencias. Compilación final `:app:assembleDebug` aprobada en 43 segundos.

Pendiente manual: revisión visual en teléfono de altura útil, teclado, contraste y ergonomía. Sin pendientes funcionales conocidos de este bloque; búsqueda de Personas/Tecnologías y nuevos indicadores quedan fuera del alcance. No se genera APK oficial, commit ni push.
