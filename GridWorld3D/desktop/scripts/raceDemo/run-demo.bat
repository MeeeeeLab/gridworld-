@echo off
setlocal
cd /d "%~dp0"
set "JAVA_BIN=java"
if defined JAVA_HOME set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
"%JAVA_BIN%" -cp "lib/*" RaceLauncher %*
exit /b %ERRORLEVEL%
