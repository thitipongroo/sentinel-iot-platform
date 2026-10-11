#!/usr/bin/env bash
# ==============================================================================
# Argo Rollouts & KEDA Autoscaler Verification Script
#
# Validates Helm chart templating, schema correctness, and RFC-001 conformance
# for Blue/Green Argo Rollouts and KEDA Kafka + Redis Autoscaling.
# ==============================================================================

set -euo pipefail

CHART_DIR="infra/helm/sentinel-iot"
PROD_VALUES="${CHART_DIR}/values-prod.yaml"

echo "======================================================================"
echo "   Sentinel IoT — Argo Rollouts & KEDA Validation Suite               "
echo "======================================================================"

# 1. Check Helm CLI
if ! command -v helm &>/dev/null; then
  echo "[ERROR] 'helm' command not found."
  exit 1
fi

echo "[1/4] Validating Helm chart linting..."
helm lint "${CHART_DIR}"

echo "[2/4] Testing development template rendering (values.yaml)..."
helm template sentinel-iot "${CHART_DIR}" > /dev/null
echo "  ✓ Development template rendered successfully."

echo "[3/4] Testing production template rendering (values-prod.yaml)..."
PROD_OUTPUT=$(helm template sentinel-iot "${CHART_DIR}" -f "${PROD_VALUES}")
echo "  ✓ Production template rendered successfully."

echo "[4/4] Asserting RFC-001 Kubernetes Resources..."

# Assert Rollout kind exists
if [[ "$PROD_OUTPUT" == *"kind: Rollout"* ]]; then
  echo "  ✓ [PASS] argoproj.io/v1alpha1 Rollout resource verified."
else
  echo "  ✗ [FAIL] Rollout resource missing in production template."
  exit 1
fi

# Assert AnalysisTemplate kind exists
if [[ "$PROD_OUTPUT" == *"kind: AnalysisTemplate"* ]]; then
  echo "  ✓ [PASS] argoproj.io/v1alpha1 AnalysisTemplate verified."
else
  echo "  ✗ [FAIL] AnalysisTemplate resource missing in production template."
  exit 1
fi

# Assert KEDA ScaledObject kind exists
if [[ "$PROD_OUTPUT" == *"kind: ScaledObject"* ]]; then
  echo "  ✓ [PASS] keda.sh/v1alpha1 ScaledObject resource verified."
else
  echo "  ✗ [FAIL] ScaledObject resource missing in production template."
  exit 1
fi

# Assert KEDA triggers for both Kafka and Redis
if [[ "$PROD_OUTPUT" == *"type: kafka"* ]] && [[ "$PROD_OUTPUT" == *"type: redis"* ]]; then
  echo "  ✓ [PASS] Dual KEDA triggers verified (Kafka lag + Redis replay queue depth)."
else
  echo "  ✗ [FAIL] Missing dual Kafka/Redis triggers in ScaledObject."
  exit 1
fi

# Assert Spring Boot Actuator readiness probe is "UP"
if [[ "$PROD_OUTPUT" == *'successCondition: result == "UP"'* ]]; then
  echo "  ✓ [PASS] Spring Boot Actuator readiness check ('UP') verified."
else
  echo "  ✗ [FAIL] Spring Boot Actuator readiness probe check incorrect."
  exit 1
fi

echo "======================================================================"
echo "   ALL ARGO ROLLOUTS & KEDA INFRA CHECKS PASSED SUCCESSFULLY          "
echo "======================================================================"
exit 0
