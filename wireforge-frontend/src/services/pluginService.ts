export interface PluginMeta {
  id: string
  name: string
  subtitle: string
  author: string
  authorAvatar?: string
  version: string
  usersCount: string
  likesCount: number
  category: 'data' | 'assets' | 'motion' | 'utility'
  iconName: string // Lucide icon name
  bannerGradient: string
  description: string
  tags: string[]
}

export interface MockUserData {
  name: string
  title: string
  department: string
  phone: string
  email: string
  city: string
  avatarUrl: string
}

export interface VectorIconItem {
  id: string
  name: string
  collection: string
  category?: string
  tags?: string[]
  svg?: string
  previewUrl?: string
  prefix?: string
  iconName?: string
}

export const BUILTIN_PLUGINS: PluginMeta[] = [
  {
    id: 'user-profile-avatar',
    name: '用户头像素材库',
    subtitle: '真实人像与职场头像快速填充',
    author: 'WireForge 社区',
    version: '1.4.0',
    usersCount: '2.5万',
    likesCount: 1820,
    category: 'data',
    iconName: 'User',
    bannerGradient: 'from-blue-600 via-indigo-600 to-sky-500',
    description: '专为 UI 原型打造的高清头像库。支持写实人像、职场肖像等风格，可一键将头像填入选中的占位框或直接插入全新用户头像组件。',
    tags: ['头像', '真实肖像', '用户卡片', '个人资料'],
  },
  {
    id: 'chinese-user-generator',
    name: '中文业务数据生成器',
    subtitle: '姓名、职务、手机号等一键生成',
    author: '数据实验室',
    version: '2.1.0',
    usersCount: '3.8万',
    likesCount: 2450,
    category: 'data',
    iconName: 'FileText',
    bannerGradient: 'from-violet-600 via-purple-600 to-pink-500',
    description: '快速为原型稿生成符合国内业务规范的中文真实假数据，包括姓名、手机号、企业邮箱、省市区地址及部门职务，杜绝生硬重复文案。',
    tags: ['中文数据', '表单填充', '姓名职务', '企业信息'],
  },
  {
    id: 'vector-icon-finder',
    name: '开源矢量图标库',
    subtitle: '20万+ 纯净矢量图标即插即用',
    author: '矢量引擎',
    version: '3.0.0',
    usersCount: '5.2万',
    likesCount: 4210,
    category: 'assets',
    iconName: 'Search',
    bannerGradient: 'from-cyan-500 via-teal-600 to-emerald-500',
    description: '无缝接入开源图标生态（包括 Material Symbols, Lucide, Tabler, Solar, Remix 等 200,000+ 开源矢量 SVG），支持中英双语检索与一键置入画板。',
    tags: ['图标', 'SVG', '矢量', '开源图标集', 'Iconify'],
  },
  {
    id: 'icon-motion-effects',
    name: '组件交互微动效',
    subtitle: '呼吸、脉冲发光与动效生成器',
    author: '动效工坊',
    version: '1.2.0',
    usersCount: '1.3万',
    likesCount: 960,
    category: 'motion',
    iconName: 'Sparkles',
    bannerGradient: 'from-amber-500 via-orange-600 to-rose-500',
    description: '一键为静态图标、按钮及卡片注入流畅生动的轻量 CSS 微动效。内置呼吸、弹跳、旋转、脉冲光晕等交互效果，完美适配交互原型走查。',
    tags: ['微动效', '动画交互', 'CSS', '发光特效'],
  },
  {
    id: 'commercial-image-library',
    name: '商业高清配图库',
    subtitle: '百万级免版权摄影与场景配图',
    author: 'Unsplash 开放摄影',
    version: '1.5.0',
    usersCount: '6.8万',
    likesCount: 5120,
    category: 'assets',
    iconName: 'Image',
    bannerGradient: 'from-emerald-500 via-teal-600 to-cyan-500',
    description: '打通国际免版权高质量摄影生态，支持一键按科技商务、建筑空间、现代生活、自然风光等高频主题检索配图，支持直接填入选中的图片框或插入画报卡片。',
    tags: ['商业配图', 'Unsplash', '摄影素材', '背景图', '高清图片'],
  },
  {
    id: 'business-chart-generator',
    name: '业务图表与指标卡',
    subtitle: '核心KPI、走势折线与统计图表',
    author: '商业智能组',
    version: '2.0.0',
    usersCount: '4.5万',
    likesCount: 3890,
    category: 'data',
    iconName: 'BarChart3',
    bannerGradient: 'from-blue-600 via-indigo-600 to-violet-600',
    description: '专为管理后台与数据看板原型打造。快速生成带真实曲线与高质感样式的 KPI 指标卡、周趋势折线图、渠道柱状图与占比环形图，一键置入画板。',
    tags: ['图表', 'KPI', '折线图', '柱状图', '数据看板', '仪表盘'],
  },
  {
    id: 'brand-payment-logos',
    name: '品牌与支付矢量库',
    subtitle: '官方支付方式、企业与技术徽标',
    author: 'Simple Icons',
    version: '1.3.0',
    usersCount: '3.9万',
    likesCount: 2950,
    category: 'assets',
    iconName: 'CreditCard',
    bannerGradient: 'from-amber-500 via-orange-600 to-red-500',
    description: '汇聚全球主流移动支付（支付宝、微信支付、银联、Apple Pay、Visa）、社交平台与核心科技协议的官方纯净矢量 Logo，一键插入结账与合作模块。',
    tags: ['支付方式', 'Logo', '品牌图标', '结算收银', '合作厂商'],
  },
  {
    id: 'qr-barcode-generator',
    name: '动态二维码与条码',
    subtitle: '扫码登录、支付码与核销条码',
    author: '工具实验室',
    version: '1.1.0',
    usersCount: '3.1万',
    likesCount: 2180,
    category: 'utility',
    iconName: 'QrCode',
    bannerGradient: 'from-sky-500 via-blue-600 to-indigo-700',
    description: '支持自定义输入任意链接或订单号，实时生成可真实扫码验证的现代高对比二维码与商品条形码组件，完美适配扫码登录、下载引导与收银核销原型。',
    tags: ['二维码', '条形码', '扫码登录', '支付码', '核销码'],
  },
  {
    id: 'game-bubble-icons',
    name: '气泡图标',
    subtitle: '表情、礼物、钻石和宝箱',
    author: 'WireForge 社区',
    version: '1.0.0',
    usersCount: '8600',
    likesCount: 640,
    category: 'assets',
    iconName: 'Smile',
    bannerGradient: 'from-amber-400 via-orange-500 to-rose-500',
    description: '游戏界面常用的小图标。表情、点赞、礼物、红包、钻石、宝箱和体力都可以单独放进画板，适合扭蛋、任务和商城。',
    tags: ['游戏', '表情', '礼物', '钻石', '宝箱'],
  },
  {
    id: 'game-chat-bubbles',
    name: '聊天气泡',
    subtitle: '椭圆、云朵、爆炸、心想这些外形',
    author: 'WireForge 社区',
    version: '1.0.0',
    usersCount: '7200',
    likesCount: 510,
    category: 'utility',
    iconName: 'MessageCircle',
    bannerGradient: 'from-sky-500 via-blue-600 to-indigo-600',
    description: '按开源漫画气泡的外形来分：尖尾、椭圆、方框、丝带、云朵、心想、爆炸和虚线。区别在轮廓，不靠换颜色。',
    tags: ['游戏', '气泡', '对话', 'NPC', '系统提示'],
  },
  {
    id: 'game-chat-frame',
    name: '聊天框',
    subtitle: '头像、昵称、气泡和时间',
    author: 'WireForge 社区',
    version: '1.0.0',
    usersCount: '6900',
    likesCount: 480,
    category: 'utility',
    iconName: 'MessageCircle',
    bannerGradient: 'from-violet-500 via-purple-600 to-fuchsia-500',
    description: '一整条游戏消息。包含头像、昵称、气泡和时间，适合私聊和世界频道。',
    tags: ['游戏', '聊天', '私聊', '频道', '消息'],
  },
  {
    id: 'game-item-slots',
    name: '道具格',
    subtitle: '品质底色、图标和数量',
    author: 'WireForge 社区',
    version: '1.0.0',
    usersCount: '9100',
    likesCount: 730,
    category: 'assets',
    iconName: 'Gift',
    bannerGradient: 'from-yellow-400 via-amber-500 to-orange-600',
    description: '背包和奖励用的道具格。按普通、精良、稀有、史诗、传说区分底色，右下角带数量。',
    tags: ['游戏', '道具', '背包', '奖励', '品质'],
  },
]

// 预置高清免版权真实头像库
export const PRESET_AVATARS = [
  'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1488426862026-3ee34a7d66df?auto=format&fit=crop&w=300&q=80',
  'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=300&q=80',
]

// 常用本地矢量图标库集合 (精选 60+ 高频标准 SVG，即开即用)
export const PRESET_ICONS: VectorIconItem[] = [
  // 基础操作类
  {
    id: 'local:search',
    name: '搜索',
    collection: '',
    category: '基础操作',
    tags: ['search', 'find', 'lookup', '搜索', '查找', '放大镜'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>`,
  },
  {
    id: 'local:bell',
    name: '通知铃铛',
    collection: '',
    category: '基础操作',
    tags: ['bell', 'notice', 'notification', '铃铛', '提醒', '通知'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>`,
  },
  {
    id: 'local:user',
    name: '用户资料',
    collection: '',
    category: '用户组织',
    tags: ['user', 'profile', 'account', 'person', '用户', '头像', '人像'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>`,
  },
  {
    id: 'local:users',
    name: '团队成员',
    collection: '',
    category: '用户组织',
    tags: ['users', 'team', 'group', '团队', '群组', '人群'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path></svg>`,
  },
  {
    id: 'local:settings',
    name: '系统设置',
    collection: '',
    category: '基础操作',
    tags: ['settings', 'gear', 'config', '设置', '齿轮', '配置'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>`,
  },
  {
    id: 'local:star',
    name: '收藏标星',
    collection: '',
    category: '基础操作',
    tags: ['star', 'favorite', 'like', '标星', '收藏', '星星'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>`,
  },
  {
    id: 'local:heart',
    name: '心形喜爱',
    collection: '',
    category: '基础操作',
    tags: ['heart', 'love', 'like', '喜爱', '关注', '爱心'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>`,
  },
  {
    id: 'local:mail',
    name: '邮件收件箱',
    collection: '',
    category: '基础操作',
    tags: ['mail', 'email', 'inbox', '邮件', '邮箱', '信件'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>`,
  },
  {
    id: 'local:home',
    name: '首页主屏',
    collection: '',
    category: '导航布局',
    tags: ['home', 'house', 'main', '首页', '主页', '房子'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg>`,
  },
  {
    id: 'local:cart',
    name: '购物车',
    collection: '',
    category: '电商商务',
    tags: ['cart', 'shopping', 'store', 'buy', '购买', '商城', '购物车'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="21" r="1"></circle><circle cx="20" cy="21" r="1"></circle><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path></svg>`,
  },
  {
    id: 'local:send',
    name: '发送箭头',
    collection: '',
    category: '基础操作',
    tags: ['send', 'paperplane', 'submit', '发送', '飞机', '提交'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>`,
  },
  {
    id: 'local:check',
    name: '校验勾选',
    collection: '',
    category: '基础操作',
    tags: ['check', 'done', 'success', '对勾', '成功', '完成'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>`,
  },
  {
    id: 'local:calendar',
    name: '日历日程',
    collection: '',
    category: '基础操作',
    tags: ['calendar', 'date', 'schedule', '日历', '日期', '日程'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>`,
  },
  {
    id: 'local:plus',
    name: '添加新建',
    collection: '',
    category: '基础操作',
    tags: ['plus', 'add', 'create', '添加', '新建', '加号'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>`,
  },
  {
    id: 'local:trash',
    name: '垃圾桶删除',
    collection: '',
    category: '基础操作',
    tags: ['trash', 'delete', 'remove', '删除', '垃圾桶', '移除'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`,
  },
  {
    id: 'local:edit',
    name: '编辑画笔',
    collection: '',
    category: '基础操作',
    tags: ['edit', 'pencil', 'write', '编辑', '修改', '铅笔'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>`,
  },
  {
    id: 'local:close',
    name: '关闭取消',
    collection: '',
    category: '基础操作',
    tags: ['close', 'x', 'cancel', '关闭', '取消', '叉号'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>`,
  },
  {
    id: 'local:refresh',
    name: '刷新重载',
    collection: '',
    category: '基础操作',
    tags: ['refresh', 'reload', 'sync', '刷新', '同步', '重载'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>`,
  },
  {
    id: 'local:download',
    name: '下载导出',
    collection: '',
    category: '基础操作',
    tags: ['download', 'save', 'export', '下载', '保存', '导出'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>`,
  },
  {
    id: 'local:upload',
    name: '上传导入',
    collection: '',
    category: '基础操作',
    tags: ['upload', 'import', '上传', '导入'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>`,
  },
  {
    id: 'local:share',
    name: '分享外发',
    collection: '',
    category: '基础操作',
    tags: ['share', 'forward', '分享', '转发'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>`,
  },
  {
    id: 'local:link',
    name: '链接锚点',
    collection: '',
    category: '基础操作',
    tags: ['link', 'chain', 'url', '链接', '网址'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path></svg>`,
  },

  // 导航布局类
  {
    id: 'local:menu',
    name: '汉堡菜单',
    collection: '',
    category: '导航布局',
    tags: ['menu', 'hamburger', 'nav', '菜单', '列表', '导航'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>`,
  },
  {
    id: 'local:arrow-left',
    name: '返回左箭头',
    collection: '',
    category: '导航布局',
    tags: ['arrow-left', 'back', 'prev', '返回', '左箭头', '上一页'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>`,
  },
  {
    id: 'local:arrow-right',
    name: '前进右箭头',
    collection: '',
    category: '导航布局',
    tags: ['arrow-right', 'next', 'forward', '前进', '右箭头', '下一步'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>`,
  },
  {
    id: 'local:arrow-up',
    name: '置顶上箭头',
    collection: '',
    category: '导航布局',
    tags: ['arrow-up', 'top', '置顶', '上箭头', '上升'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"></line><polyline points="5 12 12 5 19 12"></polyline></svg>`,
  },
  {
    id: 'local:arrow-down',
    name: '折叠下箭头',
    collection: '',
    category: '导航布局',
    tags: ['arrow-down', 'collapse', '下箭头', '下拉', '展开'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><polyline points="19 12 12 19 5 12"></polyline></svg>`,
  },
  {
    id: 'local:more-horizontal',
    name: '更多选项',
    collection: '',
    category: '导航布局',
    tags: ['more', 'dots', 'horizontal', '更多', '省略号'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="1"></circle><circle cx="19" cy="12" r="1"></circle><circle cx="5" cy="12" r="1"></circle></svg>`,
  },
  {
    id: 'local:grid',
    name: '网格视图',
    collection: '',
    category: '导航布局',
    tags: ['grid', 'layout', 'view', '网格', '九宫格', '布局'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>`,
  },
  {
    id: 'local:map-pin',
    name: '定位图钉',
    collection: '',
    category: '导航布局',
    tags: ['pin', 'map', 'location', 'gps', '定位', '地址', '位置'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>`,
  },

  // 媒体视听类
  {
    id: 'local:image',
    name: '图片相册',
    collection: '',
    category: '媒体视听',
    tags: ['image', 'photo', 'picture', '图片', '照片', '相册'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>`,
  },
  {
    id: 'local:camera',
    name: '相机拍摄',
    collection: '',
    category: '媒体视听',
    tags: ['camera', 'photo', 'shoot', '相机', '拍照', '摄影'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"></path><circle cx="12" cy="13" r="4"></circle></svg>`,
  },
  {
    id: 'local:play',
    name: '视频播放',
    collection: '',
    category: '媒体视听',
    tags: ['play', 'video', 'media', '播放', '视频', '开始'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>`,
  },
  {
    id: 'local:pause',
    name: '暂停播放',
    collection: '',
    category: '媒体视听',
    tags: ['pause', 'stop', '暂停', '停止'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>`,
  },
  {
    id: 'local:volume',
    name: '音量开启',
    collection: '',
    category: '媒体视听',
    tags: ['volume', 'audio', 'sound', '音量', '声音', '喇叭'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"></path></svg>`,
  },
  {
    id: 'local:music',
    name: '音乐音符',
    collection: '',
    category: '媒体视听',
    tags: ['music', 'audio', 'song', '音乐', '音符', '歌曲'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>`,
  },
  {
    id: 'local:mic',
    name: '麦克风录音',
    collection: '',
    category: '媒体视听',
    tags: ['mic', 'microphone', 'voice', '麦克风', '语音', '话筒'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path><path d="M19 10v2a7 7 0 0 1-14 0v-2"></path><line x1="12" y1="19" x2="12" y2="23"></line><line x1="8" y1="23" x2="16" y2="23"></line></svg>`,
  },

  // 安全系统类
  {
    id: 'local:lock',
    name: '密码锁定',
    collection: '',
    category: '安全系统',
    tags: ['lock', 'security', 'password', '锁', '锁定', '密码', '安全'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>`,
  },
  {
    id: 'local:unlock',
    name: '解除锁定',
    collection: '',
    category: '安全系统',
    tags: ['unlock', 'open', '解锁', '打开'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 9.9-1"></path></svg>`,
  },
  {
    id: 'local:shield',
    name: '安全盾牌',
    collection: '',
    category: '安全系统',
    tags: ['shield', 'protect', 'safe', '盾牌', '防护', '安全'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>`,
  },
  {
    id: 'local:key',
    name: '凭据钥匙',
    collection: '',
    category: '安全系统',
    tags: ['key', 'auth', 'access', '钥匙', '认证', '权限'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 2l-2 2m-1.5 1.5L14 9l-1.5-1.5L11 9l-1-1v2l-1 1H7v2L5 15v2L2 20a2.83 2.83 0 0 0 4 4l3-3v-2h2v-2l1-1h2v-1l1.5-1.5L20 18l2-2-4.5-4.5z"></path></svg>`,
  },
  {
    id: 'local:cloud',
    name: '云端存储',
    collection: '',
    category: '安全系统',
    tags: ['cloud', 'storage', 'server', '云', '云端', '服务器'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path></svg>`,
  },
  {
    id: 'local:wifi',
    name: '无线网络',
    collection: '',
    category: '安全系统',
    tags: ['wifi', 'network', 'wireless', '网络', '无线', '信号'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.55a11 11 0 0 1 14.08 0"></path><path d="M1.42 9a16 16 0 0 1 21.16 0"></path><path d="M8.53 16.11a6 6 0 0 1 6.95 0"></path><line x1="12" y1="20" x2="12.01" y2="20"></line></svg>`,
  },
  {
    id: 'local:database',
    name: '数据仓库',
    collection: '',
    category: '安全系统',
    tags: ['database', 'data', 'sql', '数据库', '数据', '存储'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"></ellipse><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path></svg>`,
  },

  // 电商商务与图表类
  {
    id: 'local:wallet',
    name: '钱包资产',
    collection: '',
    category: '电商商务',
    tags: ['wallet', 'money', 'finance', '钱包', '资产', '资金'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z"></path><path d="M16 3H4a2 2 0 0 0-2 2v2"></path><circle cx="16" cy="14" r="1"></circle></svg>`,
  },
  {
    id: 'local:credit-card',
    name: '银行卡片',
    collection: '',
    category: '电商商务',
    tags: ['card', 'credit', 'payment', '银行卡', '支付', '信用卡'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"></rect><line x1="1" y1="10" x2="23" y2="10"></line></svg>`,
  },
  {
    id: 'local:tag',
    name: '价格标签',
    collection: '',
    category: '电商商务',
    tags: ['tag', 'discount', 'price', '标签', '折扣', '优惠'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"></path><line x1="7" y1="7" x2="7.01" y2="7"></line></svg>`,
  },
  {
    id: 'local:gift',
    name: '礼品奖赠',
    collection: '',
    category: '电商商务',
    tags: ['gift', 'present', 'reward', '礼物', '礼包', '礼盒'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 12 20 22 4 22 4 12"></polyline><rect x="2" y="7" width="20" height="5"></rect><line x1="12" y1="22" x2="12" y2="7"></line><path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z"></path><path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z"></path></svg>`,
  },
  {
    id: 'local:bar-chart',
    name: '柱状图表',
    collection: '',
    category: '电商商务',
    tags: ['chart', 'bar', 'stats', '柱状图', '统计', '图表'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"></line><line x1="12" y1="20" x2="12" y2="4"></line><line x1="6" y1="20" x2="6" y2="14"></line></svg>`,
  },
  {
    id: 'local:trending-up',
    name: '趋势增长',
    collection: '',
    category: '电商商务',
    tags: ['trending', 'growth', 'up', '增长', '趋势', '上升'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"></polyline><polyline points="17 6 23 6 23 12"></polyline></svg>`,
  },
  {
    id: 'local:file-text',
    name: '文件文档',
    collection: '',
    category: '电商商务',
    tags: ['file', 'doc', 'document', '文件', '文档', '资料'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>`,
  },
  {
    id: 'local:folder',
    name: '文件目录',
    collection: '',
    category: '电商商务',
    tags: ['folder', 'directory', '文件夹', '目录'],
    svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>`,
  },
]

// 中英文图标搜索词映射词典（支持 100+ 中文常见词一键联通 Iconify 20万+ 开源库）
export const ZH_EN_KEYWORD_MAP: Record<string, string> = {
  '家': 'home',
  '首页': 'home',
  '主页': 'home',
  '房子': 'home',
  '用户': 'user',
  '个人': 'user',
  '头像': 'user',
  '账号': 'user',
  '人': 'person',
  '人群': 'users',
  '团队': 'users',
  '设置': 'settings',
  '齿轮': 'settings',
  '配置': 'settings',
  '搜索': 'search',
  '查找': 'search',
  '放大镜': 'search',
  '铃铛': 'bell',
  '通知': 'notification',
  '提醒': 'alert',
  '警告': 'warning',
  '错误': 'error',
  '信息': 'info',
  '帮助': 'help',
  '问号': 'help',
  '勾': 'check',
  '对勾': 'check',
  '完成': 'check',
  '成功': 'success',
  '关闭': 'close',
  '取消': 'cancel',
  '加': 'plus',
  '添加': 'plus',
  '新建': 'plus',
  '删除': 'trash',
  '垃圾桶': 'trash',
  '编辑': 'edit',
  '修改': 'pencil',
  '画笔': 'pen',
  '星星': 'star',
  '收藏': 'star',
  '爱心': 'heart',
  '心': 'heart',
  '喜欢': 'heart',
  '点赞': 'thumbs-up',
  '分享': 'share',
  '下载': 'download',
  '上传': 'upload',
  '导入': 'upload',
  '导出': 'download',
  '刷新': 'refresh',
  '重载': 'reload',
  '同步': 'sync',
  '日历': 'calendar',
  '日期': 'date',
  '时间': 'clock',
  '时钟': 'time',
  '历史': 'history',
  '邮件': 'mail',
  '邮箱': 'email',
  '信封': 'mail',
  '发送': 'send',
  '纸飞机': 'send',
  '聊天': 'message',
  '消息': 'chat',
  '评论': 'comment',
  '电话': 'phone',
  '手机': 'smartphone',
  '电脑': 'laptop',
  '屏幕': 'monitor',
  '平板': 'tablet',
  '相机': 'camera',
  '拍照': 'camera',
  '图片': 'image',
  '照片': 'photo',
  '视频': 'video',
  '播放': 'play',
  '暂停': 'pause',
  '停止': 'stop',
  '音乐': 'music',
  '音频': 'audio',
  '声音': 'volume',
  '静音': 'mute',
  '麦克风': 'mic',
  '文件': 'file',
  '文档': 'document',
  '文件夹': 'folder',
  '代码': 'code',
  '终端': 'terminal',
  '锁': 'lock',
  '锁定': 'lock',
  '解锁': 'unlock',
  '钥匙': 'key',
  '盾牌': 'shield',
  '安全': 'security',
  '眼睛': 'eye',
  '查看': 'eye',
  '隐藏': 'eye-off',
  '购物车': 'cart',
  '商店': 'store',
  '商城': 'shop',
  '钱包': 'wallet',
  '卡': 'credit-card',
  '支付': 'payment',
  '钱': 'money',
  '金钱': 'dollar',
  '标签': 'tag',
  '礼物': 'gift',
  '定位': 'map-pin',
  '地图': 'map',
  '导航': 'navigation',
  '车': 'car',
  '汽车': 'car',
  '飞机': 'plane',
  '火箭': 'rocket',
  '云': 'cloud',
  '太阳': 'sun',
  '月亮': 'moon',
  '天气': 'weather',
  '网络': 'wifi',
  '信号': 'signal',
  '电池': 'battery',
  '链接': 'link',
  '图表': 'chart',
  '柱状图': 'bar-chart',
  '折线图': 'line-chart',
  '数据': 'database',
  '火': 'flame',
  '热门': 'fire',
  '闪电': 'zap',
  '箭头': 'arrow',
  '返回': 'arrow-left',
}

// 缓存已拉取的 SVG 内容
const svgTextCache = new Map<string, string>()

// 格式化开源图标集前缀名称
export function formatCollectionName(prefix: string): string {
  const map: Record<string, string> = {
    'mdi': 'Material Design',
    'material-symbols': 'Material Symbols',
    'lucide': 'Lucide',
    'tabler': 'Tabler',
    'solar': 'Solar',
    'ph': 'Phosphor',
    'ri': 'Remix Icon',
    'carbon': 'Carbon',
    'heroicons': 'Heroicons',
    'line-md': 'Line MD',
    'boxicons': 'BoxIcons',
    'mingcute': 'MingCute',
    'fa6-solid': 'FontAwesome',
    'fa6-regular': 'FontAwesome',
  }
  return map[prefix] || prefix
}

// 联网从 Iconify 200,000+ 开源矢量库中检索
export async function searchOnlineIcons(query: string, limit = 48): Promise<VectorIconItem[]> {
  const trimmed = query.trim()
  if (!trimmed) return []

  let searchTerm = trimmed.toLowerCase()
  if (/[\u4e00-\u9fa5]/.test(searchTerm)) {
    if (ZH_EN_KEYWORD_MAP[searchTerm]) {
      searchTerm = ZH_EN_KEYWORD_MAP[searchTerm]
    } else {
      for (const [zh, en] of Object.entries(ZH_EN_KEYWORD_MAP)) {
        if (searchTerm.includes(zh)) {
          searchTerm = en
          break
        }
      }
    }
  }

  try {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 4500)
    const res = await fetch(`https://api.iconify.design/search?query=${encodeURIComponent(searchTerm)}&limit=${limit}`, {
      signal: controller.signal,
    })
    clearTimeout(timer)
    if (!res.ok) return []
    const data = await res.json()
    const icons: string[] = data.icons || []

    return icons.map((full) => {
      const [prefix, iconName] = full.split(':')
      const previewUrl = `https://api.iconify.design/${prefix}/${iconName}.svg`
      return {
        id: full,
        name: iconName,
        collection: formatCollectionName(prefix),
        prefix,
        iconName,
        previewUrl,
      }
    })
  } catch (err) {
    console.warn('Iconify 在线图标库检索超时或失败，已自动降级为本地库:', err)
    return []
  }
}

// 获取可用于画布插入的纯净 SVG 字符串
export async function fetchCleanSvg(item: VectorIconItem): Promise<string> {
  if (item.svg) return item.svg
  if (svgTextCache.has(item.id)) return svgTextCache.get(item.id)!

  if (item.previewUrl) {
    try {
      const res = await fetch(item.previewUrl)
      if (res.ok) {
        let svg = await res.text()
        // 修正默认尺寸为 24x24 并保留 currentColor 便于变色
        svg = svg.replace(/width="1em"/g, 'width="24"').replace(/height="1em"/g, 'height="24"')
        if (!svg.includes('width=') && !svg.includes('height=')) {
          svg = svg.replace('<svg ', '<svg width="24" height="24" ')
        }
        svgTextCache.set(item.id, svg)
        return svg
      }
    } catch (err) {
      console.warn('拉取在线图标 SVG 失败:', err)
    }
  }

  return `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/></svg>`
}

// 中文业务假数据生成逻辑
const CHINESE_FIRST_NAMES = ['张', '王', '李', '赵', '陈', '周', '吴', '刘', '林', '黄', '杨', '韩', '徐', '孙']
const CHINESE_LAST_NAMES = ['小凡', '青羽', '子墨', '晓晨', '浩轩', '雨涵', '雅琪', '嘉明', '思辰', '一诺', '欣然', '俊杰']
const CHINESE_TITLES = ['资深产品经理', '交互体验设计师', '前端架构师', '全栈开发专家', '运营总监', '项目总监', '视觉设计总监']
const CHINESE_DEPTS = ['用户体验中心 (UXD)', '核心产品研发部', '商业增长与运营部', '云原生基础架构部', '创新业务实验室']
const CHINESE_CITIES = ['北京市海淀区中关村南大街1号', '上海市浦东新区张江高科技园区', '深圳市南山区科技园科技南一路', '杭州市余杭区文一西路969号', '成都市高新区天府大道北段']

/**
 * 动态从真实人像库拉取一批全新头像
 * 优先通过 RandomUser 开放 API 动态获取随机真实头像；
 * 若网络异常则从 200 张真实 CDN 人像池中无重复随机抽选，保证每次点击都能换到全新真实人像。
 */
export async function fetchDynamicAvatars(count = 12, gender: 'all' | 'men' | 'women' = 'all'): Promise<string[]> {
  try {
    const genderParam = gender === 'men' ? '&gender=male' : gender === 'women' ? '&gender=female' : ''
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 3500)
    const res = await fetch(`https://randomuser.me/api/?results=${count}${genderParam}&inc=picture`, {
      signal: controller.signal,
    })
    clearTimeout(timer)
    if (res.ok) {
      const data = await res.json()
      if (Array.isArray(data.results) && data.results.length > 0) {
        return data.results.map((r: any) => r.picture?.large || r.picture?.medium).filter(Boolean)
      }
    }
  } catch (e) {
    // 超时或失败降级
  }

  // 本地 CDN 动态备用池（200 张真实男女头像无重复随机采样）
  const pool: string[] = []
  const used = new Set<string>()
  let attempts = 0
  while (pool.length < count && attempts < 120) {
    attempts++
    const isMale = gender === 'men' ? true : gender === 'women' ? false : Math.random() > 0.5
    const id = Math.floor(Math.random() * 95) + 1
    const url = `https://randomuser.me/api/portraits/${isMale ? 'men' : 'women'}/${id}.jpg`
    if (!used.has(url)) {
      used.add(url)
      pool.push(url)
    }
  }
  return pool.length > 0 ? pool : PRESET_AVATARS
}

export function generateRandomUserData(): MockUserData {
  const fName = CHINESE_FIRST_NAMES[Math.floor(Math.random() * CHINESE_FIRST_NAMES.length)]
  const lName = CHINESE_LAST_NAMES[Math.floor(Math.random() * CHINESE_LAST_NAMES.length)]
  const fullName = fName + lName
  const phone = '1' + ['3', '5', '7', '8', '9'][Math.floor(Math.random() * 5)] + String(Math.floor(100000000 + Math.random() * 900000000)).slice(1)
  const pinyin = 'user_' + Math.floor(100 + Math.random() * 900)
  const isMale = Math.random() > 0.5
  const avatarId = Math.floor(Math.random() * 95) + 1
  const avatar = `https://randomuser.me/api/portraits/${isMale ? 'men' : 'women'}/${avatarId}.jpg`

  return {
    name: fullName,
    title: CHINESE_TITLES[Math.floor(Math.random() * CHINESE_TITLES.length)],
    department: CHINESE_DEPTS[Math.floor(Math.random() * CHINESE_DEPTS.length)],
    phone: phone,
    email: `${pinyin}@wireforge.design`,
    city: CHINESE_CITIES[Math.floor(Math.random() * CHINESE_CITIES.length)],
    avatarUrl: avatar,
  }
}

// ==========================================
// 插件 5: 商业高清配图库 (Unsplash & Picsum API)
// ==========================================
export interface CommercialImageItem {
  id: string
  title: string
  category: string
  url: string
  author: string
}

export const PRESET_COMMERCIAL_IMAGES: CommercialImageItem[] = [
  // 科技商务
  {
    id: 'tech-1',
    title: '极简代码工作台',
    category: '科技商务',
    url: 'https://images.unsplash.com/photo-1498050108023-c5249f4df085?auto=format&fit=crop&w=600&q=80',
    author: 'Christopher Gower',
  },
  {
    id: 'tech-2',
    title: '现代化研讨会议室',
    category: '科技商务',
    url: 'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=600&q=80',
    author: 'Nastuh Abootalebi',
  },
  {
    id: 'tech-3',
    title: '平板数据看板分析',
    category: '科技商务',
    url: 'https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=600&q=80',
    author: 'Carlos Muza',
  },
  {
    id: 'tech-4',
    title: '科技团队敏捷协同',
    category: '科技商务',
    url: 'https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=600&q=80',
    author: 'Annie Spratt',
  },
  // 建筑空间
  {
    id: 'arch-1',
    title: '极简玻璃幕墙中庭',
    category: '建筑空间',
    url: 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=600&q=80',
    author: 'Sean Pollock',
  },
  {
    id: 'arch-2',
    title: '现代主义几何立面',
    category: '建筑空间',
    url: 'https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=600&q=80',
    author: 'Daniel von Appen',
  },
  {
    id: 'arch-3',
    title: '清水混凝土螺旋楼梯',
    category: '建筑空间',
    url: 'https://images.unsplash.com/photo-1508873696983-2df5293cb32f?auto=format&fit=crop&w=600&q=80',
    author: 'Scott Webb',
  },
  {
    id: 'arch-4',
    title: '采光通透开放走廊',
    category: '建筑空间',
    url: 'https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&w=600&q=80',
    author: 'Nastuh Abootalebi',
  },
  // 现代生活
  {
    id: 'life-1',
    title: '手冲咖啡与工作笔记',
    category: '现代生活',
    url: 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=600&q=80',
    author: 'Nathan Dumlao',
  },
  {
    id: 'life-2',
    title: '晨光下的书房绿植',
    category: '现代生活',
    url: 'https://images.unsplash.com/photo-1449247709967-d4461a6a6103?auto=format&fit=crop&w=600&q=80',
    author: 'Roman Bozhko',
  },
  {
    id: 'life-3',
    title: '设计师桌面灵感文具',
    category: '现代生活',
    url: 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=600&q=80',
    author: 'Marvin Meyer',
  },
  {
    id: 'life-4',
    title: '城市天际线与日落',
    category: '现代生活',
    url: 'https://images.unsplash.com/photo-1477959858617-67f30bc75b82?auto=format&fit=crop&w=600&q=80',
    author: 'Sawyer Bengtson',
  },
  // 自然风光
  {
    id: 'nature-1',
    title: '晨雾笼罩的雪山山峦',
    category: '自然风光',
    url: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=600&q=80',
    author: 'Kalina Sakal',
  },
  {
    id: 'nature-2',
    title: '深蓝静谧的高山湖泊',
    category: '自然风光',
    url: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=600&q=80',
    author: 'Bailey Zindel',
  },
  {
    id: 'nature-3',
    title: '晨光穿透森林光束',
    category: '自然风光',
    url: 'https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&w=600&q=80',
    author: 'Sebastian Unrau',
  },
  {
    id: 'nature-4',
    title: '极简沙漠波浪纹理',
    category: '自然风光',
    url: 'https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=600&q=80',
    author: 'Jeremy Bishop',
  },
]

// 联网调用 Picsum Photos 开放 REST API 获取高质量真实商业摄影
export async function fetchPicsumPhotos(page = 1, limit = 8): Promise<CommercialImageItem[]> {
  try {
    const res = await fetch(`https://picsum.photos/v2/list?page=${page}&limit=${limit}`)
    if (!res.ok) return []
    const data = await res.json()
    return data.map((item: any) => ({
      id: `picsum-${item.id}`,
      title: `摄影作品 #${item.id}`,
      category: '随机精选',
      url: `https://picsum.photos/id/${item.id}/600/400`,
      author: item.author,
    }))
  } catch (e) {
    console.warn('Picsum API 请求失败:', e)
    return []
  }
}

// ==========================================
// 插件 6: 业务图表与指标卡 (SaaS & Fintech Charts)
// ==========================================
export interface ChartPresetItem {
  id: string
  name: string
  category: string
  description: string
  previewSvg: string
  getComponentHtml: () => string
  width: number
  height: number
}

export const CHART_PRESETS: ChartPresetItem[] = [
  {
    id: 'kpi-gmv',
    name: '核心 KPI 环比指标卡',
    category: '指标概览',
    description: '含金额/数据、正向环比增长标签与大面积发光趋势图',
    previewSvg: `<svg viewBox="0 0 100 50" width="100%" height="100%" style="width: 100%; height: 100%; display: block;"><path d="M5 40 Q25 35 45 20 T85 10" fill="none" stroke="#0D99FF" stroke-width="3" stroke-linecap="round"/><path d="M5 40 Q25 35 45 20 T85 10 L85 48 L5 48 Z" fill="#0D99FF" opacity="0.2"/></svg>`,
    width: 320,
    height: 140,
    getComponentHtml: () => `
<div class="wf-kpi-card" style="width: 100%; height: 100%; box-sizing: border-box; min-width: 0; min-height: 0; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05); padding: 14px 16px; display: flex; flex-direction: column; justify-content: space-between; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <span style="font-size: 13px; font-weight: 600; color: #64748b;">今日核心营收 (GMV)</span>
    <span style="padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0;">+18.4% 环比</span>
  </div>
  <div style="display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; flex: 1; min-height: 0; margin-top: 6px;">
    <div style="display: flex; flex-direction: column; justify-content: flex-end; shrink: 0;">
      <span style="font-size: 11px; color: #94a3b8; margin-bottom: 2px;">实时交易总额</span>
      <div style="font-size: 26px; font-weight: 800; font-family: ui-monospace, SFMono-Regular, monospace; color: #0f172a; line-height: 1.1; letter-spacing: -0.5px;">¥284,590</div>
    </div>
    <div style="flex: 1; height: 100%; min-height: 0; display: flex; align-items: flex-end;">
      <svg width="100%" height="100%" viewBox="0 0 120 45" preserveAspectRatio="none" style="width: 100%; height: 100%; min-height: 0; display: block; overflow: visible;">
        <defs>
          <linearGradient id="sparkGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#0D99FF" stop-opacity="0.38"/>
            <stop offset="100%" stop-color="#0D99FF" stop-opacity="0.02"/>
          </linearGradient>
        </defs>
        <path d="M0 38 Q30 32 60 16 T120 4 L120 45 L0 45 Z" fill="url(#sparkGrad)"/>
        <path d="M0 38 Q30 32 60 16 T120 4" fill="none" stroke="#0D99FF" stroke-width="2.5" stroke-linecap="round"/>
        <circle cx="120" cy="4" r="3.5" fill="#0D99FF" stroke="#FFFFFF" stroke-width="1.5"/>
      </svg>
    </div>
  </div>
</div>
`,
  },
  {
    id: 'area-revenue-trend',
    name: '周订单走势面积图',
    category: '趋势走势',
    description: '近7天业务走势，带双渐变曲面与虚线参考网格',
    previewSvg: `<svg viewBox="0 0 100 50" width="100%" height="100%" style="width: 100%; height: 100%; display: block;"><line x1="5" y1="15" x2="95" y2="15" stroke="#E2E8F0" stroke-dasharray="2 2"/><line x1="5" y1="35" x2="95" y2="35" stroke="#E2E8F0" stroke-dasharray="2 2"/><path d="M5 45 C20 40, 35 15, 55 25 C75 35, 85 10, 95 8 L95 48 L5 48 Z" fill="#3B82F6" opacity="0.25"/><path d="M5 45 C20 40, 35 15, 55 25 C75 35, 85 10, 95 8" fill="none" stroke="#3B82F6" stroke-width="2"/></svg>`,
    width: 480,
    height: 260,
    getComponentHtml: () => `
<div class="wf-chart-card" style="width: 100%; height: 100%; box-sizing: border-box; min-width: 0; min-height: 0; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05); padding: 16px; display: flex; flex-direction: column; justify-content: space-between; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <div style="font-size: 15px; font-weight: 700; color: #0f172a;">近 7 日订单趋势</div>
    <div style="font-size: 12px; color: #64748b;">总订单 18,920 · 单位: 笔</div>
  </div>
  <div style="position: relative; width: 100%; flex: 1; min-height: 0; margin: 6px 0;">
    <svg width="100%" height="100%" viewBox="0 0 360 130" preserveAspectRatio="none" style="width: 100%; height: 100%; display: block; overflow: visible;">
      <defs>
        <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#3B82F6" stop-opacity="0.45"/>
          <stop offset="100%" stop-color="#3B82F6" stop-opacity="0.02"/>
        </linearGradient>
      </defs>
      <line x1="0" y1="30" x2="360" y2="30" stroke="#f1f5f9" stroke-width="1" stroke-dasharray="3 3"/>
      <line x1="0" y1="75" x2="360" y2="75" stroke="#f1f5f9" stroke-width="1" stroke-dasharray="3 3"/>
      <line x1="0" y1="120" x2="360" y2="120" stroke="#e2e8f0" stroke-width="1"/>
      <path d="M0 105 C50 100, 100 40, 160 65 C220 90, 270 25, 360 15 L360 125 L0 125 Z" fill="url(#areaGrad)"/>
      <path d="M0 105 C50 100, 100 40, 160 65 C220 90, 270 25, 360 15" fill="none" stroke="#3B82F6" stroke-width="3" stroke-linecap="round"/>
      <circle cx="160" cy="65" r="4.5" fill="#3B82F6" stroke="#FFFFFF" stroke-width="2"/>
      <circle cx="360" cy="15" r="4.5" fill="#3B82F6" stroke="#FFFFFF" stroke-width="2"/>
    </svg>
  </div>
  <div style="display: flex; justify-content: space-between; font-size: 11px; font-family: ui-monospace, monospace; color: #94a3b8; border-top: 1px solid #f1f5f9; padding-top: 8px;">
    <span>周一</span><span>周二</span><span>周三</span><span>周四</span><span>周五</span><span>周六</span><span>周日</span>
  </div>
</div>
`,
  },
  {
    id: 'bar-acquisition',
    name: '渠道获客对比柱状图',
    category: '渠道对比',
    description: '圆角渐变多柱对比，带百分比及来源图例',
    previewSvg: `<svg viewBox="0 0 100 50" width="100%" height="100%" style="width: 100%; height: 100%; display: block;"><rect x="15" y="15" width="12" height="30" rx="3" fill="#0D99FF"/><rect x="35" y="22" width="12" height="23" rx="3" fill="#8B5CF6"/><rect x="55" y="10" width="12" height="35" rx="3" fill="#10B981"/><rect x="75" y="28" width="12" height="17" rx="3" fill="#F59E0B"/></svg>`,
    width: 440,
    height: 240,
    getComponentHtml: () => `
<div class="wf-bar-card" style="width: 100%; height: 100%; box-sizing: border-box; min-width: 0; min-height: 0; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05); padding: 16px; display: flex; flex-direction: column; justify-content: space-between; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <div style="font-size: 15px; font-weight: 700; color: #0f172a;">获客渠道转化分布</div>
    <div style="font-size: 11px; font-weight: 700; background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; padding: 3px 8px; border-radius: 6px;">总转化率 32.4%</div>
  </div>
  <div style="position: relative; width: 100%; flex: 1; min-height: 0; margin: 6px 0;">
    <svg width="100%" height="100%" viewBox="0 0 360 120" preserveAspectRatio="none" style="width: 100%; height: 100%; display: block;">
      <defs>
        <linearGradient id="barGrad1" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#38BDF8"/><stop offset="100%" stop-color="#2563EB"/></linearGradient>
        <linearGradient id="barGrad2" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#C084FC"/><stop offset="100%" stop-color="#7C3AED"/></linearGradient>
        <linearGradient id="barGrad3" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#34D399"/><stop offset="100%" stop-color="#059669"/></linearGradient>
        <linearGradient id="barGrad4" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#FBBF24"/><stop offset="100%" stop-color="#D97706"/></linearGradient>
      </defs>
      <line x1="0" y1="110" x2="360" y2="110" stroke="#E2E8F0" stroke-width="1.5"/>
      <line x1="0" y1="60" x2="360" y2="60" stroke="#F1F5F9" stroke-dasharray="2 2"/>
      <line x1="0" y1="15" x2="360" y2="15" stroke="#F1F5F9" stroke-dasharray="2 2"/>
      <rect x="30" y="20" width="46" height="90" rx="6" fill="url(#barGrad1)"/>
      <rect x="120" y="45" width="46" height="65" rx="6" fill="url(#barGrad2)"/>
      <rect x="210" y="70" width="46" height="40" rx="6" fill="url(#barGrad3)"/>
      <rect x="295" y="85" width="46" height="25" rx="6" fill="url(#barGrad4)"/>
    </svg>
  </div>
  <div style="display: grid; grid-template-columns: repeat(4, 1fr); text-align: center; border-top: 1px solid #f1f5f9; padding-top: 8px;">
    <div><div style="font-weight: 700; font-size: 13px; color: #0f172a;">42%</div><div style="font-size: 11px; color: #64748b; margin-top: 2px;">自然搜索</div></div>
    <div><div style="font-weight: 700; font-size: 13px; color: #0f172a;">28%</div><div style="font-size: 11px; color: #64748b; margin-top: 2px;">社交裂变</div></div>
    <div><div style="font-weight: 700; font-size: 13px; color: #0f172a;">18%</div><div style="font-size: 11px; color: #64748b; margin-top: 2px;">外部链接</div></div>
    <div><div style="font-weight: 700; font-size: 13px; color: #0f172a;">12%</div><div style="font-size: 11px; color: #64748b; margin-top: 2px;">线下推广</div></div>
  </div>
</div>
`,
  },
  {
    id: 'donut-user-segment',
    name: '客户占比环形图',
    category: '占比分布',
    description: '高质感环形甜甜圈图，附带中心高亮与分类标签',
    previewSvg: `<svg viewBox="0 0 100 50" width="100%" height="100%" style="width: 100%; height: 100%; display: block;"><circle cx="50" cy="25" r="18" fill="none" stroke="#E2E8F0" stroke-width="7"/><circle cx="50" cy="25" r="18" fill="none" stroke="#0D99FF" stroke-width="7" stroke-dasharray="75 113" stroke-linecap="round"/><circle cx="50" cy="25" r="18" fill="none" stroke="#8B5CF6" stroke-width="7" stroke-dasharray="30 113" stroke-dashoffset="-75" stroke-linecap="round"/></svg>`,
    width: 380,
    height: 200,
    getComponentHtml: () => `
<div class="wf-donut-card" style="width: 100%; height: 100%; box-sizing: border-box; min-width: 0; min-height: 0; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05); padding: 16px; display: flex; align-items: center; justify-content: space-between; gap: 16px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <div style="position: relative; flex: 1; height: 100%; min-height: 0; display: flex; align-items: center; justify-content: center;">
    <svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet" style="width: 100%; height: 100%; display: block; transform: rotate(-90deg);">
      <circle cx="50" cy="50" r="38" fill="none" stroke="#F1F5F9" stroke-width="12"/>
      <circle cx="50" cy="50" r="38" fill="none" stroke="#0D99FF" stroke-width="12" stroke-dasharray="160 238" stroke-linecap="round"/>
      <circle cx="50" cy="50" r="38" fill="none" stroke="#8B5CF6" stroke-width="12" stroke-dasharray="55 238" stroke-dashoffset="-160" stroke-linecap="round"/>
      <circle cx="50" cy="50" r="38" fill="none" stroke="#10B981" stroke-width="12" stroke-dasharray="23 238" stroke-dashoffset="-215" stroke-linecap="round"/>
    </svg>
    <div style="position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; pointer-events: none;">
      <span style="font-size: 16px; font-weight: 800; color: #0f172a; line-height: 1;">68%</span>
      <span style="font-size: 10px; color: #64748b; margin-top: 2px;">企业大客</span>
    </div>
  </div>
  <div style="flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 10px; font-size: 12px;">
    <div style="display: flex; align-items: center; justify-content: space-between;">
      <span style="display: flex; align-items: center; gap: 6px; color: #475569;"><span style="width: 8px; height: 8px; border-radius: 50%; background: #0D99FF; display: inline-block;"></span>大中型企业</span>
      <span style="font-weight: 700; font-family: monospace; color: #0f172a;">68%</span>
    </div>
    <div style="display: flex; align-items: center; justify-content: space-between;">
      <span style="display: flex; align-items: center; gap: 6px; color: #475569;"><span style="width: 8px; height: 8px; border-radius: 50%; background: #8B5CF6; display: inline-block;"></span>成长型团队</span>
      <span style="font-weight: 700; font-family: monospace; color: #0f172a;">22%</span>
    </div>
    <div style="display: flex; align-items: center; justify-content: space-between;">
      <span style="display: flex; align-items: center; gap: 6px; color: #475569;"><span style="width: 8px; height: 8px; border-radius: 50%; background: #10B981; display: inline-block;"></span>独立开发者</span>
      <span style="font-weight: 700; font-family: monospace; color: #0f172a;">10%</span>
    </div>
  </div>
</div>
`,
  },
  {
    id: 'line-multi-metric',
    name: '双折线指标对比图',
    category: '趋势走势',
    description: '全天24小时 PV/UV 流量走势双线拟合与坐标轴',
    previewSvg: `<svg viewBox="0 0 100 50" width="100%" height="100%" style="width: 100%; height: 100%; display: block;"><path d="M5 38 Q30 20 55 30 T95 10" fill="none" stroke="#0D99FF" stroke-width="2.5"/><path d="M5 45 Q30 35 55 38 T95 22" fill="none" stroke="#10B981" stroke-width="2.5" stroke-dasharray="3 2"/></svg>`,
    width: 480,
    height: 260,
    getComponentHtml: () => `
<div class="wf-line-card" style="width: 100%; height: 100%; box-sizing: border-box; min-width: 0; min-height: 0; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05); padding: 16px; display: flex; flex-direction: column; justify-content: space-between; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <div style="font-size: 15px; font-weight: 700; color: #0f172a;">访问量 (PV) vs 独立访客 (UV)</div>
    <div style="display: flex; align-items: center; gap: 12px; font-size: 12px;">
      <span style="display: flex; align-items: center; gap: 4px; color: #475569;"><span style="width: 8px; height: 8px; border-radius: 50%; background: #0D99FF; display: inline-block;"></span>PV 访问</span>
      <span style="display: flex; align-items: center; gap: 4px; color: #475569;"><span style="width: 8px; height: 8px; border-radius: 50%; background: #10B981; display: inline-block;"></span>UV 访客</span>
    </div>
  </div>
  <div style="position: relative; width: 100%; flex: 1; min-height: 0; margin: 6px 0;">
    <svg width="100%" height="100%" viewBox="0 0 360 120" preserveAspectRatio="none" style="width: 100%; height: 100%; display: block; overflow: visible;">
      <line x1="0" y1="25" x2="360" y2="25" stroke="#f1f5f9" stroke-width="1" stroke-dasharray="3 3"/>
      <line x1="0" y1="65" x2="360" y2="65" stroke="#f1f5f9" stroke-width="1" stroke-dasharray="3 3"/>
      <line x1="0" y1="105" x2="360" y2="105" stroke="#e2e8f0" stroke-width="1"/>
      <path d="M0 80 Q60 40 120 60 T240 30 T360 15" fill="none" stroke="#0D99FF" stroke-width="3" stroke-linecap="round"/>
      <path d="M0 95 Q60 75 120 80 T240 50 T360 35" fill="none" stroke="#10B981" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="4 2"/>
      <circle cx="120" cy="60" r="4" fill="#0D99FF" stroke="#FFFFFF" stroke-width="2"/>
      <circle cx="240" cy="30" r="4" fill="#0D99FF" stroke="#FFFFFF" stroke-width="2"/>
      <circle cx="360" cy="15" r="4" fill="#0D99FF" stroke="#FFFFFF" stroke-width="2"/>
      <circle cx="240" cy="50" r="3.5" fill="#10B981" stroke="#FFFFFF" stroke-width="1.5"/>
      <circle cx="360" cy="35" r="3.5" fill="#10B981" stroke="#FFFFFF" stroke-width="1.5"/>
    </svg>
  </div>
  <div style="display: flex; justify-content: space-between; font-size: 11px; font-family: ui-monospace, monospace; color: #94a3b8; border-top: 1px solid #f1f5f9; padding-top: 8px;">
    <span>00:00</span><span>04:00</span><span>08:00</span><span>12:00</span><span>16:00</span><span>20:00</span><span>24:00</span>
  </div>
</div>
`,
  },
  {
    id: 'funnel-conversion',
    name: '全链路转化漏斗',
    category: '漏斗转化',
    description: '多层级业务留存比例与漏斗转化进度条',
    previewSvg: `<svg viewBox="0 0 100 50" width="100%" height="100%" style="width: 100%; height: 100%; display: block;"><rect x="10" y="8" width="80" height="6" rx="3" fill="#3B82F6"/><rect x="20" y="18" width="60" height="6" rx="3" fill="#6366F1"/><rect x="30" y="28" width="40" height="6" rx="3" fill="#8B5CF6"/><rect x="40" y="38" width="20" height="6" rx="3" fill="#10B981"/></svg>`,
    width: 380,
    height: 220,
    getComponentHtml: () => `
<div class="wf-funnel-card" style="width: 100%; height: 100%; box-sizing: border-box; min-width: 0; min-height: 0; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05); padding: 16px; display: flex; flex-direction: column; justify-content: space-between; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <div style="font-size: 15px; font-weight: 700; color: #0f172a;">全链路销售转化漏斗</div>
    <div style="font-size: 11px; font-weight: 700; background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; padding: 3px 8px; border-radius: 6px;">总留存 4.2%</div>
  </div>
  <div style="flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 8px; margin: 8px 0; min-height: 0;">
    <div>
      <div style="display: flex; justify-content: space-between; font-size: 11px; color: #64748b; font-weight: 600; margin-bottom: 3px;"><span>访问浏览</span><span>120,000 (100%)</span></div>
      <div style="width: 100%; background: #f1f5f9; border-radius: 9999px; height: 10px; overflow: hidden;"><div style="background: #3B82F6; height: 100%; border-radius: 9999px; width: 100%;"></div></div>
    </div>
    <div>
      <div style="display: flex; justify-content: space-between; font-size: 11px; color: #64748b; font-weight: 600; margin-bottom: 3px;"><span>加入购物车</span><span>38,400 (32%)</span></div>
      <div style="width: 100%; background: #f1f5f9; border-radius: 9999px; height: 10px; overflow: hidden;"><div style="background: #6366F1; height: 100%; border-radius: 9999px; width: 32%;"></div></div>
    </div>
    <div>
      <div style="display: flex; justify-content: space-between; font-size: 11px; color: #64748b; font-weight: 600; margin-bottom: 3px;"><span>提交订单</span><span>14,400 (12%)</span></div>
      <div style="width: 100%; background: #f1f5f9; border-radius: 9999px; height: 10px; overflow: hidden;"><div style="background: #8B5CF6; height: 100%; border-radius: 9999px; width: 12%;"></div></div>
    </div>
    <div>
      <div style="display: flex; justify-content: space-between; font-size: 11px; color: #64748b; font-weight: 600; margin-bottom: 3px;"><span>支付成功</span><span>5,040 (4.2%)</span></div>
      <div style="width: 100%; background: #f1f5f9; border-radius: 9999px; height: 10px; overflow: hidden;"><div style="background: #10B981; height: 100%; border-radius: 9999px; width: 4.2%;"></div></div>
    </div>
  </div>
</div>
`,
  },
]

// ==========================================
// 插件 7: 品牌与支付矢量库 (Brand & Payment Logos)
// ==========================================
export interface BrandLogoItem {
  id: string
  name: string
  category: 'payment' | 'platform' | 'tech'
  svg: string
}

export const PRESET_BRAND_LOGOS: BrandLogoItem[] = [
  {
    id: "alipay",
    name: "支付宝",
    category: "payment",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="#1677ff" d="M19.695 15.07c3.426 1.158 4.203 1.22 4.203 1.22V3.846c0-2.124-1.705-3.845-3.81-3.845H3.914C1.808.001.102 1.722.102 3.846v16.31c0 2.123 1.706 3.845 3.813 3.845h16.173c2.105 0 3.81-1.722 3.81-3.845v-.157s-6.19-2.602-9.315-4.119c-2.096 2.602-4.8 4.181-7.607 4.181c-4.75 0-6.361-4.19-4.112-6.949c.49-.602 1.324-1.175 2.617-1.497c2.025-.502 5.247.313 8.266 1.317a16.8 16.8 0 0 0 1.341-3.302H5.781v-.952h4.799V6.975H4.77v-.953h5.81V3.591s0-.409.411-.409h2.347v2.84h5.744v.951h-5.744v1.704h4.69a19.5 19.5 0 0 1-1.986 5.06c1.424.52 2.702 1.011 3.654 1.333m-13.81-2.032c-.596.06-1.71.325-2.321.869c-1.83 1.608-.735 4.55 2.968 4.55c2.151 0 4.301-1.388 5.99-3.61c-2.403-1.182-4.438-2.028-6.637-1.809"/></svg>`,
  },
  {
    id: "wechat-pay",
    name: "微信支付",
    category: "payment",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="#07c160" d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213c0 .163.13.295.29.295a.33.33 0 0 0 .167-.054l1.903-1.114a.86.86 0 0 1 .717-.098a10.2 10.2 0 0 0 2.837.403c.276 0 .543-.027.811-.05c-.857-2.578.157-4.972 1.932-6.446c1.703-1.415 3.882-1.98 5.853-1.838c-.576-3.583-4.196-6.348-8.596-6.348M5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178a1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18m5.34 2.867c-1.797-.052-3.746.512-5.28 1.786c-1.72 1.428-2.687 3.72-1.78 6.22c.942 2.453 3.666 4.229 6.884 4.229c.826 0 1.622-.12 2.361-.336a.72.72 0 0 1 .598.082l1.584.926a.3.3 0 0 0 .14.047c.134 0 .24-.111.24-.247c0-.06-.023-.12-.038-.177l-.327-1.233a.6.6 0 0 1-.023-.156a.49.49 0 0 1 .201-.398C23.024 18.48 24 16.82 24 14.98c0-3.21-2.931-5.837-6.656-6.088V8.89c-.135-.01-.27-.027-.407-.03zm-2.53 3.274c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983a.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982zm4.844 0c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983a.976.976 0 0 1-.969-.983c0-.542.434-.982.969-.982"/></svg>`,
  },
  {
    id: "unionpay",
    name: "中国银联",
    category: "payment",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 160"><path fill="#e21836" d="M50.042 0h63.778c8.903 0 14.44 7.257 12.364 16.19L96.49 143.675c-2.095 8.902-11.01 16.165-19.918 16.165H12.799c-8.89 0-14.44-7.263-12.363-16.165L30.142 16.19C32.219 7.257 41.128 0 50.042 0"/><path fill="#00447c" d="M108.513 0h73.344c8.901 0 4.888 7.257 2.794 16.19l-29.69 127.485c-2.082 8.902-1.433 16.165-10.353 16.165H71.265c-8.92 0-14.44-7.263-12.345-16.165L88.607 16.19C90.715 7.257 99.606 0 108.513 0"/><path fill="#007b84" d="M178.948 0h63.778c8.916 0 14.453 7.257 12.36 16.19l-29.69 127.485c-2.095 8.902-11.015 16.165-19.93 16.165h-63.748c-8.92 0-14.453-7.263-12.363-16.165L159.048 16.19C161.125 7.257 170.028 0 178.948 0"/><path fill="#fefefe" d="M66.698 40.856c-6.558.067-8.495 0-9.114-.146c-.238 1.13-4.664 21.546-4.676 21.563c-.953 4.13-1.646 7.075-4.002 8.976c-1.336 1.105-2.897 1.639-4.707 1.639c-2.908 0-4.603-1.445-4.887-4.184l-.055-.94s.886-5.533.886-5.564c0 0 4.645-18.605 5.477-21.064c.044-.14.056-.213.067-.28c-9.041.08-10.644 0-10.754-.146c-.061.2-.285 1.354-.285 1.354l-4.743 20.97l-.407 1.778l-.788 5.817c0 1.725.339 3.134 1.013 4.325c2.161 3.776 8.325 4.342 11.812 4.342c4.492 0 8.707-.955 11.555-2.698c4.943-2.92 6.237-7.486 7.39-11.544l.535-2.081S65.8 43.65 66.613 41.136c.03-.14.043-.213.085-.28m16.28 15.588c-1.153 0-3.26.28-5.154 1.208c-.687.353-1.337.76-2.023 1.166l.619-2.234l-.339-.376c-4.015.813-4.914.922-8.623 1.444l-.31.207c-.431 3.57-.814 6.255-2.41 13.274a832 832 0 0 1-1.872 7.78l.17.327c3.802-.2 4.955-.2 8.259-.146l.267-.29c.42-2.15.474-2.654 1.404-7.009c.437-2.064 1.348-6.6 1.797-8.215c.825-.383 1.64-.759 2.417-.759c1.852 0 1.626 1.615 1.555 2.259c-.08 1.08-.754 4.609-1.446 7.639l-.462 1.955c-.321 1.445-.674 2.848-.996 4.28l.14.286c3.746-.2 4.889-.2 8.088-.146l.376-.29c.579-3.358.748-4.257 1.774-9.146l.516-2.246c1.003-4.397 1.507-6.626.748-8.442c-.802-2.035-2.727-2.526-4.494-2.526m18.188 4.603c-1.992.383-3.262.637-4.524.802c-1.251.2-2.472.383-4.396.65l-.153.138l-.14.11c-.2 1.434-.34 2.673-.606 4.13c-.225 1.506-.572 3.218-1.136 5.677c-.437 1.882-.662 2.538-.911 3.2c-.243.663-.51 1.307-1.001 3.158l.115.171l.096.157c1.799-.085 2.976-.146 4.185-.157c1.208-.043 2.46 0 4.396.01l.17-.137l.182-.153c.28-1.67.321-2.119.492-2.933c.17-.874.462-2.083 1.179-5.314c.339-1.517.716-3.03 1.068-4.578c.365-1.542.747-3.06 1.111-4.578l-.054-.183zm.042-6.206c-1.81-1.068-4.985-.729-7.123.746c-2.131 1.446-2.374 3.498-.57 4.58c1.778 1.038 4.966.729 7.085-.759c2.127-1.477 2.393-3.51.608-4.567m10.943 24.734c3.661 0 7.414-1.01 10.239-4.003c2.173-2.428 3.17-6.041 3.515-7.529c1.123-4.931.248-7.233-.85-8.635c-1.67-2.138-4.621-2.824-7.682-2.824c-1.84 0-6.224.182-9.649 3.34c-2.46 2.277-3.596 5.368-4.281 8.33c-.692 3.02-1.488 8.454 3.51 10.477c1.542.662 3.765.844 5.198.844m-.286-11.095c.844-3.734 1.841-6.868 4.384-6.868c1.993 0 2.138 2.332 1.252 6.078c-.159.832-.886 3.924-1.87 5.24c-.688.972-1.5 1.561-2.4 1.561c-.267 0-1.857 0-1.882-2.361c-.012-1.166.226-2.357.516-3.65m23.191 10.615l.286-.291c.406-2.15.473-2.655 1.371-7.008c.45-2.065 1.38-6.6 1.816-8.216c.827-.384 1.628-.76 2.43-.76c1.839 0 1.615 1.615 1.542 2.259c-.067 1.082-.742 4.609-1.446 7.639l-.437 1.955c-.334 1.446-.698 2.848-1.02 4.282l.14.286c3.76-.2 4.858-.2 8.076-.146l.389-.291c.564-3.36.717-4.258 1.774-9.146l.503-2.247c1.008-4.397 1.518-6.624.771-8.44c-.825-2.035-2.762-2.526-4.505-2.526c-1.155 0-3.273.278-5.156 1.208c-.673.352-1.348.758-2.01 1.166l.577-2.234l-.31-.378c-4.014.814-4.93.923-8.635 1.446l-.285.206c-.449 3.571-.814 6.254-2.41 13.275a884 884 0 0 1-1.871 7.779l.17.328c3.807-.2 4.943-.2 8.24-.146m27.618.145c.236-1.154 1.64-7.99 1.652-7.99c0 0 1.195-5.017 1.269-5.199c0 0 .376-.522.752-.729h.553c5.222 0 11.119 0 15.74-3.4c3.145-2.332 5.295-5.775 6.254-9.96c.249-1.026.432-2.246.432-3.466c0-1.603-.321-3.189-1.251-4.428c-2.357-3.297-7.05-3.358-12.468-3.383l-2.67.025c-6.935.086-9.716.061-10.859-.079c-.096.505-.278 1.404-.278 1.404s-2.484 11.513-2.484 11.531l-6.225 25.632c6.055-.073 8.538-.073 9.583.042m4.603-20.452s2.64-11.49 2.628-11.446l.086-.59l.037-.448l1.056.108s5.447.468 5.574.48c2.15.832 3.036 2.976 2.418 5.774c-.565 2.558-2.223 4.708-4.355 5.746c-1.755.88-3.905.953-6.12.953h-1.433zm16.444 9.905c-.698 2.975-1.5 8.41 3.473 10.347c1.586.674 3.007.875 4.45.802c1.526-.082 2.939-.847 4.248-1.948l-.355 1.359l.226.29c3.577-.15 4.687-.15 8.562-.12l.352-.268c.565-3.327 1.099-6.557 2.57-12.922c.716-3.048 1.431-6.067 2.166-9.103l-.115-.334c-4.001.741-5.07.9-8.92 1.445l-.292.238c-.039.31-.08.607-.117.904c-.598-.967-1.466-1.793-2.804-2.307c-1.712-.674-5.732.194-9.187 3.34c-2.429 2.247-3.595 5.325-4.257 8.277m8.404.182c.857-3.668 1.84-6.77 4.39-6.77c1.612 0 2.461 1.487 2.288 4.024a129 129 0 0 1-.46 2.054c-.255 1.09-.531 2.17-.8 3.252c-.274.74-.593 1.438-.942 1.903c-.656.93-2.217 1.506-3.116 1.506c-.255 0-1.828 0-1.882-2.32c-.013-1.154.225-2.344.522-3.65m43.886-12.109l-.31-.353c-3.959.802-4.676.93-8.313 1.421l-.268.267c-.012.044-.023.11-.042.171l-.012-.06c-2.708 6.248-2.629 4.9-4.833 9.818c-.013-.224-.013-.363-.025-.601l-.552-10.663l-.346-.353c-4.148.802-4.246.93-8.076 1.421l-.299.267c-.042.128-.042.268-.067.42l.025.055c.479 2.446.364 1.9.844 5.762c.224 1.895.523 3.801.746 5.673c.378 3.132.59 4.674 1.051 9.455c-2.586 4.268-3.199 5.884-5.69 9.63l.018.037l-1.754 2.774c-.2.292-.382.492-.637.578c-.28.138-.644.163-1.149.163h-.972l-1.444 4.804l4.955.085c2.908-.012 4.736-1.372 5.72-3.2l3.116-5.339h-.05l.328-.376c2.096-4.511 18.036-31.856 18.036-31.856m-52.29 63.088h-2.102l7.78-25.73h2.58l.82-2.65l.078 2.947c-.096 1.822 1.337 3.437 5.102 3.17h4.355l1.498-4.954h-1.638c-.942 0-1.38-.238-1.325-.748l-.079-2.999h-8.063v.016c-2.607.054-10.393.25-11.969.67c-1.907.49-3.917 1.937-3.917 1.937l.79-2.654h-7.543l-1.572 5.265l-7.883 26.123h-1.53l-1.5 4.919h15.023l-.503 1.64h7.402l.492-1.64h2.076zm-6.164-20.502c-1.208.334-3.455 1.348-3.455 1.348l1.999-6.576h5.992l-1.445 4.791s-1.852.11-3.09.437m.115 9.394s-1.882.237-3.121.516c-1.22.37-3.509 1.536-3.509 1.536l2.065-6.843h6.023zm-3.358 11.168h-6.011l1.743-5.775h5.992zm14.477-15.959h8.665l-1.245 4.033h-8.78l-1.318 4.408h7.683l-5.818 8.191c-.407.602-.772.815-1.178.984c-.407.207-.942.45-1.56.45h-2.132l-1.464 4.828h5.574c2.898 0 4.61-1.318 5.873-3.048l3.99-5.46l.856 5.544c.182 1.038.928 1.646 1.433 1.882c.558.28 1.135.76 1.95.832c.873.037 1.504.067 1.924.067h2.74l1.644-5.404h-1.08c-.62 0-1.689-.104-1.87-.298c-.183-.237-.183-.6-.28-1.154l-.87-5.556h-3.558l1.561-1.858h8.763l1.348-4.408h-8.113l1.263-4.033h8.089l1.5-4.973h-24.114zm-73.185 17.082l2.022-6.728h8.313l1.518-5.004h-8.32l1.27-4.141h8.13l1.507-4.846h-20.343l-1.475 4.846h4.622l-1.233 4.14h-4.634l-1.536 5.09h4.62l-2.696 8.902c-.363 1.178.171 1.627.51 2.175c.347.533.698.886 1.488 1.086c.815.182 1.373.291 2.131.291h9.371l1.67-5.544l-4.154.571c-.802 0-3.024-.096-2.781-.838m.953-32.208l-2.106 3.807c-.45.831-.857 1.348-1.222 1.585c-.322.2-.96.285-1.883.285h-1.099l-1.469 4.87h3.65c1.756 0 3.103-.643 3.747-.965c.692-.37.874-.159 1.408-.675l1.233-1.068h11.398l1.513-5.07h-8.344l1.457-2.77zm16.827 32.306c-.194-.28-.054-.773.243-1.799l3.115-10.311h11.083c1.615-.023 2.78-.042 3.54-.096c.814-.086 1.7-.377 2.665-.899c.997-.547 1.507-1.124 1.937-1.786c.48-.66 1.252-2.107 1.914-4.336l3.916-13.049l-11.5.067s-3.542.522-5.101 1.1c-1.573.643-3.821 2.44-3.821 2.44l1.038-3.577h-7.104l-9.946 32.986c-.353 1.28-.59 2.21-.644 2.769c-.018.601.759 1.197 1.263 1.646c.595.449 1.475.376 2.319.449c.888.067 2.15.109 3.893.109h5.46l1.675-5.659l-4.887.462c-.523 0-.9-.28-1.058-.516m5.368-19.074h11.64l-.74 2.319c-.103.054-.352-.115-1.537.025h-10.08zm2.332-7.78h11.739l-.845 2.794s-5.532-.054-6.418.109c-3.9.675-6.177 2.758-6.177 2.758zm8.83 17.866c-.097.347-.25.558-.462.717c-.237.152-.62.206-1.19.206h-1.658l.098-2.824h-6.9l-.28 13.809c-.01.996.086 1.573.815 2.035c.73.576 2.976.65 6 .65h4.323l1.56-5.17l-3.763.207l-1.252.073c-.17-.073-.334-.14-.516-.321c-.158-.157-.426-.061-.382-1.057l.03-3.54l3.946-.163c2.132 0 3.043-.694 3.82-1.354c.741-.633.984-1.36 1.264-2.345l.662-3.134h-5.424z"/></svg>`,
  },
  {
    id: "apple-pay",
    name: "Apple Pay",
    category: "payment",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 512 211"><path d="M93.552 27.103c-6 7.1-15.602 12.702-25.203 11.901c-1.2-9.6 3.5-19.802 9.001-26.103C83.35 5.601 93.852.4 102.353 0c1 10.001-2.9 19.802-8.8 27.103m8.701 13.802c-13.902-.8-25.803 7.9-32.404 7.9c-6.7 0-16.802-7.5-27.803-7.3c-14.301.2-27.603 8.3-34.904 21.202c-15.002 25.803-3.9 64.008 10.601 85.01c7.101 10.401 15.602 21.802 26.803 21.402c10.602-.4 14.802-6.9 27.604-6.9c12.901 0 16.602 6.9 27.803 6.7c11.601-.2 18.902-10.4 26.003-20.802c8.1-11.801 11.401-23.303 11.601-23.903c-.2-.2-22.402-8.7-22.602-34.304c-.2-21.402 17.502-31.603 18.302-32.203c-10.002-14.802-25.603-16.402-31.004-16.802m80.31-29.004V167.82h24.202v-53.306h33.504c30.603 0 52.106-21.002 52.106-51.406c0-30.403-21.103-51.206-51.306-51.206zm24.202 20.403h27.903c21.003 0 33.004 11.201 33.004 30.903s-12.001 31.004-33.104 31.004h-27.803zM336.58 169.019c15.202 0 29.303-7.7 35.704-19.902h.5v18.702h22.403V90.21c0-22.502-18.002-37.004-45.706-37.004c-25.703 0-44.705 14.702-45.405 34.904h21.803c1.8-9.601 10.7-15.902 22.902-15.902c14.802 0 23.103 6.901 23.103 19.603v8.6l-30.204 1.8c-28.103 1.7-43.304 13.202-43.304 33.205c0 20.202 15.701 33.603 38.204 33.603m6.5-18.502c-12.9 0-21.102-6.2-21.102-15.702c0-9.8 7.901-15.501 23.003-16.401l26.903-1.7v8.8c0 14.602-12.401 25.003-28.803 25.003m82.01 59.707c23.603 0 34.704-9 44.405-36.304L512 54.706h-24.603l-28.503 92.11h-.5l-28.503-92.11h-25.303l41.004 113.513l-2.2 6.901c-3.7 11.701-9.701 16.202-20.402 16.202c-1.9 0-5.6-.2-7.101-.4v18.702c1.4.4 7.4.6 9.201.6"/></svg>`,
  },
  {
    id: "visa",
    name: "Visa",
    category: "payment",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 83"><defs><linearGradient id="SVGSdd43daQ" x1="45.974%" x2="54.877%" y1="-2.006%" y2="100%"><stop offset="0%" stop-color="#222357"/><stop offset="100%" stop-color="#254aa5"/></linearGradient></defs><path fill="url(#SVGSdd43daQ)" d="M132.397 56.24c-.146-11.516 10.263-17.942 18.104-21.763c8.056-3.92 10.762-6.434 10.73-9.94c-.06-5.365-6.426-7.733-12.383-7.825c-10.393-.161-16.436 2.806-21.24 5.05l-3.744-17.519c4.82-2.221 13.745-4.158 23-4.243c21.725 0 35.938 10.724 36.015 27.351c.085 21.102-29.188 22.27-28.988 31.702c.069 2.86 2.798 5.912 8.778 6.688c2.96.392 11.131.692 20.395-3.574l3.636 16.95c-4.982 1.814-11.385 3.551-19.357 3.551c-20.448 0-34.83-10.87-34.946-26.428m89.241 24.968c-3.967 0-7.31-2.314-8.802-5.865L181.803 1.245h21.709l4.32 11.939h26.528l2.506-11.939H256l-16.697 79.963zm3.037-21.601l6.265-30.027h-17.158zm-118.599 21.6L88.964 1.246h20.687l17.104 79.963zm-30.603 0L53.941 26.782l-8.71 46.277c-1.022 5.166-5.058 8.149-9.54 8.149H.493L0 78.886c7.226-1.568 15.436-4.097 20.41-6.803c3.044-1.653 3.912-3.098 4.912-7.026L41.819 1.245H63.68l33.516 79.963z" transform="matrix(1 0 0 -1 0 82.668)"/></svg>`,
  },
  {
    id: "mastercard",
    name: "Mastercard",
    category: "payment",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 199"><path d="M46.54 198.011V184.84c0-5.05-3.074-8.342-8.343-8.342c-2.634 0-5.488.878-7.464 3.732c-1.536-2.415-3.731-3.732-7.024-3.732c-2.196 0-4.39.658-6.147 3.073v-2.634h-4.61v21.074h4.61v-11.635c0-3.731 1.976-5.488 5.05-5.488c3.072 0 4.61 1.976 4.61 5.488v11.635h4.61v-11.635c0-3.731 2.194-5.488 5.048-5.488c3.074 0 4.61 1.976 4.61 5.488v11.635zm68.271-21.074h-7.463v-6.366h-4.61v6.366h-4.171v4.17h4.17v9.66c0 4.83 1.976 7.683 7.245 7.683c1.976 0 4.17-.658 5.708-1.536l-1.318-3.952c-1.317.878-2.853 1.098-3.951 1.098c-2.195 0-3.073-1.317-3.073-3.513v-9.44h7.463zm39.076-.44c-2.634 0-4.39 1.318-5.488 3.074v-2.634h-4.61v21.074h4.61v-11.854c0-3.512 1.536-5.488 4.39-5.488c.878 0 1.976.22 2.854.439l1.317-4.39c-.878-.22-2.195-.22-3.073-.22m-59.052 2.196c-2.196-1.537-5.269-2.195-8.562-2.195c-5.268 0-8.78 2.634-8.78 6.805c0 3.513 2.634 5.488 7.244 6.147l2.195.22c2.415.438 3.732 1.097 3.732 2.195c0 1.536-1.756 2.634-4.83 2.634s-5.488-1.098-7.025-2.195l-2.195 3.512c2.415 1.756 5.708 2.634 9 2.634c6.147 0 9.66-2.853 9.66-6.805c0-3.732-2.854-5.708-7.245-6.366l-2.195-.22c-1.976-.22-3.512-.658-3.512-1.975c0-1.537 1.536-2.415 3.951-2.415c2.635 0 5.269 1.097 6.586 1.756zm122.495-2.195c-2.635 0-4.391 1.317-5.489 3.073v-2.634h-4.61v21.074h4.61v-11.854c0-3.512 1.537-5.488 4.39-5.488c.879 0 1.977.22 2.855.439l1.317-4.39c-.878-.22-2.195-.22-3.073-.22m-58.833 10.976c0 6.366 4.39 10.976 11.196 10.976c3.073 0 5.268-.658 7.463-2.414l-2.195-3.732c-1.756 1.317-3.512 1.975-5.488 1.975c-3.732 0-6.366-2.634-6.366-6.805c0-3.951 2.634-6.586 6.366-6.805c1.976 0 3.732.658 5.488 1.976l2.195-3.732c-2.195-1.757-4.39-2.415-7.463-2.415c-6.806 0-11.196 4.61-11.196 10.976m42.588 0v-10.537h-4.61v2.634c-1.537-1.975-3.732-3.073-6.586-3.073c-5.927 0-10.537 4.61-10.537 10.976s4.61 10.976 10.537 10.976c3.073 0 5.269-1.097 6.586-3.073v2.634h4.61zm-16.904 0c0-3.732 2.415-6.805 6.366-6.805c3.732 0 6.367 2.854 6.367 6.805c0 3.732-2.635 6.805-6.367 6.805c-3.951-.22-6.366-3.073-6.366-6.805m-55.1-10.976c-6.147 0-10.538 4.39-10.538 10.976s4.39 10.976 10.757 10.976c3.073 0 6.147-.878 8.562-2.853l-2.196-3.293c-1.756 1.317-3.951 2.195-6.146 2.195c-2.854 0-5.708-1.317-6.367-5.05h15.587v-1.755c.22-6.806-3.732-11.196-9.66-11.196m0 3.951c2.853 0 4.83 1.757 5.268 5.05h-10.976c.439-2.854 2.415-5.05 5.708-5.05m114.372 7.025v-18.879h-4.61v10.976c-1.537-1.975-3.732-3.073-6.586-3.073c-5.927 0-10.537 4.61-10.537 10.976s4.61 10.976 10.537 10.976c3.074 0 5.269-1.097 6.586-3.073v2.634h4.61zm-16.903 0c0-3.732 2.414-6.805 6.366-6.805c3.732 0 6.366 2.854 6.366 6.805c0 3.732-2.634 6.805-6.366 6.805c-3.952-.22-6.366-3.073-6.366-6.805m-154.107 0v-10.537h-4.61v2.634c-1.537-1.975-3.732-3.073-6.586-3.073c-5.927 0-10.537 4.61-10.537 10.976s4.61 10.976 10.537 10.976c3.074 0 5.269-1.097 6.586-3.073v2.634h4.61zm-17.123 0c0-3.732 2.415-6.805 6.366-6.805c3.732 0 6.367 2.854 6.367 6.805c0 3.732-2.635 6.805-6.367 6.805c-3.951-.22-6.366-3.073-6.366-6.805"/><path fill="#ff5f00" d="M93.298 16.903h69.15v124.251h-69.15z"/><path fill="#eb001b" d="M97.689 79.029c0-25.245 11.854-47.637 30.074-62.126C114.373 6.366 97.47 0 79.03 0C35.343 0 0 35.343 0 79.029s35.343 79.029 79.029 79.029c18.44 0 35.343-6.366 48.734-16.904c-18.22-14.269-30.074-36.88-30.074-62.125"/><path fill="#f79e1b" d="M255.746 79.029c0 43.685-35.343 79.029-79.029 79.029c-18.44 0-35.343-6.366-48.734-16.904c18.44-14.488 30.075-36.88 30.075-62.125s-11.855-47.637-30.075-62.126C141.373 6.366 158.277 0 176.717 0c43.686 0 79.03 35.563 79.03 79.029"/></svg>`,
  },
  {
    id: "paypal",
    name: "PayPal",
    category: "payment",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 302"><path fill="#27346a" d="M217.168 23.507C203.234 7.625 178.046.816 145.823.816h-93.52A13.39 13.39 0 0 0 39.076 12.11L.136 259.077c-.774 4.87 2.997 9.28 7.933 9.28h57.736l14.5-91.971l-.45 2.88c1.033-6.501 6.593-11.296 13.177-11.296h27.436c53.898 0 96.101-21.892 108.429-85.221c.366-1.873.683-3.696.957-5.477q-2.334-1.236 0 0c3.671-23.407-.025-39.34-12.686-53.765"/><path fill="#27346a" d="M102.397 68.84a11.7 11.7 0 0 1 5.053-1.14h73.318c8.682 0 16.78.565 24.18 1.756a102 102 0 0 1 6.177 1.182a90 90 0 0 1 8.59 2.347c3.638 1.215 7.026 2.63 10.14 4.287c3.67-23.416-.026-39.34-12.687-53.765C203.226 7.625 178.046.816 145.823.816H52.295C45.71.816 40.108 5.61 39.076 12.11L.136 259.068c-.774 4.878 2.997 9.282 7.925 9.282h57.744L95.888 77.58a11.72 11.72 0 0 1 6.509-8.74"/><path fill="#2790c3" d="M228.897 82.749c-12.328 63.32-54.53 85.221-108.429 85.221H93.024c-6.584 0-12.145 4.795-13.168 11.296L61.817 293.621c-.674 4.262 2.622 8.124 6.934 8.124h48.67a11.71 11.71 0 0 0 11.563-9.88l.474-2.48l9.173-58.136l.591-3.213a11.71 11.71 0 0 1 11.562-9.88h7.284c47.147 0 84.064-19.154 94.852-74.55c4.503-23.15 2.173-42.478-9.739-56.054c-3.613-4.112-8.1-7.508-13.327-10.28c-.283 1.79-.59 3.604-.957 5.477"/><path fill="#1f264f" d="M216.952 72.128a90 90 0 0 0-5.818-1.49a110 110 0 0 0-6.177-1.174c-7.408-1.199-15.5-1.765-24.19-1.765h-73.309a11.6 11.6 0 0 0-5.053 1.149a11.68 11.68 0 0 0-6.51 8.74l-15.582 98.798l-.45 2.88c1.025-6.501 6.585-11.296 13.17-11.296h27.444c53.898 0 96.1-21.892 108.428-85.221c.367-1.873.675-3.688.958-5.477q-4.682-2.47-10.14-4.279a83 83 0 0 0-2.77-.865"/></svg>`,
  },
  {
    id: "figma",
    name: "Figma",
    category: "platform",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 384"><path fill="#0acf83" d="M64 384c35.328 0 64-28.672 64-64v-64H64c-35.328 0-64 28.672-64 64s28.672 64 64 64"/><path fill="#a259ff" d="M0 192c0-35.328 28.672-64 64-64h64v128H64c-35.328 0-64-28.672-64-64"/><path fill="#f24e1e" d="M0 64C0 28.672 28.672 0 64 0h64v128H64C28.672 128 0 99.328 0 64"/><path fill="#ff7262" d="M128 0h64c35.328 0 64 28.672 64 64s-28.672 64-64 64h-64z"/><path fill="#1abcfe" d="M256 192c0 35.328-28.672 64-64 64s-64-28.672-64-64s28.672-64 64-64s64 28.672 64 64"/></svg>`,
  },
  {
    id: "github",
    name: "GitHub",
    category: "platform",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="currentColor" d="M12 .297c-6.63 0-12 5.373-12 12c0 5.303 3.438 9.8 8.205 11.385c.6.113.82-.258.82-.577c0-.285-.01-1.04-.015-2.04c-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729c1.205.084 1.838 1.236 1.838 1.236c1.07 1.835 2.809 1.305 3.495.998c.108-.776.417-1.305.76-1.605c-2.665-.3-5.466-1.332-5.466-5.93c0-1.31.465-2.38 1.235-3.22c-.135-.303-.54-1.523.105-3.176c0 0 1.005-.322 3.3 1.23c.96-.267 1.98-.399 3-.405c1.02.006 2.04.138 3 .405c2.28-1.552 3.285-1.23 3.285-1.23c.645 1.653.24 2.873.12 3.176c.765.84 1.23 1.91 1.23 3.22c0 4.61-2.805 5.625-5.475 5.92c.42.36.81 1.096.81 2.22c0 1.606-.015 2.896-.015 3.286c0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/></svg>`,
  },
  {
    id: "bilibili",
    name: "哔哩哔哩",
    category: "platform",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="#00aeec" d="M17.813 4.653h.854q2.266.08 3.773 1.574Q23.946 7.72 24 9.987v7.36q-.054 2.266-1.56 3.773c-1.506 1.507-2.262 1.524-3.773 1.56H5.333q-2.266-.054-3.773-1.56C.053 19.614.036 18.858 0 17.347v-7.36q.054-2.267 1.56-3.76t3.773-1.574h.774l-1.174-1.12a1.23 1.23 0 0 1-.373-.906q0-.534.373-.907l.027-.027q.4-.373.92-.373t.92.373L9.653 4.44q.107.106.187.213h4.267a.8.8 0 0 1 .16-.213l2.853-2.747q.4-.373.92-.373c.347 0 .662.151.929.4s.391.551.391.907q0 .532-.373.906zM5.333 7.24q-1.12.027-1.88.773q-.76.748-.786 1.894v7.52q.026 1.146.786 1.893t1.88.773h13.334q1.12-.026 1.88-.773t.786-1.893v-7.52q-.026-1.147-.786-1.894t-1.88-.773zM8 11.107q.56 0 .933.373q.375.374.4.96v1.173q-.025.586-.4.96q-.373.375-.933.374c-.56-.001-.684-.125-.933-.374q-.375-.373-.4-.96V12.44q0-.56.386-.947q.387-.386.947-.386m8 0q.56 0 .933.373q.375.374.4.96v1.173q-.025.586-.4.96q-.373.375-.933.374c-.56-.001-.684-.125-.933-.374q-.375-.373-.4-.96V12.44q.025-.586.4-.96q.373-.373.933-.373"/></svg>`,
  },
  {
    id: "bytedance",
    name: "字节跳动",
    category: "platform",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="#3c8cff" d="M19.877 1.469L24 2.532v18.942l-4.123 1.056zM6.53 10.897l4.115 1.064v8.978L6.53 22.003zM0 2.572l4.115 1.064v16.736L0 21.428zm17.455 5.62V19.3l-4.122-1.065V9.257z"/></svg>`,
  },
  {
    id: "feishu",
    name: "飞书",
    category: "platform",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 48 48"><g fill-rule="evenodd" clip-rule="evenodd"><path d="M41.0716 5.99409L3.31071 16.5187L12.3856 25.8126L20.7998 25.9594L30.4827 16.5187C30.2266 15.9943 30.0985 15.5552 30.0985 15.2013C30.0985 14.4074 30.4104 13.7786 30.8947 13.333C31.7241 12.57 32.7222 12.4558 33.8889 12.9905L41.0716 5.99409Z"/><path d="M42.1021 6.72842L31.5775 44.4893L22.2836 35.4144L22.1367 27.0002L31.5115 17.4816C32.0195 17.8454 32.5743 18.0105 33.1759 17.9769C34.0784 17.9264 34.6614 17.3813 34.9349 17.0602C35.2083 16.7392 35.5293 16.2051 35.5025 15.4113C35.4847 14.8821 35.3109 14.3941 34.9812 13.9472L42.1021 6.72842Z"/></g></svg>`,
  },
  {
    id: "vue",
    name: "Vue.js",
    category: "tech",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 221"><path fill="#41b883" d="M204.8 0H256L128 220.8L0 0h97.92L128 51.2L157.44 0z"/><path fill="#41b883" d="m0 0l128 220.8L256 0h-51.2L128 132.48L50.56 0z"/><path fill="#35495e" d="M50.56 0L128 133.12L204.8 0h-47.36L128 51.2L97.92 0z"/></svg>`,
  },
  {
    id: "react",
    name: "React",
    category: "tech",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 228"><path fill="#00d8ff" d="M210.483 73.824a172 172 0 0 0-8.24-2.597c.465-1.9.893-3.777 1.273-5.621c6.238-30.281 2.16-54.676-11.769-62.708c-13.355-7.7-35.196.329-57.254 19.526a171 171 0 0 0-6.375 5.848a156 156 0 0 0-4.241-3.917C100.759 3.829 77.587-4.822 63.673 3.233C50.33 10.957 46.379 33.89 51.995 62.588a171 171 0 0 0 1.892 8.48c-3.28.932-6.445 1.924-9.474 2.98C17.309 83.498 0 98.307 0 113.668c0 15.865 18.582 31.778 46.812 41.427a146 146 0 0 0 6.921 2.165a168 168 0 0 0-2.01 9.138c-5.354 28.2-1.173 50.591 12.134 58.266c13.744 7.926 36.812-.22 59.273-19.855a146 146 0 0 0 5.342-4.923a168 168 0 0 0 6.92 6.314c21.758 18.722 43.246 26.282 56.54 18.586c13.731-7.949 18.194-32.003 12.4-61.268a145 145 0 0 0-1.535-6.842c1.62-.48 3.21-.974 4.76-1.488c29.348-9.723 48.443-25.443 48.443-41.52c0-15.417-17.868-30.326-45.517-39.844m-6.365 70.984q-2.102.694-4.3 1.345c-3.24-10.257-7.612-21.163-12.963-32.432c5.106-11 9.31-21.767 12.459-31.957c2.619.758 5.16 1.557 7.61 2.4c23.69 8.156 38.14 20.213 38.14 29.504c0 9.896-15.606 22.743-40.946 31.14m-10.514 20.834c2.562 12.94 2.927 24.64 1.23 33.787c-1.524 8.219-4.59 13.698-8.382 15.893c-8.067 4.67-25.32-1.4-43.927-17.412a157 157 0 0 1-6.437-5.87c7.214-7.889 14.423-17.06 21.459-27.246c12.376-1.098 24.068-2.894 34.671-5.345q.785 3.162 1.386 6.193M87.276 214.515c-7.882 2.783-14.16 2.863-17.955.675c-8.075-4.657-11.432-22.636-6.853-46.752a157 157 0 0 1 1.869-8.499c10.486 2.32 22.093 3.988 34.498 4.994c7.084 9.967 14.501 19.128 21.976 27.15a135 135 0 0 1-4.877 4.492c-9.933 8.682-19.886 14.842-28.658 17.94M50.35 144.747c-12.483-4.267-22.792-9.812-29.858-15.863c-6.35-5.437-9.555-10.836-9.555-15.216c0-9.322 13.897-21.212 37.076-29.293c2.813-.98 5.757-1.905 8.812-2.773c3.204 10.42 7.406 21.315 12.477 32.332c-5.137 11.18-9.399 22.249-12.634 32.792a135 135 0 0 1-6.318-1.979m12.378-84.26c-4.811-24.587-1.616-43.134 6.425-47.789c8.564-4.958 27.502 2.111 47.463 19.835a144 144 0 0 1 3.841 3.545c-7.438 7.987-14.787 17.08-21.808 26.988c-12.04 1.116-23.565 2.908-34.161 5.309a160 160 0 0 1-1.76-7.887m110.427 27.268a348 348 0 0 0-7.785-12.803c8.168 1.033 15.994 2.404 23.343 4.08c-2.206 7.072-4.956 14.465-8.193 22.045a381 381 0 0 0-7.365-13.322m-45.032-43.861c5.044 5.465 10.096 11.566 15.065 18.186a322 322 0 0 0-30.257-.006c4.974-6.559 10.069-12.652 15.192-18.18M82.802 87.83a323 323 0 0 0-7.227 13.238c-3.184-7.553-5.909-14.98-8.134-22.152c7.304-1.634 15.093-2.97 23.209-3.984a322 322 0 0 0-7.848 12.897m8.081 65.352c-8.385-.936-16.291-2.203-23.593-3.793c2.26-7.3 5.045-14.885 8.298-22.6a321 321 0 0 0 7.257 13.246c2.594 4.48 5.28 8.868 8.038 13.147m37.542 31.03c-5.184-5.592-10.354-11.779-15.403-18.433c4.902.192 9.899.29 14.978.29c5.218 0 10.376-.117 15.453-.343c-4.985 6.774-10.018 12.97-15.028 18.486m52.198-57.817c3.422 7.8 6.306 15.345 8.596 22.52c-7.422 1.694-15.436 3.058-23.88 4.071a382 382 0 0 0 7.859-13.026a347 347 0 0 0 7.425-13.565m-16.898 8.101a359 359 0 0 1-12.281 19.815a329 329 0 0 1-23.444.823c-7.967 0-15.716-.248-23.178-.732a310 310 0 0 1-12.513-19.846h.001a307 307 0 0 1-10.923-20.627a310 310 0 0 1 10.89-20.637l-.001.001a307 307 0 0 1 12.413-19.761c7.613-.576 15.42-.876 23.31-.876H128c7.926 0 15.743.303 23.354.883a329 329 0 0 1 12.335 19.695a359 359 0 0 1 11.036 20.54a330 330 0 0 1-11 20.722m22.56-122.124c8.572 4.944 11.906 24.881 6.52 51.026q-.518 2.504-1.15 5.09c-10.622-2.452-22.155-4.275-34.23-5.408c-7.034-10.017-14.323-19.124-21.64-27.008a161 161 0 0 1 5.888-5.4c18.9-16.447 36.564-22.941 44.612-18.3M128 90.808c12.625 0 22.86 10.235 22.86 22.86s-10.235 22.86-22.86 22.86s-22.86-10.235-22.86-22.86s10.235-22.86 22.86-22.86"/></svg>`,
  },
  {
    id: "docker",
    name: "Docker",
    category: "tech",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 185"><path fill="#2396ed" d="M250.716 70.497c-5.765-4-18.976-5.5-29.304-3.5c-1.2-10-6.725-18.749-16.333-26.499l-5.524-4l-3.844 5.75c-4.803 7.5-7.205 18-6.485 28c.24 3.499 1.441 9.749 5.044 15.249c-3.362 2-10.328 4.5-19.455 4.5H1.155l-.48 2c-1.682 9.999-1.682 41.248 18.014 65.247c14.892 18.249 36.99 27.499 66.053 27.499c62.93 0 109.528-30.25 131.386-84.997c8.647.25 27.142 0 36.51-18.75c.24-.5.72-1.5 2.401-5.249l.961-2zM139.986 0h-26.42v24.999h26.42zm0 29.999h-26.42v24.999h26.42zm-31.225 0h-26.42v24.999h26.42zm-31.225 0H51.115v24.999h26.421zM46.311 59.998H19.89v24.999h26.42zm31.225 0H51.115v24.999h26.421zm31.225 0h-26.42v24.999h26.42zm31.226 0h-26.422v24.999h26.422zm31.225 0H144.79v24.999h26.422z"/></svg>`,
  },
  {
    id: "google",
    name: "Google",
    category: "tech",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 262"><path fill="#4285f4" d="M255.878 133.451c0-10.734-.871-18.567-2.756-26.69H130.55v48.448h71.947c-1.45 12.04-9.283 30.172-26.69 42.356l-.244 1.622l38.755 30.023l2.685.268c24.659-22.774 38.875-56.282 38.875-96.027"/><path fill="#34a853" d="M130.55 261.1c35.248 0 64.839-11.605 86.453-31.622l-41.196-31.913c-11.024 7.688-25.82 13.055-45.257 13.055c-34.523 0-63.824-22.773-74.269-54.25l-1.531.13l-40.298 31.187l-.527 1.465C35.393 231.798 79.49 261.1 130.55 261.1"/><path fill="#fbbc05" d="M56.281 156.37c-2.756-8.123-4.351-16.827-4.351-25.82c0-8.994 1.595-17.697 4.206-25.82l-.073-1.73L15.26 71.312l-1.335.635C5.077 89.644 0 109.517 0 130.55s5.077 40.905 13.925 58.602z"/><path fill="#eb4335" d="M130.55 50.479c24.514 0 41.05 10.589 50.479 19.438l36.844-35.974C195.245 12.91 165.798 0 130.55 0C79.49 0 35.393 29.301 13.925 71.947l42.211 32.783c10.59-31.477 39.891-54.251 74.414-54.251"/></svg>`,
  },
  {
    id: "aws",
    name: "AWS",
    category: "tech",
    svg: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 256 153"><path fill="#252f3e" d="M72.392 55.438c0 3.137.34 5.68.933 7.545a45.4 45.4 0 0 0 2.712 6.103c.424.678.593 1.356.593 1.95c0 .847-.508 1.695-1.61 2.543l-5.34 3.56c-.763.509-1.526.763-2.205.763c-.847 0-1.695-.424-2.543-1.187a26 26 0 0 1-3.051-3.984c-.848-1.44-1.696-3.052-2.628-5.001q-9.919 11.697-24.922 11.698c-7.12 0-12.8-2.035-16.954-6.103c-4.153-4.07-6.272-9.495-6.272-16.276c0-7.205 2.543-13.054 7.714-17.462c5.17-4.408 12.037-6.612 20.768-6.612c2.882 0 5.849.254 8.985.678c3.137.424 6.358 1.102 9.749 1.865V29.33c0-6.443-1.357-10.935-3.985-13.563c-2.712-2.628-7.29-3.9-13.817-3.9c-2.967 0-6.018.34-9.155 1.103s-6.188 1.695-9.155 2.882c-1.356.593-2.373.932-2.967 1.102s-1.017.254-1.356.254c-1.187 0-1.78-.848-1.78-2.628v-4.154c0-1.356.17-2.373.593-2.966c.424-.594 1.187-1.187 2.374-1.78q4.45-2.29 10.68-3.815C33.908.763 38.316.255 42.978.255c10.088 0 17.463 2.288 22.21 6.866c4.662 4.577 7.036 11.528 7.036 20.853v27.464zM37.976 68.323c2.798 0 5.68-.508 8.731-1.526c3.052-1.017 5.765-2.882 8.053-5.425c1.357-1.61 2.374-3.39 2.882-5.425c.509-2.034.848-4.493.848-7.375v-3.56a71 71 0 0 0-7.799-1.441a64 64 0 0 0-7.968-.509c-5.68 0-9.833 1.102-12.63 3.391s-4.154 5.51-4.154 9.748c0 3.984 1.017 6.951 3.136 8.986c2.035 2.119 5.002 3.136 8.901 3.136m68.069 9.155c-1.526 0-2.543-.254-3.221-.848c-.678-.508-1.272-1.695-1.78-3.305L81.124 7.799c-.51-1.696-.764-2.798-.764-3.391c0-1.356.678-2.12 2.035-2.12h8.307c1.61 0 2.713.255 3.306.848c.678.509 1.187 1.696 1.695 3.306l14.241 56.117l13.224-56.117c.424-1.695.933-2.797 1.61-3.306c.679-.508 1.866-.847 3.392-.847h6.781c1.61 0 2.713.254 3.39.847c.679.509 1.272 1.696 1.611 3.306l13.394 56.795L168.01 6.442c.508-1.695 1.102-2.797 1.695-3.306c.678-.508 1.78-.847 3.306-.847h7.883c1.357 0 2.12.678 2.12 2.119c0 .424-.085.848-.17 1.356s-.254 1.187-.593 2.12l-20.43 65.525q-.762 2.544-1.78 3.306c-.678.509-1.78.848-3.22.848h-7.29c-1.611 0-2.713-.254-3.392-.848c-.678-.593-1.271-1.695-1.61-3.39l-13.14-54.676l-13.054 54.59c-.423 1.696-.932 2.798-1.61 3.391c-.678.594-1.865.848-3.39.848zm108.927 2.289c-4.408 0-8.816-.509-13.054-1.526c-4.239-1.017-7.544-2.12-9.748-3.39c-1.357-.764-2.29-1.611-2.628-2.374a6 6 0 0 1-.509-2.374V65.78c0-1.78.678-2.628 1.95-2.628a4.8 4.8 0 0 1 1.526.255c.508.17 1.271.508 2.119.847a46 46 0 0 0 9.324 2.967a51 51 0 0 0 10.088 1.017c5.34 0 9.494-.932 12.376-2.797s4.408-4.577 4.408-8.053c0-2.373-.763-4.323-2.289-5.934s-4.408-3.051-8.561-4.408l-12.292-3.814c-6.188-1.95-10.765-4.832-13.563-8.647c-2.797-3.73-4.238-7.883-4.238-12.291q0-5.34 2.289-9.41c1.525-2.712 3.56-5.085 6.103-6.95c2.543-1.95 5.425-3.391 8.816-4.408c3.39-1.017 6.95-1.441 10.68-1.441c1.865 0 3.815.085 5.68.339c1.95.254 3.73.593 5.51.932c1.695.424 3.306.848 4.832 1.357q2.288.762 3.56 1.525c1.187.679 2.034 1.357 2.543 2.12q.763 1.017.763 2.797v3.984c0 1.78-.678 2.713-1.95 2.713c-.678 0-1.78-.34-3.22-1.018q-7.25-3.306-16.276-3.306c-4.832 0-8.647.763-11.275 2.374c-2.627 1.61-3.984 4.069-3.984 7.544c0 2.374.848 4.408 2.543 6.019s4.832 3.221 9.325 4.662l12.037 3.815c6.103 1.95 10.511 4.662 13.139 8.137s3.9 7.46 3.9 11.868c0 3.645-.764 6.951-2.205 9.833c-1.525 2.882-3.56 5.425-6.188 7.46c-2.628 2.119-5.764 3.645-9.409 4.747c-3.815 1.187-7.799 1.78-12.122 1.78"/><path fill="#f90" d="M230.993 120.964c-27.888 20.599-68.408 31.534-103.247 31.534c-48.827 0-92.821-18.056-126.05-48.064c-2.628-2.373-.255-5.594 2.881-3.73c35.942 20.854 80.276 33.484 126.136 33.484c30.94 0 64.932-6.442 96.212-19.666c4.662-2.12 8.646 3.052 4.068 6.442m11.614-13.224c-3.56-4.577-23.566-2.204-32.636-1.102c-2.713.34-3.137-2.034-.678-3.814c15.936-11.19 42.13-7.968 45.181-4.239c3.052 3.815-.848 30.008-15.767 42.554c-2.288 1.95-4.492.933-3.475-1.61c3.39-8.393 10.935-27.296 7.375-31.789"/></svg>`,
  },
]

// ==========================================
// 插件 8: 动态二维码与条形码生成
// ==========================================
export function generateQrCodeUrl(content: string, size = 200): string {
  const encoded = encodeURIComponent(content || 'https://wireforge.design')
  return `https://api.qrserver.com/v1/create-qr-code/?size=${size}x${size}&margin=10&data=${encoded}`
}

export interface GameBubbleIcon {
  id: string
  name: string
  category: '表情' | '互动' | '战斗' | '资源'
  svg: string
}

export interface GameChatMessage {
  id: string
  role: 'player' | 'npc' | 'system'
  name: string
  text: string
  time: string
  color: string
}

export interface GameItemSlot {
  id: string
  name: string
  rarity: string
  count: number
  bg: string
  border: string
  svg: string
}

function gameSvg(body: string) {
  return `<svg viewBox="0 0 48 48" style="width:100%;height:100%;display:block">${body}</svg>`
}

export const GAME_BUBBLE_ICONS: GameBubbleIcon[] = [
  { id: 'smile', name: '开心', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#FDE68A"/><circle cx="17" cy="20" r="2.5" fill="#92400E"/><circle cx="31" cy="20" r="2.5" fill="#92400E"/><path d="M16 28c2.4 4 13.6 4 16 0" fill="none" stroke="#92400E" stroke-width="2.4" stroke-linecap="round"/>') },
  { id: 'laugh', name: '大笑', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#FDBA74"/><path d="M15 20c1.2-2 3.2-2 4.4 0M29 20c1.2-2 3.2-2 4.4 0" fill="none" stroke="#9A3412" stroke-width="2" stroke-linecap="round"/><path d="M16 27h16a8 8 0 0 1-16 0z" fill="#fff"/><path d="M18 27h12v2H18z" fill="#F87171"/>') },
  { id: 'wow', name: '惊讶', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#FDE68A"/><circle cx="17" cy="20" r="3" fill="#fff"/><circle cx="31" cy="20" r="3" fill="#fff"/><circle cx="17" cy="20" r="1.4" fill="#92400E"/><circle cx="31" cy="20" r="1.4" fill="#92400E"/><ellipse cx="24" cy="31" rx="4" ry="5" fill="#fff"/>') },
  { id: 'heart', name: '爱心', category: '表情', svg: gameSvg('<path fill="#FB7185" d="M24 40s-13-8.2-13-17a7.2 7.2 0 0 1 13-4 7.2 7.2 0 0 1 13 4c0 8.8-13 17-13 17z"/>') },
  { id: 'like', name: '点赞', category: '互动', svg: gameSvg('<path fill="#38BDF8" d="M18 21v16H10a2 2 0 0 1-2-2V23a2 2 0 0 1 2-2h8z"/><path fill="#7DD3FC" d="M18 21l4.2-8.2A3.2 3.2 0 0 1 28 15v6h8.2a3 3 0 0 1 3 3.4l-1.4 9A3 3 0 0 1 34.8 36H18V21z"/>') },
  { id: 'gift', name: '礼物', category: '互动', svg: gameSvg('<rect x="8" y="20" width="32" height="18" rx="3" fill="#F43F5E"/><rect x="6" y="16" width="36" height="8" rx="2" fill="#FB7185"/><rect x="22" y="16" width="4" height="22" fill="#FDE68A"/><path d="M24 16c-4-8-12-6-10-2s8 2 10 2 8-6 10-2-6 8-10 2z" fill="#FBBF24"/>') },
  { id: 'packet', name: '红包', category: '互动', svg: gameSvg('<rect x="10" y="8" width="28" height="32" rx="4" fill="#EF4444"/><path d="M10 16c6 6 22 6 28 0v-4a4 4 0 0 0-4-4H14a4 4 0 0 0-4 4v4z" fill="#DC2626"/><circle cx="24" cy="22" r="5" fill="#FDE68A"/><text x="24" y="25" text-anchor="middle" font-size="8" font-weight="700" fill="#B45309">福</text>') },
  { id: 'diamond', name: '钻石', category: '资源', svg: gameSvg('<path fill="#38BDF8" d="M24 6 42 18 24 42 6 18z"/><path fill="#E0F2FE" d="M24 6 33 18H15z"/><path fill="#0284C7" opacity=".45" d="M6 18h36L24 42z"/>') },
  { id: 'coin', name: '金币', category: '资源', svg: gameSvg('<circle cx="24" cy="24" r="16" fill="#FBBF24"/><circle cx="24" cy="24" r="11" fill="none" stroke="#F59E0B" stroke-width="2"/><text x="24" y="28" text-anchor="middle" font-size="12" font-weight="800" fill="#B45309">¥</text>') },
  { id: 'chest', name: '宝箱', category: '资源', svg: gameSvg('<path fill="#D97706" d="M8 20h32v16a4 4 0 0 1-4 4H12a4 4 0 0 1-4-4V20z"/><path fill="#F59E0B" d="M8 18c0-6 6-10 16-10s16 4 16 10v4H8v-4z"/><rect x="21" y="22" width="6" height="8" rx="1" fill="#FDE68A"/>') },
  { id: 'energy', name: '体力', category: '资源', svg: gameSvg('<path fill="#34D399" d="M26 6 12 26h10l-2 16 16-22H26l2-14z"/>') },
  { id: 'star', name: '星星', category: '资源', svg: gameSvg('<path fill="#FBBF24" d="m24 6 4.8 10.2L40 18l-8 7.6L34 38l-10-5.6L14 38l2-12.4L8 18l11.2-1.8z"/>') },
  { id: 'sad', name: '难过', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#93C5FD"/><circle cx="17" cy="19" r="2.4" fill="#1E3A8A"/><circle cx="31" cy="19" r="2.4" fill="#1E3A8A"/><path d="M16 32c2.6-4 13.4-4 16 0" fill="none" stroke="#1E3A8A" stroke-width="2.4" stroke-linecap="round"/>') },
  { id: 'angry', name: '生气', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#FCA5A5"/><path d="M13 16l8 4M35 16l-8 4" stroke="#7F1D1D" stroke-width="2.4" stroke-linecap="round"/><circle cx="18" cy="23" r="2.2" fill="#7F1D1D"/><circle cx="30" cy="23" r="2.2" fill="#7F1D1D"/><path d="M17 32h14" stroke="#7F1D1D" stroke-width="2.4" stroke-linecap="round"/>') },
  { id: 'cool', name: '酷', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#FDE68A"/><rect x="11" y="17" width="26" height="8" rx="3" fill="#111827"/><path d="M16 31c2.4 3 13.2 3 16 0" fill="none" stroke="#92400E" stroke-width="2.2" stroke-linecap="round"/>') },
  { id: 'cry', name: '哭', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#FDE68A"/><path d="M15 18c1.4 2 3.4 2 4.6 0M28 18c1.4 2 3.4 2 4.6 0" fill="none" stroke="#92400E" stroke-width="2" stroke-linecap="round"/><path d="M17 30c2.2-3 11.6-3 14 0" fill="none" stroke="#92400E" stroke-width="2.2" stroke-linecap="round"/><ellipse cx="16" cy="28" rx="2" ry="3" fill="#38BDF8"/><ellipse cx="33" cy="30" rx="2" ry="3" fill="#38BDF8"/>') },
  { id: 'wink', name: '眨眼', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#FDE68A"/><circle cx="17" cy="20" r="2.4" fill="#92400E"/><path d="M27 20c2 1.4 5 1.4 7 0" fill="none" stroke="#92400E" stroke-width="2.2" stroke-linecap="round"/><path d="M16 29c2.4 4 13.6 4 16 0" fill="none" stroke="#92400E" stroke-width="2.2" stroke-linecap="round"/>') },
  { id: 'sleep', name: '睡觉', category: '表情', svg: gameSvg('<circle cx="24" cy="24" r="20" fill="#DDD6FE"/><path d="M14 21c2 1.6 4 1.6 6 0M28 21c2 1.6 4 1.6 6 0" fill="none" stroke="#5B21B6" stroke-width="2.2" stroke-linecap="round"/><path d="M30 10h8l-8 7h8" fill="none" stroke="#7C3AED" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>') },
  { id: 'clap', name: '鼓掌', category: '互动', svg: gameSvg('<path fill="#FDBA74" d="M14 28c0-6 3-10 7-10 2 0 3 2 3 4v8c-4 3-10 2-10-2z"/><path fill="#F59E0B" d="M24 18c2-4 8-4 10 0v14c0 4-4 6-8 4l-2-18z"/><path fill="#FDE68A" d="M18 16c1-3 5-4 6-1"/>') },
  { id: 'bell', name: '铃铛', category: '互动', svg: gameSvg('<path fill="#FBBF24" d="M24 8a10 10 0 0 0-10 10v7l-3 4h26l-3-4v-7A10 10 0 0 0 24 8z"/><circle cx="24" cy="34" r="3" fill="#F59E0B"/><rect x="22" y="5" width="4" height="4" rx="1" fill="#F59E0B"/>') },
  { id: 'flag', name: '旗帜', category: '互动', svg: gameSvg('<rect x="12" y="8" width="3" height="32" rx="1" fill="#334155"/><path fill="#F43F5E" d="M15 8h20l-4 7 4 7H15z"/>') },
  { id: 'mail', name: '信件', category: '互动', svg: gameSvg('<rect x="8" y="12" width="32" height="24" rx="3" fill="#F8FAFC" stroke="#334155" stroke-width="2"/><path d="M8 14l16 12L40 14" fill="none" stroke="#38BDF8" stroke-width="2.4"/>') },
  { id: 'horn', name: '喇叭', category: '互动', svg: gameSvg('<path fill="#FB7185" d="M10 20h8l16-8v24L18 28h-8z"/><path fill="#FDE68A" d="M10 20v8a4 4 0 0 0 4 4h2v-12h-2a4 4 0 0 0-4 0z"/><path d="M36 18c4 2 4 10 0 12" fill="none" stroke="#F43F5E" stroke-width="2" stroke-linecap="round"/>') },
  { id: 'sword', name: '长剑', category: '战斗', svg: gameSvg('<path fill="#E2E8F0" d="M28 6l10 10-16 16-4-2z"/><path fill="#FBBF24" d="M16 30l6 6-8 2z"/><path fill="#334155" d="M12 36l6-6 2 2-6 6z"/>') },
  { id: 'shield', name: '盾牌', category: '战斗', svg: gameSvg('<path fill="#2563EB" d="M24 6l14 6v10c0 9-6 15-14 20-8-5-14-11-14-20V12z"/><path fill="#BFDBFE" d="M24 12l8 3v6c0 5-3 9-8 12-5-3-8-7-8-12v-6z"/>') },
  { id: 'bow', name: '弓箭', category: '战斗', svg: gameSvg('<path d="M16 8c12 8 12 24 0 32" fill="none" stroke="#B45309" stroke-width="3" stroke-linecap="round"/><path d="M16 8v32" stroke="#94A3B8" stroke-width="1.6"/><path fill="#334155" d="M18 22h18l-4-3v6z"/>') },
  { id: 'bomb', name: '炸弹', category: '战斗', svg: gameSvg('<circle cx="24" cy="28" r="12" fill="#111827"/><rect x="22" y="12" width="4" height="6" rx="1" fill="#334155"/><path d="M26 12c4-4 8-2 8 1" fill="none" stroke="#F59E0B" stroke-width="2" stroke-linecap="round"/><circle cx="34" cy="10" r="2" fill="#FBBF24"/>') },
  { id: 'potion', name: '药水', category: '资源', svg: gameSvg('<rect x="20" y="8" width="8" height="6" rx="1" fill="#94A3B8"/><path fill="#34D399" d="M16 22c0-4 3-6 8-6s8 2 8 6v12a6 6 0 0 1-6 6h-4a6 6 0 0 1-6-6z"/><path fill="#6EE7B7" d="M18 28h12v4a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4z"/>') },
  { id: 'key', name: '钥匙', category: '资源', svg: gameSvg('<circle cx="18" cy="20" r="8" fill="none" stroke="#F59E0B" stroke-width="3"/><path stroke="#F59E0B" stroke-width="3" stroke-linecap="round" d="M24 24l14 8M32 28v5M36 30v4"/>') },
  { id: 'ticket', name: '抽奖券', category: '资源', svg: gameSvg('<path fill="#FDE68A" stroke="#D97706" stroke-width="2" d="M8 14h32v6a4 4 0 0 0 0 8v6H8v-6a4 4 0 0 0 0-8z"/><path d="M24 16v16" stroke="#D97706" stroke-width="2" stroke-dasharray="2 3"/>') },
  { id: 'scroll', name: '卷轴', category: '资源', svg: gameSvg('<rect x="12" y="12" width="24" height="26" rx="2" fill="#FEF3C7" stroke="#B45309" stroke-width="2"/><path d="M12 16h-4v18h4M36 16h4v18h-4" fill="#FDE68A" stroke="#B45309" stroke-width="2"/><path d="M18 22h12M18 28h10" stroke="#D97706" stroke-width="2" stroke-linecap="round"/>') },
  { id: 'crown', name: '王冠', category: '资源', svg: gameSvg('<path fill="#FBBF24" d="M8 32l4-16 8 8 4-12 4 12 8-8 4 16z"/><rect x="8" y="32" width="32" height="6" rx="1" fill="#F59E0B"/><circle cx="12" cy="16" r="2" fill="#FDE68A"/><circle cx="24" cy="12" r="2" fill="#FDE68A"/><circle cx="36" cy="16" r="2" fill="#FDE68A"/>') },
  { id: 'flame', name: '火焰', category: '资源', svg: gameSvg('<path fill="#F97316" d="M24 6c6 8 12 12 12 20a12 12 0 0 1-24 0c0-6 4-8 6-12 2 4 4 4 6-8z"/><path fill="#FDE68A" d="M24 22c3 3 5 5 5 8a5 5 0 0 1-10 0c0-3 2-4 3-6 1 2 1 2 2-2z"/>') },
  { id: 'meat', name: '肉排', category: '资源', svg: gameSvg('<ellipse cx="24" cy="26" rx="14" ry="10" fill="#F87171"/><ellipse cx="24" cy="26" rx="8" ry="5" fill="#FECACA"/><path d="M34 18c4-6 8-6 8-2s-4 6-8 6" fill="#FDBA74"/>') },
  { id: 'shard', name: '碎片', category: '资源', svg: gameSvg('<path fill="#C4B5FD" d="M24 6l10 12-4 20-6 4-6-4-4-20z"/><path fill="#EDE9FE" d="M24 6l6 12H18z"/><path fill="#7C3AED" opacity=".35" d="M14 18h20l-4 20-6 4-6-4z"/>') },
]

export const GAME_CHAT_PRESETS = ['这个副本我来开', '奖励已经领好了', '先去商城补体力', '今晚八点集合']

export interface GameChatBubbleStyle {
  id: string
  name: string
  group: '外形' | '漫画'
  stackStep: number
  preview: string
}

function bubblePreview(inner: string) {
  return `<svg viewBox="0 0 48 32" style="width:100%;height:100%;display:block">${inner}</svg>`
}

export const GAME_CHAT_BUBBLE_STYLES: GameChatBubbleStyle[] = [
  { id: 'tail', name: '尖尾', group: '外形', stackStep: 64, preview: bubblePreview('<rect x="8" y="5" width="30" height="14" rx="5" fill="#fff" stroke="#111" stroke-width="2"/><path d="M14 19l4 8 5-8" fill="#fff" stroke="#111" stroke-width="2" stroke-linejoin="round"/>') },
  { id: 'ellipse', name: '椭圆', group: '外形', stackStep: 84, preview: bubblePreview('<ellipse cx="24" cy="13" rx="16" ry="9" fill="#fff" stroke="#111" stroke-width="2"/><path d="M16 20l3 8 5-8" fill="#fff" stroke="#111" stroke-width="2"/>') },
  { id: 'box', name: '方框', group: '外形', stackStep: 64, preview: bubblePreview('<rect x="10" y="5" width="28" height="14" fill="#fff" stroke="#111" stroke-width="2"/><path d="M16 19h8v7H16z" fill="#fff" stroke="#111" stroke-width="2"/>') },
  { id: 'ribbon', name: '丝带', group: '外形', stackStep: 56, preview: bubblePreview('<polygon points="6,8 14,16 6,24 42,24 36,16 42,8" fill="#fff" stroke="#111" stroke-width="2" stroke-linejoin="round"/>') },
  { id: 'cloud', name: '云朵', group: '漫画', stackStep: 88, preview: bubblePreview('<path d="M16 22c-5 0-7-5-4-8 0-5 6-7 9-3 2-4 9-5 12-1 4-3 9 0 8 5 5 0 6 6 1 8-1 4-8 5-12 2-3 3-9 3-14-3z" fill="#fff" stroke="#111" stroke-width="1.7"/>') },
  { id: 'thought', name: '心想', group: '漫画', stackStep: 96, preview: bubblePreview('<ellipse cx="27" cy="12" rx="13" ry="8" fill="#fff" stroke="#111" stroke-width="2"/><circle cx="15" cy="22" r="3.2" fill="#fff" stroke="#111" stroke-width="1.6"/><circle cx="9" cy="27" r="1.8" fill="#fff" stroke="#111" stroke-width="1.3"/>') },
  { id: 'shout', name: '爆炸', group: '漫画', stackStep: 96, preview: bubblePreview('<polygon points="24,2 28,9 36,5 33,13 43,15 33,18 38,27 29,22 27,31 22,22 13,29 17,19 7,17 17,13 13,5 22,9" fill="#ffe14a" stroke="#111" stroke-width="1.5" stroke-linejoin="round"/>') },
  { id: 'whisper', name: '虚线', group: '漫画', stackStep: 78, preview: bubblePreview('<ellipse cx="24" cy="16" rx="16" ry="9" fill="#fff" stroke="#111" stroke-width="1.6" stroke-dasharray="3 2"/>') },
]

export const GAME_CHAT_MESSAGES: GameChatMessage[] = [
  { id: 'player-1', role: 'player', name: '小星', text: '这个副本我来开', time: '刚刚', color: '#38BDF8' },
  { id: 'player-2', role: 'player', name: '阿宁', text: '宝箱还差最后一下', time: '3分钟前', color: '#34D399' },
  { id: 'player-3', role: 'player', name: '老周', text: '谁有多余的抽奖券', time: '5分钟前', color: '#F59E0B' },
  { id: 'player-4', role: 'player', name: '晚晚', text: '组队的来世界频道', time: '8分钟前', color: '#FB7185' },
  { id: 'npc-1', role: 'npc', name: '引导员', text: '先去商城把体力领了', time: '1分钟前', color: '#A78BFA' },
  { id: 'npc-2', role: 'npc', name: '铁匠', text: '这把剑还差一块碎片', time: '6分钟前', color: '#F97316' },
  { id: 'npc-3', role: 'npc', name: '店长', text: '今日礼包限时半价', time: '12分钟前', color: '#22C55E' },
  { id: 'npc-4', role: 'npc', name: '守卫', text: '前方副本已经开放', time: '20分钟前', color: '#6366F1' },
  { id: 'system-1', role: 'system', name: '系统', text: '每日任务已刷新', time: '现在', color: '#94A3B8' },
  { id: 'system-2', role: 'system', name: '系统', text: '获得钻石 ×120', time: '刚刚', color: '#38BDF8' },
  { id: 'system-3', role: 'system', name: '公告', text: '公会战将在今晚开启', time: '1小时前', color: '#F59E0B' },
  { id: 'system-4', role: 'system', name: '邮件', text: '你有一份未领取的奖励', time: '昨天', color: '#A78BFA' },
]

export const GAME_ITEM_SLOTS: GameItemSlot[] = [
  { id: 'diamond', name: '钻石', rarity: '稀有', count: 120, bg: '#EFF6FF', border: '#38BDF8', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'diamond')!.svg },
  { id: 'chest', name: '宝箱', rarity: '传说', count: 2, bg: '#FFFBEB', border: '#F59E0B', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'chest')!.svg },
  { id: 'energy', name: '体力', rarity: '精良', count: 8, bg: '#ECFDF5', border: '#34D399', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'energy')!.svg },
  { id: 'gift', name: '礼包', rarity: '史诗', count: 1, bg: '#F5F3FF', border: '#A78BFA', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'gift')!.svg },
  { id: 'coin', name: '金币', rarity: '普通', count: 980, bg: '#F8FAFC', border: '#CBD5E1', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'coin')!.svg },
  { id: 'star', name: '星石', rarity: '史诗', count: 6, bg: '#F5F3FF', border: '#A78BFA', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'star')!.svg },
  { id: 'potion', name: '药水', rarity: '精良', count: 5, bg: '#ECFDF5', border: '#34D399', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'potion')!.svg },
  { id: 'key', name: '钥匙', rarity: '稀有', count: 1, bg: '#EFF6FF', border: '#38BDF8', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'key')!.svg },
  { id: 'ticket', name: '抽奖券', rarity: '史诗', count: 3, bg: '#F5F3FF', border: '#A78BFA', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'ticket')!.svg },
  { id: 'scroll', name: '卷轴', rarity: '稀有', count: 2, bg: '#FFFBEB', border: '#F59E0B', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'scroll')!.svg },
  { id: 'crown', name: '王冠', rarity: '传说', count: 1, bg: '#FFFBEB', border: '#F59E0B', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'crown')!.svg },
  { id: 'flame', name: '火焰', rarity: '史诗', count: 4, bg: '#FFF7ED', border: '#FB923C', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'flame')!.svg },
  { id: 'meat', name: '肉排', rarity: '普通', count: 12, bg: '#F8FAFC', border: '#CBD5E1', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'meat')!.svg },
  { id: 'shard', name: '碎片', rarity: '精良', count: 18, bg: '#F5F3FF', border: '#A78BFA', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'shard')!.svg },
  { id: 'sword', name: '长剑', rarity: '传说', count: 1, bg: '#FFFBEB', border: '#F59E0B', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'sword')!.svg },
  { id: 'shield', name: '盾牌', rarity: '稀有', count: 1, bg: '#EFF6FF', border: '#38BDF8', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'shield')!.svg },
  { id: 'bomb', name: '炸弹', rarity: '精良', count: 3, bg: '#F8FAFC', border: '#94A3B8', svg: GAME_BUBBLE_ICONS.find((i) => i.id === 'bomb')!.svg },
]

function escapeGameText(text: string) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function gameBubbleIconHtml(icon: GameBubbleIcon) {
  return `<div class="wf-el wf-game-icon" title="${escapeGameText(icon.name)}" style="width:48px;height:48px;display:flex;align-items:center;justify-content:center;box-sizing:border-box;">${icon.svg}</div>`
}

function gameChatText(text: string, extra = '') {
  return `<span class="wf-text" style="position:relative;z-index:1;display:block;text-align:center;color:#111;line-height:1.35;${extra}">${text}</span>`
}

export function gameChatBubbleHtml(styleId: string, text: string) {
  const safe = escapeGameText(text || '……')
  if (styleId === 'ellipse') {
    return `<div class="wf-el wf-chat-bubble" style="position:relative;display:inline-flex;align-items:center;justify-content:center;min-width:156px;min-height:84px;padding:22px 34px 28px;box-sizing:border-box;"><div style="position:absolute;left:0;right:0;top:0;bottom:14px;border-radius:50%;background:#fff;border:2.5px solid #111;box-sizing:border-box;"></div><div style="position:absolute;left:26px;bottom:2px;width:0;height:0;border-left:7px solid transparent;border-right:11px solid transparent;border-top:16px solid #111;"></div><div style="position:absolute;left:29px;bottom:6px;width:0;height:0;border-left:5px solid transparent;border-right:8px solid transparent;border-top:12px solid #fff;"></div>${gameChatText(safe, 'font-size:14px;max-width:150px;')}</div>`
  }
  if (styleId === 'box') {
    return `<div class="wf-el wf-chat-bubble" style="position:relative;display:inline-block;margin:0 0 14px 0;box-sizing:border-box;"><div style="background:#fff;color:#111;border:3px solid #111;padding:8px 12px;font-size:14px;line-height:1.4;max-width:220px;">${gameChatText(safe, 'text-align:left;font-size:14px;')}</div><div style="position:absolute;left:16px;bottom:-8px;width:14px;height:14px;background:#fff;border-left:3px solid #111;border-bottom:3px solid #111;box-sizing:border-box;"></div></div>`
  }
  if (styleId === 'ribbon') {
    return `<div class="wf-el wf-chat-bubble" style="display:inline-block;background:#fff;color:#111;border:2.5px solid #111;clip-path:polygon(12px 0,calc(100% - 12px) 0,100% 50%,calc(100% - 12px) 100%,12px 100%,0 50%);padding:8px 26px;box-sizing:border-box;">${gameChatText(safe, 'font-size:14px;font-weight:700;')}</div>`
  }
  if (styleId === 'cloud') {
    return `<div class="wf-el wf-chat-bubble" style="position:relative;display:inline-block;min-width:168px;padding:26px 34px 30px;box-sizing:border-box;"><svg viewBox="0 0 220 120" preserveAspectRatio="none" style="position:absolute;inset:0;width:100%;height:100%;overflow:visible;"><path d="M62 74C40 74 30 58 46 50C40 32 64 24 80 36C94 18 132 18 146 38C168 26 196 42 186 62C208 68 204 92 176 94C170 108 136 112 118 98C98 112 70 106 62 92C44 98 38 86 62 74Z" fill="#fff" stroke="#111" stroke-width="3" vector-effect="non-scaling-stroke"/></svg>${gameChatText(safe, 'font-size:14px;max-width:140px;')}</div>`
  }
  if (styleId === 'thought') {
    return `<div class="wf-el wf-chat-bubble" style="position:relative;display:inline-flex;flex-direction:column;align-items:flex-start;box-sizing:border-box;"><div style="position:relative;display:flex;align-items:center;justify-content:center;min-width:150px;min-height:72px;padding:18px 28px;box-sizing:border-box;"><div style="position:absolute;inset:0;border-radius:50%;background:#fff;border:2.5px solid #111;box-sizing:border-box;"></div>${gameChatText(safe, 'font-size:14px;max-width:130px;')}</div><div style="width:14px;height:14px;margin:2px 0 0 22px;border-radius:50%;background:#fff;border:2px solid #111;box-sizing:border-box;"></div><div style="width:8px;height:8px;margin:3px 0 0 10px;border-radius:50%;background:#fff;border:2px solid #111;box-sizing:border-box;"></div></div>`
  }
  if (styleId === 'shout') {
    const burst = 'polygon(50% 0,58% 14%,74% 4%,70% 20%,90% 16%,82% 32%,100% 36%,84% 48%,98% 64%,78% 62%,82% 84%,64% 72%,60% 96%,46% 74%,30% 92%,28% 70%,8% 78%,18% 58%,0 52%,16% 40%,4% 24%,22% 26%,18% 8%,36% 16%,40% 0)'
    return `<div class="wf-el wf-chat-bubble" style="position:relative;display:inline-block;padding:28px 36px 34px;max-width:230px;box-sizing:border-box;"><div style="position:absolute;inset:0;background:#111;clip-path:${burst};"></div><div style="position:absolute;inset:6px;background:#ffe14a;clip-path:${burst};"></div>${gameChatText(safe, 'font-size:16px;font-weight:800;')}</div>`
  }
  if (styleId === 'whisper') {
    return `<div class="wf-el wf-chat-bubble" style="position:relative;display:inline-flex;align-items:center;justify-content:center;min-width:140px;min-height:64px;padding:16px 26px;box-sizing:border-box;"><div style="position:absolute;inset:0;border-radius:50%;background:#fff;border:2px dashed #64748b;box-sizing:border-box;"></div>${gameChatText(safe, 'font-size:12px;color:#64748b;')}</div>`
  }
  return `<div class="wf-el wf-chat-bubble" style="position:relative;display:inline-block;margin:0 0 14px 0;box-sizing:border-box;"><div style="background:#fff;color:#111;border:2.5px solid #111;border-radius:16px;padding:8px 14px;max-width:220px;">${gameChatText(safe, 'text-align:left;font-size:14px;')}</div><div style="position:absolute;left:22px;bottom:-9px;width:0;height:0;border-left:8px solid transparent;border-right:10px solid transparent;border-top:12px solid #111;"></div><div style="position:absolute;left:24px;bottom:-5px;width:0;height:0;border-left:6px solid transparent;border-right:8px solid transparent;border-top:9px solid #fff;"></div></div>`
}

export function gameChatFrameHtml(message: GameChatMessage) {
  const safeName = escapeGameText(message.name)
  const safeText = escapeGameText(message.text)
  const safeTime = escapeGameText(message.time)
  if (message.role === 'system') {
    return `<div class="wf-el wf-chat-frame" style="width:280px;display:flex;justify-content:center;box-sizing:border-box;"><div style="padding:6px 12px;background:#F1F5F9;border-radius:999px;color:#64748B;font-size:12px;line-height:1.4;"><span class="wf-text">${safeName} · ${safeText}</span></div></div>`
  }
  const avatar = `<div style="width:36px;height:36px;border-radius:50%;background:${message.color};color:#fff;font-size:14px;font-weight:800;display:flex;align-items:center;justify-content:center;flex-shrink:0;">${safeName.slice(0, 1)}</div>`
  const mine = message.role === 'player'
  const bubble = mine
    ? 'background:#ffffff;color:#0f172a;border:1px solid #e2e8f0;border-radius:4px 14px 14px 14px;'
    : 'background:#7C3AED;color:#ffffff;border-radius:14px 4px 14px 14px;'
  const body = `<div style="min-width:0;flex:1;display:flex;flex-direction:column;align-items:${mine ? 'flex-start' : 'flex-end'};"><div style="display:flex;gap:8px;align-items:center;margin-bottom:4px;${mine ? '' : 'flex-direction:row-reverse;'}"><span class="wf-text" style="font-size:12px;font-weight:700;color:#0f172a;">${safeName}</span><span class="wf-text" style="font-size:10px;color:#94a3b8;">${safeTime}</span></div><div style="${bubble}padding:8px 12px;font-size:13px;line-height:1.45;max-width:210px;"><span class="wf-text">${safeText}</span></div></div>`
  return `<div class="wf-el wf-chat-frame" style="width:280px;display:flex;gap:8px;align-items:flex-start;box-sizing:border-box;${mine ? '' : 'flex-direction:row-reverse;'}">${avatar}${body}</div>`
}

export function gameItemSlotHtml(item: GameItemSlot) {
  return `<div class="wf-el wf-item-slot" title="${escapeGameText(item.name)}" style="width:64px;height:64px;position:relative;border-radius:14px;background:${item.bg};border:2px solid ${item.border};box-sizing:border-box;display:flex;align-items:center;justify-content:center;"><div style="width:34px;height:34px;">${item.svg}</div><span class="wf-text" style="position:absolute;right:4px;bottom:2px;font-size:11px;font-weight:800;color:#0f172a;line-height:1;">${item.count}</span></div>`
}
