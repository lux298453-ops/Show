# ==========================================
# 前端构建阶段 (Node 18 Alpine)
# ==========================================
FROM node:18-alpine AS build-stage
WORKDIR /app/wireforge-frontend

# 安装依赖
COPY wireforge-frontend/package*.json ./
RUN npm install

# 拷贝源码并构建生产包
COPY wireforge-frontend/ .
# Share the exact control assets packaged into backend-generated HTML.
COPY wireforge-backend/src/main/resources/prototype/controls.* /app/wireforge-backend/src/main/resources/prototype/
# The bundled Nginx proxies /api locally. Override only for a deliberate split deployment.
ARG VITE_API_BASE_URL=""
RUN VITE_API_BASE_URL="$VITE_API_BASE_URL" npm run build

# ==========================================
# 生产运行阶段 (Nginx Alpine)
# ==========================================
FROM nginx:stable-alpine AS production-stage

# 拷贝构建产物到 Nginx 默认静态目录
COPY --from=build-stage /app/wireforge-frontend/dist /usr/share/nginx/html

# 拷贝自定义 Nginx 配置文件
COPY wireforge-frontend/nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
