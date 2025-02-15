@echo off
setlocal enabledelayedexpansion

echo Starting Vosk Chinese model download...
echo Current directory: %CD%

:: Create models directory
echo Creating model directory...
if not exist vosk-model-cn (
    mkdir vosk-model-cn
    if errorlevel 1 (
        echo Failed to create directory
        pause
        exit /b 1
    )
)

:: Download directly from Vosk's GitHub repository
echo Downloading model file (about 40MB)...
curl -# -L "https://github.com/alphacep/vosk-api/releases/download/model-small-cn-0.22/vosk-model-small-cn-0.22.zip" -o vosk-model-small-cn-0.22.zip
if errorlevel 1 (
    echo Download failed
    pause
    exit /b 1
)

:: Extract using PowerShell (most reliable method)
echo Extracting model files...
powershell -command "& {Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('vosk-model-small-cn-0.22.zip', 'vosk-model-cn')}"
if errorlevel 1 (
    echo Extraction failed
    pause
    exit /b 1
)

:: Move files from nested directory if needed
if exist "vosk-model-cn\vosk-model-small-cn-0.22" (
    echo Moving files from nested directory...
    xcopy /E /Y "vosk-model-cn\vosk-model-small-cn-0.22\*" "vosk-model-cn\"
    rmdir /S /Q "vosk-model-cn\vosk-model-small-cn-0.22"
)

:: Clean up
echo Cleaning up...
del vosk-model-small-cn-0.22.zip
if errorlevel 1 (
    echo Warning: Failed to clean up zip file
)

:: Verify files
echo Verifying model files...
if not exist "vosk-model-cn\am" (
    echo Error: Missing model files
    pause
    exit /b 1
)

echo.
echo Model download and extraction completed successfully!
echo Model files are in: %CD%\vosk-model-cn
dir vosk-model-cn
echo.
pause
