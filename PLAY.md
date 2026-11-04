# Publicación en Google Play — Komanda POS

Checklist operativo para subir la app. Lo que ya está resuelto en el repo está
marcado ✅; lo que depende de una persona o de Play Console está marcado ⬜.

---

## 1. Estado técnico del repo

| Ítem | Estado |
| :--- | :--- |
| `applicationId` | ✅ `com.komanda.pos` |
| Firma de release | ✅ `signingConfigs.release` cableado vía `keystore.properties` / env vars |
| AAB firmado | ✅ `./gradlew :app:bundleRelease` firma con la upload key |
| Versión | ✅ `version.properties` (`versionCode=3`, `versionName=0.0.2`) |
| Lint de release | ✅ `./gradlew :app:lintRelease` pasa sin errores |
| `targetSdk` | ✅ 37 (Android 17) — cumple el requisito de Play de 2026 |
| Copias de seguridad | ✅ `allowBackup="false"` (el token de sesión no sale del dispositivo) |
| URL de backend | ✅ `BuildConfig.DEFAULT_BASE_URL` = `https://kda.ar`, override con `-Pkomanda.baseUrl=` |
| Atajos de desarrollo en login | ✅ ocultos en release (`BuildConfig.DEBUG`) |
| Icono de marca | ✅ reemplazado el template de Android Studio |
| Metadatos de tienda | ✅ `fastlane/metadata/android/es-AR/` |

> ⚠️ El backend **tiene que estar desplegado y sirviendo `/api/v1/...` en
> `https://kda.ar`** antes de publicar. Hoy ese host responde 307 en `/` y 404 en
> `/api/health/live`.

---

## 2. Bloqueantes que dependen de vos

| # | Qué falta | Detalle |
| :--- | :--- | :--- |
| 1 | **Cuenta de Google Play Console** | 25 USD único. Si la cuenta es personal nueva (no organización), Play exige **closed testing con 12 testers durante 14 días** antes de habilitar producción. Si tenés CUIT, conviene cuenta de organización (verificación por D-U-N-S). |
| 2 | **Política de privacidad publicada** | El texto está en `PRIVACY.md`. Falta: completar razón social/CUIT y publicarlo en una URL pública (ej. `https://kda.ar/privacidad/pos`). |
| 3 | **Email de soporte** | Usé `soporte@komanda.ar` como placeholder en metadatos y política. Confirmá que existe. |
| 4 | **Capturas de pantalla** | Play exige mínimo 2 por tipo de dispositivo. No se pueden generar sin un dispositivo con sesión iniciada contra el backend real. Ver §5. |
| 5 | **Backup de la upload key** | `~/.komanda/keys/komanda-pos-upload.jks` + contraseñas en `~/.komanda/keys/CREDENTIALS.txt`. Si se pierden y la app ya está publicada sin Play App Signing, **no se puede volver a actualizar**. |
| 6 | **Alta de la app en Play Console** | Nombre, categoría, país, contacto. No se puede automatizar sin credenciales de servicio. |

---

## 3. Cómo se firma

La config lee credenciales en este orden:

1. `keystore.properties` en la raíz del repo (gitignored, ya creado).
2. Variables de entorno (para CI): `KOMANDA_KEYSTORE_FILE`,
   `KOMANDA_KEYSTORE_PASSWORD`, `KOMANDA_KEY_ALIAS`, `KOMANDA_KEY_PASSWORD`.

Si no encuentra credenciales, el build **avisa por consola** y genera un AAB
**sin firmar** (Play lo rechaza).

```bash
# Generar el bundle de release
./gradlew :app:bundleRelease

# Verificar que quedó firmado (debe decir "jar verified")
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

Artefacto: `app/build/outputs/bundle/release/app-release.aab`

Huella de la upload key (para comparar con Play Console):

```bash
keytool -list -v -keystore ~/.komanda/keys/komanda-pos-upload.jks -alias komanda-pos | grep SHA256
```

---

## 4. Declaraciones en Play Console

### 4.1 Seguridad de los datos

| Pregunta | Respuesta |
| :--- | :--- |
| ¿La app recopila o comparte datos? | **Sí, recopila** |
| ¿Los datos se transmiten cifrados? | Sí (HTTPS) |
| ¿Se pueden solicitar la eliminación de datos? | Sí — por email a soporte |
| **Información personal → Dirección de correo electrónico** | Recopilada · No compartida · Obligatoria · Para autenticación |
| **Información personal → Nombre** | Recopilada (nombre del comercio/sucursal) · No compartida |
| **Información financiera → Historial de compras / pagos** | Recopilada (pedidos y cobros del comercio) · No compartida |
| **Actividad en la app → Acciones dentro de la app** | Recopilada (pedidos, caja) · No compartida |
| **Información y rendimiento de la app → Registros de errores** | Recopilado · No compartido |
| **Identificador de publicidad** | **No** se recopila |
| **Ubicación** | **No** se recopila |
| **Contactos, fotos, archivos, salud** | **No** se recopilan |

### 4.2 Clasificación de contenido (cuestionario IARC)

- Categoría: **Utilidad / Productividad / Negocios**
- Violencia, desnudos, drogas, lenguaje: **No**
- Apuestas, compras dentro de la app: **No**
- Interacción entre usuarios / ubicación compartida: **No**
- Resultado esperado: **Apto para todo público (3+)**

### 4.3 Permisos sensibles

| Permiso | Declaración |
| :--- | :--- |
| `CAMERA` | Uso principal: escanear códigos de barras. No se usa en segundo plano. No requiere formulario adicional. |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` | Impresoras térmicas. Uso acotado, sin formulario adicional. |
| `USB host` | Impresoras ESC/POS por cable. |

La app **no** usa `QUERY_ALL_PACKAGES`, `READ_SMS`, ubicación en segundo plano ni
servicios en primer plano, por lo que no aplican formularios extra.

### 4.4 Seguridad

- Target API: **37**
- App signing: **Play App Signing** (recomendado; subís la upload key y Google
  custodia la clave de firma de distribución).

---

## 5. Capturas de pantalla

Requisitos: JPG o PNG de 24 bits, entre 320 y 3840 px por lado, sin transparencia.

Con un dispositivo o emulador con sesión iniciada contra `https://kda.ar`:

```bash
adb exec-out screencap -p > phoneScreenshots/01-tablero.png
```

Guardarlas en `fastlane/metadata/android/es-AR/images/phoneScreenshots/`
(numeradas `01-…png`, `02-…png`, …). Mínimo 2, ideal 4–6:

1. Tablero de pedidos
2. Toma de pedido / carrito
3. Caja abierta
4. Impresión / ajustes de impresora

También sirve subirlas a mano desde Play Console.

---

## 6. Publicar

```bash
./gradlew clean :app:bundleRelease :app:lintRelease
```

1. Play Console → **Crear app** → nombre, idioma predeterminado (es-AR), app
   gratuita, declaraciones.
2. **Pruebas internas** → subir el `.aab` → revisar advertencias.
3. Completar *Contenido de la app* (privacidad, seguridad de datos,
   clasificación, público objetivo).
4. Completar *Presencia en la tienda* (textos e imágenes de
   `fastlane/metadata/android/es-AR/`).
5. Promover a **cerrada** (12 testers × 14 días si la cuenta es personal nueva).
6. Promover a **producción**.

### Incrementar versión antes de cada subida

```bash
# editá version.properties
versionCode=4
versionName=0.0.3
```

`versionCode` nunca se reutiliza, aunque la subida se descarte.
