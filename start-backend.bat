@echo off
title CampKu Backend Server
cd /d "%~dp0backend"
echo [1/2] Menyiapkan database MySQL...
call npm run db:init
echo.
echo [2/2] Menjalankan server CampKu di http://localhost:3000 ...
call npm start
pause

