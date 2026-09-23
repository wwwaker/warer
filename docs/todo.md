# Warer 项目 TODO

---

## ✅ 已完成

### 后端 (FastAPI + SymPy)

- [x] FastAPI 基础框架 + CORS + `/health` 健康检查
- [x] `POST /v1/compute` 计算接口（请求/响应 Pydantic 模型）
- [x] `diff` / `derivative` 求导
- [x] `integrate` 不定积分 + 定积分（含瑕积分）
- [x] `solve` 解方程（多项式 + 超越方程）
- [x] `nsolve` 数值求根
- [x] `dsolve` 微分方程
- [x] `linsolve` 线性方程组
- [x] `limit` 求极限（含方向参数）
- [x] `series` / `taylor` 级数展开
- [x] `simplify` 化简 + 纯表达式自动化简
- [x] 矩阵操作：`det` / `inv` / `transpose` / `eigenvals` / `eigenvects` / `rank` / 矩阵算术
- [x] 表达式预处理：`^`→`**`、隐式乘号、`ln`→`log`、`sin(x)^2`→`(sin(x))**2`
- [x] 嵌套命令检测（`diff(solve(...))` 拒绝并提示分步计算）
- [x] 30s 计算超时保护
- [x] 括号匹配校验 + 中文错误提示
- [x] 常见数学错误的友好中文映射（奇异矩阵、除零、不收敛等）
- [x] 智能舍入（`smartRound`）：分数/整数识别
- [x] 虚数 `i` / `j` 支持
- [x] 结果返回 LaTeX + plain text + 数值近似 + 变量列表

### Web 前端 (React + TypeScript)

- [x] 多栏卡片系统（拖拽、重命名、关闭、最多 3 栏 + 栏宽拖动）
- [x] 科学键盘（三角函数、对数、`diff`/`int`、`θ`/`t` 按钮）
- [x] LaTeX 实时预览（词法分析器 + 递归下降解析器 → KaTeX）
- [x] 括号自动补全
- [x] 混合计算路由（Local Math.js ↔ Cloud SymPy + 自动降级）
- [x] 函数绘图 4 模式（线性 / 极坐标 / 参数方程 / 隐式方程）
- [x] 计算器→图像路由（显式表示法 `y()`/`r()`/`t()`/`f()` + 启发式检测）
- [x] 每函数独立类型（同一图内混合模式）
- [x] 图像交互：缩放、平移、轨迹跟踪、等比例坐标轴
- [x] 极坐标网格切换、PNG 导出
- [x] 历史记录（搜索 + 点击回填）
- [x] 常用模板面板（求根/求导/积分/化简/矩阵等预设）

---

## 🔄 进行中

- [ ] **Android 端完整重写** — 旧版 `android/` 已废弃，见 `docs/ANDROID_DEV_PLAN.md`

---

## 📋 Android 重写任务队列

### P0 — 核心骨架

- [ ] 项目初始化：Gradle KTS + Version Catalog + Compose + Material3
- [ ] 主题系统：亮/暗/跟随系统 + 动态取色 (API 31+) + fallback
- [ ] 网络层：Retrofit + OkHttp + Kotlinx Serialization + 动态 BaseUrl
- [ ] 配置持久化：DataStore（后端地址、精度、主题）
- [ ] `/health` 连接测试界面 + 状态反馈
- [ ] 输入框 + 基本结果展示 + 错误展示

### P1 — 核心计算

- [ ] 科学键盘完整移植（trig / log / sqrt / diff / int / solve / θ / t / π / e / 括号）
- [ ] 本地计算引擎集成（mXparser 或等价方案）
- [ ] 混合路由 Dispatcher（1:1 移植 `dispatcher.ts` 逻辑）
- [ ] WebView + KaTeX 实时公式预览（复用 `katex_preview.html`）
- [ ] 表达式预处理 Transpiler（1:1 移植 `localEngine.ts` 预处理）
- [ ] 请求防抖 300ms + 竞态处理（取消前序请求）
- [ ] 云端降级（网络异常 → 本地兜底）
- [ ] 常用模板面板

### P2 — 函数绘图

- [ ] WebView + function-plot 桥接层
- [ ] 4 种绘图模式
- [ ] 显式表示法检测（移植 `graphDetection.ts`）
- [ ] 计算器 → 绘图路由
- [ ] 图像交互（缩放 / 平移 / 跟踪）

### P3 — 历史 & 持久化

- [ ] Room 集成 + 数据实体设计
- [ ] 历史 CRUD + 搜索
- [ ] 历史点击回填
- [ ] 计算结果本地缓存

### P4 — 打磨

- [ ] 触觉反馈（按键震动）
- [ ] 横竖屏适配
- [ ] 错误处理全面覆盖
- [ ] 精度控制设置
- [ ] 启动画面 (SplashScreen)
- [ ] ProGuard 混淆
- [ ] 发布 APK / AAB

---

## 📋 后端未来方向

### 短期

- [ ] `series` 返回更多项（目前默认到 O(x^n)，需保留移除 O 项的选项）
- [ ] `nsolve` 支持多初值尝试、自动初值选取
- [ ] 计算步骤分解（`steps` 字段，展示推导过程）

### 中期

- [ ] 方程组求解 `nonlinsolve`
- [ ] 矩阵化简（行阶梯形、零空间）
- [ ] 缓存层（相同表达式命中缓存直接返回）
- [ ] `sympy.lambdify` 数值计算加速

### 长期

- [ ] WebSocket 长连接（替代短轮询）
- [ ] 可选 PostgreSQL 持久化计算历史

---

## 📋 Web 前端未来方向

### 短期

- [ ] 动态错误高亮（输入框中标记错误字符位置）
- [ ] 触控反馈（按键按压态 + 震动 API）
- [ ] 键盘布局自适应（小屏幕折叠科学键区）
- [ ] `Ctrl+Z` / `Ctrl+Y` 撤销重做
- [ ] 深色模式切换

### 中期

- [ ] 图像关键点标注（零点、极值点、交点 — 后端 plot-analysis 端点）
- [ ] CSV 图像数据导出
- [ ] 单位换算器模块
- [ ] 精度控制设置面板
- [ ] 计算耗时显示
- [ ] IndexedDB 计算结果缓存

### 长期

- [ ] Error Boundary（单卡片崩溃不影响全局）
- [ ] PWA 离线支持
- [ ] i18n 国际化
