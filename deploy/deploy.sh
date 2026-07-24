#!/usr/bin/env bash
# Deploy del backend RestFood — ver Lección 03 del handbook.
# Uso: ./deploy/deploy.sh  (desde cualquier directorio)
set -euo pipefail

REPO_DIR="$HOME/RestFoodB"
API_DIR="$REPO_DIR/api"
DEPLOY_DIR="$HOME/deploys/restfood-backend"
RELEASES_DIR="$DEPLOY_DIR/releases"
SERVICE=restfood-backend
HEALTH_URL="http://localhost:8080/"
KEEP_RELEASES=5

log() { echo "[deploy] $*"; }
die() { echo "[deploy] ERROR: $*" >&2; exit 1; }

# 1. Solo se despliega main, limpio y actualizado
cd "$REPO_DIR"
[[ "$(git rev-parse --abbrev-ref HEAD)" == "main" ]] || die "hay que estar en main (estás en $(git rev-parse --abbrev-ref HEAD))"
[[ -z "$(git status --porcelain)" ]] || die "hay cambios sin commitear; el deploy solo usa código versionado"
git pull --ff-only
SHA="$(git rev-parse --short HEAD)"

# 2. Solo se despliega lo que el CI ya aprobó
ESTADO_CI="$(gh api "repos/davidpp09/RestFoodB/commits/$SHA/check-runs" \
    --jq '[.check_runs[] | select(.name=="tests")][0].conclusion' 2>/dev/null || echo desconocido)"
[[ "$ESTADO_CI" == "success" ]] || die "el check 'tests' del commit $SHA no está en verde (estado: $ESTADO_CI)"

# 3. Construir el artefacto
log "construyendo jar de $SHA..."
(cd "$API_DIR" && ./mvnw -B -q package -Dtest='!RestApiApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false)
JAR="$(ls "$API_DIR"/target/api-*.jar | grep -v '\.original$' | head -1)"
[[ -f "$JAR" ]] || die "no se encontró el jar en target/"

# 4. Guardar release versionada y apuntar current.jar (previous.jar queda para rollback)
mkdir -p "$RELEASES_DIR"
RELEASE="$RELEASES_DIR/$(date +%Y%m%d-%H%M%S)-$SHA.jar"
cp "$JAR" "$RELEASE"
if [[ -e "$DEPLOY_DIR/current.jar" ]]; then
    ln -sfn "$(readlink -f "$DEPLOY_DIR/current.jar")" "$DEPLOY_DIR/previous.jar"
fi
ln -sfn "$RELEASE" "$DEPLOY_DIR/current.jar"

# 5. Reiniciar y verificar que el backend responde
log "reiniciando $SERVICE..."
sudo systemctl restart "$SERVICE"
CODIGO=000
for _ in {1..30}; do
    CODIGO="$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)"
    [[ "$CODIGO" != "000" ]] && break
    sleep 2
done
if [[ "$CODIGO" == "000" ]]; then
    log "el backend no respondió tras 60s — ROLLBACK automático"
    if [[ -e "$DEPLOY_DIR/previous.jar" ]]; then
        ln -sfn "$(readlink -f "$DEPLOY_DIR/previous.jar")" "$DEPLOY_DIR/current.jar"
        sudo systemctl restart "$SERVICE"
    fi
    die "deploy fallido; se restauró la versión anterior"
fi

# 6. Conservar solo las últimas $KEEP_RELEASES releases (nunca la actual ni la anterior)
ls -t "$RELEASES_DIR"/*.jar 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) | while read -r viejo; do
    [[ "$viejo" == "$(readlink -f "$DEPLOY_DIR/current.jar")" ]] && continue
    [[ -e "$DEPLOY_DIR/previous.jar" && "$viejo" == "$(readlink -f "$DEPLOY_DIR/previous.jar")" ]] && continue
    rm -f "$viejo"
done

log "desplegado $SHA → $(basename "$RELEASE") (respondió HTTP $CODIGO)"
