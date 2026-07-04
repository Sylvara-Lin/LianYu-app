# PowerShell implementation of pack_so.py
# Encrypts .text section of SO files in-place and patches decrypt stub globals

param(
    [string]$InputSo,
    [string]$OutputSo,
    [string]$KeyHex  # optional, if not provided a random key is generated
)

# Error handling
$ErrorActionPreference = "Stop"

if (-not $InputSo -or -not (Test-Path $InputSo)) {
    Write-Error "Input SO not found: $InputSo"
    exit 1
}
if (-not $OutputSo) {
    $OutputSo = $InputSo  # in-place
}

# Read the SO file as bytes
$bytes = [System.IO.File]::ReadAllBytes($InputSo)
Write-Host "  Read $($bytes.Length) bytes from $InputSo"

# ── ELF parsing ──
# We need to find:
# 1. .text section (offset, size, vaddr)
# 2. lianyu_xor_key symbol (file offset, 16 bytes)
# 3. lianyu_text_start symbol (file offset, 8 bytes)
# 4. lianyu_text_size symbol (file offset, 8 bytes)

# Use llvm-readelf to get section and symbol info
$readelf = "C:\Users\27194\AppData\Local\Android\Sdk\ndk\30.0.14904198\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"

# Get section headers
$sectionOutput = & $readelf -S $InputSo 2>&1 | Out-String

# Parse .text section
$textLine = $sectionOutput -split "`n" | Where-Object { $_ -match '\.text\s+PROGBITS' } | Select-Object -First 1
if (-not $textLine) {
    Write-Error "  .text section not found"
    exit 1
}
# Parse: [14] .text PROGBITS 000000000086e500 86e500 088ddc 00 AX 0 0 16
$textParts = $textLine -split '\s+' | Where-Object { $_ -ne '' }
$textVaddr = [Convert]::ToInt64($textParts[3], 16)
$textOffset = [Convert]::ToInt64($textParts[4], 16)
$textSize = [Convert]::ToInt64($textParts[5], 16)
Write-Host "  .text: vaddr=0x$($textVaddr.ToString('X')) offset=0x$($textOffset.ToString('X')) size=0x$($textSize.ToString('X'))"

# Get symbol table
$symOutput = & $readelf -s $InputSo 2>&1 | Out-String

# Helper: find symbol vaddr by name
function Find-SymbolVaddr($symOutput, $name) {
    $line = $symOutput -split "`n" | Where-Object { $_ -match "\s$name\s" } | Select-Object -First 1
    if (-not $line) { return $null }
    $parts = $line -split '\s+' | Where-Object { $_ -ne '' }
    # Format: N: 00000000008fd1c8 16 OBJECT GLOBAL DEFAULT 25 lianyu_xor_key
    return [Convert]::ToInt64($parts[1], 16)
}

$keyVaddr = Find-SymbolVaddr $symOutput "lianyu_xor_key"
$startVaddr = Find-SymbolVaddr $symOutput "lianyu_text_start"
$sizeVaddr = Find-SymbolVaddr $symOutput "lianyu_text_size"

if (-not $keyVaddr -or -not $startVaddr -or -not $sizeVaddr) {
    Write-Error "  Decrypt symbols not found"
    exit 1
}

Write-Host "  lianyu_xor_key: vaddr=0x$($keyVaddr.ToString('X'))"
Write-Host "  lianyu_text_start: vaddr=0x$($startVaddr.ToString('X'))"
Write-Host "  lianyu_text_size: vaddr=0x$($sizeVaddr.ToString('X'))"

# Convert vaddr to file offset using program headers
# For segment 01 (LOAD R E): p_offset = p_vaddr (typically 0)
# For segment 03 (LOAD RW .data): p_offset = p_vaddr - 0x4000 (alignment)
# We need to figure out the mapping

$segOutput = & $readelf -l $InputSo 2>&1 | Out-String

# Parse LOAD segments
$loadLines = $segOutput -split "`n" | Where-Object { $_ -match '^\s*LOAD\s' }
$segments = @()
foreach ($line in $loadLines) {
    $parts = $line -split '\s+' | Where-Object { $_ -ne '' }
    # Format: LOAD 0x000000 0x0000000000000000 0x0000000000000000 0x8f7b60 0x8f7b60 R E 0x4000
    $segOffset = [Convert]::ToInt64($parts[1], 16)
    $segVaddr = [Convert]::ToInt64($parts[2], 16)
    $segFileSz = [Convert]::ToInt64($parts[4], 16)
    $segMemSz = [Convert]::ToInt64($parts[5], 16)
    $segments += @{
        Offset = $segOffset
        Vaddr = $segVaddr
        FileSz = $segFileSz
        MemSz = $segMemSz
    }
}

function Vaddr-To-FileOffset($vaddr, $segments) {
    foreach ($seg in $segments) {
        $segEnd = $seg.Vaddr + $seg.MemSz
        if ($seg.Vaddr -le $vaddr -and $vaddr -lt $segEnd) {
            return $seg.Offset + ($vaddr - $seg.Vaddr)
        }
    }
    return -1
}

$keyFileOff = Vaddr-To-FileOffset $keyVaddr $segments
$startFileOff = Vaddr-To-FileOffset $startVaddr $segments
$sizeFileOff = Vaddr-To-FileOffset $sizeVaddr $segments

Write-Host "  lianyu_xor_key: file_offset=0x$($keyFileOff.ToString('X'))"
Write-Host "  lianyu_text_start: file_offset=0x$($startFileOff.ToString('X'))"
Write-Host "  lianyu_text_size: file_offset=0x$($sizeFileOff.ToString('X'))"

# ── Generate XOR key ──
if ($KeyHex) {
    $key = [byte[]]::new(16)
    $hexBytes = $KeyHex -replace 'hex:',''
    for ($i = 0; $i -lt 16; $i++) {
        $key[$i] = [Convert]::ToByte($hexBytes.Substring($i * 2, 2), 16)
    }
} else {
    $key = [byte[]]::new(16)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($key)
}
$keyStr = ($key | ForEach-Object { $_.ToString('x2') }) -join ''
Write-Host "  XOR key: $keyStr"

# ── Encrypt .text in-place ──
Write-Host "  Encrypting .text at offset 0x$($textOffset.ToString('X')), size 0x$($textSize.ToString('X'))"
for ($i = 0; $i -lt $textSize; $i++) {
    $bytes[$textOffset + $i] = $bytes[$textOffset + $i] -bxor $key[$i % 16]
}

# ── Patch lianyu_xor_key (16 bytes) ──
[Array]::Copy($key, 0, $bytes, $keyFileOff, 16)

# ── Patch lianyu_text_start (8 bytes, signed offset from key to .text) ──
$offsetFromKey = $textVaddr - $keyVaddr  # signed, may be negative
Write-Host "  text_vaddr=0x$($textVaddr.ToString('X')) key_vaddr=0x$($keyVaddr.ToString('X')) offset=$offsetFromKey"
$offsetBytes = [BitConverter]::GetBytes([long]$offsetFromKey)
[Array]::Copy($offsetBytes, 0, $bytes, $startFileOff, 8)

# ── Patch lianyu_text_size (8 bytes) ──
$sizeBytes = [BitConverter]::GetBytes([long]$textSize)
[Array]::Copy($sizeBytes, 0, $bytes, $sizeFileOff, 8)

# ── Write output ──
[System.IO.File]::WriteAllBytes($OutputSo, $bytes)
Write-Host "  OK Packed SO written to: $OutputSo"
Write-Host "  File size: $($bytes.Length) bytes (unchanged)"
