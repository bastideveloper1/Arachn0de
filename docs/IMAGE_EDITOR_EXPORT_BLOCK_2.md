# Bloque 2: editor y exportación de imágenes

## Diagnóstico y corrección

Reproducción anterior a la corrección: diez imágenes cuadradas (una con `arNd`)
producen `IllegalArgumentException` por superar los 8192 px del renderer. Cada
imagen se apilaba a 976 px de ancho. El lote conserva los PNG completos anteriores
y el mensaje ocultaba la causa. Esto reproduce un mecanismo compatible con el
aviso de seis archivos; sin la tarjeta/log original no se atribuye a ese mecanismo
el incidente exacto. Las anotaciones no tienen una vía diferente en el renderer.

Se ajustan las imágenes proporcionalmente solo cuando la ficha no cabe, sin
recortarlas ni cambiar las fuentes. Permanece el límite técnico de alto; el texto
que por sí solo no cabe falla explícitamente. Se conserva la decodificación
secuencial y la liberación de bitmaps. El lote informa éxito, fallo o progreso
parcial con motivo, y reanuda los elementos pendientes en la misma sesión/carpeta.
Tras recrear el controlador o reiniciar, no hay continuación durable de completos.
El diario existente recupera parciales; un borrado denegado bloquea nuevos PNG.

El editor dibujaba además un rectángulo auxiliar de selección (cian en el código),
incluso en modo dibujo. Se suprime en ese modo y se comprueba que cada gesto
confirma una sola marca. No se ha reproducido el color rojo exacto del incidente.
La interfaz conserva cuatro herramientas, seis colores, tres grosores y Deshacer.
Cancelar no importa archivos. Guardar sustituye referencias con un PNG plano,
conservando leyendas y cifrado; no abre exportación ni añade una foto visible.
Los PNG editables históricos se abren con sus píxeles ya anotados, sin redibujar
sus objetos encima. Sus lectores y backups históricos se conservan.

La previsualización no se usa como fuente del guardado: este conserva dimensiones
y orientación originales hasta 12 MP y el límite existente de 20 MiB por adjunto.
Fuentes mayores requieren otra estrategia de renderizado; se rechazan conservando
la imagen anterior. Los borradores exteriores retienen su original hasta commit
o cancelación. Referencias compartidas y reservas protegen la eliminación física.

## Componentes y validación

Producción: `CardShareRenderer`, `CardTreeImages`, `CardTreeImageCommands`,
`ImageAnnotationEditor`, `ImageAnnotationStage`, `AnnotationImage`,
`AttachmentRepository`, `AttachmentReferences`, `AttachmentDescriptionEditor`.
Pruebas: `CardShareFilesTest`, `CardTreeImagesTest`, `ImageAnnotationsTest`,
`ImageAnnotationUiTest`.

20 comprobaciones focalizadas aprobadas entre ejecuciones: reproducción corregida,
renderers existentes, anotaciones históricas, seis PNG completos más fallo/reintento,
limpieza denegada, cancelación, herramientas/contorno, reemplazo cifrado, rollback,
reserva compartida, recuperación y backup/restauración con bytes exactos. El primer
intento de la prueba nueva de backup usaba una contraseña demasiado corta; se
corrigió la fixture y pasó. Compilación Kotlin y `git diff --check` correctos.
No se ejecutó la suite global ni se generaron APK, commits o publicaciones.
Pendiente: la tarjeta real del incidente y una comprobación en teléfono/proveedor SAF.
