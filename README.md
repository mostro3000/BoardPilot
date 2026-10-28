# BoardPilot

> Versión en español: [LEERME.md](LEERME.md)

A multimodal AI copilot for repairing electronic boards (PCBs) at the **component level**.
The technician photographs the board and describes the symptom; BoardPilot maps the
components with **SAM 3.1**, ranks fault hypotheses and guides **real multimeter
measurements, one at a time**, with **Muse Spark**, and renders a reference view of the
repair with **Muse Image** — all through the **Meta Model API**, from a **native Android
app with no backend of its own**.

> Built for the Meta Global AI Developer Hackathon (Indie Developer track, *Push the
> Frontier* theme) during the build period (Oct 27 – Nov 4, 2026). Full design notes and
> build diary (in Spanish): [`docs/boardpilot-hackathon.md`](docs/boardpilot-hackathon.md).

## Why

Field technicians and independent repair shops (initial focus: Latin America) often
replace a whole board — expensive and sometimes impossible to source — because
diagnosing at the component level takes schematics, experience and time. The result is
costlier repairs and more e-waste.

BoardPilot is not a "magic photo that guesses the fault". It is a **verifiable,
multi-step diagnosis agent with the technician in control**: every verdict is backed by
measurements the technician actually took, and when the evidence is not enough it asks
for another measurement or photo instead of inventing an answer.

## How it works: the agent loop

1. **Intake** — a photo from the gallery or the camera (full resolution, upright
   according to its EXIF orientation, freely cropped and rotated) plus the symptom
   ("does not power on").
2. **Map (SAM 3.1)** — components are segmented by physical shape ("cylindrical
   component", "yellow rectangular block", …), filtered by confidence and de-duplicated,
   and drawn as an interactive overlay on the photo.
3. **Damage pre-scan (Muse Spark)** — a dedicated pass over the clean photo looks for
   burnt, blown or bulging parts. Damage that SAM did not box becomes a synthetic
   candidate (`DMG 1`) that hypotheses and measurements can point to.
4. **Hypotheses (Muse Spark)** — the annotated photo, the candidate list (JSON with
   stable ids and normalized boxes) and the symptom produce up to four ranked
   hypotheses, each with its confidence, rationale and the concrete fix if confirmed
   (part type, ratings, package, an example part number).
5. **Guided measurements** — one at a time: continuity, resistance, diode test or DC
   voltage, with what a normal vs. abnormal reading looks like. Every measurement is
   behind a **safety gate chosen by the code, never by the model**, and each result
   updates the hypotheses deterministically. A **visual guide** shows a magnified
   lens of the component next to the instructions, the meter setup (dial and
   VΩ/COM jacks) and, from Muse Spark, where the red and black probes go, validated
   against the component boxes and labeled as AI-suggested. The board map marks what
   to measure now, each hypothesis' % and the evidence so far (✓ ✗ ?).
6. **Re-plan (Muse Spark)** — if the measurements run out without a verdict, the agent
   asks for the next ones, passing the hypotheses and everything already measured with
   its result.
7. **Verdict and reference (Muse Image)** — the confirmed component, the suggested
   replacement and an AI-generated reference of the repaired board, always labeled as
   illustrative (it is never diagnostic evidence).
8. **Verification** — did the unit power on? The repair is closed or goes back to the
   verdict.

Model output is never trusted blindly: responses are strict JSON validated in a pure,
unit-tested domain layer that drops unknown components and invalid measurement kinds,
rejects AC/mains measurements, resolves designators to stable ids and keeps internal
ids out of the texts the technician reads.

## Meta models in the app

| Model | Endpoint | Role |
|---|---|---|
| `sam-3.1` | `POST /v1/responses` (streaming) | Component segmentation → overlay and candidate list |
| `muse-spark-1.3` | `POST /v1/chat/completions` (multimodal) | Damage pre-scan, ranked hypotheses with fixes, measurement plans and re-planning, probe placement |
| `muse-image-1.0` | `POST /v1/responses` | Reference image of the repaired board |

Each model is part of the core loop: without SAM there is no component map, without
Muse Spark there is no diagnosis, without Muse Image there is no repair reference. The
reasoning effort for Muse Spark is configurable in Settings (Auto by default).

## Built during the event (Oct 27 – Nov 4, 2026)

All app code in this repository was written during the build period (planning and
documentation only before it); commit dates are the evidence.

- **Oct 27** — Meta Model API smoke tests; Android foundation (API 37, Jetpack
  Compose) with the diagnosis state machine, safety gates and the offline demo case;
  Spanish/English UI; settings with the user's own key; SAM 3.1 protocol and first live
  mapping.
- **Oct 28** — SAM shape-descriptor strategy validated on a real switch-mode power
  supply; session persistence and camera permissions; live Muse Spark diagnosis grounded
  in the SAM candidates; visible-damage pre-scan; configurable reasoning effort; Muse
  Image repair reference; "My repairs" saved projects; free crop/rotate and pinch zoom;
  measurement re-planning with concrete fixes; full-resolution camera capture with EXIF
  orientation; visual measurement guide (lens, meter setup, evidence map and
  AI-suggested probe points).

The day-by-day diary with measurements, decisions and live test results is in
[`docs/boardpilot-hackathon.md`](docs/boardpilot-hackathon.md) (Spanish).

## Try it

**Requirements:** an Android phone with Android 8.0 or newer (API 26+). Internet is only
needed for live mode.

**Install:** download the APK from this repository's Releases page when available, or
build it from source (below) and install it with `adb install -r app-debug.apk`.

**Demo mode (no key needed):** on first launch choose *Continue without a key (demo
mode)*. Then *New repair* → *Start demo diagnosis* walks the whole flow offline with the
golden case "switch-mode power supply that does not power on": component map,
hypotheses, guided measurements with their safety gates, verdict, repair and
verification.

**Live mode (bring your own key):** open Settings (gear icon) and paste your Meta Model
API key. Then *New repair* → photo → symptom → *Start diagnosis* → *Map with live SAM
3.1* → *Generate hypotheses (live Muse Spark)* → follow the measurements.

## Build from source

Requirements: JDK 17 and the Android SDK with platform `android-37.0` and build-tools
37. No Android Studio needed. Toolchain: Gradle 9.6.1 (wrapper), Android Gradle Plugin
9.4.1, Kotlin 2.3.21, Jetpack Compose Material 3; compile/target SDK 37, min SDK 26.

```bash
./gradlew testDebugUnitTest   # unit tests: state machine, model-output validation, photo math
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Smoke tests against the Meta Model API (they read the key from the environment only and
never print it):

```bash
MODEL_API_KEY=... python3 tools/smoke_api.py
MODEL_API_KEY=... python3 tools/smoke_sam.py IMAGE "electrolytic capacitor"
MODEL_API_KEY=... python3 tools/smoke_map.py PHOTO OUT_DIR   # same SAM mapping as the app → candidatos.json
MODEL_API_KEY=... python3 tools/smoke_spark.py IMAGE CANDIDATES.json "symptom" [ES|EN] [--plan]
MODEL_API_KEY=... python3 tools/smoke_image.py IMAGE "damage description"
```

## Architecture

```
Android app (APK) — all state on the device               Meta Model API (Meta cloud)
Camera/gallery · crop & rotate · component overlay   ── HTTPS ──►  SAM 3.1
Diagnosis state machine + safety gates                              Muse Spark 1.3
Model-output validation (strict JSON, grounded ids)                 Muse Image 1.0
"My repairs" projects stored locally · offline demo
```

- `app/src/main/java/ar/com/mt0/boardpilot/domain/` — pure, unit-tested logic:
  `DiagnosisEngine` (state machine), `SparkOutputParser` / `SamOutputParser` (output
  validation), `SessionCodec` / `RepairProject` (persistence), `PhotoGestures` /
  `PhotoLoading` (crop, rotation, EXIF, sampling).
- `app/src/main/java/ar/com/mt0/boardpilot/api/` — `MetaSamClient`, `MetaSparkClient`,
  `MetaImageClient` (plain HTTPS, no SDK).
- `app/src/main/java/ar/com/mt0/boardpilot/MainActivity.kt` — Jetpack Compose UI.
- `tools/` — Python smoke tests for each model.

## Safety first

BoardPilot guides; the technician decides. No measurement is recorded without
confirming its safety gate: the unit unplugged and capacitors discharged for continuity,
resistance and diode tests, and an explicit high-voltage warning before any measurement
on a powered unit. The gate is derived from the measurement kind by the code, never by
the model, and AC/mains measurements are never guided.

## Privacy and your key

No accounts and no backend of ours: projects, photos and sessions stay in the app's
private storage, and app backups are disabled. The only network calls go from the phone
to `https://api.meta.ai/v1` with the key each user loads on their own device. No key is
ever embedded in the APK or in this repository.

## Limitations

- Built for boards with visible, through-hole or larger SMD parts: switch-mode power
  supplies, TV/monitor boards and appliance inverter boards. Phones and micro-soldering
  (0402, BGA, microscope work) are out of scope.
- SAM needs a reasonably sharp, well-lit photo of the board, taken straight on.
- No schematics or boardviews: the diagnosis works from what is visible, typical
  topologies and the technician's measurements.
- The Muse Image reference is illustrative only, never evidence.

## License

BoardPilot is free software under the **GNU General Public License v3.0 or later**
(GPL-3.0-or-later). Full text in [`LICENSE`](LICENSE).

Copyright © 2026 Mauricio Tagliaferro.
