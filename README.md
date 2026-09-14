# Show (WireForge - AI 交互原型平台)

WireForge 是一款面向视觉设计稿的 AI 原型生成与交互演练平台，支持整页高保真渲染、画布交互跳转、移动端真机模拟器预览等能力。

---

## 🏗️ 架构与部署矩阵

| 组件 | 推荐部署平台 | 说明 |
| :--- | :--- | :--- |
| **前端 (wireforge-frontend)** | **Vercel** | Vue 3 + Vite + Tailwind CSS + Element Plus |
| **后端 (wireforge-backend)** | **Render** | Spring Boot 3 + Java 17 + Docker |
| **数据库** | **Aiven MySQL** | 云原生高可用托管 MySQL 8.0+ |

---

## 🚀 极速部署指南 (Vercel + Render + Aiven MySQL)

### 第一步：初始化 Aiven MySQL 数据库

1. 登录 [Aiven Console](https://console.aiven.io/)，创建一个 **MySQL** 实例（可选择免费试用或对应云区）。
2. 等待实例运行，在 Service Overview 中获取：
   - **Host** (如 `mysql-xxxx.aivencloud.com`)
   - **Port** (如 `12345`)
   - **User** (如 `avnadmin`)
   - **Password**
   - **Database Name** (默认为 `defaultdb`，或自行建库 `wireforge`)
3. 在本地或通过任何 MySQL 客户端（如 Navicat / DBeaver / MySQL CLI）执行仓库根目录下的 `init.sql`，导入全套表结构与项目原型数据：
   ```bash
   mysql -h <AIVEN_HOST> -P <AIVEN_PORT> -u <AIVEN_USER> -p <DB_NAME> < init.sql
   ```

---

### 第二步：在 Render 上部署后端服务

1. 登录 [Render Dashboard](https://dashboard.render.com/)，点击 **New +** -> **Web Service**。
2. 连接 GitHub 仓库 `lux298453-ops/Show`。
3. 配置参数：
   - **Name**: `wireforge-backend` (自定义)
   - **Language**: `Docker`
   - **Region**: 建议选择与 Aiven MySQL 相同或邻近区域（如 Singapore / Frankfurt / Oregon）
   - **Dockerfile Path**: `./Dockerfile`（使用根目录 Dockerfile，包含设计稿素材与源码打包）
4. 在 **Environment Variables** (环境变量) 中添加以下配置：
   | 环境变量名 | 示例值 | 说明 |
   | :--- | :--- | :--- |
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://<AIVEN_HOST>:<AIVEN_PORT>/<DB_NAME>?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai&sslMode=REQUIRED&useSSL=true` | Aiven MySQL 连接串（必须开启 SSL） |
   | `SPRING_DATASOURCE_USERNAME` | `avnadmin` | Aiven 用户名 |
   | `SPRING_DATASOURCE_PASSWORD` | `<your_aiven_password>` | Aiven 密码 |
   | `SQL_INIT_MODE` | `never` | 线上运行时避免重复覆盖执行建表脚本 |
   | `WIREFORGE_AUTO_ANALYZE` | `false` | 关闭启动时自动分析，保证启动秒级响应 |
5. 点击 **Deploy Web Service** 开始构建，成功后获取 Render 分配的后端公网地址（例如：`https://wireforge-backend-xxxx.onrender.com`）。

---

### 第三步：在 Vercel 上部署前端应用

1. 登录 [Vercel Dashboard](https://vercel.com/)，点击 **Add New...** -> **Project**。
2. 导入 GitHub 仓库 `lux298453-ops/Show`。
3. 在配置界面设置：
   - **Framework Preset**: `Vite`
   - **Root Directory**: 点击 Edit，选择 `wireforge-frontend`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
4. 展开 **Environment Variables**，添加：
   | 变量名 | 变量值 |
   | :--- | :--- |
   | `VITE_API_BASE_URL` | `https://wireforge-backend-xxxx.onrender.com` (你的 Render 后端公网域名，末尾不带斜杠) |
5. 点击 **Deploy**，等待部署完成后即可通过 Vercel 分配的域名访问完整原型平台！

---

## 💻 本地运行指南

### 1. 后端运行
- JDK 17+, Maven 3.9+
- 配置本地 MySQL 8 并导入 `init.sql`
- 启动：
  ```bash
  cd wireforge-backend
  mvn spring-boot:run
  ```

### 2. 前端运行
- Node.js 18+
- 安装依赖并启动：
  ```bash
  cd wireforge-frontend
  npm install
  npm run dev
  ```
  访问 `http://localhost:5173` 即可。
