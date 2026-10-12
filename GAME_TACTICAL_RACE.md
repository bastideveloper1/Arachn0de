# Carrera táctica de Arachn0de

La carrera sustituye la batalla anterior. El catálogo jugable contiene exclusivamente los diez retratos suministrados; los veinte PNG se copian sin alteración. Se conserva productividad, Metro, SQLCipher, claves oficiales y versión 0.3.0 / 10.

## Comportamiento

- De 2 a 10 corredores, humanos locales o bots; los puestos vacantes se completan con personajes distintos y bots. Tres vueltas iniciales, configurables de 1 a 9. Primera meta completada gana.
- Circuito conectado con salida/meta y flechas; dos bifurcaciones, ruta directa o desvío con dos pasos más y caja. Clasificación por vuelta y progreso normalizado, sin ventaja artificial del desvío.
- Cajas aleatorias y garantizadas estables, recogida al pasar una vez por corredor/vuelta, premio persistido antes de la ruleta, dos espacios y decisión de reemplazo/descarte. Objetos nuevos disponibles en el siguiente turno.
- Objeto opcional antes del dado, máximo uno, coste −1 con mínimo cero. Cancelación o selección inválida conserva el objeto. Segunda oportunidad consume el objeto incluso al conservar el primer dado.
- Roca por rama seleccionada hasta el primer rival, incluidos invisibles, con contador independiente para rotar impactos en casillas compartidas. Avispas dirigidas solo a visibles. Trampas solo al aterrizar, dueño inmune, tres rondas; dinamita con zona ±2 fijada y resolución al comenzar el próximo turno propio, incluso omitido.
- Ojo con zona ±3 fijada, detección privada y rayo opcional al próximo comienzo; ocupa el uso de objeto de ese turno sin otro coste. Barrera e invisibilidad hasta el próximo comienzo propio. Aturdimientos no acumulables, sin corazones ni eliminación.
- Diez bordes/números persistentes, ficha propia grande, ocupantes desplazados y panel desplazable; retratos completos sin rotar. Cámara seguir/explorar/centrar, pasos suaves, destellos, dado, clasificación, inventario y efectos identificados por las imágenes originales. Cambio privado entre humanos.
- Guardado de todas las fases en Room, revisión CAS, respaldo transaccional y recuperación de premio/movimiento/temporizadores. Lectura histórica v1–v3 conservada para respaldos; v4 requiere esta aplicación o una posterior que lo admita.

## Balance

`RaceBalance` centraliza roca 5, avispas 8, colocación 5, ojo 8, radio de dinamita 2, ojo 3 y trampas 3 rondas. Cada guardado incluye sus valores, para que un ajuste posterior no reinterprete una partida existente. No se añaden habilidades exclusivas ni red.

## Validación y entrega

- **60 escenarios JVM dirigidos aprobados**: 57 en la batería ampliada y 3 comprobaciones repetidas tras corregir fixtures. Incluyen reglas de diez corredores, casillas compartidas, ramas/progreso, cajas y disponibilidad, inventario lleno, todos los objetos, daño no acumulable, carrera completa automática, igualdad de premios/dados, privacidad de interfaz, guardado CAS, cancelación, rollback, backups históricos y cifrados. Las pruebas históricas de interfaz se actualizan a la carrera que reemplaza la batalla.
- La prueba de migración histórica ahora compara las columnas originales de Room 22 y comprueba por separado el valor nuevo `layersFirst=false` de Room 27; no cambia ninguna migración de producción. La captura visual usa dibujo directo del decorView, pues PixelCopy no respondió en Robolectric; la carga de las imágenes y las interacciones se verifican además por semántica.
- **5 pruebas nativas aprobadas** en el emulador con un paquete aislado `com.r0ybt.arachn0de.racevalidation`: las 3 oficiales de SQLCipher (migración, apertura/clave incorrecta y recuperación interrumpida), recuperación cifrada de carrera con premio pendiente conservando productividad, y migración 26→27 con ambos almacenes y fotografías. Los resultados fueron `OK (3 tests)` y `OK (2 tests)`; no se reemplazó ni limpió la aplicación del usuario.
- Compilación Debug y AndroidTest aprobadas. Lint Debug completo aprobado: **0 errores, 42 advertencias y 10 sugerencias**. Incluye una advertencia no bloqueante sobre el overload de `offset` durante la animación; no se desactivaron checks ni se cambiaron opciones de Gradle. `git diff --check` aprobado.
- Verificación de ZIP, firma Debug y recursos: **25/25 PNG** idénticos a los del repositorio, incluidos los 20 adjuntos y las 5 texturas históricas. Los 20 originales también se compararon byte por byte con los archivos adjuntos. Sus hashes están en [GAME_RACE_ASSETS_SHA256.txt](GAME_RACE_ASSETS_SHA256.txt).
- Preflight oficial renovado y aprobado con el código probado, sin contraseñas ni firma. El helper conserva keystore/alias y exige la misma validación de certificado oficial; se amplía únicamente la comprobación de imágenes a los 25 PNG.

APK de revisión: **`/home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/Arachn0de-v0.3.0-code10-carrera-DEBUG.apk`**, **163.311.646 bytes (155,75 MiB)**, versión **0.3.0 / 10**, `com.r0ybt.arachn0de`. Firma v2 válida de Debug, certificado SHA-256 `2b77e47d7a03bccf1cc2edee3543d0b8b2a591e94b9875cbe30625a492b14883`; **no es la firma oficial ni puede actualizar una instalación oficial**. SHA-256 del APK: `357e21bca2eae53d2b015a9a71d45557382b41917a771d40391a305dd3d11065`. Se adjunta `.apk.sha256`; logs y resumen JSON quedan en `release-assets/carrera-*`, y la captura en `release-assets/Arachn0de-carrera-v0.3.0-vista-previa.png`.

Para generar manualmente el APK con la firma oficial, usando exclusivamente la terminal:

```bash
cd /home/r0ybt/AndroidStudioProjects/Arachn0de
bash release-assets/preparar-apk-0.3.0.sh
```

No se ejecutó la firma oficial, no se crearon keystores, no se modificó la versión ni se publicó una release. Se conserva la APK oficial anterior. El script solo solicita las dos contraseñas, sin eco y sin guardarlas, y verifica firma/identidad/configuración Release al terminar.

## Límites reales

- Falta validar esta APK en el teléfono físico y comprobar allí actualización, rendimiento y comodidad del tablero con datos reales. La prueba nativa usa el emulador, no certifica todos los dispositivos.
- Los bots tienen decisiones sencillas y el balance es inicial; no existen habilidades exclusivas ni multijugador en red, conforme al alcance solicitado.
- Los guardados antiguos siguen respaldables/restaurables, pero no son jugables como un segundo modo. Crear una carrera sustituye el único guardado después de confirmación; conservar un respaldo si se desea archivar la batalla anterior. Una aplicación anterior no puede leer el payload de carrera v4.
- La generación/verificación final del APK oficial queda para la ejecución manual del comando anterior. Se mantiene versionCode 10; este sprint no constituye una nueva release ni un incremento de versión. Conserva un respaldo recuperable antes de actualizar, sin desinstalar ni borrar datos.
