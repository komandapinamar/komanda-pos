/* Original procedural scene. No third-party shaders, assets or network requests. */
(() => {
  'use strict';
  const embedded = new URLSearchParams(location.search).has('embedded');
  const canvas = document.getElementById('scene');
  const status = document.getElementById('status');
  const error = document.getElementById('error');
  const gl = canvas.getContext('webgl', { alpha: false, antialias: false, powerPreference: 'low-power' });
  const fail = message => { error.textContent = message; error.hidden = false; };
  if (!gl) { fail('Este dispositivo no tiene WebGL disponible. Elegí otra animación del proyector.'); return; }
  const vertex = `
    attribute vec2 position;
    void main() { gl_Position = vec4(position, 0.0, 1.0); }
  `;
  const fragment = `
    precision highp float;
    uniform vec2 resolution;
    uniform float time, intensity, palette, embedded;
    uniform vec3 audio;
    mat2 rotate(float a) { float c=cos(a), s=sin(a); return mat2(c,-s,s,c); }
    float shape(vec3 p) {
      p.xz = rotate(time * 0.12) * p.xz;
      p.xy = rotate(0.28 * sin(p.z * 1.4 + time * 0.2)) * p.xy;
      float a = atan(p.z, p.x);
      float r = length(p.xz);
      float fold = sin(a * 5.0 + p.y * 3.2 - time * 0.48);
      float radius = 0.94 + 0.12 * fold + audio.x * 0.12 * intensity;
      vec2 q = vec2(r - radius, p.y);
      float tube = 0.34 + 0.075 * sin(a * 3.0 - time * 0.3);
      float ridges = sin(a * 26.0 + p.y * 12.0 + time * 0.4) * 0.014;
      return length(q) - tube + ridges;
    }
    vec3 normalAt(vec3 p) {
      vec2 e = vec2(0.002,0.0);
      return normalize(vec3(shape(p+e.xyy)-shape(p-e.xyy),
        shape(p+e.yxy)-shape(p-e.yxy),shape(p+e.yyx)-shape(p-e.yyx)));
    }
    vec3 environment(vec3 d) {
      vec3 cool = palette > 1.5 ? vec3(0.13,0.65,0.95) : vec3(0.25,0.65,0.8);
      vec3 warm = palette > 1.5 ? vec3(0.65,0.85,1.0) : vec3(1.0,0.34,0.08);
      if (palette > 0.5 && palette < 1.5) { cool=vec3(0.65,0.18,0.04); warm=vec3(1.0,0.7,0.25); }
      float strip = pow(max(0.0,1.0-abs(d.y*0.7+d.x*0.6-0.12)),18.0);
      float upper = pow(max(dot(d,normalize(vec3(-0.4,0.8,0.4))),0.0),12.0);
      float lower = pow(max(dot(d,normalize(vec3(0.8,-0.25,0.4))),0.0),9.0);
      return vec3(0.035,0.045,0.065) + vec3(0.85,0.94,1.0)*strip*1.4 + cool*upper*2.5 + warm*lower*2.8;
    }
    void main() {
      vec2 uv=(gl_FragCoord.xy*2.0-resolution)/resolution.y;
      // Leave editorial space in the standalone preview. Projection stays centered.
      uv.x -= embedded > 0.5 ? 0.0 : (resolution.x/resolution.y > 1.3 ? 0.64 : 0.0);
      if (embedded < 0.5) {
        if (resolution.x/resolution.y > 1.3) uv *= 1.15;
        else { uv *= max(1.45,1.5*resolution.y/resolution.x); uv.y -= 0.65; }
      }
      vec3 ro=vec3(0.0,0.0,4.7);
      vec3 rd=normalize(vec3(uv, -3.0));
      mat2 tilt=rotate(0.63 + sin(time*0.09)*0.18);
      ro.yz=tilt*ro.yz; rd.yz=tilt*rd.yz;
      ro.xy=rotate(-0.32)*ro.xy; rd.xy=rotate(-0.32)*rd.xy;
      float travel=0.0;
      float hit=0.0;
      for (int i=0;i<72;i++) {
        float d=shape(ro+rd*travel);
        if (d<0.0025) { hit=1.0; break; }
        travel += max(d*0.65,0.001);
        if (travel>8.0) break;
      }
      vec3 color=vec3(0.0);
      if (hit>0.5) {
        vec3 p=ro+rd*travel;
        vec3 n=normalAt(p);
        vec3 reflection=reflect(rd,n);
        float fresnel=pow(1.0-max(dot(n,-rd),0.0),3.0);
        float ao=clamp(shape(p+n*0.12)/0.12,0.25,1.0);
        vec3 metal=environment(reflection);
        float grain=sin(p.y*92.0+atan(p.z,p.x)*48.0)*0.035;
        vec3 film=0.65+0.35*cos(vec3(0.3,2.1,4.1)+dot(n,-rd)*7.0+time*0.08);
        color=metal*(0.65+film*0.45+grain)*ao;
        color+=environment(n)*0.12+fresnel*vec3(0.12,0.16,0.2);
        color*=0.9+audio.y*intensity*0.22;
        color+=pow(max(dot(reflection,normalize(vec3(-0.5,0.8,1.0))),0.0),64.0)*vec3(1.0,0.92,0.8)*(0.4+audio.z*intensity);
        color=color/(1.0+color);
        color=pow(color,vec3(0.4545));
      }
      gl_FragColor=vec4(color,1.0);
    }
  `;
  function compile(type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      gl.deleteShader(shader);
      throw new Error('No se pudo compilar la escena en esta GPU.');
    }
    return shader;
  }
  let program;
  try {
    program = gl.createProgram();
    const vs = compile(gl.VERTEX_SHADER, vertex), fs = compile(gl.FRAGMENT_SHADER, fragment);
    gl.attachShader(program, vs); gl.attachShader(program, fs); gl.linkProgram(program);
    gl.deleteShader(vs); gl.deleteShader(fs);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error('Esta GPU no puede iniciar la escena.');
  } catch (e) { fail(e.message); return; }
  gl.useProgram(program);
  const buffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]), gl.STATIC_DRAW);
  const position = gl.getAttribLocation(program, 'position');
  gl.enableVertexAttribArray(position); gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
  const uniforms = Object.fromEntries(['resolution','time','audio','intensity','palette','embedded'].map(k => [k, gl.getUniformLocation(program,k)]));
  let amount=0.8, palette=0, demo=false, paused=matchMedia('(prefers-reduced-motion: reduce)').matches && !embedded;
  let features=[0,0,0], target=[0,0,0], nativeAt=-Infinity;
  let audioContext, stream, analyser, bins, source, audioGeneration=0;
  let elapsed=0, previous=performance.now(), lastFrame=0, frameCount=0, reportAt=performance.now();
  let raf=0, lost=false, hostActive=!embedded;
  const clamp = x => Number.isFinite(x) ? Math.min(1,Math.max(0,x)) : 0;
  // Called only through evaluateJavascript by the trusted local Android host.
  window.setProjectorAudio = (bass, mid, high) => { target=[clamp(bass),clamp(mid),clamp(high)]; nativeAt=performance.now(); };
  const meters = ['bass','mid','high'].map(id => document.getElementById(id));
  function stopAudio() {
    audioGeneration++;
    if (source) source.disconnect();
    if (stream) stream.getTracks().forEach(track => track.stop());
    if (audioContext) audioContext.close().catch(() => {});
    source=stream=audioContext=analyser=bins=null;
    document.getElementById('microphone').textContent='Usar micrófono';
  }
  function setDemo(on) {
    demo=on;
    document.getElementById('demo').setAttribute('aria-pressed',String(on));
    document.getElementById('demo').textContent=on ? 'Detener pulso demo' : 'Probar pulso demo';
  }
  function size() {
    const scale=Math.min(1,960/innerWidth,640/innerHeight);
    canvas.width=Math.max(1,Math.floor(innerWidth*scale));
    canvas.height=Math.max(1,Math.floor(innerHeight*scale));
    gl.viewport(0,0,canvas.width,canvas.height);
  }
  addEventListener('resize',size); size();
  function frame(now) {
    raf=0;
    if (document.hidden || lost || !hostActive) { previous=now; return; }
    if (now-lastFrame<32) { raf=requestAnimationFrame(frame); return; }
    const dt=Math.min((now-previous)/1000,0.1); previous=now; lastFrame=now;
    if (!paused) elapsed+=dt*(0.65+features[0]*amount*0.5);
    if (analyser) {
      analyser.getByteFrequencyData(bins);
      const hz=audioContext.sampleRate/analyser.fftSize;
      target=[[40,200],[200,2000],[2000,10000]].map(([lo,hi]) => {
        let sum=0,n=0;
        for (let i=Math.ceil(lo/hz);i<Math.min(bins.length,Math.ceil(hi/hz));i++) { sum+=bins[i]/255; n++; }
        return n ? sum/n : 0;
      });
    } else if (demo) {
      const beat=(elapsed*1.8)%1;
      target=[Math.exp(-beat*9)*0.9,0.25+Math.sin(elapsed*3)*0.18,Math.exp(-((elapsed*3.6)%1)*14)*0.6];
    } else if (now-nativeAt>600) target=[0,0,0];
    features=features.map((x,i) => x+(target[i]-x)*(1-Math.exp(-dt*(target[i]>x ? 16 : 4))));
    gl.uniform2f(uniforms.resolution,canvas.width,canvas.height);
    gl.uniform1f(uniforms.time,elapsed);
    gl.uniform3fv(uniforms.audio,features);
    gl.uniform1f(uniforms.intensity,amount);
    gl.uniform1f(uniforms.palette,palette);
    gl.uniform1f(uniforms.embedded,embedded?1:0);
    gl.drawArrays(gl.TRIANGLES,0,6);
    frameCount++;
    if (!embedded) {
      meters.forEach((meter,i) => { meter.style.width=`${Math.round(features[i]*100)}%`; });
      if (now-reportAt>1000) {
        document.getElementById('performance').textContent=`${Math.round(frameCount*1000/(now-reportAt))} FPS · ${canvas.width} × ${canvas.height}`;
        frameCount=0; reportAt=now;
      }
    }
    raf=requestAnimationFrame(frame);
  }
  function resume() { if (!raf && !lost && !document.hidden && hostActive) { previous=performance.now(); raf=requestAnimationFrame(frame); } }
  window.setProjectorActive = active => {
    hostActive=Boolean(active);
    if (!hostActive) { cancelAnimationFrame(raf); raf=0; target=[0,0,0]; nativeAt=-Infinity; }
    else resume();
  };
  document.addEventListener('visibilitychange',() => {
    if (document.hidden) { cancelAnimationFrame(raf); raf=0; stopAudio(); setDemo(false); status.textContent='Modo ambiente. Sin audio conectado.'; }
    else resume();
  });
  canvas.addEventListener('webglcontextlost',event => { event.preventDefault(); lost=true; cancelAnimationFrame(raf); raf=0; stopAudio(); fail('La GPU interrumpió la escena. Volvé a abrir esta animación.'); });
  document.querySelectorAll('[data-palette]').forEach(button => button.addEventListener('click',() => {
    palette=Number(button.dataset.palette);
    document.querySelectorAll('[data-palette]').forEach(b => b.setAttribute('aria-pressed',String(b===button)));
  }));
  document.getElementById('intensity').addEventListener('input',event => {
    amount=Number(event.target.value); document.getElementById('intensity-value').value=amount.toFixed(2);
  });
  document.getElementById('demo').addEventListener('click',() => {
    stopAudio(); setDemo(!demo);
    status.textContent=demo ? 'Pulso sintético de demostración. No está escuchando Spotify.' : 'Modo ambiente. Sin audio conectado.';
  });
  document.getElementById('microphone').addEventListener('click',async () => {
    if (stream) { stopAudio(); status.textContent='Modo ambiente. Micrófono desconectado.'; return; }
    stopAudio(); setDemo(false);
    const generation=audioGeneration;
    status.textContent='Esperando permiso de micrófono…';
    try {
      const pending=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:false}});
      if (generation!==audioGeneration || document.hidden) { pending.getTracks().forEach(t=>t.stop()); return; }
      stream=pending; audioContext=new AudioContext(); await audioContext.resume();
      if (generation!==audioGeneration) return;
      analyser=audioContext.createAnalyser(); analyser.fftSize=2048; bins=new Uint8Array(analyser.frequencyBinCount);
      source=audioContext.createMediaStreamSource(stream); source.connect(analyser);
      status.textContent='Micrófono en vivo. Análisis local, sin grabación. No captura Spotify internamente.';
      document.getElementById('microphone').textContent='Desconectar micrófono';
    } catch (_) { if (generation===audioGeneration) { stopAudio(); status.textContent='No pudimos abrir el micrófono. Revisá el permiso o usá el pulso demo.'; } }
  });
  document.getElementById('fullscreen').addEventListener('click',async () => {
    try { if (document.fullscreenElement) await document.exitFullscreen(); else await document.documentElement.requestFullscreen(); }
    catch (_) { status.textContent='Pantalla completa no disponible en este navegador.'; }
  });
  const pauseButton=document.getElementById('pause');
  function pauseLabel() { pauseButton.textContent=paused?'Reanudar':'Pausar'; pauseButton.setAttribute('aria-pressed',String(paused)); }
  pauseLabel();
  pauseButton.addEventListener('click',() => { paused=!paused; pauseLabel(); });
  addEventListener('pagehide',() => { cancelAnimationFrame(raf); raf=0; stopAudio(); });
  addEventListener('pageshow',resume);
  resume();
})();
