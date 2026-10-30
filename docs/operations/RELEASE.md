---
title: "Release e deploy Android"
description: "Processo de release do SignallQ Android: build local, tag, publicação na Play Console, Firebase App Distribution e deploy de Workers."
type: "runbook"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Release e deploy Android

- **Fonte de verdade:** `.github/workflows/release.yml`, `promote-release.yml`, `firebase-distribution.yml`; versão real em `android/gradle/libs.versions.toml`
- **Escopo:** release do app (`:app`, `io.signallq.app`) e deploy dos Workers Cloudflare
- **Documentos substituídos:** `DEPLOY.md` e `GuiaReleaseBuild.md` (consolidados aqui; recuperáveis via `git show f6f9d437:docs_ai/operations/<arquivo>`)

Regra única para qualquer canal: **incremente `versionCode` em `android/gradle/libs.versions.toml`
antes de subir um build** (commitado e pushado). O campo é global; não há contador por canal
(ver `VERSIONING.md`).

## Canais (ambos via GitHub Actions)

### 1. Play Console — release oficial (`release.yml`)

1. Bump de versão em `libs.versions.toml`, `CHANGELOG.md` e `docs/RELEASES.md`;
   notas públicas em `android/app/src/main/play/release-notes/pt-BR/default.txt` (lidas pelo workflow).
2. `git tag vX.Y.Z && git push origin vX.Y.Z` dispara o workflow: build assinado
   (`:app:assembleRelease`, `:app:bundleRelease`), upload do mapping ao Crashlytics, GitHub Release e
   `:app:publishReleaseBundle` (gradle-play-publisher, secret `PLAY_SERVICE_ACCOUNT_JSON`).
   Push de tag publica **sempre** na trilha `beta` com anúncios desligados
   (`-PplayTrack=beta -PadsEnabled=false`).
3. **Produção** é disparo manual do mesmo workflow (`workflow_dispatch`) com `playTrack=production`
   e, se for o caso, `adsEnabled=true`. Publica um AAB **novo**: `USE_TEST_ADS`/`ADS_ENABLED` são
   compilados no build (`app/build.gradle.kts`), então promover o binário de `beta` não liga anúncio.
   O workflow rejeita `adsEnabled=true` fora de `production`. Decisão de produção/ads é do Luiz.
4. Antes de publicar com ads reais, as chaves de Firebase Remote Config (`ads_native_enabled` + 5 por
   tela, ver `AdsRemoteConfigRepository.kt`) precisam existir no console; sem elas o app cai em
   `AdsFlags.DESLIGADO`.

Secrets usados: `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
`GOOGLE_SERVICES_JSON`, `PLAY_SERVICE_ACCOUNT_JSON`, `ADMIN_INGEST_KEY`, `NDS_API_TOKEN` (assinatura: `SIGNING.md`).

`promote-release.yml` (`workflow_dispatch`) só aceita origem `internal`/`alpha` e destino
`internal`/`alpha`; `beta`/`production` são bloqueados. Como nenhum fluxo atual publica em
`internal`/`alpha`, ele não é caminho ativo.

### 2. Firebase App Distribution — validação rápida (`firebase-distribution.yml`)

`workflow_dispatch` manual, input `buildType` (`release`/`debug`). Builda, assina e envia via
`appDistributionUpload*`. Exige o secret `FIREBASE_TOKEN` (gerado com `firebase login:ci`, que precisa
de TTY).

## Build local de APK assinado

Pré-requisitos: PowerShell 7+, JDK, Android SDK, `android/key.properties` e
`android/segredos/signallq.jks` (ver `SIGNING.md`).

```powershell
.\scripts\version.ps1 patch          # ou minor | major | build | set <versão>
.\scripts\build-apk-release.ps1      # alternativa: gradlew archiveReleaseApk
```

O APK sai em `android/builds/apk/release/<versionName>/` com o nome definido em
`APK_OUTPUT_POLICY.md`. Nunca distribua `app-release.apk` bruto. Validação pós-build:

```powershell
aapt dump badging <apk> | findstr version
jarsigner -verify <apk>
adb install -r <apk>
```

## Workers Cloudflare

Mudança em `integrations/cloudflare/<worker>/src/` é deployada à parte, em cada pasta:
`npx wrangler deploy` (workers listados em `ENVIRONMENTS.md`). Garanta compatibilidade entre a versão do
app e a do Worker antes de publicar; deploy de Worker em produção exige autorização do Luiz.
Rollback: `ROLLBACK_PLAN.md`.

## Ativação de feature flag no release

1. Mude o `buildConfigField` da flag no bloco `release` de `android/app/build.gradle.kts`.
2. Bump de versão e entrada no `CHANGELOG.md` descrevendo a feature para o usuário.
3. Atualize `FUNCIONAL.md`/`TECNICO.md` se o comportamento documentado mudar.
4. Publique pelo fluxo acima. Se a feature não estiver pronta, reverta a flag e faça hotfix
   (`HOTFIX_PROCEDURE.md`).

## Riscos conhecidos

- Rollout gradual (`userFraction`) não está configurado no workflow; ajuste de rollout é manual no Play Console.
- Aprovações e sign-offs de release dependem do Luiz; não há gate de GitHub Environment no disparo manual de produção (decisão de 2026-08-27, PR #1805).
