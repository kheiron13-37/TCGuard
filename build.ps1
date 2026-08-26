$baseDir = $PSScriptRoot
$srcDir = "$baseDir\src"
$binDir = "$baseDir\bin"
$libsDir = "$baseDir\libs"
$resDir = "$baseDir\resources"
$distDir = "$baseDir\dist"
$targetJar = "$distDir\TCGuard.jar"
$testServerPluginPath = "$baseDir\test-server\plugins\TCGuard.jar"

if (Test-Path $binDir) { Remove-Item -Recurse -Force $binDir }
New-Item -ItemType Directory -Path $binDir -Force | Out-Null
if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir -Force | Out-Null }

$cp = "$libsDir\spigot-1.8.8.jar;$libsDir\ProtocolLib.jar"
$sources = Get-ChildItem -Path $srcDir -Filter *.java -Recurse | Select-Object -ExpandProperty FullName

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TCGuard Derleniyor...                 " -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "[1/3] Java dosyalari derleniyor..."

& javac -cp $cp -d $binDir --release 8 -encoding UTF-8 $sources

if ($LASTEXITCODE -ne 0) {
    Write-Host "[HATA] Derleme basarisiz oldu!" -ForegroundColor Red
    exit 1
}

Write-Host "[2/3] Kaynak dosyalari kopyalaniyor..."
if (Test-Path $resDir) {
    Copy-Item "$resDir\*" $binDir -Recurse -Force
}

Write-Host "[3/3] JAR paketi olusturuluyor..."
& jar -cf $targetJar -C $binDir .

# Release ve GitHub/Release klasorlerine kopyala
$releaseDir = "$baseDir\release"
$ghReleaseDir = "$baseDir\GitHub\Release"
if (-not (Test-Path $releaseDir)) { New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null }
if (-not (Test-Path $ghReleaseDir)) { New-Item -ItemType Directory -Path $ghReleaseDir -Force | Out-Null }

Copy-Item $targetJar "$releaseDir\TCGuard-2.0-ENTERPRISE.jar" -Force
Copy-Item $targetJar "$ghReleaseDir\TCGuard-2.0-ENTERPRISE.jar" -Force
Write-Host "[+] Release ve GitHub/Release klasorlerine TCGuard-2.0-ENTERPRISE.jar kopyalandi." -ForegroundColor Green

# Test sunucusuna kopyala
if (Test-Path "$baseDir\test-server\plugins") {
    $oldJar = "$baseDir\test-server\plugins\MCGamerGuard.jar"
    if (Test-Path $oldJar) { Remove-Item -Force $oldJar }
    Copy-Item $targetJar $testServerPluginPath -Force
    Write-Host "[+] Test sunucusu plugins/ klasorune TCGuard.jar kopyalandi." -ForegroundColor Green
}

Write-Host "========================================" -ForegroundColor Green
Write-Host "  BASARILI! TCGuard.jar HAZIR.           " -ForegroundColor Green
Write-Host "  Konum: $targetJar                      " -ForegroundColor White
Write-Host "  Release: $ghReleaseDir\TCGuard-2.0-ENTERPRISE.jar" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
