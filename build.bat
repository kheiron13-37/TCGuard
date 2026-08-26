@echo off
title MCGamerGuard - Build
color 0a

powershell -ExecutionPolicy Bypass -File "%~dp0build.ps1"

pause
