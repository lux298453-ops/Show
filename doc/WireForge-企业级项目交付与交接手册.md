# WireForge 企业级项目交付与交接手册

> **项目名称**：WireForge（AI 原型设计与多人协同交互演练平台）  
> **版本**：v1.0.0-Release  
> **文档适用对象**：运维工程师、后端/前端研发人员、技术负责人、项目交接人  

---

## 一、交付物清单 (Delivery Checklist)

本次交付包含完整的软件产物、数据库初始化脚本、容器化部署配置及相关文档，开箱即用：

| 交付类型 | 文件 / 目录路径 | 说明 |
| :--- | :--- | :--- |
| **后端可执行包** | `wireforge-backend/target/wireforge-backend-0.1.0.jar` | Spring Boot 3 独立运行 Jar 包 (内置 Tomcat 与自愈机制) |
| **前端构建产物** | `wireforge-frontend/dist/` (执行编译后生成) | Vue 3 + Vite 纯静态打包文件，可直接被 Nginx 托管 |
| **基础表结构脚本** | `sql/01_schema.sql` | 干净幂等的 MySQL 8.0 建表 DDL (包含最新拓展字段及中文注释) |
| **初始演示数据** | `init.sql` / `sql/02_init_demo_data.sql` | 包含高保真移动端商城、养成、抽奖、动效整套演示项目数据 |
| **容器化编排** | `docker-compose.yml`, `.env.example` | 一键拉起 MySQL、后端和前端 Nginx，零手动配环境 |
| **前端镜像配置** | `wireforge-frontend/Dockerfile`, `nginx.conf` | 前端生产级多阶段构建与 Nginx 反向代理配置 |
| **一键打包脚本** | `deploy/build_all.bat` (Win) / `build_all.sh` (Linux) | 自动打包前端 + 后端并归集交付包 |

---

## 二、系统架构与技术选型

### 2.1 整体架构拓扑
```
               [ 浏览器用户端 ]
                      │ (HTTP/HTTPS :80 / :5173)
                      ▼
            ┌───────────────────┐
            │  Nginx 反向代理   │
            └─────────┬─────────┘
                      │
        ┌─────────────┴─────────────┐
        ▼                           ▼
[ 静态资源 SPA ]            [ 后端接口 /api/ ]
(Vue 3 + Vite 产物)    (Spring Boot 3 :8090/:8091)
                                    │
                                    ├────► [ MySQL 8.0 数据库 ]
                                    │
                                    ├────► [ Playwright Chromium (无头渲染引擎) ]
                                    │
                                    └────► [ 外部 AI 识别模型 API (可选) ]
```

### 2.2 技术栈清单
* **前端 (wireforge-frontend)**：
  * 框架：Vue 3 (Composition API, `<script setup>`) + TypeScript
  * 构建工具：Vite 6.0
  * UI 组件库：Element Plus 2.9 + Lucide Icons
  * 画布引擎：Vue Flow 1.48 (支持缩放、多节点连线、视口拖拽)
  * 样式：Tailwind CSS 3.4 + SASS
* **后端 (wireforge-backend)**：
  * 框架：Java 17 + Spring Boot 3.2.5
  * ORM 数据持久层：MyBatis-Plus 3.5.5 + HikariCP 高性能连接池
  * 原型自动化验证：Microsoft Playwright 1.49 (无头浏览器真实点击与交互链路回放)
  * 架构特色：内置 `DatabaseMigrationConfig` 启动期动态表结构自愈组件
* **数据库**：
  * MySQL 8.0+ (字符集采用 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`)

---

## 三、软硬件运行环境要求

* **服务器推荐配置**：
  * 最低要求：2 核 CPU、4 GB 内存、20 GB 磁盘空间
  * 生产/多人推荐：4 核 CPU、8 GB 内存、50 GB 磁盘空间
* **依赖环境版本（传统非容器化部署时需要）**：
  * JDK：`Eclipse Temurin 17` 或 `OpenJDK 17+`
  * Node.js：`Node 18.x` 或 `20.x` (LTS 版本)
  * Nginx：`1.20+`
  * 数据库：`MySQL 8.0.x`
* **网络与端口规划**：
  * 前端 Web 端口：`80` (生产) / `5173` (本地开发)
  * 后端服务端口：`8090` (生产容器) / `8091` (本地开发)
  * 数据库端口：`3306`

---

## 四、全场景部署指南

### 方案 A：Docker Compose 一键部署（公司内网最推荐，最省事）

无论在公司内网物理机还是云服务器上，只要安装了 Docker 和 Docker Compose，仅需一行命令即可拉起全部环境。

1. **获取代码并进入目录**：
   ```bash
   cd /opt/wireforge  # 项目根目录
   ```
2. **准备环境配置**：
   ```bash
   cp .env.example .env
   # 按需修改 .env 中的 MySQL 密码或端口（默认密码为 wireforge_root_2026）
   ```
3. **一键拉起全套服务**：
   ```bash
   docker compose up -d --build
   ```
4. **验证部署状态**：
   ```bash
   docker compose ps
   # 看到 wireforge-mysql、wireforge-backend、wireforge-frontend 均为 Up (healthy) 状态
   ```
5. **访问应用**：
   在浏览器输入服务器 IP 地址：`http://<服务器内网或公网IP>` 即可打开系统，内置演示数据已自动初始化完毕。

---

### 方案 B：公司传统 Linux 服务器手动部署 (Nginx + Jar 包 + MySQL)

如果公司运维要求在现有 Linux 主机上独立运行各组件：

#### 1. 数据库初始化
登录公司的 MySQL 8.0 实例，执行建表与数据脚本：
```bash
mysql -u root -p -e "CREATE DATABASE wireforge DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p wireforge < sql/01_schema.sql
mysql -u root -p wireforge < init.sql
```

#### 2. 后端部署
将打包好的 `wireforge-backend-0.1.0.jar` 上传至 `/opt/wireforge/backend/`：
```bash
# 创建运行目录
mkdir -p /opt/wireforge/backend && cd /opt/wireforge/backend

# 后台启动服务 (指定生产配置与数据库连接)
nohup java -jar wireforge-backend-0.1.0.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/wireforge?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai&useSSL=false" \
  --spring.datasource.username=root \
  --spring.datasource.password=your_password \
  --server.port=8090 \
  > backend.log 2>&1 &

# 检查服务健康状态
curl http://127.0.0.1:8090/api/projects
```

#### 3. 前端部署
将前端打包后的 `dist/` 文件夹上传至 `/var/www/wireforge/`，并配置 Nginx：
```nginx
server {
    listen       80;
    server_name  wireforge.yourcompany.com; # 或内网 IP

    root   /var/www/wireforge;
    index  index.html;

    # Vue3 单页路由刷新重定向
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端接口反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8090/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 180s;
        proxy_read_timeout 180s;
    }
}
```
配置完成后重载 Nginx：`nginx -s reload`。

---

### 方案 C：公网免费云托管部署 (Vercel + Render + 云数据库)

适合毕业设计、对外演示或异地办公测试：
1. **数据库**：使用 Aiven / TiDB Cloud Serverless 托管 MySQL 8.0。
2. **后端 (Render)**：
   - 连接 GitHub 仓库，选择 Docker 构建（根目录 `Dockerfile`）。
   - 配置环境变量 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`。
3. **前端 (Vercel)**：
   - 导入仓库，Root Directory 选择 `wireforge-frontend`。
   - 配置环境变量 `VITE_API_BASE_URL=https://<你的Render后端域名>`。

---

## 五、核心业务特性与技术设计解析 (研发接盘必读)

为了方便后续迭代维护，此处记录系统几项关键的架构设计与实现细节：

### 5.1 页面级独占编辑锁 (Concurrent Page-Level Lock)
* **设计目标**：防止多人在多人协同环境下同时微调同一个页面发生修改相互覆盖，同时**不阻断**其他成员对项目中不同页面的微调。
* **技术实现**：
  * 后端维护内存并发哈希表 `Map<Long, PageEditLock>`，记录每个页面当前持有锁的 `clientId`、`userName`、`expireTime`。
  * 前端进入微调模式时触发锁定请求，并通过定时器进行 10 秒心跳续租；微调退出或保存时主动释放。
  * 若前端异常退出，锁在 45 秒后由于心跳超时自动过期释放。
  * **视觉隔离机制**：锁定的遮罩层严格收敛在 `PageCanvas.vue` 内部的手机真机可视区域（`.canvas-slot`，375x812），绝不遮挡左侧原稿、右侧标注列表与画布顶部工具条，兼顾协同安全与视觉体验。

### 5.2 业务说明排序与引线标注拖拽
* **数据模型**：`annotation` 表通过 `sort_order`（序号）、`box_x/box_y`（说明卡片画布坐标）、`elbow_x`（折线转折点坐标）保存排版状态。
* **坐标系计算**：
  * 引线起点锚点采用相对线框的百分比/逻辑坐标，终点连接右侧说明框。
  * 用户可以直接在画布上拖动说明框和引线拐角，前端防抖实时保存至后端，刷新画布即可完全还原排版。

### 5.3 数据库启动期动态自愈迁移 (`DatabaseMigrationConfig`)
* **背景**：团队多人协作或多套环境（本地、测试服、生产服）拉取代码时，常因数据库表版本不同抛出 `Unknown column 'xxx'` 异常。
* **机制**：
  * 在 Spring Boot 启动时由 `@PostConstruct` 触发，自动对比当前数据库系统字典，安全追加缺失列（如 `annotation.sort_order`、`page.canvas_x` 等）。
  * 即使将项目部署在没有任何历史变更记录的旧数据库上，服务也能自动补全结构平滑拉起。

---

## 六、常见运维与排障指南 (FAQ)

### Q1: 接口请求返回 `500`，错误信息显示 `系统异常: null` 或 `Communications link failure`？
* **原因**：后端连接不上数据库。
* **排查方法**：
  1. 检查 MySQL 服务是否正在运行（如云托管 Aiven 是否由于试用期或无流量自动 Power Off）。
  2. 检查 `application-prod.yml` 或环境变量中的 `SPRING_DATASOURCE_URL` 主机 IP、端口和密码是否正确。
  3. 尝试在服务器上手动执行 `mysql -h <host> -u <user> -p` 验证连通性。

### Q2: 前端刷新页面后浏览器报 `404 Not Found`？
* **原因**：Vue 3 采用了 HTML5 History 路由模式，直接刷新时 Nginx 找不到该静态文件路径。
* **解决办法**：确保 Nginx 中包含以下配置：
  ```nginx
  location / {
      try_files $uri $uri/ /index.html;
  }
  ```

### Q3: 端口被占用无法启动？
* **解决办法**：
  * Linux 查看占用：`lsof -i :8090` 或 `netstat -tlpn | grep 8090`，随后 `kill -9 <PID>`。
  * Windows 查看占用：`netstat -ano | findstr :8090`，随后在任务管理器或 `taskkill /F /PID <PID>` 关闭。

---

## 七、交接签收表 (交付留存)

* **交接人 (开发)**：____________________ 日期：2026-09-16
* **接收人 (运维/开发)**：________________ 日期：________________
* **交付物核验结果**：
  - [x] Git 仓库全部提交并同步至主干分支
  - [x] 提供完整的全量 SQL 建表与初始数据脚本
  - [x] 提供一键打包脚本与 Docker 编排配置
  - [x] 成功在目标机器上跑通主流程验证
