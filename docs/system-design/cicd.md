# CI/CD — Sentinel IoT Platform

GitHub Actions runs on every push and pull request via `.github/workflows/ci.yml`.

---

## Pipeline

1. **Security scan** — Gitleaks (full git history, blocks on any secret found) + Trivy filesystem scan (SCA, SARIF → GitHub Security tab) + Trivy container image scan (CRITICAL/HIGH CVEs fail the build)
2. **Backend** — Checkstyle → unit tests → integration tests (Testcontainers, real Postgres + Redis + Mosquitto)
3. **Frontend** — ESLint → Next.js build
4. **Docker** — `docker compose config` validation (with `JWT_SECRET` placeholder) → parallel image build

All steps are hard-fails — no `|| true` overrides. A red build means a real problem.

---

## Contract Testing

A separate workflow (`api-contract.yml`) runs schemathesis-based contract fuzzing against the OpenAPI spec to catch undocumented behavior or regressions in request/response shapes.
