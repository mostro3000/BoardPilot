# BoardPilot

> English version: [README.md](README.md)

Copiloto multimodal de IA para reparar placas electrónicas (PCB) a **nivel componente**.
El técnico fotografía la placa y describe el síntoma; BoardPilot mapea los componentes
con **SAM 3.1**, ordena hipótesis de falla y guía **mediciones reales con multímetro, una
por turno**, con **Muse Spark**, y genera una vista de referencia de la reparación con
**Muse Image** — todo vía **Meta Model API**, desde una **app Android nativa sin servidor
propio**.

> Proyecto del Meta Global AI Developer Hackathon (track Indie Developer, tema *Push the
> Frontier*), construido durante el período del evento (27 oct – 4 nov de 2026).

## Por qué

Los técnicos de campo y talleres independientes (foco inicial: Latinoamérica) suelen
reemplazar la placa completa —cara y a veces inhallable— porque diagnosticar a nivel
componente requiere esquemáticos, experiencia y tiempo. Resultado: reparaciones más caras
y más basura electrónica.

BoardPilot no es una "foto mágica que adivina la falla". Es un **agente de diagnóstico
verificable, en varios pasos, con el técnico en control**: cada veredicto se apoya en
mediciones que el técnico realmente hizo y, si la evidencia no alcanza, pide otra
medición o foto en vez de inventar.

## Cómo funciona: el loop del agente

1. **Ingreso** — foto de la galería o de la cámara (resolución completa, derecha según su
   orientación EXIF, con recorte y giro libres) más el síntoma ("no enciende").
2. **Mapeo (SAM 3.1)** — los componentes se segmentan por forma física ("cylindrical
   component", "yellow rectangular block", …), se filtran por confianza, se eliminan
   duplicados y se dibujan como overlay interactivo sobre la foto.
3. **Pre-escaneo de daño (Muse Spark)** — pasadas dedicadas sobre la foto limpia, entera y en
   cuatro sectores ampliados, buscan piezas quemadas, explotadas o hinchadas. El daño que SAM
   no encuadró se convierte en un candidato sintético (`DMG 1`) al que pueden apuntar
   hipótesis y mediciones.
4. **Hipótesis (Muse Spark)** — la foto anotada, la lista de candidatos (JSON con ids
   estables y cajas normalizadas) y el síntoma producen hasta cuatro hipótesis ordenadas,
   cada una con su confianza, su fundamento y qué reemplazar si se confirma (tipo de
   pieza, valores, encapsulado y un número de parte de ejemplo). Muse Spark primero
   identifica el **tipo de placa** (fuente, placa de TV/monitor, inverter de motor,
   placa de control de electrodoméstico, amplificador de audio, otra), o usa el que
   eligió el técnico, y aplica las fallas típicas y la estrategia de medición de ese tipo
   (guías versionadas en `app/src/main/resources/playbooks.json`). Las fallas que no se
   arreglan con tester (firmware, BGA, el panel) vuelven como consejo, no como hipótesis.
5. **Mediciones guiadas** — una por vez, y solo las que pueden cambiar la decisión: continuidad, resistencia, diodo o tensión DC, con
   qué lectura es normal o anormal. Cada medición pasa por una **compuerta de seguridad
   que fija el código, nunca el modelo**, y cada resultado actualiza las hipótesis de
   forma determinística. Una **guía visual** muestra una lupa del componente junto a las
   instrucciones, cómo preparar el multímetro (selector y entradas VΩ/COM) y, según Muse
   Spark, dónde van la punta roja y la negra, validado contra las cajas de los
   componentes y rotulado como sugerencia de IA. El mapa de la placa marca qué medir
   ahora, el % de cada hipótesis y la evidencia reunida (✓ ✗ ?).
6. **Replanificación (Muse Spark)** — si las mediciones se agotan sin veredicto, el agente
   pide las siguientes con las hipótesis y todo lo ya medido con su resultado.
7. **Veredicto y referencia (Muse Image)** — el componente confirmado, el reemplazo
   sugerido y una referencia generada por IA de la placa reparada, siempre rotulada como
   ilustrativa (nunca es evidencia diagnóstica).
8. **Verificación** — ¿encendió el equipo? La reparación se cierra o vuelve al veredicto.

Las respuestas del modelo nunca se usan a ciegas: son JSON estricto validado en una capa
de dominio pura y con tests, que descarta componentes inexistentes y tipos de medición
inválidos, rechaza mediciones en alterna (red eléctrica), resuelve designadores a ids
estables y saca los ids internos de los textos que lee el técnico.

## Modelos de Meta en la app

| Modelo | Endpoint | Rol |
|---|---|---|
| `sam-3.1` | `POST /v1/responses` (streaming) | Segmentación de componentes → overlay y lista de candidatos |
| `muse-spark-1.3-contributor` por defecto, tras aceptar un aviso (o `muse-spark-1.3`, Estándar) | `POST /v1/chat/completions` (multimodal) | Pre-escaneo de daño, hipótesis con su reemplazo, plan de mediciones y replanificación, ubicación de las puntas |
| `muse-image-1.0` | `POST /v1/responses` | Imagen de referencia de la placa reparada |

Cada modelo es parte del núcleo del flujo: sin SAM no hay mapa de componentes, sin Muse
Spark no hay diagnóstico y sin Muse Image no hay referencia de la reparación. El esfuerzo
de razonamiento de Muse Spark se configura en Configuración (Máximo por defecto), donde también
se elige entre los modos Contributor y Estándar: ver [Privacidad y tu clave](#privacidad-y-tu-clave).

## Construido durante el evento (27 oct – 4 nov de 2026)

Todo el código de la app de este repositorio se escribió durante el período de
construcción (antes solo hubo planificación y documentación); las fechas de los commits
son la evidencia.

- **27 oct** — smoke tests de la Meta Model API; fundación Android (API 37, Jetpack
  Compose) con la máquina de estados del diagnóstico, las compuertas de seguridad y un
  caso dorado de fuente conmutada (primero demo offline, hoy test de unidad); interfaz en
  español e inglés; configuración con la clave propia del usuario; protocolo de SAM 3.1 y
  primer mapeo en vivo.
- **28 oct** — estrategia de SAM por descriptores de forma validada sobre una fuente
  conmutada real; persistencia de sesión y permisos de cámara; diagnóstico en vivo con
  Muse Spark anclado a los candidatos de SAM; pre-escaneo de daño visible; esfuerzo de
  razonamiento configurable; referencia de reparación con Muse Image; proyectos guardados
  "Mis reparaciones"; recorte y giro libres y zoom con dos dedos; replanificación de
  mediciones con reemplazos concretos; foto completa de cámara con orientación EXIF;
  guía visual de mediciones (lupa, multímetro, mapa de evidencia y puntas sugeridas por
  IA); tipos de placa con playbooks versionados y zona de riesgo por medición; se quitó el
  demo offline (BoardPilot siempre diagnostica en vivo, con la clave propia del usuario; sin
  clave abre en Configuración para cargarla); un círculo que gira mientras trabaja la IA;
  avisos con el color de error del tema.
- **29 oct** — ojito para ver u ocultar la clave en su campo (oculta por defecto); APK y
  AAB de release firmados en `release/` (`./gradlew signedRelease`, con las claves de firma
  fuera del repositorio); el veredicto y el mapa nombran el tipo de componente en el idioma
  de la app.
- **31 oct** — modo Contributor con esfuerzo Máximo por defecto para Muse Spark, con un aviso
  antes del primer análisis (Estándar queda a un toque); resumen de privacidad en Acerca de;
  ahora el código decide cuándo se puede
  enchufar una placa; prompts más estrictos con lo que la foto no respalda.

## Probarla

**Requisitos:** un teléfono con Android 8.0 o superior (API 26+), conexión a internet y
una clave propia de la Meta Model API (dev.meta.ai). BoardPilot siempre diagnostica en
vivo: no tiene modo sin conexión.

**Instalar:** el APK firmado está en [`release/`](release/)
(`ar.com.mt0.boardpilot-<versión>.apk`): copiarlo al teléfono y abrirlo, o instalarlo con
`adb install -r`. También se puede compilar (abajo).

**Primer inicio:** sin clave cargada, la app abre en Configuración con el campo de la
clave primero. Pegar la clave de la Meta Model API: queda guardada solo en el
almacenamiento privado de la app en ese teléfono (se puede borrar desde Configuración,
ruedita).

**Diagnosticar:** *Nueva reparación* → foto → síntoma (y tipo de placa, o que lo detecte
Muse Spark) → *Iniciar diagnóstico* → *Mapear con SAM 3.1 en vivo* → *Generar hipótesis
(Muse Spark en vivo)* (la primera vez, aceptar el aviso de Contributor o elegir Estándar) → seguir las mediciones, una por turno, cada una con su compuerta
de seguridad.

## Compilar

Requiere JDK 17 y el Android SDK con la plataforma `android-37.0` y build-tools 37. No
hace falta Android Studio. Cadena: Gradle 9.6.1 (wrapper), Android Gradle Plugin 9.4.1,
Kotlin 2.3.21, Jetpack Compose Material 3; compile/target SDK 37, min SDK 26.

```bash
./gradlew testDebugUnitTest   # tests: máquina de estados, validación de respuestas, cálculo de fotos
./gradlew assembleDebug       # APK en app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Release firmado:** `./gradlew signedRelease` arma el APK y el AAB de release firmados con
tu clave y los copia a `release/` como `ar.com.mt0.boardpilot-<versión>.apk` y `.aab`. La
clave nunca va al repositorio: se pasan `boardpilotStoreFile`, `boardpilotStorePassword`,
`boardpilotKeyAlias` y `boardpilotKeyPassword` como propiedades de Gradle (por ejemplo en
`~/.gradle/gradle.properties`); sin ellas, la tarea se detiene antes de compilar. Para
firmar el AAB con otra clave de subida, como pide Google Play, se suman
`boardpilotBundleStoreFile`, `boardpilotBundleStorePassword`, `boardpilotBundleKeyAlias` y
`boardpilotBundleKeyPassword`.

Smoke tests contra la Meta Model API (leen la clave solo del entorno y nunca la imprimen):

```bash
MODEL_API_KEY=... python3 tools/smoke_api.py
MODEL_API_KEY=... python3 tools/smoke_sam.py IMAGEN "electrolytic capacitor"
MODEL_API_KEY=... python3 tools/smoke_map.py FOTO SALIDA   # mismo mapeo SAM que la app → candidatos.json
MODEL_API_KEY=... python3 tools/smoke_spark.py IMAGEN CANDIDATOS.json "síntoma" [ES|EN] [--plan] [--probes]
MODEL_API_KEY=... python3 tools/smoke_image.py IMAGEN "descripción del daño"
```

## Arquitectura

```
App Android (APK) — todo el estado en el dispositivo           Meta Model API (nube Meta)
Cámara/galería · recorte y giro · overlay de componentes  ── HTTPS ──►  SAM 3.1
Máquina de estados del diagnóstico + compuertas de seguridad             Muse Spark 1.3
Validación de respuestas (JSON estricto, ids anclados)                   Muse Image 1.0
Proyectos "Mis reparaciones" guardados localmente
```

- `app/src/main/java/ar/com/mt0/boardpilot/domain/` — lógica pura con tests:
  `DiagnosisEngine` (máquina de estados), `SparkOutputParser` / `SamOutputParser`
  (validación de respuestas), `SessionCodec` / `RepairProject` (persistencia),
  `BoardPlaybooks` (tipos de placa y sus playbooks), `BoardView` (geometría del mapa y la
  lupa), `PhotoGestures` / `PhotoLoading` (recorte, giro, EXIF, muestreo).
- `app/src/main/java/ar/com/mt0/boardpilot/api/` — `MetaSamClient`, `MetaSparkClient`,
  `MetaImageClient` (HTTPS directo, sin SDK; los errores pasajeros 429/5xx y de red se
  reintentan con espera creciente, `MetaApiRetry`).
- `app/src/main/java/ar/com/mt0/boardpilot/MainActivity.kt` — interfaz en Jetpack Compose.
- `tools/` — smoke tests en Python para cada modelo.

## Seguridad ante todo

BoardPilot guía; el técnico decide. Ninguna medición se registra sin confirmar su
compuerta de seguridad: equipo desenchufado y capacitores descargados para continuidad,
resistencia y diodo, y advertencia explícita de alta tensión antes de cualquier medición
con el equipo energizado. La compuerta la deriva el código del tipo de medición, nunca el
modelo, y nunca se guían mediciones en alterna.

El código también decide cuándo se puede enchufar una placa. Las mediciones sin tensión
siempre van primero, y las con tensión no se ofrecen si hay daño visible, si ya se midió
una falla sin tensión o mientras ningún chequeo sin tensión haya dado bien. La compuerta
con tensión indica el procedimiento: fijar las puntas con el equipo desenchufado, enchufar
sin tocar la placa, leer y desenchufar. El veredicto recuerda leer la pieza original antes
de comprar: los valores que sugiere el modelo nunca reemplazan lo que está impreso en ella.

## Privacidad y tu clave

Esta sección es la política de privacidad de BoardPilot; la app muestra un resumen en Acerca
de.

**Qué recoge BoardPilot:** nada. No tiene cuentas, ni servidor propio, ni analíticas, ni
publicidad. Proyectos, fotos y sesiones quedan en el almacenamiento privado de la app, y los
backups de la app están desactivados.

**Qué sale del teléfono:** fotos de placas, síntomas y resultados de mediciones, solo cuando
pedís un análisis y solo hacia la Meta Model API (`https://api.meta.ai/v1`), con la clave que
cargás en tu dispositivo. Meta los trata según sus
[términos](https://dev.meta.ai/legal/terms-of-service). La clave nunca se embebe en el APK ni
en este repositorio.

**Modo de Muse Spark:** el modo **Contributor** es el de por defecto: cuesta mucho menos, pero
puede tardar más del doble y Meta puede usar lo que envíes y lo que responda para entrenar sus
modelos, también fotos y textos anteriores de una reparación que sigas en este modo. Nada se envía
en este modo hasta que aceptes un aviso, que aparece antes del primer análisis. Ahí, o después en
Configuración, podés elegir el modo **Estándar**, donde Meta no usa este contenido para entrenar sus
modelos. En modo Contributor no podés enviar datos personales ni confidenciales (revisá que las
fotos no muestren nombres ni etiquetas de clientes), y conviene usarlo solo si las condiciones de
Meta lo permiten en tu país. SAM 3.1 y Muse Image no tienen variante Contributor.

## Limitaciones

- Pensada para placas de equipos de red con componentes visibles, de inserción o SMD
  grandes: fuentes conmutadas, placas de TV/monitor, inverters de motor y compresor,
  placas de control de electrodomésticos, amplificadores de audio y similares. Cada
  medición lleva su zona de riesgo (lado de red, baja tensión, alta tensión) y nunca se
  guían mediciones con tensión en alta tensión. Celulares, micro-soldadura (0402, BGA,
  microscopio) y placas lógicas densas quedan fuera.
- SAM necesita una foto razonablemente nítida y bien iluminada, tomada de frente.
- Sin esquemáticos ni boardviews: el diagnóstico trabaja con lo visible, las topologías
  típicas y las mediciones del técnico.
- La referencia de Muse Image es solo ilustrativa, nunca evidencia.

## Licencia

BoardPilot es software libre bajo la **GNU General Public License v3.0 o posterior**
(GPL-3.0-or-later). Texto completo en [`LICENSE`](LICENSE).

Copyright © 2026 Mauricio Tagliaferro.
