.PHONY: up up-obs up-full down down-v logs ps build

# ── Core stack (no observability, tracing disabled) ──────────────────────────
up:
	docker compose up -d

# ── Core + rebuild images ─────────────────────────────────────────────────────
build:
	docker compose up --build -d

# ── Core + Prometheus / Grafana / Jaeger (tracing enabled automatically) ─────
up-obs:
	TRACING_ENABLED=true docker compose --profile observability up -d

# ── Everything + rebuild ──────────────────────────────────────────────────────
up-full:
	TRACING_ENABLED=true docker compose --profile full up --build -d

# ── Stop all containers (all profiles) ───────────────────────────────────────
down:
	docker compose --profile observability --profile full down

# ── Stop all containers and wipe all data volumes ────────────────────────────
down-v:
	docker compose --profile observability --profile full down -v

# ── Tail logs ────────────────────────────────────────────────────────────────
logs:
	docker compose logs -f

# ── Show container status ─────────────────────────────────────────────────────
ps:
	docker compose ps
