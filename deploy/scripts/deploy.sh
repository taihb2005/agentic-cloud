#!/bin/bash
set -euo pipefail

# ==============================================================================
# Agentic Cloud - Automated Deployment Script
# Được kích hoạt từ Webhook Server (adnanh/webhook)
# Tham số: $1 = tên service (frontend, user-service, storage-service, api-gateway)
# ==============================================================================

SERVICE="${1:-}"
PROJECT_ROOT="${PROJECT_ROOT:-/opt/agentic-cloud}"
BRANCH="${BRANCH:-develop}"
LOG_PREFIX="[DEPLOY-$(date +'%Y-%m-%d %H:%M:%S')]"

echo "$LOG_PREFIX Bắt đầu quy trình deploy cho service: $SERVICE"

if [ -z "$SERVICE" ]; then
  echo "$LOG_PREFIX [LỖI] Chưa cung cấp tên service (ví dụ: ./deploy.sh frontend)"
  exit 1
fi

if [ ! -d "$PROJECT_ROOT" ]; then
  echo "$LOG_PREFIX [LỖI] Thư mục dự án không tồn tại: $PROJECT_ROOT"
  exit 1
fi

cd "$PROJECT_ROOT"

# Cập nhật mã nguồn mới nhất từ git
echo "$LOG_PREFIX Kéo mã nguồn mới nhất từ nhánh $BRANCH..."
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git pull origin "$BRANCH"

case "$SERVICE" in
  frontend)
    echo "$LOG_PREFIX Đang triển khai Frontend..."

    # Phương án 1: Deploy bằng Docker Compose (Khuyến nghị đồng bộ)
    if [ -f "$PROJECT_ROOT/frontend/docker-compose.yml" ]; then
      echo "$LOG_PREFIX Build và khởi động lại container Frontend qua Docker Compose..."
      docker compose -f frontend/docker-compose.yml up -d --build --force-recreate
      docker image prune -f
    # Phương án 2: Deploy trực tiếp bằng Bun (nếu máy chủ chạy Bun + PM2)
    elif command -v bun &> /dev/null; then
      echo "$LOG_PREFIX Build Frontend bằng Bun và khởi động lại qua PM2..."
      cd "$PROJECT_ROOT/frontend"
      bun install --frozen-lockfile
      bun run build:demo
      if command -v pm2 &> /dev/null; then
        pm2 restart agentic-frontend || pm2 start server.ts --name "agentic-frontend" --interpreter "$(which bun)"
      fi
    else
      echo "$LOG_PREFIX [LỖI] Không tìm thấy Docker hoặc Bun để deploy Frontend!"
      exit 1
    fi
    echo "$LOG_PREFIX >>> Deploy Frontend THÀNH CÔNG!"
    ;;

  user-service)
    echo "$LOG_PREFIX Đang triển khai user-service..."
    docker compose -f backend/docker-compose.yml up -d --build --force-recreate user-service
    echo "$LOG_PREFIX >>> Deploy user-service THÀNH CÔNG!"
    ;;

  storage-service)
    echo "$LOG_PREFIX Đang triển khai storage-service..."
    docker compose -f backend/docker-compose.yml up -d --build --force-recreate storage-service
    echo "$LOG_PREFIX >>> Deploy storage-service THÀNH CÔNG!"
    ;;

  api-gateway)
    echo "$LOG_PREFIX Đang triển khai api-gateway..."
    docker compose -f backend/docker-compose.yml up -d --build --force-recreate api-gateway
    echo "$LOG_PREFIX >>> Deploy api-gateway THÀNH CÔNG!"
    ;;

  *)
    echo "$LOG_PREFIX [LỖI] Dịch vụ không xác định: $SERVICE"
    exit 1
    ;;
esac

echo "$LOG_PREFIX Hoàn tất quy trình deploy cho: $SERVICE"
