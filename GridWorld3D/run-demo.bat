@echo off
setlocal
cd /d "%~dp0"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
call gradlew.bat --project-cache-dir "%GRADLE_USER_HOME%\gridworld-demo-project-cache" -I "%~dp0demo.init.gradle" :desktop:raceDemo %*
exit /b %ERRORLEVEL%
