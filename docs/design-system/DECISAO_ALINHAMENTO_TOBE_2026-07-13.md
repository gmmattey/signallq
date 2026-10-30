---
title: "Decisão — Alinhamento do design ao Fluxo de Telas To-Be"
description: "Registro histórico: Fluxo de Telas (#5B21D6) substituiu o manual MD3 de 2026-07-11 (#6C2BFF) como fonte de paleta."
type: "adr"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Decisão — Alinhamento do design ao Fluxo de Telas To-Be

Resumo condensado. Valores vigentes: `docs/04-design-system.md` e `android/app/.../ui/SignallQTheme.kt`
(o código vence). Texto integral anterior: `git show f6f9d437:docs_ai/design-system/DECISAO_ALINHAMENTO_TOBE_2026-07-13.md`.

## O que aconteceu

Dois documentos do mesmo projeto Claude Design especificavam paletas MD3 diferentes: o manual MD3 estrito
(2026-07-11, `primary=#6C2BFF`) e o Fluxo de Telas (mais recente, `primary=#5B21D6`).

## Decisão

O **Fluxo de Telas** passa a ser a fonte de verdade; o manual de 2026-07-11 deixa de valer para paleta e forma.

| Token | Antes | Depois |
|---|---|---|
| `primary` | `#6C2BFF` | `#5B21D6` |
| `secondary` | derivado do primary | `#2851B8` (azul fixo) |
| `tertiary` | tríade HCT | não definido pela spec (alias legado, não confirmado) |
| Escala tipográfica | 15 estilos | 12 estilos, fonte única Google Sans Flex |
| Espaçamento | 6 degraus | 8 degraus (`lg` 16 → 20) |
| Raio | card 12 / botão 12 | card 16 / botão 20 / sheet 28 / dialog 24 |

Elevação tonal, state layers, motion e densidade de ícone não foram redefinidos pela spec e mantêm o
manual de 2026-07-11.
