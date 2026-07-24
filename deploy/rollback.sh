#!/usr/bin/env bash
# Rollback del backend RestFood: vuelve a la release anterior en segundos, sin recompilar.
# Uso: ./deploy/rollback.sh
# OJO: esto revierte el CÓDIGO, no las migraciones de BD — ver Lección 03.
set -euo pipefail

DEPLOY_DIR="$HOME/deploys/restfood-backend"
SERVICE=restfood-backend
HEALTH_URL="http://localhost:8080/"

[[ -e "$DEPLOY_DIR/previous.jar" ]] || { echo "[rollback] ERROR: no hay release anterior registrada" >&2; exit 1; }

ACTUAL="$(readlink -f "$DEPLOY_DIR/current.jar")"
ANTERIOR="$(readlink -f "$DEPLOY_DIR/previous.jar")"

ln -sfn "$ANTERIOR" "$DEPLOY_DIR/current.jar"
ln -sfn "$ACTUAL" "$DEPLOY_DIR/previous.jar"   # deja registrado cómo des-hacer el rollback
sudo systemctl restart "$SERVICE"

CODIGO=000
for _ in {1..30}; do
    CODIGO="$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)"
    [[ "$CODIGO" != "000" ]] && break
    sleep 2
done
[[ "$CODIGO" != "000" ]] || { echo "[rollback] ERROR: el backend no respondió tras 60s" >&2; exit 1; }

echo "[rollback] ahora corre: $(basename "$ANTERIOR") (antes corría: $(basename "$ACTUAL"))"
