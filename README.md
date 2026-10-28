# BoardPilot

Copiloto multimodal de IA para reparar placas electrónicas (PCB) a **nivel componente**.
El técnico fotografía la placa y describe el síntoma; BoardPilot segmenta y etiqueta los
componentes con **SAM 3.1**, razona e interactúa con mediciones reales de multímetro usando
**Muse Spark**, y genera la vista de referencia de la reparación con **Muse Image** —
todo vía **Meta Model API**, desde una **app Android nativa sin servidor propio**.

> Proyecto del Meta Global AI Developer Hackathon (track Indie, tema Push the Frontier),
> construido durante el evento (27 oct – 4 nov). Documentación completa en
> [`docs/boardpilot-hackathon.md`](docs/boardpilot-hackathon.md).

## Estado

- ✅ Fase 0 (27 oct): conexión a Meta Model API verificada (`tools/smoke_api.py`):
  modelos visibles `muse-spark-1.3`, `sam-3.1`, `muse-image-1.0`, entre otros.
- ✅ Fase 1 (27 oct): fundación Android (API 37) con **modo demo offline** completo:
  máquina de estados del diagnóstico con compuertas de seguridad eléctrica y caso dorado
  SMPS (fuente que no enciende → MOSFET en corto), con tests unitarios.
- ✅ Fase 2 (27–28 oct): foto real (cámara/galería) + mapeo **SAM 3.1 en vivo** con
  descriptores visuales validados sobre una SMPS real; overlay interactivo; persistencia
  de sesión/foto; ES/EN y tema automático/claro/oscuro.
- ✅ Fase 3 (28 oct): **Muse Spark en vivo** — la foto anotada con las cajas SAM y el
  síntoma producen hipótesis rankeadas y mediciones guiadas en JSON estricto, validadas
  en el dominio (la compuerta de seguridad la fija el código, no el modelo). Circuito
  completo verificado en un S25 con una fuente real. Smokes: `tools/smoke_sam.py`,
  `tools/smoke_spark.py`.
- ✅ Mis reparaciones (28 oct): proyectos guardados como chats — cada placa con su foto,
  diagnóstico y referencia; estados En curso / En espera de repuesto / Reparada; borrar
  deslizando hacia un costado (con Deshacer).
- ⏳ Siguiente: caso dorado con falla real confirmada por medición, APK release y video.

## Seguridad ante todo

BoardPilot guía; el técnico decide. Ninguna medición se registra sin confirmar su
compuerta de seguridad (equipo desenergizado para continuidad/resistencia, advertencias
explícitas ante tensión). Si la evidencia no alcanza, la app pide otra medición en vez de
inventar un diagnóstico.

## Compilar y probar

Requiere JDK 17 y Android SDK con plataforma API 37.

```bash
./gradlew testDebugUnitTest   # tests de la máquina de estados
./gradlew assembleDebug       # APK debug en app/build/outputs/apk/debug/
```

Smoke test de la API (lee la clave solo del entorno, nunca la imprime):

```bash
MODEL_API_KEY=... python3 tools/smoke_api.py
```

## Clave de API (BYOK)

Sin servidor propio: la app llama directo a `https://api.meta.ai/v1` con **la clave que
cada usuario carga en su dispositivo**. La clave nunca se embebe en el APK ni en este
repo. Sin clave, la app corre el modo demo offline con casos precargados.

## Licencia

BoardPilot es software libre bajo la **GNU General Public License v3.0 o posterior**
(GPL-3.0-or-later). Texto completo en [`LICENSE`](LICENSE).

Copyright © 2026 Mauricio Tagliaferro.
