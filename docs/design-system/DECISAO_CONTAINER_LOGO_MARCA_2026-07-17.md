---
title: "Decisão — Container de logo de marca de terceiros"
description: "Regra de container (fundo branco fixo + anel) para logos de operadora; implementada em OperadoraBadge.kt."
type: "adr"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Decisão — Container de logo de marca de terceiros

Resumo condensado. Fonte de verdade do comportamento: `OperadoraBadge.kt` e `docs/04-design-system.md`
(linha `SignallQOperadoraBadge`). Texto integral anterior: `git show f6f9d437:docs_ai/design-system/DECISAO_CONTAINER_LOGO_MARCA_2026-07-17.md`.

## Problema

Logo de terceiro assume fundo claro no próprio arquivo; sobre o fundo escuro do tema, partes do logo somem.

## Decisão (implementada)

1. Logo real (bundled ou remoto) fica em container **circular** com fundo `Color.White` fixo — hardcode
   intencional, não deriva de token de superfície, porque o fundo não pode mudar entre temas.
2. Anel de **1dp** com `LocalLkTokens.current.border` (`outlineVariant`), para separar o container branco de
   cards claros.
3. Fallback de monograma (sem asset) usa fundo sólido de cor de marca (ou `primary`) e texto `Color.White`,
   sem anel. Nunca fundo translúcido (contraste quase nulo no tema claro).
4. O tamanho, o padding interno (`size * 0.08f`) e a API do composable não mudam.

## Alternativas descartadas

- Só anel, sem fundo sólido: o logo continua caindo sobre o fundo do tema.
- Fundo com `surface`: no tema escuro reproduz o problema original.

## Nota

O fluxo "Jogos" (`GameArtworkBadge`) citado na versão original foi removido (issue #1487); a regra vale
hoje apenas para operadora.
