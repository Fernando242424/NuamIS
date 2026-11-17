#!/usr/bin/env bash
# HU-24.3: monitoreo simple. Consulta /health cada minuto; si falla, deja el aviso en monitor.log (conecte aquí correo/Slack).
URL="${1:-https://localhost:8443/health}"
while true; do
  r=$(curl -sk -m 10 "$URL")
  if ! echo "$r" | grep -q '"bd":"ok"'; then echo "$(date -Is) ALERTA: servicio sin respuesta o BD caída -> $r" | tee -a monitor.log; fi
  sleep 60
done
