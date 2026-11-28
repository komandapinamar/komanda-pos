# Previsualización del menú (preview web)

Vista previa estática del menú de controles: paletas, intensidad y pantalla
completa. Sin animaciones, canvas ni recursos remotos.

## Preview

Desde la raíz del repositorio:

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory app/src/main/assets/projector
```

Abrir <http://127.0.0.1:8765/menu.html>.

## Controles

- **Cromo / Ámbar / Glaciar:** selección visual de paleta.
- **Intensidad:** slider con valor numérico.
- **Pantalla completa:** fullscreen del navegador.

## Alcance

Solo la previsualización del menú y su test. No incluye escena 3D, WebGL,
integración con Android ni permisos de la app.

## Verificación

```sh
node scripts/test-projector-menu.mjs
```

Requiere Chromium y Node 22+. Si el ejecutable se llama distinto, definir
`CHROMIUM`. Para guardar capturas reales del navegador:

```sh
PROJECTOR_ARTIFACTS=/tmp/opencode/komanda-menu node scripts/test-projector-menu.mjs
```
