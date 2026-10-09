Add-Type -AssemblyName System.Drawing
$folder = $PSScriptRoot
$sheet = New-Object System.Drawing.Bitmap 2200,1750
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.Clear([System.Drawing.Color]::FromArgb(91,61,40))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$font = New-Object System.Drawing.Font 'Segoe UI',23
$small = New-Object System.Drawing.Font 'Segoe UI',18
$brush = [System.Drawing.Brushes]::Ivory
$g.DrawString('STONE / V2 / MOUNTAIN RANGE OPTIONS',$font,$brush,35,18)
$cols = @('Flat / 0 degrees','Mid / 27.5 degrees','Max / 55 degrees','3 x 3 / 55 degrees')
for($c=0;$c -lt 4;$c++){ $g.DrawString($cols[$c],$small,$brush,(160+510*$c),78) }
$names = @('R1 Ridge','R2 Massif','R3 Alpine chain')
$files = @('flat','mid','max','patch_55')
for($r=0;$r -lt 3;$r++){
 $g.DrawString($names[$r],$small,$brush,25,(145+535*$r))
 for($c=0;$c -lt 4;$c++){
  $p = Join-Path $folder ('R'+($r+1)+'_'+$files[$c]+'.png')
  $im = [System.Drawing.Image]::FromFile($p)
  if($im.Width -ne 512 -or $im.Height -ne 512){ throw 'Unexpected render size' }
  $g.DrawImage($im,(150+510*$c),(180+510*$r),500,500)
  $im.Dispose()
 }
}
$sheet.Save((Join-Path $folder 'range-options.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $sheet.Dispose(); $font.Dispose(); $small.Dispose()
