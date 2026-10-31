#!/usr/bin/env python3
"""Smoke test de Muse Spark (Fase 3) para BoardPilot.

Anota la foto con las cajas candidatas de SAM (JSON) y pide a muse-spark-1.3
hipotesis + mediciones en JSON estricto por /chat/completions multimodal.
Valida que la respuesta parsee, referencie solo ids candidatos y traiga
mediciones con tipos validos. Lee la clave solo de MODEL_API_KEY.

Uso:
    MODEL_API_KEY=... python3 tools/smoke_spark.py IMAGEN CANDIDATOS.json "sintoma" [ES|EN] [--plan] [--probes]

Con --plan, despues del diagnostico simula que todas sus mediciones dieron
normal y pide las siguientes con el mismo prompt que MetaSparkClient.planMeasurements.
Con --probes pide donde van las puntas de las mediciones del diagnostico (mismo
prompt que MetaSparkClient.probeGuides), las valida como la app y las dibuja en
SMOKE_OUT/boardpilot_probes.jpg (por defecto /tmp).

CANDIDATOS.json: [{"id","designator","kind","x","y","w","h"}] normalizado 0..1.
La foto anotada queda en /tmp/boardpilot_annotated.jpg y el dano del pre-escaneo, ya
unido, en boardpilot_damage.json.
"""

import base64
import io
import json
import re
import os
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image, ImageDraw, ImageFont

BASE = os.environ.get("META_API_BASE", "https://api.meta.ai/v1").rstrip("/")
MODEL = os.environ.get("MUSE_SPARK_MODEL", "muse-spark-1.3")
KEY = os.environ.get("MODEL_API_KEY", "")
# Carpeta de las imagenes del smoke (anotada y puntas); /tmp por defecto.
OUT_DIR = os.environ.get("SMOKE_OUT", "/tmp")
KINDS = {"CONTINUITY", "RESISTANCE", "VOLTAGE_DC", "DIODE"}

DAMAGE_PROMPT = """Look at this electronic board photo and scan the ENTIRE board for visible physical damage ONLY: burnt, charred or blackened components or PCB areas; bulging, leaking or exploded capacitors; cracked or melted parts; heavy corrosion or burnt traces.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"damages":[{"description":"...","x":0.0,"y":0.0,"w":0.0,"h":0.0}]}
Use tight normalized boxes (0..1, origin top-left) around each damaged component or area, at most 3, worst first. Descriptions in LANGUAGE. If there is no visible damage, return {"damages":[]}."""

SYSTEM = """You are BoardPilot, an expert electronics repair copilot for component-level PCB diagnosis.
You receive TWO images of the same board: first the clean photo, second the same photo annotated with candidate regions from a segmentation model; plus the candidate list as JSON (normalized 0..1 coordinates, origin top-left) and a symptom. Candidates are visual regions and may be wrong or incomplete; use the clean photo (labels can hide detail) to interpret what each region really is.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"hypotheses":[{"id":"h1","title":"...","componentId":"...","confidence":0.4,"rationale":"...","fix":"..."}],"measurements":[{"id":"m1","componentId":"...","kind":"CONTINUITY|RESISTANCE|VOLTAGE_DC|DIODE","instruction":"...","normal":"...","abnormal":"...","targetHypothesisId":"h1","zone":"PRIMARY|SECONDARY|HIGH_VOLTAGE"}],"damages":[{"componentId":"...","damage":"..."}],"unmappedDamage":{"description":"...","x":0.0,"y":0.0,"w":0.0,"h":0.0},"needMoreEvidence":"","boardType":"..."}
Rules:
- FIRST scan the ENTIRE photo for visible damage: burnt/charred/blackened components or PCB areas, bulging or leaking capacitors, cracked or melted parts, heavy corrosion. For every candidate that is visibly damaged add a "damages" entry describing it. A visibly burnt or exploded component is the strongest evidence there is: rank its hypothesis FIRST with the highest confidence and target it with the first measurement.
- Claim only what the clean photo really shows. Blur, shadows, reflections, glue, flux, dust, dark packages and the camera angle are not damage. Never write that a part is burnt, deformed, cracked or bulging unless the candidate list marks it with visibleDamage or you report it in "damages" or "unmappedDamage". A hypothesis without a damage mark is a suspicion from the symptom: say that in its rationale, do not say the part looks undamaged (a photo cannot rule it out), and keep its confidence at 0.6 or less.
- If visible damage has NO candidate box around it, report it in "unmappedDamage" with a tight normalized box (x,y,w,h in 0..1) around the damaged component/area; in that case you may reference that region as componentId "dmg1-spark" in hypotheses and measurements. If there is no unmapped damage, set "unmappedDamage" to null.
- componentId must be the candidate "id" value exactly as in the list (for example "cyl2-sam1"), plus "dmg1-spark" when you reported unmappedDamage. Designators (for example "CYL 2") are only for naming components inside texts and only tell the rough shape of the box (CYL a cylinder, RECT a rectangular block, not a rectifier): work out what each part really is from the photo. Never invent silkscreen reference designators.
BOARD_RULES
- For each hypothesis, "fix" says what to replace or repair if it is confirmed, written as a direct action without restating the condition (for example "Replace with ..."). Give the part type and quote every marking you can read on that part in the photo, with the value or rating it states. Only when no marking is readable, give no numbers: say to use the same value and an equal or higher voltage rating, read from the original part or a schematic. Never suggest a lower rating than the original, and name a package or part number only if you can see it.
- Whenever you give hypotheses, also give between 1 and 5 measurements, ordered by diagnostic value and safety. Each targets exactly one hypothesis by its id and one component, uses a kind that is exactly CONTINUITY, RESISTANCE, VOLTAGE_DC or DIODE (never AC or mains measurements), says where to put the probes on this board, and describes what normal vs abnormal looks like on the meter.
- zone says where on the board the measurement is: PRIMARY (mains side: everything before the isolation transformer or optocoupler, the DC bus, and any board that is not isolated from mains), SECONDARY (the isolated low-voltage side) or HIGH_VOLTAGE (CCFL or backlight inverters, microwave or CRT high voltage, flash circuits, any part marked in kV). Never give a VOLTAGE_DC measurement in a HIGH_VOLTAGE zone. If unsure, use PRIMARY.
- Safety: continuity, resistance and diode measurements are done only with the unit unplugged and capacitors discharged, and they all go before any VOLTAGE_DC. Give a VOLTAGE_DC only when de-energized checks cannot tell the hypotheses apart, never as the only measurement and never if the photo shows damage. Use the SECONDARY zone whenever it can answer; PRIMARY only when nothing else can. Its instruction starts with the condition to plug the unit in (the de-energized checks found no short, naming what was checked) and says to attach the probes while unplugged, plug in without touching the board, read and unplug.
- If the photo or candidates are insufficient, return empty arrays and explain in needMoreEvidence what photo/measurement you need.
- Faults that a multimeter cannot confirm or fix (firmware, BGA chips, the display panel itself) are not hypotheses: if they are likely, say so in needMoreEvidence with the next step (for example reflashing or replacing the module), even when you also give hypotheses.
- All user-visible strings (title, rationale, fix, instruction, normal, abnormal, damage, description, needMoreEvidence) in LANGUAGE. They never contain ids (h1, m1, cyl2-sam1): name hypotheses by what they say and components by their designator."""


# Guias por tipo de placa: el mismo archivo que lee la app (BoardPlaybooks).
PLAYBOOKS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main",
                                        "resources", "playbooks.json"), encoding="utf-8"))
BOARD_TYPES = list(PLAYBOOKS["types"])


def board_section(board: str) -> str:
    """Como BoardPlaybooks.promptSection: el tipo elegido o detectarlo primero."""
    if board == "UNKNOWN":
        return ('- FIRST identify what kind of board this is from the photo and report "boardType" as exactly '
                "one of " + ", ".join(BOARD_TYPES) + ". Then give at most 4 hypotheses ranked by likelihood "
                "with the typical failures of THAT kind of board for the symptom, adapted to what is actually "
                "visible:\n" + "\n".join(f"  - {t}: {PLAYBOOKS['types'][t]}" for t in BOARD_TYPES))
    return (f'- The technician says this board is {board}: report "boardType" as {board} and give at most 4 '
            "hypotheses ranked by likelihood with its typical failures for the symptom, adapted to what is "
            f"actually visible. {PLAYBOOKS['types'][board]}")


def board_arg() -> str:
    """--board=TIPO (por defecto UNKNOWN: detectar)."""
    raw = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--board=")), "UNKNOWN").upper()
    return raw if raw in BOARD_TYPES or raw == "UNKNOWN" else "UNKNOWN"


# Mismo prompt que MetaSparkClient.PLAN_PROMPT (mantener en sync).
PLAN = """You are BoardPilot, an expert electronics repair copilot guiding a technician with a multimeter, one measurement at a time.
You receive the clean photo of a board, its candidate components (JSON, normalized 0..1 boxes, origin top-left), the symptom, the current fault hypotheses (with ids and confidence) and the measurements already done with their results. Propose the NEXT measurements that best confirm or rule out the hypotheses, most informative first.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"measurements":[{"id":"m1","componentId":"...","kind":"CONTINUITY|RESISTANCE|VOLTAGE_DC|DIODE","instruction":"...","normal":"...","abnormal":"...","targetHypothesisId":"h1","zone":"PRIMARY|SECONDARY|HIGH_VOLTAGE"}],"needMoreEvidence":""}
Rules:
- Between 1 and 3 measurements and never repeat one already done. Each targets one hypothesis id from the list and one componentId exactly as in the candidate list.
- kind is exactly CONTINUITY, RESISTANCE, VOLTAGE_DC or DIODE (never AC or mains measurements). The instruction says where to put the probes on this board; normal and abnormal say what the meter shows.
- zone says where on the board the measurement is: PRIMARY (mains side: everything before the isolation transformer or optocoupler, the DC bus, and any board that is not isolated from mains), SECONDARY (the isolated low-voltage side) or HIGH_VOLTAGE (CCFL or backlight inverters, microwave or CRT high voltage, flash circuits, any part marked in kV). Never give a VOLTAGE_DC measurement in a HIGH_VOLTAGE zone. If unsure, use PRIMARY.
- Safety: continuity, resistance and diode measurements only with the unit unplugged and capacitors discharged, and they all go before any VOLTAGE_DC. Give a VOLTAGE_DC only when de-energized checks cannot tell the hypotheses apart, and never if a candidate has visibleDamage, a measurement already done came out ABNORMAL or none came out NORMAL with the unit unplugged yet: then the board stays unplugged, so ask for de-energized checks only. Use the SECONDARY zone whenever it can answer; PRIMARY only when nothing else can. Its instruction starts with the condition to plug the unit in (the de-energized checks found no short, naming what was checked) and says to attach the probes while unplugged, plug in without touching the board, read and unplug.
- If no further measurement can tell the hypotheses apart, return an empty list and say in needMoreEvidence what photo or information you need.
- All user-visible strings (instruction, normal, abnormal, needMoreEvidence) in LANGUAGE. They never contain ids (h1, m1, cyl2-sam1): name hypotheses by what they say and components by their designator."""


ID_IN_TEXT = re.compile(r"(?<![\w-])(h\d+[a-z]?|m\d+[a-z]?)(?![\w-])")


def text_warnings(items: list, fields: tuple, candidate_ids: set) -> list:
    """Ids internos o condicion repetida en textos para el tecnico (la app los limpia; aca se cuentan)."""
    warnings = []
    for item in items:
        if not isinstance(item, dict):
            continue
        for field in fields:
            text = str(item.get(field) or "")
            low = text.lower()
            found = ID_IN_TEXT.findall(text) + [c for c in candidate_ids if c.lower() in low]
            if found:
                warnings.append(f"id interno en {field}: {sorted(set(found))} <- {text[:100]!r}")
            if field == "fix" and re.match(r"\s*(si se confirma|if confirmed)\b", low):
                warnings.append(f"fix repite la condicion: {text[:80]!r}")
    return warnings

# Mismo prompt que MetaSparkClient.PROBE_PROMPT (mantener en sync).
PROBE = """You are BoardPilot, an expert electronics repair copilot. You receive a clean photo of a circuit board, the same photo annotated with candidate component boxes, the candidate list (JSON, normalized 0..1 boxes, origin top-left, x to the right, y down) and the measurements the technician will take. For each measurement say exactly where to place the red and the black multimeter probes on THIS board.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"probes":[{"measurementId":"m1","red":{"componentId":"...","x":0.0,"y":0.0,"terminal":"..."},"black":{"componentId":"...","x":0.0,"y":0.0,"terminal":"..."},"visible":true,"note":""}]}
Rules:
- x and y are normalized coordinates on the clean photo of the exact point to touch: the metal lead or pin where it leaves the body, or its solder joint; never the plastic body or the component center.
- componentId is the candidate "id" exactly as in the list of the part that probe touches: usually the measured component, or another candidate when the probe goes to a reference point such as the negative of the bulk capacitor (primary ground).
- terminal names that point briefly (anode, cathode, +, -, pin 1, drain, ground...). If you cannot see the polarity or pin-1 mark in the photo, append "?" to the terminal name.
- Respect polarity: DIODE red on anode and black on cathode; VOLTAGE_DC red on + and black on - (ground).
- For parts with more than two terminals (transistors, MOSFETs, ICs, optocouplers, bridges) give points only when the photo shows which pins they are (silkscreen, pin-1 mark, package orientation) and name them by function (drain, source, gate, pin 1, +, -, ~); otherwise set visible to false and say in note how to identify the pins (the pinout of the part number printed on it).
- If the terminals to touch are not visible in this photo (leads hidden under the body or only reachable on the solder side), set visible to false, omit red and black, and say in note where to touch instead.
- terminal and note in LANGUAGE. Never put ids in them: name components by their designator."""


def select_candidates(candidates: list, must: set = frozenset(), max_n: int = 60) -> list:
    """Como SparkCandidates.select: en juego, con dano y los mas grandes, en el orden original."""
    if len(candidates) <= max_n:
        return candidates
    ranked = sorted(candidates, key=lambda c: (c["id"] in must, bool(c.get("visibleDamage")),
                                               c["w"] * c["h"]), reverse=True)
    keep = {c["id"] for c in ranked[:max_n]}
    return [c for c in candidates if c["id"] in keep]


def annotate(image_path: str, candidates: list[dict]) -> bytes:
    im = Image.open(image_path).convert("RGB")
    w, h = im.size
    d = ImageDraw.Draw(im)
    lw = max(2, w // 200)
    for c in candidates:
        x0, y0 = c["x"] * w, c["y"] * h
        x1, y1 = (c["x"] + c["w"]) * w, (c["y"] + c["h"]) * h
        d.rectangle([x0, y0, x1, y1], outline=(255, 179, 0), width=lw)
        label = c["designator"]
        d.rectangle([x0, max(0, y0 - 16), x0 + 8 + 7 * len(label), y0],
                    fill=(255, 179, 0))
        d.text((x0 + 3, max(0, y0 - 13)), label, fill=(0, 0, 0))
    im.save(os.path.join(OUT_DIR, "boardpilot_annotated.jpg"), quality=88)
    buf = io.BytesIO()
    im.save(buf, format="JPEG", quality=88)
    return buf.getvalue()


def chat(body: dict) -> tuple[str, dict]:
    effort = os.environ.get("SPARK_EFFORT", "xhigh")
    if effort != "auto":
        body["reasoning_effort"] = effort
    req = urllib.request.Request(
        f"{BASE}/chat/completions", data=json.dumps(body).encode(), method="POST",
        headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=240) as resp:
        data = json.loads(resp.read().decode() or "{}")
    choice = (data.get("choices") or [{}])[0]
    return (choice.get("message") or {}).get("content") or "", data.get("usage") or {}


def parse_obj(text: str) -> dict:
    text = text.strip().strip("`")
    return json.loads(text[text.find("{"):text.rfind("}") + 1])


# Como SparkDamageParser.SCAN_SECTORS: 2x2 con solape, ampliados hasta SECTOR_SIDE.
SCAN_SECTORS = [(0.0, 0.0, 0.6, 0.6), (0.4, 0.0, 0.6, 0.6), (0.0, 0.4, 0.6, 0.6), (0.4, 0.4, 0.6, 0.6)]
SECTOR_SIDE = 1024


def sector_url(image_path: str, sector: tuple) -> str:
    im = Image.open(image_path).convert("RGB")
    sx, sy, sw, sh = sector
    x, y = int(sx * im.width), int(sy * im.height)
    crop = im.crop((x, y, x + int(sw * im.width), y + int(sh * im.height)))
    scale = SECTOR_SIDE / max(crop.size)
    if scale > 1:
        crop = crop.resize((round(crop.width * scale), round(crop.height * scale)), Image.LANCZOS)
    buf = io.BytesIO()
    crop.save(buf, format="JPEG", quality=85)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode("ascii")


def scan_pass(url: str, language: str, sector: tuple | None = None) -> list[dict]:
    """Una pasada del pre-escaneo; si falla, sin hallazgos. Cajas en coordenadas de la foto."""
    try:
        text, usage = chat({
            "model": MODEL, "max_tokens": 6000, "temperature": 0.2,
            "messages": [{"role": "user", "content": [
                {"type": "text", "text": DAMAGE_PROMPT.replace("LANGUAGE", language)},
                {"type": "image_url", "image_url": {"url": url}},
            ]}],
        })
        print("usage pre-escaneo:", json.dumps(usage))
        found = [f for f in parse_obj(text).get("damages") or []
                 if str(f.get("description") or "").strip()][:3]
        boxes = [tuple(float(f[k]) for k in ("x", "y", "w", "h")) for f in found]
    except Exception as exc:
        print("pasada del pre-escaneo fallo:", exc)
        return []
    if sector:
        sx, sy, sw, sh = sector
        boxes = [(sx + x * sw, sy + y * sh, w * sw, h * sh) for x, y, w, h in boxes]
    return [{"description": f["description"], "x": b[0], "y": b[1], "w": b[2], "h": b[3]}
            for f, b in zip(found, boxes)]


def merge_findings(passes: list[list[dict]]) -> list[dict]:
    """Como SparkDamageParser.merge: solapados = mismo dano, caja mayor, los mas vistos primero."""
    def area(f):
        return f["w"] * f["h"]

    def same(a, b):
        w = min(a["x"] + a["w"], b["x"] + b["w"]) - max(a["x"], b["x"])
        h = min(a["y"] + a["h"], b["y"] + b["h"]) - max(a["y"], b["y"])
        return w > 0 and h > 0 and w * h >= 0.5 * min(area(a), area(b))

    groups = []
    for found in (f for p in passes for f in p):
        group = next((g for g in groups if same(g["finding"], found)), None)
        if group is None:
            groups.append({"finding": dict(found), "seen": 1})
        else:
            group["seen"] += 1
            if area(found) > area(group["finding"]):
                group["finding"].update(x=found["x"], y=found["y"], w=found["w"], h=found["h"])
    groups.sort(key=lambda g: -g["seen"])
    return [g["finding"] for g in groups[:3]]


def assign_damage(findings: list[dict], candidates: list[dict]) -> list[str]:
    """Como SparkDamageParser.assign: centro dentro de candidato o DMG nuevo."""
    summary = []
    n = 1
    for f in findings[:3]:
        desc = str(f.get("description") or "").strip()
        try:
            x, y, w, h = (float(f[k]) for k in ("x", "y", "w", "h"))
        except Exception:
            continue
        if not desc:
            continue
        cx, cy = x + w / 2, y + h / 2
        hosts = [c for c in candidates
                 if c["x"] <= cx <= c["x"] + c["w"] and c["y"] <= cy <= c["y"] + c["h"]]
        if hosts:
            host = min(hosts, key=lambda c: c["w"] * c["h"])
            host.setdefault("visibleDamage", desc)
            summary.append(f"{host['designator']}: {desc}")
        else:
            cid = f"dmg{n}-spark"
            candidates.append({"id": cid, "designator": f"DMG {n}", "kind": "OTHER",
                               "x": x, "y": y, "w": w, "h": h, "visibleDamage": desc})
            summary.append(f"DMG {n}: {desc}")
            n += 1
    return summary


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) < 3:
        print('Uso: smoke_spark.py IMAGEN CANDIDATOS.json "sintoma" [ES|EN] [--plan] [--probes] [--board=TIPO]')
        return 2
    if not KEY:
        print("ERROR: falta MODEL_API_KEY en el entorno.")
        return 2
    image_path, cand_path, symptom = args[0], args[1], args[2]
    lang = (args[3] if len(args) > 3 else "ES").upper()
    language = "Spanish" if lang == "ES" else "English"
    candidates = json.load(open(cand_path))
    clean_url = "data:image/jpeg;base64," + base64.b64encode(
        open(image_path, "rb").read()).decode("ascii")

    # 1) Pre-escaneo de dano sobre la foto limpia: entera y sectores, en paralelo
    print("pre-escaneo de dano...")
    with ThreadPoolExecutor(1 + len(SCAN_SECTORS)) as pool:
        passes = [pool.submit(scan_pass, clean_url, language)]
        passes += [pool.submit(scan_pass, sector_url(image_path, s), language, s) for s in SCAN_SECTORS]
        findings = merge_findings([p.result() for p in passes])
    json.dump(findings, open(os.path.join(OUT_DIR, "boardpilot_damage.json"), "w"), ensure_ascii=False, indent=1)
    summary = assign_damage(findings, candidates)
    print("dano asignado:", summary)

    # 2) Diagnostico con foto limpia + anotada (ya incluye los DMG)
    shown = select_candidates(candidates)
    if len(shown) < len(candidates):
        print(f"candidatos para Spark: {len(shown)} de {len(candidates)} (tope como la app)")
    jpeg = annotate(image_path, shown)
    data_url = "data:image/jpeg;base64," + base64.b64encode(jpeg).decode("ascii")
    prescan = ("\nVisible damage found by a dedicated pre-scan (already included "
               "as candidates with visibleDamage): " + "; ".join(summary) + "\n"
               if summary else "")
    user_text = (
        f"Symptom: {symptom}\n{prescan}"
        f"Candidate components from segmentation (kind is a rough visual guess):\n"
        f"{json.dumps(shown, ensure_ascii=False)}"
    )
    body = {
        "model": MODEL,
        "max_tokens": int(os.environ.get("SPARK_MAX_TOKENS", "16000")),
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": SYSTEM.replace("BOARD_RULES", board_section(board_arg()))
                .replace("LANGUAGE", language)},
            {"role": "user", "content": [
                {"type": "text", "text": user_text},
                {"type": "image_url", "image_url": {"url": clean_url}},
                {"type": "image_url", "image_url": {"url": data_url}},
            ]},
        ],
    }
    print(f"POST {BASE}/chat/completions model={MODEL} candidatos={len(candidates)} lang={lang}")
    try:
        content, usage = chat(body)
    except urllib.error.HTTPError as e:
        print("status:", e.code)
        print("error:", (e.read().decode(errors="replace") or "")[:600])
        return 1
    print("usage diagnostico:", json.dumps(usage))
    print(f"respuesta: {len(content)} caracteres")
    print(content[:2500])

    try:
        parsed = parse_obj(content)
    except Exception as exc:
        print("RESULTADO: JSON invalido:", exc)
        return 1
    valid_ids = {c["id"] for c in candidates}
    unmapped = parsed.get("unmappedDamage")
    if isinstance(unmapped, dict) and unmapped.get("description"):
        valid_ids.add("dmg1-spark")
        print("unmappedDamage:", json.dumps(unmapped, ensure_ascii=False)[:300])
    print("damages:", json.dumps(parsed.get("damages"), ensure_ascii=False)[:400])
    hyps = parsed.get("hypotheses") or []
    meas = parsed.get("measurements") or []
    hyp_ids = {h.get("id") for h in hyps if isinstance(h, dict)}
    problems = []
    for hpos in hyps:
        if hpos.get("componentId") not in valid_ids:
            problems.append(f"hipotesis con componentId fuera de lista: {hpos}")
    for m in meas:
        if m.get("componentId") not in valid_ids:
            problems.append(f"medicion con componentId fuera de lista: {m}")
        if m.get("kind") not in KINDS:
            problems.append(f"medicion con kind invalido: {m}")
        if m.get("targetHypothesisId") not in hyp_ids:
            problems.append(f"medicion sin hipotesis destino valida: {m}")
    print(f"tipo de placa: elegido={board_arg()} detectado={parsed.get('boardType')}")
    for m in meas:
        print(f"MED {m.get('kind')} {m.get('componentId')} zona={m.get('zone')}: {str(m.get('instruction'))[:90]}")
        if m.get("kind") == "VOLTAGE_DC" and str(m.get("zone")).upper() == "HIGH_VOLTAGE":
            problems.append(f"medicion con tension en alta tension (la app la descarta): {m.get('id')}")
    print(f"hipotesis={len(hyps)} mediciones={len(meas)} "
          f"needMoreEvidence={str(parsed.get('needMoreEvidence'))[:120]!r}")
    for hpos in hyps:
        print(f"HYP {hpos.get('componentId')} {hpos.get('confidence')} "
              f"{str(hpos.get('title'))[:80]} | fix: {str(hpos.get('fix'))[:110]}")
    warns = text_warnings(hyps, ("title", "rationale", "fix"), valid_ids) + \
        text_warnings(meas, ("instruction", "normal", "abnormal"), valid_ids)
    for w in warns:
        print("AVISO:", w)
    print("textos sin ids ni condicion repetida:", "si" if not warns else f"NO ({len(warns)})")
    for p in problems:
        print("PROBLEMA:", p)
    ok = bool(hyps) and not problems
    print("RESULTADO:", "OK" if ok else "revisar (arriba)")
    if "--plan" in sys.argv and hyps:
        ok = plan_step(clean_url, candidates, valid_ids, symptom, language, hyps, meas) and ok
    if "--probes" in sys.argv and meas:
        ok = probe_step(image_path, clean_url, data_url, candidates, language, meas) and ok
    return 0 if ok else 1


def probe_step(image_path, clean_url, annotated_url, candidates, language, meas) -> bool:
    """Donde van las puntas; validacion como SparkOutputParser.parseProbes."""
    measurements = [{"id": m.get("id"), "componentId": m.get("componentId"), "kind": m.get("kind"),
                     "instruction": m.get("instruction")} for m in meas]
    shown = select_candidates(candidates, {m.get("componentId") for m in meas})
    annotated_url = "data:image/jpeg;base64," + base64.b64encode(annotate(image_path, shown)).decode("ascii")
    user_text = (f"Candidate components:\n{json.dumps(shown, ensure_ascii=False)}\n"
                 f"Measurements:\n{json.dumps(measurements, ensure_ascii=False)}")
    body = {
        "model": MODEL,
        "max_tokens": 12000,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": PROBE.replace("LANGUAGE", language)},
            {"role": "user", "content": [
                {"type": "text", "text": user_text},
                {"type": "image_url", "image_url": {"url": clean_url}},
                {"type": "image_url", "image_url": {"url": annotated_url}},
            ]},
        ],
    }
    print("puntas de las mediciones...")
    content, usage = chat(body)
    print("usage puntas:", json.dumps(usage))
    try:
        probes = parse_obj(content).get("probes") or []
    except ValueError:
        # Como la app: sin puntas sugeridas (alli queda el boton para reintentar).
        print(f"RESPUESTA VACIA O INVALIDA en puntas ({len(content)} caracteres)")
        print("RESULTADO PUNTAS: revisar (arriba)")
        return False
    boxes = {c["id"]: c for c in candidates}
    by_designator = {c["designator"].replace(" ", "").lower(): c["id"] for c in candidates}
    by_meas = {m.get("id"): m for m in meas}
    shown, hidden, dropped, drawn = 0, 0, 0, []
    for p in probes:
        m = by_meas.get(p.get("measurementId"))
        if not m:
            print("PUNTAS de una medicion desconocida:", p.get("measurementId"))
            continue
        if p.get("visible") is False:
            hidden += 1
            print(f"OCULTA {m.get('id')} {m.get('componentId')}: {str(p.get('note'))[:120]}")
            continue
        for probe in ("red", "black"):
            q = p.get(probe) or {}
            cid = str(q.get("componentId") or m.get("componentId"))
            cid = cid if cid in boxes else by_designator.get(cid.replace(" ", "").lower(), cid)
            box = boxes.get(cid)
            try:
                x, y = float(q["x"]), float(q["y"])
            except (KeyError, TypeError, ValueError):
                box = None
            if not box:
                dropped += 1
                print(f"DESCARTADA {m.get('id')} {probe}: {q}")
                continue
            margin = max(0.02, 0.25 * max(box["w"], box["h"]))
            if not (box["x"] - margin <= x <= box["x"] + box["w"] + margin and
                    box["y"] - margin <= y <= box["y"] + box["h"] + margin):
                dropped += 1
                print(f"FUERA DE SU CAJA {m.get('id')} {probe} ({x:.3f},{y:.3f}) sobre {cid}")
                continue
            shown += 1
            drawn.append((m.get("componentId"), probe, x, y, str(q.get("terminal") or "")))
            print(f"PUNTA {m.get('id')} {probe} {cid} ({x:.3f},{y:.3f}) {q.get('terminal')}")
    print(f"puntas validas={shown} descartadas={dropped} mediciones con patas ocultas={hidden}")
    points = [q for p in probes for q in (p.get("red") or {}, p.get("black") or {})]
    warns = text_warnings(points, ("terminal",), set(boxes)) + text_warnings(probes, ("note",), set(boxes))
    for w in warns:
        print("AVISO:", w)
    im = Image.open(image_path).convert("RGB")
    w, h = im.size
    d = ImageDraw.Draw(im)
    try:  # Fuente con acentos ("ánodo"); si no esta, la de PIL.
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)
    except OSError:
        font = ImageFont.load_default()
    for cid in {c for c, *_ in drawn}:
        b = boxes.get(cid)
        if b:
            d.rectangle([b["x"] * w, b["y"] * h, (b["x"] + b["w"]) * w, (b["y"] + b["h"]) * h],
                        outline=(255, 179, 0), width=2)
    for _, probe, x, y, terminal in drawn:
        color = (230, 30, 30) if probe == "red" else (15, 15, 15)
        d.ellipse([x * w - 6, y * h - 6, x * w + 6, y * h + 6], fill=color, outline=(255, 255, 255), width=2)
        d.text((x * w + 8, y * h - 7), terminal, fill=(255, 255, 255), font=font)
    im.save(os.path.join(OUT_DIR, "boardpilot_probes.jpg"), quality=90)
    print("imagen:", os.path.join(OUT_DIR, "boardpilot_probes.jpg"))
    ok = (shown + hidden) > 0
    print("RESULTADO PUNTAS:", "OK" if ok else "revisar (arriba)")
    return ok


def plan_step(clean_url, candidates, valid_ids, symptom, language, hyps, meas) -> bool:
    """Pide las mediciones siguientes como si todas las del diagnostico dieran normal."""
    done = [{"id": m.get("id"), "measured": f"{m.get('kind')} {m.get('componentId')}: {m.get('instruction')}",
             "result": "NORMAL"} for m in meas]
    hyp_list = [{"id": h.get("id"), "title": h.get("title"), "componentId": h.get("componentId"),
                 "confidence": h.get("confidence"), "confirmedByMeasurement": False} for h in hyps]
    shown = select_candidates(candidates, {h.get("componentId") for h in hyps})
    board = board_arg()
    board_line = "" if board == "UNKNOWN" else f"Board type: {board}. {PLAYBOOKS['types'][board]}\n"
    user_text = (f"Symptom: {symptom}\n{board_line}Candidate components:\n{json.dumps(shown, ensure_ascii=False)}\n"
                 f"Current hypotheses:\n{json.dumps(hyp_list, ensure_ascii=False)}\n"
                 f"Measurements already done:\n{json.dumps(done, ensure_ascii=False)}")
    body = {
        "model": MODEL,
        "max_tokens": 12000,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": PLAN.replace("LANGUAGE", language)},
            {"role": "user", "content": [
                {"type": "text", "text": user_text},
                {"type": "image_url", "image_url": {"url": clean_url}},
            ]},
        ],
    }
    print("plan de mediciones siguientes...")
    content, usage = chat(body)
    print("usage plan:", json.dumps(usage))
    parsed = parse_obj(content)
    plan = parsed.get("measurements") or []
    hyp_ids = {h.get("id") for h in hyps}
    done_ids = {d["id"] for d in done}
    problems = []
    for m in plan:
        if m.get("componentId") not in valid_ids:
            problems.append(f"plan con componentId fuera de lista: {m}")
        if m.get("kind") not in KINDS:
            problems.append(f"plan con kind invalido: {m}")
        if m.get("targetHypothesisId") not in hyp_ids:
            problems.append(f"plan sin hipotesis destino valida: {m}")
        print(f"PLAN {m.get('kind')} {m.get('componentId')} -> {m.get('targetHypothesisId')}: "
              f"{str(m.get('instruction'))[:120]}")
    print(f"plan={len(plan)} (ids repetidos de lo ya medido: {sorted(done_ids & {m.get('id') for m in plan})}) "
          f"needMoreEvidence={str(parsed.get('needMoreEvidence'))[:160]!r}")
    warns = text_warnings(plan, ("instruction", "normal", "abnormal"), valid_ids)
    for w in warns:
        print("AVISO:", w)
    print("textos del plan sin ids:", "si" if not warns else f"NO ({len(warns)})")
    for p in problems:
        print("PROBLEMA:", p)
    ok = bool(plan or parsed.get("needMoreEvidence")) and not problems
    print("RESULTADO PLAN:", "OK" if ok else "revisar (arriba)")
    return ok


if __name__ == "__main__":
    sys.exit(main())
