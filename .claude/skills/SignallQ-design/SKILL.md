---
name: SignallQ-design
description: Gere interfaces e assets aderentes à marca e ao Design System do SignallQ para produção ou protótipos.
user-invocable: true
---

# SignallQ Design

Use esta skill como ponto de partida para **criar** UI/artefato novo do SignallQ. Para conferir uma tela já implementada, use `design-check`; para auditoria multi-tela, use `auditar-ux`.

Leia os arquivos desta skill antes de desenhar:

- `README.md` — contexto e índice;
- `colors_and_type.css` — tokens;
- `preview/` — referências visuais;
- `assets/` — marca;
- `ui_kits/android/` — componentes/recriação de referência.

A fonte implementada de Design System continua sendo `docs/04-design-system.md`; specs futuras marcadas como draft são direção, não comportamento já entregue.

## Princípios

- Material Design 3 e componentes/tokens do projeto;
- não invente segunda paleta, tipografia ou linguagem de componente;
- preserve hierarquia clara e densidade adequada a um app de diagnóstico;
- informação técnica avançada não deve sufocar a ação principal do usuário;
- estados de loading, empty, error, offline e permission denied são parte do design;
- acessibilidade Android/TalkBack e contraste são requisitos, não acabamento posterior;
- não use aparência genérica de “dashboard de IA” nem excesso de cards como solução padrão.

## Produto

Antes de desenhar uma superfície nova, confirme com a direção de produto:

- problema do usuário;
- entrada no fluxo;
- comportamento esperado;
- ação seguinte;
- nível de detalhe técnico necessário;
- o que acontece quando a evidência é incompleta.

Marcelo decide direção de produto; Priscila define jornada, estados e visual; Camillo implementa Compose e valida a semântica de diagnóstico quando a UI representa evidência ou classificação; Renan revisa qualidade. Mudança sistêmica segue o gate do Camillo.

## Artefatos

Para protótipo descartável, mantenha o artefato claramente separado do código de produção. Para produção, reutilize tokens/assets/componentes existentes e siga os padrões Compose do repo.

Esta skill é procedimento de design, não persona e não define modelo de IA.
