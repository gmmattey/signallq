---
name: priscila-design-system-ux
description: Diretrizes de design, UX/UI, tokens, acessibilidade e auditoria visual da Priscila para o SignallQ (Android, Windows desktop e web).
---

# Skill: priscila-design-system-ux

Guia a **Priscila** na definição de jornadas, fidelidade de protótipo e auditoria visual do SignallQ. Fontes: `docs/04-design-system.md`, `docs/design-system/`, `packages/design-system/` e o protótipo canônico da tela. Esta skill não duplica tokens; consulte-os lá.

## 1. Princípios de experiência
- **Diagnóstico, não painel:** a tela responde "o que está acontecendo, quão certo estou, o que faço agora, resolveu?". O dado medido é protagonista; zero ornamento vazio e zero "card soup".
- **Confiança visível:** dado medido, inferência determinística e interpretação de IA têm tratamento visual distinto. Incerteza aparece, não se esconde.
- **Ergonomia por plataforma:**
  - **Android:** Material 3, alcance do polegar, navegação do sistema, estados de permissão e fallback quando a plataforma não expõe o dado.
  - **Windows desktop:** janela redimensionável, teclado e ponteiro, densidade maior sem virar planilha.
  - **Web:** responsivo, rápido e coerente com a marca.

## 2. Design System e tokens
- Cor, tipografia, espaçamento e forma vêm dos tokens. Zero `Color(0x...)` ou `dp` solto quando existe token oficial.
- Hierarquia por elevação e superfície, não por borda pesada.
- Tema claro e escuro de primeira classe, com contraste preservado.
- Ativos de marca em `brand/` são oficiais: não redesenhe, deforme nem recolora.

## 3. Acessibilidade inegociável
- WCAG AA: contraste mínimo 4.5:1 em texto normal.
- Fonte grande do sistema sem quebrar layout.
- Nenhuma informação só por cor: ícone e rótulo acompanham.
- Alvo de toque mínimo de 48 dp no Android; rótulos de TalkBack coerentes.

## 4. Auditoria visual
- Compare a **tela real aberta** com o protótipo.
- Audite todos os estados: vazio, carregando, resultado, parcial, erro, offline, permissão negada e modais.
- Se o Camillo achar caso de borda não previsto, a especificação volta para alinhamento antes do código.
- Separe o que foi **visto rodando** do que é leitura de código.
