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

echo [1/3] Installing dependencies...
pip install -r requirements.txt pyinstaller
if errorlevel 1 (
    echo [ERROR] Failed to install dependencies.
    pause
    exit /b 1
)

echo.
echo [2/3] Building MagicCamera.exe ...
pyinstaller --onefile --noconsole --name MagicCamera ^
    --add-data "%LOCALAPPDATA%\Programs\Python\Python312\Lib\site-packages\mediapipe;mediapipe" ^
    main.py
if errorlevel 1 (
    echo.
    echo [NOTE] If mediapipe path failed, trying auto-detect...
    for /f "tokens=*" %%i in ('python -c "import mediapipe, os; print(os.path.dirname(mediapipe.__file__))"') do set MP_PATH=%%i
    pyinstaller --onefile --noconsole --name MagicCamera ^
        --add-data "%MP_PATH%;mediapipe" ^
        main.py
)

echo.
echo [3/3] Done!
echo.
echo ========================================
echo   MagicCamera.exe is in: dist\MagicCamera.exe
echo ========================================
echo.
pause
