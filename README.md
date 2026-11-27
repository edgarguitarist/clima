# Clima

App de Android (Kotlin + Jetpack Compose, mismo stack que Lince) con el clima de donde estás:

- **Ahora**: temperatura, **sensación térmica**, humedad, punto de rocío, viento y ráfagas con dirección,
  nubosidad, lluvia, índice UV, presión, visibilidad, salida y puesta del sol.
- **Próximas 2 horas cada 15 min**: milímetros de lluvia y probabilidad.
- **24 horas** (temperatura, sensación, probabilidad de lluvia, nubosidad) y **10 días**.
- **Mapa «Radar y nubes»** con una línea de tiempo: las **últimas 2 horas** vistas por satélite (cada 20 min) y las
  **próximas 12 horas** pronosticadas (cada hora). Se anima sola; tocando la barra se salta a un momento.
  - **Lluvia** (encendida): en el pasado, las tormentas del infrarrojo GOES-East (~30 min de retraso; la app traduce la
    paleta de la NASA a temperatura y la repinta con colores de lluvia). En el futuro, la lluvia del modelo de
    Open-Meteo en una cuadrícula de 17×17 puntos (±3°) alrededor tuyo, suavizada.
  - **Radar** (encendida): RainViewer, solo pasado. En Ecuador casi no hay radares públicos.
  - **Nubes**: foto del satélite GOES-East (GeoColor) en el pasado y nubosidad del modelo en el futuro.
  - **Lluvia NASA** (IMERG): fija; más exacta pero con ~5 h de retraso. Muestra su hora y se desenfoca según el zoom.
- Se actualiza sola cada 10 min y al volver a la app; también deslizando hacia abajo. Al abrir muestra al instante
  el último pronóstico guardado mientras baja el nuevo, y reintenta 3 veces si la red se cuelga.

Sin permiso de ubicación muestra Guayaquil y lo dice junto al nombre.

Al abrir, avisa si hay una versión nueva en los [releases](https://github.com/edgarguitarist/clima/releases) de GitHub, la baja y abre el instalador (igual que Lince). «Ahora no» la vuelve a ofrecer al día siguiente.

## Fuentes (todas gratis y sin API key)

| Qué | Fuente |
|---|---|
| Pronóstico | [Open-Meteo](https://open-meteo.com): elige el mejor modelo para cada punto (ECMWF, GFS, ICON…) |
| Radar | [RainViewer](https://www.rainviewer.com/api.html) |
| Lluvia por satélite y nubes | NASA GIBS (IMERG y GOES-East GeoColor) |
| Mapa base | Esri World Dark Gray (CARTO empezó a pedir API key) |
| Nombre del lugar | `Geocoder` de Android |

Sin Google Play Services: la ubicación sale del `LocationManager` del sistema.

## Compilar

Requiere el SDK de Android (`local.properties` → `sdk.dir`) y un JDK 17 a 22.

```bash
./gradlew testDebugUnitTest assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk` (~1,6 MB). Se firma con la llave de Lince si
están `LINCE_STORE_FILE`, `LINCE_STORE_PASSWORD`, `LINCE_KEY_ALIAS` y `LINCE_KEY_PASSWORD` (o las mismas con
prefijo `CLIMA_`); si no, con la debug.

## Estructura

| Archivo | Qué hace |
|---|---|
| `Clima.kt` | Pide y parsea el pronóstico de Open-Meteo; textos e íconos de los códigos WMO |
| `Actualizador.kt` | Aviso de versión nueva desde los releases de GitHub |
| `Ubicacion.kt` | Ubicación actual y nombre del lugar |
| `MainActivity.kt` | Pantallas: pronóstico y mapa |
| `assets/mapa.html` | El mapa (Leaflet embebido, en un WebView) |

## Publicar una versión

Subir `versionCode` y `versionName` en `app/build.gradle.kts`, compilar y:

```bash
gh release create vX.Y.Z app/build/outputs/apk/release/app-release.apk#Clima-X.Y.Z.apk --notes "qué cambió"
```

El release debe quedar firmado con la misma llave (la de Lince), o Android no deja actualizar encima.

## Diagnóstico

Los errores de la página del mapa van al logcat: `adb logcat -s ClimaMapa`.
