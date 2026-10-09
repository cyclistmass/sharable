param([string]$Blender='C:/Program Files/Blender Foundation/Blender 4.5/blender.exe',
      [string]$Python='C:/Users/mauro/AppData/Local/Programs/Python/Python312/python.exe')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$logs='docs/design/tiles-3d-models/token-clearing'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
foreach ($terrainName in @('timber','stone','fleece','grain','clay','desert')) {
    # Direct invocation waits for Blender to exit. No parallel jobs or background processes.
    & $Blender -b -t 2 -P tools/blender/render_density.py -- --terrain $terrainName *> "$logs/$terrainName-render.log"
    if ($LASTEXITCODE -ne 0) { throw "Render failed: $terrainName" }
    Write-Output "Finished $terrainName; Blender exited"
}
& $Blender -b -t 2 -P tools/blender/render_density.py -- --stone-patch *> "$logs/stone-patch-render.log"
if ($LASTEXITCODE -ne 0) { throw 'Stone patch failed' }
# Existing sheep/goat sprite motions remain at unchanged, unobstructed anchors.
foreach ($terrainName in @('timber','stone','fleece','grain','clay','desert')) {
    & $Python tools/blender/animate_v2.py --terrain $terrainName
    if ($LASTEXITCODE -ne 0) { throw "Animation failed: $terrainName" }
}
& $Python tools/blender/compose_alpine.py --full-set-rebuild
if ($LASTEXITCODE -ne 0) { throw 'Stone composition failed' }
foreach ($scriptName in @('compose_v2_set.py','compose_token_check.py','check_v2_assets.py','finalize_token_clearings.py')) {
    & $Python "tools/blender/$scriptName"
    if ($LASTEXITCODE -ne 0) { throw "$scriptName failed" }
}
