# =====================================================================
# GoodBrainRot - Contenu "Reconnais" : noms multilingues + images
#
# Pour chaque animal/dino/vehicule :
#  - noms localises FR/EN/IT/ES/RU via Wikidata (labels)
#  - image principale via l'article Wikipedia anglais (Wikimedia Commons)
# Resultat : met a jour app/src/main/assets/content/*.json (fusion, sans
# ecraser le contenu de base) et telecharge les images dans les dossiers
# assets/animals, assets/dinos, assets/vehicles, assets/babies.
#
# L'app fonctionne SANS ces images (emoji / dessin / nom en repli). Ce
# script ne fait que l'enrichir. Relancable : ignore ce qui existe deja.
#
# ATTENTION : Wikimedia limite fortement le trafic anonyme. Depuis certaines
# connexions le telechargement echoue en masse (HTTP 429). Relancez le script
# (il reprend la ou il s'est arrete) ou lancez-le depuis une autre connexion.
#
# Usage :
#   powershell -ExecutionPolicy Bypass -File scripts\fetch_content.ps1
#   ... -Only animals|dinos|vehicles|babies   (cible une categorie)
#   ... -NamesOnly                            (pas d'images)
# =====================================================================

param(
    [string]$Only = "all",
    [switch]$NamesOnly,
    [switch]$Rank
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $PSScriptRoot
$contentDir = Join-Path $root 'app\src\main\assets\content'
$headers = @{ 'User-Agent' = 'GoodBrainRotContent/1.0 (app educative; +https://github.com/hypedigger/GoodBrainRot)' }
$delayMs = 1200
$langs = @('en', 'fr', 'it', 'es', 'ru')

function Invoke-Retry([scriptblock]$action) {
    foreach ($wait in @(0, 5, 15, 40)) {
        if ($wait -gt 0) { Start-Sleep -Seconds $wait }
        try { return & $action } catch { $script:lastErr = $_.Exception.Message }
    }
    throw "echec: $script:lastErr"
}

function Get-Slug([string]$title) {
    $s = $title.ToLower() -replace "[^a-z0-9]+", "_"
    return $s.Trim('_')
}

function Get-WikidataId([string]$title) {
    Start-Sleep -Milliseconds $delayMs
    $u = "https://en.wikipedia.org/w/api.php?action=query&prop=pageprops&ppprop=wikibase_item&redirects=1&format=json&titles=" + [uri]::EscapeDataString($title)
    $r = Invoke-Retry { Invoke-RestMethod -Uri $u -Headers $headers -TimeoutSec 30 }
    $page = ($r.query.pages.PSObject.Properties | Select-Object -First 1).Value
    return $page.pageprops.wikibase_item
}

function Get-Labels([string]$qid) {
    Start-Sleep -Milliseconds $delayMs
    $u = "https://www.wikidata.org/w/api.php?action=wbgetentities&props=labels&format=json&ids=$qid&languages=" + ($langs -join '|')
    $r = Invoke-Retry { Invoke-RestMethod -Uri $u -Headers $headers -TimeoutSec 30 }
    $labels = $r.entities.$qid.labels
    $out = @{}
    foreach ($l in $langs) {
        if ($labels.$l) { $out[$l] = $labels.$l.value }
    }
    return $out
}

function Get-ImageUrl([string]$title) {
    Start-Sleep -Milliseconds $delayMs
    $summary = Invoke-Retry { Invoke-RestMethod -Uri ("https://en.wikipedia.org/api/rest_v1/page/summary/" + [uri]::EscapeDataString($title)) -Headers $headers -TimeoutSec 30 }
    $img = $summary.thumbnail.source
    if ($img) { return ($img -replace '/\d+px-', '/500px-') }
    return $null
}

function Load-Json([string]$path) {
    if (-not (Test-Path $path)) { return @() }
    $txt = [IO.File]::ReadAllText($path)
    if ([string]::IsNullOrWhiteSpace($txt)) { return @() }
    return @($txt | ConvertFrom-Json)
}

function Save-Json([string]$path, $arr) {
    $json = $arr | ConvertTo-Json -Depth 6
    [IO.File]::WriteAllText($path, $json, [Text.UTF8Encoding]::new($false))
}

# Traite une categorie : liste de @{title=...; key=... (optionnel)}, dossier image, fichier json
function Process-Category([string]$name, $list, [string]$imgFolder, [string]$jsonFile) {
    Write-Host "=== $name ===" -ForegroundColor Cyan
    $imgDir = Join-Path $root "app\src\main\assets\$imgFolder"
    New-Item -ItemType Directory -Force $imgDir | Out-Null
    $jsonPath = Join-Path $contentDir $jsonFile
    $existing = Load-Json $jsonPath
    $byKey = @{}
    foreach ($e in $existing) { $byKey[$e.key] = $e }
    $okImg = 0; $okName = 0

    foreach ($item in $list) {
        $title = $item.title
        $key = if ($item.key) { $item.key } else { Get-Slug $title }

        # --- nom multilingue (si pas deja un item avec nameKey/names) ---
        $entry = $byKey[$key]
        $needNames = (-not $entry) -or (-not $entry.nameKey -and -not $entry.names)
        if ($needNames) {
            try {
                $qid = Get-WikidataId $title
                if ($qid) {
                    $labels = Get-Labels $qid
                    if ($labels.Count -gt 0) {
                        if (-not $entry) {
                            $entry = [ordered]@{ key = $key; names = $labels }
                            $existing += [pscustomobject]$entry
                            $byKey[$key] = $existing[-1]
                        }
                        $okName++
                        Write-Host ("[nom]   $key : " + $labels['fr']) -ForegroundColor DarkGray
                    }
                }
            } catch {
                Write-Host "[nom]   $key : ECHEC ($($_.Exception.Message))" -ForegroundColor Yellow
            }
        } else {
            $okName++
        }

        # --- image ---
        if (-not $NamesOnly) {
            $hasImg = Get-ChildItem $imgDir -Filter "$key.*" -ErrorAction SilentlyContinue
            if ($hasImg) { $okImg++ }
            else {
                try {
                    $url = Get-ImageUrl $title
                    if ($url) {
                        $ext = ([IO.Path]::GetExtension(($url -split '\?')[0])).ToLower()
                        if ($ext -notin '.jpg', '.jpeg', '.png', '.webp') { $ext = '.jpg' }
                        $dest = Join-Path $imgDir "$key$ext"
                        Start-Sleep -Milliseconds $delayMs
                        Invoke-Retry { Invoke-WebRequest -Uri $url -OutFile $dest -Headers $headers -UseBasicParsing -TimeoutSec 60 } | Out-Null
                        $okImg++
                        Write-Host "[image] $key : OK"
                    }
                } catch {
                    Write-Host "[image] $key : ECHEC ($($_.Exception.Message))" -ForegroundColor Yellow
                }
            }
        }
    }

    Save-Json $jsonPath $existing
    Write-Host "$name : $okName noms, $okImg images -> $jsonFile" -ForegroundColor Green
}

# ---------------- Listes ----------------
$animals = @(
    'Cat','Dog','Cattle','Horse','Sheep','Goat','Pig','Chicken','Rooster','Duck',
    'Goose','Turkey (bird)','Rabbit','Mouse','Rat','Hamster','Guinea pig','Ferret',
    'Lion','Tiger','Leopard','Cheetah','Jaguar','Cougar','Lynx','Panther',
    'Elephant','Giraffe','Zebra','Hippopotamus','Rhinoceros','Gorilla','Chimpanzee',
    'Orangutan','Monkey','Baboon','Lemur','Sloth','Koala','Kangaroo','Wombat',
    'Panda','Brown bear','Polar bear','Wolf','Fox','Coyote','Raccoon','Badger',
    'Otter','Beaver','Squirrel','Chipmunk','Hedgehog','Mole (animal)','Bat',
    'Deer','Moose','Elk','Reindeer','Antelope','Gazelle','Bison','Buffalo','Yak',
    'Camel','Llama','Alpaca','Donkey','Mule','Boar','Hyena','Meerkat','Mongoose',
    'Armadillo','Anteater','Porcupine','Skunk','Opossum','Platypus','Echidna',
    'Whale','Dolphin','Orca','Shark','Seal','Sea lion','Walrus','Manatee',
    'Octopus','Squid','Jellyfish','Crab','Lobster','Shrimp','Starfish','Seahorse',
    'Clownfish','Goldfish','Salmon','Tuna','Pufferfish','Eel','Stingray',
    'Eagle','Hawk','Falcon','Owl','Vulture','Parrot','Macaw','Cockatoo',
    'Penguin','Flamingo','Peacock','Swan','Pelican','Seagull','Stork','Crane (bird)',
    'Heron','Woodpecker','Robin','Sparrow','Pigeon','Crow','Raven','Magpie',
    'Hummingbird','Kingfisher','Toucan','Ostrich','Emu','Kiwi (bird)',
    'Frog','Toad','Salamander','Newt','Turtle','Tortoise','Crocodile','Alligator',
    'Lizard','Iguana','Chameleon','Gecko','Komodo dragon','Snake','Cobra',
    'Python (genus)','Rattlesnake','Boa (genus)','Anaconda',
    'Bee','Wasp','Hornet','Ant','Butterfly','Moth','Dragonfly','Ladybird',
    'Grasshopper','Cricket (insect)','Beetle','Caterpillar','Spider','Tarantula',
    'Scorpion','Snail','Slug','Worm','Centipede','Mosquito','Fly','Firefly',
    'Hare','Weasel','Stoat','Wildebeest','Warthog','Tapir','Okapi','Lemming',
    'Puffin','Albatross','Cassowary','Cardinal (bird)','Goldcrest','Nightingale',
    'Seal (animal)','Narwhal','Beluga whale','Manta ray','Catfish','Carp',
    'Axolotl','Tree frog','Bullfrog','Monitor lizard','Gila monster',
    'Praying mantis','Cockroach','Termite','Earwig','Aphid','Tick','Flea',
    'Capybara','Chinchilla','Gerbil','Vole','Shrew','Pangolin','Aardvark',
    'Bushbaby','Tarsier','Marmoset','Gibbon','Mandrill','Wallaby','Tasmanian devil',
    'Dingo','Jackal','Wild boar','Ibex','Chamois','Pronghorn','Caribou',
    'Snow leopard','Clouded leopard','Serval','Caracal','Ocelot','Bobcat'
)

$dinos = @(
    @{ key='trex'; title='Tyrannosaurus' },
    @{ key='triceratops'; title='Triceratops' },
    @{ key='stegosaurus'; title='Stegosaurus' },
    @{ key='brachiosaurus'; title='Brachiosaurus' },
    @{ key='velociraptor'; title='Velociraptor' },
    @{ key='pteranodon'; title='Pteranodon' },
    @{ key='ankylosaurus'; title='Ankylosaurus' },
    @{ key='parasaurolophus'; title='Parasaurolophus' },
    @{ key='spinosaurus'; title='Spinosaurus' },
    @{ key='diplodocus'; title='Diplodocus' },
    @{ key='allosaurus'; title='Allosaurus' },
    @{ key='iguanodon'; title='Iguanodon' },
    @{ key='brontosaurus'; title='Brontosaurus' },
    @{ key='dimetrodon'; title='Dimetrodon' },
    @{ key='pachycephalosaurus'; title='Pachycephalosaurus' },
    @{ key='compsognathus'; title='Compsognathus' }
)

$vehicles = @(
    @{ key='car'; title='Car' }, @{ key='fire_truck'; title='Fire engine' },
    @{ key='police'; title='Police car' }, @{ key='ambulance'; title='Ambulance' },
    @{ key='train'; title='Train' }, @{ key='plane'; title='Airplane' },
    @{ key='helicopter'; title='Helicopter' }, @{ key='bike'; title='Bicycle' },
    @{ key='bus'; title='Bus' }, @{ key='tractor'; title='Tractor' },
    @{ key='boat'; title='Boat' }, @{ key='rocket'; title='Rocket' },
    @{ key='motorcycle'; title='Motorcycle' }, @{ key='truck'; title='Truck' },
    @{ key='submarine'; title='Submarine' }, @{ key='excavator'; title='Excavator' },
    @{ key='crane_vehicle'; title='Mobile crane' }, @{ key='ship'; title='Ship' }
)

$babies = @(
    @{ key='cat'; title='Kitten' }, @{ key='dog'; title='Puppy' },
    @{ key='cow'; title='Calf' }, @{ key='horse'; title='Foal' },
    @{ key='sheep'; title='Lamb' }, @{ key='frog'; title='Tadpole' },
    @{ key='hen'; title='Chick' }, @{ key='duck'; title='Duckling' }
)

# Babies : images seules (les noms sont deja dans les strings de l'app)
function Process-Babies {
    Write-Host "=== babies ===" -ForegroundColor Cyan
    $imgDir = Join-Path $root 'app\src\main\assets\babies'
    New-Item -ItemType Directory -Force $imgDir | Out-Null
    $ok = 0
    foreach ($b in $babies) {
        $hasImg = Get-ChildItem $imgDir -Filter "$($b.key).*" -ErrorAction SilentlyContinue
        if ($hasImg) { $ok++; continue }
        if ($NamesOnly) { continue }
        try {
            $url = Get-ImageUrl $b.title
            if ($url) {
                $ext = ([IO.Path]::GetExtension(($url -split '\?')[0])).ToLower()
                if ($ext -notin '.jpg', '.jpeg', '.png', '.webp') { $ext = '.jpg' }
                Start-Sleep -Milliseconds $delayMs
                Invoke-Retry { Invoke-WebRequest -Uri $url -OutFile (Join-Path $imgDir "$($b.key)$ext") -Headers $headers -UseBasicParsing -TimeoutSec 60 } | Out-Null
                $ok++; Write-Host "[bebe]  $($b.key) : OK"
            }
        } catch { Write-Host "[bebe]  $($b.key) : ECHEC" -ForegroundColor Yellow }
    }
    Write-Host "babies : $ok images" -ForegroundColor Green
}

# Classe les animaux du plus connu au plus rare via les pages vues Wikipedia.
function Rank-Category([string]$jsonFile) {
    Write-Host "=== rank ($jsonFile) ===" -ForegroundColor Cyan
    $jsonPath = Join-Path $contentDir $jsonFile
    $arr = Load-Json $jsonPath
    if ($arr.Count -eq 0) { return }
    # Fenetre : mois precedent
    $end = (Get-Date).ToString('yyyyMM') + '0100'
    $start = (Get-Date).AddMonths(-1).ToString('yyyyMM') + '0100'
    $views = @{}
    foreach ($e in $arr) {
        $title = if ($e.names -and $e.names.en) { $e.names.en } else { (Get-Culture).TextInfo.ToTitleCase(($e.key -replace '_', ' ')) }
        $t = $title -replace ' ', '_'
        try {
            Start-Sleep -Milliseconds 400
            $u = "https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/en.wikipedia.org/all-access/user/" + [uri]::EscapeDataString($t) + "/monthly/$start/$end"
            $r = Invoke-Retry { Invoke-RestMethod -Uri $u -Headers $headers -TimeoutSec 30 }
            $sum = ($r.items | Measure-Object -Property views -Sum).Sum
            $views[$e.key] = [int]$sum
        } catch {
            $views[$e.key] = 0
        }
    }
    # rang 1 = le plus consulte
    $sorted = $arr | Sort-Object { -1 * ($views[$_.key]) }
    $rank = 1
    foreach ($e in $sorted) {
        $e | Add-Member -NotePropertyName rank -NotePropertyValue $rank -Force
        $rank++
    }
    Save-Json $jsonPath $arr
    Write-Host "rank ecrit pour $($arr.Count) items ($jsonFile)" -ForegroundColor Green
}

# ---------------- Execution ----------------
if ($Rank) {
    if ($Only -in 'all', 'animals') { Rank-Category 'animals.json' }
    if ($Only -in 'all', 'dinos') { Rank-Category 'dinos.json' }
    if ($Only -in 'all', 'vehicles') { Rank-Category 'vehicles.json' }
    Write-Host "Termine (rang). Recompilez l'APK." -ForegroundColor Green
    return
}

$animalList = $animals | ForEach-Object { @{ title = $_ } }

if ($Only -in 'all', 'animals') { Process-Category 'animals' $animalList 'animals' 'animals.json' }
if ($Only -in 'all', 'dinos') { Process-Category 'dinos' $dinos 'dinos' 'dinos.json' }
if ($Only -in 'all', 'vehicles') { Process-Category 'vehicles' $vehicles 'vehicles' 'vehicles.json' }
if ($Only -in 'all', 'babies') { Process-Babies }

Write-Host ""
Write-Host "Termine. Recompilez l'APK pour embarquer le nouveau contenu." -ForegroundColor Green
Write-Host "Sources : noms = Wikidata, images = Wikipedia/Wikimedia Commons (licences libres)."
