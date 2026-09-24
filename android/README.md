# Warer Android

Android 科学计算器，Kotlin + Jetpack Compose 实现。

---

## 功能

### 二维公式编辑器（实验）

所见即所得的结构化输入，不是"在一行文本里加括号"：

- 支持分式、根式（含 n 次方根）、上下标、括号 / 绝对值、积分、求和、连乘、极限
- 撤销 / 重做、**点击定位**（点公式的哪个位置，光标就到哪个位置）
- 模板键插入后光标自动进入第一个空槽位，"跳到空位"可依次填写
- 空槽位显示灰色占位框；顶部实时显示**"将交给计算引擎的命令"**，输入什么一目了然

入口：侧边菜单 → 开发工具 → **公式编辑器（实验）**

### 混合计算

| 类型 | 执行位置 |
| :--- | :--- |
| 四则、三角函数、对数、幂、分数 | 本地（mXparser，完全离线） |
| 求导、积分、解方程、极限、级数、矩阵 | 云端（SymPy） |

云端不可用时降级本地计算，并明确标注当前模式，不会静默失败。

### 函数绘图

- 四种模式：`y = f(x)`、极坐标 `r = f(θ)`、参数方程 `x(t), y(t)`、隐函数 `f(x, y) = 0`
- 平移 / 缩放 / 重置，多函数同时绘制并可单独显示或隐藏

### 历史记录

- 按计算 / 绘图分类筛选，关键词搜索，按日期（今天 / 昨天 / 本周）分组
- 点击计算记录可恢复输入，点击绘图记录跳转到绘图页

---

## 启动步骤

### 环境要求

| 项 | 要求 |
| :--- | :--- |
| JDK | **21，必须是完整 JDK**（JRE 不含 `jlink`，打包会失败） |
| Android SDK | compileSdk 36 |
| 最低运行版本 | Android 8.0（minSdk 26） |

### 方式一：Android Studio

打开 `android/` 目录 → 同步 Gradle → 连接设备 → 点击运行。

### 方式二：命令行

```bash
cd android

./gradlew :app:assembleDebug      # 构建 debug APK
./gradlew :app:installDebug       # 构建并安装到已连接设备

adb shell am start -n com.wwwaker.warer_android/.MainActivity
```

> Windows 的 cmd / PowerShell 下把 `./gradlew` 写成 `.\gradlew.bat`。

### 运行单元测试

```bash
cd android
./gradlew :core:test
```

`core` 是**零 Android 依赖**的纯 Kotlin 模块，公式排版、编辑逻辑、计算链路全在里面，因此可以跑纯 JVM 测试，**不需要模拟器**，反馈在秒级。

### 常见问题

**打包时 `jlink executable ... does not exist`**
Gradle 守护进程选到了一个 JRE。指定完整 JDK 即可：

```bash
# 方式一：命令行传入
./gradlew :app:assembleDebug -Dorg.gradle.java.home=/path/to/jdk-21

# 方式二：写入用户级配置（一次搞定，推荐）
# ~/.gradle/gradle.properties
org.gradle.java.home=/path/to/jdk-21
```

---

## 项目结构

```
android/
├── core/                          纯 Kotlin 模块，零 Android 依赖
│   ├── engine/                    能力分层与本地数值计算
│   ├── formula/
│   │   ├── model/                 公式 AST
│   │   ├── layout/                排版引擎（em 几何 → 绘制指令）
│   │   ├── edit/                  编辑操作 · 光标定位 · 撤销重做
│   │   └── convert/               AST → 计算引擎命令
│   └── graph/                     绘图模式检测
│
└── app/                           UI 与平台集成
    ├── ui/formula/                公式编辑器渲染与交互
    ├── ui/screen/                 计算 · 绘图 · 历史
    ├── ui/component/              科学键盘 · 结果面板 · 抽屉菜单
    ├── ui/viewmodel/              状态管理
    ├── data/                      Room · DataStore · Retrofit
    └── navigation/                路由
```

---

## 连接后端

符号计算需要后端支持。应用默认连接 `http://121.40.223.203/`，可在侧边菜单中修改服务器地址。

本地启动后端：

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

---

## 技术栈

| 模块 | 选型 |
| :--- | :--- |
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + 单向数据流 |
| 公式排版 | 自研 Compose Canvas 引擎（无 WebView、无 KaTeX） |
| 本地计算 | mXparser |
| 函数绘图 | WebView + function-plot |
| 网络 | Retrofit + OkHttp + kotlinx-serialization |
| 存储 | Room + DataStore Preferences |
| 导航 | Navigation Compose |
| 构建 | AGP 9.1.1 · Kotlin 2.2.10 · Compose BOM 2026.02.01 |

---

## 开发提示

改动排版或编辑逻辑后，先跑 `:core:test`（秒级、无需模拟器）：

```bash
./gradlew :core:test
./gradlew :core:test --tests '*FormulaLayoutTest*'   # 只跑某一类
```
