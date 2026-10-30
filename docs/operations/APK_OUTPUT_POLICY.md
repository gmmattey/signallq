---
title: "Política de saída de APK"
description: "Local e nome obrigatórios dos APKs gerados localmente."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Política de saída de APK

- **Fonte de verdade:** este documento (regra de nome/local de artefato); versão real em `android/gradle/libs.versions.toml`
- **Escopo:** build local e scripts de empacotamento do app Android

Todo APK gerado pelo projeto deve ser arquivado na pasta oficial:

```text
android/builds/apk/<buildType>/<versionName>/
```

## Nome obrigatorio

```text
signallq-android-v<versionName>+<versionCode>-<buildType>-<yyyyMMdd-HHmmss>.apk
```

Exemplo:

```text
android/builds/apk/release/1.0.9/signallq-android-v1.0.9+89-release-20261004-120000.apk
```

## Comandos oficiais

Use estes comandos para gerar APKs arquivados:

```powershell
.\scripts\build-apk-debug.ps1
.\scripts\build-apk-release.ps1
```

Ou diretamente via Gradle:

```powershell
cd android; .\gradlew.bat archiveDebugApk
cd android; .\gradlew.bat archiveReleaseApk
```

## Regras

- Nao distribuir `app-debug.apk` ou `app-release.apk` diretamente.
- Nao salvar APK em `Downloads`, desktop, raiz do projeto ou pastas antigas.
- Nao criar pasta `apk/` paralela.
- `versionName` e `versionCode` vem de `android/gradle/libs.versions.toml`.
- Para release publico, incrementar `versionCode` antes do build.
- APKs gerados continuam fora do Git.
