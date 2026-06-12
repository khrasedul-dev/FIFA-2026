@echo off
echo ====================================================
echo Building FIFA 2026 Android IPTV Player APKs...
echo ====================================================

:: Run Gradle clean and assemble tasks
call .\gradlew clean assembleDebug assembleRelease

:: Check if build was successful
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Gradle build failed!
    pause
    exit /b %ERRORLEVEL%
)

:: Create target output directory in the workspace root if it does not exist
if not exist "build_output" mkdir "build_output"

:: Copy debug APK
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "build_output\FIFA2026-debug.apk" >nul
    echo [SUCCESS] Copied debug APK to build_output\FIFA2026-debug.apk
)

:: Copy release APK (signed or unsigned)
if exist "app\build\outputs\apk\release\app-release.apk" (
    copy /Y "app\build\outputs\apk\release\app-release.apk" "build_output\FIFA2026-release.apk" >nul
    echo [SUCCESS] Copied release APK to build_output\FIFA2026-release.apk
) else if exist "app\build\outputs\apk\release\app-release-unsigned.apk" (
    copy /Y "app\build\outputs\apk\release\app-release-unsigned.apk" "build_output\FIFA2026-release.apk" >nul
    echo [SUCCESS] Copied release APK to build_output\FIFA2026-release.apk
)

echo.
echo ====================================================
echo Build Completed Successfully!
echo Opening output folder...
echo ====================================================

:: Open the output folder in Windows Explorer
explorer "build_output"
