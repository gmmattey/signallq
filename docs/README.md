---
title: "Documentação — SignallQ"
description: "Ponto de entrada da documentação do app Android, do Windows desktop e do backend Cloudflare, com as fontes de verdade por assunto."
type: "índice"
status: "ativo"
owner: "Marcelo (processo) · Camillo (técnico)"
last_updated: "2026-10-30"
---

# SignallQ — documentação do projeto

Atualizado em 2026-10-30. Marca oficial: **SignallQ**.

O SignallQ é um produto de **diagnóstico de conectividade**: ajuda a pessoa a entender o que acontece com a internet, o que fazer e se resolveu (entender, diagnosticar, resolver, confirmar). Speed test, Wi-Fi, DNS, equipamentos e IA são fontes de evidência, nunca o protagonista. Este arquivo é só um índice e não repete o conteúdo dos documentos que lista; o mapa completo, com status e responsável, está em [`INDICE.md`](INDICE.md).

## Fontes de verdade

| Assunto | Documento |
| --- | --- |
| Escopo, jornada e regras de produto | [Funcional](01-funcional.md) e [páginas de feature](features/INDEX.md) |
| Módulos e responsabilidades | [Arquitetura](02-arquitetura.md) e [um documento por módulo](modulos/) |
| Contratos, stack, algoritmos e validação técnica | [Especificação técnica](03-especificacao-tecnica.md) e [contratos](CONTRATOS/openapi/) |
| Tokens, marca e acessibilidade | [Design system](04-design-system.md) |
| Papéis, ciclo de trabalho e gates | [Governança](05-governanca-e-fluxo-de-trabalho.md) e [workflow](../.agents/WORKFLOW.md) |
| Canais, versões e publicação | [Fluxo de release](06-fluxo-de-release.md) e [histórico de releases](RELEASES.md) |
| Tom, copy e localização | [Voz do produto](07-voz-do-produto.md) |
| Posicionamento e origem | [Posicionamento](POSICIONAMENTO_PRODUTO.md) e [História](HISTORIA.md) |
| Windows desktop | [`windows/`](windows/) |
| Aprovações e histórico de decisões | [Documento único de decisões](DECISOES.md) |

Decisão de ID maior refina ou substitui a anterior. Documentos em [`archive/`](archive/README.md) preservam histórico. Divergência entre documentos sem decisão posterior exige alinhamento antes de implementar. Em divergência entre código e documento, vale a ordem de `.claude/rules/higiene-e-padronizacao-repositorio.md` §3.

## Perímetro: o que é deste repositório

| Produto | Onde vive |
| --- | --- |
| **SignallQ Android** | `android/` neste repositório |
| **SignallQ Windows desktop** | `windows/` neste repositório (ver [`windows/`](windows/)) |
| **Backend Cloudflare** (Workers, D1) | `integrations/cloudflare/` neste repositório |
| **Site signallq.com** | `web/` neste repositório (governado por `web/AGENTS.md`) |
| SignallQ Admin | repositório `buildea-admin` |
| SignallQ Pro | descontinuado permanentemente ([ADR-016](DECISOES.md#adr-016)) |

O `signallq-admin-worker` é **deste** repositório, embora o painel Admin que o consome não seja. Assets de marca vivem em `brand/` na raiz.

## Pastas

| Pasta | Conteúdo |
| --- | --- |
| `features/` | Uma página por feature (negócio e técnico, mapa de código, eventos, flags, testes). Comece por `features/INDEX.md` |
| `modulos/` | Um documento por módulo Gradle |
| `CONTRATOS/` | OpenAPI dos Workers e índice de schemas reais (Room, D1) |
| `operations/` | Runbooks: release, hotfix, rollback, assinatura, custos |
| `technical/` | Referências técnicas pontuais (schema da Admin API, mapas de campo de equipamento, fluxo de IA, analytics) |
| `functional/` | Specs funcionais pontuais que não migraram para `01-funcional.md` |
| `design-system/` | Decisões de design; o vigente está em `04-design-system.md` |
| `prototypes/` | Pacotes de prototipação; não são fonte canônica |
| `legal/` | Política de privacidade e termos de uso; não editar sem revisão |
| `templates/` | Modelos para documento novo |
| `testing/` | Casos de teste do Firebase Test Lab |
| `windows/` | Documentos do SignallQ Windows desktop |
| `archive/` | Decisões originais (`archive/decisoes/`), governança de squads anteriores e instruções de recuperação |

## Regras de manutenção

Documento substituído é **removido**, não movido para pasta morta: o git é o arquivo. Mudou o comportamento de uma feature? Atualize `features/<slug>.md` na mesma PR. Decisão nova entra ao final de [`DECISOES.md`](DECISOES.md). O CI roda `scripts/validar-docs.sh`. Política completa em [`.claude/rules/politica-documentacao-viva.md`](../.claude/rules/politica-documentacao-viva.md).
