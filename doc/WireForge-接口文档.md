# WireForge 后端接口文档

> **文档版本**：v1.0
> **最后更新**：2026-09-15
> **对应代码**：`wireforge-backend`（Spring Boot 3.2.5 / JDK 17 / MyBatis-Plus 3.5.5 / MySQL 8）
> **接口基地址**：开发环境 `http://localhost:8090`；`dev` profile 副本为 `http://localhost:8091`
> **前端代理**：Vite 将 `/api`、`/files` 代理到 `VITE_API_TARGET`（默认 `http://localhost:8091`）

---

## 一、通用约定

### 1.1 请求约定

| 项目 | 约定 |
| :--- | :--- |
| 协议 | HTTP/1.1，`Content-Type: application/json; charset=UTF-8` |
| 路径前缀 | 业务接口统一 `/api/**`；设计稿原图静态资源为 `/files/**` |
| 鉴权 | **无**。当前版本未接入登录/Token 体系，所有接口匿名可访问 |
| 跨域 | 后端 `WebConfig` 全量放行（`allowedOriginPatterns("*")`，允许携带凭证，max-age 3600s） |
| 前端超时 | axios 统一超时 **600 秒**（AI 识别、整页渲染属于长耗时任务） |

### 1.2 统一响应体

除素材二进制流接口（§6.1）外，所有接口返回统一信封 `Result<T>`：

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `code` | int | `0` = 成功；`1` = 业务失败 |
| `message` | string | 成功固定为 `"ok"`，失败为可读的中文错误原因 |
| `data` | T | 业务数据，失败时为 `null` |

前端 `src/api/http.ts` 的响应拦截器会自动**拆信封**：`code === 0` 时直接把 `data` 返回给调用方；`code !== 0` 时抛出 `Error(message)`。因此前端代码里拿到的是 `data` 本身。

### 1.3 异常与 HTTP 状态码

由 `GlobalExceptionHandler` 统一兜底：

| 触发条件 | HTTP 状态码 | 响应体 |
| :--- | :--- | :--- |
| `IllegalStateException`（业务校验失败，如"页面不属于该项目"） | `400` | `{code:1, message:"<原始异常信息>", data:null}` |
| `ResponseStatusException`（如素材不存在） | 保留原状态码（多为 `404`） | `{code:1, message:"素材不存在: xxx", data:null}` |
| 其他未捕获异常 | `500` | `{code:1, message:"系统异常: xxx", data:null}` |

前端对空 message 的兜底文案：`400` → "请求参数有误或任务正在进行中，请稍候再试"；`404` → "请求的资源或页面不存在"；`500` → "服务端处理异常，请稍后重试"；超时 → "⏱️ 请求响应超时…"；`Network Error` → "🔌 无法连接到后端服务…"。

### 1.4 坐标系约定

- 画布宽度**固定 375 px**，高度按设计稿图片宽高比换算（默认 812）。
- 元素坐标 `position_x / position_y / width / height` 均为**画布坐标系**（非原图像素）。AI 返回的原图像素 bbox 在后端按 `scaleX = 375 / 原图宽` 等比缩放后落库。
- 页面区块在无限画布上的位置存于 `page.canvas_x / canvas_y`，`null` 表示交由前端自动布局。

---

## 二、数据模型

### 2.1 Project（项目）

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 主键，自增 |
| `name` | String | 项目名称，空值时后端兜底为"未命名项目" |
| `description` | String | 项目描述 |
| `coverImage` | String | 封面图 |
| `appMap` | String(JSON) | App Map：全局共享组件定义（底部 Tab 栏），由 `AppMapService` 确定性构建，非 AI 产出 |
| `createdAt` | DateTime | 创建时间 |

### 2.2 Page（页面 / 画框）

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 主键 |
| `projectId` | Long | 所属项目 |
| `name` | String | 页面名，由 `PageNameResolver` 确定性解析（优先用文件名语义 → 图内文字指纹 → AI 建议名兜底） |
| `backgroundImage` | String | 设计稿**绝对路径** |
| `canvasWidth` / `canvasHeight` | Integer | 画布尺寸，宽固定 375 |
| `canvasX` / `canvasY` | Double | 区块在无限画布上的位置，`null` = 自动布局 |
| `sortOrder` | Integer | 排序 |
| `analyzed` | Integer | `0` 未识别 / `1` 已识别 |
| `htmlContent` | String(LONGTEXT) | 整页直出的完整 HTML/CSS，`null` = 未生成（前端退回组件式渲染） |
| `createdAt` | DateTime | 创建时间 |

### 2.3 Element（界面元素）

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 主键 |
| `pageId` | Long | 所属页面 |
| `type` | String | 元素类型，取值见 §4.2 |
| `label` | String | 设计稿上**真实可见**的文案（无文字则为空字符串） |
| `assetId` | String | 素材库 ID（`avatar-03` / `product-01` / `icon-home` / `bg-01` / `effect-glow`） |
| `positionX` / `positionY` | Double | 画布坐标 |
| `width` / `height` | Double | 宽高 |
| `style` | String(JSON) | 扩展样式（`icon`、`fill`、`ink`、`radius` 等），由渲染器消费 |
| `createdBy` | String | `ai` / 人工 |
| `createdAt` | DateTime | 创建时间 |

### 2.4 Interaction（交互关系）

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 主键 |
| `elementId` | Long | 触发元素 |
| `triggerType` | String | 触发方式，当前固定 `click` |
| `actionType` | String | 动作类型，取值见 §4.3 |
| `targetPageId` | Long | 目标页面 ID（`back` 动作为 `null`） |
| `params` | String(JSON) | 扩展参数；补链未命中时暂存 `{"target_name":"xxx"}` |

### 2.5 Annotation（业务说明 / 标注）

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 主键 |
| `pageId` | Long | 所属页面 |
| `elementId` | Long | 关联元素（`null` 表示纯页面级说明） |
| `text` | String | 说明正文 |
| `positionX` / `positionY` | Double | 标注位置 |
| `boxX` / `boxY` | Double | 标注框位置，`null` = 自动布局 |
| `anchorX` / `anchorY` | Double | 引线在元素端的锚点（线框逻辑坐标），`null` = 自动跟随元素中心 |
| `elbowX` | Double | 引线竖折线 X，`null` = 自动 |
| `sortOrder` | Integer | 排序权重，越小越靠前 |
| `createdAt` | DateTime | 创建时间 |

> **数据关系**：`project 1—n page 1—n element 1—n interaction`，`page 1—n annotation`。数据库未设置外键级联，删除项目时由 `ProjectService.deleteProject` 按依赖顺序手工清理。

---

## 三、接口总览

| # | 方法 | 路径 | 名称 | 耗时量级 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | GET | `/` | 健康检查 | 毫秒 |
| 2 | GET | `/api/projects` | 项目列表 | 毫秒 |
| 3 | POST | `/api/projects` | 新建项目 | 毫秒 |
| 4 | GET | `/api/projects/{id}` | 项目详情 | 毫秒 |
| 5 | DELETE | `/api/projects/{id}` | 删除项目（级联） | 毫秒 |
| 6 | POST | `/api/projects/{id}/scan` | 扫描设计稿目录 | 秒级 |
| 7 | POST | `/api/projects/{id}/analyze` | AI 识别（仅未识别的页） | **分钟级** |
| 8 | POST | `/api/projects/{id}/pages/{pageId}/reanalyze` | 强制重识别单页 | **分钟级** |
| 9 | POST | `/api/projects/{id}/reanalyze-all` | 强制重识别全部页 | **分钟级×N** |
| 10 | POST | `/api/projects/{id}/extract-interactions` | 轻量补提取交互 | **分钟级×N** |
| 11 | POST | `/api/projects/{id}/autowire-interactions` | 交互拓扑自动布线 | 秒级 |
| 12 | POST | `/api/projects/{id}/appmap` | 重建 App Map 并重渲染 | 秒级 |
| 13 | POST | `/api/projects/{id}/verify` | 交互自动化验证 | 十秒级 |
| 14 | POST | `/api/projects/{id}/pages/{pageId}/regenerate-html` | 重生成单页 HTML | 秒级（零 AI） |
| 15 | GET | `/api/projects/{id}/prototype` | 获取完整原型数据 | 百毫秒〜秒 |
| 16 | PUT | `/api/projects/{id}/annotations/{annId}` | 更新标注 | 毫秒 |
| 17 | PUT | `/api/projects/{id}/pages/{pageId}/annotation-orders` | 批量更新说明排序 | 毫秒 |
| 18 | PUT | `/api/projects/{id}/pages/{pageId}/position` | 更新区块画布位置 | 毫秒 |
| 19 | PUT | `/api/projects/{id}/pages/{pageId}/html` | 保存微调后的整页 HTML | 毫秒 |
| 20 | POST | `/api/projects/{id}/pages/{pageId}/edit-lock` | 编辑锁心跳上报/释放 | 毫秒 |
| 21 | GET | `/api/projects/{id}/pages/{pageId}/edit-status` | 查询单页编辑冲突 | 毫秒 |
| 22 | GET | `/api/projects/{id}/edit-statuses` | 批量查询冲突状态 | 毫秒 |
| 23 | GET | `/api/assets/{assetId}` | 素材二进制流 | 毫秒 |
| 24 | GET | `/api/assets/list` | 素材列表 | 毫秒 |
| 25 | GET | `/api/assets/categories` | 素材分类 | 毫秒 |
| 26 | GET | `/api/assets/random/{category}` | 随机取某类素材 | 毫秒 |
| 27 | GET | `/files/**` | 设计稿原图静态资源 | 毫秒 |

---

## 四、枚举定义

### 4.1 元素类型（`element.type`）

AI 只能输出以下 22 种类型（`WireframePrompt.SYSTEM_PROMPT`），后端 `normalizeType` 会把别名（如 `toggle`/`card`/`tabbar`）归一化到标准值，避免退化成方框：

| 类型 | 含义 | 类型 | 含义 |
| :--- | :--- | :--- | :--- |
| `button` | 按钮 | `checkbox` | 勾选框 |
| `input` | 输入框 | `radio` | 单选框 |
| `search` | 搜索框 | `switch` | 开关 |
| `text` | 文本 | `slider` | 滑杆 |
| `image` | 图片 / 插画 / 立绘 | `progress` | 进度条 / 血条 / 经验条 |
| `icon` | 独立图标 / 圆形图标按钮 | `rating` | 星级评分 |
| `avatar` | 用户头像 | `badge` | 徽标 / 角标数字 |
| `container` | 容器 / 卡片 | `divider` | 分隔线 |
| `list` | 列表 | `background` | 背景图 / 全屏底色块 |
| `navbar` | 顶部导航栏 | `effect` | 特效 / 光晕 |
| `tabs` | 页内选项卡 | `other` | 其他 |

### 4.2 交互动作（`interaction.action_type`）

| 值 | 语义 | 来源 |
| :--- | :--- | :--- |
| `navigate` | 整页跳转 | AI 识别 + 自动布线 |
| `popup` | 打开弹窗浮层 | AI 识别（弹窗页） |
| `modal` | 打开弹窗浮层（与 `popup` 等价，渲染器两者都接受） | 自动布线（自动将误判为 `navigate` 的弹窗连线**升级**为 `modal`） |
| `back` | 返回 / 关闭 | AI 识别 + 自动布线（左上角返回按钮） |
| `tab_switch` | 页内选项卡切换 | AI 识别 |

渲染落地为 HTML 属性：`data-nav="目标页名"` / `data-modal="目标页ID"` / `data-action="back|close|tab"`，由前端 `PageCanvas.vue` 注入的 runtime 与后端 `HtmlRenderer.WF_RUNTIME` 共同消费（**两处脚本必须保持同步**）。

### 4.3 素材分类（`category`）

| 分类目录 | assetId 前缀 | 数量 | 说明 |
| :--- | :--- | :--- | :--- |
| `avatars` | `avatar-01`〜`avatar-05` | 5 | 人物头像 |
| `products` | `product-01`〜`product-04` | 4 | 产品图片 |
| `icons` | `icon-<name>`（如 `icon-home`） | 52 | 功能图标 |
| `backgrounds` | `bg-01`〜`bg-04` | 4 | 背景图 |
| `effects` | `effect-<name>`（如 `effect-glow`） | 2 | 特效占位 |
| | | **67** | 合计 |

素材清单由 `classpath:assets/wireframe/assets.json` 声明；`AssetService` 启动时扫描构建，**用户上传素材**（设计稿目录下 `assets/` 子目录）会被合并进同一套 assetId 空间。

---

## 五、接口明细

### 5.1 健康检查

#### 1. `GET /` 服务健康检查

- **请求参数**：无
- **响应** `Result<Map>`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "status": "UP",
    "service": "WireForge Backend",
    "timestamp": 1757922966000
  }
}
```

---

### 5.2 项目管理

#### 2. `GET /api/projects` 项目列表

- **请求参数**：无
- **响应** `Result<List<Project>>`：按 `createdAt` **倒序**返回全部项目
- **前端用途**：项目库首页；随后并发调用 `prototype` 补齐每个项目的画框数与"已就绪"数

#### 3. `POST /api/projects` 新建项目

- **请求体**：

```json
{ "name": "电商大促移动端原型", "description": "双十一主链路" }
```

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `name` | String | 否 | 为空/空白时后端兜底为"未命名项目" |
| `description` | String | 否 | 项目描述 |

- **响应** `Result<Project>`：返回落库后的完整项目对象

#### 4. `GET /api/projects/{id}` 项目详情

| 参数 | 位置 | 类型 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | path | Long | 项目 ID |

- **响应** `Result<Project>`
- **异常**：项目不存在 → `400` `{code:1,message:"项目不存在: <id>"}`

#### 5. `DELETE /api/projects/{id}` 删除项目（级联）

- **路径参数**：`id`
- **响应** `Result<null>`
- **级联规则**：手工按依赖顺序清理 `interaction → annotation → element → page → project`，避免孤儿数据
- **响应日志**：`项目已删除: id=xx，共清理 N 个页面及其子数据`

---

### 5.3 设计稿扫描与 AI 识别

#### 6. `POST /api/projects/{id}/scan` 扫描设计稿目录

- **路径参数**：`id`
- **行为**：递归扫描 `wireforge.designs-dir` 指定的目录（**最多 3 层**），跳过任意层级的 `assets/` 素材目录，仅收 `.png/.jpg/.jpeg/.webp`，把**尚未被本项目引用**的图片创建为 `Page`
- **页面命名**：取**含分组目录的相对路径去扩展名**，如 `装扮修改/装扮首页`
- **画布尺寸**：宽固定 375，高按图片宽高比换算（读取失败回退 812）
- **响应** `Result<List<Page>>`：**仅返回本次新建的页面**（已存在的不会重复创建，天然幂等）
- **异常**：目录不存在 → `400` `"设计稿目录不存在: <path>"`

#### 7. `POST /api/projects/{id}/analyze` AI 识别（增量）

- **行为**：只挑 `analyzed IS NULL OR analyzed = 0` 的页面执行识别，**已识别的页面不重跑**（节省 AI 调用成本）
- **响应** `Result<List<Map>>`，逐页结果：

```json
[
  { "page_id": 398, "page_name": "扭蛋抽奖页", "status": "ok", "elements": 42 },
  { "page_id": 399, "page_name": "背包弹窗", "status": "error", "error": "设计稿图片不存在: D:/..." }
]
```

- **并发保护**：单项目同时只允许一个分析任务，重复触发会直接抛错
  - `400` `{code:1, message:"该项目正在分析中，请等待当前分析完成"}`
- **全部已识别**时返回 **空数组**（前端据此提示"所有页面都已有线稿，无需生成"）
- **⚠️ 长耗时**：耗时取决于页数 × AI 单次调用（配置超时 150s）。前端超时上限 600s

**执行链路（一次 `analyze` 内部的完整编排，见 `ProjectService.analyzePages`）**：

```
1. 逐页 AI 识别（视觉大模型 → 元素/坐标/交互/说明 JSON）
2. resolvePendingTargets    补链：把分析期间未匹配目标的交互按页面名快照补上 target_page_id
3. alignVariantPages        变体页面对齐：同源状态页的导航/立绘框/底栏坐标强制吸附
4. autowireProjectInteractions  交互自动布线：返回键、Tab 广播、功能入口、弹窗升级
5. buildAppMap              重建共享底栏（依赖已稳定的 target_page_id）
6. 全项目重渲染一遍           保证 data-nav / data-modal / 共享底栏按最终数据生成
7. verifyProject            无头浏览器逐页真实点击验证（不阻断，仅记录失败）
```

#### 8. `POST /api/projects/{id}/pages/{pageId}/reanalyze` 强制重识别单页

- **行为**：清空该页旧元素/交互/标注数据，重跑一次**完整**视觉识别（含整页 HTML 重渲染），随后同样走补链 → 对齐 → 布线 → App Map → 重渲染 → 验证
- **响应**：同 `analyze`，返回**单元素**数组
- **异常**：页面不存在或不属于该项目 → `400` `"页面不存在: id=<pageId>"`
- **前端用途**：左侧文件浏览器的"AI 重新识别"批量按钮（约 10–20 秒/页，消耗 Token）

#### 9. `POST /api/projects/{id}/reanalyze-all` 强制重识别全部页面

- **行为**：对项目下**所有**页面执行与 `reanalyze` 相同的强制识别流程
- **响应** `Result<List<Map>>`：逐页结果数组；无页面时返回**空数组**
- **成本提示**：N 页 ≈ N 次最贵的视觉调用，是全站最重的接口

#### 10. `POST /api/projects/{id}/extract-interactions` 轻量交互提取

- **定位**：**不重跑元素识别、不重跑整页渲染**，只让 AI 补答"哪个元素 → 跳哪"
- **输入**：每页的设计稿截图 + 已识别元素清单（`元素id | 类型 | 文案 | x,y,宽,高`）+ 全项目页面名
- **输出处理**：
  - 元素 ID 不属于本页 → 丢弃
  - 动作经 `normalizeAction` 归一化，未知动作 → 丢弃
  - `navigate`/`popup` 缺 target → 丢弃
  - 同页内 `元素:动作:目标` 去重
  - 目标页面名未命中 → 先存入 `params.target_name`，最后由 `resolvePendingTargets` 兜底补链
- **幂等性**：执行前会**清空项目旧交互**再插入，可重复执行
- **响应** `Result<Integer>`：新插入的交互条数

---

### 5.4 渲染、交互与验证（零 AI 的确定性通道）

#### 11. `POST /api/projects/{id}/autowire-interactions` 交互拓扑自动布线

- **行为**：纯确定性算法（**不调用 AI**），基于工程化图谱拓扑推导多页面语义跳转关系
- **四条布线策略**：

| 策略 | 判定规则 | 生成动作 |
| :--- | :--- | :--- |
| 返回键识别 | 位于 `y≤90 && x≤75`，文案含"返回/< /后退/back"，或左上角 ≤48×48 的 icon/button | `back` |
| Tab 广播矩阵 | `y ≥ 680`（底栏带）且 `label.length ≤ 6` 或 type 为 `icon`/`text`，label 命中 Tab 根页面关键词 | `navigate` → 对应主页面 |
| 出货槽唤起 | label 含"出货"，或 `y∈[550,600] && w≥150` 且页面名含"扭蛋" | `modal` → 抽奖结果弹窗页 |
| 功能入口下钻 | type ∈ {button, icon, container} 或 `label.length ≥ 2`，文案语义模糊匹配目标页 | `navigate`；目标是弹窗页则 `modal` |

- **保护与纠错**：
  - 已有 `back` 连线的元素**不覆盖**（尊重人工配置）
  - 已有明确跳转目标（且目标不是弹窗）的元素**不覆盖**
  - **升级**：若已有连线是 `navigate` 但目标是弹窗页，自动改写为 `modal`
  - **纠错**：清理"开始探险"误连"探险与属性说明弹窗"、"规则"误连"说明页"的历史脏连线
  - "探险/规则"类关键词有**反向排除**规则，防止误连说明书弹窗
- **响应** `Result<Integer>`：新增/更新的连线数量

#### 12. `POST /api/projects/{id}/appmap` 重建 App Map 并重渲染全部页面

- **行为**：
  1. `AppMapService.buildAppMap` 重新投票构建共享底栏
  2. 对项目下**每一页**重跑确定性模板渲染并覆盖 `html_content`
  3. 若 `wireforge.verify=true`，追加一次全项目交互验证
- **全程不调用 AI**（毫秒〜秒级）
- **响应** `Result<Integer>`：成功重渲染的页面数

**App Map 构建算法（`AppMapService`，全是确定性投票，无 AI）**：

```
1. 底栏带 = 画布高度 × 0.80 以下区域；收集带内 icon 元素作为候选
     - 有 navigate 交互 → 候选目标 = 交互目标页
     - 无任何交互     → 候选目标 = 本页（激活态自指项）
     - 项文案 = 图标自身 label，或正下方 30px 内相邻 text 的文案
2. 共识投票：出现在 ≥2 个页面底栏里的目标才算 Tab 项
   （单页独有的底部图标是"页面级工具条"，必须排除）
3. Tab 页 = 底栏内含 ≥2 个共识目标的页
4. Tab 铁证：目标页自己的底栏里也有指向自己的自指项
   （"自定义/收集"这类跨页共享按钮永远没有自指 → 借此区分；证据不足时退回纯共识集）
5. 规范项：文案取多数票、x 取中位；最多保留 5 项（移动端底栏上限），按中位 x 排序
6. bar 盒：y = max(画布高中位数 × 0.80, 各 Tab 页最小项 y − 16)，h = max(48, 画布高 − y)
```

- **识别失败（返回 `null` 的三种情况）**：页面数 < 2；共识目标 < 2；规范 Tab 项 < 2 或 Tab 页为空

#### 13. `POST /api/projects/{id}/verify` 交互自动化验证

- **行为**：用无头 Chromium（Playwright）**逐页真实点击**所有交互元素并断言行为

| 检查项 | 断言内容 | 失败提示 |
| :--- | :--- | :--- |
| `navigate` | 点击 `[data-nav]` 后应向父窗口发出 `{type:'wf-nav', page:目标名}` | "点击后未收到 wf-nav 消息（可能被遮挡或 runtime 缺失）" |
| `modal` | 点击 `[data-modal]` 后对应的 `.wf-modal` 应出现 `wf-show`；再点其内部 `[data-action=back]` 应关闭 | "点击后浮层未显示…" / "浮层能打开但 back 未关闭" |
| `back` | 点击浮层外的 `[data-action=back]` 应发出 `{type:'wf-back'}` | "点击后未收到 wf-back 消息" |
| `runtime` | 验证脚本自身异常 | "验证执行异常: …" |

- **关键前提**：存库的 `html_content` **不含** runtime 脚本（由前端预览时注入），因此验证前后端必须先把 `WF_RUNTIME` 补进 `</body>` 之前
- **响应** `Result<List<CheckResult>>`：**只返回失败项**（空数组 = 全部通过）

```json
[ { "page": "扭蛋抽奖页", "kind": "modal", "target": "512", "ok": false, "note": "点击后浮层未显示（wf-modal 层缺失或样式异常）" } ]
```

- **降级**：Chromium 未安装 / 启动失败时 `HtmlRenderer` 以"不可用"状态运行，返回空列表，**不阻断主流程**
- **⚠️ 覆盖边界**：此接口**只验证交互点击消息是否正确，不检查视觉渲染质量**

#### 14. `POST /api/projects/{id}/pages/{pageId}/regenerate-html` 重新生成单页 HTML

- **行为**：**零 AI**。先跑一次 `alignVariantPages` 对齐，再用确定性模板渲染器把该页元素按坐标直出为整页 HTML 并落库
- **响应** `Result<String>`：新生成的 HTML 全文
- **前端用途**：左侧"原型交互刷新"批量按钮（毫秒级，**不消耗 AI Token**）

---

### 5.5 原型数据

#### 15. `GET /api/projects/{id}/prototype` 获取完整原型数据

一次返回前端渲染所需的**全部数据**（页面 + 元素 + 交互 + 标注），是原型画板的唯一数据源。

- **响应** `Result<Map>`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "project": { "id": 9, "name": "...", "appMap": "{...}" },
    "pages": [
      {
        "id": 398,
        "name": "扭蛋抽奖页",
        "background_image": "/files/扭蛋抽奖页.png",
        "canvas_width": 375,
        "canvas_height": 812,
        "canvas_x": null,
        "canvas_y": null,
        "analyzed": 1,
        "html_content": "<!DOCTYPE html>...",
        "elements": [
          {
            "id": 1201,
            "type": "button",
            "label": "开始抽奖",
            "asset_id": null,
            "icon": "sparkles",
            "style": "{\"fill\":\"#FF6B35\",\"ink\":\"#FFFFFF\"}",
            "x": 120, "y": 640, "width": 135, "height": 44,
            "interaction": {
              "trigger": "click",
              "action": "modal",
              "target_page_id": 512,
              "params": null
            }
          }
        ],
        "annotations": [
          {
            "id": 88, "element_id": 1201,
            "text": "点击后消耗 1 枚扭蛋币并播放开箱动画",
            "x": 300, "y": 200,
            "box_x": null, "box_y": null,
            "anchor_x": null, "anchor_y": null, "elbow_x": null,
            "sort_order": 0
          }
        ]
      }
    ]
  }
}
```

**字段要点**：

| 字段 | 说明 |
| :--- | :--- |
| `background_image` | 已由绝对路径转换为 `/files/<相对路径>` 可访问 URL |
| `icon` | 从 `style` JSON 中抽出的图标名（`ExtractIconName`），无则为 `null` |
| `elements[].interaction` | **只取该元素的第一条交互**；无交互时该字段**不存在** |
| `annotations` | 按 `sort_order` 升序、再按 `id` 升序 |
| `pages` | 按 `sort_order` 升序 |

- **降级**：查询标注时若 `sort_order` 列因历史库缺失报错，会自动重试一次**不含该列**的查询（`buildPageVo` 内 catch 兜底）

---

### 5.6 标注、布局与微调保存

#### 16. `PUT /api/projects/{id}/annotations/{annId}` 更新标注

- **请求体**（所有字段均可选，只传要改的）：

```json
{
  "text": "点击后消耗 1 枚扭蛋币",
  "title": "抽奖按钮",
  "positionX": 300, "positionY": 200,
  "boxX": 280, "boxY": 180,
  "anchorX": 120, "anchorY": 640,
  "elbowX": 260
}
```

| 字段 | 说明 |
| :--- | :--- |
| `text` | 说明正文 |
| `title` | ⚠️ 特殊字段：**不写标注表**，而是更新其关联元素的 `label`（改的是元素标题，不是标注标题） |
| `positionX/Y`、`boxX/Y`、`anchorX/Y`、`elbowX` | 各类坐标，`null` 表示恢复自动布局 |

- **响应** `Result<Map>`：返回更新后的全部字段快照（`id`、`text`、可选 `title`、各坐标）
- **异常**：标注不存在 → `"标注不存在: <annId>"`；标注不属于该项目 → `"标注不属于该项目"`

#### 17. `PUT /api/projects/{id}/pages/{pageId}/annotation-orders` 批量更新说明排序

- **请求体**：**裸 JSON 数组**（非对象），按目标顺序排列的标注 ID

```json
[91, 88, 95, 90]
```

- **行为**：数组下标即新的 `sort_order`；跳过不属于该页的 ID；单条更新失败仅告警不中断
- **响应** `Result<List<Long>>`：回显入参顺序
- **异常**：页面不属于该项目 → `"页面不属于该项目: <pageId>"`

#### 18. `PUT /api/projects/{id}/pages/{pageId}/position` 更新区块画布位置

- **请求体**：`{ "canvasX": 1240, "canvasY": 320 }`
- **响应** `Result<Map>`：`{ "id": 398, "canvasX": 1240, "canvasY": 320 }`
- **前端用途**：无限画布上拖动页面区块，松手后持久化（拖动过程用 `requestAnimationFrame` 本地渲染，不打接口）

#### 19. `PUT /api/projects/{id}/pages/{pageId}/html` 保存微调后的整页 HTML

- **请求体**：`{ "html": "<!DOCTYPE html>...", "clientId": "user_abc123" }`
- **行为**：前端把用户在"元素微调"模式下序列化后的完整 HTML 直接落库到 `page.html_content`
- **冲突防护**：若该页正被**其他** `clientId` 独占编辑且心跳未超时（< 12s），拒绝保存
  - `400` `{code:1, message:"页面当前正被 <用户名> 独占微调中，无法保存覆盖！"}`
- **异常**：HTML 为空 → `"HTML 内容不能为空"`；页面不存在 → `"页面不存在: <pageId>"`
- **前端行为**：500ms 防抖自动保存，成功后提示"✅ 微调已保存"；冲突时本地拦截并提示"🔒 保存被拦截…"

---

### 5.7 协同编辑锁

三个接口共用同一份**进程内内存会话表** `PAGE_EDIT_SESSIONS`（`ConcurrentHashMap<pageId, EditSession>`），属**单实例**方案，多副本部署不共享。

**存活判定**：`now − lastHeartbeat < 12000ms`（12 秒）。超过即视为离线，读取/上报时会被自动清理。

**冲突判定**：会话存活 **且** `clientId` 不同 → `conflict = true`。

#### 20. `POST /api/projects/{id}/pages/{pageId}/edit-lock` 上报/释放编辑锁

- **请求体**：

```json
{ "clientId": "user_abc123", "userName": "协同成员", "active": true }
```

| 字段 | 说明 |
| :--- | :--- |
| `clientId` | 前端 `sessionStorage.wf_client_id`，形如 `user_xxxxxxx` |
| `userName` | 展示用名称，缺省为"其他成员" |
| `active` | `true` = 上报/续期心跳；`false` = 释放锁（仅释放属于自己的锁） |

- **响应** `Result<Map>`：

```json
{ "editing": true, "conflict": true, "editor": "协同成员" }
```

- 无冲突时 `editor` 字段**不存在**；`active=false` 时固定返回 `{editing:false, conflict:false}`
- **前端节奏**：编辑模式下每 **3500ms** 轮询批量状态，并在"微调 + 编辑模式 + 焦点页"时持续上报心跳

#### 21. `GET /api/projects/{id}/pages/{pageId}/edit-status` 查询单页冲突

| 参数 | 位置 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | path | 是 | 项目 ID |
| `pageId` | path | 是 | 页面 ID |
| `clientId` | query | 否 | 默认空字符串；为空时任何存活会话都会判为冲突 |

- **响应** `Result<Map>`：结构同 `edit-lock`

#### 22. `GET /api/projects/{id}/edit-statuses` 批量查询编辑状态

| 参数 | 位置 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | path | 是 | 项目 ID |
| `clientId` | query | 否 | 同上 |

- **行为**：先清理全部超时会话，再返回**当前存活的会话**（**注意：键是全局 pageId，不按 projectId 过滤**）
- **响应** `Result<Map<Long, Map>>`，key 为 pageId：

```json
{
  "code": 0, "message": "ok",
  "data": {
    "398": { "editing": true, "conflict": false, "editor": "协同成员" },
    "512": { "editing": true, "conflict": true,  "editor": "张三" }
  }
}
```

**前端冲突展示（4 处）**：顶部工具条橙色锁条、区块标签上玫红徽标"X 独占微调中"、冲突页整页半透明遮罩 + 玫红虚线框、"当前有人正在编辑"琥珀徽标。自己的页面显示闪烁绿色"微调中"。

---

### 5.8 素材库

#### 23. `GET /api/assets/{assetId}` 素材二进制流

- **路径参数**：`assetId`，如 `icon-home`、`avatar-03`
- **响应**：**裸二进制流**（不走 `Result` 信封）
  - `Content-Type`：按扩展名推断（`image/svg+xml` / `image/png` / `image/jpeg` / `image/gif` / `image/webp`，未知为 `application/octet-stream`）
  - `Cache-Control: no-store`
- **异常**：`404` `{code:1, message:"素材不存在: <assetId>"}`
- **前端用法**：`<img src="/api/assets/icon-home">` 直接引用

#### 24. `GET /api/assets/list` 素材列表

- **响应** `Result<List<Map>>`：每个素材的 `assetId`、`category`、`fileName`、`description`、`url`

#### 25. `GET /api/assets/categories` 素材分类

- **响应** `Result<List<Map>>`：分类名（`avatars` / `products` / `icons` / `backgrounds` / `effects`）与描述

#### 26. `GET /api/assets/random/{category}` 随机取某类素材

- **路径参数**：`category` 可为元素类型（`avatar` / `image` / `icon` / `background` / `effect`）或分类目录名
- **响应** `Result<Map>`：`{ "assetId": "icon-star", "category": "icons", "fileName": "star.svg", "description": "功能图标", "url": "/api/assets/icon-star" }`
- **异常**：`404` `"未找到该分类的素材: <category>"`

#### 27. `GET /files/**` 设计稿原图静态资源

- **映射**：`/files/**` → `wireforge.designs-dir` 的绝对路径（`WebConfig.addResourceHandlers`）
- **说明**：`prototype` 接口返回的 `background_image` 就是这个形态；图片改名/移出目录会导致 404

---

## 六、配置项

`application.yml` 全部支持环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `PORT` / `SERVER_PORT` | `8090` | 服务端口 |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://127.0.0.1:3306/wireforge?...` | 数据库连接串 |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `root` / `abc123` | 数据库账号 |
| `SQL_INIT_MODE` | `always` | `schema.sql` 执行策略；**线上务必设 `never`**，避免重复覆盖 |
| `DESIGNS_DIR` | `designs` | 设计稿目录（支持相对/绝对路径） |
| `WIREFORGE_AUTO_ANALYZE` | `false` | 启动时自动扫描并分析新设计稿（`AutoAnalyzeRunner`） |
| `WIREFORGE_MOCK` | `false` | `true` 时不调用 AI，返回内置示例数据（中转站不可用时联调前端） |
| `WIREFORGE_ASSETS_PATH` | `classpath:assets/wireframe` | 素材库根路径 |
| `AI_BASE_URL` | `https://api.flintic.uk` | OpenAI 兼容中转站地址 |
| `AI_API_KEY` | **（空）** | 密钥；为空时任何 AI 调用直接抛错 |
| `AI_MODEL` | `gpt-5.5` | 默认视觉模型 |
| `AI_HTML_MODEL` | （空） | 整页 HTML 专用模型，空则回退 `AI_MODEL` |
| `AI_MAX_OUTPUT_TOKENS` | `32768` | 单次最大输出 Token |
| `AI_TIMEOUT_SECONDS` | `150` | 线程级执行超时（超时强制中断，防 SSE 挂起） |
| `WIREFORGE_AI_REPAIR` | `true` | 是否开启"渲染→截图→对比原稿→回修"闭环 |
| `WIREFORGE_AI_REPAIR_ROUNDS` | `1` | 回修最大轮数 |
| `wireforge.verify` | `true`（代码默认） | 分析后是否自动跑交互验证 |
| `wireforge.ai.render-scale` | `2` | 回修截图倍率 |

**前端环境变量**：

| 变量 | 说明 |
| :--- | :--- |
| `VITE_API_BASE_URL` | 生产环境后端域名（末尾不带斜杠）；为空时走相对路径 `/api` |
| `VITE_API_TARGET` | 仅开发环境生效，Vite 代理目标，默认 `http://localhost:8091` |

---

## 七、典型调用时序

### 7.1 从零到可交互原型（完整流水线）

```
POST /api/projects                  新建项目                    → projectId
POST /api/projects/{id}/scan        扫描设计稿 → 建 Page          → [Page...]
POST /api/projects/{id}/analyze     AI 识别 + 补链 + 对齐 + 布线 + AppMap
                                    + 全量重渲染 + 交互验证        → [逐页结果]
GET  /api/projects/{id}/prototype   取全量数据进入画布             → {project, pages}
```

### 7.2 设计稿有增改后的增量刷新

```
POST /api/projects/{id}/scan              只导入新增图片
POST /api/projects/{id}/analyze           只识别未识别的页（幂等，不浪费 Token）
POST /api/projects/{id}/autowire-interactions   重跑确定性布线
POST /api/projects/{id}/appmap            重建底栏 + 全量重渲染 + 验证（零 AI）
```

### 7.3 不花 Token 的快速视觉迭代

```
POST /api/projects/{id}/pages/{pageId}/regenerate-html    单页重渲染（零 AI）
PUT  /api/projects/{id}/pages/{pageId}/html               人工微调后回存
POST /api/projects/{id}/verify                             交互回归验证
```

### 7.4 画布协同编辑

```
POST /api/projects/{id}/pages/{pageId}/edit-lock  {active:true}   每 3.5s 心跳
GET  /api/projects/{id}/edit-statuses?clientId=...                每 3.5s 拉状态
PUT  /api/projects/{id}/pages/{pageId}/html      {html, clientId} 冲突时被拒
POST /api/projects/{id}/pages/{pageId}/edit-lock {active:false}   离开/退出微调时释放
```

---

## 八、已知约束与注意事项

1. **无鉴权、无多租户**：所有项目对任何访问者可见可改，仅适合内网/演示环境。
2. **编辑锁是进程内状态**：后端多副本部署时锁不共享；实例重启锁即丢失（前端 3.5s 轮询会自动恢复）。
3. **`extract-interactions` 幂等但具有破坏性**：它会**先删光项目所有交互**再写入新结果，因此不应在用户已手工调整连线后随手执行。
4. **runtime 脚本双份维护**：`PageCanvas.vue` 注入的脚本与 `HtmlRenderer.WF_RUNTIME` 必须保持一致，改动其一务必同步另一份，否则"线上能点、验证报失败"。
5. **`edit-statuses` 不按项目过滤**：返回的是全局存活会话，前端只用 `pageId` 命中自己需要的键即可。
6. **AI 调用超时分层**：`AI_TIMEOUT_SECONDS`（线程级强制中断，150s）< 前端 axios 超时（600s）。大项目建议拆分多次 `analyze`。
7. **大图自动预处理**：超过 400KB 的图片在发往 AI 前会等比缩放到最长边 1280px 并转 JPEG（体积降 10 倍以上），避免 base64 过大导致中转站超时。
8. **`page.background_image` 存绝对路径**：换机器/换目录后需重新 `scan`，否则 `renderTemplateHtml` 读不到原图（会降级保留组件模式而不是报错）。
