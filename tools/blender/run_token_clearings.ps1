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
# Each animal phase is a separate, sequential Blender process.
for ($frameIndex=0; $frameIndex -lt 16; $frameIndex++) {
    & $Blender -b -t 2 -P tools/blender/render_density.py -- --animals-frame $frameIndex *> "$logs/animals_$frameIndex-render.log"
    if ($LASTEXITCODE -ne 0) { throw "Animal phase failed: $frameIndex" }
}
& $Python tools/blender/finalize_density.py --crop-only
if ($LASTEXITCODE -ne 0) { throw 'Animal cropping failed' }
foreach ($terrainName in @('timber','stone','fleece','grain','clay','desert')) {
    & $Python tools/blender/animate_v2.py --terrain $terrainName
    if ($LASTEXITCODE -ne 0) { throw "Animation failed: $terrainName" }
}
& $Python tools/blender/compose_alpine.py --full-set-rebuild
if ($LASTEXITCODE -ne 0) { throw 'Stone composition failed' }
foreach ($scriptName in @('compose_v2_set.py','compose_token_check.py','check_v2_assets.py','finalize_token_clearings.py','compose_alpaca_clay.py')) {
    & $Python "tools/blender/$scriptName"
    if ($LASTEXITCODE -ne 0) { throw "$scriptName failed" }
}
