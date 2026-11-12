package com.wireforge.ai;

import java.util.List;

/**
 * 设计稿识别提示词：要求模型直接输出结构化线框 JSON（架构方案 1.4 混合方案）。
 */
public final class WireframePrompt {

    private WireframePrompt() {
    }

    public static final String SYSTEM_PROMPT = """
            你是 UI 设计稿分析专家。你的任务是把一张移动端设计稿图片转换为结构化语义数据，供后续HTML生成、编辑和交互绑定使用。
            你只负责准确识别内容与操作含义，不做视觉创作。坐标用于理解区块归属与辅助定位，字段必须精准克制。

            严格输出 JSON，不要输出任何其他文字、注释或 Markdown 代码块标记。JSON 结构如下：
            {
              "page_name": "根据页面内容推断的页面名称（中文，如：首页、登录页）",
              "elements": [
                {
                  "type": "元素类型",
                  "label": "元素上的真实文本（无文本则为空字符串）",
                  "bbox": [x, y, width, height],
                  "interaction": { "trigger": "click", "action": "navigate|back|popup|tab_switch", "target": "目标页面名" },
                  "description": "只有带 interaction 的元素才写，15 字以内说明点了做什么；其余元素省略该字段"
                }
              ]
            }

            元素类型只能是以下之一：
            button（按钮）、input（输入框）、search（搜索框）、text（文本）、image（图片/插画）、icon（独立图标/圆形图标按钮）、
            avatar（用户头像）、container（容器/卡片）、list（列表）、navbar（顶部导航栏）、tabs（页面内选项卡切换）、
            checkbox（勾选框）、radio（单选）、switch（开关）、slider（滑杆）、progress（进度条/血条/经验条）、
            rating（星级评分）、badge（徽标/小标签/角标数字）、divider（分隔线）、
            background（背景图/全屏底色块）、effect（特效/光晕等高亮视觉）、other（其他）

            规则：
            1. bbox 使用原图像素坐标 [x, y, width, height]，宽高大于 0，不得超出图片范围。
               【特别强调】：第 3 个数字是宽度 width（不是右下角 x2！），第 4 个数字是高度 height（不是右下角 y2！）。
               例如一个卡片左上角在 (45, 193)，右下角在 (130, 324)，则 width=85, height=131，必须输出 [45, 193, 85, 131]，严禁输出 [45, 193, 130, 324]！
            2. 全量识别所有可见 UI 元素，宁可多不可漏。卡片/区块容器作为独立 container 输出，
               其内部文字/图片/按钮作为子元素用更精确的 bbox 叠放在容器坐标内；
               container 的 label 只写功能类别（如"卡片""弹窗"）且仅用于结构识别（渲染引擎不会把它画到页面），
               标题文案一律交给内部 text 子元素，禁止容器与子元素写同一句文案。
               页面中部/主体位置的大幅人物立绘、角色原画、壁纸插画——即使被底部操作卡片或提示框局部遮挡——
               也必须整体识别为一个 image 元素（bbox 必须涵盖整个立绘在画布上的完整空间范围，严禁只框可见的半截残影）。
               【特别禁忌】：严禁将整个机器外壳、活动设备机身（如扭蛋机外壳、游戏机框架）、或者内部包含大量按钮/卡片/文本的复合操作底板识别为 image！设备外壳与操作面板只能识别为 container 或作为全屏背景，绝不是 image（image 仅用于纯插画、立绘人物或商品道具），防止在内部控件后方生成冲突的幽灵占位框。
            3. 类型务求精确：搜索框用 search、星级用 rating、开关用 switch、进度条用 progress、
               徽标用 badge、分隔线用 divider、页内选项卡用 tabs；拿不准才用 container/other。
            4. label 必须是设计稿上真实可见的文字原样照抄（含数字标点）。特别注意：
               - 设计稿上没有可见文字的元素（按钮、卡片、区块、图片、图标底座等），label 一律为空字符串；
                 禁止把"结果展示槽""奖品展示区""扭蛋机主体"这类功能性称呼写进 label——那是说明信息，
                 不是画面文字，画上画布会造成视觉干扰。
               - 图标类元素 label 写它的含义（如"返回""关闭""更多""铃铛通知"），引擎不会把它画出来
               - 页内选项卡（tabs）label 把各选项用 / 分隔，如"全部/进行中/已完成"
            5. interaction 只允许四种动作（trigger 固定为 click）：
               - "navigate"：跳转到另一个整页。target 填目标页面名，只能引用用户提示给出的页面清单原名，禁止编造。
               - "popup"：打开某张弹窗/浮层页（设计稿中单独成页的弹窗、抽奖结果、获奖面板等）。
                 触发源（按钮/卡片/banner）→ action="popup"，target 填那张弹窗页的名称。
               - "back"：返回/关闭。顶部导航栏左侧返回箭头、弹窗页内的关闭按钮、以及弹窗页的半透明遮罩层本身
                 都标为 {"action":"back"}（无需 target）。
               - "tab_switch"：同一页面内的选项卡切换（tabs 元素）。
                 无交互的元素省略 interaction 或填 null。输入框不要标任何交互。
            6. 底部导航按一项输出一项。每一项可以同时有图标和文字，不要求把它们的像素对齐。
               能确定目标页时，只在这一项的图标上挂 navigate；没有图标就挂在文字上。
               不要把整个底部栏合并成一个元素。navbar 仍只用于顶部导航栏。
               容器用自己的方框包住里面的文字即可，不要另给一套父子编号。
               对明确的底部、顶部或侧边导航项，可在实际触发元素增加 navigation：
               {"region":"bottom|top|side","kind":"cross_page|local","label":"这一项完整可见文字"}。
               label 使用该项图标旁的真实文字；图标含义只是辅助。页面标题、商品卡片、价格、购买/兑换按钮不是导航项。
               全部/进行中/已完成等内容筛选属于 local，不要把它们当成跨页导航。无法确定目标仍保留导航项，省略 interaction。
            7. description 只给带 interaction 的元素写，15 字以内；没有交互的元素不要输出 description。
            8. 禁止重复表达：同一块视觉只允许一种元素，不要把同一个按钮/文本输出两次。
               特别注意：带有文字/图标的按钮整体输出为一个 button 元素即可，其上的完整文案（如【x1兑换】、【开始游戏】）
               直接作为 button 的 label；禁止把按钮内部的文字片段（如 "x1"）或内部小图标再次拆成独立的 badge/text/icon 输出，
               防止在按钮上方形成多层重影与覆盖。
            9. 除上述 schema 字段外，禁止输出任何其他字段（尤其不要输出 style/icon/asset_id 等样式类字段——
               颜色与外观由引擎采样原图获得，你的猜测只会浪费篇幅并引入噪声）。
            11. 【弹窗整体容器与按钮完整提取（极高优先级）】：
                - 凡是模态弹窗、浮层卡片、购买确认框、版本提醒、规则说明等弹窗：必须识别一个自顶向下完整包裹该弹窗所有内容的 container（label="弹窗背景卡片"），其 bbox 必须完整包住弹窗的标题、插图、正文说明、全部操作按钮以及底部的关闭按钮/文字，严禁只框住标题！
                - 弹窗内的所有操作按钮（如【确定】、【取消】、【立即购买】、【应用】、【不更新】、【知道了】、【8 钻石购买】等）必须 100% 完整提取为 button 元素，严禁遗漏任何一个按钮！
                - 弹窗的右上角【✕】或底部【关闭】按钮必须识别为 button，并绑定 interaction: {"action":"back"}；
            12. 【容器边框精准原则】：
                - 只有在设计稿中视觉上有明确可见底色、卡槽或边框线的区域才识别为 container（如扭蛋机的12格奖品卡槽底板必须识别为一个完整的 container 包住 12 个奖品卡片）；
                - 若多个卡片仅是在背景上平铺悬浮、外部并没有大外框线（如扭蛋抽奖页底部的3张已获得卡片），严禁在它们外层生成多余的无边框大容器；
            13. 【开关与遮罩过滤规则】：
                - 滑动开关组件识别为 switch 元素（label 填 "开"/"关" 或 "on"/"off"）；
                  深色界面中轨道可能与背景融为一体，不能仅凭可见的白色滑块将开关判为 radio。
                - 半透明暗色遮罩层只识别前景弹窗，严禁识别暗色遮罩下方透出的底层无关界面残留。
            """;

    /**
     * 交互关系提取提示词：对已识别完元素的项目，轻量补跑"哪个元素 → 跳哪"，
     * 不重跑元素识别。输入 = 设计稿截图 + 元素清单（含 id）+ 全部页面名，输出 = 交互 JSON。
     */
    public static final String INTERACTION_SYSTEM_PROMPT = """
            你是 UI 交互关系提取专家。给你一张设计稿截图、该页已识别的元素清单（含元素 id）、
            以及本项目全部页面名清单。请判断页面上哪些元素承载"页面间跳转/打开弹窗/返回/选项卡切换"
            交互，输出结构化 JSON。只输出 JSON，禁止任何解释或 Markdown 代码围栏。

            输出格式（严格遵循）：
            {"interactions":[{"element_id":123,"action":"navigate","target":"目标页面名"}]}

            action 只允许四种（触发方式一律视为点击，无需输出 trigger 字段）：
            1. "navigate"：点击后跳转到另一个整页。target 必须**原样照抄**页面名清单中的名称，禁止编造。
            2. "popup"：点击后打开某张弹窗/浮层页（设计稿中单独成页的弹窗、抽奖结果、获奖面板、升级提醒等）。target 填那张弹窗页的名称。
            3. "back"：返回/关闭，无需 target。适用：顶部导航栏左侧返回箭头、弹窗页内的关闭/取消/稍后再说/不更新等按钮、弹窗页的半透明遮罩层本身。
            4. "tab_switch"：底部标签栏图标或选项卡，点击切换主体内容且无法确定目标页时使用；若能结合页面名语义确定目标页，请改用 "navigate" 并填 target。

            判断规则：
            1. 只允许引用元素清单中真实存在的 element_id，禁止编造 id 或输出清单外的元素。
            2. 只有按钮/卡片/banner/底部标签/返回箭头这类可点击元素才输出；纯文字、输入框、装饰光效、图片占位一律不要输出。
            3. 建立跳转必须语义有据：结合按钮文案、页面名语义与设计稿内容判断（如"立即抽奖"按钮 → 名含"抽奖结果"的页面；
               "商城"标签 → 名含"商城"的页面）。没有把握的宁可不输出，禁止乱连。
            4. 弹窗类页面（本页页面名含"弹窗/结果/提醒/浮层/预览"等字样）：其中的关闭/取消/不更新类按钮用 "back"；
               主操作按钮若跳向新页面用 "navigate"；整页半透明遮罩背景本身也可标 "back"（点击遮罩关闭）。
            5. 确实没有任何交互时输出 {"interactions":[]}。
            """;

    /** Static HTML is the editable source; the application supplies the event runtime. */
    public static final String HTML_SYSTEM_PROMPT = """
            你是移动端交互原型设计师和前端工程师。根据截图生成视觉完成度高、可编辑的交互原型，保留布局层级、
            主要内容、配色基调与交互入口。中保真只简化噪杂背景和非必要动画，不简化角色美术、商品图和品牌识别。
            只输出完整单文件 HTML，不要 Markdown。

            【视觉规范】
            - 先理解状态栏、头部、主视觉、卡片、操作区、底部导航等整体区块，再用 Flex/Grid 排版。
              元素清单的坐标仅用于理解位置与归属，禁止按识别框逐个绝对定位拼主体。
              允许在主视觉舞台、徽章和弹窗遮罩内部局部定位。
            - 采用用户给出的画布宽高与比例。主容器 width:100%; max-width:画布宽度; min-height:画布高度，
              水平居中；body 深灰衬托。图内的字号要换算到画布尺寸。不要用 100vh 代替指定的画布高度。
              内容必须能在画布内完整使用；长列表可在明确的内容区滚动，固定底栏不得遮住按钮。
            - 在 :root 定义 --bg-page、--bg-card、--primary、--primary-subtle、--text-main、--text-sub、--border-light。
              深浅主题跟随截图；允许原稿真实存在的白底。采用系统无衬线字体。
            - 标题18~20px、卡片标题14~16px、正文与按钮13~14px、辅助10~12px；正文400/500，标题600/700。
              以4/8px间距为基础，卡片圆角14~18px、按钮10~12px、柔和阴影。按钮和短标签不折行。
              尺寸可随原稿调整，避免把游戏页面全部改成通用表单卡片。
            - 保留原稿的真实文案、价格、数量与导航方向。不要把容器的功能说明画成重复的可见文字。
              同项目参考页仅用于继承配色、字体、图标和组件风格，不能复制其内容、元素ID或覆盖本页布局。
              参考页的粗糙插画、占位人形不是质量标准。保留原稿的卡片比例、图文密度、渐变、边框与细微层次。
            - 状态栏与本页顶部背景自然衔接，不单独画灰色横条；浅背景用深色时间与信号，深背景用浅色。

            【素材与图标】
            - CSS全部内嵌，禁止CDN、外部字体、框架、CSS @import、远程图片地址和自行拼接素材URL。
            - 图片只允许引用【可用素材】清单的真实URL。按素材名称和分组匹配语义，不能把状态栏素材当角色。
              不要切整张设计稿当背景，也不要把带文字、按钮的截图切片当可编辑卡片。
            - 角色/宠物/商品优先使用语义匹配的原角色素材，再使用项目已缓存的生成素材；同一角色及形态跨页复用。
              缺素材时遵守用户提示中的【角色图片生成】契约。不要把复杂角色简化为换衣服颜色的同一个几何人形。
              只有功能图标才用简洁线条。备用SVG插画也须有不同的轮廓、发型、脸部、服装配件、姿态及明暗层次；
              角色本应未揭晓时才使用剪影。不使用emoji替代立绘，不画裂图，不承诺原创生图等于原角色原画。
            - 功能图标统一为24×24 viewBox的内联SVG，fill="none"、stroke="currentColor"、stroke-width="1.8"，
              显式width/height：行内14~18px、导航20~22px。插画可用填充色与更大尺寸。
              常用路径统一：返回 M15 18l-6-6 6-6；关闭 M18 6 6 18M6 6l12 12；
              搜索 circle cx=11 cy=11 r=8 加 path M21 21l-4.3-4.3；箭头 M9 18l6-6-6-6。

            【静态结构与编辑器契约——必须遵守】
            - 所有卡片、商品、文字、图标、按钮必须直接写在HTML中。禁止任何script、onclick等事件属性、
              innerHTML、模板循环、加载时render()重建内容。运行时由应用统一补入，只修改状态，不重建可编辑节点。
            - 每个独立可编辑节点带唯一语义化 data-wf-id，例如 card-witch、btn-buy-witch、text-title；
              data-wf-role 使用 card/button/tab/modal/input/navigation-bar/icon/image/text/page。
              卡片和内部文字/图标保留正常DOM归属，禁止把整张卡片做成一个SVG或图片。
            - 已识别清单的每个实际对应节点加 data-wf-element-id="清单中的数据库ID"。
              特别是【交互清单】的每个源节点必须绑定其ID；不得编造、重复、套用参考页的ID。
              页面布局可以调整，ID保持不变；不要求照原识别坐标排版。
            - 每个导航项用一个button包住SVG和文字，并加 data-nav-item="1"，
              data-wf-edit-role="navigation-item"；底部主导航条加 data-wf-navigation="bar"、data-wf-chrome="bottom-navigation"。
              系统状态栏根节点加 data-wf-chrome="status"。公共组件复用【项目共享状态栏与底部主导航】规范，
              同项目不得分别改变高度、字号、图标路径和样式；只改变当前激活项，保留本页的元素ID与跳转。
              当前激活项标记 aria-current="page"；选中项的图标、文字与底色必须成套保留，禁止白色图标/白字落在白底上不可见。
              页头返回条和本页筛选标签不属于底部主导航，原稿没有状态栏/底栏时不要强行添加。
            - 跨页/跨页弹窗入口使用 data-nav="清单中的目标页面原名"。只能引用本项目页面名，
              外部预览容器结合数据库交互决定整页跳转或浮层。不要为跨页弹窗伪造本页空弹窗。
            - 返回使用 data-action="back"；关闭本页弹窗使用 data-action="close"。

            【声明式微交互——无需编写脚本】
            - 本页已有静态弹窗：触发按钮 data-modal="detail"，弹窗 id="wf-modal-detail" class="wf-modal"
              role="dialog" aria-modal="true"；默认隐藏，通过 wf-show 类显示。关闭按钮 data-action="close"。
              独立弹窗设计稿的页面主体应直接可见，不要把整个本页藏进未打开的wf-modal。
            - 本页标签：同组按钮 data-wf-tab="group-name"，选中项class="active"、aria-pressed="true"；
              如有切换内容，用 aria-controls="panel-id" 关联静态面板，非选中面板加hidden。
              标签不要同时使用跨页data-nav或data-action，否则优先执行跨页/返回动作。
            - 搜索框 data-wf-search="list-id"，商品列表 id="list-id"，各静态商品卡片 data-wf-search-text="商品名"。
            - 点击反馈 data-wf-feedback="已加入背包"；确认后关闭浮层再加 data-wf-close-modal="true"。
              收藏/点赞图标按钮使用 data-wf-toggle="favorite" aria-pressed="false"，保留图标按钮外观。
            - 标准控件由项目统一提供外观和交互，禁止自行绘制轨道、圆点、勾选图案或为其编写CSS/伪元素。
              开关输出空button：<button data-wf-id="toggle-pendant" data-wf-role="button" data-wf-control="switch"
              role="switch" aria-checked="false" aria-label="挂件开关"></button>，文案放在控件外部。
              保留设计稿与已识别元素的开关类型；独立开关各自切换开/关，不得改成同组互斥的radio。
              复选框/单选框分别用原生input type="checkbox|radio"，加 data-wf-control="checkbox|radio"、
              独立data-wf-id和aria-label（或外部label）；选中状态用checked，同组单选必须使用相同name。
              控件可放在任意Flex/Grid容器中。开关默认44×25px，复选框/单选框默认20×20px；
              只设计容器间距、标签文案和初始状态，主色由项目共享主题继承，不能改标准控件的内部结构。
            - 没有真实路由的动作提供明确的本地状态或反馈，不要编造业务结果、网络请求或付款接口。

            输出前检查：完整DOCTYPE/html/head/body；主体静态可见；无脚本或外部依赖；素材在清单中；
            源元素ID准确；交互目标存在；导航、弹窗、标签标识相互一致；没有重叠、挤字或空白裂图。
            """;

    public static final String REPAIR_SYSTEM_PROMPT = HTML_SYSTEM_PROMPT + """
            本轮是截图校对：第1张图是原设计稿，第2张图是当前HTML的渲染结果。
            对照检查布局比例、遗漏内容、文字挤压、颜色、图标和素材位置，以及商品卡片与角色是否被过度简化。
            保留已经装配的角色图片URL与图片容器，不得把真实素材降级为SVG人形；不要补回非必要背景光效。
            以提供的当前静态HTML为基础，保持已存在节点的data-wf-id、
            data-wf-uid、data-wf-element-id及交互目标不变。仍然只输出完整HTML，不得输出脚本。
            """;

    public static String buildHtmlUserPrompt(int canvasW, int canvasH, String pageName,
                                             List<String> pageNames, String elementSummary,
                                             String navSummary, String assetsSummary, String styleReferenceHtml) {
        return """
                本页名称：%s
                目标画布尺寸：%d × %d px，比例以本页截图为准。截图若是多倍图，请换算字号与间距。
                本项目页面清单（data-nav只允许原样引用这些名称）：
                %s
                已识别内容（数据库ID | 类型 | 文案 | 参考位置x,y,宽,高 | 颜色）：
                %s
                坐标仅帮助理解内容位置，不是生成HTML的布局约束。必须保留可点击节点的数据库ID。
                交互清单：
                %s
                可用素材清单：
                %s
                同项目风格参考（只继承视觉系统；以第1张图的内容和布局为准）：
                %s
                生成完整静态HTML，不要写脚本；交互通过规范中的data属性声明。
                """.formatted(pageName, canvasW, canvasH, String.join("\n", pageNames),
                    empty(elementSummary), empty(navSummary), empty(assetsSummary), reference(styleReferenceHtml));
    }

    public static String buildRepairUserPrompt(int canvasW, int canvasH, String pageName,
                                               List<String> pageNames, String elementSummary, String navSummary,
                                               String assetsSummary, String styleReferenceHtml, String currentHtml) {
        return buildHtmlUserPrompt(canvasW, canvasH, pageName, pageNames, elementSummary, navSummary,
                assetsSummary, styleReferenceHtml) + "\n当前静态HTML（以此为基础回修，保持节点标识）：\n" + currentHtml;
    }

    private static String empty(String value) { return value == null || value.isBlank() ? "（无）" : value; }
    private static String reference(String value) {
        return value == null || value.isBlank() ? "（无）" : value.substring(0, Math.min(value.length(), 24000));
    }

    /**
     * 组装最终系统提示词（语义提取版）。
     * 旧版会追加素材库说明；新架构下颜色由引擎采样、外观由模板渲染，AI 无需素材信息。
     */
    public static String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String buildUserPrompt(List<String> pageNames, int imageWidth, int imageHeight) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析这张设计稿图片，输出线框原型 JSON。\n\n");
        if (imageWidth > 0 && imageHeight > 0) {
            sb.append("设计稿原图尺寸为 ").append(imageWidth).append(" x ").append(imageHeight)
                    .append(" 像素，bbox 必须使用该尺寸下的像素坐标，不得超出图片范围。\n\n");
        }
        sb.append("本项目已识别的页面列表（用于交互 target 引用，target 必须使用这里的页面名之一）：\n");
        for (String name : pageNames) {
            sb.append("- ").append(name).append('\n');
        }
        sb.append("\n请确保：\n");
        sb.append("1. page_name 使用根据页面内容推断的简洁中文语义名称（如：首页、任务列表页），不要照抄上面的文件名。\n");
        sb.append("2. interaction.target 只能引用上面列表中的页面名（原样照抄），不要编造，无法判断目标页时省略 target。\n");
        sb.append("3. 只给带 interaction 的元素写 description，15 字以内；其余元素不写。\n");
        return sb.toString();
    }

    /** 组装交互提取的用户提示词（本页元素清单含 id + 全部页面名清单） */
    public static String buildInteractionUserPrompt(String pageName, int canvasW, int canvasH,
                                                    List<String> pageNames, String elementList) {
        StringBuilder sb = new StringBuilder();
        sb.append("本页页面名：").append(pageName).append('\n');
        sb.append("画布尺寸：").append(canvasW).append(" x ").append(canvasH)
                .append(" px（元素坐标为画布像素）\n\n");
        sb.append("本项目全部页面名清单（navigate/popup 的 target 只能从中原样照抄）：\n");
        for (String name : pageNames) {
            sb.append("- ").append(name).append('\n');
        }
        sb.append("\n本页已识别元素清单（格式：元素id | 类型 | 文案 | x,y,宽,高）：\n");
        sb.append(elementList == null || elementList.isBlank() ? "（无）" : elementList);
        sb.append("\n\n请输出交互 JSON。");
        return sb.toString();
    }
}
