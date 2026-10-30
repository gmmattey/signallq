---
title: "Brand spec do protótipo — SignallQ Android 2.0"
description: "Tokens de marca, cor e tipografia aplicados no protótipo da Jornada Android 2.0."
type: "funcional"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-08-16"
---

# SignallQ 2.0 — brand spec

O sistema visual é um guia Android calmo e preciso: branco ou preto absolutos, superfícies neutras discretas, Google Sans Flex e violeta reservado à ação e seleção.

```css
:root {
  --bg: oklch(100% 0 0);
  --surface: oklch(97.6% 0.002 286);
  --fg: oklch(22.5% 0.011 300);
  --muted: oklch(40.3% 0.021 300);
  --border: oklch(84.5% 0.015 300);
  --accent: oklch(45.2% 0.245 293);
}
```

- Display/body: `'Google Sans Flex', 'Google Sans', Roboto, system-ui, sans-serif`.
- Mono: `ui-monospace, 'Roboto Mono', monospace` apenas quando alinhamento numérico for necessário.
- Hierarquia nasce de espaço, peso e alinhamento; cards apenas para agrupamento real e sem contorno.
- A conclusão humana precede métricas; detalhes técnicos são progressivos.
- Violeta aparece somente em CTA, foco, seleção e navegação ativa; azul fica restrito ao símbolo oficial.
- Movimento explica continuidade e respeita redução de movimento.
