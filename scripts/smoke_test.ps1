# Test de fumée GoodBrainRot sur émulateur/appareil : installe l'APK debug, ouvre chaque hub et
# chaque activité accessible par son libellé, puis vérifie qu'aucun plantage n'a été journalisé.
# Usage : .\scripts\smoke_test.ps1 [-Serial emulator-5554] [-NoInstall]
param([string]$Serial = "emulator-5554", [switch]$NoInstall)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$pkg = "com.goodbrainrot.app"
$root = Split-Path $PSScriptRoot -Parent
function A { & $adb -s $Serial @args }
function UiText { A shell uiautomator dump /sdcard/ui.xml 2>$null | Out-Null; (A shell cat /sdcard/ui.xml 2>$null) -join "" }
function TapText([string]$txt) {
    $x = UiText
    $m = [regex]::Match($x, 'text="' + [regex]::Escape($txt) + '"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $false }
    $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2; $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    A shell input tap $cx $cy | Out-Null; Start-Sleep -Milliseconds 2500; return $true
}
function Back { A shell input keyevent KEYCODE_BACK | Out-Null; Start-Sleep -Milliseconds 1500 }
$screenH = [int]([regex]::Match((A shell wm size), '(\d+)x(\d+)').Groups[2].Value)
# libellés des tuiles : textes dont le centre est dans la bande verticale [minF, maxF] de l'écran
# (exclut l'en-tête, le sélecteur de profil et l'interrupteur « mode bébé » qui épinglerait l'appli)
function Labels([double]$minF, [double]$maxF) {
    [regex]::Matches((UiText), 'text="([^"]+)"[^>]*?bounds="\[\d+,(\d+)\]\[\d+,(\d+)\]"') | ForEach-Object {
        $cy = ([int]$_.Groups[2].Value + [int]$_.Groups[3].Value) / 2 / $screenH
        if ($cy -ge $minF -and $cy -le $maxF) { $_.Groups[1].Value }
    } | Select-Object -Unique
}

if (-not $NoInstall) {
    $apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
    $r = A install -r $apk 2>&1 | Select-Object -Last 1
    if ($r -notmatch "Success") { Write-Host "INSTALL FAILED: $r (signature différente ? désinstaller d'abord)"; exit 2 }
}
A logcat -c
A shell am force-stop $pkg | Out-Null
A shell am start -n "$pkg/.MainActivity" | Out-Null
# écran d'avertissement puis accueil (le démarrage prend 8-15 s)
$atHome = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep 2; $t = UiText
    $w = [regex]::Match($t, 'text="([^"]*(compris|understand|capito|entendido|понятно)[^"]*)"', 'IgnoreCase')
    if ($w.Success) { TapText $w.Groups[1].Value | Out-Null }
    if ($t -match 'text="(Jeux|Games|Giochi|Juegos|Игры)"') { $atHome = $true; break }
}
if (-not $atHome) { Write-Host "FAIL : accueil jamais atteint"; exit 3 }

$visited = 0; $fails = @()
# hubs = tuiles de l'accueil ; on ouvre chaque hub puis chaque activité qu'il contient (aller-retour)
$hubs = Labels 0.22 0.85 | Where-Object { $_ -notmatch "^v\d" }
Write-Host "Hubs : $($hubs -join ', ')"
foreach ($hub in $hubs) {
    if (-not (TapText $hub)) { continue }
    A shell input swipe 672 2300 672 1000 300 | Out-Null; Start-Sleep 1
    $acts = @(Labels 0.13 0.98)
    A shell input swipe 672 1000 672 2300 300 | Out-Null; Start-Sleep 1
    $acts = @($acts + (Labels 0.13 0.98)) | Select-Object -Unique
    Write-Host "  $hub : $($acts -join ', ')"
    foreach ($act in $acts) {
        if (-not (TapText $act)) {
            A shell input swipe 672 2300 672 1000 300 | Out-Null; Start-Sleep 1
            if (-not (TapText $act)) { continue }
        }
        $visited++
        Start-Sleep 2
        $crash = A logcat -d -s AndroidRuntime:E 2>$null | Select-String "FATAL EXCEPTION"
        if ($crash) { $fails += "$hub > $act"; A logcat -c; A shell am start -n "$pkg/.MainActivity" | Out-Null; Start-Sleep 8 }
        # retour au hub (certains jeux ont plusieurs écrans : on insiste jusqu'à revoir le titre du hub)
        for ($k = 0; $k -lt 4; $k++) { Back; if ((UiText) -match ('text="' + [regex]::Escape($hub) + '"')) { break } }
        A shell input swipe 672 1000 672 2300 300 | Out-Null; Start-Sleep 1
    }
    Back
}
Write-Host "Écrans visités : $visited"
$crash = A logcat -d -s AndroidRuntime:E 2>$null | Select-String "FATAL EXCEPTION" -Context 0, 8
if ($fails.Count -gt 0 -or $crash) { Write-Host "ÉCHEC — plantages :"; $fails | ForEach-Object { Write-Host "  $_" }; $crash; exit 1 }
Write-Host "OK — aucun plantage"
exit 0
