FROM node:20-alpine AS builder
WORKDIR /build
COPY agent-ui/package.json agent-ui/package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY agent-ui/ .
RUN npm run build

FROM nginx:1.25-alpine
COPY docker/agent-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html
EXPOSE 80
