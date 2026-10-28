#!/usr/bin/env python3
"""Smoke test de Muse Image 1.0 (Fase 4) para BoardPilot.

Protocolo oficial (cookbook 05_muse_image): POST https://api.meta.ai/v1/responses
con model muse-image-1.0; la imagen de referencia va como parte input_image
(image_url data URL) junto al input_text; la salida trae la imagen base64 en el
item image_generation_call.result de la lista output.

Genera la vista de referencia de la reparacion: la misma placa tras reemplazar
el componente danado indicado. Lee la clave solo de MODEL_API_KEY.

Uso:
    MODEL_API_KEY=... python3 tools/smoke_image.py IMAGEN "descripcion del dano"
La imagen generada queda en /tmp/boardpilot_reference.webp.
"""

import base64
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("META_API_BASE", "https://api.meta.ai/v1").rstrip("/")
MODEL = os.environ.get("MUSE_IMAGE_MODEL", "muse-image-1.0")
KEY = os.environ.get("MODEL_API_KEY", "")

PROMPT = (
    "Edit the attached photo of an electronic power supply board. Keep the "
    "exact same board, camera angle, framing and background. Show the board "
    "AFTER a successful repair: DAMAGE is cleaned, the damaged component is "
    "replaced with a new clean component of the same type, scorch marks are "
    "gone and solder joints look fresh. Everything else must stay identical. "
    "Photorealistic electronics repair bench photo. Do not add any text, "
    "labels, arrows or extra tools."
)


def main() -> int:
    if len(sys.argv) < 3:
        print('Uso: smoke_image.py IMAGEN "descripcion del dano"')
        return 2
    if not KEY:
        print("ERROR: falta MODEL_API_KEY en el entorno.")
        return 2
    image_path, damage = sys.argv[1], sys.argv[2]
    mime = mimetypes.guess_type(image_path)[0] or "image/jpeg"
    encoded = base64.b64encode(open(image_path, "rb").read()).decode("ascii")
    body = {
        "model": MODEL,
        "input": [{
            "type": "message",
            "role": "user",
            "content": [
                {"type": "input_text", "text": PROMPT.replace("DAMAGE", damage)},
                {"type": "input_image",
                 "image_url": f"data:{mime};base64,{encoded}"},
            ],
        }],
    }
    req = urllib.request.Request(
        f"{BASE}/responses", data=json.dumps(body).encode(), method="POST",
        headers={"Authorization": f"Bearer {KEY}",
                 "Content-Type": "application/json"},
    )
    print(f"POST {BASE}/responses model={MODEL} imagen={image_path}")
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            data = json.loads(resp.read().decode() or "{}")
            print("status HTTP:", resp.status, "| status respuesta:", data.get("status"))
    except urllib.error.HTTPError as e:
        print("status:", e.code)
        print("error:", (e.read().decode(errors="replace") or "")[:600])
        return 1
    print("usage:", json.dumps(data.get("usage") or {}))
    for item in data.get("output") or []:
        if item.get("type") == "image_generation_call" and item.get("result"):
            raw = base64.b64decode(item["result"])
            with open("/tmp/boardpilot_reference.webp", "wb") as fh:
                fh.write(raw)
            print(f"imagen: {len(raw)} bytes -> /tmp/boardpilot_reference.webp")
            print("RESULTADO: OK")
            return 0
    print("salida sin image_generation_call:", json.dumps(data)[:600])
    print("RESULTADO: sin imagen")
    return 1


if __name__ == "__main__":
    sys.exit(main())
