.PHONY: up up-obs up-full down down-v logs ps build

# Generate JWT_SECRET in .env if missing or empty
define ensure-jwt-secret
	@if [ ! -f .env ]; then cp .env.template .env; fi
	@if ! grep -qE '^JWT_SECRET=.+' .env; then \
		secret=$$(openssl rand -base64 48); \
		sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$$secret|" .env; \
		echo "[sentinel] Generated JWT_SECRET and saved to .env"; \
	fi
endef

# ── Core stack (no observability, tracing disabled) ──────────────────────────
up:
	$(ensure-jwt-secret)
	docker compose up -d

# ── Core + rebuild images ─────────────────────────────────────────────────────
build:
	$(ensure-jwt-secret)
	docker compose up --build -d

# ── Core + Prometheus / Grafana / Jaeger (tracing enabled automatically) ─────
up-obs:
	$(ensure-jwt-secret)
	TRACING_ENABLED=true docker compose --profile observability up -d

# ── Everything + rebuild ──────────────────────────────────────────────────────
up-full:
	$(ensure-jwt-secret)
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
