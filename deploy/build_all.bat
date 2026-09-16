@echo off
chcp 65001 > nul
echo ==============================================================================
echo WireForge 全自动编译打包脚本 (Windows)
echo ==============================================================================

set ROOT_DIR=%~dp0..
cd /d "%ROOT_DIR%"

echo.
echo [1/3] 正在打包前端静态资源 (wireforge-frontend)...
cd wireforge-frontend
call npm run build
if %errorlevel% neq 0 (
    echo [错误] 前端打包失败！
    pause
    exit /b %errorlevel%
)
echo [成功] 前端打包完成，产物位于 wireforge-frontend/dist

echo.
echo [2/3] 正在编译打包后端可执行 Jar (wireforge-backend)...
cd /d "%ROOT_DIR%\wireforge-backend"
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo [错误] 后端编译打包失败！
    pause
    exit /b %errorlevel%
)
echo [成功] 后端打包完成，产物位于 wireforge-backend/target/wireforge-backend-0.1.0.jar

echo.
echo [3/3] 汇总交付物到 release 目录...
cd /d "%ROOT_DIR%"
if not exist release mkdir release
if not exist release\frontend mkdir release\frontend
xcopy /E /I /Y wireforge-frontend\dist release\frontend > nul
copy /Y wireforge-backend\target\wireforge-backend-*.jar release\ > nul
copy /Y sql\01_schema.sql release\ > nul
copy /Y init.sql release\02_init_demo_data.sql > nul

echo.
echo ==============================================================================
echo [大功告成] 交付产物已成功生成并归集至: %ROOT_DIR%\release
echo - 后端 Jar 包: release\wireforge-backend-0.1.0.jar
echo - 前端静态资源: release\frontend
echo - 数据库全套 SQL: release\01_schema.sql, release\02_init_demo_data.sql
echo ==============================================================================
pause
