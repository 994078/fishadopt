@echo off
chcp 65001 >nul
echo ==============================================
echo   One-click push "FishAdopt" to GitHub
echo   yi-jian-ling-yang-xiao-fei-yu
echo ==============================================
echo.

rem ---- 1. Check git ----
where git >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Git not found.
  echo Install from: https://git-scm.com/download/win
  echo Then run this script again.
  pause
  exit /b 1
)

rem ---- 2. Ask for GitHub username / repo ----
set /p GH_USER=Your GitHub username (e.g. zhangsan): 
if "%GH_USER%"=="" (
  echo [ERROR] Username is required.
  pause
  exit /b 1
)

set /p GH_REPO=Repo name [default: fishadopt]: 
if "%GH_REPO%"=="" set GH_REPO=fishadopt

rem ---- 3. Go to this folder ----
cd /d "%~dp0"

rem ---- 4. git init (if not yet) ----
if not exist ".git" git init

rem ---- 5. Set local commit identity (does not touch global config) ----
git config user.name "%GH_USER%"
git config user.email "%GH_USER%@users.noreply.github.com"

rem ---- 6. add + commit ----
git add .
git commit -m "FishAdopt v1" 2>nul

rem ---- 7. main branch ----
git branch -M main

rem ---- 8. set remote + push ----
git remote remove origin >nul 2>nul
git remote add origin "https://github.com/%GH_USER%/%GH_REPO%.git"

echo.
echo Pushing to https://github.com/%GH_USER%/%GH_REPO%.git
echo (First push opens a browser to sign in to GitHub - click Authorize.)
echo.
git push -u origin main

echo.
echo ==============================================
echo DONE! Next steps:
echo   Open https://github.com/%GH_USER%/%GH_REPO%/actions
echo   Click "Build Android APK" - "Run workflow" - "Run workflow"
echo   Wait, then download the "apk" artifact.
echo ==============================================
echo.
pause
