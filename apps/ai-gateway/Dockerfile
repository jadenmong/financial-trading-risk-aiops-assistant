FROM node:22.19.0-bookworm-slim@sha256:4a4884e8a44826194dff92ba316264f392056cbe243dcc9fd3551e71cea02b90 AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/ai-gateway/package.json apps/ai-gateway/package.json
COPY apps/ops-console/package.json apps/ops-console/package.json
RUN npm ci
COPY apps/ai-gateway apps/ai-gateway
RUN npm run build -w @risk-aiops/ai-gateway && npm prune --omit=dev

FROM node:22.19.0-bookworm-slim@sha256:4a4884e8a44826194dff92ba316264f392056cbe243dcc9fd3551e71cea02b90
ENV NODE_ENV=production
WORKDIR /workspace
COPY --from=build --chown=node:node /workspace/node_modules ./node_modules
COPY --from=build --chown=node:node /workspace/apps/ai-gateway/dist ./apps/ai-gateway/dist
COPY --from=build --chown=node:node /workspace/apps/ai-gateway/package.json ./apps/ai-gateway/package.json
USER node
EXPOSE 3000
CMD ["node", "apps/ai-gateway/dist/http.js"]
