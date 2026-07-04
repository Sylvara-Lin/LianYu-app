# ================================================================
# LianYu Release Build Script (PowerShell) — 完整加固构建流程
# ================================================================
# 这个脚本执行完整的 release 构建，包括：
#   1. 生成白盒AES表（使用真正的随机密钥）
#   2. 构建 Release APK
#   3. 提取 APK 签名证书哈希
#   4. 更新 native-bridge.cpp 中的签名验证哈希
#   5. 生成 APK 完整性校验哈希（DEX/SO/ARSC）
#   6. 重新编译 Native 并重新打包
#
# Usage:
#   .\tools\build_release.ps1
#   .\tools\build_release.ps1 -Keystore ..\release.keystore -Alias lianyu
# ================================================================

param(
    [string]$Keystore = "",
    [string]$Alias = "",
    [string]$Password = ""
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
Set-Location $ProjectDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "LianYu Release Build — Hardened" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Step 1: Generate White-Box AES tables
Write-Host ""
Write-Host "[Step 1/6] Generating White-Box AES tables..." -ForegroundColor Yellow
if (Test-Path ".lianyu_wb_key") {
    Write-Host "[*] Using existing key from .lianyu_wb_key"
    python tools/setup_wb_key.py
} else {
    Write-Host "[*] Generating new random key..."
    python tools/setup_wb_key.py --generate
}

# Step 2: Initial Release Build
Write-Host ""
Write-Host "[Step 2/6] Building Release APK (initial)..." -ForegroundColor Yellow
.\gradlew assembleRelease

$ApkPath = "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $ApkPath)) {
    Write-Host "ERROR: APK not found at $ApkPath" -ForegroundColor Red
    exit 1
}

# Step 3: Extract signature hash and patch native-bridge.cpp
Write-Host ""
Write-Host "[Step 3/6] Extracting APK signature hash..." -ForegroundColor Yellow
if ($Keystore -and $Alias) {
    python tools/extract_apk_signature.py `
        --keystore "$Keystore" --alias "$Alias" --password "$Password" `
        --patch
} else {
    python tools/extract_apk_signature.py "$ApkPath" --patch
}

# Step 4: Generate APK integrity hashes
Write-Host ""
Write-Host "[Step 4/6] Generating APK integrity hashes..." -ForegroundColor Yellow
$SoPath = "app/build/intermediates/merged_native_libs/release/out/lib/arm64-v8a/liblianyu_security.so"
if (Test-Path $SoPath) {
    python tools/generate_apk_hashes.py "$ApkPath" "$SoPath"
} else {
    Write-Host "[!] SO not found at $SoPath, generating DEX-only hashes..." -ForegroundColor Yellow
    python tools/generate_apk_hashes.py "$ApkPath"
}

# Step 5: Rebuild with updated hashes
Write-Host ""
Write-Host "[Step 5/6] Rebuilding with integrity hashes..." -ForegroundColor Yellow
.\gradlew assembleRelease

# Step 6: Verify final APK
Write-Host ""
Write-Host "[Step 6/6] Verifying final APK..." -ForegroundColor Yellow
$FinalApk = "app/build/outputs/apk/release/app-release.apk"
if (Test-Path $FinalApk) {
    Write-Host "[+] Final APK: $FinalApk" -ForegroundColor Green
    Get-Item $FinalApk | Select-Object Length, LastWriteTime | Format-Table -AutoSize
    
    # Verify signing
    $Apksigner = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($Apksigner) {
        apksigner verify "$FinalApk"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[+] Signature verified" -ForegroundColor Green
        } else {
            Write-Host "[!] Signature verification failed" -ForegroundColor Red
        }
    }
    
    # Show APK contents summary
    Write-Host ""
    Write-Host "[*] APK contents:" -ForegroundColor Cyan
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $FinalApk).Path)
    $Zip.Entries | Where-Object { $_.Name -match "\.(so|dex|arsc)$" } | 
        Select-Object FullName, @{N="Size";E={$_.Length}} | Format-Table -AutoSize
    $Zip.Dispose()
} else {
    Write-Host "ERROR: Final APK not found" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Build Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "Output: $FinalApk"
Write-Host ""
Write-Host "IMPORTANT POST-BUILD STEPS:" -ForegroundColor Yellow
Write-Host "  1. Run generate_apk_hashes.py on the FINAL APK"
Write-Host "  2. Update g_apk_digests_obs in native-bridge.cpp"
Write-Host "  3. Rebuild ONE MORE TIME if hashes changed"
Write-Host "  4. Keep .lianyu_wb_key SECRET — never commit it"
Write-Host "========================================" -ForegroundColor Green
