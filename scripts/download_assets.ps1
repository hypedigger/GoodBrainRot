# =====================================================================
# GoodBrainRot - Telechargement des assets libres (images + sons d'animaux)
#
# - Images : photo principale de l'article Wikipedia anglais de chaque animal
#   (fichiers heberges sur Wikimedia Commons, licences libres).
# - Sons   : premier resultat audio pertinent trouve sur Wikimedia Commons.
# - Le fichier CREDITS-ASSETS.md est (re)genere avec les sources et licences.
#
# L'application fonctionne SANS ces fichiers : fallback emoji pour les images
# et synthese vocale pour les sons. Ce script ne fait qu'ameliorer le rendu.
# Les fichiers deja presents ne sont pas retelecharges : on peut relancer le
# script autant de fois que necessaire (utile en cas de rate-limit Wikimedia).
#
# Usage :  powershell -ExecutionPolicy Bypass -File scripts\download_assets.ps1
#          (-SkipSounds pour ne telecharger que les images)
# =====================================================================

param([switch]$SkipSounds)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $PSScriptRoot
$imgDir = Join-Path $root 'app\src\main\assets\animals'
$sndDir = Join-Path $root 'app\src\main\assets\sounds'
$babyDir = Join-Path $root 'app\src\main\assets\babies'
New-Item -ItemType Directory -Force $imgDir | Out-Null
New-Item -ItemType Directory -Force $sndDir | Out-Null
New-Item -ItemType Directory -Force $babyDir | Out-Null

# Wikimedia exige un User-Agent identifiable avec un moyen de contact
$headers = @{ 'User-Agent' = 'GoodBrainRotAssets/1.0 (app educative pour enfants; +https://github.com/hypedigger/GoodBrainRot)' }

# Pause entre les requetes + retries : Wikimedia limite le trafic anonyme
$delayMs = 1200

function Invoke-Retry([scriptblock]$action, [string]$what) {
    $delays = @(0, 5, 15, 40)
    foreach ($wait in $delays) {
        if ($wait -gt 0) {
            Write-Host "        nouvel essai dans ${wait}s..." -ForegroundColor DarkGray
            Start-Sleep -Seconds $wait
        }
        try {
            return & $action
        } catch {
            $script:lastError = $_.Exception.Message
        }
    }
    throw "echec apres retries : $script:lastError"
}

# key = nom de fichier attendu par l'app ; wiki = article Wikipedia (image) ;
# sound = requete de recherche audio sur Commons
$animals = @(
    @{ key='cat';      wiki='Cat';                        sound='cat meowing' }
    @{ key='dog';      wiki='Dog';                        sound='dog barking' }
    @{ key='cow';      wiki='Cattle';                     sound='cow moo' }
    @{ key='horse';    wiki='Horse';                      sound='horse neigh' }
    @{ key='sheep';    wiki='Sheep';                      sound='sheep bleating' }
    @{ key='goat';     wiki='Goat';                       sound='goat bleating' }
    @{ key='pig';      wiki='Pig';                        sound='pig grunt' }
    @{ key='hen';      wiki='Chicken';                    sound='hen clucking' }
    @{ key='rooster';  wiki='Rooster';                    sound='rooster crowing' }
    @{ key='duck';     wiki='Duck';                       sound='duck quacking' }
    @{ key='bird';     wiki='European_robin';             sound='birdsong' }
    @{ key='owl';      wiki='Owl';                        sound='owl hooting' }
    @{ key='lion';     wiki='Lion';                       sound='lion roaring' }
    @{ key='tiger';    wiki='Tiger';                      sound='tiger growl' }
    @{ key='elephant'; wiki='African_bush_elephant';      sound='elephant trumpeting' }
    @{ key='monkey';   wiki='Monkey';                     sound='monkey sounds' }
    @{ key='bear';     wiki='Brown_bear';                 sound='bear growl' }
    @{ key='wolf';     wiki='Wolf';                       sound='wolf howling' }
    @{ key='frog';     wiki='Frog';                       sound='frog croaking' }
    @{ key='rabbit';   wiki='Rabbit';                     sound='rabbit' }
    @{ key='mouse';    wiki='House_mouse';                sound='mouse squeak' }
    @{ key='snake';    wiki='Snake';                      sound='snake hiss' }
    @{ key='bee';      wiki='Western_honey_bee';          sound='bees buzzing' }
    @{ key='dolphin';  wiki='Common_bottlenose_dolphin';  sound='dolphin sounds' }
)

$credits = New-Object System.Collections.Generic.List[string]
$credits.Add('# Credits des assets (images et sons)')
$credits.Add('')
$credits.Add('Fichiers telecharges depuis Wikimedia (licences libres). Genere par scripts/download_assets.ps1 le ' + (Get-Date -Format 'yyyy-MM-dd') + '.')
$credits.Add('')

function Get-CommonsLicense([string]$fileTitle) {
    # Recupere licence + auteur d'un fichier Commons pour les credits
    try {
        Start-Sleep -Milliseconds $delayMs
        $api = 'https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=extmetadata&titles=' + [uri]::EscapeDataString($fileTitle)
        $r = Invoke-RestMethod -Uri $api -Headers $headers -TimeoutSec 30
        $page = ($r.query.pages.PSObject.Properties | Select-Object -First 1).Value
        $meta = $page.imageinfo[0].extmetadata
        $lic = $meta.LicenseShortName.value
        $artist = ($meta.Artist.value -replace '<[^>]+>', '').Trim()
        if (-not $lic) { $lic = 'voir page du fichier' }
        if (-not $artist) { $artist = 'inconnu' }
        return "$lic - $artist"
    } catch {
        return 'licence : voir la page Commons du fichier'
    }
}

$okImg = 0; $okSnd = 0

foreach ($a in $animals) {
    $key = $a.key

    # ---------- IMAGE : vignette de l'article Wikipedia ----------
    $imgExisting = Get-ChildItem $imgDir -Filter "$key.*" -ErrorAction SilentlyContinue
    if ($imgExisting) {
        Write-Host "[image] $key : deja present, ignore"
        $okImg++
    } else {
        try {
            Start-Sleep -Milliseconds $delayMs
            $wikiTitle = $a.wiki
            $summary = Invoke-Retry { Invoke-RestMethod -Uri ("https://en.wikipedia.org/api/rest_v1/page/summary/" + $wikiTitle) -Headers $headers -TimeoutSec 30 } "summary $key"
            $imgUrl = $summary.thumbnail.source
            if ($imgUrl) {
                # Demande une vignette plus grande (600px)
                $imgUrl = $imgUrl -replace '/\d+px-', '/600px-'
                $ext = ([IO.Path]::GetExtension(($imgUrl -split '\?')[0])).ToLower()
                if ($ext -notin '.jpg', '.jpeg', '.png', '.webp') { $ext = '.jpg' }
                $dest = Join-Path $imgDir "$key$ext"
                Start-Sleep -Milliseconds $delayMs
                Invoke-Retry { Invoke-WebRequest -Uri $imgUrl -OutFile $dest -Headers $headers -UseBasicParsing -TimeoutSec 60 } "image $key" | Out-Null
                $fileName = [uri]::UnescapeDataString((($summary.originalimage.source -split '/')[-1]))
                $licInfo = Get-CommonsLicense ("File:" + $fileName)
                $credits.Add("- **Image $key** : https://en.wikipedia.org/wiki/$wikiTitle - fichier ``$fileName`` - $licInfo")
                Write-Host "[image] $key : OK ($ext)"
                $okImg++
            } else {
                Write-Host "[image] $key : pas d'image trouvee (fallback emoji)" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "[image] $key : ECHEC ($($_.Exception.Message)) - fallback emoji" -ForegroundColor Yellow
        }
    }

    # ---------- SON : recherche audio sur Wikimedia Commons ----------
    if ($SkipSounds) { continue }
    $sndExisting = Get-ChildItem $sndDir -Filter "$key.*" -ErrorAction SilentlyContinue
    if ($sndExisting) {
        Write-Host "[son]   $key : deja present, ignore"
        $okSnd++
        continue
    }
    try {
        Start-Sleep -Milliseconds $delayMs
        $q = [uri]::EscapeDataString($a.sound + ' filetype:audio')
        $api = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrsearch=$q&gsrlimit=10&prop=imageinfo&iiprop=url"
        $r = Invoke-Retry { Invoke-RestMethod -Uri $api -Headers $headers -TimeoutSec 30 } "recherche son $key"
        $pages = @()
        if ($r.query -and $r.query.pages) {
            $pages = $r.query.pages.PSObject.Properties.Value | Sort-Object index
        }
        $picked = $null
        foreach ($p in $pages) {
            $u = $p.imageinfo[0].url
            if ($u -match '\.(ogg|oga|mp3|wav)$') { $picked = $p; break }
        }
        if ($picked) {
            $u = $picked.imageinfo[0].url
            $ext = ([IO.Path]::GetExtension($u)).ToLower()
            if ($ext -eq '.oga') { $ext = '.ogg' }
            $dest = Join-Path $sndDir "$key$ext"
            Start-Sleep -Milliseconds $delayMs
            Invoke-Retry { Invoke-WebRequest -Uri $u -OutFile $dest -Headers $headers -UseBasicParsing -TimeoutSec 120 } "son $key" | Out-Null
            $licInfo = Get-CommonsLicense $picked.title
            $credits.Add("- **Son $key** : ``$($picked.title)`` - https://commons.wikimedia.org/wiki/$([uri]::EscapeDataString($picked.title)) - $licInfo")
            Write-Host "[son]   $key : OK ($ext)"
            $okSnd++
        } else {
            Write-Host "[son]   $key : aucun audio trouve (fallback voix)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "[son]   $key : ECHEC ($($_.Exception.Message)) - fallback voix" -ForegroundColor Yellow
    }
}

# ---------- BEBES ANIMAUX (jeu « Maman et bebe ») ----------
$babies = @(
    @{ key='cat';   wiki='Kitten' }
    @{ key='dog';   wiki='Puppy' }
    @{ key='cow';   wiki='Calf' }
    @{ key='horse'; wiki='Foal' }
    @{ key='sheep'; wiki='Lamb_(animal)' }
    @{ key='frog';  wiki='Tadpole' }
    @{ key='hen';   wiki='Chick_(bird)' }
    @{ key='duck';  wiki='Duckling' }
)
$okBaby = 0
foreach ($b in $babies) {
    $key = $b.key
    $existing = Get-ChildItem $babyDir -Filter "$key.*" -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "[bebe]  $key : deja present, ignore"
        $okBaby++
        continue
    }
    try {
        Start-Sleep -Milliseconds $delayMs
        $wikiTitle = $b.wiki
        $summary = Invoke-Retry { Invoke-RestMethod -Uri ("https://en.wikipedia.org/api/rest_v1/page/summary/" + $wikiTitle) -Headers $headers -TimeoutSec 30 } "summary bebe $key"
        $imgUrl = $summary.thumbnail.source
        if ($imgUrl) {
            $imgUrl = $imgUrl -replace '/\d+px-', '/600px-'
            $ext = ([IO.Path]::GetExtension(($imgUrl -split '\?')[0])).ToLower()
            if ($ext -notin '.jpg', '.jpeg', '.png', '.webp') { $ext = '.jpg' }
            $dest = Join-Path $babyDir "$key$ext"
            Start-Sleep -Milliseconds $delayMs
            Invoke-Retry { Invoke-WebRequest -Uri $imgUrl -OutFile $dest -Headers $headers -UseBasicParsing -TimeoutSec 60 } "image bebe $key" | Out-Null
            $fileName = [uri]::UnescapeDataString((($summary.originalimage.source -split '/')[-1]))
            $licInfo = Get-CommonsLicense ("File:" + $fileName)
            $credits.Add("- **Bebe $key** : https://en.wikipedia.org/wiki/$wikiTitle - fichier ``$fileName`` - $licInfo")
            Write-Host "[bebe]  $key : OK ($ext)"
            $okBaby++
        } else {
            Write-Host "[bebe]  $key : pas d'image trouvee (fallback emoji)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "[bebe]  $key : ECHEC ($($_.Exception.Message)) - fallback emoji" -ForegroundColor Yellow
    }
}

$credits.Add('')
$credits.Add('## Sons issus de la collection locale')
$credits.Add('- cat, cow, goat, hen, horse, sheep : enregistrements LaSonotheque.fr (Joseph Sardin, libres d''utilisation) ; rooster : collection personnelle.')
$credits.Add('- Les sons de plus de 5 secondes ont ete retires : fallback voix.')
$credits.Add('')
$credits.Add('Les assets manquants sont remplaces automatiquement dans l''app : emoji pour les images, synthese vocale pour les sons.')
[IO.File]::WriteAllLines((Join-Path $root 'CREDITS-ASSETS.md'), $credits)
Write-Host "Bebes animaux : $okBaby/8"

Write-Host ""
Write-Host "Termine : $okImg/24 images, $okSnd/24 sons. Credits -> CREDITS-ASSETS.md" -ForegroundColor Green
Write-Host "Si des fichiers manquent (rate-limit), relancez simplement le script."
Write-Host "Recompilez ensuite l'APK pour embarquer les nouveaux assets."
