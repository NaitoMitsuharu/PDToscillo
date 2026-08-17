param(
    [string]$Ffmpeg = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Gyan.FFmpeg.Essentials_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.1-essentials_build\bin\ffmpeg.exe"
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSCommandPath
$landscape = Join-Path $root 'assets\video-thumbnail.png'
$project = Join-Path $root 'assets\project-thumbnail.png'
$output = Join-Path $root 'PDToscillo-exhibition.mp4'
$font = 'C\:/Windows/Fonts/YuGothR.ttc'

if (-not (Test-Path -LiteralPath $Ffmpeg)) {
    throw "FFmpeg が見つかりません: $Ffmpeg"
}

$scene1 = "[0:v]scale=2048:1152:force_original_aspect_ratio=increase,crop=2048:1152,zoompan=z='min(zoom+0.0003,1.05)':d=1:s=1920x1080:fps=30,drawtext=fontfile='$font':text='PDToscillo':fontcolor=white:fontsize=66:x=110:y=110,drawtext=fontfile='$font':text='LAN対応の計測機器を、ひとつの体験へ。':fontcolor=white@0.9:fontsize=30:x=114:y=200,fade=t=in:st=0:d=1,fade=t=out:st=14:d=1[s1]"
$scene2 = "[1:v]scale=2048:1152:force_original_aspect_ratio=increase,crop=2048:1152,zoompan=z='min(zoom+0.0003,1.05)':d=1:s=1920x1080:fps=30,drawtext=fontfile='$font':text='接続した機器を、自動で判別。':fontcolor=white:fontsize=52:x=(w-text_w)/2:y=100,drawtext=fontfile='$font':text='PDT-FP1を含むLAN対応機器へ、対応を拡張。':fontcolor=white@0.9:fontsize=28:x=(w-text_w)/2:y=180,fade=t=in:st=0:d=1,fade=t=out:st=14:d=1[s2]"
$scene3 = "[2:v]scale=2048:1152:force_original_aspect_ratio=increase,crop=2048:1152,zoompan=z='min(zoom+0.0003,1.05)':d=1:s=1920x1080:fps=30,drawtext=fontfile='$font':text='つなぐ。見る。測る。':fontcolor=white:fontsize=66:x=110:y=110,drawtext=fontfile='$font':text='測定の現場を、もっと軽やかに。':fontcolor=white@0.9:fontsize=30:x=114:y=200,fade=t=in:st=0:d=1,fade=t=out:st=14:d=1[s3]"
$filter = "$scene1;$scene2;$scene3;[s1][s2][s3]concat=n=3:v=1:a=0,format=yuv420p[v]"

& $Ffmpeg -y `
    -loop 1 -framerate 30 -t 15 -i $landscape `
    -loop 1 -framerate 30 -t 15 -i $project `
    -loop 1 -framerate 30 -t 15 -i $landscape `
    -filter_complex $filter -map '[v]' -r 30 -c:v libx264 -preset medium -crf 20 -movflags +faststart $output

if ($LASTEXITCODE -ne 0) { throw "FFmpeg の動画生成に失敗しました。終了コード: $LASTEXITCODE" }
Get-Item -LiteralPath $output
