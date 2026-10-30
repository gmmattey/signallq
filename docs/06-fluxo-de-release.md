---
title: "Fluxo de release"
description: "Canais, versões e publicação do SignallQ Android e dos Workers: o que cada etapa significa e quem autoriza."
type: "runbook"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-30"
version: "1.0.0"
---

# Fluxo de release

- **Fonte de verdade:** `.github/workflows/release.yml`, `promote-release.yml`, `firebase-distribution.yml` e `android/gradle/libs.versions.toml`. O passo a passo detalhado está em [`operations/RELEASE.md`](operations/RELEASE.md); este documento define o vocabulário e as autorizações.
- **Escopo:** app Android (`io.signallq.app`) e deploy dos Workers Cloudflare. Windows tem fluxo próprio em [`windows/BUILD_AND_RELEASE.md`](windows/BUILD_AND_RELEASE.md); o site em `web/`.

## Estados que não se misturam

| Estado | Significa |
| --- | --- |
| Debug | Instalação local; não é beta. |
| Firebase App Distribution | Validação rápida de um build assinado, para testadores convidados. |
| Trilha `beta` da Play Console | Release candidate identificado por `versionName` e `versionCode`. Push de tag `v*` publica aqui, com anúncios desligados. |
| Produção | Disparo manual de `release.yml` com `playTrack=production`, só com autorização explícita do Luiz. |

Documentado, implementado, validado em emulador, validado em aparelho físico, enviado ao teste fechado e publicado são afirmações diferentes. Só afirme o que tem evidência do ambiente correto.

## Versões

`versionName` segue `MAJOR.MINOR.PATCH`. `versionCode` é inteiro crescente e nunca se repete; incremente antes de **qualquer** build publicado, em qualquer canal. Regras completas em [`operations/VERSIONING.md`](operations/VERSIONING.md). O histórico está em [`RELEASES.md`](RELEASES.md).

## Checklist mínimo antes de publicar

1. Renan aprovou com evidência; Marcelo deu o aceite final.
2. `./gradlew test`, `ktlintCheck detekt` e `assembleDebug` passaram (`android/`).
3. `versionCode` incrementado e notas públicas em `android/app/src/main/play/release-notes/pt-BR/default.txt`.
4. Declarações da loja (Data Safety, política de privacidade) conferem com o AAB real. Skills `checar-release` e `loja-e-aso`.
5. Anúncios reais exigem as chaves de Firebase Remote Config já criadas e decisão do Luiz; promover o mesmo binário de `beta` não liga anúncio.
6. Rollback e hotfix conhecidos: [`operations/ROLLBACK_PLAN.md`](operations/ROLLBACK_PLAN.md) e [`operations/HOTFIX_PROCEDURE.md`](operations/HOTFIX_PROCEDURE.md).

## Autorização

Release, promoção de trilha, deploy de Worker em produção, mudança de segredo e alteração no Play Console são do Luiz. Agentes preparam e conferem; não publicam. Não crie repositório, Actions, chave, certificado, upload ou submissão sem pedido explícito. Segredos nunca entram no repositório (`operations/SIGNING.md`).
