# =====================================================================
# GoodBrainRot - Samples MPC (hip-hop / electro) depuis Dirt-Samples
#
# Source : github.com/tidalcycles/Dirt-Samples (sons de boîtes à rythmes
# 808/909/etc., librement réutilisables). Téléchargés dans :
#   app/src/main/assets/kits/hiphop/<pad>.wav
#   app/src/main/assets/kits/electro/<pad>.wav
# L'app joue le sample s'il est présent, sinon la synthèse 808/909.
#
# Utilise l'API GitHub (liste des dossiers) + téléchargement raw.
# Usage : powershell -ExecutionPolicy Bypass -File scripts\fetch_mpc_samples.ps1
# =====================================================================

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $PSScriptRoot
$headers = @{ 'User-Agent' = 'GoodBrainRotMPC/1.0'; 'Accept' = 'application/vnd.github+json' }
$api = 'https://api.github.com/repos/tidalcycles/Dirt-Samples/contents'

# pad -> dossiers candidats (le premier présent gagne)
$map = [ordered]@{
    'kick'    = @('808bd','bd','kicklinn')
    '808'     = @('808bd','jvbass','bass')
    'bass'    = @('jvbass','bass','808bd')
    'snare'   = @('808sd','sd','sn')
    'clap'    = @('cp','hand','realclaps')
    'hat'     = @('hh','hc','808')
    'openhat' = @('ho','oh','hh')
    'rim'     = @('rim','rs','click')
    'tom'     = @('808lt','lt','mt','ht')
    'cowbell' = @('cb','808cy','metal')
    'perc'    = @('perc','click','tabla')
    'stab'    = @('arpy','rave','stab','chin')
    'zap'     = @('blip','glitch','click')
    'blip'    = @('blip','click','glitch')
    'ride'    = @('808cy','metal','hc')
    'crash'   = @('crash','808cy','metal')
    'laser'   = @('glitch','space','blip')
    'sweep'   = @('sweep','wind','space','noise')
}
$hiphopPads  = @('kick','clap','hat','openhat','snare','rim','tom','cowbell','808','perc','stab','zap','blip','ride','crash')
$electroPads = @('kick','clap','hat','openhat','808','snare','zap','sweep','laser','stab','cowbell','blip','crash','bass')

# dossiers existants
Write-Host "Liste des dossiers Dirt-Samples..."
$dirs = @{}
try {
    (Invoke-RestMethod -Uri $api -Headers $headers -TimeoutSec 40) |
        Where-Object { $_.type -eq 'dir' } | ForEach-Object { $dirs[$_.name] = $true }
} catch { Write-Host "Echec API GitHub : $($_.Exception.Message)" -ForegroundColor Red; return }
Write-Host "$($dirs.Count) dossiers trouves."

$cacheDir = Join-Path $root '.tmp\mpc_cache'
New-Item -ItemType Directory -Force $cacheDir | Out-Null

# Telecharge un sample pour un nom de pad, renvoie le chemin local (ou $null)
$resolved = @{}
function Get-Sample([string]$pad) {
    if ($resolved.ContainsKey($pad)) { return $resolved[$pad] }
    $local = Join-Path $cacheDir "$pad.wav"
    if (Test-Path $local) { $resolved[$pad] = $local; return $local }
    foreach ($folder in $map[$pad]) {
        if (-not $dirs[$folder]) { continue }
        try {
            Start-Sleep -Milliseconds 300
            $files = Invoke-RestMethod -Uri "$api/$folder" -Headers $headers -TimeoutSec 40
            $wav = $files | Where-Object { $_.name -match '\.wav$' } | Select-Object -First 1
            if ($wav) {
                Invoke-WebRequest -Uri $wav.download_url -OutFile $local -Headers $headers -UseBasicParsing -TimeoutSec 60
                Write-Host "[$pad] <- $folder/$($wav.name)"
                $resolved[$pad] = $local
                return $local
            }
        } catch { }
    }
    Write-Host "[$pad] introuvable" -ForegroundColor Yellow
    $resolved[$pad] = $null
    return $null
}

function Fill-Kit([string]$kit, $pads) {
    $dir = Join-Path $root "app\src\main\assets\kits\$kit"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $ok = 0
    foreach ($pad in $pads) {
        $src = Get-Sample $pad
        if ($src) { Copy-Item $src (Join-Path $dir "$pad.wav") -Force; $ok++ }
    }
    Write-Host "$kit : $ok/$($pads.Count) pads" -ForegroundColor Green
}

Fill-Kit 'hiphop' $hiphopPads
Fill-Kit 'electro' $electroPads

Add-Content -Path (Join-Path $root 'CREDITS-ASSETS.md') -Encoding UTF8 -Value @'

## Samples MPC (hip-hop / electro) - v2
- Source : Dirt-Samples (github.com/tidalcycles/Dirt-Samples), sons de boites a rythmes librement reutilisables.
'@

Write-Host ""
Write-Host "Termine. Recompilez l'APK pour embarquer les samples." -ForegroundColor Green
