# Clima

App de Android (Kotlin + Jetpack Compose, mismo stack que Lince) con el clima de donde estás:

- **Ahora**: temperatura, **sensación térmica**, humedad, punto de rocío, viento y ráfagas con dirección,
  nubosidad, lluvia, índice UV, presión, visibilidad, salida y puesta del sol.
- **Próximas 2 horas cada 15 min**: milímetros de lluvia y probabilidad.
- **24 horas** (temperatura, sensación, probabilidad de lluvia, nubosidad) y **10 días**.
- **Mapa «Radar y nubes»**, con cuatro capas que se prenden y apagan:
  - **Tormentas** (encendida): infrarrojo del satélite GOES-East cada 10 min (~30 min de retraso). Los topes de nube
    bajo -38 °C son tormentas donde casi seguro llueve; la app traduce la paleta de la NASA a temperatura y la repinta
    con colores de lluvia (más frío = más fuerte). Es lo único al día en Ecuador, donde no hay radares públicos.
  - **Radar** (encendida, animada): lluvia de radares en tierra, últimas 2 h cada 10 min (RainViewer).
  - **Nubes**: foto real del satélite GOES-East (GeoColor; de noche, infrarrojo).
  - **Lluvia NASA** (IMERG): estimación más exacta, pero llega con ~5 h de retraso y en celdas de ~10 km; se
    desenfoca según el zoom para que no se vean cuadros. Muestra la hora de los datos.
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
