@echo off
setlocal
chcp 65001 >nul
title Minecraft Server - Potato Minigames

set "MC_RUNTIME_HOME=%~dp0"
if exist "%~dp0mc_minigames\.tools\server\asp-server.jar" set "MC_RUNTIME_HOME=%~dp0mc_minigames\"
if defined MINIGAMES_HOME set "MC_RUNTIME_HOME=%MINIGAMES_HOME%\"
set "MC_SERVER_DIR=%MC_RUNTIME_HOME%.tools\server"
set "MC_JAVA=%MC_RUNTIME_HOME%.tools\jdk25\jdk-25.0.5.0.3\bin\java.exe"

if not exist "%MC_JAVA%" (
    echo Java 25 was not found: "%MC_JAVA%"
    echo Keep this launcher next to mc_minigames, or set MINIGAMES_HOME to the checkout containing .tools.
    pause
    exit /b 1
)
if not exist "%MC_SERVER_DIR%\asp-server.jar" (
    echo Server was not found: "%MC_SERVER_DIR%\asp-server.jar"
    pause
    exit /b 1
)

set "MC_SERVER_PORT=25565"
for /f "usebackq tokens=1,* delims==" %%A in ("%MC_SERVER_DIR%\server.properties") do (
    if /i "%%A"=="server-port" set "MC_SERVER_PORT=%%B"
)
powershell.exe -NoLogo -NoProfile -Command "try { $port = [int]$env:MC_SERVER_PORT; $busy = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -eq $port; if ($busy) { exit 10 }; exit 0 } catch { exit 1 }"
if errorlevel 10 (
    echo Port %MC_SERVER_PORT% is already in use. The server may already be running.
    echo A second instance will not start. Try connecting to localhost:%MC_SERVER_PORT%.
    pause
    exit /b 1
)
if errorlevel 1 (
    echo Could not check port %MC_SERVER_PORT%. Startup was cancelled to avoid a duplicate server.
    pause
    exit /b 1
)

pushd "%MC_SERVER_DIR%"
if errorlevel 1 (
    echo Could not open the server folder.
    pause
    exit /b 1
)
echo Starting Minecraft Java 26.3 with up to 2 GB of memory.
echo Wait for Done. To shut down, type stop and press Enter.
echo Keep this window open while playing.
echo.
"%MC_JAVA%" -Xms512M -Xmx2G -Dterminal.jline=false -Dterminal.ansi=false -jar asp-server.jar nogui
set "MC_EXIT_CODE=%ERRORLEVEL%"
popd
echo.
echo Server stopped. Exit code: %MC_EXIT_CODE%.
pause
exit /b %MC_EXIT_CODE%
