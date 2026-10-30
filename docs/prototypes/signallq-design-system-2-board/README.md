---
title: "Prancha visual — SignallQ Design System 2.0"
description: "Referência navegável de foundations e componentes centrais do Design System 2.0."
type: "funcional"
status: "draft"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "0.2.0"
---

# Prancha visual — SignallQ Design System 2.0

Artefato estático de referência visual das foundations e dos componentes centrais do Design System 2.0. Os tokens já estão em Compose (`SignallQTheme.kt`, `ui/component/`); a prancha é só apoio visual — em divergência vale o código e `DESIGN_SYSTEM.md`.

## Escopo

- foundations: cor, tipografia, espaçamento, forma, estados e movimento;
- componentes centrais: botões, campos, chips, badges, banners, listas, resultado e estados de tela;
- temas claro e escuro;
- comportamento responsivo básico.

A prancha incorpora localmente os mesmos arquivos Google Sans Flex usados pelo Android, nos pesos
400, 500, 600 e 700. O carregamento não depende de a fonte estar instalada no computador.

Não representa código de produção. Neutros claros e `outline` da prancha podem diferir dos valores do app (ver "Estado de implementação" no spec).

## Abrir

Abra `index.html` diretamente no navegador. O seletor no cabeçalho alterna entre os temas.

`brand.html` apresenta a direção premium da expressão de marca: posicionamento, uso do símbolo,
voz, paleta e exemplos de aplicação. Ela preserva os ativos oficiais sem redesenhá-los.

## Fontes

- [`../../design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md`](../../design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md)
- [`../../DESIGN_SYSTEM.md`](../../04-design-system.md)
- `foundations.css`, cópia da skill canônica `SignallQ-design` no momento de criação da prancha;
  `styles.css` aplica os overrides do 2.0.
