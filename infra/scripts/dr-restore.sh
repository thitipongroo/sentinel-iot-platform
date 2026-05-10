#!/usr/bin/env bash
# Sentinel IoT — Disaster Recovery Restore Script
#
# RTO target : 30 minutes from decision to service-restored
# RPO target : 5 minutes (RDS automated backup interval)
#
# Usage:
#   ./dr-restore.sh --env prod --region ap-southeast-1 --snapshot <snapshot-id>
#
# Pre-requisites:
#   aws CLI configured with DR access, kubectl, helm, argo rollouts plugin

set -euo pipefail

# ── Colour helpers ─────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Defaults ───────────────────────────────────────────────────────────────────
ENV="prod"
REGION="ap-southeast-1"
NAMESPACE="sentinel"
HELM_RELEASE="sentinel-iot"
SNAPSHOT_ID=""
SKIP_CONFIRM=false

# ── Argument parsing ───────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case $1 in
    --env)         ENV="$2";         shift 2 ;;
    --region)      REGION="$2";      shift 2 ;;
    --namespace)   NAMESPACE="$2";   shift 2 ;;
    --snapshot)    SNAPSHOT_ID="$2"; shift 2 ;;
    --yes)         SKIP_CONFIRM=true; shift ;;
    *) error "Unknown argument: $1" ;;
  esac
done

NAME_PREFIX="sentinel-iot-${ENV}"
DR_INSTANCE_ID="${NAME_PREFIX}-postgres-dr-$(date +%Y%m%d%H%M)"

# ── Step 0: Preflight ─────────────────────────────────────────────────────────
info "=== Sentinel IoT DR Restore ==="
info "Env: ${ENV} | Region: ${REGION} | Namespace: ${NAMESPACE}"

for cmd in aws kubectl helm; do
  command -v "$cmd" &>/dev/null || error "Required tool not found: $cmd"
done

if [[ -z "$SNAPSHOT_ID" ]]; then
  info "No --snapshot provided. Listing the 5 most recent automated snapshots..."
  aws rds describe-db-snapshots \
    --db-instance-identifier "${NAME_PREFIX}-postgres" \
    --snapshot-type automated \
    --region "$REGION" \
    --query 'sort_by(DBSnapshots, &SnapshotCreateTime)[-5:].{ID:DBSnapshotIdentifier,Created:SnapshotCreateTime,Status:Status}' \
    --output table
  read -rp "Enter snapshot ID to restore: " SNAPSHOT_ID
fi

[[ -z "$SNAPSHOT_ID" ]] && error "No snapshot ID provided."

if [[ "$SKIP_CONFIRM" == false ]]; then
  warn "This will restore snapshot '${SNAPSHOT_ID}' as '${DR_INSTANCE_ID}'."
  warn "The active Kubernetes deployment will be paused and repointed."
  read -rp "Type 'restore' to confirm: " CONFIRM
  [[ "$CONFIRM" != "restore" ]] && error "Aborted by user."
fi

# ── Step 1: Scale down application (halt new writes) ──────────────────────────
info "[1/7] Pausing application rollout..."
if kubectl argo rollouts status "${HELM_RELEASE}-backend" -n "$NAMESPACE" &>/dev/null; then
  kubectl argo rollouts pause "${HELM_RELEASE}-backend" -n "$NAMESPACE" || true
else
  kubectl scale deployment "${HELM_RELEASE}-backend" --replicas=0 -n "$NAMESPACE" || true
fi
info "Application paused. MQTT devices will buffer in Kafka during restore."

# ── Step 2: Restore RDS snapshot to new instance ──────────────────────────────
info "[2/7] Restoring RDS snapshot ${SNAPSHOT_ID} → ${DR_INSTANCE_ID}..."
DB_SUBNET_GROUP=$(aws rds describe-db-instances \
  --db-instance-identifier "${NAME_PREFIX}-postgres" \
  --region "$REGION" \
  --query 'DBInstances[0].DBSubnetGroup.DBSubnetGroupName' \
  --output text 2>/dev/null || echo "${NAME_PREFIX}-rds-subnets")

VPC_SG=$(aws rds describe-db-instances \
  --db-instance-identifier "${NAME_PREFIX}-postgres" \
  --region "$REGION" \
  --query 'DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId' \
  --output text 2>/dev/null || error "Cannot determine VPC security group.")

aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier "$DR_INSTANCE_ID" \
  --db-snapshot-identifier "$SNAPSHOT_ID" \
  --db-instance-class "db.t3.medium" \
  --db-subnet-group-name "$DB_SUBNET_GROUP" \
  --vpc-security-group-ids "$VPC_SG" \
  --no-multi-az \
  --no-publicly-accessible \
  --region "$REGION" \
  --tags "Key=Environment,Value=${ENV}" "Key=DR,Value=true" \
  --output text >/dev/null

info "Waiting for DR instance to become available (may take 10-20 min)..."
aws rds wait db-instance-available \
  --db-instance-identifier "$DR_INSTANCE_ID" \
  --region "$REGION"
info "RDS DR instance is available."

# ── Step 3: Retrieve new endpoint ─────────────────────────────────────────────
info "[3/7] Retrieving DR database endpoint..."
DR_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier "$DR_INSTANCE_ID" \
  --region "$REGION" \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text)
[[ -z "$DR_ENDPOINT" ]] && error "Could not retrieve DR endpoint."
info "DR endpoint: ${DR_ENDPOINT}"

# ── Step 4: Update Kubernetes ConfigMap ───────────────────────────────────────
info "[4/7] Patching ConfigMap with DR database endpoint..."
kubectl patch configmap "${HELM_RELEASE}-config" \
  -n "$NAMESPACE" \
  --type=json \
  -p "[{\"op\":\"replace\",\"path\":\"/data/DB_HOST\",\"value\":\"${DR_ENDPOINT}\"}]"
info "ConfigMap updated."

# ── Step 5: Verify connectivity from a test pod ───────────────────────────────
info "[5/7] Verifying database connectivity..."
DB_USER=$(kubectl get secret sentinel-secrets -n "$NAMESPACE" \
  -o jsonpath='{.data.DB_USER}' | base64 -d)
PGPASSWORD=$(kubectl get secret sentinel-secrets -n "$NAMESPACE" \
  -o jsonpath='{.data.DB_PASSWORD}' | base64 -d)

kubectl run dr-verify --rm -it --restart=Never \
  -n "$NAMESPACE" \
  --image=postgres:16-alpine \
  --env="PGPASSWORD=${PGPASSWORD}" \
  --command -- \
  pg_isready -h "$DR_ENDPOINT" -U "$DB_USER" -d sentinel \
  && info "Database connectivity verified." \
  || error "Cannot connect to DR database. Aborting."

# ── Step 6: Resume application ────────────────────────────────────────────────
info "[6/7] Resuming application..."
if kubectl argo rollouts status "${HELM_RELEASE}-backend" -n "$NAMESPACE" &>/dev/null; then
  kubectl argo rollouts resume "${HELM_RELEASE}-backend" -n "$NAMESPACE"
  kubectl argo rollouts restart "${HELM_RELEASE}-backend" -n "$NAMESPACE"
else
  kubectl scale deployment "${HELM_RELEASE}-backend" \
    --replicas="$(kubectl get deploy "${HELM_RELEASE}-backend" -n "$NAMESPACE" \
      -o jsonpath='{.spec.replicas}' 2>/dev/null || echo 2)" \
    -n "$NAMESPACE"
fi

info "Waiting for backend rollout to complete..."
kubectl rollout status deployment/"${HELM_RELEASE}-backend" -n "$NAMESPACE" \
  --timeout=300s 2>/dev/null \
  || kubectl argo rollouts status "${HELM_RELEASE}-backend" -n "$NAMESPACE" \
       --timeout 5m 2>/dev/null \
  || warn "Rollout status check failed — verify manually."

# ── Step 7: Smoke test ────────────────────────────────────────────────────────
info "[7/7] Running smoke test..."
BACKEND_SVC="${HELM_RELEASE}-backend.${NAMESPACE}.svc:8080"
HEALTH=$(kubectl run smoke-test --rm -it --restart=Never \
  -n "$NAMESPACE" \
  --image=curlimages/curl:8.5.0 \
  --command -- \
  curl -sf "http://${BACKEND_SVC}/actuator/health/readiness" \
  | grep -o '"status":"[^"]*"' | head -1 || echo "unknown")

if echo "$HEALTH" | grep -q "UP"; then
  info "Smoke test passed. Service is healthy."
else
  warn "Smoke test returned: ${HEALTH}. Investigate before marking DR complete."
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
info "=========================================================="
info "  DR Restore Complete"
info "  DR RDS instance : ${DR_INSTANCE_ID}"
info "  Endpoint        : ${DR_ENDPOINT}"
info "  Namespace       : ${NAMESPACE}"
info "=========================================================="
warn "NEXT STEPS:"
warn "  1. Monitor application logs: kubectl logs -l app.kubernetes.io/component=backend -n ${NAMESPACE} -f"
warn "  2. Validate data integrity in the application"
warn "  3. When stable, update Terraform state to point to DR instance"
warn "  4. Delete old primary RDS instance only after full validation"
warn "  5. Promote DR instance to primary and re-enable Multi-AZ"
