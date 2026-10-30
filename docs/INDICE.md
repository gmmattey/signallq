---
title: "Índice — documentação SignallQ"
description: "Mapa de todos os documentos existentes em docs, gerado a partir do disco"
type: "índice"
status: "ativo"
owner: "Squad"
last_updated: "2026-10-04"
---

# Índice da documentação

**111 documentos.** Escopo: app consumer Android e backend Cloudflare, mais a foundation do produto
Perímetro e o que saiu em
2026-08-06 e 2026-08-15 estão em [`README.md`](README.md).

> ✅ **Canônicos regenerados do código em 2026-08-06 (PR 2).** `03-especificacao-tecnica.md` e
> `02-arquitetura.md` carregam um bloco de inventário **gerado** por
> `scripts/gerar-inventario-docs.sh` — versões, módulos, workers, tabelas D1 e contagem de
> endpoints saem direto do código, e o CI reprova se divergirem. Não edite esse bloco à mão.

---

## Começar por aqui

1. [`../AGENTS.md`](../AGENTS.md) — o que é o SignallQ, stack, agentes
2. [`POSICIONAMENTO_PRODUTO.md`](POSICIONAMENTO_PRODUTO.md) — posicionamento obrigatório para Android e Web/PWA
3. [`design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md`](design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md) — direção futura de identidade e experiência
4. [`functional/JORNADA_ANDROID_GUIADA_2_SPEC.md`](functional/JORNADA_ANDROID_GUIADA_2_SPEC.md) — arquitetura futura da jornada Android
5. [`prototypes/signallq-android-2-0/`](prototypes/signallq-android-2-0/) — protótipo navegável da Jornada 2.0 (abrir `index.html`)
6. [`README.md`](README.md) — perímetro e mapa das pastas
7. [`01-funcional.md`](01-funcional.md) — o que o app faz
8. [`03-especificacao-tecnica.md`](03-especificacao-tecnica.md) — como é construído
9. [`02-arquitetura.md`](02-arquitetura.md) — módulos e dependências
10. [`05-governanca-e-fluxo-de-trabalho.md`](05-governanca-e-fluxo-de-trabalho.md) · [`06-fluxo-de-release.md`](06-fluxo-de-release.md) · [`07-voz-do-produto.md`](07-voz-do-produto.md)
11. [`DECISOES.md`](DECISOES.md) — documento único de decisões

---

## Canônicos

| Documento | Estado |
|---|---|
| [POSICIONAMENTO_PRODUTO.md](POSICIONAMENTO_PRODUTO.md) | ✅ diretriz de produto aprovada para Android e Web/PWA |
| [03-especificacao-tecnica.md](03-especificacao-tecnica.md) | ✅ reescrito do código · inventário gerado |
| [02-arquitetura.md](02-arquitetura.md) | ✅ reescrito do código · inventário gerado |
| [01-funcional.md](01-funcional.md) | ✅ reescrito do código · 4 raízes, 16 overlays, 77 citações de código |
| [04-design-system.md](04-design-system.md) | ✅ tokens conferidos 1 a 1 em `SignallQTheme.kt` |
| [05-governanca-e-fluxo-de-trabalho.md](05-governanca-e-fluxo-de-trabalho.md) | ✅ papéis, ciclo de 3 passos, gate e critérios de conclusão |
| [06-fluxo-de-release.md](06-fluxo-de-release.md) | ✅ canais, versões e autorização de publicação |
| [07-voz-do-produto.md](07-voz-do-produto.md) | ✅ tom e regras de copy |
| [DECISOES.md](DECISOES.md) | ✅ documento único vivo de decisões |
| [RELEASES.md](RELEASES.md) | não regenerado — histórico de releases, sai do git |

## Arquitetura por módulo — `modulos/` (22)

**22 documentos — um por módulo Gradle.** Mesmo template: responsabilidade, dependências,
consumidores, componentes principais, riscos e dívidas.

`app` · `core-database` · `core-datastore` · `core-diagnostico` · `core-featureflags` ·
`core-nds` · `core-network` · `core-permissions` · `core-probejogo` · `core-recommendation` · `core-relatorio` ·
`core-telephony` · `feature-devices` · `feature-diagnostico` · `feature-dns` · `feature-fibra` ·
`feature-history` · `feature-home` · `feature-router` · `feature-settings` · `feature-speedtest` · `feature-wifi`.

`core-nds` (fatia NDS-01, issue #1744, ADR-017) é o mais novo — cliente HTTP e contrato tipado do
Network Diagnostics Service, isolado, sem consumidor real ainda.


## Contratos — `CONTRATOS/`

5 contratos OpenAPI 3.0.3, **108 endpoints** (`CONTRATOS/openapi/`):

| Contrato | Versão | Paths |
|---|---|---:|
| `signallq-admin-api.yaml` | 2.2.0 | 59 |
| `signallq-diagnostic-worker.yaml` | 1 | 43 |
| `ai-diagnosis-worker.yaml` | 2 | 2 |
| `game-latency-probe-worker.yaml` | 1 | 2 |
| `signallq-privacy-worker.yaml` | 1 | 2 |

Mais `CONTRATOS/schemas/README.md`. A reconciliação dos dois últimos contratos transversais com o
código real é a issue **#1588**.

## Decisões — `DECISOES.md` · **documento único e vivo**

[`DECISOES.md`](DECISOES.md) reúne as decisões (ADR-001 a ADR-019) com índice no topo. Decisão nova entra ao final, numerada em sequência; **não se cria arquivo por decisão**. Os arquivos originais ficam em `archive/decisoes/` só para auditoria. Registro de ADRs removidos em 2026-08-15 (consolidados no ADR-016 ou substituídos): `ADR-006`, `ADR-007`, `ADR-010`, `ADR-014`, `ADR-015` e nove decisões de negócio da Fase 4d do épico [#1623](https://github.com/buildea-labs/signallq/issues/1623); o git preserva por SHA.

> Próximo ID livre: **ADR-020**.

## Operações — `operations/` (18)

Release e build: `RELEASE.md`, `APK_OUTPUT_POLICY.md`, `VERSIONING.md`, `SIGNING.md`, `ci-cd.md`,
`SCRIPTS.md`. Incidente e continuidade: `HOTFIX_PROCEDURE.md`, `ROLLBACK_PLAN.md`,
`INCIDENTE_BYPASS_BLOQUEIO_SEGURANCA_2026-07-20.md`, `MAINTENANCE_PLAN.md`. Qualidade e loja:
`DEVICE_TEST_MATRIX.md`, `MANIFEST_AUDIT.md`, `PLAY_STORE_LISTING.md`, `ENVIRONMENTS.md`,
`INFRASTRUCTURE_COSTS.md`. Processo: `PROCESSO_PR_E_AGENTES_2026-07-16.md`, `FAQ_USERS.md`,
`THIRD_PARTY_NOTICES.md`.

## Páginas de feature — `features/` (14)

Uma página por feature de produto, com negócio e técnico no mesmo arquivo e frontmatter legível por máquina (módulos, arquivos, eventos, flags, testes). Comece por [`features/INDEX.md`](features/INDEX.md) (tabela gerada por `scripts/gerar-features-index.sh`; `features/features.json` tem o mesmo conteúdo para máquina). Formato e regras: [`features/README.md`](features/README.md). O CI valida que todo caminho, evento e flag do frontmatter existe.

## Referências técnicas — `technical/` (15)

`admin-api-schema.md` · `analytics-events.md` · `analytics-events-schema.md` · `AI_FLOW.md` ·
`PING_EXECUTOR_ARCHITECTURE.md` · `MONITORAMENTO_PASSIVO.md` · `feature-flags-remote-config.md` ·
`SCREEN_MAP.md` · `PARIDADE_REC_WORKER_2026-07-26.md` · `P2_AMBIENTE_D1_ADMIN_SEPARACAO.md` ·
`INTELBRAS_RX1500_FIELD_MAP.md` · `NOKIA_GPON_FIELD_MAP.md` · `TPLINK_ARCHER_ROUTER_FIELD_MAP.md` ·
`MATRIZ_DIAGNOSTICO_2026-07-03.xlsx` · `appshell-overlay-registry.md` ·
`appshell-root-content-registry.md`.

## Funcional pontual — `functional/` (2)

`FEATURE_FLAGS.md` · `JORNADA_ANDROID_GUIADA_2_SPEC.md` (princípios da jornada, entregue na v1.0.9). O comportamento por feature vive em `features/`.

## Design — `design-system/` (5)

`SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md` (direção futura, draft) · `SPEC_BRAND_ENDORSEMENT_BY_7A_2026-07-26.md` ·
`DECISAO_ALINHAMENTO_TOBE_2026-07-13.md` · `DECISAO_CONTAINER_LOGO_MARCA_2026-07-17.md` ·
`DECISAO_SEPARACAO_DS_PROTOTIPOS_2026-07-18.md`. O Android implementado vive em `04-design-system.md`.

## Protótipos — `prototypes/` (2 pacotes)

`signallq-android-2-0/` — protótipo navegável da Jornada Android 2.0 (`README.md` · `COVERAGE.md` ·
`brand-spec.md` + `index.html`), referência visual do épico #1647.
`signallq-design-system-2-board/README.md` — prancha visual de foundations e componentes do DS 2.0.

## Legal — `legal/` (2) · **não editar sem revisão**

`PRIVACY_POLICY.md` · `TERMS_OF_USE.md`.

## Testes — `testing/`

`firebase-test-cases.yaml` — casos de teste do Firebase Test Lab. Único artefato da pasta e não é
Markdown, por isso não aparece nas contagens de documento.

## Templates — `templates/` (6)

`README.md` · `TEMPLATE_TECNICO.md` · `TEMPLATE_FUNCIONAL.md` · `TEMPLATE_ADR.md` ·
`TEMPLATE_RUNBOOK.md` · `feature-page.md`.

## Histórico — `archive/`

Decisões originais (`archive/decisoes/`), governança de squads anteriores (`archive/ai-governance/`) e instruções para recuperar qualquer documento removido em [`archive/README.md`](archive/README.md).

## Windows — `windows/`

Documentos do SignallQ Windows desktop (arquitetura, build e release, limitações, QA). Decisão de origem: ADR-019 em [`DECISOES.md`](DECISOES.md).

---

## Dívidas conhecidas

| # | Dívida | Onde |
|---|---|---|
| **#1585** | `/ingest/provider-detection` e `/ingest/diagnostic-divergence` aceitam POST anônimo — o padrão `INGEST_KEY` já existe no admin-worker e não foi aplicado | `signallq-diagnostic-worker/src/index.ts:1141,1145` |
| **#1586** | `MetricClassifier` não usado em `SinalScreen.kt`; limiares duplicados em três lugares | Android + worker |
| **#1587** | `auth.ts` duplicado byte-a-byte entre admin-worker e diagnostic-worker | `integrations/cloudflare/*/src/auth.ts` |
| **#1588** | OpenAPI transversais a reconciliar com o código | `CONTRATOS/openapi/` |
| — | ~~Caminho físico legado `io/veloo` em 525 arquivos `.kt`~~ — RESOLVIDO em 2026-08-15 (#1645) | `.claude/rules/higiene…§4.1` |

## Manutenção

Política em [`.claude/rules/politica-documentacao-viva.md`](../.claude/rules/politica-documentacao-viva.md).
Documento substituído é **removido**, não arquivado. Próxima auditoria: **2026-11-06**.
