#Requires -Version 5.0
<#
.SYNOPSIS
    数据库 Schema 锁定检测脚本。

.DESCRIPTION
    固定数据库环境（v22）的 CI 守卫。检测以下违规行为：
    1. AppDatabase.kt 中的 version 被修改（必须保持 22）
    2. schemas 目录出现 23.json 或更高版本的 schema 文件
    3. 22.json 的内容与版本控制基线不一致（Entity 定义被修改）

    任一违规将导致脚本以非零退出码结束，CI 流水线失败。

.EXAMPLE
    .\scripts\check-database-schema.ps1
#>

[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)

$ErrorActionPreference = "Stop"
$exitCode = 0

$AppDatabasePath = Join-Path $ProjectRoot "core\database\src\main\java\com\lianyu\ai\database\AppDatabase.kt"
$SchemaDir = Join-Path $ProjectRoot "core\database\schemas\com.lianyu.ai.database.AppDatabase"
$LockedVersion = 22

Write-Host "=== Database Schema Lock Check ===" -ForegroundColor Cyan
Write-Host "Locked version: v$LockedVersion"
Write-Host ""

# --- 检查 1: AppDatabase.kt 中的 version 是否仍为 $LockedVersion ---
Write-Host "[1/3] Checking AppDatabase.kt version..." -ForegroundColor Yellow
if (-not (Test-Path $AppDatabasePath)) {
    Write-Host "  FAIL: AppDatabase.kt not found at $AppDatabasePath" -ForegroundColor Red
    exit 1
}

$appDbContent = Get-Content $AppDatabasePath -Raw
if ($appDbContent -match "version\s*=\s*(\d+)") {
    $currentVersion = [int]$Matches[1]
    if ($currentVersion -ne $LockedVersion) {
        Write-Host "  FAIL: Database version is $currentVersion, expected $LockedVersion" -ForegroundColor Red
        Write-Host "  The database version is LOCKED at $LockedVersion." -ForegroundColor Red
        Write-Host "  New features MUST use ext_json column, NOT ALTER TABLE." -ForegroundColor Red
        Write-Host "  See: docs/DATABASE_STABLE_ENVIRONMENT.md" -ForegroundColor Red
        $exitCode = 1
    } else {
        Write-Host "  OK: version = $LockedVersion" -ForegroundColor Green
    }
} else {
    Write-Host "  FAIL: Could not parse version from AppDatabase.kt" -ForegroundColor Red
    $exitCode = 1
}

# --- 检查 2: schemas 目录中是否有超过 $LockedVersion 的 schema 文件 ---
Write-Host "[2/3] Checking for unauthorized schema files..." -ForegroundColor Yellow
if (Test-Path $SchemaDir) {
    $schemaFiles = Get-ChildItem -Path $SchemaDir -Filter "*.json" -ErrorAction SilentlyContinue
    $unauthorized = @()
    foreach ($file in $schemaFiles) {
        if ($file.BaseName -match "^(\d+)$") {
            $fileVersion = [int]$Matches[1]
            if ($fileVersion -gt $LockedVersion) {
                $unauthorized += $file.Name
            }
        }
    }
    if ($unauthorized.Count -gt 0) {
        Write-Host "  FAIL: Found schema files for versions above ${LockedVersion}:" -ForegroundColor Red
        $unauthorized | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
        Write-Host "  The database version is LOCKED at $LockedVersion." -ForegroundColor Red
        Write-Host "  Remove the new version schema and use ext_json for feature extensions." -ForegroundColor Red
        $exitCode = 1
    } else {
        Write-Host "  OK: No unauthorized schema files" -ForegroundColor Green
    }
} else {
    Write-Host "  WARN: Schema directory not found" -ForegroundColor Yellow
}

# --- 检查 3: 22.json 是否被修改（与 git 基线对比） ---
Write-Host "[3/3] Checking locked schema (v$LockedVersion.json) integrity..." -ForegroundColor Yellow
$lockedSchemaPath = Join-Path $SchemaDir "$LockedVersion.json"
if (Test-Path $lockedSchemaPath) {
    Push-Location $ProjectRoot
    try {
        $gitDiff = git diff --name-only -- "core/database/schemas/com.lianyu.ai.database.AppDatabase/$LockedVersion.json" 2>$null
        $gitStaged = git diff --cached --name-only -- "core/database/schemas/com.lianyu.ai.database.AppDatabase/$LockedVersion.json" 2>$null
        if ($gitDiff -or $gitStaged) {
            Write-Host "  FAIL: Locked schema v$LockedVersion.json has uncommitted changes" -ForegroundColor Red
            Write-Host "  Entity definitions must NOT be modified (no new columns, no type changes)." -ForegroundColor Red
            Write-Host "  Use ext_json column for feature extensions instead." -ForegroundColor Red
            Write-Host "  See: docs/DATABASE_STABLE_ENVIRONMENT.md" -ForegroundColor Red
            $exitCode = 1
        } else {
            Write-Host "  OK: Locked schema unchanged" -ForegroundColor Green
        }
    } catch {
        Write-Host "  WARN: Could not check git status (not a git repo?)" -ForegroundColor Yellow
    } finally {
        Pop-Location
    }
} else {
    Write-Host "  FAIL: Locked schema v$LockedVersion.json not found" -ForegroundColor Red
    $exitCode = 1
}

Write-Host ""
if ($exitCode -eq 0) {
    Write-Host "=== ALL CHECKS PASSED ===" -ForegroundColor Green
} else {
    Write-Host "=== CHECKS FAILED ===" -ForegroundColor Red
    Write-Host ""
    Write-Host "Database version is LOCKED at v$LockedVersion." -ForegroundColor Cyan
    Write-Host "To add new entity fields, use the ext_json column:" -ForegroundColor Cyan
    Write-Host "  val tag: String? = entity.extJson.readExt(""customTag"")" -ForegroundColor White
    Write-Host "  entity = entity.copy(extJson = entity.extJson.writeExt(""customTag"", ""value""))" -ForegroundColor White
    Write-Host ""
    Write-Host "See docs/DATABASE_STABLE_ENVIRONMENT.md for details." -ForegroundColor Cyan
}

exit $exitCode
