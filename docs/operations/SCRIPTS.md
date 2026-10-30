---
title: "Scripts Oficiais"
description: "Scripts PowerShell/shell oficiais em scripts/ para build, versionamento e ambiente do SignallQ Android"
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Scripts Oficiais

- **Status:** ativo
- **Fonte de verdade:** `scripts/` (índice resumido em `scripts/README.md`)
- **Escopo:** scripts PowerShell/shell em `scripts/`

Scripts ficam em `scripts/`, na raiz do repositório.

## Build APK

### Debug

```powershell
.\scripts\build-apk-debug.ps1
```

Saida:

```text
android/builds/apk/debug/<versionName>/signallq-android-v<versionName>+<versionCode>-debug-<yyyyMMdd-HHmmss>.apk
```

### Release

```powershell
.\scripts\build-apk-release.ps1
```

Saida:

```text
android/builds/apk/release/<versionName>/signallq-android-v<versionName>+<versionCode>-release-<yyyyMMdd-HHmmss>.apk
```

Tambem existem tarefas Gradle equivalentes:

```powershell
cd android; .\gradlew.bat archiveDebugApk
cd android; .\gradlew.bat archiveReleaseApk
```

Nunca entregue `app-debug.apk` ou `app-release.apk` diretamente.

## Versionamento

```powershell
.\scripts\version.ps1 show
.\scripts\version.ps1 patch
.\scripts\version.ps1 minor
.\scripts\version.ps1 major
.\scripts\version.ps1 build
```

O script altera `android/gradle/libs.versions.toml`. Também aceita `set X.Y.Z+N`.

## Ambiente

```powershell
.\scripts\check-env.ps1
```

Valida Java, Android SDK, ADB e ferramentas auxiliares.

## Limpeza

```powershell
.\scripts\clean-build.ps1
```

Remove outputs de build locais, mas não deve apagar `android/builds/apk/`, onde ficam os APKs arquivados.

## Outros scripts

`pre-commit-android.sh`/`setup-hooks.*` (hook de pré-commit), `validar-docs.sh` e `gerar-inventario-docs.sh` (docs), `sync-skills-mirrors.sh` (espelhos de skills), `observe-and-act.sh` (hook do Claude Code) e `issue-move.sh`/`setup-github-labels.sh`/`migrate-issue-labels.sh` (GitHub). Detalhes em `scripts/README.md`. Scripts antigos de build e `scripts/legacy/` foram removidos; o git preserva o histórico.
