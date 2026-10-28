#!/usr/bin/env python3
"""Mapeo SAM 3.1 como lo hace la app, para probar fotos nuevas sin el telefono.

Prepara la foto como la app (derecha segun su EXIF, lado mayor de 1600 px) y la
segmenta con los mismos conceptos y en el mismo orden que SAM_CONCEPTS de
MainActivity: confianza >= 0,35, sin duplicados (IoU > 0,6 contra lo ya
encontrado) y nombres CYL n / RECT n / TRF n / PART n. Deja en SALIDA la foto
preparada (foto.jpg), los candidatos para smoke_spark.py (candidatos.json) y una
vista con las cajas (mapa.jpg).

Lee la clave solo de MODEL_API_KEY. Nunca la imprime.
Uso:
    MODEL_API_KEY=... python3 tools/smoke_map.py FOTO SALIDA
    MODEL_API_KEY=... python3 tools/smoke_spark.py SALIDA/foto.jpg SALIDA/candidatos.json "sintoma" ES --probes
"""

import json
import os
import sys

from PIL import Image, ImageDraw, ImageOps

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import smoke_sam  # noqa: E402

# Mismos conceptos y orden que SAM_CONCEPTS en MainActivity (mantener en sync).
CONCEPTS = [
    ("yellow rectangular block", "TRANSFORMER", "TRF"),
    ("green rectangular block", "TRANSFORMER", "TRF"),
    ("rectangular electronic component", "OTHER", "RECT"),
    ("cylindrical component", "OTHER", "CYL"),
    ("electronic component", "OTHER", "PART"),
]
MIN_CONFIDENCE = 0.35
MAX_IOU = 0.6
MAX_SIDE = 1600


def iou(a, b) -> float:
    """Solape de dos cajas (x, y, w, h) normalizadas, como SamOutputParser.iou."""
    x1, y1 = max(a[0], b[0]), max(a[1], b[1])
    x2, y2 = min(a[0] + a[2], b[0] + b[2]), min(a[1] + a[3], b[1] + b[3])
    inter = max(x2 - x1, 0.0) * max(y2 - y1, 0.0)
    union = a[2] * a[3] + b[2] * b[3] - inter
    return inter / union if union > 0 else 0.0


def main() -> int:
    if len(sys.argv) != 3:
        print("Uso: smoke_map.py FOTO SALIDA")
        return 2
    if not smoke_sam.KEY:
        print("ERROR: falta MODEL_API_KEY en el entorno.")
        return 2
    source, out_dir = sys.argv[1:3]
    os.makedirs(out_dir, exist_ok=True)
    im = ImageOps.exif_transpose(Image.open(source)).convert("RGB")
    scale = min(1.0, MAX_SIDE / max(im.size))
    if scale < 1.0:
        im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    photo = os.path.join(out_dir, "foto.jpg")
    im.save(photo, quality=90)
    data = open(photo, "rb").read()
    print(f"foto preparada {im.width}x{im.height}: {photo}")

    found, counters = [], {}
    for phrase, kind, label in CONCEPTS:
        boxes = smoke_sam.segment(data, "image/jpeg", phrase)
        kept = 0
        for oid, x1, y1, x2, y2, conf in boxes:
            x1, y1, x2, y2 = (min(max(v, 0.0), 1.0) for v in (x1, y1, x2, y2))
            box = (x1, y1, x2 - x1, y2 - y1)
            if box[2] <= 0 or box[3] <= 0 or (conf if conf is not None else 1.0) < MIN_CONFIDENCE:
                continue
            if any(iou(c["box"], box) > MAX_IOU for c in found):
                continue
            n = counters.get(label, 0) + 1
            counters[label] = n
            found.append({"id": f"{label.lower()}{n}-sam{oid}", "designator": f"{label} {n}",
                          "kind": kind, "box": box})
            kept += 1
        print(f"  {phrase!r}: {len(boxes)} caja(s), {kept} nueva(s)")

    candidates = [{"id": c["id"], "designator": c["designator"], "kind": c["kind"],
                   "x": round(c["box"][0], 4), "y": round(c["box"][1], 4),
                   "w": round(c["box"][2], 4), "h": round(c["box"][3], 4)} for c in found]
    with open(os.path.join(out_dir, "candidatos.json"), "w", encoding="utf-8") as f:
        json.dump(candidates, f, ensure_ascii=False, indent=1)

    d = ImageDraw.Draw(im)
    for c in candidates:
        x0, y0 = c["x"] * im.width, c["y"] * im.height
        d.rectangle([x0, y0, x0 + c["w"] * im.width, y0 + c["h"] * im.height],
                    outline=(255, 179, 0), width=max(2, im.width // 400))
        d.text((x0 + 3, y0 + 2), c["designator"], fill=(255, 179, 0))
    im.save(os.path.join(out_dir, "mapa.jpg"), quality=88)
    print(f"candidatos={len(candidates)}: {out_dir}/candidatos.json · vista: {out_dir}/mapa.jpg")
    print("RESULTADO:", "OK" if candidates else "sin candidatos (probar otra foto o recorte)")
    return 0 if candidates else 1


if __name__ == "__main__":
    sys.exit(main())
