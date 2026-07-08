# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

ARG VITE_ORDERS_API_URL=http://localhost:8081
ARG VITE_INVENTORY_API_URL=http://localhost:8082
ARG VITE_NOTIFICATIONS_API_URL=http://localhost:8083
ENV VITE_ORDERS_API_URL=$VITE_ORDERS_API_URL
ENV VITE_INVENTORY_API_URL=$VITE_INVENTORY_API_URL
ENV VITE_NOTIFICATIONS_API_URL=$VITE_NOTIFICATIONS_API_URL

COPY . .
RUN npm run build

# ---- Runtime stage ----
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

COPY --chown=nginx:nginx nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
