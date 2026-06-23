# Warer Android

> 科学计算器 Android 客户端 — Kotlin + Jetpack Compose + Material3

与 Web 端共享"本地数值 + 云端符号"混合架构，支持实时 LaTeX 预览与函数绘图。

---

## 🌐 项目导航

| 项目 | 仓库地址 |
| :--- | :--- |
| **Web 前端** | [warer-web](https://github.com/wwwaker/warer-web) |
| **后端** | [warer](https://github.com/wwwaker/warer) |
| **Android 端** | [warer-android](https://github.com/wwwaker/warer-android) |

---

## 功能

- **混合计算** — 数值运算本地执行（mXparser），符号计算（微分/积分/求解）切换云端，离线自动降级
- **实时 LaTeX 预览** — 递归下降解析器，支持分数、幂次、函数参数、矩阵等
- **函数绘图** — 线性 / 极坐标 / 参数方程 / 隐函数四种模式，多函数管理
- **历史记录** — Room 持久化，按日期分组，支持搜索与类型筛选，点击可恢复计算或跳转绘图
- **函数模板面板** — 内置求导、积分、求解、矩阵等预设模板

## 技术栈

| 模块 | 选型 |
| :--- | :--- |
| UI/架构 | Jetpack Compose + Material3 / MVVM (StateFlow) |
| 网络 | Retrofit + OkHttp + kotlinx-serialization |
| 存储 | Room + DataStore |
| 本地计算 | mXparser |
| 公式渲染 | WebView + KaTeX |
| 导航 | Navigation Compose |

环境：minSdk 26 / targetSdk 36 / Kotlin 2.2.10 / Compose BOM 2026.02.01

## 快速开始

```bash
cd android
./gradlew assembleDebug        # 构建 APK
./gradlew installDebug         # 安装到设备
```

或使用 Android Studio 打开 `android/` 目录直接运行。

## 项目结构

```
data/
├── api/          # Retrofit 网络层
├── db/           # Room 数据库
├── engine/       # GraphDetector + LatexParser
└── settings/     # DataStore
ui/
├── screen/       # 计算器 / 绘图 / 历史页面
├── component/    # 输入面板、键盘、KaTeX/Graph WebView 等
├── viewmodel/    # CalculatorViewModel + HistoryViewModel
└── theme/        # Material3 主题
navigation/       # NavGraph + Screen 路由
```

## 开发

默认后端地址 `http://121.40.223.203/`，可在侧边菜单修改。本地启动后端：

```bash
cd backend && pip install -r requirements.txt && uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```
