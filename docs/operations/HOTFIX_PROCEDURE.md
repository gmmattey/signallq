---
title: "Procedimento de hotfix"
description: "Fluxo de correção crítica fora do ciclo regular de release."
type: "runbook"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Procedimento de Hotfix — SignallQ

- **Fonte de verdade:** `RELEASE.md` (canais) e `.github/workflows/release.yml`
- **Escopo:** correções críticas fora do ciclo regular de release

## Quando usar

Hotfix é para correções **críticas** que não podem esperar o próximo ciclo regular:
- Crash rate > 1% em release
- Funcionalidade core quebrada (speedtest, diagnóstico, IA)
- Vulnerabilidade de segurança explorada
- Perda de dados do usuário

## Fluxo

### 1. Identificar e classificar

| Severidade | Critério | SLA |
|---|---|---|
| P0 — Crítico | Crash em >5% dos usuários, dados corrompidos | 4h para fix, deploy imediato |
| P1 — Alto | Feature core quebrada, crash em 1-5% | 24h para fix |
| P2 — Médio | Bug visível mas com workaround | Próximo ciclo regular |

### 2. Branch e desenvolvimento

```
git checkout main
git checkout -b hotfix/N-descricao-curta
```

`N` é o número da issue GitHub.

- Escopo mínimo: apenas o fix, nada mais
- Sem refactor, sem cleanup, sem features
- Testes do fluxo afetado obrigatórios

### 3. Validação

- [ ] Build release compila sem erros
- [ ] Teste manual do fluxo afetado
- [ ] Crashlytics sem novos crashes no APK de teste
- [ ] Regressão básica: speedtest, diagnóstico, histórico

### 4. Deploy

**Android:** bump de `versionCode`/`versionName`, `CHANGELOG.md` e tag `vX.Y.Z`; `release.yml` publica na trilha `beta`. Produção só por disparo manual e com autorização do Luiz — fluxo completo em `RELEASE.md`. Para validação rápida antes, use `firebase-distribution.yml`. Rollout imediato só para P0.

**Workers Cloudflare:**
```bash
cd integrations/cloudflare/<worker-afetado>
npx wrangler deploy
```

### 5. Pós-deploy

- [ ] Monitorar Crashlytics por 2h
- [ ] Verificar Analytics events normais
- [ ] Comunicar na issue GitHub (comentário + fechar via PR "Closes #N")
- [ ] Merge hotfix branch em main
- [ ] Bump versionCode (não versionName para patch)

## Versionamento de Hotfix

- Patch version: ex. `1.0.9` → `1.0.10`
- versionCode: incrementar em 1
- CHANGELOG.md: adicionar entrada na seção da versão atual

## Rollback

Se o hotfix introduzir novo problema:
1. Play Store: pausar rollout imediatamente
2. Firebase: redistribuir o build anterior (ver `ROLLBACK_PLAN.md`)
3. Workers: `npx wrangler rollback` ou redeploy versão anterior
