# Integración de los siete PNG oficiales

Recursos en `app/src/main/res/drawable-nodpi/official_*.png`, copiados sin modificación desde los siete originales. La fuente real de Proyecto se llama `Proyecto.png`. Las copias se comprobaron byte por byte. No se recortaron los márgenes transparentes, especialmente apreciables en Personas, Bloquear y Captura rápida.

| Recurso | Ubicación |
| --- | --- |
| official_personas | Menú lateral: Personas |
| official_proyecto | Menú lateral: Proyectos; identidad de proyectos sin fotografía propia |
| official_metro | Menú lateral: Metro de Santiago |
| official_gestion_proyecto | Acceso contextual existente «Gestión del proyecto», que permite consultar Sprint/iteraciones y Backlog |
| official_bloquear | Barra inferior de un proyecto; bloqueo superior en las demás vistas |
| official_nuevo | Botón «Nuevo elemento» de la barra inferior del proyecto |
| official_rapido | Captura rápida inferior del proyecto; menú lateral cuando no hay barra contextual |

La barra del proyecto mantiene el orden Bloquear / Nuevo elemento / Captura rápida / Capas de cebolla. El último recurso y su acción se conservan. El bloqueo utiliza el mismo callback que revoca la autorización de capturas y cierra la sesión; su habilitación sigue el estado de las operaciones de seguridad. La captura abre el diálogo existente y conserva sus reglas. Se ocultan los accesos superiores duplicados cuando está disponible la barra contextual.

`OfficialIcon` usa Image sin tintes ni filtros y ContentScale.Fit. Decodifica los originales con inSampleSize según el tamaño físico de presentación, evitando mantener los PNG de más de 1200 px completos para iconos pequeños. Cada composición conserva únicamente su bitmap escalado durante su vida. No agrega fondos ni altera colores, transparencia o proporciones.

No hay cambios de esquema, respaldo, versión, reglas de dominio ni recursos del juego. La reorganización del menú, búsqueda y cabeceras del Sprint UI/UX 1 se completó posteriormente; véase `UIUX_NAVIGATION.md`. Esta integración no crea un destino global adicional de Gestión.

La comprobación de geometría cubre 360 dp y los cinco temas. La revisión visual en teléfono de escala, brillo y contraste sigue siendo manual; no se ha instalado ninguna APK en el teléfono.

## Archivos de esta integración

- `ui/OfficialIcons.kt`: presentación escalada y acciones de bloqueo/captura existentes.
- `ui/NavigationChrome.kt`, `ui/ProjectPhotoComponents.kt`, `ui/ManagementSection.kt`: destinos e identidades.
- `ui/ProjectScreen.kt`, `ui/ElementAccessUi.kt`, `ui/AppRoot.kt`, `MainActivity.kt`, `security/SecurityUi.kt`: colocación contextual de acciones, sin duplicación del bloqueo ni de captura rápida.
- Siete recursos `drawable-nodpi/official_*.png`.
- `OfficialIconsUiTest.kt`: cuatro pruebas nuevas. `ManagementUiTest.kt` y `ElementAccessUiTest.kt`: sincronización del guardado y cierre seguro del entorno de pruebas.

Validación dirigida: 16 pruebas aprobadas (OfficialIconsUiTest, ElementAccessUiTest, ManagementUiTest y VisualActionsNavigationTest). `git diff --check` sin errores.
Compilación final `:app:assembleDebug`: aprobada, 59 segundos. No se generó ni firmó una APK oficial.
