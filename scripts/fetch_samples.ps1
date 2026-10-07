# =====================================================================
# GoodBrainRot - Samples CC0 pour les MPC (hip-hop / electro)
#
# Source : kit de batterie de Sonic Pi (github sonic-pi-net/sonic-pi),
# samples publies sous CC0 (domaine public). Telecharges dans
#   app/src/main/assets/kits/hiphop/<pad>.flac
#   app/src/main/assets/kits/electro/<pad>.flac
#
# Si un sample manque, l'app se rabat sur un son de batterie synthetise :
# ce script est donc optionnel mais rend les pads plus realistes.
# GitHub n'est pas rate-limite comme Wikimedia : ce script marche en general
# du premier coup.
#
# Usage : powershell -ExecutionPolicy Bypass -File scripts\fetch_samples.ps1
# =====================================================================

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $PSScriptRoot
$base = 'https://raw.githubusercontent.com/sonic-pi-net/sonic-pi/dev/etc/samples'
$headers = @{ 'User-Agent' = 'GoodBrainRotSamples/1.0' }

# pad (nom attendu par l'app) -> sample Sonic Pi (CC0)
$hiphop = [ordered]@{
    'kick'    = 'drum_heavy_kick'
    'clap'    = 'drum_splash_soft'
    'hat'     = 'drum_cymbal_closed'
    'openhat' = 'drum_cymbal_open'
    'snare'   = 'drum_snare_hard'
    'rim'     = 'drum_cymbal_pedal'
    'tom'     = 'drum_tom_mid_hard'
    'cowbell' = 'drum_cowbell'
    '808'     = 'drum_bass_soft'
    'perc'    = 'drum_tom_hi_soft'
    'stab'    = 'drum_snare_soft'
    'zap'     = 'drum_cymbal_soft'
}
$electro = [ordered]@{
    'kick'    = 'drum_heavy_kick'
    'clap'    = 'drum_splash_hard'
    'hat'     = 'drum_cymbal_closed'
    'openhat' = 'drum_cymbal_open'
    'zap'     = 'drum_cymbal_soft'
    'sweep'   = 'drum_roll'
    'laser'   = 'drum_cymbal_hard'
    'stab'    = 'drum_snare_soft'
    'bass'    = 'drum_bass_hard'
    'cowbell' = 'drum_cowbell'
    'snare'   = 'drum_snare_hard'
    'blip'    = 'drum_tom_hi_soft'
}

function Fetch-Kit([string]$kit, $map) {
    $dir = Join-Path $root "app\src\main\assets\kits\$kit"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $ok = 0
    foreach ($pad in $map.Keys) {
        $dest = Join-Path $dir "$pad.flac"
        if (Test-Path $dest) { $ok++; continue }
        $url = "$base/$($map[$pad]).flac"
        try {
            Invoke-WebRequest -Uri $url -OutFile $dest -Headers $headers -UseBasicParsing -TimeoutSec 60
            $ok++
            Write-Host "[$kit] $pad : OK"
        } catch {
            Write-Host "[$kit] $pad : ECHEC ($($_.Exception.Message))" -ForegroundColor Yellow
        }
    }
    Write-Host "$kit : $ok/$($map.Count) samples" -ForegroundColor Green
}

Fetch-Kit 'hiphop' $hiphop
Fetch-Kit 'electro' $electro

# Credits
$credits = Join-Path $root 'CREDITS-ASSETS.md'
Add-Content -Path $credits -Encoding UTF8 -Value @'

## Samples MPC (hip-hop / electro)
- Kit de batterie issu de Sonic Pi (github.com/sonic-pi-net/sonic-pi), samples sous CC0 (domaine public).
- Mappes sur les pads de l'app ; si absents, l'app utilise une batterie synthetisee.
'@

Write-Host ""
Write-Host "Termine. Recompilez l'APK pour embarquer les samples." -ForegroundColor Green
