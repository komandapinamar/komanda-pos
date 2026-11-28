/* Menu preview only. No animation, canvas, third-party code or network requests. */
(() => {
  'use strict';
  const intensity = document.getElementById('intensity');
  const intensityValue = document.getElementById('intensity-value');
  const paletteButtons = Array.from(document.querySelectorAll('[data-palette]'));

  paletteButtons.forEach(button => button.addEventListener('click', () => {
    paletteButtons.forEach(other => other.setAttribute('aria-pressed', String(other === button)));
  }));

  intensity.addEventListener('input', event => {
    intensityValue.value = Number(event.target.value).toFixed(2);
  });

  document.getElementById('fullscreen').addEventListener('click', async () => {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await document.documentElement.requestFullscreen();
    } catch (_) {
      document.getElementById('status').textContent = 'Pantalla completa no disponible en este navegador.';
    }
  });
})();
