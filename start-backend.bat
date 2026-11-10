@echo off
title CampKu Backend Server
cd /d "%~dp0backend"

if not exist ".env" (
    echo [0/3] Menyalin konfigurasi .env dari .env.example...
    copy /Y ".env.example" ".env" >nul
)

if not exist "node_modules" (
    echo [1/3] Menginstal dependensi backend (npm install)...
    call npm install
    echo.
)

echo [2/3] Menyiapkan database MySQL...
call npm run db:init
echo.
echo [3/3] Menjalankan server CampKu di http://localhost:3000 ...
call npm start
pause


