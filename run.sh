#!/bin/sh
# Usage: ./run.sh <command>
# Mirror of Makefile targets for environments without make.

ensure_jwt_secret() {
  if [ ! -f .env ]; then cp .env.template .env; fi
  if ! grep -qE '^JWT_SECRET=.+' .env; then
    secret=$(openssl rand -base64 48)
    sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$secret|" .env
    echo "[sentinel] Generated JWT_SECRET and saved to .env"
  fi
}

CMD=${1:-help}

case "$CMD" in
  up)
    ensure_jwt_secret
    docker compose up -d
    ;;
  build)
    ensure_jwt_secret
    docker compose up --build -d
    ;;
  up-obs)
    ensure_jwt_secret
    TRACING_ENABLED=true docker compose --profile observability up -d
    ;;
  up-full)
    ensure_jwt_secret
    TRACING_ENABLED=true docker compose --profile full up --build -d
    ;;
  down)
    docker compose --profile dev --profile observability --profile full down
    ;;
  down-v)
    docker compose --profile dev --profile observability --profile full down -v
    ;;
  logs)
    docker compose logs -f
    ;;
  ps)
    docker compose ps
    ;;
  help|*)
    echo "Usage: ./run.sh <command>"
    echo ""
    echo "  up        Core stack"
    echo "  build     Core stack (rebuild images)"
    echo "  up-obs    Core + Prometheus / Grafana / Jaeger"
    echo "  up-full   Everything + rebuild"
    echo "  down      Stop all containers"
    echo "  down-v    Stop all + wipe volumes"
    echo "  logs      Tail logs"
    echo "  ps        Container status"
    ;;
esac
