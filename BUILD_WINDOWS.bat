@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat build
if errorlevel 1 (
  echo Build failed. Check the error above and install JDK 17 if Java is missing.
  pause
  exit /b 1
)
echo Built (put the jar without "-sources" in mods):
dir /b build\libs\botand-*.jar
pause
