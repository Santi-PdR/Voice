@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=8.3"
set "BOOTSTRAP_ROOT=%USERPROFILE%\.gradle\rafael-bootstrap"
set "GRADLE_HOME=%BOOTSTRAP_ROOT%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
    echo [Rafael Build] Gradle %GRADLE_VERSION% no encontrado. Descargando distribucion oficial...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $version='%GRADLE_VERSION%'; $root='%BOOTSTRAP_ROOT%'; $zip=Join-Path $env:TEMP ('gradle-' + $version + '-bin.zip'); $url='https://services.gradle.org/distributions/gradle-' + $version + '-bin.zip'; New-Item -ItemType Directory -Force -Path $root | Out-Null; Invoke-WebRequest -Uri $url -OutFile $zip; if (Test-Path (Join-Path $root ('gradle-' + $version))) { Remove-Item -Recurse -Force (Join-Path $root ('gradle-' + $version)) }; Expand-Archive -Path $zip -DestinationPath $root -Force; Remove-Item $zip -Force"
    if errorlevel 1 ( echo [Rafael Build] ERROR: no se pudo descargar Gradle %GRADLE_VERSION%. & exit /b 1 )
)
if not exist "%GRADLE_BIN%" ( echo [Rafael Build] ERROR: Gradle no quedo instalado en "%GRADLE_BIN%". & exit /b 1 )
call "%GRADLE_BIN%" %*
set "EXIT_CODE=%ERRORLEVEL%"
exit /b %EXIT_CODE%
