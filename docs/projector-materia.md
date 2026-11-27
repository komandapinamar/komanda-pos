# Materia projector preview

Standalone procedural WebGL sculpture with metallic shading, animated folds and
audio-driven deformation. This is an original shader scene, not a cables.gl patch,
TouchDesigner runtime, recorded video, or fluid physics simulation.

The preview is fully local: no CDN, remote assets or network requests.

## Preview

From the repository root:

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory app/src/main/assets/projector
```

Open <http://127.0.0.1:8765>.

## On-screen menu

- **Cromo / Ámbar / Glaciar:** color treatments.
- **Probar pulso demo:** clearly labeled synthetic features, not Spotify capture.
- **Usar micrófono:** explicit browser consent; locally analyzed, never recorded or
  uploaded. This is not internal Spotify capture. Stops when the page is hidden or
  unloaded.
- **Intensidad:** adjusts audio responsiveness.
- **Pausar:** stops sculpture time; audio meters can still follow input.
- **Pantalla completa:** browser fullscreen.
- Reduced-motion preference starts the standalone scene paused.
- `?embedded=1` hides the menu and centers the sculpture for a host page.

The page exposes `window.setProjectorActive(boolean)` and
`window.setProjectorAudio(bass, mid, high)` for a trusted host page. Non-finite or
out-of-range values are ignored. Rendering is suspended while inactive or hidden.

## Scope

This change only adds the standalone preview, its on-screen menu and the smoke
test. It is **not wired into Komanda POS** yet: no Android navigation entry, no
external-display presentation, no calibration and no audio permission flow are
part of this PR.

## Verification

```sh
node scripts/test-projector-preview.mjs
```

The smoke test requires Chromium and Node 22+ with built-in WebSocket. Set
`CHROMIUM` if the executable has a different name. Set
`PROJECTOR_ARTIFACTS=/tmp/opencode/komanda-materia` to save actual browser-rendered
screenshots. Chromium uses software rendering in this test; its frame rate is not
evidence of Telpo GPU performance.

The renderer caps internal resolution to 960 x 640 bounds and targets approximately
30 fps on the browser preview. A real-device acceptance check is still required
before using this scene in the POS projector.
