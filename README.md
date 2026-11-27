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

- **Lo que pasa ahora, no solo el modelo**: la intensidad de la lluvia del último cuarto de hora en mm/h (ligera,
  moderada, fuerte) y lo que ve el satélite infrarrojo justo encima tuyo («Tormenta fuerte encima», con su hora).
  El modelo puede no ver una tormenta que ya está sobre ti; el satélite sí.
- **Se coteja con la estación del aeropuerto** (METAR, cada hora, aviationweather.gov) si hay una a menos de 20 km:
  los modelos dan llovizna en Guayaquil mucho más de lo que llueve, así que si la estación no ve lluvia, el «ahora»
  pasa a lo que ella reporta (con nota de lo que daba el modelo), y al revés si llueve y el modelo no lo ve. La
  temperatura y la humedad que se muestran son las medidas por la estación.
- **Cielo sobre tu punto exacto** (de día): el visible de GOES-East (~1 km, cada 10 min) dice cuántas nubes tienes
  encima. Con pocas nubes no puede llover, así que se desmiente la lluvia del modelo y se muestra el cielo que se ve.
- **Titular** arriba de todo que responde «¿necesito paraguas?» («☔ Lluvia hasta las 20:30», «😎 Sin lluvia en
  las próximas 2 horas»), y avisa si el satélite ve una tormenta encima aunque el modelo no la vea.
- **Lugares guardados** (Guayaquil y Durán de entrada) con chips arriba, una tarjeta «Tus lugares ahora» para comparar
  de un vistazo (por ejemplo, cómo está Guayaquil antes de salir de Durán) y un buscador para agregar más.
- **Aviso de lluvia**: cada ~15 min (WorkManager) revisa los lugares con el aviso activado y notifica si va a llover
  en la próxima hora o si el satélite ve una tormenta encima. No repite el mismo aviso antes de 3 h. También avisa
  si una pleamar de las próximas horas coincide con lluvia (riesgo de que se represe el agua), una vez por pleamar.
- **Tocar una hora o un día** abre su detalle: lluvia e intensidad, probabilidad, viento y ráfagas, humedad, UV,
  visibilidad; el día suma lluvia total, horas con lluvia, amanecer/atardecer y su hora por hora.
- **Deslizar** a los lados cambia entre «Aquí» y los lugares guardados.
- **Las tarjetas se ordenan según lo que importa ahora**: si viene lluvia, la marea coincide con lluvia o el aire está
  malo, esa tarjeta sube.
- **Íconos animados** (Meteocons, MIT) y **fondo animado** según el tiempo: gotas, nubes, sol, estrellas, niebla.
- **Calidad del aire** (Open-Meteo / Copernicus): índice US-AQI con consejo, PM2.5, PM10, O₃ y NO₂, y el peor valor de
  las próximas 24 h. Polen no: Open-Meteo solo lo da para Europa.
- **Marea del río Guayas** (para lugares a menos de ~60 km de Guayaquil): nivel ahora y si sube o baja, próxima
  pleamar o bajamar, gráfico de 6 h atrás a 30 h adelante, aviso de **aguaje** (luna nueva o llena: las mareas más altas
  del mes) y alerta si una pleamar coincide con lluvia fuerte prevista, porque el agua no tiene por dónde salir. Fuente:
  predicción oficial de INOCAR para la estación Guayaquil-Río (Durán queda enfrente); si no responde, el modelo de
  Open-Meteo en el estuario, que no llega río adentro.
- **Widget** para la pantalla de inicio (4×2, redimensionable): temperatura, sensación, máx/mín y la lluvia de las
  próximas 2 horas. Se refresca cada 30 min con la última ubicación que usó la app, y al abrirla. Y uno **chico
  (2×2)**: temperatura y «Lluvia en 20 min» / «Para en 15 min».

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
| `Estacion.kt` | Estación meteorológica cercana (METAR) para corregir el «ahora» del modelo |
| `Satelite.kt` | GOES-East sobre tu ubicación: infrarrojo («tormenta encima») y visible (cuántas nubes) |
| `Lugares.kt`, `PantallaLugares.kt` | Lugares guardados, buscador y su pantalla |
| `Alerta.kt` | Aviso de lluvia en segundo plano |
| `Aire.kt`, `ui/TarjetaAire.kt` | Calidad del aire |
| `Marea.kt`, `PantallaMarea.kt` | Marea del río Guayas (INOCAR), aguaje y su gráfico |
| `Widget.kt`, `WidgetChico.kt` | Widgets de pantalla de inicio (4×2 y 2×2) |
| `DetalleHoraDia.kt` | Hojas de detalle de una hora o un día |
| `Iconos.kt`, `assets/iconos/` | Íconos animados Meteocons (Lottie) |
| `FondoAnimado.kt` | Fondo animado según el tiempo |
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
