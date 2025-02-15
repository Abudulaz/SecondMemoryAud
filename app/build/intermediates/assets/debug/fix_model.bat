@echo off
echo Fixing model directory structure...

cd vosk-model-cn

:: Move files from nested directory if they exist
if exist "vosk-model-small-cn-0.22\am" (
    echo Moving files from nested directory...
    xcopy /E /Y /I "vosk-model-small-cn-0.22\*" "."
    rmdir /S /Q "vosk-model-small-cn-0.22"
)

:: Verify directory structure
echo Verifying directory structure...
if not exist "am\final.mdl" (
    echo Error: Missing acoustic model
    exit /b 1
)
if not exist "conf\model.conf" (
    echo Error: Missing configuration
    exit /b 1
)
if not exist "graph\Gr.fst" (
    echo Error: Missing language model
    exit /b 1
)
if not exist "ivector\final.ie" (
    echo Error: Missing ivector
    exit /b 1
)

echo Model structure verified successfully:
dir /s
echo.
pause
