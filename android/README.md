> [!WARNING]
> **本项目已停止维护（2026-09）**
>
> 代码仅作存档参考，不再更新。

---

# Warer Android

Warer Android 端采用 **Kotlin + Jetpack Compose** 构建，与 Web 端共享"本地数值 + 云端符号"混合计算架构，支持实时 LaTeX 公式预览和函数绘图。

---

## 🌐 项目导航

| 项目            | 仓库地址                                                           |
| :------------ | :-------------------------------------------------------- |
| **主仓库**       | [warer](https://github.com/wwwaker/warer)                    |
| **Web 前端**    | [warer-web](https://github.com/wwwaker/warer-web)             |
| **Android 端** | [warer-android](https://github.com/wwwaker/warer-android)   |

---

## ✨ 已实现功能

### 🧮 计算器核心
- **混合计算** — 基本运算本地执行（mXparser），符号计算（微分、积分、求解等）自动切换云端
- **实时 LaTeX 预览** — 输入即见公式预览，使用递归下降解析器，支持分数、幂次、函数参数、矩阵等
- **智能括号补全** — 自动检测括号不匹配并提供一键修复
- **函数模板面板** — 内置求导、积分、求解、矩阵等常用模板
- **本地降级** — 云端不可用时自动降级本地计算

### 📈 函数绘图
- **四种函数模式**：
  - 线性函数 `y = f(x)`
  - 极坐标函数 `r = f(θ)`
  - 参数方程 `x(t), y(t)`
  - 隐函数 `f(x, y) = 0`
- **视图控制**：平移、缩放、重置
- **多函数管理**：同时绘制多条函数，支持显示/隐藏切换
- **实验性功能**：Y 轴范围可调，基础交互控制

### 📋 历史记录
- **分类浏览**：按计算/绘图类型筛选
- **关键词搜索**：支持全文搜索历史记录
- **日期分组**：自动按今天/昨天/本周/日期分组
- **历史回溯**：点击计算记录恢复输入，点击绘图记录跳转绘图页

---

## 🚀 快速开始

### 方式一：Android Studio

打开 `android/` 目录，同步 Gradle，连接设备后点击运行。

### 方式二：命令行

```bash
# 构建 debug APK
./gradlew assembleDebug

# 安装到连接的设备
./gradlew installDebug

# 启动应用
adb shell am start -n com.wwwaker.warer_android/.MainActivity
```

---

## 🔧 技术栈

| 模块 | 技术选型 |
| :--- | :--- |
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM (ViewModel + StateFlow) |
| 网络 | Retrofit + OkHttp + kotlinx-serialization |
| 本地存储 | Room (SQLite) + DataStore Preferences |
| 本地计算 | mXparser |
| 公式渲染 | WebView + KaTeX (CDN) |
| 函数绘图 | WebView + function-plot.js |
| 导航 | Navigation Compose |

### 环境要求

| 参数 | 值 |
| :--- | :--- |
| minSdk | 26 (Android 8.0) |
| compileSdk / targetSdk | 36 |
| Kotlin | 2.2.10 |
| AGP | 9.1.1 |
| Compose BOM | 2026.02.01 |

---

## 📁 项目结构

```
com.wwwaker.warer_android/
├── data/
│   ├── api/              # Retrofit 网络层
│   │   ├── ApiService.kt       # 接口定义 (POST /v1/compute, GET /health)
│   │   ├── ApiModels.kt        # 请求/响应数据模型
│   │   └── RetrofitClient.kt   # Retrofit 单例 (支持动态 baseUrl)
│   ├── db/               # Room 数据库
│   │   ├── HistoryEntity.kt    # 历史记录实体
│   │   ├── HistoryDao.kt       # DAO 操作
│   │   └── AppDatabase.kt      # 数据库实例
│   ├── engine/           # 计算引擎
│   │   ├── GraphDetector.kt    # 函数类型检测 (线性/极坐标/参数/隐式)
│   │   └── LatexParser.kt      # LaTeX 预览解析器
│   └── settings/         # 设置管理
│       └── SettingsManager.kt  # DataStore 封装
├── ui/
│   ├── screen/           # 页面
│   │   ├── CalculatorScreen.kt # 计算器页
│   │   ├── GraphScreen.kt      # 绘图页
│   │   └── HistoryScreen.kt    # 历史页
│   ├── component/        # 组件
│   │   ├── InputPanel.kt       # 输入面板 (含绘图跳转按钮)
│   │   ├── ResultPanel.kt      # 结果展示
│   │   ├── ScientificKeyboard.kt # 科学键盘
│   │   ├── FunctionPanel.kt    # 函数与模板面板
│   │   ├── KatexWebView.kt     # KaTeX WebView 渲染器
│   │   ├── GraphWebView.kt     # 函数绘图 WebView
│   │   └── DrawerMenu.kt       # 侧边菜单
│   ├── viewmodel/        # 状态管理
│   │   ├── CalculatorViewModel.kt # 计算器状态
│   │   └── HistoryViewModel.kt    # 历史记录状态
│   └── theme/            # Material3 主题
│       ├── Color.kt
│       ├── Type.kt
│       └── Theme.kt
├── navigation/           # 路由导航
│   ├── Screen.kt         # 路由定义
│   └── NavGraph.kt       # 导航图
├── MainActivity.kt       # 单 Activity 入口
└── WarerApplication.kt   # Application 初始化
```

---

## 🔗 连接后端

应用默认连接 `http://121.40.223.203/`（公网部署），可通过侧边菜单修改服务器地址。

开发时可在本地启动后端：

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 网络配置

`res/xml/network_security_config.xml` 中使用 `<base-config cleartextTrafficPermitted="true" />` 允许明文 HTTP 流量，方便开发环境连接任意后端地址。

---

## 🧠 混合计算流程

```
用户输入
  ├─ 需要符号计算？  (检测 symbolic 关键词 / 矩阵字面量 / 独立变量)
  │   ├─ 否 → mXparser 本地计算
  │   │          ├─ 数值结果 → 智能四舍五入为常见分数 (1/2..1/12)
  │   │          └─ 显示 "离线" 标记
  │   └─ 是 → Retrofit 请求云端 (POST /v1/compute)
  │              ├─ 成功 → 展示 LaTeX 结果 + "云端" 标记
  │              ├─ 云端报错 → 尝试本地降级
  │              └─ 网络异常 → 显示网络错误 / 本地降级
```

### LaTeX 预览解析

内置递归下降解析器（`LatexParser.kt`），与 Web 端共享相同解析逻辑：

| 输入 | LaTeX 输出 |
| :--- | :--- |
| `a/b` | `\frac{a}{b}` |
| `x^2` | `x^{2}` |
| `sin(x)^2` | `\sin^{2}\left(x\right)` |
| `sqrt(x)` | `\sqrt{x}` |
| `limit(sin(x)/x, x, 0)` | `\lim_{x \to 0} \frac{\sin(x)}{x}` |
| `diff(x^2, x)` | `\frac{d}{d x}\left(x^{2}\right)` |
| `integrate(x^2, x, 0, 1)` | `\int_{0}^{1} x^{2} \, d x` |
| `[[1,2],[3,4]]` | `\begin{pmatrix}1 & 2 \\ 3 & 4\end{pmatrix}` |

---

## 📝 待实现功能

### 功能完善
- [ ] **图像导出** — 函数图像保存为图片
- [ ] **关键点标注** — 自动计算并标注零点、极值点

### UI/UX 优化
- [ ] **移动端触控优化** — 更好的手势支持
- [ ] **横屏自适应** — 横屏时左侧显示公式，右侧键盘
- [ ] **错误位置高亮** — 在输入框中标记语法错误位置
- [ ] **键盘快捷键** — 外接键盘支持
- [ ] **帮助页面** — 功能使用引导

### 个性化设置
- [ ] **主题色自定义** — 可自定义主题色
- [ ] **历史记录导出** — 导出历史记录
- [ ] **精度控制** — 可调节小数保留位数

---

## 📜 设计理念

**精准、锋利、一触即发**

- **简洁小巧** — 不追求大而全，专注于核心计算需求
- **上手容易** — 直观的操作方式，无需学习成本
- **跨平台一致** — 与 Web 端共享计算引擎和渲染逻辑
- **离线优先** — 基础运算无需网络，确保随时可用
