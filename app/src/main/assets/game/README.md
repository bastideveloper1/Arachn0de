# Texturas definitivas del juego

PNG originales incorporados, conservando exactamente sus nombres y bytes:

- `pantano.png`
- `pastoseco.png`
- `terrenocueva.png`
- `paredcueva.png`
- `araña.png`

Los cinco están incorporados. `GameTextures` los carga automáticamente desde `assets/game/`; los cuatro nombres ASCII también admiten `res/drawable-nodpi/`. Mantener `araña.png` en assets: Android no admite ñ en identificadores drawable. La araña se dibuja encima del suelo como entidad independiente.

Todos tienen transparencia RGBA. Las losetas y paredes son de 1254×1254 y la araña de 1323×1189; el renderer usa ContentScale.Fit para conservar proporciones y rota las paredes según el mapa. La araña se superpone como entidad independiente, respetando la niebla y su derrota. No se han creado imágenes sustitutas ni se han editado los originales. Usar PNG de hasta 4096×4096; se reducen a un máximo de 512×512 en memoria. Son recursos del APK y no archivos privados del usuario.
