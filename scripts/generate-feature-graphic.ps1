param(
    [string]$OutputPath = "artifacts/play/feature-graphic-en-US.png"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function New-RoundedRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $r * 2
    $path.AddArc($x, $y, $diameter, $diameter, 180, 90)
    $path.AddArc($x + $w - $diameter, $y, $diameter, $diameter, 270, 90)
    $path.AddArc($x + $w - $diameter, $y + $h - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($x, $y + $h - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-RoundedImage($graphics, $image, [float]$x, [float]$y, [float]$w, [float]$h, [float]$radius) {
    $path = New-RoundedRectPath $x $y $w $h $radius
    $state = $graphics.Save()
    $graphics.SetClip($path)
    $graphics.DrawImage($image, [System.Drawing.RectangleF]::new($x, $y, $w, $h))
    $graphics.Restore($state)

    $borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(48, 255, 255, 255), 1.4)
    $graphics.DrawPath($borderPen, $path)
    $borderPen.Dispose()
    $path.Dispose()
}

function New-FittedFont(
    $graphics,
    [string]$fontFamily,
    [float]$startingSize,
    [System.Drawing.FontStyle]$style,
    [string]$text,
    [float]$maxWidth,
    [float]$maxHeight
) {
    $size = $startingSize
    while ($size -gt 10) {
        $font = New-Object System.Drawing.Font($fontFamily, $size, $style)
        $measured = $graphics.MeasureString($text, $font, [int][Math]::Ceiling($maxWidth))
        if ($measured.Width -le $maxWidth -and $measured.Height -le $maxHeight) {
            return $font
        }
        $font.Dispose()
        $size -= 1
    }

    return New-Object System.Drawing.Font($fontFamily, 10, $style)
}

function Draw-PhoneFrame($graphics, $image, [float]$x, [float]$y, [float]$w, [float]$h, [float]$angle) {
    $state = $graphics.Save()
    $graphics.TranslateTransform($x + ($w / 2), $y + ($h / 2))
    $graphics.RotateTransform($angle)

    $shadowPath = New-RoundedRectPath (-$w / 2 + 8) (-$h / 2 + 12) $w $h 28
    $shadowBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(78, 0, 0, 0))
    $graphics.FillPath($shadowBrush, $shadowPath)
    $shadowBrush.Dispose()
    $shadowPath.Dispose()

    $outerPath = New-RoundedRectPath (-$w / 2) (-$h / 2) $w $h 28
    $outerBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 20, 24, 29))
    $graphics.FillPath($outerBrush, $outerPath)
    $outerBrush.Dispose()

    $screenX = -$w / 2 + 10
    $screenY = -$h / 2 + 10
    $screenW = $w - 20
    $screenH = $h - 20
    Draw-RoundedImage $graphics $image $screenX $screenY $screenW $screenH 20

    $notchBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 20, 24, 29))
    $graphics.FillRectangle($notchBrush, -28, -$h / 2 + 8, 56, 8)
    $notchBrush.Dispose()

    $framePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(54, 255, 255, 255), 1.2)
    $graphics.DrawPath($framePen, $outerPath)
    $framePen.Dispose()
    $outerPath.Dispose()

    $graphics.Restore($state)
}

function Get-AppName() {
    [xml]$stringsXml = Get-Content (Resolve-Path "app/src/main/res/values/strings.xml")
    $appName = $stringsXml.resources.string |
        Where-Object { $_.name -eq "app_name" } |
        Select-Object -First 1

    if ($null -eq $appName -or [string]::IsNullOrWhiteSpace($appName.'#text')) {
        throw "Unable to resolve app_name from app/src/main/res/values/strings.xml"
    }

    return $appName.'#text'
}

function Draw-AccentBand($graphics, [float]$x, [float]$y, [float]$w) {
    $redPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 216, 46, 54), 8)
    $orangePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 247, 141, 32), 8)
    $yellowPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 255, 210, 64), 8)

    $graphics.DrawLine($redPen, $x, $y, $x + $w, $y)
    $graphics.DrawLine($orangePen, $x, $y + 14, $x + $w - 28, $y + 14)
    $graphics.DrawLine($yellowPen, $x, $y + 28, $x + $w - 56, $y + 28)

    $redPen.Dispose()
    $orangePen.Dispose()
    $yellowPen.Dispose()
}

$outputFile = Join-Path (Get-Location) $OutputPath
$outputDir = Split-Path -Parent $outputFile
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$shotDir = Resolve-Path "artifacts/play-screenshots/en-US"
$shot1 = [System.Drawing.Image]::FromFile((Join-Path $shotDir "01-home.png"))
$shot2 = [System.Drawing.Image]::FromFile((Join-Path $shotDir "02-search-results.png"))
$shot3 = [System.Drawing.Image]::FromFile((Join-Path $shotDir "03-expanded-route.png"))
$trainMark = [System.Drawing.Image]::FromFile((Resolve-Path "app/src/main/res/img.png"))

$canvasWidth = 1024
$canvasHeight = 500
$bitmap = New-Object System.Drawing.Bitmap($canvasWidth, $canvasHeight)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$backgroundRect = New-Object System.Drawing.Rectangle(0, 0, $canvasWidth, $canvasHeight)
$backgroundBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $backgroundRect,
    [System.Drawing.Color]::FromArgb(255, 18, 21, 26),
    [System.Drawing.Color]::FromArgb(255, 54, 16, 20),
    8
)
$graphics.FillRectangle($backgroundBrush, $backgroundRect)
$backgroundBrush.Dispose()

foreach ($shape in @(
    @{ X = -110; Y = -60; W = 320; H = 320; Color = [System.Drawing.Color]::FromArgb(36, 255, 210, 64) },
    @{ X = 90; Y = 280; W = 280; H = 280; Color = [System.Drawing.Color]::FromArgb(24, 255, 255, 255) },
    @{ X = 744; Y = -40; W = 340; H = 340; Color = [System.Drawing.Color]::FromArgb(22, 216, 46, 54) }
)) {
    $shapeBrush = New-Object System.Drawing.SolidBrush($shape.Color)
    $graphics.FillEllipse($shapeBrush, $shape.X, $shape.Y, $shape.W, $shape.H)
    $shapeBrush.Dispose()
}

$panelX = 34
$panelY = 34
$panelWidth = 404
$panelHeight = 432
$panelPath = New-RoundedRectPath $panelX $panelY $panelWidth $panelHeight 34
$panelBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(34, 255, 255, 255))
$panelPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(44, 255, 255, 255), 1.4)
$graphics.FillPath($panelBrush, $panelPath)
$graphics.DrawPath($panelPen, $panelPath)
$panelBrush.Dispose()
$panelPen.Dispose()
$panelPath.Dispose()

$iconShadowBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, 0, 0, 0))
$graphics.FillEllipse($iconShadowBrush, 68, 72, 132, 132)
$iconShadowBrush.Dispose()
$graphics.DrawImage($trainMark, 60, 60, 132, 132)

Draw-AccentBand $graphics 64 212 214

$whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(246, 255, 255, 255))
$mutedBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(216, 233, 236, 241))
$footerBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(178, 232, 236, 241))

$textFormat = New-Object System.Drawing.StringFormat
$textFormat.Alignment = [System.Drawing.StringAlignment]::Near
$textFormat.LineAlignment = [System.Drawing.StringAlignment]::Near
$textFormat.Trimming = [System.Drawing.StringTrimming]::Word
$textFormat.FormatFlags = [System.Drawing.StringFormatFlags]::LineLimit

$appName = Get-AppName
$headline = "Montenegro train schedules"
$bodyText = "Search routes, inspect every stop, and set reminders before departure."
$footerText = "Preview built from the current en-US screens"

$titleFont = New-FittedFont $graphics "Segoe UI Semibold" 37 ([System.Drawing.FontStyle]::Bold) $appName 300 56
$headlineFont = New-FittedFont $graphics "Segoe UI Semibold" 27 ([System.Drawing.FontStyle]::Bold) $headline 310 84
$bodyFont = New-FittedFont $graphics "Segoe UI" 17 ([System.Drawing.FontStyle]::Regular) $bodyText 314 110
$footerFont = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Regular)

$graphics.DrawString($appName, $titleFont, $whiteBrush, [System.Drawing.RectangleF]::new(64, 246, 300, 56), $textFormat)
$graphics.DrawString($headline, $headlineFont, $whiteBrush, [System.Drawing.RectangleF]::new(64, 298, 316, 52), $textFormat)
$graphics.DrawString($bodyText, $bodyFont, $mutedBrush, [System.Drawing.RectangleF]::new(64, 364, 314, 96), $textFormat)

$phoneWidth = 168
$phoneHeight = 336
Draw-PhoneFrame $graphics $shot1 492 98 $phoneWidth $phoneHeight -10
Draw-PhoneFrame $graphics $shot2 650 64 $phoneWidth $phoneHeight 0
Draw-PhoneFrame $graphics $shot3 810 98 $phoneWidth $phoneHeight 10

$graphics.DrawString(
    $footerText,
    $footerFont,
    $footerBrush,
    [System.Drawing.RectangleF]::new(586, 448, 260, 20),
    $textFormat
)

$bitmap.Save($outputFile, [System.Drawing.Imaging.ImageFormat]::Png)

$textFormat.Dispose()
$shot1.Dispose()
$shot2.Dispose()
$shot3.Dispose()
$trainMark.Dispose()
$titleFont.Dispose()
$headlineFont.Dispose()
$bodyFont.Dispose()
$footerFont.Dispose()
$whiteBrush.Dispose()
$mutedBrush.Dispose()
$footerBrush.Dispose()
$graphics.Dispose()
$bitmap.Dispose()

$image = [System.Drawing.Image]::FromFile($outputFile)
[pscustomobject]@{
    FullName = $outputFile
    Width = $image.Width
    Height = $image.Height
    Length = (Get-Item $outputFile).Length
}
$image.Dispose()
