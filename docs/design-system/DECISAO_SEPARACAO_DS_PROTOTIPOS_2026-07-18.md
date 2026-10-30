---
title: "Decisão — Separar Design System de Protótipos no Claude Design"
description: "Telas e fluxos não pertencem ao design system: DS reutilizável em projeto próprio, protótipos em outro."
type: "adr"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Decisão — Separar Design System de Protótipos (2026-07-18)

Resumo condensado. Texto integral anterior: `git show f6f9d437:docs_ai/design-system/DECISAO_SEPARACAO_DS_PROTOTIPOS_2026-07-18.md`.

## Contexto

O "gêmeo digital" React (`packages/design-system/`) foi sincronizado ao Claude Design com telas e sheets
dentro da biblioteca de componentes. Telas são composições de produto, não peças reutilizáveis.

## Decisão (executada em 2026-07-18)

- Projeto Claude Design **`SignallQ Design System`** (`2d25d7a1-31b2-4ac3-881f-72dbc8f35a29`): só o
  reutilizável — tokens, primitivos (Avatar, Badge, Icon, SignalBars), layout (BottomNav, Card, Overline,
  PhoneFrame, ScreenScroll, SheetFrame, StatusBar, TopBar) e animações (Thinking, TypeOut). 14 componentes.
- Projeto antigo `e77ea465-…` renomeado **"SignallQ — Protótipos"**: hospeda fluxos (`tobe/`, `templates/`,
  `uploads/`), não o DS.
- `.design-sync/config.json` fixa só o projeto do DS (ver `packages/design-system/.design-sync/conventions.md`).

Não toca código do app Android.
