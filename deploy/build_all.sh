#!/usr/bin/env bash
set -e

echo "=============================================================================="
echo "WireForge 全自动编译打包脚本 (Linux/macOS)"
echo "=============================================================================="

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo ""
echo "[1/3] 正在打包前端静态资源 (wireforge-frontend)..."
cd "$ROOT_DIR/wireforge-frontend"
npm run build
echo "[成功] 前端打包完成，产物位于 wireforge-frontend/dist"

echo ""
echo "[2/3] 正在编译打包后端可执行 Jar (wireforge-backend)..."
cd "$ROOT_DIR/wireforge-backend"
mvn clean package -DskipTests
echo "[成功] 后端打包完成，产物位于 wireforge-backend/target/wireforge-backend-0.1.0.jar"

echo ""
echo "[3/3] 汇总交付物到 release 目录..."
cd "$ROOT_DIR"
mkdir -p release/frontend
cp -r wireforge-frontend/dist/* release/frontend/
cp wireforge-backend/target/wireforge-backend-*.jar release/
cp sql/01_schema.sql release/
cp init.sql release/02_init_demo_data.sql

echo ""
echo "=============================================================================="
echo "[大功告成] 交付产物已成功生成并归集至: $ROOT_DIR/release"
echo "- 后端 Jar 包: release/wireforge-backend-0.1.0.jar"
echo "- 前端静态资源: release/frontend"
echo "- 数据库全套 SQL: release/01_schema.sql, release/02_init_demo_data.sql"
echo "=============================================================================="
