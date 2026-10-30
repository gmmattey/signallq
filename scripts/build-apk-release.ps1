#Requires -Version 7
<#
.SYNOPSIS
    Gera APK release assinado do projeto SignallQ Android e salva em builds\apk\release\<versionName>\.

.DESCRIPTION
    Lê versionName e versionCode de gradle/libs.versions.toml, executa o assembleRelease
    via Gradle wrapper, copia o APK gerado para a pasta oficial com nome padronizado.

    Requer key.properties para assinatura. Se não existir, APK será gerado mas não assinado.

.EXAMPLE
    .\scripts\build-apk-release.ps1
    .\scripts\build-apk-release.ps1 -Verbose
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot   = Resolve-Path (Join-Path $PSScriptRoot '..')
$androidRoot = Join-Path $repoRoot 'android'
$tomlPath   = Join-Path $androidRoot 'gradle\libs.versions.toml'
$gradlew    = Join-Path $androidRoot 'gradlew.bat'

# ── 1. Lê versão do catálogo ──────────────────────────────────────────────────
if (-not (Test-Path $tomlPath)) {
    Write-Error "Catálogo não encontrado: $tomlPath"
}

$toml        = Get-Content $tomlPath -Raw
$versionName = [regex]::Match($toml, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$versionCode = [regex]::Match($toml, 'versionCode\s*=\s*"([^"]+)"').Groups[1].Value

if (-not $versionName -or -not $versionCode) {
    Write-Error "Não foi possível extrair versionName/versionCode de $tomlPath"
}

Write-Host "=== SignallQ Kotlin — Build Release ===" -ForegroundColor Cyan
Write-Host "  versionName : $versionName"
Write-Host "  versionCode : $versionCode"
Write-Host ""

# ── 2. Verifica key.properties ────────────────────────────────────────────────
$keyProps = Join-Path $androidRoot 'key.properties'
$hasSigning = Test-Path $keyProps

if ($hasSigning) {
    Write-Host "✓ key.properties encontrado — APK será assinado" -ForegroundColor Green
} else {
    Write-Host "⚠ key.properties não encontrado em $keyProps" -ForegroundColor Yellow
    Write-Host "  ⚠ O APK gerado NÃO estará assinado." -ForegroundColor Yellow
    Write-Host "  Para release com assinatura, crie key.properties conforme:" -ForegroundColor Yellow
    Write-Host "    - docs/operations/ENVIRONMENT.md" -ForegroundColor Yellow
    Write-Host ""
}

# ── 3. Executa o build ────────────────────────────────────────────────────────
Write-Host "▶ Executando: gradlew :app:assembleRelease" -ForegroundColor Yellow
Push-Location $androidRoot
try {
    & $gradlew :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { Write-Error "Gradle falhou com código $LASTEXITCODE" }
} finally {
    Pop-Location
}

# ── 4. Localiza o APK gerado ──────────────────────────────────────────────────
$apkSrc = Join-Path $androidRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apkSrc)) {
    Write-Error "APK não encontrado em $apkSrc — verifique a saída do Gradle."
}

# ── 5. Copia para pasta oficial com nome padronizado ──────────────────────────
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$destDir  = Join-Path $androidRoot "builds\apk\release\$versionName"
$destFile = Join-Path $destDir "signallq-android-v$versionName+$versionCode-release-$timestamp.apk"

New-Item -ItemType Directory -Path $destDir -Force | Out-Null
Copy-Item -Path $apkSrc -Destination $destFile -Force

$sizeMb = [math]::Round((Get-Item $destFile).Length / 1MB, 1)

Write-Host ""
Write-Host "✔ APK gerado com sucesso!" -ForegroundColor Green
Write-Host "  Arquivo  : $destFile"
Write-Host "  Tamanho  : ${sizeMb} MB"
Write-Host "  Assinado : $(if ($hasSigning) { 'Sim' } else { 'Não' })"
Write-Host ""
Write-Host "Próximos passos:" -ForegroundColor Cyan
Write-Host "  1. Validar: aapt dump badging `"$destFile`" | findstr version"
Write-Host "  2. Testar: adb install -r `"$destFile`""
if (-not $hasSigning) {
    Write-Host "  3. Para release com assinatura, crie key.properties e re-build" -ForegroundColor Yellow
}
