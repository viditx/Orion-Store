<#
    Downloads Google Sans (SIL Open Font License) as static TrueType files and subsets them.

    Google Fonts serves one static instance per weight when it is asked with a non-browser user
    agent, which is what WPF needs (it cannot pick weights out of a variable font). The subsetter
    then cuts each file down to the characters Orion renders, so four weights cost a few hundred KB
    instead of the ~8 MB the full family would.
#>
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$weights = @(
    @{ weight = 400; name = 'GoogleSans-Regular.ttf' },
    @{ weight = 500; name = 'GoogleSans-Medium.ttf' },
    @{ weight = 600; name = 'GoogleSans-SemiBold.ttf' },
    @{ weight = 700; name = 'GoogleSans-Bold.ttf' }
)

$full = Join-Path $repo 'installer\fonts\_full'
New-Item -ItemType Directory -Force -Path $full | Out-Null

# A plain user agent makes the API answer with a real TrueType file per weight.
$ua = 'curl/7.68.0'

foreach ($w in $weights) {
    $css = (Invoke-WebRequest -Uri "https://fonts.googleapis.com/css2?family=Google+Sans:wght@$($w.weight)" -UserAgent $ua -UseBasicParsing).Content
    $m = [regex]::Match($css, 'src:\s*url\(([^)]+)\)')
    if (-not $m.Success) { throw "no font url for weight $($w.weight)" }
    $dest = Join-Path $full $w.name
    Invoke-WebRequest -Uri $m.Groups[1].Value -OutFile $dest -UseBasicParsing -UserAgent $ua
    $head = [System.IO.File]::ReadAllBytes($dest)[0..3]
    if ($head[0] -ne 0x00 -or $head[1] -ne 0x01) { throw "not a TrueType file: $dest" }
    "downloaded $w.name ($([math]::Round((Get-Item $dest).Length/1MB,2)) MB)"
}

Write-Host '=== subsetting ==='
& python (Join-Path $PSScriptRoot 'subset_fonts.py') $full `
    (Join-Path $repo 'installer\fonts') `
    (Join-Path $repo 'desktop\src\main\resources\fonts')
if ($LASTEXITCODE -ne 0) { throw 'subsetting failed' }

# the full-size downloads are only a build input
Remove-Item $full -Recurse -Force

Add-Type -AssemblyName PresentationCore
foreach ($f in Get-ChildItem (Join-Path $repo 'installer\fonts') -Filter '*.ttf') {
    $gt = New-Object System.Windows.Media.GlyphTypeface($f.FullName)
    "  $($f.Name): family='$(($gt.FamilyNames.Values)[0])' face='$(($gt.FaceNames.Values)[0])' weight=$($gt.Weight) style=$($gt.Style)"
}
