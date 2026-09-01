@echo off
setlocal
echo [INFO] Inicializando Gradle 8.3 para compilacion de Forge 1.20.1...

powershell -Command "$ErrorActionPreference = 'Stop'; try { $zip = 'gradle-8.3-bin.zip'; $url = 'https://services.gradle.org/distributions/gradle-8.3-bin.zip'; $target = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.3'; $exe = Join-Path $target 'gradle-8.3\bin\gradle.bat'; if (-not (Test-Path $exe)) { Write-Host 'Descargando Gradle 8.3 oficial...'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13; Invoke-WebRequest -Uri $url -OutFile $zip; if (-not (Test-Path $target)) { New-Item -ItemType Directory -Force -Path $target | Out-Null }; Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path $zip).Path, $target); Remove-Item $zip -Force; Write-Host 'Gradle instalado con exito.' } } catch { Write-Host '❌ Error: ' $_.Exception.Message; exit 1 }"

if %errorlevel% neq 0 (
    echo [ERROR] No se pudo inicializar Gradle.
    exit /b 1
)

for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-8.3*") do (
    for /d %%B in ("%%D\*") do (
        if exist "%%B\bin\gradle.bat" set "GRADLE_BIN=%%B\bin\gradle.bat"
    )
)

if not defined GRADLE_BIN (
    for /r "%USERPROFILE%\.gradle\wrapper\dists" %%F in (gradle.bat) do (
        set "GRADLE_BIN=%%F"
    )
)

if defined GRADLE_BIN (
    echo [INFO] Compilando con: "%GRADLE_BIN%"
    call "%GRADLE_BIN%" build --no-daemon
) else (
    echo [ERROR] No se encontro gradle.bat en ninguna ruta de .gradle.
    exit /b 1
)
endlocal
