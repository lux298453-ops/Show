# WireForge 全栈平台部署与上线 SOP 标准作业程序

> **适用场景**：WireForge 前后端分离项目（Vue 3 + Spring Boot 3 + MySQL + AI视觉识别）由本地/旧环境向亚太全免费云原生高可用架构迁移与部署。  
> **文档版本**：v1.0  
> **最后更新**：2026-10-08  

---

## 一、 部署架构与服务清单 (Service Inventory)

本次部署采用了 **“亚太同城低延迟 + 全球边缘 CDN”** 的纯免费云架构：

| 角色 | 使用平台 | 官方网址 | 选型原因与配置规格 |
| :--- | :--- | :--- | :--- |
| **代码托管 & CI/CD** | **GitHub** | [github.com](https://github.com) | 集中管理源码，作为 Render 和 Cloudflare Pages 的自动化构建触发源。 |
| **云数据库** | **TiDB Cloud** *(PingCAP)* | [tidbcloud.com](https://tidbcloud.com) | **替换即将过期的 Aiven**。Serverless 免费版（5 RU/月），完全兼容 MySQL 8.0 语法；节点选在 **AWS 新加坡 (ap-southeast-1)**。 |
| **后端 API 服务** | **Render** | [render.com](https://render.com) | 托管 Dockerized Spring Boot 后端。机房选在 **Singapore (Southeast Asia)**，与 TiDB 同机房，内网级 2~5ms 极低延迟。 |
| **前端静态托管 & CDN** | **Cloudflare Pages** | [dash.cloudflare.com](https://dash.cloudflare.com) | 免费无限流量，享受全球 300+ 节点的 Anycast 边缘网络，国内访问极速且抗大并发。 |
| **大模型能力支持** | **Flintic AI** *(中转站)* | `api.flintic.uk` | OpenAI 兼容接口，驱动设计稿 OCR/多模态分析与整页线框原型直出。 |

---

## 二、 详细部署全流程 (Step-by-Step SOP)

### 阶段一：云数据库准备（TiDB Cloud 新加坡）

1. **注册与创建集群**：
   - 访问 [TiDB Cloud 控制台](https://tidbcloud.com) 注册/登录；
   - 点击 **Create Cluster** ➔ 选择 **Serverless (Free)**；
   - **Cloud Provider & Region**：务必选择 **AWS** ➔ **Singapore (ap-southeast-1)**；
   - 点击创建，几秒内即可就绪。
2. **获取连接凭证**：
   - 在集群详情页点击 **Connect** ➔ 驱动选择 **General**；
   - 获取主机地址（形如 `gateway01.ap-southeast-1.prod.aws.tidbcloud.com`，端口 `4000`）；
   - 点击 **Generate Password** 获取数据库密码，并保存好用户名（形如 `36N7TvsCAY1LhoF.root`）。
3. **验证数据库初始化**：
   - 默认数据库名为 `wireforge`（JDBC URL 中加上 `createDatabaseIfNotExist=true` 会自动创建库名）；
   - 后端启动时自带 `DatabaseMigrationConfig` 与 `schema.sql`，会自动建表，无需手动导出导入 SQL。

---

### 阶段二：后端 API 容器化部署（Render 新加坡）

1. **新建 Web Service**：
   - 访问 [Render Dashboard](https://dashboard.render.com) ➔ 点击右上角 **New +** ➔ **Web Service**；
   - 绑定 GitHub 账号，选择仓库 **`lux298453-ops/Show`** ➔ 点击 **Connect**。
2. **基础配置项**：
   - **Name**：`wireforge-backend-sg`（建议带 `-sg` 后缀以便区分地域）；
   - **Region**：**【核心关键】必须选择 `Singapore (Southeast Asia)`**；
   - **Branch**：`main`；
   - **Root Directory**：**保持绝对空白（不要填任何内容）**，因为根目录 Dockerfile 需要读取 `wireforge-backend` 和 `designs` 两个目录；
   - **Runtime**：系统会自动识别为 `Docker`；
   - **Instance Type**：选择 **Free ($0/month)**。
3. **环境变量配置 (Environment Variables)**：
   展开下方 **Environment Variables**，添加以下键值对：

   | 环境变量名称 (Key) | 推荐取值 (Value) | 说明 |
   | :--- | :--- | :--- |
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/wireforge?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai&useSSL=true` | TiDB 新加坡 JDBC 链接 |
   | `SPRING_DATASOURCE_USERNAME`| `36N7TvsCAY1LhoF.root` | TiDB 用户名 |
   | `SPRING_DATASOURCE_PASSWORD`| *(您的 TiDB 生成密码)* | **注意：不要带尖括号 `<>`** |
   | `SQL_INIT_MODE` | `never` | 首次建表后设为 never，避免重复重置 |
   | `WIREFORGE_AUTO_ANALYZE` | `false` | 避免每次冷启动重复触发耗时扫描 |
   | `WIREFORGE_MOCK` | `false` | **必须填布尔值 `false` 或 `true`** |
   | `AI_API_KEY` | `sk-79618953...` | 真实 AI 接口调用密钥 |
   | `AI_BASE_URL` | `https://api.flintic.uk` | AI 中转站接口地址 |

4. **开始部署并获取公网域名**：
   - 点击 **Deploy Web Service**；
   - 构建日志中看到 `Tomcat started on port 10000 / 8090` 且状态变为绿色的 **Live**；
   - 复制 Render 顶部自动分配的公网 URL，例如：`https://wireforge-backend-sg.onrender.com`。

---

### 阶段三：后端核心性能优化（已合并进代码）

在将后端迁移到新加坡前，排查并解决了系统最关键的访问缓慢瓶颈：
- **原始问题**：每次前端打开项目调用 `/api/projects/{id}/prototype` 时，后端在 for 循环中为每个页面逐一查询元素、交互和标注，产生 **105 次串行数据库往返查询**（N+1 查询瓶颈）；
- **优化方案**：在 `ProjectService.java` 中将 105 次查询彻底重构为 **3 次批量 `IN (...)` 聚合查询**（提交 [`5730b36`](https://github.com/lux298453-ops/Show/commit/5730b36)）；
- **收益**：单接口数据库通信开销骤降 95% 以上，页面加载由原先的十几秒缩短至数百毫秒。

---

### 阶段四：前端部署（Cloudflare Pages 全球 CDN）

1. **新建 Pages 应用**：
   - 登录 [Cloudflare 控制台](https://dash.cloudflare.com) ➔ 左侧菜单 **Workers & Pages** ➔ **Create application**；
   - 选择 **Pages** 选项卡 ➔ 点击 **Connect to Git**；
   - 选中 GitHub 仓库 `lux298453-ops/Show` ➔ 点击 **Begin setup**。
2. **构建与输出配置**：
   - **Project name**：自定义（如 `wireforge`，生成形如 `wireforge.pages.dev` 的二级域名）；
   - **Production branch**：`main`；
   - **Framework preset**：选择 **`Vue`**；
   - **Root directory**：**`wireforge-frontend`**（必须指定，因为前端在子目录下）；
   - **Build command**：`npm run build`；
   - **Build output directory**：`dist`。
3. **绑定后端环境变量**：
   - 在构建配置下方展开 **Environment variables (advanced)**；
   - 添加变量：
     - **Variable name**：`VITE_API_BASE_URL`
     - **Value**：`https://wireforge-backend-sg.onrender.com` *(阶段二获得的 Render 后端地址，末尾不要带斜杠)*
4. **发布上线**：
   - 点击 **Save and Deploy**；
   - 等待约 40 秒，构建成功后点击分配的 `xxx.pages.dev` 域名即可直接访问！

---

## 三、 日常运维与持续交付 (CI/CD SOP)

今后您在本地开发或优化代码后，**完全不需要重新在 Render 或 Cloudflare 上手动操作**：

1. **本地开发并推送代码**：
   ```bash
   git add .
   git commit -m "feat: 你的功能描述"
   git push origin main
   ```
2. **全自动持续集成更新**：
   - **前端自动更新**：Cloudflare Pages 监听到 `main` 分支变动，会自动启动 30~50 秒的增量打包并无缝发布；
   - **后端自动更新**：若修改了后端代码，Render 会自动触发 Docker 构建并热重启服务。

---

## 四、 常见避坑与故障排查指南 (Troubleshooting)

| 遇到的错误现象 | 根本原因 | 标准解决步骤 |
| :--- | :--- | :--- |
| **`CommunicationsException: Communications link failure`** | 1. 数据库账号密码带了 `<>` 尖括号；<br>2. 数据库实例未在新加坡，导致跨国链路被掐断。 | 去除密码中的 `<>` 字符；确认 TiDB 处于 AWS 新加坡可用区。 |
| **`Failed to convert value of type 'String' to 'boolean'; Invalid boolean value [sk-...]`** | 误把 API Key 填到了 `WIREFORGE_MOCK` 中。 | 将 `WIREFORGE_MOCK` 还原为 `false`；将 Key 正确填入独立的 `AI_API_KEY` 变量中。 |
| **一段时间不访问后，第 1 次打开网页转圈等待约 30 秒** | Render 免费版实例在 **连续 15 分钟无访问** 时会自动休眠 (Spin-down)。 | **正常现象**。唤醒后即恢复毫秒级秒开。如需 24 小时常开，可在 [UptimeRobot](https://uptimerobot.com) 注册免费账号，配置每 10 分钟自动 ping 一次后端。 |
| **前端打开显示网络错误或无法加载设计稿** | Cloudflare Pages 的 `VITE_API_BASE_URL` 未配置或仍指向旧地址。 | 在 Cloudflare Pages ➔ **Settings** ➔ **Environment variables** 中更新为 Render 最新的后端地址并重新部署。 |
