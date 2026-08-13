FROM node:22.19.0-bookworm-slim@sha256:4a4884e8a44826194dff92ba316264f392056cbe243dcc9fd3551e71cea02b90 AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/ai-gateway/package.json apps/ai-gateway/package.json
COPY apps/ops-console/package.json apps/ops-console/package.json
RUN npm ci
COPY apps/ops-console apps/ops-console
ARG VITE_OIDC_ISSUER
ENV VITE_OIDC_ISSUER=$VITE_OIDC_ISSUER
RUN npm run build -w @risk-aiops/ops-console

FROM nginx:1.29.2-alpine@sha256:61e01287e546aac28a3f56839c136b31f590273f3b41187a36f46f6a03bbfe22
COPY apps/ops-console/nginx.conf /etc/nginx/nginx.conf
COPY --from=build --chown=101:101 /workspace/apps/ops-console/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
