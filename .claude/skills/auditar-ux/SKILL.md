---
name: auditar-ux
description: Auditoria profunda de UX, acessibilidade, arquitetura de informação e aderência ao Design System do SignallQ Android.
---

# Auditar UX

Use para revisão multi-tela, fluxo completo, navegação ou problema de usabilidade que não cabe num `design-check` pontual.

A skill é procedimento. Marcelo pode acioná-la para produto, Priscila para auditoria de experiência, Camillo para preparar uma implementação ampla e Renan para revisão de qualidade.

## Fontes

- `docs/04-design-system.md` — sistema implementado;
- `docs/design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md` — direção futura enquanto marcada como draft;
- `docs/01-funcional.md` — fluxo implementado;
- `docs/functional/JORNADA_ANDROID_GUIADA_2_SPEC.md` — direção futura enquanto draft;
- skill `SignallQ-design` — referência visual.

Não trate draft como comportamento entregue.

## Auditoria

### Arquitetura de informação

Mapeie destinos do NavGraph e fluxos principais. Verifique:

- funções principais encontráveis sem exploração excessiva;
- nomes de destinos orientados à tarefa do usuário;
- profundidade e back stack previsíveis;
- features avançadas acessíveis sem dominar a navegação;
- um CTA primário claro por contexto.

### Fluxos críticos

Avalie ao menos os fluxos afetados:

- diagnóstico;
- speedtest;
- Wi-Fi/rede;
- histórico;
- configurações;
- Assist/IA quando aplicável.

Para cada fluxo: entrada, estados intermediários, resultado, ação seguinte, erro, offline, cancelamento e recuperação.

### Design System

Confirme tokens de cor, tipografia, spacing, shapes, componentes e dark mode contra a fonte canônica. Não invente valores nesta skill.

### Acessibilidade

- WCAG AA;
- TalkBack/semantics;
- touch target;
- ordem de foco/leitura;
- conteúdo compreensível sem depender só de cor;
- estados de loading/erro anunciáveis.

### Diagnóstico compreensível

O usuário deve entender:

- o que foi medido;
- o que o SignallQ concluiu;
- quão confiável é a conclusão quando houver incerteza;
- o que pode fazer a seguir.

Não substitua evidência por copy confiante.

## Gate de arquitetura

Auditoria pode descobrir problema sistêmico, mas não desenha arquitetura escondida. Mudança de navegação raiz, contratos entre módulos ou integração sistêmica segue o gate do Camillo.

## Saída

Para cada achado:

```text
Categoria: UX | Design System | Acessibilidade | Produto
Fluxo/tela: ...
Evidência: ...
Severidade: crítico | importante | melhoria
Problema: ...
Recomendação: ...
Responsável provável: Marcelo | Priscila | Camillo
```

Feche com os 3–5 achados que realmente merecem prioridade. A skill não altera código nem aprova release.
