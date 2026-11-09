# ==========================================
# Stage 1: Build Frontend với Bun
# ==========================================
FROM oven/bun:latest AS builder
WORKDIR /app

# Cache dependency layer
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile

# Copy mã nguồn
COPY . .

# Build argument cho API Gateway URL (có thể override khi docker build)
ARG VITE_API_GATEWAY_URL=http://localhost:8089
ENV VITE_API_GATEWAY_URL=${VITE_API_GATEWAY_URL}

# Build production bundle
RUN bun run build:demo

# ==========================================
# Stage 2: Production Server siêu nhẹ với Bun
# ==========================================
FROM oven/bun:latest AS runner
WORKDIR /app

# Chỉ copy thư mục build tĩnh và file server
COPY --from=builder /app/demo/dist ./demo/dist
COPY --from=builder /app/server.ts ./

ENV PORT=3000
EXPOSE 3000

CMD ["bun", "run", "server.ts"]
