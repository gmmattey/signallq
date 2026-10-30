---
title: "Spec — Componente BrandEndorsement (by 7A) no Android"
description: "Assinatura institucional 'by 7A' no app Consumer. Draft: não implementado no Android (nenhuma ocorrência em android/)."
type: "funcional"
status: "draft"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Spec — `BrandEndorsement` ("by 7A") no Android

**Status: draft — não implementado.** Nenhuma ocorrência de "by 7A" ou do símbolo em `android/` (verificado em
2026-10-04). As implementações React do Admin e do Site pertencem a `buildea-admin` e `signallq-web`; o SignallQ
Pro foi descontinuado (ADR-016). Versão integral anterior (inclui Admin/Site/PRO):
`git show f6f9d437:docs_ai/design-system/SPEC_BRAND_ENDORSEMENT_BY_7A_2026-07-26.md`.

## Regras de conteúdo

- Texto `by 7A`: "by" em peso normal, "7A" em bold, mesmo tamanho de fonte (não é lockup com tamanhos diferentes).
- Nunca o lockup completo "7A Labs" em tela operacional (só em texto legal/institucional corrido).
- Não repetir em todas as telas. Nunca na Home, na navegação principal, em cards ou durante o Speedtest.

## Asset

Símbolo vetorial já existe em `brand/7alabs-symbol-{dark,light}.svg` (viewBox ~1.17:1, não quadrado; ver
`brand/README.md`). Falta só o Composable e o wiring.

## Contrato sugerido (Compose)

- Nome `BrandEndorsement`; parâmetros `variant` (`Text` | `SymbolText`), `size` (`Compact` | `Default`),
  `symbolRes: Int? = null`.
- Cor: token de texto de menor hierarquia do tema (`onSurfaceVariant`); fonte menor que o corpo.
- Símbolo decorativo: `contentDescription = null`; o texto "by 7A" nunca é ocultado de leitor de tela.
- Preservar proporção do símbolo (altura fixa, largura automática).

## Onde entra

Ajustes/Sobre (versão, créditos, licenças). `AjustesScreen.kt` está acima de 700 linhas
(higiene §4.4): extrair como seção própria, não inchar o arquivo.

## Pendente

Composable, wiring em Ajustes/Sobre, validação visual nos dois temas e teste de acessibilidade.
