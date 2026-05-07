# Warer

简洁易用的科学计算器后端 —— 轻量上手，满足中学到大学的一般数学需求。

## 技术栈

- Python 3.10+
- FastAPI
- SymPy
- Uvicorn

## 快速开始

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
uvicorn app.main:app --reload --port 8000
```

### 3. 验证

浏览器访问 `http://localhost:8000/docs` 查看 API 文档（Swagger UI）。

## API

### POST /v1/compute

请求体：

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

响应体：

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
