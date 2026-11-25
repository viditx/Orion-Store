<#
    Builds the Windows icon assets from Orion's own Android launcher artwork, with an Apple-style
    squircle edge.

    The artwork is Orion's, not ours: the PNG in mipmap-*/ic_launcher.png is the source. It is
    cropped to its own opaque bounds, scaled, and clipped to a continuous-curvature squircle
    (superellipse, n=5) so the icon reads the way a modern launcher icon does. Every .ico entry is
    rendered from the density bucket Android ships for that size, so the Windows icon is the same
    artwork at the same fidelity Android ships it.
#>
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Multiplies a bitmap's alpha channel by a mask's alpha channel. Done in C# because a per-pixel
# pass over the 256px master in PowerShell is far too slow.
if (-not ('OrionAlphaMask' -as [type])) {
    Add-Type -ReferencedAssemblies System.Drawing @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class OrionAlphaMask
{
    public static void Apply(Bitmap dst, Bitmap mask)
    {
        int w = dst.Width, h = dst.Height;
        Rectangle rect = new Rectangle(0, 0, w, h);
        BitmapData bd = dst.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        BitmapData bm = mask.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        try
        {
            int stride = bd.Stride;
            byte[] d = new byte[stride * h];
            byte[] m = new byte[stride * h];
            Marshal.Copy(bd.Scan0, d, 0, d.Length);
            Marshal.Copy(bm.Scan0, m, 0, m.Length);
            for (int y = 0; y < h; y++)
            {
                int off = y * stride;
                for (int x = 0; x < w; x++)
                {
                    int i = off + x * 4;
                    int ma = m[i + 3];
                    if (ma != 255)
                    {
                        int da = d[i + 3];
                        d[i + 3] = ma == 0 ? (byte)0 : (byte)((da * ma + 127) / 255);
                    }
                }
            }
            Marshal.Copy(d, 0, bd.Scan0, d.Length);
        }
        finally
        {
            dst.UnlockBits(bd);
            mask.UnlockBits(bm);
        }
    }
}
'@
}

$repo = Split-Path -Parent $PSScriptRoot
$res = Join-Path $repo 'app\src\main\res'
$outDir = Join-Path $repo 'desktop\parity-shots'

# Android density bucket -> png size
$density = [ordered]@{
    'ldpi'     = 36
    'mdpi'     = 48
    'hdpi'     = 72
    'xhdpi'    = 96
    'xxhdpi'   = 144
    'xxxhdpi'  = 192
}

function Get-Source([int]$size) {
    foreach ($k in $density.Keys) {
        if ($density[$k] -ge $size) {
            $p = Join-Path $res "mipmap-$k\ic_launcher.png"
            if (Test-Path $p) { return $p }
        }
    }
    return (Join-Path $res 'mipmap-xxxhdpi\ic_launcher.png')
}

# Reduces a bitmap to a target size in halvings. A single large step with a scaled bicubic
# kernel still aliases; halving keeps the edge clean.
function Downscale([System.Drawing.Bitmap]$src, [int]$target) {
    $cur = $src
    while ($cur.Width -gt $target) {
        $next = [Math]::Max($target, [int]($cur.Width / 2))
        $step = New-Object System.Drawing.Bitmap $next, $next, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $gs = [System.Drawing.Graphics]::FromImage($step)
        $gs.Clear([System.Drawing.Color]::Transparent)
        $gs.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $gs.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $gs.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $gs.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $gs.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $gs.DrawImage($cur, 0, 0, $next, $next)
        $gs.Dispose()
        if ($cur -ne $src) { $cur.Dispose() }
        $cur = $step
    }
    if ($cur -eq $src) {
        $copy = New-Object System.Drawing.Bitmap $target, $target, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $gc = [System.Drawing.Graphics]::FromImage($copy)
        $gc.DrawImage($src, 0, 0, $target, $target)
        $gc.Dispose()
        return $copy
    }
    return $cur
}

# --- Apple-style squircle: superellipse |x|^n + |y|^n = 1, n = 5 ---
function Get-SquirclePath([double]$size, [int]$perQuadrant = 24) {
    $n = 5.0
    $pts = New-Object System.Collections.Generic.List[System.Drawing.PointF]
    $quadrants = @(
        @{ sx = 1.0;  sy = 1.0;  a0 = 0.0;   a1 = 90.0 },
        @{ sx = -1.0; sy = 1.0;  a0 = 90.0;  a1 = 180.0 },
        @{ sx = -1.0; sy = -1.0; a0 = 180.0; a1 = 270.0 },
        @{ sx = 1.0;  sy = -1.0; a0 = 270.0; a1 = 360.0 }
    )
    foreach ($q in $quadrants) {
        for ($i = 0; $i -le $perQuadrant; $i++) {
            $t = $q.a0 + ($q.a1 - $q.a0) * $i / $perQuadrant
            $rad = [Math]::PI * $t / 180.0
            $cx = [Math]::Sign($q.sx) * [Math]::Pow([Math]::Abs([Math]::Cos($rad)), 2.0 / $n)
            $cy = [Math]::Sign($q.sy) * [Math]::Pow([Math]::Abs([Math]::Sin($rad)), 2.0 / $n)
            $pts.Add((New-Object System.Drawing.PointF ([float](($cx + 1) / 2 * $size)), ([float](($cy + 1) / 2 * $size))))
        }
    }
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddPolygon($pts.ToArray())
    return $path
}

function Get-ArtworkBounds([System.Drawing.Bitmap]$bmp) {
    $minX = $bmp.Width; $minY = $bmp.Height; $maxX = -1; $maxY = -1
    for ($y = 0; $y -lt $bmp.Height; $y += 1) {
        for ($x = 0; $x -lt $bmp.Width; $x += 1) {
            if ($bmp.GetPixel($x, $y).A -gt 16) {
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    return @($minX, $minY, ($maxX - $minX + 1), ($maxY - $minY + 1))
}

function New-SquircleIcon([string]$srcPath, [int]$size) {
    # Two things are kept apart on purpose:
    #   * the shape comes from a mask, built at 8x and reduced, so the edge is a smooth alpha
    #     ramp instead of a staircase. (GDI+ clip regions are binary, which is what made the old
    #     edge look like pixels.)
    #   * the artwork is drawn straight to the final size, so the glyph stays crisp instead of
    #     being softened by a double resample.
    $ss = 8

    # --- shape mask ---
    $big = $size * $ss
    $maskBig = New-Object System.Drawing.Bitmap $big, $big, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gm = [System.Drawing.Graphics]::FromImage($maskBig)
    $gm.Clear([System.Drawing.Color]::Transparent)
    $gm.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $gm.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $gm.FillPath([System.Drawing.Brushes]::White, (Get-SquirclePath $big 128))
    $gm.Dispose()
    $mask = Downscale $maskBig $size

    # --- artwork, one resample, at final size ---
    $src = [System.Drawing.Bitmap]::FromFile($srcPath)
    $b = Get-ArtworkBounds $src
    $crop = New-Object System.Drawing.Rectangle $b[0], $b[1], $b[2], $b[3]
    $art = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $ga = [System.Drawing.Graphics]::FromImage($art)
    $ga.Clear([System.Drawing.Color]::Transparent)
    $ga.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $ga.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $ga.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $ga.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $size, $size), $crop, 'Pixel')
    $ga.Dispose(); $src.Dispose()

    # --- apply the shape ---
    [OrionAlphaMask]::Apply($art, $mask)
    $mask.Dispose(); $maskBig.Dispose()
    return $art
}

# ---- the 256px PNG ----
$icon = New-SquircleIcon (Join-Path $res 'mipmap-xxxhdpi\ic_launcher.png') 256
$pngPath = Join-Path $repo 'desktop\src\main\resources\orion_icon.png'
$icon.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$icon.Dispose()
Copy-Item $pngPath (Join-Path $repo 'installer\orion_icon.png') -Force
"wrote 256px squircle png"

# ---- multi-size .ico (BMP entries, maximum Windows compatibility) ----
function New-OrionIco([string]$outPath, [int[]]$sizes) {
    $entries = @()
    foreach ($s in $sizes) {
        $bmp = New-SquircleIcon (Get-Source $s) $s
        $rect = New-Object System.Drawing.Rectangle 0, 0, $s, $s
        $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $stride = $s * 4
        $bytes = New-Object byte[] ($stride * $s)
        # The source pointer has to advance a row at a time, otherwise every row ends up as a
        # copy of the first one.
        for ($y = 0; $y -lt $s; $y++) {
            [System.Runtime.InteropServices.Marshal]::Copy(
                [System.IntPtr]::Add($data.Scan0, $y * $stride), $bytes, $y * $stride, $stride)
        }
        $bmp.UnlockBits($data)
        $bmp.Dispose()

        # GDI+ hands the rows back top-down, a BMP stores them bottom-up, so the rows are flipped.
        $xor = New-Object byte[] ($stride * $s)
        for ($y = 0; $y -lt $s; $y++) {
            [Array]::Copy($bytes, ($s - 1 - $y) * $stride, $xor, $y * $stride, $stride)
        }
        # AND mask: 1bpp, rows padded to 4 bytes
        $maskStride = [int]([Math]::Ceiling($s / 32.0) * 4)
        $mask = New-Object byte[] ($maskStride * $s)
        $header = New-Object byte[] 40
        [Array]::Copy([System.BitConverter]::GetBytes([int]40), 0, $header, 0, 4)
        [Array]::Copy([System.BitConverter]::GetBytes([int]$s), 0, $header, 4, 4)
        [Array]::Copy([System.BitConverter]::GetBytes([int]($s * 2)), 0, $header, 8, 4)
        [Array]::Copy([System.BitConverter]::GetBytes([int]1), 0, $header, 12, 4)
        [Array]::Copy([System.BitConverter]::GetBytes([int]32), 0, $header, 14, 4)
        $img = New-Object byte[] (40 + $xor.Length + $mask.Length)
        [Array]::Copy($header, 0, $img, 0, 40)
        [Array]::Copy($xor, 0, $img, 40, $xor.Length)
        [Array]::Copy($mask, 0, $img, 40 + $xor.Length, $mask.Length)
        $entries += ,@{ size = $s; data = $img }
    }

    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter $ms
    $bw.Write([int16]0); $bw.Write([int16]1); $bw.Write([int16]$entries.Count)
    $offset = 6 + 16 * $entries.Count
    foreach ($e in $entries) {
        $w = if ($e.size -ge 256) { 0 } else { $e.size }
        $bw.Write([byte]$w); $bw.Write([byte]$w); $bw.Write([byte]0); $bw.Write([byte]0)
        $bw.Write([int16]1); $bw.Write([int16]32)
        $bw.Write([int]$e.data.Length); $bw.Write([int]$offset)
        $offset += $e.data.Length
    }
    foreach ($e in $entries) { $bw.Write($e.data) }
    $bw.Flush()
    [System.IO.File]::WriteAllBytes($outPath, $ms.ToArray())
    $ms.Dispose()
}

New-OrionIco (Join-Path $repo 'desktop\src\main\resources\orion_icon.ico') @(16, 32, 48, 64, 128, 256)
New-OrionIco (Join-Path $repo 'installer\orion_icon.ico') @(16, 32, 48, 64, 128, 256)
"wrote ico files"

# ---- the squircle outline as XAML path data, for the installer's own surfaces ----
$path = Get-SquirclePath 100 24
$pts = $path.PathPoints
$sb = New-Object System.Text.StringBuilder
[void]$sb.Append('M ')
for ($i = 0; $i -lt $pts.Count; $i++) {
    if ($i -gt 0) { [void]$sb.Append(' L ') }
    [void]$sb.Append(('{0:0.##},{1:0.##}' -f $pts[$i].X, $pts[$i].Y))
}
[void]$sb.Append(' Z')
$xamlPath = Join-Path $outDir 'squircle_path.txt'
[System.IO.File]::WriteAllText($xamlPath, $sb.ToString())
"squircle path data ($($pts.Count) points) -> $xamlPath"
