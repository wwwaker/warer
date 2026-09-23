# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Warer is a scientific calculator with a "local numeric + cloud symbolic" hybrid architecture. Three targets: Web (React 19), Backend (FastAPI/SymPy), Android (Kotlin/Compose).

## Backend (Python/FastAPI/SymPy)

- **Run dev server**: `uvicorn app.main:app --reload --host 0.0.0.0 --port 8000` (or `start.sh`/`start.bat`)
- **Run tests**: `cd backend && python -m tests.test_engine`
- **Dependencies**: `pip install -r requirements.txt` (fastapi, sympy, uvicorn, pydantic)
- **Files**: `app/main.py` (app entry + `/health` endpoint), `app/api/routes.py` (`POST /v1/compute`), `app/core/engine.py` (SympyEngine), `app/models/schemas.py` (Pydantic models)
- **API**: `POST /v1/compute` with `{ payload: { expression, engine_hint, output_config } }` → `{ result: { main_display (LaTeX), plain_text, is_symbolic, numeric_approximation, variables }, error: { type, message, position } }`
- **Computation flow**: `process_expression()` → `_pre_check_expr()` (bracket/operator validation) → `_dispatch()` (command routing → pattern-matched handlers like `_handle_solve`, `_handle_matrix_op`, `_try_matrix_expression`) → SymPy eval
- **Supported commands**: `diff/derivative`, `integrate` (indefinite/definite), `solve`, `nsolve`, `dsolve`, `linsolve`, `limit`, `series/taylor`, `simplify`, matrix ops (`det/inv/transpose/eigenvals/eigenvects/rank`), matrix arithmetic (`+`, `-`, `*`, `^`)
- **Dispatch order**: matrix commands → matrix arithmetic → matrix literal → dsolve → linsolve → limit → series → nsolve → solve → definite integral → diff/integrate (2-arg) → simplify → auto_simplify
- **Timeout**: 30s via `ThreadPoolExecutor`
- **Error handling**: Chinese error messages via `_friendly_math_error()` mapping specific SymPy exceptions; bracket validation in `_validate_brackets()`; trailing-operator and duplicate-operator checks in `_pre_check_expr()`
- **Expression preprocess**: `^` → `**`, `ln` → `log`, insert `*` between digit/letter, `)`→digit/letter implicit multiply, `sin(x)^2` → `(sin(x))^2`
- **Nested commands** (e.g. `diff(solve(...))`) explicitly rejected — user must compute step by step

## Web Frontend (React 19/TypeScript/Vite)

- **Run dev**: `cd web && npm run dev` (default http://localhost:5173)
- **Build**: `npm run build` (runs `tsc -b && vite build`)
- **Lint**: `npm run lint`
- **Key deps**: react 19, mathjs (local engine), katex (formula render), function-plot (graphing), zustand (state), d3 (function-plot dep), vite 8

### Architecture

```
src/
├── engine/
│   ├── dispatcher.ts       # Hybrid routing: needsCloud() check → cloud API; fallback to local on error. AbortController for cancellation.
│   ├── localEngine.ts      # Math.js evaluate + smart rounding to common fractions (1/2..1/12) within 1e-10 tolerance
│   ├── cloudEngine.ts      # fetch() wrapper for POST /v1/compute
│   ├── latexPreview.ts     # Tokenizer + recursive-descent parser → KaTeX string. solve/linsolve get special `= 0`/cases rendering.
│   └── graphDetection.ts   # Expression → graph mode detection (linear/polar/parametric/implicit)
├── store/
│   └── calculatorStore.ts  # Zustand: flat state, cards in columns with drag-and-drop, history as `calculation`|`graph` entries
├── components/
│   ├── CalculatorTab.tsx    # Input, KaTeX preview, result display, bracket auto-fix, plot routing
│   ├── GraphTab.tsx         # Lazy-loaded function-plot, 4 modes, zoom/pan, SVG→Canvas PNG export
│   ├── HistoryTab.tsx       # Filter/search history, click to restore input
│   ├── Keyboard.tsx         # Scientific calculator keyboard
│   └── KatexRenderer.tsx   # KaTeX component wrapper
├── App.tsx                  # Multi-column card layout with drag-and-drop + resizable columns
└── main.tsx
```

- **needsCloud**: true if symbolic command keywords (`diff/solve/integrate/...`), matrix literals (`[[...]]`), or standalone variables (not `pi`/`e`)
- **VITE_API_BASE**: env var (default `http://localhost:8000`). Vite dev proxies `/v1` to it.

## Android (Kotlin/Jetpack Compose)

- **Open in Android Studio** — builds with Gradle. `minSdk 26`, `compileSdk 36`, `targetSdk 36`
- **Build APK**: `cd android && ./gradlew assembleDebug`
- **Key deps**: Retrofit+OkHttp (networking), kotlinx-serialization (JSON), Room (DB), DataStore (settings), mXparser (local compute), Navigation Compose, WebView-based KaTeX rendering
- **Architecture**: MVVM — `CalculatorViewModel` + `HistoryViewModel`, `StateFlow` UI state, single-activity with Navigation Compose

### Structure

```
com.wwwaker.warer_android/
├── data/
│   ├── api/         # RetrofitClient (singleton, dynamic baseUrl), ApiService (POST /v1/compute + GET /health), ApiModels
│   ├── db/          # Room: HistoryEntity, HistoryDao, AppDatabase
│   ├── engine/      # GraphDetector (linear/polar/parametric/implicit detection)
│   └── settings/    # SettingsManager (DataStore: baseUrl, themeMode, precision, haptic)
├── ui/
│   ├── screen/      # CalculatorScreen, GraphScreen (WebView-based function plot), HistoryScreen, MainScaffold
│   ├── component/   # InputPanel, ResultPanel (KaTeX WebView), ScientificKeyboard, KatexWebView, GraphWebView, DrawerMenu, FunctionPanel
│   ├── viewmodel/   # CalculatorViewModel, HistoryViewModel
│   └── theme/       # Color, Type, Theme (Material3 light/dark)
├── navigation/      # NavGraph, Screen sealed class
├── MainActivity.kt
└── WarerApplication.kt
```

### Key patterns

- **Hybrid compute**: `CalculatorViewModel.compute()` → `needsCloud()` check → local (mXparser) for simple arithmetic → cloud (Retrofit) for symbolic → on cloud failure, falls back to local or shows network error
- **Local engine** (mXparser): smart rounding to common fractions, implicit multiplication preprocessing, same logic as web-localEngine
- **Base URL**: user-configurable in DrawerMenu → saved to DataStore → `RetrofitClient.updateBaseUrl()`. Default from `BuildConfig.DEFAULT_API_BASE`. **Network security config** (`res/xml/network_security_config.xml`) uses `<base-config cleartextTrafficPermitted="true" />` to allow any HTTP backend URL.
- **KaTeX rendering**: via WebView loading local HTML with KaTeX — cross-platform consistency with web target
- **Graph**: WebView-based with function-plot JS library
- **History**: SQLite via Room, with local/cloud source tracking and error storage

## Nginx Deployment

- **Config**: `/etc/nginx/sites-available/calbackend` — proxies `/v1/` and `/health` to backend on `127.0.0.1:8000`, SPA fallback for frontend
- **Backend systemd service**: `calbackend.service` — runs `uvicorn app.main:app --host 127.0.0.1 --port 8000`, logs to `/var/www/logs/backend.log`
- **Update backend**: `cd /var/www/calbackend && git pull && systemctl restart calbackend`
- **Update frontend**: `cd /var/www/calweb && git pull && npm install && npm run build`

## General

- All user-facing text is in Chinese
- Three syntax domains (user input, SymPy, KaTeX) — each target has preprocessing to convert between them
- Matrix literals use `[[row1],[row2]]` notation consistently across all endpoints
