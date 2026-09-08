# Генератор PNG-ассетов (иконка лаунчера + TV-баннер) для video_viewer_android.
# Запуск: powershell -ExecutionPolicy Bypass -File tools\gen_icons.ps1
# Нужен только для перегенерации картинок; сами .png коммитятся в репозиторий.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$res = Join-Path $PSScriptRoot '..\app\src\main\res'
$bg      = [System.Drawing.Color]::FromArgb(255, 10, 14, 19)    # #0A0E13
$panel   = [System.Drawing.Color]::FromArgb(255, 21, 27, 46)    # #151B2E
$ring    = [System.Drawing.Color]::FromArgb(255, 42, 58, 94)     # #2A3A5E
$accent  = [System.Drawing.Color]::FromArgb(255, 62, 194, 255)   # #3EC2FF
$text    = [System.Drawing.Color]::FromArgb(255, 238, 238, 238)  # #EEEEEE

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear($bg)
    return @($bmp, $g)
}

function Draw-PlayLogo($g, [double]$cx, [double]$cy, [double]$R, [double]$alpha) {
    # Тёмный диск с тонким кольцом + треугольник Play
    $brushPanel = New-Object System.Drawing.SolidBrush($panel)
    $g.FillEllipse($brushPanel, [float]($cx-$R), [float]($cy-$R), [float](2*$R), [float](2*$R))
    $brushPanel.Dispose()
    $penRing = New-Object System.Drawing.Pen($ring, [float]([Math]::Max(1.5, $R*0.06)))
    $g.DrawEllipse($penRing, [float]($cx-$R), [float]($cy-$R), [float](2*$R), [float](2*$R))
    $penRing.Dispose()
    $brushAcc = New-Object System.Drawing.SolidBrush($accent)
    # Вершины треугольника Play (чуть смещён вправо от центра для оптического баланса)
    $w = $R * 0.62; $h = $R * 0.78
    $pts = @(
        (New-Object System.Drawing.PointF([float]($cx - $w*0.30), [float]($cy - $h*0.55))),
        (New-Object System.Drawing.PointF([float]($cx - $w*0.30), [float]($cy + $h*0.55))),
        (New-Object System.Drawing.PointF([float]($cx + $w*0.78), [float]$cy))
    )
    $g.FillPolygon($brushAcc, $pts)
    $brushAcc.Dispose()
}

function Save-Png($bmp, [string]$path) {
    $dir = Split-Path -Parent $path
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# ---------- Launcher icons (полный квадрат, без прозрачности) ----------
$sizes = @{
    'mdpi'    = 48
    'hdpi'    = 72
    'xhdpi'   = 96
    'xxhdpi'  = 144
    'xxxhdpi' = 192
}
foreach ($d in $sizes.Keys) {
    $S = $sizes[$d]
    $c = New-Canvas $S $S
    $g = $c[1]
    Draw-PlayLogo $g ($S/2.0) ($S/2.0) ($S*0.40) 1.0
    Save-Png $c[0] (Join-Path $res "mipmap-$d\ic_launcher.png")
    $g.Dispose()
}

# ---------- TV banner (320x180 @ xhdpi и производные) ----------
function Draw-Banner([string]$dpi, [int]$W, [int]$H) {
    $c = New-Canvas $W $H
    $g = $c[1]
    $R = $H * 0.36
    $cx = $H * 0.5
    Draw-PlayLogo $g $cx ($H/2.0) $R 1.0

    $label = 'Video Viewer TV'
    $fontSize = [float]($H * 0.26)
    $font = New-Object System.Drawing.Font('Segoe UI', $fontSize, [System.Drawing.FontStyle]::Bold)
    $brushText = New-Object System.Drawing.SolidBrush($text)
    $x0 = $cx + $R + ($H * 0.08)
    $maxW = $W - $x0 - ($H * 0.06)
    # Уменьшаем шрифт, пока текст не влезет
    $sf = New-Object System.Drawing.StringFormat
    $sf.Trimming = [System.Drawing.StringTrimming]::None
    while ($true) {
        $sz = $g.MeasureString($label, $font)
        if ($sz.Width -le $maxW -or $fontSize -lt 6) { break }
        $fontSize = $fontSize * 0.9
        $font.Dispose()
        $font = New-Object System.Drawing.Font('Segoe UI', $fontSize, [System.Drawing.FontStyle]::Bold)
    }
    $sz = $g.MeasureString($label, $font)
    $g.DrawString($label, $font, $brushText, [float]$x0, [float](($H - $sz.Height) / 2.0))
    $font.Dispose(); $brushText.Dispose(); $sf.Dispose()
    Save-Png $c[0] (Join-Path $res "drawable-$dpi\tv_banner.png")
    $g.Dispose()
}

Draw-Banner 'mdpi'   160  90
Draw-Banner 'hdpi'   240  135
Draw-Banner 'xhdpi'  320  180
Draw-Banner 'xxhdpi' 480  270

Write-Output 'Icons & banner generated.'
