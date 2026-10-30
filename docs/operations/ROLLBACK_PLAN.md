---
title: "Plano de rollback"
description: "Quando e como reverter Android (Play Store/Firebase), Workers Cloudflare e D1."
type: "runbook"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Plano de Rollback — SignallQ

- **Fonte de verdade:** `.github/workflows/release.yml`, `integrations/cloudflare/*/wrangler.toml`
- **Escopo:** rollback de Android (Play Store/Firebase), workers Cloudflare e D1

## Quando fazer rollback

| Indicador | Threshold | Ação |
|---|---|---|
| Crash rate | > 2% em 1h | Rollback imediato |
| ANR rate | > 1% em 1h | Rollback imediato |
| Feature core quebrada | Speedtest/diagnóstico inacessível | Rollback imediato |
| Worker down | Error rate > 10% por 15min | Rollback do Worker |
| Regressão severa | Perda de dados, UI inutilizável | Rollback imediato |

## Procedimentos por componente

### Android — Play Store

**Pausar rollout staged:**
1. Play Console > Release > Production
2. Clicar "Halt rollout"
3. O app continua disponível na versão anterior para quem não atualizou

**Rollback para versão anterior:**
1. Play Console > Release > Production > Create new release
2. Selecionar AAB da versão anterior (já uploaded)
3. Staged rollout 100% (rollback é urgente)

**Limitação:** quem já atualizou não recebe downgrade automático. A nova versão (fix ou rollback) precisa ter versionCode maior.

### Android — Firebase App Distribution

Faça checkout da tag anterior (`git checkout vX.Y.Z`), incremente `versionCode` (não pode regredir) e dispare `firebase-distribution.yml` (ver `RELEASE.md`).

### Workers Cloudflare

**Rollback automático (última versão):**
```bash
npx wrangler rollback <worker-name>
```

**Rollback manual (versão específica):**
```bash
# Checkout do código anterior
git checkout <commit-hash> -- integrations/cloudflare/<worker>/
cd integrations/cloudflare/<worker>
npx wrangler deploy
```

Lista dos 5 Workers e nomes reais: `ENVIRONMENTS.md`.

### D1 Database

**Não há rollback automático de schema.** Mitigações:
- Migrations são aditivas (ADD COLUMN) — não quebram versões anteriores
- Para rollback de dados: restaurar backup D1
- Backup manual: `npx wrangler d1 export signallq-admin-db --remote --output backup.sql`

## Comunicação durante rollback

1. **Imediato:** comentário na issue GitHub com status
2. **Em 30min:** atualização com causa raiz identificada
3. **Resolução:** post-mortem breve na issue

## Prevenção

- Staged rollout sempre (10% → 25% → 50% → 100%)
- Monitorar Crashlytics nas primeiras 2h após cada stage
- Nunca fazer rollout 100% no mesmo dia do upload
- Feature flags para funcionalidades novas de risco
