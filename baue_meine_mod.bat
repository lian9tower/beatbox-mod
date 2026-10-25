@echo off
echo Starte Mod-Bau...
./gradlew build
if %errorlevel% neq 0 (
    gradlew build
)
pause
