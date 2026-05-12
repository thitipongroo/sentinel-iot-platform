FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --silent
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=${BACKEND_URL}
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup -S sentinel && adduser -S sentinel -G sentinel
COPY --from=builder /app/public* ./public/
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
USER sentinel
EXPOSE 3000
CMD ["node", "server.js"]
