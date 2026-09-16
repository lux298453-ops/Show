# ==========================================
# 前端构建阶段 (Node 18 Alpine)
# ==========================================
FROM node:18-alpine AS build-stage
WORKDIR /app

# 安装依赖
COPY package*.json ./
RUN npm install

# 拷贝源码并构建生产包
COPY . .
RUN npm run build

# ==========================================
# 生产运行阶段 (Nginx Alpine)
# ==========================================
FROM nginx:stable-alpine AS production-stage

# 拷贝构建产物到 Nginx 默认静态目录
COPY --from=build-stage /app/dist /usr/share/nginx/html

# 拷贝自定义 Nginx 配置文件
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
