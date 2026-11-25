<#
    Rebuilds the Windows setup executable from the current sources.

    Two traps this script guards against:
      * packageDistributionForCurrentOS only wraps the *existing* app image in an installer - it
        does not rebuild the image, so the app jar inside payload.zip can silently stay months
        old. createDistributable is the task that regenerates the app image.
      * createDistributable can report itself UP-TO-DATE while its jpackage output still holds an
        older app jar, so --rerun-tasks forces the chain to rebuild.
    The step below then fails loudly if the packaged jar is older than the compiled classes.
#>
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

Write-Host '=== 1. rebuilding the desktop app image ==='
.\gradlew.bat :desktop:createDistributable --no-daemon --rerun-tasks | Select-Object -Last 5

$appDir = Join-Path $repo 'desktop\build\compose\binaries\main\app\OrionStore'
if (-not (Test-Path $appDir)) { throw "app dir missing: $appDir" }

# The app jar must be newer than the compiled classes, otherwise the payload is stale.
$appJar = Get-ChildItem (Join-Path $appDir 'app') -Filter 'desktop-*.jar' |
    Where-Object { $_.Name -notlike 'desktop-jvm-*' } | Select-Object -First 1
$classes = Get-ChildItem (Join-Path $repo 'desktop\build\classes\kotlin\main\com\orion\desktop') -Filter '*.class' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $appJar) { throw "app jar missing in $appDir" }
"app jar : $($appJar.Name)  $($appJar.LastWriteTime)"
"newest class: $($classes.Name)  $($classes.LastWriteTime)"
if ($appJar.LastWriteTime -lt $classes.LastWriteTime) {
    throw "STALE DISTRIBUTION: the packaged app jar is older than the compiled classes."
}

Write-Host '=== 2. refreshing payload.zip ==='
$payload = Join-Path $repo 'installer\payload.zip'
if (Test-Path $payload) { Remove-Item $payload -Force }
Compress-Archive -Path (Join-Path $appDir '*') -DestinationPath $payload -CompressionLevel Optimal
"payload: $((Get-Item $payload).Length) bytes"

Write-Host '=== 3. publishing the installer ==='
Push-Location (Join-Path $repo 'installer')
dotnet publish OrionInstaller.csproj -c Release -r win-x64 --self-contained true -o (Join-Path $repo 'Orion-Setup-Build') --nologo 2>&1 | Select-Object -Last 6
Pop-Location

$exe = Join-Path $repo 'Orion-Setup-Build\OrionInstaller.exe'
Copy-Item $exe (Join-Path $repo 'OrionStore-Setup.exe') -Force
Get-ChildItem (Join-Path $repo 'OrionStore-Setup.exe'), $exe, $payload |
    Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize
Write-Host 'DONE'
