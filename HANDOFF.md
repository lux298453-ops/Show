# WireForge 项目交接文档

> **最后更新**: 2026-08-27
> **当前状态**: 核心功能完成，9 页面全流程跑通，交互验证 0 failures，但视觉验证缺失、部署包过时

---

## 一、项目概述

**WireForge** 是一个「设计稿截图 → 低保真 HTML 线框图」的转换工具。

**产品定位**：用户上传 UI 设计截图，AI 识别出各个区域的功能（按钮、文字、输入框、卡片等），然后用**方框+文字标签**在画布上标出每个区域是干什么的。不需要图标、不需要还原颜色，只要能看懂原型图各部分的功能用途即可。

**核心流水线**：设计稿截图 → AI 语义识别（提取元素/坐标/交互） → 确定性渲染引擎（零 AI）用方框+标签生成 HTML → 支持页面间导航。

**当前状态**：Spring Boot + Vue3 + MySQL，已跑通 9 页面项目（ID 390-398），交互验证 0 failures。

**⚠️ 重要**：`verify=0 failures` 只意味着交互点击消息正确，不检查视觉渲染质量。详见第九节。

---

## 二、技术栈

| 层 | 技术 | 版本 | 说明 |
|---|---|---|---|
| 后端 | Spring Boot + Java | 3.2.5 / JDK 17 | 主要语言 Java |
| ORM | MyBatis-Plus | 3.5.5 | 自动驼峰映射 |
| 数据库 | MySQL | 8.0 | 5 张表，schema.sql 幂等 |
| 前端 | Vue 3 + TypeScript + Vite | 3.5 / 6.0 | SPA，3 个页面 |
| UI 库 | Element Plus | 2.9 | 按钮/复选框/对话框等 |
| 画布 | Vue Flow | 1.48 | 无限画布，拖拽/缩放 |
| AI | OpenAI 兼容 API | GPT-5.5 | 只做语义提取，不做渲染 |
| 验证 | Playwright | 1.49 | Chromium headless 截图+交互验证 |
| 颜色采样 | Java AWT | JDK 17 | BufferedImage 像素采样 |

---

## 三、目录结构

```
html不是很好版/
├── wireforge-backend/              # 后端 (Spring Boot)
│   ├── pom.xml
│   ├── src/main/java/com/wireforge/
│   │   ├── WireforgeApplication.java      # 启动入口
│   │   ├── ai/                            # AI 识别层
│   │   │   ├── AiClient.java              # HTTP 调 AI API
│   │   │   ├── HtmlRenderer.java          # Playwright headless 验证
│   │   │   └── WireframePrompt.java       # SYSTEM_PROMPT（识别指令）
│   │   ├── service/                       # 核心业务逻辑
│   │   │   ├── AnalyzeService.java        # 分析流程编排
│   │   │   ├── AppMapService.java         # 共享 Tab 栏检测
│   │   │   ├── AssetService.java          # 素材库
│   │   │   ├── DesignColorSampler.java    # 像素采色
│   │   │   ├── ProjectService.java        # 项目全流程
│   │   │   └── TemplateHtmlRenderer.java  # 核心渲染引擎 (~1130 行)
│   │   ├── controller/
│   │   │   ├── AssetController.java       # /api/assets/*
│   │   │   └── ProjectController.java     # /api/projects/*
│   │   ├── entity/                        # 数据实体
│   │   │   ├── Project.java               # 含 app_map
│   │   │   ├── Page.java                  # 含 analyzed/html_content
│   │   │   ├── Element.java               # UI 元素
│   │   │   ├── Interaction.java           # 交互关系
│   │   │   └── Annotation.java            # 标注
│   │   ├── mapper/                        # 5 个 Mapper
│   │   ├── config/
│   │   │   ├── AutoAnalyzeRunner.java     # 启动时自动分析
│   │   │   └── WebConfig.java             # CORS
│   │   └── common/
│   │       ├── Result.java                # {code, data, message}
│   │       └── GlobalExceptionHandler.java
│   └── src/main/resources/
│       ├── application.yml
│       └── schema.sql                     # 幂等建表
├── wireforge-frontend/             # 前端 (Vue3 + Vite)
│   ├── package.json
│   ├── vite.config.ts                     # 端口 5173
│   └── src/
│       ├── api/
│       │   ├── http.ts                    # Axios，自动解包 Result
│       │   └── project.ts                 # 项目 API
│       ├── router/index.ts                # 3 条路由
│       ├── types.ts
│       ├── components/
│       │   ├── PageCanvas.vue             # iframe 画布 + runtime 注入
│       │   ├── WireframeElement.vue
│       │   └── PrototypeNode.vue
│       └── views/
│           ├── ProjectList.vue
│           ├── ProjectDetail.vue
│           └── PrototypeView.vue          # 无限画布 + 原型预览
├── designs/                         # 设计稿图片
└── wireforge-dist/                  # 部署包（⚠️ 过时，需重建）
```

---

## 四、核心架构

### 4.1 数据流

```
设计稿图片 (PNG/JPG)
  → [scan] 扫描目录，创建 Page 记录 (analyzed=0, html_content=NULL)
  → [analyze] AI 语义识别 → Element / Interaction / Annotation
  → [AppMap] 共识投票检测共享 Tab 栏 → project.app_map JSON
  → [TemplateHtmlRenderer] 确定性 HTML 生成 → page.html_content
  → [Playwright verify] 浏览器交互验证 → 0 failures?
  → 前端 iframe 展示
```

### 4.2 数据库 Schema

```sql
-- 5 张表，schema.sql 幂等（IF NOT EXISTS）
project:     id, name, description, cover_image, app_map (TEXT/JSON)
page:        id, project_id, name, background_image (绝对路径),
             canvas_width(375), canvas_height(812), canvas_x, canvas_y,
             sort_order, analyzed(0/1), html_content(LONGTEXT)
element:     id, page_id, type, label, asset_id, position_x/y, width, height,
             style(JSON), created_by('ai')
interaction: id, element_id, trigger_type('click'), action_type,
             target_page_id, params(JSON)
annotation:  id, page_id, element_id, text, position_x/y, box_x/y,
             anchor_x/y, elbow_x
```

**关键字段说明**：
- `page.background_image`：存储**绝对 Windows 路径**（如 `D:/idea/Project/.../designs/xxx.png`）
- `project.app_map`：JSON，存储共享 Tab 栏配置（哪些页共享、标签名+跳转目标）
- `page.analyzed`：0=未分析，1=已分析。只有 1 的页面才会渲染 HTML
- `page.html_content`：LARGE TEXT，存储完整的 HTML 页面（Stitch 式整页直出）

### 4.3 AI 识别产物

AI 从每张设计稿提取 JSON（存入 Element + Interaction 表）：

```json
{
  "page_name": "首页",
  "elements": [
    {
      "type": "button|icon|text|avatar|container|image|input|search|switch|badge|rating|divider|tabs|effect|other|navbar|banner|background|progress|checkbox|radio|slider|list",
      "label": "元素上的真实文字（无文字则空字符串）",
      "bbox": [x, y, width, height],
      "interaction": { "trigger": "click", "action": "navigate|back|popup|tab_switch", "target": "页面名" },
      "description": "功能说明（中文，用于标注）"
    }
  ]
}
```

**交互类型**：
- `navigate` — 跳转到目标页
- `back` — 返回上一页
- `popup` / `modal` — 弹出浮层（目标页内容作为隐藏层注入）
- `tab_switch` — 切换底部 Tab

**SYSTEM_PROMPT 位置**: `WireframePrompt.java`（~100 行中文指令），规则非常详细，包括 20+ 元素类型、10 条识别规则、4 种交互动作。

### 4.4 确定性渲染引擎

**`TemplateHtmlRenderer.java`**（~1130 行）是核心，零 AI 参与。

#### 渲染流程

```
render()
  → 构建 PageBundle (页面+元素+交互)
  → 加载 app_map，判断是否注入 Tab 栏
  → 渲染弹窗浮层 (wf-modal)
  → renderBody()
      → containDepth() z-order 排序
      → collapseDecorPiles() 场景简化（贪心非重叠坍缩）
      → normalizeCardRows() 卡片网格对齐
      → detectGapArt() 间隙色块
      → 分层: blocks (背景) + texts (前景)
      → 逐元素 renderElement() → 按类型分派
  → renderTabBar() 共享底部导航栏
  → pageBackground() 设计稿背景色采样
  → doc() 输出完整 HTML 文档
```

#### 文字系统

| 方法 | 作用 |
|---|---|
| `fs()` | 根据 bbox 宽度计算字号，CJK=1.0em，ASCII=0.56em |
| `fitTextGeometry()` | 文字框太窄时扩展（不改字号） |
| `shrinkAwayFromPlaced()` | 碰撞避让：新框不与已有框重叠 |
| `isDuplicateText()` | 重复文字检测 + 图标邻近文字抑制 |
| `estLineCount()` | 三态行模型：显式换行 / 真实自动换行 / 单行 |
| `textStyle()` | 三号模型（normal/tiny/large）+ 白字对比色 |
| `shortLabelCap` | 短标签最大 16px |

#### 装饰块分类

- **blocks 层**：background / image / container / effect / other / avatar / banner — z-index 底层
- **texts 层**：text / button / icon / input / search / switch / badge / rating / tabs / divider / navbar — z-index 上层

#### 元素类型 → HTML 映射

| type | HTML class | 视觉 |
|---|---|---|
| text | wf-text | 实际文字+自适应字号 |
| button | wf-btn | 填充色圆角矩形+对比色文字 |
| icon | wf-icon | 小方块+图标名缩写 |
| image | wf-img | 低饱和采样色块+微渐变 |
| avatar | wf-avatar | 圆形采样色块 |
| container | wf-card | 圆角矩形+描边 |
| input | wf-input | 圆角矩形+提示文字 |
| search | wf-search | 输入框样式 |
| switch | wf-switch | 开关样式 |
| badge | wf-badge | 小标签 |
| rating | wf-rating | 星级 |
| tabs | wf-tabs | 标签栏 |
| divider | wf-divider | 分割线 |
| effect | 半透明块 | 仅 blocks 层 |
| navbar | wf-nav | 顶部导航条 |
| background | wf-bg | 全屏底色块 |

---

## 五、API 接口

### ProjectController (`/api/projects`)

| Method | Endpoint | 说明 |
|---|---|---|
| GET | `/api/projects` | 列出所有项目 |
| POST | `/api/projects` | 创建项目 `{name, description}` |
| DELETE | `/api/projects/{id}` | 删除（级联） |
| GET | `/api/projects/{id}` | 获取单个 |
| POST | `/api/projects/{id}/scan` | 扫描设计稿目录 |
| POST | `/api/projects/{id}/analyze` | AI 分析+渲染 |
| POST | `/api/projects/{id}/appmap` | 重建 Tab 栏+重渲染 |
| POST | `/api/projects/{id}/verify` | Playwright 交互验证 |
| GET | `/api/projects/{id}/prototype` | 完整原型数据 |
| PUT | `/api/projects/{id}/annotations/{annId}` | 更新标注 |
| PUT | `/api/projects/{id}/pages/{pageId}/position` | 更新画布位置 |
| POST | `/api/projects/{id}/pages/{pageId}/regenerate-html` | 重渲染单页 |
| PUT | `/api/projects/{id}/pages/{pageId}/html` | 保存编辑 HTML |

### AssetController (`/api/assets`)

| Method | Endpoint | 说明 |
|---|---|---|
| GET | `/api/assets/{assetId}` | 素材二进制流 |
| GET | `/api/assets/list` | 素材列表 |
| GET | `/api/assets/categories` | 分类 |
| GET | `/api/assets/random/{category}` | 随机素材 |

---

## 六、配置

### application.yml

```yaml
server:
  port: ${SERVER_PORT:8090}
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/${DB_NAME:wireforge}?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
    username: ${DB_USER:root}
    password: ${DB_PASS:abc123}
  sql.init.mode: always             # 每次启动执行 schema.sql（幂等）
wireforge:
  designs-dir: ${DESIGNS_DIR:./designs}
  ai:
    base-url: ${AI_BASE_URL:https://api.flintic.uk}
    api-key: ${AI_API_KEY:sk-xxx}
    model: ${AI_MODEL:gpt-5.5}
    max-output-tokens: 32768
    timeout-seconds: 180
    mock: false
  verify: true                      # 渲染后 Playwright 验证
  auto-analyze: true                # 启动时自动分析
```

### vite.config.ts

```typescript
server: {
  port: 5173,
  proxy: {
    '/api': process.env.VITE_API_TARGET || 'http://localhost:8090',
    '/files': process.env.VITE_API_TARGET || 'http://localhost:8090',
  },
}
```

---

## 七、启动方式

### 标准流程

```bash
# 1. MySQL
# 确保 wireforge 数据库存在

# 2. 后端
cd wireforge-backend
mvn -q -DskipTests package
java -jar target/wireforge-backend-0.1.0.jar \
  --spring.profiles.active=dev \
  --wireforge.auto-analyze=false

# 3. 前端
cd wireforge-frontend
npm install
VITE_API_TARGET=http://localhost:8091 npm run dev
```

### 开发环境 (application-dev.yml)

```yaml
server:
  port: 8091
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/wireforge_dev?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
    password: abc123
wireforge:
  designs-dir: D:/idea/Project/html版本/html不是很好版/designs
  auto-analyze: false
```

### 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | 8090 | 后端端口 |
| `DB_NAME` | wireforge | 数据库名 |
| `DB_USER` | root | 数据库用户 |
| `DB_PASS` | abc123 | 数据库密码 |
| `DESIGNS_DIR` | ./designs | 设计稿目录 |
| `AI_BASE_URL` | https://api.flintic.uk | AI API 地址 |
| `AI_API_KEY` | sk-xxx | AI API Key |
| `AI_MODEL` | gpt-5.5 | AI 模型 |
| `VITE_API_TARGET` | http://localhost:8090 | 前端代理目标 |

---

## 八、关键概念

| 术语 | 含义 |
|---|---|
| **app_map** | project.app_map JSON 字段，存储共享 Tab 栏配置 |
| **Tab 栏** | 多页面 App 共享的底部导航栏（如家园/商城/收集），AppMapService 共识投票检测 |
| **PageBundle** | TemplateHtmlRenderer.PageBundle record = 页面+元素+交互，渲染器输入单元 |
| **DesignColorSampler** | 从设计稿原图像素采样：背景色、文字色、强调色 |
| **Stitch 式** | 整页 HTML 直出（非组件化），每页一个独立 HTML 文档 |
| **containDepth** | 包含深度——一个元素被多少更大的块包含，用于 z-order |
| **blocks/texts 层** | 背景容器类 vs 文字控件类，分层渲染 |
| **collapseDecorPiles** | 场景简化：贪心非重叠坍缩纯装饰块 |
| **normalizeCardRows** | 卡片网格对齐 |
| **overlapRatio** | 重叠面积占较小方面积比例，碰撞检测用 |
| **WF_RUNTIME** | 注入到 HTML 中的 JS runtime，处理导航/弹窗/返回/Tab切换 |
| **regenerate-html** | 不重新跑 AI，只用已有 Element/Interaction 重新渲染 HTML |

---

## 九、⚠️ 验证系统详解（重要！）

### 9.1 verify 到底检查了什么

`POST /api/projects/{id}/verify` → `ProjectService.verifyProject()` → `HtmlRenderer.verifyInteractions()`

**只检查 3 类交互行为：**

| 检查项 | 方法 | 通过条件 |
|---|---|---|
| **navigate** | 点击 `[data-nav]` 元素 | `window.__wf` 收到 `wf-nav` 消息且 page 字段匹配 |
| **modal** | 点击 `[data-modal]` 元素 | 对应 `.wf-modal` 出现 `wf-show` 类；内部 back 按钮关闭它 |
| **back** | 点击 `[data-action="back"]` | `window.__wf` 收到 `wf-back` 消息 |

**完全不检查的：**
- 元素是否视觉正确（颜色、位置、大小）
- 文字是否可读（字号、溢出、截断）
- 布局是否匹配设计稿
- Tab 栏是否正确显示
- 元素是否重叠/遮挡
- 弹窗内容是否正确渲染
- CSS 类是否正确应用

### 9.2 三个 False Positive 风险

**风险 1：浏览器未初始化 → 0 failures with 0 checks**
```java
// HtmlRenderer.java line 116-118
if (browser == null) {
    return out;  // 返回空列表 = 0 failures，0 checks
}
```
如果 Playwright Chromium 没安装，验证直接返回"全部通过"，但实际没做任何检查。

**风险 2：HTML < 100 字符的页面被跳过**
```java
// ProjectService.java line 64
if (p.getHtmlContent() == null || p.getHtmlContent().length() < 100) continue;
```
坏渲染产生空 HTML 反而不会报错。

**风险 3：前端 guard 样式不在验证中**
前端 `PageCanvas.vue` 注入了额外 CSS（overflow 保护、SVG 缩放、flex 居中），后端验证没有这些。可能导致后端验证通过但前端显示异常。

### 9.3 结论

`verify=0 failures` **只意味着**：所有可点击的交互元素在 headless 浏览器里能发出正确的 postMessage。**不代表页面渲染正确**。

要真正检测视觉问题，需要增加截图对比或 DOM 结构检查。这是当前最大的系统性缺陷。

---

## 十、已知问题与限制

### 10.1 渲染质量问题

1. **装饰块堆叠残留**: 某些页面（如 393）的房间/场景装饰块经过 `collapseDecorPiles` 仍可能有少量重叠（互相 disjoint 所以都被保留）。**根因是 AI bbox 提取问题**。
2. **文字大小不稳定**: AI bbox 宽度不准 → `fs()` 算出过大/过小字号。可调 `DEFAULT_FS` / `MIN_FS`。
3. **Tab 栏标签错误**: 某些情况显示"自定义"而非真实名称，需重跑 `buildAppMap()`。
4. **长文字截断**: `shortLabelCap` 限制短标签最大 16px。
5. **verify 不检测视觉**: 见第九节。

### 10.2 AI 识别问题

1. **bbox 不稳定**: 每次识别可能给不同坐标，靠 `regenerate-html`（不重新识别）缓解。
2. **重复文字**: AI 同一区域提取两次 → `isDuplicateText()` 抑制。
3. **图标文字**: 纯图形被标为有文字 → icon-adjacent 抑制。
4. **token 上限**: 32768 可能导致复杂页面识别不完整。

### 10.3 已修复但需注意

1. **app_map SQL 编码**: PowerShell 执行中文 SQL 静默失败 → 用 `UNHEX()` 或后端 API。
2. **background_image 绝对路径**: 存的是 Windows 绝对路径。
3. **schema.sql 幂等**: `IF NOT EXISTS`，每次启动执行。
4. **effect 元素**: 只渲染在 blocks 层（半透明）。

### 10.4 设计文档缺口

原始需求文档（`doc/需求梳理.md`）描述的是一个**交互式线框编辑器**（拖拽/组件库/undo/redo），但实际实现的是**只读原型查看器**。

| 文档描述 | 实际实现 |
|---|---|
| 拖拽编辑器 | ❌ 未实现 |
| 手动交互配置 | ❌ AI 自动推断 |
| 分享链接 | ❌ 未实现 |
| iframe 嵌入 | ❌ 未实现 |
| 标注系统 | ⚠️ 部分实现 |
| 共享 Tab 栏 | ✅ 超出计划 |
| Playwright 验证 | ✅ 超出计划 |
| 像素采色 | ✅ 超出计划 |

**核心矛盾**：文档说"线框图"（低保真），但实际生成的是全彩还原 HTML（高保真）。术语使用不一致。

---

## 十一、核心文件阅读指南

### 按重要性排序

| 优先级 | 文件 | 作用 |
|---|---|---|
| ★★★ | `TemplateHtmlRenderer.java` | 核心渲染引擎，最复杂，改动最多 |
| ★★★ | `DesignColorSampler.java` | 像素采色 |
| ★★★ | `ProjectService.java` | 全流程编排 |
| ★★☆ | `AnalyzeService.java` | 分析流程编排 |
| ★★☆ | `AppMapService.java` | Tab 栏检测 |
| ★★☆ | `WireframePrompt.java` | AI 识别指令 |
| ★★☆ | `HtmlRenderer.java` | Playwright 验证 |
| ★★☆ | `AiClient.java` | HTTP 调 AI |
| ★☆☆ | `ProjectController.java` | REST 接口 |
| ★☆☆ | `PageCanvas.vue` | 前端画布+runtime |
| ★☆☆ | `PrototypeView.vue` | 前端预览 |

### TemplateHtmlRenderer 关键方法

```
render()                    → 入口：bundle → tab 栏 → 弹窗 → doc()
renderBody()                → 排序 → 坍缩 → 对齐 → 分层 → 拼接
renderElement()             → 按类型分派
fs()                        → 字号计算
fitTextGeometry()           → 文字框扩展
shrinkAwayFromPlaced()      → 碰撞避让
isDuplicateText()           → 重复检测
normalizeCardRows()         → 卡片对齐
collapseDecorPiles()        → 场景简化
detectGapArt()              → 间隙色块
containDepth()              → z-order
renderTabBar()              → Tab 栏
pageBackground()            → 背景色采样
doc()                       → HTML 骨架
```

---

## 十二、前端说明

### 路由

| 路径 | 组件 | 功能 |
|---|---|---|
| `/` | ProjectList.vue | 项目列表 |
| `/projects/:id` | ProjectDetail.vue | 项目详情 |
| `/projects/:id/prototype` | PrototypeView.vue | 无限画布+原型预览 |

### Runtime 注入

`PageCanvas.vue` 在 iframe 内注入 JS（与 `HtmlRenderer.WF_RUNTIME` 保持同步）：

- **导航**: `[data-nav]` 点击 → `postMessage({type:'wf-nav', page:目标名})`
- **弹窗**: `[data-modal]` 点击 → 显示 `.wf-modal-{id}`
- **返回**: `[data-action="back"]` → `postMessage({type:'wf-back'})`
- **Tab 切换**: 底部 Tab 栏 active 状态切换

`PrototypeView.vue` 维护 `backStack`，收到 `wf-back` 时 `iframe.contentWindow.history.back()`。

### 前端额外注入（不在后端验证中）

- `guard` 样式：overflow 保护、SVG 缩放、flex 居中
- `sizer`：iframe 高度适配
- `editor`：编辑模式（当前禁用）

---

## 十三、部署包重建

`wireforge-dist/` 已过时，需要重建：

```bash
# 后端
cd wireforge-backend && mvn -q -DskipTests package
cp target/wireforge-backend-0.1.0.jar ../wireforge-dist/backend/

# 前端
cd ../wireforge-frontend && npm run build
cp -r dist/* ../wireforge-dist/frontend/dist/

# 数据库
mysqldump -u root -p wireforge_dev > ../wireforge-dist/db/wireforge_dev.sql

# 设计稿
cp -r designs/* ../wireforge-dist/designs/
```

---

## 十四、开发注意事项

### 编码

- MySQL CLI: `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`
- PowerShell 默认 GBK，中文 SQL 必须用 `UNHEX()`
- Java 读文件: `[IO.File]::ReadAllText($path, (New-Object Text.UTF8Encoding $false))`

### 环境

- JAVA_HOME: `D:\JDK\jdk-17.0.12`
- Playwright Chromium: `C:\Users\admin\AppData\Local\ms-playwright\chromium-1148\chrome-win\chrome.exe`
- `ChildProcess.kill` 错误是 sandbox artifact，后端通常正常启动

### 测试命令

```bash
# 验证所有页面
curl -s -X POST http://localhost:8091/api/projects/36/verify

# 重渲染单页
curl -s -X POST http://localhost:8091/api/projects/36/pages/393/regenerate-html

# 重建 Tab 栏
curl -s -X POST http://localhost:8091/api/projects/36/appmap

# Playwright 截图
& "C:\Users\admin\AppData\Local\ms-playwright\chromium-1148\chrome-win\chrome.exe" \
  --headless=new --disable-gpu --window-size=375,900 \
  --screenshot=xxx.png "file:///C:/path/to/page.html"
```

---

## 十五、下一步工作

### 高优先级

1. **重建 wireforge-dist** — 当前包缺少所有新引擎代码
2. **增加视觉验证** — verify 只检查交互，不检查画面。需要：截图对比原图、DOM 结构检查（溢出/重叠/字号）
3. **修复 verify false positive** — browser==null 时报错而非静默通过；HTML<100 字符时告警

### 中优先级

4. **393 页面 bbox 优化** — 左侧竖条区域重叠
5. **文字系统参数化** — DEFAULTFS/MINFSC可配置
6. **AI prompt 迭代** — 提高 bbox 准确度
7. **更新设计文档** — 使其匹配实际实现的产品

### 低优先级

8. 组件化（从 Stitch 式转可复用组件）
9. 多平台适配
10. 素材库扩展
11. 用户编辑能力

---

## 十六、核心设计原则

1. **AI 只做语义提取，不做渲染** — 渲染完全确定性
2. **颜色来自设计稿** — 像素采样，不依赖 AI
3. **bbox 驱动一切** — 位置/大小完全由 AI bbox 决定
4. **collision-aware** — 文字系统有完整碰撞检测
5. **layered rendering** — blocks → texts 分层
6. **deterministic = 可重现** — 相同输入相同输出
