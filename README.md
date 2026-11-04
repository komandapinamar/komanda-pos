# Komanda POS

Aplicación Android nativa del **punto de venta** de Komanda: toma de pedidos,
tablero de cocina, caja e impresión térmica ESC/POS.

- Stack: Kotlin + Jetpack Compose + Coroutines + Retrofit/Moshi + Room
- `applicationId`: `com.komanda.pos`
- Bundle: `com.komanda.business`

## Requisitos

- JDK 17 (`org.gradle.java.home` ya apunta a `/usr/lib/jvm/java-17-openjdk`)
- Android SDK con la plataforma **API 37**
- Gradle se ejecuta con el wrapper incluido (9.5.0)

## Build de desarrollo

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug          # con un dispositivo/emulador conectado
```

## Backend

La URL base se inyecta como `BuildConfig.DEFAULT_BASE_URL` y por defecto es
`https://kda.ar`. Se puede cambiar sin tocar código:

```bash
./gradlew :app:assembleDebug -Pkomanda.baseUrl=http://10.0.2.2:3000
# o
KOMANDA_BASE_URL=http://10.0.2.2:3000 ./gradlew :app:assembleDebug
```

Además, desde la pantalla de login se puede editar la URL del servidor; queda
guardada en las preferencias del dispositivo y tiene prioridad sobre el default.

## Firma y release

Las credenciales se leen de `keystore.properties` (gitignored) o de variables de
entorno. Si el archivo no existe, el build de release sale **sin firmar**.

```bash
./gradlew :app:bundleRelease
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

Variables de entorno equivalentes (CI):

```
KOMANDA_KEYSTORE_FILE=/ruta/al/upload.jks
KOMANDA_KEYSTORE_PASSWORD=…
KOMANDA_KEY_ALIAS=komanda-pos
KOMANDA_KEY_PASSWORD=…
```

> La upload key vive **fuera del repo**, en `~/.komanda/keys/`. Hacé backup del
> `.jks` y de sus contraseñas: sin ella no se pueden firmar actualizaciones.

## Versionado

`version.properties` es la única fuente de verdad:

```properties
versionCode=3
versionName=0.0.2
```

`versionCode` debe incrementarse en 1 en cada subida a Play Console.

## Tests

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintRelease
```

## Publicación en Play

Ver [`PLAY.md`](PLAY.md) y [`PRIVACY.md`](PRIVACY.md).
