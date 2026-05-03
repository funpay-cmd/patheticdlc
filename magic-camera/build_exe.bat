@echo off
echo ========================================
echo   Magic Camera - Building .exe
echo ========================================
echo.

:: Check Python
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python not found! Install Python 3.9+ from https://python.org
    echo Make sure to check "Add Python to PATH" during installation.
    pause
    exit /b 1
)

echo [1/4] Installing dependencies...
python -m pip install -r requirements.txt pyinstaller
if errorlevel 1 (
    echo [ERROR] Failed to install dependencies.
    pause
    exit /b 1
)

echo.
echo [2/4] Detecting mediapipe path...
for /f "tokens=*" %%i in ('python -c "import mediapipe, os; print(os.path.dirname(mediapipe.__file__))"') do set MP_PATH=%%i

if "%MP_PATH%"=="" (
    echo [ERROR] Could not find mediapipe. Make sure it is installed.
    pause
    exit /b 1
)

echo    Found mediapipe at: %MP_PATH%
echo.
echo [3/4] Building MagicCamera.exe (this may take a few minutes)...
python -m PyInstaller --onefile --noconsole --name MagicCamera ^
    --add-data "%MP_PATH%;mediapipe" ^
    main.py

if errorlevel 1 (
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

echo.
echo [4/4] Done!
echo.
echo ========================================
echo   MagicCamera.exe is in: dist\MagicCamera.exe
echo ========================================
echo.
pause
