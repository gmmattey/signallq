# Handoff: SignallQ Design System → Claude Code

> **Fonte de verdade estendida:** `docs/04-design-system.md` — catálogo completo de estados
> semânticos, sistema de profundidade (4 níveis), regras de gráficos, conteúdo simulado,
> acessibilidade e mapeamento Compose. Este README é a referência rápida de tokens; para governança
> e detalhe de componente, consultar o documento acima.

## Overview

Este pacote transforma o **SignallQ Design System** em uma **Claude Code Skill** pronta para
ser instalada no seu repositório. O objetivo não é implementar telas novas (elas **já
existem** no app Android `io.signallq.app`), e sim garantir que **todo trabalho futuro de
UI feito com o Claude Code siga o design system perfeitamente** — mesmos tokens, mesma voz,
mesmos componentes.

Uma vez instalada, sempre que você pedir uma tela, um componente ou um ajuste de UI, o
Claude Code reconhece a skill pela sua `description`, carrega as regras e os tokens, e gera
código já dentro do padrão SignallQ.

---

## Sobre os arquivos deste pacote

A pasta `.claude/skills/SignallQ-design/` contém o design system completo, no formato que o
Claude Code reconhece automaticamente. **Não é código de produção para copiar e colar** — é a
**fonte de verdade de design** que o agente consulta:

| Arquivo / pasta | Conteúdo |
|---|---|
| `SKILL.md` | Manifesto da skill (frontmatter `name` / `description` / `user-invocable`). É isso que o Claude Code lê para decidir quando ativar a skill. |
| `README.md` | Fundamentos completos: contexto do produto, voz/conteúdo, fundamentos visuais, iconografia, índice. |
| `colors_and_type.css` | Todos os design tokens como CSS vars + classes de tipografia M3. **A fonte de verdade dos valores.** |
| `assets/` | Marca "SignallQ" (`ic_launcher.png`, `ic_launcher_foreground.png`). |
| `preview/` | Cards de referência do DS (cores, tipo, espaçamento, componentes) — abra no navegador para visualizar. |
| `ui_kits/android/` | Recriação React fiel do app (`index.html` interativo + componentes JSX). `chrome.jsx` carrega os tokens `LK` e os primitivos compartilhados. |

> Os tokens aqui foram **engenharia-reversa do próprio codebase Android** (`SignallQTheme.kt` →
> `LkColors`, `LkSpacing`, `LkRadius`, `signallQTypography`). Os nomes batem com o que já está no
> seu código Kotlin/Compose — use os valores abaixo como tabela de equivalência.

---

## Fidelidade

**Alta fidelidade (hi-fi).** Cores, tipografia, espaçamento, raios e semântica de status são
os valores finais e exatos, extraídos do app real. Qualquer UI nova deve bater pixel-a-token
com esta referência.

---

## Instalação (3 passos)

### 1. Copie a skill para o seu repositório

Arraste a pasta `.claude/` deste pacote para a **raiz do seu projeto**:

```
seu-projeto/
└── .claude/
    └── skills/
        └── SignallQ-design/
            ├── SKILL.md
            ├── README.md
            ├── colors_and_type.css
            ├── assets/
            ├── preview/
            └── ui_kits/android/
```

- `.claude/skills/` → skill **do projeto**: vai pro Git, todo o time herda. **Recomendado**,
  já que você quer que *o projeto* siga o DS.
- `~/.claude/skills/` → skill **pessoal**: só na sua máquina.

### 2. (Recomendado) Reforce no `CLAUDE.md`

Skills são carregadas sob demanda. Para deixar o DS **sempre presente**, adicione ao
`CLAUDE.md` da raiz do projeto:

```md
## Design System
Toda UI deste projeto segue o SignallQ Design System.
Antes de criar ou editar telas/componentes, consulte a skill `SignallQ-design`
(.claude/skills/SignallQ-design/README.md) e use os tokens de colors_and_type.css /
SignallQTheme.kt como fonte de verdade.
Não-negociáveis: Material 3 claro, acento violeta #5B21D6, secondary azul fixo #2851B8,
semântica de status verde/âmbar/vermelho, ícones Material Symbols (Outlined), tipo Google Sans Flex
(fonte única do app, todos os estilos), grid 8dp, card radius 16px e elevação tonal (sem sombra dura
isolada), superfície SignallQ (IA) DESCONTINUADA (não implementar), copy em PT-BR com "você" e SEM emoji.
```

### 3. Use

- **Automático:** peça qualquer coisa de UI ("crie a tela de configurações de DNS") e o
  Claude Code ativa a skill pela `description`.
- **Explícito:** "use a skill `SignallQ-design` para …".

Para confirmar que está instalada, rode `/doctor` ou liste skills no Claude Code.

---

## Como manter as telas existentes alinhadas

Como as telas já existem, o ganho real vem de garantir que elas usem os **tokens** como
fonte de verdade (e não valores hardcoded). Antes de gerar UI nova, peça ao Claude Code para:

1. Verificar se `SignallQTheme.kt` (`LkColors` / `LkSpacing` / `LkRadius` / `signallQTypography`) é
   de fato a origem de cores, espaçamentos e tipos das telas atuais.
2. Substituir qualquer cor/spacing hardcoded pelos tokens equivalentes (tabela abaixo).
3. Reaproveitar os componentes existentes (`…/ui/component/*.kt`) em vez de recriar.

---

## Design Tokens (referência rápida) — Fluxo de Telas To-Be (correção 2026-07-13)

> CSS var (artefatos web) → valor → equivalente Compose (`SignallQTheme.kt`, a atualizar pelo
> Camilo). **Nomenclatura MD3:** `--md-sys-color-{role}` / `on-{role}` / `{role}-container` /
> `on-{role}-container`. Os nomes antigos (`--accent`, `--bg-card`, `--text-primary`
> etc.) seguem como **aliases deprecados** em `colors_and_type.css` só por compatibilidade —
> não usar em artefato novo.

### Primary / Secondary (secondary é azul FIXO, não deriva mais de primary)
| Token | Valor | Uso |
|---|---|---|
| `--md-sys-color-primary` | `#5B21D6` | CTA primário, seleção, nav ativa |
| `--md-sys-color-on-primary` | `#FFFFFF` | Texto/ícone sobre primary |
| `--md-sys-color-primary-container` | `#EAE0FF` | Fill de destaque suave |
| `--md-sys-color-on-primary-container` | `#210A5C` | Texto sobre primary-container |
| `--md-sys-color-secondary` | `#2851B8` (azul, fixo) | Chip móvel, DNS privado, links secundários |
| `--md-sys-color-on-secondary` | `#FFFFFF` | Texto/ícone sobre secondary |
| `--md-sys-color-secondary-container` | `#DCE6FF` | — |
| `--md-sys-color-on-secondary-container` | `#001A41` | — |

`tertiary` **não é definido pelo Fluxo de Telas** — token mantido só por compatibilidade de
alias, com valor não-confirmado (`#B03A5B`, herdado do manual anterior). Não usar em artefato novo.

**Dark theme:** `primary=#D0BCFF` / `secondary=#AAC7FF` (valores próprios do Fluxo de Telas, não
mais "tone80 derivado" da migração anterior).

### Status (semáforo)
| Token | Valor | Uso |
|---|---|---|
| `--md-sys-color-success` | `#146C2E` | Conexão boa, testes OK |
| `--md-sys-color-warning` | `#8A5000` | Alertas moderados |
| `--md-sys-color-error` | `#BA1A1A` | Erros críticos, falhas |
| `--md-sys-color-error-container` / `on-error-container` | `#FFDAD6` / `#410002` | — |
| `--md-sys-color-success-container` / `on-success-container` | `#B6F2BE` / `#04210D` | — |
| `--md-sys-color-warning-container` / `on-warning-container` | `#FFDDB3` / `#2B1700` | — |

### Fases do SpeedTest
| Token | Valor |
|---|---|
| `--md-sys-color-phase-latencia` | `#2563EB` |
| `--md-sys-color-phase-download` | `#146C2E` |
| `--md-sys-color-phase-upload` | `#8A5000` |

### Superfícies — Light (5 níveis de surface container, todos com hex confirmado)
| Token | Valor |
|---|---|
| `--md-sys-color-background` / `surface` | `#FFFFFF` |
| `--md-sys-color-surface-dim` | `#DED8E1` |
| `--md-sys-color-surface-container-lowest` | `#FFFFFF` |
| `--md-sys-color-surface-container-low` | `#F8F5FB` |
| `--md-sys-color-surface-container` | `#F3EEFA` |
| `--md-sys-color-surface-container-high` | `#ECE5F5` |
| `--md-sys-color-surface-container-highest` | `#E6DDF2` |
| `--md-sys-color-on-surface` | `#1C1B1F` |
| `--md-sys-color-on-surface-variant` | `#49454F` |
| `--md-sys-color-outline` | `#79747E` |
| `--md-sys-color-outline-variant` | `#CAC4D0` |

### Superfícies — Dark
| Token | Valor |
|---|---|
| `--md-sys-color-background` / `surface` | `#131217` |
| `--md-sys-color-surface-container-low` | `#1D1B20` |
| `--md-sys-color-surface-container` | `#211F26` |
| `--md-sys-color-outline` | `#948F99` |

### SignallQ (IA — DESCONTINUADA, tela 7 do Fluxo de Telas)
Superfície sempre escura, mantida só como registro histórico do As-Is — **não implementar em
tela nova**: nenhuma rota, componente ou dado desta tela deve existir no app final.
| Token | Valor |
|---|---|
| `--SignallQ-black` | `#0D0D1A` (background) |
| `--SignallQ-dark-surface` | `#1A0B2E` |
| `--SignallQ-dark-card` | `#1E1130` |
| `--SignallQ-text-on-dark` | `#F3F4F6` |

### Elevação tonal — 5 níveis (manual §3)
| Nível | Tint | Shadow |
|---|---|---|
| level0 | `#FFFFFF` | none |
| level1 | `#FDFBFF` | `0 1px 2px rgba(0,0,0,.08)` |
| level2 | `#FBF7FF` | `0 1px 3px rgba(0,0,0,.12)` |
| level3 | `#F8F2FF` | `0 2px 6px rgba(0,0,0,.16)` |
| level4 | `#F5EDFF` | `0 3px 8px rgba(0,0,0,.18)` |

### State layers (manual §6) — aplicar a card clicável, itens de lista/sheet, tabs, ações de TopBar, chips tocáveis
`hover` 8% · `focus` 10% · `pressed` 12% · `dragged` 16% — overlay sobre `onSurface`/`onPrimary`,
nunca mudança de cor de fundo.

### Motion (manual §7)
`emphasized` / `standard` = `cubic-bezier(.2,0,0,1)` · durações `short 100ms · medium 200ms ·
long 300ms · extra-long 400ms`.

### Espaçamento — grid 8dp, 8 degraus (Fluxo de Telas)
`--space-xs` 4 · `--space-sm` 8 *(unidade base)* · `--space-md` 12 · `--space-base` 16
*(padding de tela + card — era `--space-lg` na escala antiga de 6 degraus)* · `--space-lg` 20
*(novo degrau)* · `--space-xl` 24 · `--space-xxl` 32 · `--space-xxxl` 40 *(novo degrau — CTA de
onboarding, rodapés)*. Toque mínimo: **48dp** (padrão MD3/Android — corrigido em 2026-07-19; o
antigo `44px` aqui e o `56dp` de `PRODUCT.md` estavam ambos errados, ver `docs/04-design-system.md`
seção 11), CTAs full-width em geral 40px de altura.

### Forma por componente (specs literais do Fluxo de Telas)
`Card` 16px · `SheetFrame` 28px (cantos superiores) · `Button` 40px altura / 20px radius (token
novo, fora da escala de 7 degraus) · `Field` 12px · `Chip`/`Badge` 999px (pill) · `Dialog` 24px
(token novo, ex. RestartDialog) · `IconButton` 40×40px circular. Escala base de 7 degraus mantida
para compatibilidade: `none` 0 · `xs` 4 · `sm` 8 · `md` 12 · `lg` 16 · `xl` 28 · `full` 999.

### Ícones (Fluxo de Telas)
Material Symbols Outlined, variable font (eixos `FILL` 0|1 · `wght` 400|500|700 · `GRAD` 0 ·
`opsz` 24). `24px` tamanho padrão em `currentColor`; variações pontuais 13–64px conforme
componente. `FILL 1` só no ícone ativo da Bottom Nav — resto `FILL 0` (outline).

### Tipografia — fonte única do app, escala de 12 estilos (Fluxo de Telas)
**Google Sans Flex** (fallback `Google Sans` → `Roboto` → `system-ui`), pesos 400/500/600/700, em
**todos** os estilos — não há mais split display/body por família. Implementado em PR #939 com
licença SIL OFL, arquivos embutidos no APK. Classes em `colors_and_type.css`:
`display-small` (700, 34/40) · `headline-large` (700, 26/32) · `headline-small` (600, 22/28) ·
`title-large` (600, 20/26) · `title-medium/small` (500, 16/22 · 14/20) · `body-large/medium/small`
(400, 16/24 · 14/20 · 12/16) · `label-large/medium/small` (500, 14/20 · 12/16 · 11/16) ·
`overline` (label-small + UPPERCASE, +0.3px). `display-large`, `display-medium` e
`headline-medium` foram **removidos** — nenhuma tela do Fluxo de Telas usa estilo maior que
displaySmall.

### Container de logo/badge de marca de terceiro (operadora, jogos — 2026-07-17)
Logo real (bundled ou remoto) de marca que a SignallQ não controla (operadora, artwork de jogo)
fica sempre sobre um container com fundo **branco fixo** (`#FFFFFF`, hardcode — não deriva de
`surface`/tema) + borda **1dp `outlineVariant`** (token `border`, já existe em `LocalLkTokens`).
Motivo: o asset assume fundo claro e "some" no tema escuro se ficar exposto direto ao fundo do
card. Fallback sem asset (monograma/sigla) usa fundo **sólido** da cor de marca ou `primary` +
texto branco — nunca fundo translúcido (`alpha` baixo tem contraste insuficiente). Ver decisão
completa em `docs/design-system/DECISAO_CONTAINER_LOGO_MARCA_2026-07-17.md`.

### Alpha (convenção do codebase, fora do escopo MD3)
Tints são cor-em-alpha por sufixo hex: `1A`=10% · `1F`=12% · `26`=15% · `33`=20% ·
`40`=25%. Ex.: card de Wi-Fi conectado = `success @12%` fill; seleção/IA = `primary @8–12%`
fill + `primary @25–30%` borda; banner de alerta = `warning @12%` fill.

---

## Regras não-negociáveis (resumo)

**Visual**
- Material Design 3, claro, brilhante, neutro. `primary=#5B21D6` (chave violeta) para destaque;
  `secondary=#2851B8` é um azul FIXO (não deriva mais do primary); status verde/âmbar/vermelho
  carregam o significado.
- **Sem** imagens fotográficas, hero full-bleed, padrões/texturas ou gradientes decorativos.
  Gradiente existe em exatamente 2 lugares: avatar de perfil e header de Diagnóstico/IA
  (linear `primary → accent-blue`) — a superfície de IA em si está **descontinuada** (tela 7).
- Cards: raio **16px**, fundo `surface`/`surface-container`, hairline `1px outline-variant`,
  **elevação tonal** (5 níveis, tint de superfície + shadow sutil — não sombra dura isolada).
  SheetFrame usa raio próprio de **28px** (cantos superiores).
- State layers em todo componente clicável: hover 8% / focus 10% / pressed 12% / dragged 16%
  sobre `onSurface`/`onPrimary`.
- Cards "status" tingidos: cor semântica em alpha baixo (fill) + ~25–30% (borda).
- Ícone em chip circular preenchido com a cor semântica ~10% é o motivo recorrente. Densidade:
  24dp padrão / 20dp TopBar compacta / 18dp inline.
- Barras de sinal: glyph vertical de 4 barras (alturas 6/9/12/16dp, largura 3dp, raio 1dp),
  cor pela qualidade (verde Forte / âmbar Regular / vermelho Fraco), vazias em `outline`.

**Conteúdo / voz**
- Português do Brasil, sempre. Fala **com** o usuário usando "você", a partir do mundo dele
  ("Sua internet por fibra").
- Títulos e botões em **sentence case**; overlines/labels de seção em **UPPERCASE** com
  letter-spacing leve.
- Métrica crua **sempre** acompanhada de um veredito humano (`Excelente`, `Bom`, `Regular`,
  `Fraco`, `Forte`). Separador inline: ponto médio `·`.
- **Sem emoji.** Significado vem de ícones Material + cor semântica. Único glyph decorativo: o
  `✓` dentro do badge "Conectado".

**Sistemas**
- Ícones: **Material Symbols / Material Icons (Outlined)** apenas. Na web:
  `<span class="material-symbols-outlined">wifi</span>`.
- Superfícies **SignallQ** (IA) são **sempre escuras** (`#0D0D1A` / `#1A0B2E` / `#1E1130`),
  independentes do tema.
- Movimento contido e funcional: fades/offsets rápidos, ripple M3, sem bounce nem floreio.
  Nav inferior some no scroll-down e durante teste em andamento.

**Layout fixo**
- `CenterAlignedTopAppBar` (título centralizado, `ProfileAvatarButton` à esquerda, ação
  contextual à direita) + `NavigationBar` de 5 abas (Início · Velocidade · Sinal · Histórico ·
  Ajustes). Conteúdo rola em `LazyColumn`. Telas secundárias sobrepõem as abas; fluxos
  profundos ganham seta de voltar. `ModalBottomSheet` é o padrão para permissões, análise de
  topologia e pickers.

**Anúncio nativo** (`…/ui/component/ads/`, issue #555 — padrão oficial desde v0.23.0)
- Três variantes por contexto, nunca escolha por preferência: `NativeAdCard` (card cheio,
  dispensável — Resultado, Histórico) · `NativeAdRow` (linha compacta — Velocidade idle) ·
  `NativeAdListRow` (linha dentro de uma lista existente — Dispositivos, dentro da lista de
  dispositivos conectados, nunca em Infraestrutura).
- Disclosure `AdBadge` sempre visível: "Patrocinado" (tom neutro, AdMob — única fonte ativa
  hoje) ou "Parceiro" (tom `accentBlue`, afiliado/parceiro curado — componente já suporta,
  catálogo ainda não existe).
- Nunca confundir com card orgânico: borda **tracejada** (`Modifier.dashedBorder`, nunca
  sólida), CTA **outline violeta** (nunca sólido — sólido é exclusivo de CTA primário
  orgânico), sem foto/hero, ícone do anunciante em **chip quadrado** (nunca círculo).
  Componente inteiro é omitido (não vira placeholder) quando não há criativo carregado.
- Referência completa: `docs/design-system/COMPONENTS_ANDROID.md` (seção "Monetização —
  Anúncio Nativo").

---

## Telas / Superfícies do app (referência)

As superfícies centrais — recriadas em alta fidelidade em `ui_kits/android/`:

- **Início (Home):** estado da conexão, cards de resumo, atalhos.
- **Velocidade (SpeedTest):** seletor segmentado Rápido/Completo/Triplo, botão violeta de
  iniciar com glow, gauge ao vivo por fase (latência/download/upload) → **Resultado**.
- **Sinal (Wi-Fi):** card de rede conectada, chips de filtro de banda (Todos / 2.4 / 5 / 6
  GHz), lista de redes com barras de sinal e métricas cruas + veredito.
- **Histórico:** resultados anteriores.
- **Diagnóstico / SignallQ (IA):** superfície escura, bolhas de chat com TypewriterText, bubble
  "pensando" pulsante.

Veja `_ref/` (capturas do app real, **não** para shipping) e `preview/` para os cards do DS.

---

## Arquivos para referência

- `colors_and_type.css` — tokens + classes de tipo (importe em qualquer artefato web).
- `README.md` (dentro da skill) — fundamentos completos de conteúdo, visual e iconografia.
- `ui_kits/android/index.html` — protótipo click-through; `chrome.jsx` tem os tokens `LK` e
  primitivos compartilhados; `screens.jsx`, `speedtest.jsx`, `signallq.jsx`, `app.jsx`.
- `preview/*.html` — cards de cores, tipo, espaçamento, raios e componentes.
- No seu codebase: `SignallQTheme.kt` (tokens), `…/ui/component/*.kt` (33+ composables,
  incluindo `…/ui/component/ads/` — anúncio nativo), `…/ui/screen/*.kt` (telas),
  `docs/design-system/*.md` (docs originais).
