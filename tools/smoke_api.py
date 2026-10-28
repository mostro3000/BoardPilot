#!/usr/bin/env python3
"""Smoke tests de Meta Model API para BoardPilot (Fase 0).

Lee la clave SOLO de la variable de entorno MODEL_API_KEY. Nunca la imprime.
Uso:
    MODEL_API_KEY=... python3 tools/smoke_api.py
Variables opcionales:
    META_API_BASE   (default: https://api.meta.ai/v1)
    MUSE_SPARK_MODEL (default: muse-spark-1.3)
"""

import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("META_API_BASE", "https://api.meta.ai/v1").rstrip("/")
MODEL = os.environ.get("MUSE_SPARK_MODEL", "muse-spark-1.3")
KEY = os.environ.get("MODEL_API_KEY", "")


def request(method: str, path: str, body: dict | None = None) -> tuple[int, dict]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        f"{BASE}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {KEY}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read().decode() or "{}")
        except Exception:
            payload = {}
        return e.code, payload


def main() -> int:
    if not KEY:
        print("ERROR: falta MODEL_API_KEY en el entorno. No se hizo ninguna llamada.")
        print("Cargala solo en tu terminal y reintenta; nunca la pegues en el repo.")
        return 2
    if KEY.count("|") != 2 or not KEY.startswith("LLM|"):
        print("NOTA: la clave no tiene el formato LLM|{id}|{secreto} del cookbook.")
        print("Puede ser otro formato valido; la aceptacion la define la API.")

    failures = 0

    print(f"[1/2] GET {BASE}/models ...")
    status, data = request("GET", "/models")
    print(f"  status={status}")
    model_ids = [m.get("id", "?") for m in data.get("data", []) if isinstance(m, dict)]
    if status == 200 and model_ids:
        print(f"  {len(model_ids)} modelos visibles para esta cuenta:")
        for mid in sorted(model_ids):
            print(f"    - {mid}")
    else:
        failures += 1
        err = data.get("error", data)
        print(f"  sin lista de modelos. error={json.dumps(err)[:300]}")

    print(f"[2/2] POST {BASE}/chat/completions model={MODEL} ...")
    status, data = request(
        "POST",
        "/chat/completions",
        {
            "model": MODEL,
            "messages": [{"role": "user", "content": "Reply with exactly: OK"}],
            "max_tokens": 256,
        },
    )
    print(f"  status={status}")
    if status == 200:
        choices = data.get("choices") or [{}]
        message = choices[0].get("message") or {}
        content = message.get("content") or ""
        if content:
            print(f"  respuesta={content.strip()[:80]!r}")
        else:
            print(f"  respuesta vacia; finish_reason={choices[0].get('finish_reason')}")
            print(f"  campos del mensaje: {sorted(message.keys())}")
        if "usage" in data:
            print(f"  usage={json.dumps(data['usage'])}")
    else:
        failures += 1
        err = data.get("error", data)
        print(f"  error={json.dumps(err)[:300]}")

    print("RESULTADO:", "OK" if failures == 0 else f"{failures} chequeo(s) fallaron")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
