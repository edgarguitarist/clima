# Clima

App de Android (Kotlin + Jetpack Compose, mismo stack que Lince) con el clima de donde estás:

- **Ahora**: temperatura, **sensación térmica**, humedad, punto de rocío, viento y ráfagas con dirección,
  nubosidad, lluvia, índice UV, presión, visibilidad, salida y puesta del sol.
- **Próximas 2 horas cada 15 min**: milímetros de lluvia y probabilidad.
- **24 horas** (temperatura, sensación, probabilidad de lluvia, nubosidad) y **10 días**.
- **Mapa «Radar y nubes»**, animado, con tres capas que se prenden y apagan:
  - **Radar**: lluvia de radares en tierra, últimas 2 h cada 10 min (RainViewer).
  - **Lluvia sat.**: estimación de lluvia de la NASA (IMERG, cada 30 min, resolución ~10 km). Cubre donde no hay
    radar, que es casi todo Ecuador.
  - **Nubes**: imagen real del satélite GOES-East (cada 10 min; de noche, infrarrojo). Cubre América.
- Se actualiza sola cada 10 min y al volver a la app; también deslizando hacia abajo.

Sin permiso de ubicación muestra Guayaquil y lo dice junto al nombre.

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
| `Ubicacion.kt` | Ubicación actual y nombre del lugar |
| `MainActivity.kt` | Pantallas: pronóstico y mapa |
| `assets/mapa.html` | El mapa (Leaflet embebido, en un WebView) |

## Diagnóstico

Los errores de la página del mapa van al logcat: `adb logcat -s ClimaMapa`.
