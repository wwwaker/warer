# Warer Android 开发计划

> **状态：** 全新重写。旧版 `android/` 目录代码已废弃，本计划为从零构建的新 Android 端纲领。

---

## 1. 最低 SDK 版本决策

**选定：`minSdk = 26` (Android 8.0 Oreo)**

| 因素 | 分析 |
|------|------|
| **目标用户** | 中学生到大学生（中国为主），2026 年 Android 8.0 以下设备占比 < 2% |
| **Jetpack Compose** | 官方最低 API 21，API 26 上 Compose 性能有保障 |
| **Material3 动态取色** | 需 API 31+，但可优雅降级回 fallback 色板 |
| **WebView 稳定性** | API 26 起 WebView 通过 Play Store 独立更新，KaTeX / function-plot 渲染稳定 |
| **Java 8 语言特性** | API 26 起完整支持 |
| **Coroutines / Flow / DataStore** | 全部兼容 API 21+，无约束 |
| **维护成本** | 覆盖 98%+ 活跃设备，无需为老旧厂商 ROM 做特殊适配 |

> **备选方案：** `minSdk = 24` 可额外覆盖约 2% 设备，但 Java 8 部分 API 需要 desugar，且 WebView 行为碎片化风险上升 — 不值得。

---

## 2. 技术栈

| 模块 | 选型 | 说明 |
|------|------|------|
| **语言** | Kotlin 100% | 无 Java 混编 |
| **UI 框架** | Jetpack Compose + Material3 | 声明式 UI，遵循 Material Design 3 |
| **架构模式** | MVVM 单向数据流 | `ViewModel` + `StateFlow` + `UiState` |
| **网络** | Retrofit + OkHttp + Kotlinx Serialization | Gson 换 Kotlinx Serialization 以获得原生 Kotlin 支持 |
| **本地计算** | **实现方案待定** | 选项：mXparser（成熟 Java 库）/ exp4j（轻量）/ Kotlin 自建评估器 |
| **公式渲染** | WebView + KaTeX | 复用 `android/app/src/main/assets/katex/` 资源，隔离渲染进程 |
| **函数绘图** | WebView + function-plot | 复用 Web 端 `function-plot` JS 库，统一渲染 HTML 桥接层 |
| **本地存储** | DataStore（配置） + Room（历史） | DataStore 已就绪，Room 用于持久化计算历史和图形函数 |
| **依赖注入** | 手动 DI / Hilt | MVP 阶段手动注入足够，复杂后迁移 Hilt |
| **协程** | Kotlin Coroutines + Flow | 异步网络、防抖、竞态处理 |
| **构建** | Gradle KTS + Version Catalog | `libs.versions.toml` 集中管理依赖版本 |
| **测试** | JUnit + Compose UI Test + MockK | 单元测试 ViewModel，UI 测试核心交互 |

## 3. 架构设计

### 3.1 分层架构

```
┌─────────────────────────────────────────────────┐
│  UI Layer (Compose)                             │
│  Screen / Composable / State                    │
├─────────────────────────────────────────────────┤
│  ViewModel Layer                                │
│  UiState / SideEffect / Event handling          │
├────────────────────┬────────────────────────────┤
│  Domain Layer      │  Engine Layer               │
│  (use cases)       │  LocalEngine / Dispatcher   │
├────────────────────┴────────────────────────────┤
│  Data Layer                                      │
│  Retrofit ApiService · Room DAO · DataStore      │
├─────────────────────────────────────────────────┤
│  Android Framework Layer                         │
│  WebView · File System · Network                 │
└─────────────────────────────────────────────────┘
```

### 3.2 混合计算路由（与 Web 端对齐）

```
用户输入 → needsCloud()? → 是 → POST /v1/compute (SymPy)
                ↓ 否
           mXparser 本地求值 → 结果渲染
```

判定逻辑：
1. **L1 语法检查**：包含 `diff` / `int` / `solve` / `simplify` / `limit` 等关键词 → 云端
2. **L2 符号变量检测**：含 `x` / `y` 等自由变量 → 云端
3. **L3 本地计算**：纯数值运算 + 基础函数（三角函数、对数、幂）→ mXparser 本地
4. **L4 降级**：云端请求失败/超时，若表达式可在本地计算则降级

> 此逻辑是 `web/src/engine/dispatcher.ts` 的 1:1 Kotlin 移植，保持跨端一致。

### 3.3 与 Web 端共享的设计

| 共享资产 | 位置 | 用途 |
|----------|------|------|
| KaTeX JS/CSS | `assets/katex/` | LaTeX 公式渲染 |
| `katex_preview.html` | `assets/` | WebView 渲染入口 |
| function-plot JS | `assets/`（需添加） | 函数图像绘制 |
| `function_plot.html` | `assets/`（需添加） | 绘图 WebView 入口 |
| API 响应格式 | — | 与 Web 端共享同一后端，请求/响应模型一致 |

### 3.4 UI 页面结构

```
Warer App
├── MainScreen
│   ├── TopAppBar (菜单 / 标题)
│   ├── InputPanel (输入框 + LaTeX 实时预览 WebView)
│   ├── ScientificKeyboard (响应式键盘)
│   └── ResultPanel (KaTeX 渲染结果 + 计算信息)
├── DrawerMenu
│   ├── 后端地址配置
│   ├── 连接测试
│   ├── 主题切换 (亮/暗/跟随系统)
│   └── 精度设置
├── HistoryScreen (计算历史 + 搜索)
└── GraphScreen (函数绘图 WebView)
```

---

## 4. 分阶段实施路线图

### Phase 1 — 项目骨架 & 通信（预计 1 周）

- [ ] 初始化 Gradle KTS 构建脚本，配置 Version Catalog
- [ ] 集成 Compose + Material3 + 主题（亮/暗/动态取色）
- [ ] 集成 Retrofit + Kotlinx Serialization，定义 API 接口
- [ ] 实现 `RetrofitClient` 动态 BaseUrl 切换
- [ ] 实现 `SettingsManager`（DataStore 配置持久化）
- [ ] 实现 `/health` 连接测试 + 可视化反馈
- [ ] 输入框 + 基本结果展示界面

### Phase 2 — 核心计算体验（预计 2 周）

- [ ] 移植科学键盘（与 Web 端功能对齐：trig / log / sqrt / diff / int / θ / t）
- [ ] 集成本地计算引擎（mXparser 或等替代方案）
- [ ] 实现混合路由 `Dispatcher`（`needsCloud()` 逻辑）
- [ ] 集成 WebView + KaTeX 实时公式预览（`katex_preview.html`）
- [ ] 支持常用模板（求根/求导/积分/化简）
- [ ] 实现请求防抖（Debounce 300ms）+ 竞态条件处理
- [ ] 实现云端降级（网络异常 → 本地兜底）

### Phase 3 — 函数绘图（预计 1 周）

- [ ] 集成 WebView + function-plot HTML 桥接层
- [ ] 支持 4 种绘图模式（线性 / 极坐标 / 参数方程 / 隐式方程）
- [ ] 实现 `graphDetection.ts` 的 Kotlin 移植（显式表示法检测）
- [ ] 计算器→绘图路由（一键发送函数到图像页面）
- [ ] 图像交互：缩放 / 平移 / 轨迹跟踪

### Phase 4 — 历史 & 持久化（预计 1 周）

- [ ] 集成 Room，设计 `ComputationHistory` 实体
- [ ] 实现历史记录 CRUD + 搜索
- [ ] 历史点击回填输入框
- [ ] 表达式结果本地缓存（避免重复请求）

### Phase 5 — 打磨 & 发布（预计 1 周）

- [ ] 触控反馈优化（按键按压态 + 震动）
- [ ] 横竖屏适配
- [ ] 错误处理全面覆盖（网络、语法、计算超时）
- [ ] 深色模式切换
- [ ] 精度控制设置
- [ ] ProGuard 混淆配置
- [ ] 发布 APK / App Bundle

> **总计：约 6 周（单人全职开发）**

---

## 5. 与 Web 端的关键差异

| 维度 | Web 端 | Android 端 |
|------|--------|------------|
| **本地计算** | Math.js（浏览器原生） | mXparser / Kotlin 实现（JVM 原生） |
| **公式渲染** | KaTeX 原生 DOM 渲染 | WebView 隔离渲染（`AndroidView`） |
| **函数绘图** | function-plot 原生 DOM | WebView 隔离渲染（`AndroidView`） |
| **状态管理** | Zustand | ViewModel + StateFlow |
| **持久化** | IndexedDB | DataStore + Room |
| **多栏卡片** | 原生拖拽系统（最多 3 栏） | 移动端简化：Tab 导航 + 页面栈 |

---

## 6. 关键决策记录

### 6.1 WebView 隔离渲染策略

移动端所有 LaTeX 渲染和函数绘图均通过 `AndroidView` 内嵌 WebView 实现，而非 Android 原生 Canvas：

- **原因：** KaTeX 和 function-plot 是 Web 端成熟稳定的渲染方案，通过 WebView 复用可避免 Android 端重复造轮子，且保证跨端公式显示一致。
- **代价：** WebView 启动和 JS 执行有性能开销（约 50-200ms 首屏延迟），需通过预热和 JS 缓存优化。

### 6.2 本地计算引擎选型

| 候选 | 优点 | 缺点 |
|------|------|------|
| **mXparser (Java)** | 功能齐全（符号/数值/函数），活跃维护 | LGPL 许可需注意，APK 体积增加 ~500KB |
| **exp4j** | 超轻量（~50KB），Apache 2.0 许可 | 不支持符号计算，仅数值 |
| **Kotlin 自建评估器** | 无依赖，完全控制 | 工作量巨大，容易有精度 bug |
| **evaluator** (Kotlin 库) | 原生 Kotlin，轻量 | 社区较小，功能有限 |

**推荐：mXparser** — 功能最接近 Math.js，支持隐式乘法、自定义函数、常量识别，最适合做 Web 端 `localEngine.ts` 的 Kotlin 对等实现。

---

## 7. 附录：废弃代码处置

旧版 `android/` 目录中的以下代码在新项目中**不再使用**：

| 文件 | 废弃原因 |
|------|----------|
| `ui/components/DebugInfoPanel.kt` | 调试面板，生产代码不再需要 |
| `ui/MainViewModel.kt` | 架构需要重新设计 (UiState 模式) |
| `network/ApiService.kt` | 将从 Gson 迁移至 Kotlinx Serialization |
| `engine/Transpiler.kt` | 过于简陋，需完整重写 |
| `ui/MainScreen.kt` | UI 结构重新设计 |
| 所有 `res/xml/` 备份规则 | 由新项目模板自动生成 |

可参考的保留资产：
- `assets/katex/katex.min.js` + `katex.min.css`（继续使用）
- `assets/katex_preview.html`（继续使用，需优化）
- `utils/SettingsManager.kt`（可参考，但需用 Kotlinx Serialization 改造）
- `ui/theme/`（可参考颜色和主题配置）
