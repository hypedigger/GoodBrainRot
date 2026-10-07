# =====================================================================
# GoodBrainRot - Sons réels de véhicules (Wikimedia Commons, best-effort)
#
# Télécharge jusqu'à 3 sons par véhicule dans :
#   app/src/main/assets/vehicle_sounds/<key>/<n>.<ext>
# L'app en joue un au hasard ; sans fichier, elle utilise la synthèse.
#
# ATTENTION : Wikimedia limite le trafic anonyme — résultat souvent partiel.
# Relancer si besoin (ignore ce qui existe déjà).
#
# Usage : powershell -ExecutionPolicy Bypass -File scripts\fetch_vehicle_sounds.ps1
# =====================================================================

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $PSScriptRoot
$headers = @{ 'User-Agent' = 'GoodBrainRotVehicleSounds/1.0 (+https://github.com/hypedigger/GoodBrainRot)' }
$delayMs = 1200

# key -> requête de recherche audio sur Commons
$vehicles = @(
    @{ key='car';        q='car engine' },
    @{ key='fire_truck'; q='fire truck siren' },
    @{ key='police';     q='police siren' },
    @{ key='ambulance';  q='ambulance siren' },
    @{ key='train';      q='train horn' },
    @{ key='plane';      q='airplane takeoff' },
    @{ key='helicopter'; q='helicopter' },
    @{ key='bike';       q='bicycle bell' },
    @{ key='bus';        q='bus engine' },
    @{ key='tractor';    q='tractor engine' },
    @{ key='boat';       q='boat horn' },
    @{ key='motorcycle'; q='motorcycle engine' },
    @{ key='truck';      q='truck horn' }
)

function Invoke-Retry([scriptblock]$a) {
    foreach ($w in @(0, 5, 20)) { if ($w -gt 0) { Start-Sleep -Seconds $w }; try { return & $a } catch { $script:e = $_.Exception.Message } }
    throw $script:e
}

$total = 0
foreach ($v in $vehicles) {
    $dir = Join-Path $root "app\src\main\assets\vehicle_sounds\$($v.key)"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $have = (Get-ChildItem $dir -File -ErrorAction SilentlyContinue).Count
    if ($have -ge 3) { Write-Host "[$($v.key)] deja $have sons"; $total += $have; continue }
    try {
        Start-Sleep -Milliseconds $delayMs
        $q = [uri]::EscapeDataString($v.q + ' filetype:audio')
        $api = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrsearch=$q&gsrlimit=8&prop=imageinfo&iiprop=url"
        $r = Invoke-Retry { Invoke-RestMethod -Uri $api -Headers $headers -TimeoutSec 30 }
        $pages = @()
        if ($r.query -and $r.query.pages) { $pages = $r.query.pages.PSObject.Properties.Value }
        $n = $have
        foreach ($p in $pages) {
            if ($n -ge 3) { break }
            $u = $p.imageinfo[0].url
            if ($u -notmatch '\.(ogg|oga|mp3|wav)$') { continue }
            $ext = ([IO.Path]::GetExtension($u)).ToLower().TrimStart('.'); if ($ext -eq 'oga') { $ext = 'ogg' }
            try {
                Start-Sleep -Milliseconds $delayMs
                Invoke-Retry { Invoke-WebRequest -Uri $u -OutFile (Join-Path $dir "$n.$ext") -Headers $headers -UseBasicParsing -TimeoutSec 90 } | Out-Null
                $n++; $total++
                Write-Host "[$($v.key)] son $n OK"
            } catch { Write-Host "[$($v.key)] echec dl" -ForegroundColor Yellow }
        }
        if ($n -eq $have) { Write-Host "[$($v.key)] aucun son" -ForegroundColor Yellow }
    } catch {
        Write-Host "[$($v.key)] ECHEC ($($_.Exception.Message))" -ForegroundColor Yellow
    }
}

Add-Content -Path (Join-Path $root 'CREDITS-ASSETS.md') -Encoding UTF8 -Value @'

## Sons de véhicules
- Source : Wikimedia Commons (licences libres). Plusieurs sons par véhicule, joués au hasard.
'@

Write-Host ""
Write-Host "Termine : $total sons. Recompilez l'APK." -ForegroundColor Green
