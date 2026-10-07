# =====================================================================
# GoodBrainRot - Échantillons d'instruments réalistes
#
# Source : tonejs-instruments (github.com/nbrosowsky/tonejs-instruments).
#   - Piano : Salamander Grand Piano (CC-BY 3.0, Alexander Holm) -> attribution.
#   - Guitares (acoustique/électrique/nylon) et basse : échantillons du dépôt.
# Fichiers enregistrés sous leur numéro MIDI :
#   app/src/main/assets/instruments/<set>/<midi>.mp3
# (SampleInstrument transpose pour les notes intermédiaires.)
#
# Sans ces fichiers, l'app utilise la synthèse. GitHub n'est pas rate-limité.
# Usage : powershell -ExecutionPolicy Bypass -File scripts\fetch_instruments.ps1
# =====================================================================

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $PSScriptRoot
$base = 'https://raw.githubusercontent.com/nbrosowsky/tonejs-instruments/master/samples'
$headers = @{ 'User-Agent' = 'GoodBrainRotInstruments/1.0' }

$pcNat = @('C', $null, 'D', $null, 'E', 'F', $null, 'G', $null, 'A', $null, 'B')
$pcSharpLetter = @($null, 'C', $null, 'D', $null, $null, 'F', $null, 'G', $null, 'A', $null)

function Note-Candidates([int]$midi) {
    $pc = $midi % 12
    $oct = [math]::Floor($midi / 12) - 1
    if ($pcNat[$pc]) { return @("$($pcNat[$pc])$oct") }
    $L = $pcSharpLetter[$pc]
    return @("${L}s$oct", "${L}%23$oct")
}

# inst = dossier de destination ; folder = dossier source dans le dépôt
function Fetch-Instrument([string]$inst, [string]$folder, [int]$lo, [int]$hi, [int]$step) {
    $dir = Join-Path $root "app\src\main\assets\instruments\$inst"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $ok = 0
    for ($m = $lo; $m -le $hi; $m += $step) {
        $dest = Join-Path $dir "$m.mp3"
        if (Test-Path $dest) { $ok++; continue }
        foreach ($name in (Note-Candidates $m)) {
            try {
                Invoke-WebRequest -Uri "$base/$folder/$name.mp3" -OutFile $dest -Headers $headers -UseBasicParsing -TimeoutSec 60
                $ok++; Write-Host "[$inst] midi $m <- $name"; break
            } catch { }
        }
    }
    Write-Host "$inst : $ok notes" -ForegroundColor Green
}

Fetch-Instrument 'piano'    'piano'           24 96 3
Fetch-Instrument 'acoustic' 'guitar-acoustic' 40 76 2
Fetch-Instrument 'electric' 'guitar-electric' 40 76 2
Fetch-Instrument 'nylon'    'guitar-nylon'    40 76 2
Fetch-Instrument 'bass'     'bass-electric'   28 55 2
Fetch-Instrument 'xylophone' 'xylophone'      72 96 3

Add-Content -Path (Join-Path $root 'CREDITS-ASSETS.md') -Encoding UTF8 -Value @'

## Échantillons d'instruments (option "son réaliste")
- Source : tonejs-instruments (github.com/nbrosowsky/tonejs-instruments).
- Piano : Salamander Grand Piano — CC-BY 3.0 (Alexander Holm) : attribution requise.
- Guitares (acoustique / électrique / nylon) et basse : échantillons du même dépôt.
'@

Write-Host ""
Write-Host "Termine. Recompilez l'APK pour embarquer les échantillons." -ForegroundColor Green
