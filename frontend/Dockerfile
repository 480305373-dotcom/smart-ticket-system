FROM nginx:1.28-alpine AS runtime
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=20s --timeout=3s CMD wget -q --spider http://127.0.0.1/health || exit 1

FROM runtime AS prebuilt
COPY frontend/dist /usr/share/nginx/html

FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm test && npm run build

FROM runtime AS production
COPY --from=build /app/dist /usr/share/nginx/html
