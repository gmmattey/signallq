---
title: "Política de versionamento"
description: "Regras de versionName/versionCode do app Android e relação com as trilhas da Play Console."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Política de versionamento

- **Fonte de verdade:** `android/gradle/libs.versions.toml` (`versionName`, `versionCode`), consumidos por `app/build.gradle.kts`
- **Escopo:** versionamento do app Android
- **Substitui:** versão 1.x, que reservava `1.0.0` ao primeiro publish em `production` e tratava as trilhas como `0.x.y`; a prática real mudou (ver abaixo)

## versionName (SemVer)

`MAJOR.MINOR.PATCH`: MAJOR = marco de produto ou mudança incompatível; MINOR = feature ou mudança funcional relevante; PATCH = correção ou ajuste menor. Muda só em releases significativos, não a cada iteração de debug.

Prática real: a linha `1.x` começou em 2026-08-23 (tag `v1.0.0`, publicada na trilha `beta`) e segue `1.0.x` (tag mais recente: `v1.0.9`). Não há sufixo de pré-release (`-beta.N`, `-rc.N`) nas tags.

## versionCode

Inteiro crescente, nunca reutilizado (requisito da Play Store). Incremente **sempre antes de qualquer build publicado**, em qualquer canal (Play Console ou Firebase App Distribution). O campo é global; gaps são normais, dois uploads com o mesmo número não.

## Trilhas

Push de tag `v*` publica em `beta`; `production` só por disparo manual de `release.yml` (ver `RELEASE.md`). A trilha não altera o formato do `versionName`.

## Comandos

```powershell
.\scripts\version.ps1 show
.\scripts\version.ps1 patch   # ou minor | major | build
.\scripts\version.ps1 set <versionName>+<versionCode>
```

APKs refletem a versão no nome (`APK_OUTPUT_POLICY.md`).
