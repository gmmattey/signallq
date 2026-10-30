# SignallQ — Design System

> **SignallQ** (app store name "SignallQ app", package `io.signallq.app`) is a native **Android** smart internet-diagnostics app, built in **Kotlin / Jetpack Compose / Material Design 3**. It analyzes a home connection in real time — speed, latency, Wi-Fi signal & channels, DNS, fiber (GPON) modem, 4G/5G mobile signal — and uses AI (Cloudflare Worker) to explain *why* the internet is slow, unstable, or down, in clear, non-technical **Brazilian Portuguese**.
>
> The brand is **SignallQ**, with two named sub-systems: **SignallQ** (the conversational AI assistant) and **SignallQ Pulse** (passive background monitoring).

---

## Sources

This design system was reverse-engineered from a single attached, read-only codebase. The reader is not assumed to have access; paths are recorded in case they do.

| Source | Path | What it gave us |
|---|---|---|
| Android codebase | `SignallQ/android/` (monorepo root) | Source of truth for all tokens, components & screens |
| Theme tokens | `SignallQ/android/app/src/main/kotlin/io/signallq/app/ui/SignallQTheme.kt` | `LkColors`, `LkSpacing`, `LkRadius`, `signallQTypography` |
| Design-system docs | `SignallQ/docs/design-system/*.md` | COLORS, TYPOGRAPHY, SPACING, DESIGN_TOKENS, COMPONENTS_ANDROID, MD3_GUIDELINES — canonical, kept in sync with the code |
| Functional spec | `SignallQ/docs/ANDROID_FUNCIONAL.md` | Screen-by-screen behaviour, flows, copy |
| Mockup v2 spec | `SignallQ/.claude/design-specs/mockup-v2-ui-screens.md` | Pixel specs for Home / Sinal / SpeedTest / Resultado |
| Screen composables | `SignallQ/android/app/src/main/kotlin/io/signallq/app/ui/screen/*.kt` | Layout, exact labels, metric thresholds |
| Components | `…/ui/component/*.kt` | SignallQ, SpeedTest, Pulse, layout primitives |
| Launcher icon | `…/res/mipmap-xxxhdpi/ic_launcher*.png` (copied to `assets/`) | App icon / wordmark "SignallQ" |

**AI worker (referenced, external):** `integrations/cloudflare/ai-diagnosis-worker/` (Cloudflare Worker; provider primário Gemini 2.0 Flash quando configurado, fallback Qwen3 30B MoE FP8).

---

## CONTENT FUNDAMENTALS

The product talks to a **non-technical Brazilian** whose internet "is acting up." The voice is **warm, plain-spoken, reassuring, and concrete** — never a network engineer lecturing.

- **Language:** Brazilian Portuguese, always. Accents and `ç` intact (`Conexão`, `Histórico`, `Oscilação`).
- **Person / address:** Speaks **to** the user as **"você"**, and frames things from *their* world: **"Sua internet por fibra"**, **"Sua conexão"**, **"Voce esta usando a internet do chip"**. The app itself is the helpful narrator ("precisamos da localização", "Conectando ao modem…").
- **Casing:**
  - Screen titles & button labels are **sentence case**: `Resultado do teste`, `Medir velocidade`, `Iniciar`, `Cancelar teste`.
  - **Overlines / section labels are UPPERCASE** with light letter-spacing: `SUA CONEXÃO`, `OUTRAS REDES`, `EXPERIÊNCIA DE USO`, `ÚLTIMO RESULTADO`, `CHIPS ATIVOS`.
- **Tone — jargon, then translation.** Raw metrics are always present (`RSSI −27 dBm · Canal 36 · 433 Mbps`, `RSRP`, `jitter`, `bufferbloat`) **but paired with a human verdict word**: `Excelente`, `Bom`, `Regular`, `Fraco`, `Forte`. Diagnoses lead with a feeling-level headline (`Conexão excelente`) then a one-line plain explanation (`Sua internet está…`).
- **Humanized diagnostics, not error codes.** Status reads as `OK / INFO / ATENÇÃO / CRÍTICO`; usage verdicts are everyday tasks — **Streaming, Gaming, Vídeo Chamada** rated *bom / aceitável / ruim*. Empathy in empty states: *"O Wi-Fi está desligado ou desconectado…"*.
- **Actionable & rights-aware.** Copy pushes a next step (`Testar assim que voltar`, `Permitir leitura do chip`, `Abrir Wi-Fi nas configurações`) and even cites consumer law: *"Abaixo de 40%: você tem direito a solicitar rescisão sem multa (ANATEL Ato 7869/2022)."*
- **Separators:** the middle dot `·` joins inline facts (`WI-FI · 5 GHZ`, `5GHz · Excelente`, `Banda: 5GHz · RSSI −27 dBm`).
- **Emoji:** **none.** Meaning is carried by Material icons + semantic color, never emoji. A check glyph `✓` appears inside the "Conectado" badge — that is the extent of glyph decoration.
- **Numbers:** metric values are big and bold; units (`Mbps`, `ms`, `dBm`, `%`) are small and secondary, baseline-aligned to the value.

---

## VISUAL FOUNDATIONS — MD3, Fluxo de Telas To-Be (correção 2026-07-13)

> Fonte desta seção: "SignallQ App - Fluxo de Telas.dc.html" (Claude Design, mesmo projeto
> `e77ea465-291f-4bf5-930c-a267680da04e` do manual "Migração para Material Design 3 (estrito)"
> usado na migração de 2026-07-11 — mas documento **mais recente** e autoritativo, com paleta e
> specs de componente diferentes). Ver `docs/design-system/DECISAO_ALINHAMENTO_TOBE_2026-07-13.md`
> para o detalhe da contradição entre os dois documentos e por que este substitui o anterior.
> Onde o Fluxo de Telas não redefine um sistema que já existia (elevação tonal, state layers,
> motion, densidade de ícone), o valor da migração de 2026-07-11 foi mantido.

A **clean, bright, neutral Material Design 3** surface where **Primary** (violet `#5B21D6`) and a
**fixed blue Secondary** (`#2851B8`, no longer HCT-derived from primary) do the highlighting, and
**traffic-light semantics** (green / amber / red) carry connection quality. Nothing decorative
competes with the data.

- **Color vibe.** Mostly **white & light-grey**; `primary=#5B21D6` used sparingly for the active
  nav tab, primary CTAs, selection, and the speedtest button; `secondary=#2851B8` is a plain blue,
  not a tonal harmonic of primary — used for chip móvel, DNS privado, secondary links. Status
  colors are the workhorses for meaning. There is a full **dark theme** (`#131217` bg/surface,
  `primary=#D0BCFF`, `secondary=#AAC7FF` — próprios do Fluxo de Telas, não mais "tone80 derivado").
  A superfície permanentemente escura **SignallQ (IA)** (`#0D0D1A` / `#1A0B2E` / `#1E1130`) é
  **DESCONTINUADA** — mantida aqui só como registro histórico do As-Is, nenhuma rota/componente
  novo deve implementá-la.
- **Backgrounds.** Flat solids — **no photographic imagery, no full-bleed hero images, no
  repeating patterns/textures, no decorative gradients** on surfaces. Gradients exist in exactly
  two places: the **profile avatar** (linear primary→secondary) and the (descontinuada)
  Diagnóstico/AI header. The speedtest "Iniciar" button is a solid violet disc with a soft
  same-color glow ring.
- **Type.** Fonte única do app: **Google Sans Flex** (fallback `Google Sans` → `Roboto` →
  `system-ui`), pesos 400/500/600/700, em **todos** os estilos — não há mais split
  display/body por família. Implementado em PR #939 com licença SIL OFL, arquivos embutidos no
  APK. Escala de **12 estilos** (displaySmall até labelSmall — sem display-large/medium/
  headline-medium, ausentes de qualquer tela do Fluxo de Telas). Big numeral + small unit continua
  sendo o tratamento assinatura de métrica.
- **Spacing.** Strict **8dp grid**, 8 degraus: `xs 4 · sm 8 · md 12 · base 16 · lg 20 · xl 24 ·
  xxl 32 · xxxl 40`. 16px é o padding padrão de tela/card (`base`, era `lg` na escala antiga de 6
  degraus). Botões (`Button`) têm 40px de altura.
- **Forma por componente (specs literais do Fluxo de Telas).** `Card` 16px · `SheetFrame` 28px
  (cantos superiores) · `Button` 20px radius / 40px altura (token novo, fora da escala de 7
  degraus) · `Field` 12px · `Chip`/`Badge` 999px (pill) · `Dialog` 24px (token novo, ex.
  RestartDialog) · `IconButton` 40×40px circular.
- **Cards.** Rounded **16px** corners, fill color `surface-container`, separated by a hairline
  `1px` `outline-variant`. **Elevação tonal** (5 níveis — tint de superfície + shadow sutil, não
  sombra dura isolada) — sistema mantido da migração anterior, o Fluxo de Telas não o redefine.
  Tinted "status" cards use the semantic color at low alpha for fill and ~25–30% for border:
  connected Wi-Fi card = `success @12%` fill; banners (offline/warning) = `warning @12%` fill,
  warning text + icon.
- **Buttons / pills.**
  - Primary: solid `primary`, texto `onPrimary`, **40px altura / 20px radius**. Variantes: Filled
    / Tonal (`secondaryContainer`) / Outlined (`outline` 1px + texto primary) / Text / Danger
    (`error`/`onError`). Disabled = opacidade .38.
  - Segmented selector (Rápido/Completo/Triplo): borda 1px `outline`, radius 20px, padding 2px;
    ativa = `secondaryContainer`/`onSecondaryContainer`; inativa = transparente/`onSurfaceVariant`.
  - Chips (Todos / 2.4 / 5 / 6 GHz): fully-rounded pills (999px); ativo =
    `secondaryContainer`/`onSecondaryContainer`; inativo = `surfaceContainerHigh`/`onSurfaceVariant`.
- **Borders.** `1px` solid `outline` (`#79747E`) for dividers, list separators, outline buttons,
  idle chips; `outline-variant` (`#CAC4D0`, mais fraco) para divisores puramente decorativos.
- **Radii.** card **16px** · SheetFrame **28px** (cantos superiores) · button **20px** · input/field
  **12px** · dialog **24px** · pills/chips/badge **999px** · circular icon chips & avatars fully
  round (commonly 36–52px).
- **Elevation.** Tonal, 5 níveis (`level0`–`level4`), tint de superfície crescente + shadow sutil
  acompanhando — sistema mantido da migração de 2026-07-11 (Fluxo de Telas é spec de telas, não
  redefine tokens sistêmicos), com tints realinhados aos novos hex de surface-container.
- **State layers.** Todo componente interativo (Card clicável, itens de lista/sheet, tabs do
  BottomNav, ações de TopBar, chips/badges tocáveis) expõe overlay de opacidade fixa sobre
  `onSurface`/`onPrimary`: hover 8% · focus 10% · pressed 12% · dragged 16% — mantido da migração
  anterior, sem mudança de cor de fundo.
- **Transparency & blur.** Used as **color-at-alpha tints**, not as glassmorphism. No backdrop blur.
- **Iconography in circles.** Recurring motif: a Material icon centered in a **circular chip**
  filled with its semantic color at ~14% — 44px on signal cards, 40px on friendly cards/hub
  items, 36px compact, 80px in onboarding. Ícones: Material Symbols Outlined (variable font,
  eixos FILL/wght/GRAD/opsz), 24px padrão; FILL 1 só no ícone ativo da Bottom Nav.
- **Signal bars.** Custom 4-bar vertical glyph (width `3px`), cor pela qualidade (verde=Forte,
  âmbar=Regular, vermelho=Fraco), barras vazias usam `outline`.
- **Animation / motion.** Easing e duração tokenizados: **emphasized** e **standard**, ambos
  `cubic-bezier(.2,0,0,1)`; durações **100/200/300/400ms** — mantido da migração anterior, não
  redefinido pelo Fluxo de Telas. Live speedtest gauge fills as it measures; phase pills check off
  between phases. Scroll-aware bottom nav slides off-screen on scroll-down. **No bounces, no
  flourish.**
- **Hover / press.** This is a touch app: feedback é o M3 ripple + **state layer** e, para o
  gauge/CTA, scale/haptic. No web-style hover.
- **Layout rules.** TopBar 64px (voltar OU avatar 44px à esquerda, título central, ação
  contextual à direita) + **BottomNav** 5 abas (Início · Velocidade · Sinal · Histórico ·
  Ferramentas), aba ativa = pílula 64×32px radius 16px em `secondaryContainer`. Secondary screens
  overlay the tabs rather than being separate tabs; deep flows get a back arrow. `SheetFrame`
  (radius 28px cantos superiores, grabber centralizado) é o padrão para permissões, análise de
  topologia e pickers.

### Lacunas / itens não cobertos pelo Fluxo de Telas (sinalizados, não inventados)

- **`tertiary`** — o Fluxo de Telas não define esse role (só usa primary/secondary + semânticos +
  phases). Token mantido em `colors_and_type.css` por compatibilidade de alias, com o hex
  **não-confirmado** herdado do manual anterior (`#B03A5B`). Não usar em artefato novo sem validar.
- **Elevação tonal, state layers, motion, densidade de ícone** — sistemas definidos pelo manual
  MD3 de 2026-07-11, não redefinidos pelo Fluxo de Telas (documento de telas, não de tokens
  sistêmicos). Mantidos como estavam; revisar se o Fluxo de Telas vier a especificá-los no futuro.

---

## MONETIZATION — NATIVE AD COMPONENTS

`…/ui/component/ads/` (issue #555, v0.23.0) — official pattern for AdMob native ads inside the
app's organic surfaces, currently anchored on 4 screens: **Velocidade** (`NativeAdRow`, idle
state, below "Último resultado"), **Resultado** (`NativeAdCard`, dismissible), **Dispositivos**
(`NativeAdListRow`, inside the connected-devices list itself — never in the Infraestrutura
section), **Histórico** (`NativeAdCard`, dismissible).

- **Three variants, chosen by context, not preference:** full dismissible card / compact row /
  row inside an existing list. Never invent a fourth without design review.
- **Mandatory disclosure (`AdBadge`), never hidden:** "Patrocinado" (neutral tone, `Campaign`
  icon) for `NativeAdSource.ADMOB` — the only source actually in use; "Parceiro" (`accentBlue`,
  `Storefront` icon) for `NativeAdSource.PARTNER` — component already supports it for a future
  curated affiliate/partner catalog (`coreRecommendation`), not live yet.
- **Never confusable with organic content:** dashed border (`Modifier.dashedBorder`, never
  solid like organic cards), CTA in violet **outline** (never solid — solid violet is reserved
  for primary organic CTAs like "Iniciar teste"/"Conversar com IA"), no photo/hero, advertiser
  icon in a **square chip** (never circular, unlike organic avatars/icon chips).
- **No placeholder state.** The whole composable returns early and renders nothing when there's
  no loaded creative (`nativeAd == null`) — surrounding layout recomposes without a gap, never a
  loading box or empty card.
- Full component list and per-screen anchoring: `docs/design-system/COMPONENTS_ANDROID.md`
  ("Monetização — Anúncio Nativo").

---

## ICONOGRAPHY

- **System:** **Material Symbols / Material Icons** (Compose `androidx.compose.material.icons`), **Outlined** style predominantly (with a few Filled/Rounded for back-arrow, Wi-Fi, CellTower). Single-color, ~24dp, tinted by token (`accent`, `success`, `warning`, `error`, `textSecondary`/`textTertiary`). This is the *only* icon system in the app.
- **Representative icons in use:** `Home, Speed/Adjust, Wifi, History, Settings` (nav); `Router, DeviceHub, Hub, Public/Language, Smartphone, Laptop, CellTower, SettingsInputAntenna, SignalCellularAlt, WifiOff, Lock/LockOpen, Refresh, Share, ArrowBack, ArrowForwardIos, ExpandMore, Tv, Videocam, SportsEsports, GpsFixed, Shield, Info, Warning, LocationOn, GridView`.
- **In this design system:** we link **Material Symbols (Outlined)** from the Google Fonts CDN — it is the exact icon family the app uses, so no substitution is needed. Use `<span class="material-symbols-outlined">wifi</span>`.
- **Emoji:** never used as iconography.
- **Unicode glyphs:** the middle dot `·` as a separator and a check `✓` inside the connected badge are the only non-icon glyphs; everything else is a Material icon.
- **Logo / brand mark (OFICIAL):** os arquivos oficiais estão em `brand/` na raiz do repo e replicados aqui em `assets/signallq-*`. O símbolo é um conjunto de **4 barras de sinal** (alturas curta · média · **alta** · média — a 3ª é a mais alta) em degradê **violeta `#6C2BFF` → azul**, com cantos arredondados. O **wordmark** é "SignallQ" com "Signall" em quase-preto (`#0D0D1A`) ou branco no escuro, e o **"Q" em violeta**. Use `signallq-lockup-light-bg.png` em fundo claro, `signallq-lockup-dark-bg.png` em fundo escuro, e `signallq-symbol-*.png` para ícone/avatar/espaço quadrado. **Nunca redesenhar em CSS/SVG à mão nem usar a marca anterior "linka".** Regras completas em `brand/README.md`. As marcas animadas in-app **SignallQ** (`SignallQSymbol`) e **SignallQ Pulse** (`SignallQPulseSymbol`) mudam de cor por estado (accent / success / warning / error).

---

## Index — what's in this folder

| File / folder | Contents |
|---|---|
| `README.md` | This document — context, content & visual foundations, iconography, index |
| `colors_and_type.css` | All design tokens as CSS vars + semantic type classes. **Import this in every artifact.** |
| `SKILL.md` | Agent-Skill manifest (for use in Claude Code) |
| `assets/` | Logos **oficiais**: `signallq-symbol-1024.png`, `signallq-lockup-light-bg.png`, `signallq-lockup-dark-bg.png`. (Fonte da verdade: `brand/` na raiz do repo.) Os `ic_launcher*` antigos são a marca "linka" descontinuada — não usar. |
| `_ref/` | Reference screenshots from the real app (not for shipping) |
| `preview/` | Design-system cards shown in the Design System tab (colors, type, spacing, components) |
| `ui_kits/android/` | High-fidelity Jetpack-Compose-faithful recreation of the app — `index.html` (interactive prototype) + JSX components |

### UI kits

- **`ui_kits/android/`** — the SignallQ Android app. Interactive click-through across the 5 core surfaces: **Início (Home)**, **Velocidade (SpeedTest → running → Resultado)**, **Sinal (Wi-Fi)**, **Histórico**, plus **Diagnóstico/SignallQ AI**. Built from the real composables and mockup v2 spec.
