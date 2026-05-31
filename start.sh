#!/bin/bash
echo "Starting MaWaker Backend Server..."
echo "Listening on http://0.0.0.0:8000"
echo ""
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
