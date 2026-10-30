---
title: "Protótipo navegável — Jornada Android 2.0"
description: "Referência visual e de navegação histórica do épico #1647 (encerrado); o app Android real vence em qualquer divergência."
type: "funcional"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Protótipo navegável — Jornada Android 2.0

Referência visual e de navegação usada nas fatias do épico [#1647](https://github.com/buildea-labs/signallq/issues/1647)
(encerrado). A jornada foi implementada; **em divergência com o app, vale o código**
(`docs/01-funcional.md` descreve o fluxo implementado).

## Como abrir

Abra `index.html` no navegador (autocontido, sem servidor):

```bash
open docs/prototypes/signallq-android-2-0/index.html
```

## Assets

`assets/` reaproveita arquivos que já existem no repositório (`android/app/src/main/res/font/` e `brand/`).
`assets/fonts/material-symbols-outlined.ttf` é um **symlink** para a cópia do app; se aquele arquivo
mudar de lugar, atualize o symlink. No Windows o Git só materializa symlinks com `core.symlinks=true`;
sem isso os ícones do protótipo não renderizam (falha silenciosa). Solução: copiar
`android/app/src/main/res/font/material_symbols_outlined.ttf` por cima do arquivo do protótipo.

## Documentos do pacote

- [`COVERAGE.md`](COVERAGE.md) — telas e fluxos cobertos pelo protótipo e o que ficou de fora.
- [`brand-spec.md`](brand-spec.md) — tokens de marca, cor e tipografia aplicados.

## Limite

O protótipo mostra **direção visual e de navegação**, não comportamento. Regras de negócio, motores,
telemetria e integrações vêm do código e das specs
[`SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md`](../../design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md) e
[`JORNADA_ANDROID_GUIADA_2_SPEC.md`](../../functional/JORNADA_ANDROID_GUIADA_2_SPEC.md).
