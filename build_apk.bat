@echo off
chcp 65001 >nul
set JAVA_HOME=D:\Android\jbr
set ANDROID_HOME=C:\Users\Administrator\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

cd /d D:\claude\LianYu-app
echo Building optimized APK...
call gradlew.bat :app:assembleDebug --no-daemon
echo Exit code: %ERRORLEVEL%
if %ERRORLEVEL% equ 0 (
    echo Installing...
    copy /Y app\build\outputs\apk\debug\app-debug.apk C:\Users\Administrator\Desktop\LianYu.apk
    adb install -r -d C:\Users\Administrator\Desktop\LianYu.apk
)
pause
