# Warer

数学计算器项目。仓库包含 Android 客户端与可选的符号计算后端。

---

## 仓库结构

| 目录 | 内容 |
| :--- | :--- |
| [`android/`](android/) | Android 客户端（Kotlin + Jetpack Compose）—— **主要交付物** |
| [`backend/`](backend/) | 符号计算后端（Python + FastAPI + SymPy）—— 仅符号运算需要 |

---

## 功能概览

### Android 客户端

- **二维公式编辑器（实验）** — 所见即所得的结构化输入，不是"在一行文本里加括号"。支持分式、根式、上下标、括号 / 绝对值、积分、求和、连乘、极限；具备撤销 / 重做、点击定位
- **混合计算** — 四则 / 三角 / 对数 / 幂等基础运算**本地离线**执行（mXparser）；求导、积分、解方程、极限、矩阵等符号运算自动切换**云端**（SymPy）
- **函数绘图** — `y = f(x)`、极坐标 `r = f(θ)`、参数方程、隐函数四种模式，平移 / 缩放 / 多函数叠加
- **历史记录** — 分类筛选、关键词搜索、日期分组、点击回溯

### 后端（仅符号运算需要）

基于 SymPy 的符号计算，供客户端处理本地引擎无法胜任的重运算：

`diff` · `integrate` · `solve` / `nsolve` · `dsolve` · `linsolve` · `limit` · `series` / `taylor` · `simplify` · 矩阵运算（`det` / `inv` / `rank` / 特征值 …）

单入口 `POST /v1/compute`，30 秒超时保护。

> 客户端不依赖后端也能正常启动与使用（基础运算完全本地化）；只有求导、积分、解方程等符号运算需要后端在线。

---

## 快速开始

### Android 客户端

```bash
cd android

./gradlew :app:assembleDebug      # 构建 debug APK
./gradlew :app:installDebug       # 构建并安装到已连接设备
```

前置条件：

- **完整 JDK 21** —— JRE 不含 `jlink`，打包会以 `jlink executable ... does not exist` 失败
- Android SDK（compileSdk 36）
- Windows 下把 `./gradlew` 写成 `.\gradlew.bat`

详见 [android/README.md](android/README.md)。

### 后端

```bash
cd backend

python -m venv .venv
source .venv/bin/activate                # Windows: .venv\Scripts\activate

pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

验证：浏览器打开 `http://localhost:8000/docs`。

详见 [backend/README.md](backend/README.md)。

---

## 运行测试

```bash
# 公式排版 / 编辑逻辑 / 计算链路：纯 JVM，无需模拟器，秒级反馈
cd android && ./gradlew :core:test

# 符号计算引擎回归测试
cd backend && python tests/test_engine.py
```

---

## 技术栈

| | |
| :--- | :--- |
| Android | Kotlin · Jetpack Compose · Material3 · mXparser · Room · DataStore · Retrofit |
| 后端 | Python 3.10+ · FastAPI · SymPy · Uvicorn |
