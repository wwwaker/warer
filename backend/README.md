# Warer Backend

Warer 的符号计算后端，Python + FastAPI + SymPy。为客户端提供本地引擎无法胜任的重运算（积分、微分方程、级数等）。

---

## 功能

| 命令 | 说明 |
| :--- | :--- |
| `diff` / `derivative` | 求导（单变量、多变量） |
| `integrate` / `int` | 不定积分与定积分 |
| `solve` | 解方程（多项式及部分超越方程） |
| `nsolve` | 数值求根，适用于无解析解的方程 |
| `dsolve` | 微分方程 |
| `linsolve` | 线性方程组 |
| `limit` | 极限 |
| `series` / `taylor` | 级数展开与泰勒展开 |
| `simplify` | 代数化简 |
| 矩阵运算 | `det` · `inv` / `inverse` · `transpose` · `rank` · `eigenvals` · `eigenvects` |
| 虚数单位 | `i` / `j` |

其他特性：

- 单一入口 `POST /v1/compute`，**30 秒超时保护**
- 嵌套命令检测，支持复合表达式
- 数值结果智能舍入：识别整数与常见分数，避免出现 `0.3333333333333333`
- 错误返回带类型与位置信息

---

## 启动步骤

### 环境要求

- Python 3.10+

### 安装与运行

```bash
cd backend

python -m venv .venv
source .venv/bin/activate                # Windows: .venv\Scripts\activate

pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

也可以用自带脚本启动：

```bash
./start.sh          # Linux / macOS
start.bat           # Windows
```

### 验证

浏览器打开 `http://localhost:8000/docs` 查看 Swagger UI。

```bash
curl http://localhost:8000/health
```

### 运行测试

```bash
python tests/test_engine.py
```

详见 [tests/README.md](tests/README.md)。

---

## 项目结构

```
backend/
├── app/
│   ├── api/routes.py        API 路由
│   ├── core/engine.py       符号计算引擎封装（分派与结果格式化）
│   ├── models/schemas.py     请求 / 响应模型
│   └── main.py               应用入口
├── tests/                    引擎回归测试
├── requirements.txt
├── start.sh / start.bat
└── README.md
```

---

## API

### `POST /v1/compute`

请求：

```json
{
  "metadata": {
    "client_id": "android",
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

响应：

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

出错时 `error` 不为 `null`，其中包含错误类型与位置信息。

### `GET /health`

健康检查。
