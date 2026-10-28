#!/usr/bin/env python3
"""Smoke test de SAM 3.1 (Meta Model API) para BoardPilot.

Protocolo oficial (dev.meta.ai/docs/sam/segmenting):
POST https://api.meta.ai/v1/responses, model sam-3.1, stream=true,
input = mensaje con input_text (frase nominal corta, un concepto por pedido)
+ input_image con image_url como data URL base64. La salida llega como
eventos response.output_text.delta con lineas
<0f>id<|box;x1=..;y1=..;x2=..;y2=..;w=W;h=H;c=CONF|><|mask;...|>

Lee la clave solo de MODEL_API_KEY. Nunca la imprime.
Uso:
    MODEL_API_KEY=... python3 tools/smoke_sam.py IMAGEN "electrolytic capacitor"
"""

import base64
import json
import mimetypes
import os
import re
import sys
import urllib.request

BASE = os.environ.get("META_API_BASE", "https://api.meta.ai/v1").rstrip("/")
KEY = os.environ.get("MODEL_API_KEY", "")
BOX_RE = re.compile(
    r"(\d+)<\|box;x1=([\d.]+);y1=([\d.]+);x2=([\d.]+);y2=([\d.]+);w=(\d+);h=(\d+)(?:;c=([\d.]+))?\|>"
)


def main() -> int:
    if len(sys.argv) != 3:
        print("Uso: smoke_sam.py IMAGEN CONCEPTO")
        return 2
    if not KEY:
        print("ERROR: falta MODEL_API_KEY en el entorno.")
        return 2
    image_path, concept = sys.argv[1], sys.argv[2]
    mime = mimetypes.guess_type(image_path)[0] or "image/png"
    encoded = base64.b64encode(open(image_path, "rb").read()).decode("ascii")

    body = {
        "model": "sam-3.1",
        "stream": True,
        "metadata": {"include_confidence": "true", "mask_encoding": "one_bit"},
        "input": [{
            "type": "message",
            "role": "user",
            "content": [
                {"type": "input_text", "text": concept},
                {"type": "input_image",
                 "image_url": f"data:{mime};base64,{encoded}"},
            ],
        }],
    }
    req = urllib.request.Request(
        f"{BASE}/responses",
        data=json.dumps(body).encode(),
        method="POST",
        headers={
            "Authorization": f"Bearer {KEY}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
    )
    print(f"POST {BASE}/responses model=sam-3.1 concepto={concept!r} imagen={image_path}")
    output_text = []
    with urllib.request.urlopen(req, timeout=180) as resp:
        print("status:", resp.status)
        for raw in resp:
            line = raw.decode("utf-8", errors="replace").strip()
            if not line.startswith("data:"):
                continue
            data = line.removeprefix("data:").strip()
            if data == "[DONE]":
                break
            try:
                event = json.loads(data)
            except json.JSONDecodeError:
                continue
            if event.get("type") == "response.output_text.delta" and event.get("delta"):
                output_text.append(event["delta"])
            elif event.get("type") == "error":
                print("ERROR del API:", json.dumps(event)[:400])
                return 1
    text = "".join(output_text)
    boxes = list(BOX_RE.finditer(text))
    print(f"salida: {len(text)} caracteres, {len(boxes)} caja(s)")
    for b in boxes:
        oid, x1, y1, x2, y2, w, h, conf = b.groups()
        w_i, h_i = int(w), int(h)
        print(
            f"  id={oid} box=({x1},{y1})-({x2},{y2}) frame={w}x{h} "
            f"norm=({float(x1)/w_i:.3f},{float(y1)/h_i:.3f})-"
            f"({float(x2)/w_i:.3f},{float(y2)/h_i:.3f}) conf={conf or '-'}"
        )
    print("RESULTADO:", "OK" if boxes else "sin coincidencias (respuesta valida)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
