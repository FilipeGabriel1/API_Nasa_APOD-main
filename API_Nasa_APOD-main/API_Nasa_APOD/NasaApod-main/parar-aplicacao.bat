@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Carrega variáveis do arquivo .env (se existir)
if exist .env (
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        set "k=%%A"
        if not "!k!"=="" if /i not "!k:~0,1!"=="#" (
            set "%%A=%%B"
        )
    )
)

set "PORT_TO_CHECK=%SERVER_PORT%"
if "%PORT_TO_CHECK%"=="" set "PORT_TO_CHECK=8081"

echo Procurando processos na porta %PORT_TO_CHECK%...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%PORT_TO_CHECK%') do (
    echo Encerrando processo %%a...
    taskkill /PID %%a /F
)
echo Pronto! Porta %PORT_TO_CHECK% liberada.
pause


