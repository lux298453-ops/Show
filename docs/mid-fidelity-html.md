# 中保真静态 HTML 生成

首次分析和「重新生成 HTML」默认走：设计稿 → 语义识别 → 模型生成静态 HTML/CSS → 编辑及素材契约校验 → 本地渲染检查 → 有明显问题才截图回修 → 保存。模型输出无效或调用失败时明确返回生成失败；重新生成不会把旧模板当作新结果覆盖当前原型。重新识别页面可能已经保存元素分析数据，但原型生成失败时前端必须显示失败信息，不能提示整页更新成功。只有主动关闭直出或mock模式才使用模板。

主体使用 Flex/Grid，识别坐标是内容定位参考。模型不输出 JavaScript；所有商品、卡片、文字直接存在于 HTML。应用补入 `prototype/runtime.js`，只处理状态，避免刷新时 `render()/innerHTML` 覆盖编辑结果。

## 配置

- 本机中转站配置放在 Git 忽略的 `wireforge-backend/config/application.yml` 的 `wireforge.ai` 下。`base-url` 填服务根地址（不附带 `/v1`）；`protocol: responses` 优先使用 Responses，默认 `chat-completions`。只有接口明确不支持时才切换协议。
- `api-key` 使用当前服务商的 Key；`model` 和 `html-model` 必须是该服务商支持的模型名。更换服务商后需重启后端，启动日志会显示服务域名、优先协议及两个模型名，不打印 Key。
- `WIREFORGE_AI_DIRECT_HTML=true`：默认启用；设为 `false` 可回退旧模板生成。
- `AI_HTML_MODEL`：可选的 HTML 专用模型，留空使用 `AI_MODEL`。
- `AI_HTML_IMAGE_MAX_EDGE=2400`：HTML生成和回修保留更高分辨率；语义识别仍使用1080px。`AI_HTML_REASONING_EFFORT=high` 只提高HTML调用的推理档位；不支持该参数的中转站沿用现有兼容逻辑。
- `AI_HTML_TIMEOUT_SECONDS=600`：HTML生成及回修单次调用时限，普通识别继续使用 `AI_TIMEOUT_SECONDS`。前端重新识别/生成请求最多等待30分钟，覆盖HTML、角色图片和回修的组合耗时；超时不自动重发。
- `AI_ARTWORK_ENABLED=true`：启用缺素材时的图片生成；默认关闭。`AI_ARTWORK_MODEL=gpt-image-2`，`AI_ARTWORK_MAX_PER_PAGE=12`，默认每页最多12个新角色、单张最多180秒，覆盖九宫格的不同角色形态。新增图片调用会增加生成耗时与费用。可在本地 `wireforge.ai.artwork` 下单独指定 `base-url` 和 `api-key`，留空未配置时沿用当前AI服务。
- `WIREFORGE_AI_REPAIR=true`、`WIREFORGE_AI_REPAIR_ROUNDS=1`：启用按需回修。本地 Chromium 检查主要内容超出画布、明显文字裁切、操作控件大面积重叠和可见裂图；只有命中问题才截图并调用回修模型，问题列表随提示词传入。正常页面不调用回修模型，不增加检查模型调用。明确的滚动区、隐藏弹窗、正常省略号和微小装饰溢出不触发。最多2轮，每轮重新检查，无问题立即停止；0轮/关闭/无可用Chromium均跳过。局部检查不能判断角色是否美观、配色是否完全还原原稿等主观视觉质量。
- `wireforge.ai.asset-base-url`：截图解析相对素材 URL 的服务地址，默认本机后端端口。

直出比旧模板多一次模型调用，截图回修每轮再多一次。生成调用在数据库事务之外执行。失败原因以简短信息返回界面并记录日志。截图回修失败保留已经通过校验的首次结果；回修不得删除已经生成的角色图片，服务会恢复图片显示与备用插画隐藏样式。

Responses/Chat Completions 的返回统一按 SSE 事件帧或完整 JSON 解码，兼容心跳、BOM、多行 `data:` 和中转站仅返回最终快照的情况。增量文本与 `output_text.done`、`output_item.done`、`response.completed` 快照合并去重；收到完成事件即可结束读取，无需等待中转站关闭连接。未收到完成事件、`response.incomplete`、输出长度上限、流式错误和 HTML 编辑契约不通过分别报错，不把原始事件当作 HTML 保存，也不因这些错误自动重发。解析错误不记录原始响应，避免 `response.created` 中回显的提示词或图片进入日志。

重试仅限明确的建连失败（DNS、连接拒绝、连接阶段超时或TLS握手失败），最多一次。HTTP 429/502/503、等待响应头时断开、正文断流和超时均不自动重发，因为这些现象不能证明上游尚未处理请求或没有计费。只有HTTP 400/422明确拒绝对应参数时才去掉推理/温度参数；其他错误保留当前模型和推理档位。错误页仅记录HTTP状态及简短错误代码，不输出整段网关HTML或上游错误消息。`[AI响应头]` 和 `[AI响应失败]` 记录状态、内容类型、耗时、接收行/字符/事件计数、已收正文长度和最后事件类型；不记录原始事件、HTML或图片。角色缓存只省去相同角色的图片生成，不缓存页面识别或整页HTML；命中角色缓存仍可能在HTML请求阶段遇到网关故障。

## 编辑与交互

- `data-wf-id`：模型生成的语义标识；后端为独立节点补齐唯一的 `data-wf-uid`。
- `data-wf-element-id`：本页已有的数据库元素 ID；已有交互的源节点必须绑定真实 ID。对应节点 UID 固定为 `wf-p{pageId}-e{elementId}`。
- `data-nav="页面原名"`、`data-action="back|close"`：与当前预览通信协议一致。跨页弹窗由现有数据库交互和绑定补丁提供。
- 本页弹窗：`data-modal="detail"` 对应 `id="wf-modal-detail" class="wf-modal"`，通过 `wf-show` 显隐。
- 本页标签：`data-wf-tab="组名"`；可用 `aria-controls` 指向静态面板。
- 搜索：输入框 `data-wf-search="列表ID"`，商品节点 `data-wf-search-text="商品名"`。
- 反馈与开关：`data-wf-feedback="提示"`、`data-wf-close-modal="true"`、`data-wf-toggle="收藏"`。

在画板编辑模式中，轻量运行时不执行交互。预览中跨页、返回和弹窗由画板接管，本页搜索、标签和反馈由共享运行时处理。独立 HTML 的跨页入口仅给出预览提示；完整项目路由在项目预览中运行。

App Map 重建、重新分组和分析完成后的补链读取数据库最新 HTML，统一公共组件并更新交互绑定，不再用模板重绘已存在页面。补丁保存沿用现有比较并更新机制，避免覆盖同时发生的编辑。

## 项目公共组件

`prototype/controls.js` 和 `controls.css` 是前后端共用的标准控件实现，保存HTML与画布预览使用相同资源。AI仍生成完整页面布局与内容，只声明控件类型、标签和初始状态，不绘制内部轨道/圆点/勾选图案。开关使用空 `button[data-wf-control="switch"][role="switch"][aria-checked]`，默认44×25px；复选框/单选框使用带 `data-wf-control="checkbox|radio"` 的原生input，默认20×20px，同组单选使用相同name。标签放在控件外，用label或aria-label关联，颜色继承项目主题。

前端构建直接读取后端资源目录中的这两个文件，避免维护两套实现。Compose已将前端构建上下文设为仓库根目录；单独构建前端镜像也在根目录执行 `docker build -f wireforge-frontend/Dockerfile .`。

旧 `.wf-sw`、`.switch[data-wf-toggle]` 开关和原生checkbox/radio会在预览中自动接入；普通收藏图标按钮仍使用原来的toggle行为。控件保留原DOM、编辑UID与连线绑定，画板编辑时不切换，预览时只切换一次；首次初始化之后的手工位置/尺寸调整、状态与禁用属性可随HTML保存。无需重新调用AI或覆盖当前页面来修复旧开关。

`ProjectChrome` 将同项目首个已生成页面的公共组件规范保存为 HTML 中的项目快照。之后生成、回修及重生成参考页都复用栏高、底栏配色、字号、间距和 SVG 图标。状态栏背景跟随本页顶部的纯色/渐变，文字随本页背景深浅调整；v1快照固定灰底的问题自动迁移到v2。本页的激活项、元素 ID、编辑 UID 和跳转目标保持本页归属。在有明确页面容器时，底栏固定在该画布底部，保留内容占位，避免长内容把底栏挤出画布。不同文案/顺序的导航按独立组件处理，不会混成一个底栏；原稿没有状态栏或底栏的弹窗、详情不补造。

模型通过 `data-wf-chrome="status|bottom-navigation"` 标记组件；兼容已有生成结果的语义导航标识和常见状态栏类名。`POST /api/projects/{id}/normalize-chrome` 可统一已有 AI 页面，不调用模型。保存使用 HTML 比较并更新；遇到同时编辑则报冲突。正文卡片及页面布局不重建。

v3公共组件快照将选中项的前景色、背景（含渐变）和圆角一起保存，避免规范化时移除高亮底色后出现白字/白图标落在白底上的情况。v1/v2快照在下次规范化时从原有选中规则恢复底色；无法恢复且前景与底栏几乎同色时使用可见的选中色。画板及全屏演示通过共用的 `chrome-selection.css/js` 在内存中兼容旧页面，不需要重新调用模型或批量改写已有HTML。该兼容仅处理已标记的共享底栏，不改变正文、本页筛选、节点标识和跳转绑定。

## 素材与风格

沿用已有素材库的全局与设计稿分组清单。生成器只允许清单中实际存在的素材 URL，未知图片替换为带文字的素材槽；功能图标使用统一的内联 SVG。相关页及同项目已生成页面可提供风格参考。

角色图片按“原角色素材 → 同项目已缓存的图片 → 新生成图片 → 失败备用插画”的顺序处理。模型只在纯插画容器上输出 `data-wf-artwork-key/name/prompt`；角色名和形态作为稳定key，价格、文字与按钮保留为独立DOM。已有真实图片时不生图。备用插画原节点及ID保留，生图成功后隐藏备用并插入图片，供编辑器继续选择。

`ProjectArtworkService` 调用 [Images API](https://developers.openai.com/api/reference/resources/images/methods/generate) 的 `/v1/images/generations`，请求单张1024×1536 PNG；使用返回的base64保存真实图片，不依赖临时外链。超时或接口失败不自动重发。角色图缓存位于 `designs/.wireforge-artwork/{projectId}/`，同项目跨页和进程重启复用，项目间隔离。该目录需要随部署持久化备份。截图回修仅复用缓存，不发起新的图片生成。

手动扫描与启动自动扫描共用设计稿收集规则：只遍历配置的设计稿根目录内最多3层，在任何层级跳过 `assets` 素材目录和以点号开头的内部目录（包括 `.wireforge-artwork`），不进入它们的子目录。缓存PNG只通过角色素材接口复用，不建成设计稿页面，也不会在扫描时删除；普通分组设计稿仍按原有路径顺序导入。已经误导入的画板需要单独移入回收站，扫描排除规则不直接删除数据库中的页面。

图片描述由HTML模型根据设计稿写出，当前使用文字生图，不等同于原角色身份的精确复刻。要严格保留原角色，请上传对应独立原画并用清晰角色名命名。不会把整张设计稿铺成背景或把带文字、按钮的截图当可编辑卡片。AI调整抽屉仍不在本次范围。

## 验证

后端：`mvn test`。前端：`npm run build`，以及 `node --test tests/prototype-runtime.test.mjs tests/navigation-editing.test.mjs tests/preview-navigation.test.mjs`。

`DirectHtmlLiveSmokeTest` 默认跳过。显式开启 `-Dwireforge.live-html=true`，并指定 `-Dwireforge.live-image=设计稿绝对路径` 和可选的 `-Dwireforge.live-output=输出目录`，可用现有模型配置在本地 dev 数据库中新建独立试验项目，验证真实生成、保存和 App Map 重建。它会调用模型并保留试验项目，切勿对生产数据库运行。
