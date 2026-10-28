#!/usr/bin/env python3
"""Smoke test de Muse Spark (Fase 3) para BoardPilot.

Anota la foto con las cajas candidatas de SAM (JSON) y pide a muse-spark-1.3
hipotesis + mediciones en JSON estricto por /chat/completions multimodal.
Valida que la respuesta parsee, referencie solo ids candidatos y traiga
mediciones con tipos validos. Lee la clave solo de MODEL_API_KEY.

Uso:
    MODEL_API_KEY=... python3 tools/smoke_spark.py IMAGEN CANDIDATOS.json "sintoma" [ES|EN] [--plan]

Con --plan, despues del diagnostico simula que todas sus mediciones dieron
normal y pide las siguientes con el mismo prompt que MetaSparkClient.planMeasurements.

CANDIDATOS.json: [{"id","designator","kind","x","y","w","h"}] normalizado 0..1.
La foto anotada queda en /tmp/boardpilot_annotated.jpg.
"""

import base64
import io
import json
import os
import sys
import urllib.error
import urllib.request

from PIL import Image, ImageDraw

BASE = os.environ.get("META_API_BASE", "https://api.meta.ai/v1").rstrip("/")
MODEL = os.environ.get("MUSE_SPARK_MODEL", "muse-spark-1.3")
KEY = os.environ.get("MODEL_API_KEY", "")
KINDS = {"CONTINUITY", "RESISTANCE", "VOLTAGE_DC", "DIODE"}

DAMAGE_PROMPT = """Look at this electronic board photo and scan the ENTIRE board for visible physical damage ONLY: burnt, charred or blackened components or PCB areas; bulging, leaking or exploded capacitors; cracked or melted parts; heavy corrosion or burnt traces.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"damages":[{"description":"...","x":0.0,"y":0.0,"w":0.0,"h":0.0}]}
Use tight normalized boxes (0..1, origin top-left) around each damaged component or area, at most 3, worst first. Descriptions in LANGUAGE. If there is no visible damage, return {"damages":[]}."""

SYSTEM = """You are BoardPilot, an expert electronics repair copilot for component-level PCB diagnosis.
You receive TWO images of the same board: first the clean photo, second the same photo annotated with candidate regions from a segmentation model; plus the candidate list as JSON (normalized 0..1 coordinates, origin top-left) and a symptom. Candidates are visual regions and may be wrong or incomplete; use the clean photo (labels can hide detail) to interpret what each region really is.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"hypotheses":[{"id":"h1","title":"...","componentId":"...","confidence":0.4,"rationale":"...","fix":"..."}],"measurements":[{"id":"m1","componentId":"...","kind":"CONTINUITY|RESISTANCE|VOLTAGE_DC|DIODE","instruction":"...","normal":"...","abnormal":"...","targetHypothesisId":"h1"}],"damages":[{"componentId":"...","damage":"..."}],"unmappedDamage":{"description":"...","x":0.0,"y":0.0,"w":0.0,"h":0.0},"needMoreEvidence":""}
Rules:
- FIRST scan the ENTIRE photo for visible damage: burnt/charred/blackened components or PCB areas, bulging or leaking capacitors, cracked or melted parts, heavy corrosion. For every candidate that is visibly damaged add a "damages" entry describing it. A visibly burnt or exploded component is the strongest evidence there is: rank its hypothesis FIRST with the highest confidence and target it with the first measurement.
- If visible damage has NO candidate box around it, report it in "unmappedDamage" with a tight normalized box (x,y,w,h in 0..1) around the damaged component/area; in that case you may reference that region as componentId "dmg1-spark" in hypotheses and measurements. If there is no unmapped damage, set "unmappedDamage" to null.
- componentId must be the candidate "id" value exactly as in the list (for example "cyl2-sam1"), plus "dmg1-spark" when you reported unmappedDamage. Designators (for example "CYL 2") are only for naming components inside texts; never invent silkscreen reference designators.
- At most 4 hypotheses ranked by likelihood for the symptom (on a dead switch-mode power supply consider fuse, bridge rectifier, bulk capacitor, switching MOSFET short, startup resistor, PWM controller supply, optocoupler/feedback, secondary diodes and output capacitors) adapted to what is actually visible.
- For each hypothesis, "fix" says what to replace or repair if it is confirmed, with a generic replacement spec when it applies (part type, key ratings, package and a common part number as an example).
- Whenever you give hypotheses, also give between 1 and 5 measurements, ordered by diagnostic value and safety. Each targets exactly one hypothesis by its id and one component, uses a kind that is exactly CONTINUITY, RESISTANCE, VOLTAGE_DC or DIODE (never AC or mains measurements), says where to put the probes on this board, and describes what normal vs abnormal looks like on the meter.
- Safety: continuity, resistance and diode measurements are done only with the unit unplugged and capacitors discharged; voltage measurements only when essential and with explicit caution in the instruction.
- If the photo or candidates are insufficient, return empty arrays and explain in needMoreEvidence what photo/measurement you need.
- All user-visible strings (title, rationale, fix, instruction, normal, abnormal, damage, description, needMoreEvidence) in LANGUAGE."""


# Mismo prompt que MetaSparkClient.PLAN_PROMPT (mantener en sync).
PLAN = """You are BoardPilot, an expert electronics repair copilot guiding a technician with a multimeter, one measurement at a time.
You receive the clean photo of a board, its candidate components (JSON, normalized 0..1 boxes, origin top-left), the symptom, the current fault hypotheses (with ids and confidence) and the measurements already done with their results. Propose the NEXT measurements that best confirm or rule out the hypotheses, most informative first.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"measurements":[{"id":"m1","componentId":"...","kind":"CONTINUITY|RESISTANCE|VOLTAGE_DC|DIODE","instruction":"...","normal":"...","abnormal":"...","targetHypothesisId":"h1"}],"needMoreEvidence":""}
Rules:
- Between 1 and 3 measurements and never repeat one already done. Each targets one hypothesis id from the list and one componentId exactly as in the candidate list.
- kind is exactly CONTINUITY, RESISTANCE, VOLTAGE_DC or DIODE (never AC or mains measurements). The instruction says where to put the probes on this board; normal and abnormal say what the meter shows.
- Safety: continuity, resistance and diode measurements only with the unit unplugged and capacitors discharged; voltage measurements only when essential and with explicit caution in the instruction.
- If no further measurement can tell the hypotheses apart, return an empty list and say in needMoreEvidence what photo or information you need.
- All user-visible strings (instruction, normal, abnormal, needMoreEvidence) in LANGUAGE."""


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
    im.save("/tmp/boardpilot_annotated.jpg", quality=88)
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
        print('Uso: smoke_spark.py IMAGEN CANDIDATOS.json "sintoma" [ES|EN] [--plan]')
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

    # 1) Pre-escaneo de dano sobre la foto limpia
    print("pre-escaneo de dano...")
    try:
        scan_text, scan_usage = chat({
            "model": MODEL, "max_tokens": 6000, "temperature": 0.2,
            "messages": [{"role": "user", "content": [
                {"type": "text",
                 "text": DAMAGE_PROMPT.replace("LANGUAGE", language)},
                {"type": "image_url", "image_url": {"url": clean_url}},
            ]}],
        })
        findings = parse_obj(scan_text).get("damages") or []
    except Exception as exc:
        print("pre-escaneo fallo (sigue sin dano):", exc)
        findings, scan_usage = [], {}
    print("usage pre-escaneo:", json.dumps(scan_usage))
    summary = assign_damage(findings, candidates)
    print("dano asignado:", summary)

    # 2) Diagnostico con foto limpia + anotada (ya incluye los DMG)
    jpeg = annotate(image_path, candidates)
    data_url = "data:image/jpeg;base64," + base64.b64encode(jpeg).decode("ascii")
    prescan = ("\nVisible damage found by a dedicated pre-scan (already included "
               "as candidates with visibleDamage): " + "; ".join(summary) + "\n"
               if summary else "")
    user_text = (
        f"Symptom: {symptom}\n{prescan}"
        f"Candidate components from segmentation (kind is a rough visual guess):\n"
        f"{json.dumps(candidates, ensure_ascii=False)}"
    )
    body = {
        "model": MODEL,
        "max_tokens": int(os.environ.get("SPARK_MAX_TOKENS", "12000")),
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": SYSTEM.replace("LANGUAGE", language)},
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
    print(f"hipotesis={len(hyps)} mediciones={len(meas)} "
          f"needMoreEvidence={str(parsed.get('needMoreEvidence'))[:120]!r}")
    for hpos in hyps:
        print(f"HYP {hpos.get('componentId')} {hpos.get('confidence')} "
              f"{str(hpos.get('title'))[:80]} | fix: {str(hpos.get('fix'))[:110]}")
    for p in problems:
        print("PROBLEMA:", p)
    ok = bool(hyps) and not problems
    print("RESULTADO:", "OK" if ok else "revisar (arriba)")
    if "--plan" in sys.argv and hyps:
        ok = plan_step(clean_url, candidates, valid_ids, symptom, language, hyps, meas) and ok
    return 0 if ok else 1


def plan_step(clean_url, candidates, valid_ids, symptom, language, hyps, meas) -> bool:
    """Pide las mediciones siguientes como si todas las del diagnostico dieran normal."""
    done = [{"id": m.get("id"), "measured": f"{m.get('kind')} {m.get('componentId')}: {m.get('instruction')}",
             "result": "NORMAL"} for m in meas]
    hyp_list = [{"id": h.get("id"), "title": h.get("title"), "componentId": h.get("componentId"),
                 "confidence": h.get("confidence"), "confirmedByMeasurement": False} for h in hyps]
    user_text = (f"Symptom: {symptom}\nCandidate components:\n{json.dumps(candidates, ensure_ascii=False)}\n"
                 f"Current hypotheses:\n{json.dumps(hyp_list, ensure_ascii=False)}\n"
                 f"Measurements already done:\n{json.dumps(done, ensure_ascii=False)}")
    body = {
        "model": MODEL,
        "max_tokens": 8000,
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
    for p in problems:
        print("PROBLEMA:", p)
    ok = bool(plan or parsed.get("needMoreEvidence")) and not problems
    print("RESULTADO PLAN:", "OK" if ok else "revisar (arriba)")
    return ok


if __name__ == "__main__":
    sys.exit(main())
