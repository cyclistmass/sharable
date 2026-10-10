param([string[]]$Terrains=@('timber','stone','fleece','grain','clay','desert'),
      $Blender='C:/Program Files/Blender Foundation/Blender 4.5/blender.exe',
      $Python='C:/Users/mauro/AppData/Local/Programs/Python/Python312/python.exe')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
New-Item -ItemType Directory -Force docs/design/tiles-3d-models/v3 | Out-Null
function Invoke-V3Blender([string[]]$BatchArgs,[string]$LogPath) {
    # Let Blender finish and preserve complete stderr logs before checking exit.
    $ErrorActionPreference='Continue'
    & $Blender -b -t 2 --python-exit-code 1 -P tools/blender/render_v3.py -- @BatchArgs *> $LogPath
    if ($LASTEXITCODE -ne 0) { throw "Blender batch failed; see $LogPath" }
}
foreach ($terrainName in $Terrains) {
    Invoke-V3Blender @('--terrain',$terrainName) "docs/design/tiles-3d-models/v3/$terrainName-render.log"
    Write-Output "Finished V3 $terrainName; Blender exited"
}
# Each rendered scene validates its actual final geometry before rendering.
& $Python tools/blender/compose_v3.py
if ($LASTEXITCODE -ne 0) { throw 'V3 review/validation failed' }
