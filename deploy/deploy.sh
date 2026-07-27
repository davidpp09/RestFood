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
#
# `clean` NO es opcional. Sin él, target/classes conserva archivos de ramas que
# se compilaron antes en este mismo directorio, y `package` los mete en el jar
# aunque no existan en main. El 2026-07-27 eso puso en produccion una migracion
# de una rama SIN FUSIONAR (V2, inventario): llevaba dos dias en target/ desde
# un build del 25, y viajo dentro del jar de un commit que no la contiene.
#
# La consecuencia no fue el jar, fue el rollback: la base quedo en una version
# de Flyway que las releases anteriores no conocen, asi que rollback.sh habria
# dejado el backend sin arrancar. Un despliegue tiene que depender solo de lo
# que esta en el commit, y sin `clean` depende ademas de que hizo el ultimo que
# compilo aqui.
log "construyendo jar de $SHA (build limpio)..."
(cd "$API_DIR" && ./mvnw -B -q clean package -Dtest='!RestApiApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false)
JAR="$(ls "$API_DIR"/target/api-*.jar | grep -v '\.original$' | head -1)"
[[ -f "$JAR" ]] || die "no se encontró el jar en target/"

# Red de seguridad: que las migraciones del jar sean EXACTAMENTE las del commit.
# Si alguna vez vuelven a divergir, el deploy se detiene aqui en vez de que lo
# descubra Flyway contra la base del restaurante.
MIG_COMMIT="$(git ls-tree -r HEAD --name-only | grep -c '^api/src/main/resources/db/migration/V')"
MIG_JAR="$(unzip -l "$JAR" | grep -c 'BOOT-INF/classes/db/migration/V')"
[[ "$MIG_COMMIT" == "$MIG_JAR" ]] || die "el jar tiene $MIG_JAR migraciones y el commit $MIG_COMMIT — build sucio, aborto"
log "migraciones verificadas: $MIG_JAR, las mismas que el commit"

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
