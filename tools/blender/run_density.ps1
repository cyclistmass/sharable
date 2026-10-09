param([string]$Blender='C:/Program Files/Blender Foundation/Blender 4.5/blender.exe',
      [string]$Python='C:/Users/mauro/AppData/Local/Programs/Python/Python312/python.exe')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $taskRoot
$logs=Join-Path $taskRoot 'docs/design/tiles-3d-models/density-manifests/logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
function Run-BlenderBatch([string[]]$BatchArgs,[string]$Name) {
    # The direct invocation blocks until Blender exits. Never Start-Process or parallelize.
    & $Blender -b -t 2 -P tools/blender/render_density.py -- @BatchArgs > (Join-Path $logs "$Name.log")
    if ($LASTEXITCODE -ne 0) { throw "Blender batch $Name failed" }
    Write-Output "Finished $Name; Blender exited"
}
foreach ($terrain in @('fleece','grain','clay','desert','stone')) {
    Run-BlenderBatch -BatchArgs @('--terrain',$terrain) -Name $terrain
}
Run-BlenderBatch -BatchArgs @('--stone-patch') -Name 'stone_patch'
for ($frame=0;$frame -lt 16;$frame++) {
    Run-BlenderBatch -BatchArgs @('--animals-frame',"$frame") -Name "animals_$frame"
}
& $Python tools/blender/finalize_density.py --crop-only
if ($LASTEXITCODE -ne 0) { throw 'Animal cropping failed' }
foreach ($terrain in @('stone','fleece','grain','clay','desert')) {
    & $Python tools/blender/animate_v2.py --terrain $terrain
    if ($LASTEXITCODE -ne 0) { throw "Animation $terrain failed" }
}
foreach ($script in @('compose_alpine.py','compose_v2_set.py','compose_density.py','check_v2_assets.py','finalize_density.py')) {
    if ($script -eq 'compose_alpine.py') { & $Python "tools/blender/$script" --full-set-rebuild }
    else { & $Python "tools/blender/$script" }
    if ($LASTEXITCODE -ne 0) { throw "$script failed" }
}
