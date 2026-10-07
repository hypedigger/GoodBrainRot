# =====================================================================
# Sert le dépôt GoodBrainRot en local pour l'outil d'admin de contenu.
# Ouvre ensuite http://localhost:8080/tools/content-admin/
#
# Usage : powershell -ExecutionPolicy Bypass -File tools\content-admin\serve.ps1
# (Ctrl+C pour arrêter)
# =====================================================================

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)  # racine du repo
$prefix = 'http://localhost:8080/'

$types = @{
    '.html'='text/html; charset=utf-8'; '.js'='application/javascript'; '.css'='text/css';
    '.json'='application/json; charset=utf-8'; '.jpg'='image/jpeg'; '.jpeg'='image/jpeg';
    '.png'='image/png'; '.webp'='image/webp'; '.gif'='image/gif';
    '.mp3'='audio/mpeg'; '.ogg'='audio/ogg'; '.wav'='audio/wav'; '.flac'='audio/flac'
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
try { $listener.Start() } catch {
    Write-Host "Impossible de demarrer sur $prefix : $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Essayez : python -m http.server 8080   (depuis la racine du repo)"
    return
}
Write-Host "Serveur pret. Ouvrez :" -ForegroundColor Green
Write-Host "  http://localhost:8080/tools/content-admin/" -ForegroundColor Cyan
Write-Host "Racine servie : $root"
Write-Host "Ctrl+C pour arreter."
try { Start-Process 'http://localhost:8080/tools/content-admin/' } catch {}

while ($listener.IsListening) {
    try {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response
        $rel = [uri]::UnescapeDataString($req.Url.AbsolutePath.TrimStart('/'))
        if ($rel -eq '') { $rel = 'tools/content-admin/index.html' }
        $path = Join-Path $root $rel
        if (Test-Path $path -PathType Leaf) {
            $ext = [IO.Path]::GetExtension($path).ToLower()
            $ct = $types[$ext]; if (-not $ct) { $ct = 'application/octet-stream' }
            $res.ContentType = $ct
            $res.AddHeader('Access-Control-Allow-Origin', '*')
            if ($req.HttpMethod -eq 'HEAD') {
                $res.StatusCode = 200
            } else {
                $bytes = [IO.File]::ReadAllBytes($path)
                $res.ContentLength64 = $bytes.Length
                $res.OutputStream.Write($bytes, 0, $bytes.Length)
            }
        } else {
            $res.StatusCode = 404
        }
        $res.OutputStream.Close()
    } catch {
        # client deconnecte, etc. : on continue
    }
}
