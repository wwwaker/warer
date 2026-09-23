> [!WARNING]
> **本项目已停止维护（2026-09）**
>
> 代码仅作存档参考，不再更新。

---

# Warer Backend

Warer 后端采用 Python + FastAPI + SymPy 技术栈，提供符号计算能力，支持求导、积分、解方程等高级数学运算。

***

## 🌐 项目导航

| 项目            | 仓库地址                                                           |
| :------------ | :-------------------------------------------------------- |
| **主仓库**       | [warer](https://github.com/wwwaker/warer)                    |
| **Web 前端**    | [warer-web](https://github.com/wwwaker/warer-web)             |
| **Android 端** | [warer-android](https://github.com/wwwaker/warer-android)   |

***

## 🚀 快速开始

### 1. 创建虚拟环境并安装依赖

```bash
python -m venv .venv
# Windows
.venv\Scripts\activate
# macOS / Linux
source .venv/bin/activate

pip install -r requirements.txt
```

### 2. 启动开发服务器

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 3. 验证

浏览器访问 `http://localhost:8000/docs` 查看 API 文档（Swagger UI）。

***

## 🔧 技术栈

- Python 3.10+
- FastAPI
- SymPy（符号计算引擎）
- Uvicorn（ASGI 服务器）

***

## 📁 项目结构

```
app/
├── api/
│   └── routes.py      # API 路由定义
├── core/
│   └── engine.py      # 符号计算引擎封装
├── models/
│   └── schemas.py     # 请求/响应数据模型
└── main.py            # 应用入口
```

***

## 🔌 API 接口

### POST /v1/compute

执行数学表达式计算

**请求体：**

```json
{
  "metadata": {
    "client_id": "web_client",
    "timestamp": 1714982400,
    "session_id": "string"
  },
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

**响应体：**

```json
{
  "status_code": 200,
  "execution_time": "45ms",
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

***

## ✨ 已实现功能

### 数学运算

- ✅ **求导** (`diff`) — 支持单变量和多变量求导
- ✅ **积分** (`integrate`) — 支持不定积分和定积分
- ✅ **解方程** (`solve`) — 支持多项式和部分超越方程
- ✅ **化简** (`simplify`) — 代数表达式化简
- ✅ **虚数计算** — 支持 `i` / `j` 作为虚数单位

### 系统特性

- ✅ **嵌套命令检测** — 支持复合表达式计算，30s 超时保护
- ✅ **浮点精度优化** — 智能舍入，常见分数/整数识别
- ✅ **错误处理** — 详细的错误类型和位置信息

***

## 📝 待实现功能

- [ ] **`nsolve`** — 数值求根（适用于无解析解的方程）
- [ ] **矩阵运算** — 矩阵创建、运算和求逆
- [ ] **微分方程** — `dsolve` 求解
- [ ] **方程组求解** — `linsolve` / `nonlinsolve`
- [ ] **图像分析** — 零点、极值点计算（用于前端标注）
- [ ] **缓存策略** — Redis 缓存重复计算结果

***

## 📜 设计理念

**精准、锋利、一触即发**

- **离线优先**：前端优先使用本地引擎，复杂计算才调用云端
- **统一协议**：与 Web、Android 端共享统一的数据协议
- **轻量高效**：专注核心计算能力，避免功能冗余

***

