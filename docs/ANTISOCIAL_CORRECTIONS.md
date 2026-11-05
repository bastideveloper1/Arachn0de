# Antisocial — banner y favoritos

## Causas

PersonBannerEditor se componía antes del contenedor principal, como otro hijo
de la misma Surface: ambos se superponían. Además, PersonProfileModule seguía
visible al editar el banner. Su acceso solo estaba en Opciones.

La estrella de los contactos era un Icon sin acción. La sección Favoritos era
un menú de acciones Marcar/Quitar sobre todos los contactos, no una consulta
filtrada de favoritos.

## Corrección

Banner directo junto a Foto/Opciones; accesos secundarios conservados. El editor
ocupa el mismo espacio inferior, excluyendo el panel de perfil mientras está
abierto. Se conserva PersonBannerSurface con encuadre 3:1, preview y repositorio
cifrado existentes. Atrás cancela mediante discardDraft, igual que Cancelar;
las reservas durables siguen su ciclo original durante recreaciones. No se
modifica el avatar ni se guardan cambios por cancelar el selector de Android.

PersonFavoriteButton comparte el registro PersonFavorites existente en tarjetas,
contactos y cabecera individual. Respuesta visual optimista, bloqueo de doble
pulsación y restitución ante error. Estrella rellena/contorno en tonos del tema.
Favoritos filtra las mismas tarjetas y no presenta acciones Marcar/Quitar.
Los observadores existentes reflejan las escrituras cifradas sin duplicar datos.

Sin modelos, migraciones, formatos de backup, versionado ni dependencias nuevos.
La inyección opcional del mismo AttachmentRepository en el editor permite usar
la infraestructura JVM existente, sin un almacén alternativo en producción.

Tres casos focalizados aprobados: accesos y distribución del banner; cancelación
del selector, reemplazo/guardado/reapertura sin cambiar avatar; favoritos desde
contactos y coherencia con el listado filtrado y el registro persistente.
Revisión visual en teléfono y teclado real pendiente. Sin APK ni firma.
