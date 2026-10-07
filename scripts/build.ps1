# =====================================================================
# Construit l'APK debug de GoodBrainRot.
# Usage : powershell -ExecutionPolicy Bypass -File scripts\build.ps1
# =====================================================================
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-17.0.19+10"
Write-Host "Build en cours..." -ForegroundColor Cyan
& "$root\gradlew.bat" -p $root assembleDebug --console=plain
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (Test-Path $apk) {
    $mb = [math]::Round((Get-Item $apk).Length / 1MB, 1)
    Write-Host "OK : $apk ($mb Mo)" -ForegroundColor Green
} else {
    Write-Host "Echec : APK introuvable." -ForegroundColor Red
}
