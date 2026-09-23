# Warer 项目规格说明书

**项目愿景：** 我是一名本科生个人开发者，想做一个不一样的科学计算器。市面上的专业软件功能虽然强大，但打开总让人望而生畏；系统自带计算器又太简陋。Warer 追求的是——精准、锋利、一触即发，而不是"多功能工具箱"式的臃肿。它应该像一把小刀，随身携带，随手可用。

**设计理念：** 简洁小巧，上手容易。在移动端也要好用，满足中学生到大学生的一般数学需求。四个关键词：**中学生到大学生**、**简洁小巧**、**跨平台**、**好上手**。

> 技术架构："离线基础运算 + 云端符号引擎"混合架构。
>
> **项目状态：** Web MVP 已基本完成，后端成熟稳定；Android 端从零重建中。

---

## 一、架构总览

### 1.1 混合计算路由

```
用户输入 → 前端 Dispatcher
               ├─ 含符号/命令关键词? → 云端 SymPy (POST /v1/compute)
               └─ 纯数值运算? → 本地引擎求值
                                       ↓
                               云端失败且本地可算 → 降级到本地
```

Web 端使用 Math.js 做本地引擎，Android 端使用 mXparser（规划）。两端 `needsCloud()` 逻辑保持 1:1 一致。

### 1.2 技术栈现状

| 模块 | 技术选型 | 状态 |
|------|----------|------|
| **后端** | Python 3.10+ / FastAPI / SymPy / Uvicorn | 成熟，持续迭代 |
| **Web 前端** | React 19 / TypeScript / Vite / Math.js / KaTeX / function-plot / Zustand | MVP 完成 |
| **Android** | Kotlin / Jetpack Compose / Material3 / Retrofit / mXparser（规划） | **从零重建中** |
| **通信协议** | RESTful JSON | 已固化 |

---

## 二、后端 API 契约

### 2.1 `POST /v1/compute`

**请求：**

```json
{
  "payload": {
    "expression": "diff(x^2 + sin(x), x)",
    "engine_hint": "auto",
    "output_config": {
      "format": "latex",
      "precision": 15,
      "simplify": true
    }
  }
}
```

**成功响应 (200)：**

```json
{
  "status_code": 200,
  "execution_time": "12.3ms",
  "result": {
    "is_symbolic": true,
    "main_display": "2x + \\cos(x)",
    "plain_text": "2*x + cos(x)",
    "numeric_approximation": null,
    "variables": ["x"]
  },
  "error": null
}
```

**错误响应 (400)：**

```json
{
  "status_code": 400,
  "execution_time": "3.1ms",
  "result": null,
  "error": {
    "type": "SyntaxError",
    "message": "括号不匹配：第 12 字符处的圆括号未闭合",
    "position": null
  }
}
```

### 2.2 `GET /health`

```json
{ "status": "ok" }
```

---

## 三、后端能力矩阵

| 功能 | 命令 | 说明 |
|------|------|------|
| **求导** | `diff(expr, var)` / `derivative(expr, var)` | 一阶导数 |
| **不定积分** | `integrate(expr, var)` | 原函数 |
| **定积分** | `integrate(expr, var, lower, upper)` | 含瑕积分 |
| **解方程** | `solve(expr, var)` / `solve(expr)` | 多项式 + 超越方程 |
| **数值求根** | `nsolve(expr, var, guess)` | 无解析解时使用 |
| **微分方程** | `dsolve(eq, f(x))` | ODE 解析解 |
| **线性方程组** | `linsolve([eqs], [vars])` | 唯一解/无穷解/无解 |
| **求极限** | `limit(expr, var, point)` | 含方向参数 |
| **级数展开** | `series(expr, var, point, n)` / `taylor(...)` | |
| **化简** | `simplify(expr)` / 纯表达式自动化简 | |
| **矩阵** | `det` / `inv` / `transpose` / `eigenvals` / `eigenvects` / `rank` / 矩阵算术 | |
| **超时保护** | — | 30s 自动终止 |

> `diff(solve(...))` 等嵌套命令显式拒绝，提示分步计算。错误信息全部中文。

---

## 四、表达式三态转换

用户输入、SymPy、KaTeX 三者语法各异，各端都有预处理逻辑：

| 用户输入 | SymPy (后端) | KaTeX (显示) |
|----------|-------------|--------------|
| `sin(x)^2` | `sin(x)**2` | `\sin^2(x)` |
| `2x^2` | `2*x**2` | `2x^{2}` |
| `ln(x)` | `log(x)` | `\ln(x)` |
| `3/4` | `3/4` | `\frac{3}{4}` |

**各端预处理职责：**
- 后端 `engine.py#_preprocess()`: `^`→`**`, `ln`→`log`, 数字/括号后插入 `*`, `sin(x)^2`→`(sin(x))**2`
- Web 端 `localEngine.ts#preprocessImplicitMultiplication()`: 词法分析器插入隐式乘号
- Android 端 `Transpiler.kt`: 待实现，与 Web 端逻辑 1:1 对齐

---

## 五、Web 前端结构

```
src/
├── App.tsx                          # 多栏卡片布局 + 拖拽
├── main.tsx                         # React 入口
├── engine/
│   ├── dispatcher.ts                # 混合计算路由核心
│   ├── localEngine.ts               # Math.js 本地计算 + 智能舍入
│   ├── cloudEngine.ts               # API fetch 封装
│   ├── latexPreview.ts              # 词法分析器 → 递归下降 → LaTeX
│   └── graphDetection.ts            # 表达式 → 图像类型检测
├── store/
│   └── calculatorStore.ts           # Zustand 全局状态
├── components/
│   ├── CalculatorTab.tsx             # 计算器卡片（输入/预览/结果/模板/图像路由）
│   ├── Keyboard.tsx                  # 科学键盘
│   ├── GraphTab.tsx                  # 函数绘图（4 模式）
│   ├── HistoryTab.tsx                # 计算历史（搜索/回填）
│   └── KatexRenderer.tsx            # KaTeX 封装
```

---

## 六、Android 重构规划

旧版 `android/` 代码已废弃。新 Android 端从零构建，目标：

1. **minSdk = 26** (Android 8.0) — 覆盖 98%+ 活跃设备
2. **MVVM + StateFlow + Compose + Material3**
3. **WebView 隔离渲染**公式 (KaTeX) 和图像 (function-plot)，跨端显示一致
4. **混合路由**与 Web 端 `dispatcher.ts` 算法保持 1:1

详细计划见 `docs/ANDROID_DEV_PLAN.md`。

---

## 设计原则

1. **精确而非全能** — 不追求 Mathematica 级别的覆盖，而是确保基础运算精准、响应迅速
2. **离线优先** — 能本地算的不走网络，弱网下有尊严地降级
3. **跨端一致** — 同一表达式在三端给出相同结果和相同 LaTeX 显示
4. **中文优先** — 所有用户面向提示、错误信息均采用中文
