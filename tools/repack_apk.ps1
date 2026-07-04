# repack_apk.ps1 - Replace SOs in APK with packed versions, re-sign, deploy, test
$ErrorActionPreference = "Stop"

# === Paths ===
$projectRoot = "C:\Users\27194\Desktop\LianYu"
$originalApk = "$projectRoot\app\build\outputs\apk\release\app-release.apk"
$packedSecuritySo = "$projectRoot\core\security\build\intermediates\cxx\Release\1d71203t\obj\local\arm64-v8a\liblianyu_security.packed.so"
$packedShellSo = "$projectRoot\core\security\build\intermediates\cxx\Release\1d71203t\obj\local\arm64-v8a\liblianyu_shell.packed.so"
$unsignedApk = "$projectRoot\app\build\outputs\apk\release\app-unsigned-repacked.apk"
$alignedApk = "$projectRoot\app\build\outputs\apk\release\app-aligned.apk"
$signedApk = "$projectRoot\app\build\outputs\apk\release\app-release-repacked.apk"
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat"
$zipalign = "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\zipalign.exe"
$keystore = "$projectRoot\release.keystore"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# === Signing params - read from gradle.properties ===
$gradleProps = Get-Content "$projectRoot\gradle.properties" | ForEach-Object {
    $parts = $_ -split '=', 2
    if ($parts.Length -eq 2) { @{ Key = $parts[0].Trim(); Value = $parts[1].Trim() } }
}
$propsHash = @{}
foreach ($p in $gradleProps) { $propsHash[$p.Key] = $p.Value }

$storePass = $env:LIANYU_STORE_PASSWORD
if (-not $storePass) { $storePass = $propsHash["LIANYU_STORE_PASSWORD"] }
$keyPass = $env:LIANYU_KEY_PASSWORD
if (-not $keyPass) { $keyPass = $propsHash["LIANYU_KEY_PASSWORD"] }
$keyAlias = $env:LIANYU_KEY_ALIAS
if (-not $keyAlias) { $keyAlias = $propsHash["LIANYU_KEY_ALIAS"] }
if (-not $keyAlias) { $keyAlias = "your_alias" }

Write-Host "=== APK Repack Script ===" -ForegroundColor Cyan

# 1. Verify files
Write-Host "[1/6] Verifying files..." -ForegroundColor Yellow
if (-not (Test-Path $originalApk)) { throw "Original APK not found: $originalApk" }
if (-not (Test-Path $packedSecuritySo)) { throw "Packed SO not found: $packedSecuritySo" }
if (-not (Test-Path $packedShellSo)) { throw "Packed SO not found: $packedShellSo" }
if (-not (Test-Path $keystore)) { throw "Keystore not found: $keystore" }
if (-not $storePass -or -not $keyPass) { throw "Signing env vars not set: LIANYU_STORE_PASSWORD / LIANYU_KEY_PASSWORD" }
Write-Host "  All files verified OK" -ForegroundColor Green

# 2. Create repacked APK (replace SOs, remove signing)
Write-Host "[2/6] Creating repacked APK (replace SOs + remove signing)..." -ForegroundColor Yellow
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (Test-Path $unsignedApk) { Remove-Item $unsignedApk -Force }
if (Test-Path $signedApk) { Remove-Item $signedApk -Force }
if (Test-Path $alignedApk) { Remove-Item $alignedApk -Force }

$securitySoBytes = [System.IO.File]::ReadAllBytes($packedSecuritySo)
$shellSoBytes = [System.IO.File]::ReadAllBytes($packedShellSo)
Write-Host "  Packed liblianyu_security.so: $($securitySoBytes.Length) bytes"
Write-Host "  Packed liblianyu_shell.so: $($shellSoBytes.Length) bytes"

$srcZip = [System.IO.Compression.ZipFile]::OpenRead($originalApk)
$dstZip = [System.IO.Compression.ZipFile]::Open($unsignedApk, [System.IO.Compression.ZipArchiveMode]::Create)

$replacedCount = 0
$skippedCount = 0
$copiedCount = 0

foreach ($entry in $srcZip.Entries) {
    # Skip META-INF signing files
    if ($entry.FullName -match "^META-INF/.*\.(SF|RSA|DSA|EC)$" -or

        $entry.FullName -match "^META-INF/MANIFEST\.MF$") {
        $skippedCount++
        continue
    }

    # Determine compression level: resources.arsc and .so files must be stored uncompressed for Android R+
    $needNoCompression = ($entry.FullName -eq "resources.arsc") -or ($entry.FullName -match "\.so$")
    $compLevel = if ($needNoCompression) { [System.IO.Compression.CompressionLevel]::NoCompression } else { [System.IO.Compression.CompressionLevel]::Optimal }

    # Replace arm64-v8a SOs
    if ($entry.FullName -eq "lib/arm64-v8a/liblianyu_security.so") {
        $newEntry = $dstZip.CreateEntry($entry.FullName, $compLevel)
        $stream = $newEntry.Open()
        $stream.Write($securitySoBytes, 0, $securitySoBytes.Length)
        $stream.Close()
        $replacedCount++
        Write-Host "  Replaced: $($entry.FullName)" -ForegroundColor Green
        continue
    }

    if ($entry.FullName -eq "lib/arm64-v8a/liblianyu_shell.so") {
        $newEntry = $dstZip.CreateEntry($entry.FullName, $compLevel)
        $stream = $newEntry.Open()
        $stream.Write($shellSoBytes, 0, $shellSoBytes.Length)
        $stream.Close()
        $replacedCount++
        Write-Host "  Replaced: $($entry.FullName)" -ForegroundColor Green
        continue
    }

    # Copy other files
    $newEntry = $dstZip.CreateEntry($entry.FullName, $compLevel)
    $dstStream = $newEntry.Open()
    $srcStream = $entry.Open()
    $srcStream.CopyTo($dstStream)
    $srcStream.Close()
    $dstStream.Close()
    $copiedCount++
}

$srcZip.Dispose()
$dstZip.Dispose()
Write-Host "  Replaced $replacedCount SOs, skipped $skippedCount signing files, copied $copiedCount entries" -ForegroundColor Green

# 3. ZipAlign
Write-Host "[3/6] ZipAlign..." -ForegroundColor Yellow
& $zipalign -f -v 4 $unsignedApk $alignedApk 2>&1 | Select-Object -Last 3
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }
Write-Host "  ZipAlign done" -ForegroundColor Green

# 4. Sign
Write-Host "[4/6] Signing APK..." -ForegroundColor Yellow
& $apksigner sign --ks $keystore --ks-pass "pass:$storePass" --ks-key-alias $keyAlias --key-pass "pass:$keyPass" --out $signedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
Write-Host "  Signed: $signedApk" -ForegroundColor Green

# Verify signature
Write-Host "  Verifying signature..." -ForegroundColor Yellow
& $apksigner verify --verbose $signedApk 2>&1 | ForEach-Object { Write-Host "  $_" }

# 5. Deploy to device
Write-Host "[5/6] Deploying to device..." -ForegroundColor Yellow
& $adb uninstall com.lianyu.ai 2>&1 | ForEach-Object { Write-Host "  Uninstall: $_" }
& $adb install $signedApk
if ($LASTEXITCODE -ne 0) { throw "ADB install failed" }
Write-Host "  Install done" -ForegroundColor Green

# 6. Launch and check crash
Write-Host "[6/6] Launching app and checking crash..." -ForegroundColor Yellow
& $adb logcat -c
& $adb shell am start -n com.lianyu.ai/.MainActivity
Start-Sleep -Seconds 5

$crashLog = & $adb logcat -d -s AndroidRuntime:E DEBUG:E libc:E 2>&1
if ($crashLog -match "FATAL EXCEPTION|SIGSEGV|signal|tombstone|CRASH") {
    Write-Host "  [WARNING] Crash signal detected!" -ForegroundColor Red
    Write-Host $crashLog
} else {
    Write-Host "  [OK] No crash signal detected" -ForegroundColor Green
}

$runningApp = & $adb shell pidof com.lianyu.ai 2>&1
if ($runningApp -and $runningApp -match "^\d+$") {
    Write-Host "  [OK] App is running (PID: $runningApp)" -ForegroundColor Green
} else {
    Write-Host "  [WARNING] App is not running" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Done ===" -ForegroundColor Cyan
Write-Host "Signed APK: $signedApk"
