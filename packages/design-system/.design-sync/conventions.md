# SignallQ Design System — Conventions

> **Governança:** `docs_ai/DESIGN_SYSTEM.md` é a fonte de governança do design system Android
> consumer (estados semânticos, profundidade, acessibilidade, mapeamento Compose). Este arquivo
> descreve só as convenções de uso do pacote React (`@signallq/design-system`) — não duplica
> conteúdo, referencia.

> **Projeto canônico no Claude Design: `SignallQ Design System` (projectId `2d25d7a1-31b2-4ac3-881f-72dbc8f35a29`)** — o mesmo fixado em `.design-sync/config.json`. Criado em 2026-07-18 na **separação DS/protótipos** (ver `docs_ai/design-system/DECISAO_SEPARACAO_DS_PROTOTIPOS_2026-07-18.md`): este projeto contém **só o DS reutilizável** (tokens + primitivos + layout + animações, 14 componentes). O projeto antigo `e77ea465-291f-4bf5-930c-a267680da04e` foi renomeado para **"SignallQ — Protótipos"** e agora hospeda os fluxos (`tobe/`, `templates/`, `uploads/`), NÃO o DS — não sincronizar o pacote nele. Reuse sempre este `projectId`; o workspace tem vários "...Design System" (SignallQ PRO, Speedtest by SignallQ) — se a listagem mostrar mais de um **"SignallQ Design System"** exato, pare e confirme com a Claudete antes de sincronizar.

## Setup

Provider opcional. Sem `SignallQThemeProvider`, todo componente usa `LK` (tema claro) por
padrão — nada quebra em consumidores que não sabem que o tema escuro existe.

```tsx
import { SignallQThemeProvider } from '@signallq/design-system';

<SignallQThemeProvider mode="system">  {/* 'light' | 'dark' | 'system' (default) */}
  <App />
</SignallQThemeProvider>
```

Load Roboto and Material Symbols from Google Fonts (already in `styles.css`). For icons to display, the page must have the Material Symbols font loaded — it is included via the `styles.css` `@import` closure.

```html
<link rel="stylesheet" href="_ds/styles.css">
```

## Styling idiom

**All styling via the `LK` token object and `hexA()` helper — no CSS classes, no Tailwind, no styled-components.**

```tsx
import { LK, hexA } from '@signallq/design-system';

// Token usage
style={{ color: LK.textPrimary, background: LK.bgCard, borderRadius: LK.rCard }}

// Semantic colors
LK.accent      // #5B21D6 — primary CTA, selection, active nav (era #6C2BFF)
LK.success     // #146C2E — good connection, tests OK
LK.warning     // #8A5000 — moderate alerts
LK.error       // #BA1A1A — critical failures
LK.accentBlue  // #2851B8 — secondary FIXO (não deriva mais do accent), badges informativos

// Alpha tints (color at 10–30% opacity)
hexA(LK.success, 0.12)   // e.g. card fill
hexA(LK.success, 0.3)    // e.g. card border
```

**Token files:** `tokens/` in the bundle, `styles.css`, `_ds_bundle.css`. Read `styles.css` for the full token list.

### Dark mode

Todo componente do DS já resolve o tema sozinho via `useTokens()` internamente — nada a fazer
para consumi-los. Para compor um componente novo no mesmo idioma (claro/escuro reativo), use
`useTokens()` em vez de importar `LK` estático:

```tsx
import { useTokens } from '@signallq/design-system';

function MeuComponente() {
  const LK = useTokens(); // LK (claro) fora de um SignallQThemeProvider, LK_DARK dentro de um com mode="dark"
  return <div style={{ background: LK.surface, color: LK.onSurface }} />;
}
```

`LK`/`LK_DARK` estáticos continuam exportados para exemplos/protótipos que fixam um tema
deliberadamente (ex.: `ORB`, sempre escuro).

**Per-component docs:** each `components/<group>/<Name>/<Name>.prompt.md`.

## SignallQ AI surfaces

Use `ORB` tokens (not `LK`) for the always-dark AI chat surface:

```tsx
import { ORB, LK } from '@signallq/design-system';
// ORB.bg = #0D0D1A, ORB.surface = #1A0B2E, ORB.card = #1E1130, ORB.text = #F3F4F6
```

## Typography

No CSS classes — compose font shorthand inline:

```tsx
style={{ font: `600 18px/1.3 ${LK.font}` }}   // headline-small
style={{ font: `400 14px/1.5 ${LK.font}` }}   // body-medium
style={{ font: `600 11px/1.3 ${LK.font}`, letterSpacing: '.4px', textTransform: 'uppercase' }}  // overline
```

Use the `Overline` component for section labels. Typography sizes (12 estilos MD3, Fluxo de Telas
2026-07-13 — display-large/display-medium/headline-medium foram removidos, maior estilo real é
displaySmall/34): display-small 34, headline large/small 26/22, title 20/16/14, body 16/14/12,
label 14/12/11.

## Idiomatic example

```tsx
import { Card, Overline, Badge, Icon, LK, hexA } from '@signallq/design-system';

<Card style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
  <div style={{
    width: 44, height: 44, borderRadius: '50%',
    background: hexA(LK.success, 0.1),
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  }}>
    <Icon name="wifi" size={22} color={LK.success} />
  </div>
  <div style={{ flex: 1 }}>
    <div style={{ font: `600 15px/1.3 ${LK.font}`, color: LK.textPrimary }}>Luiz-5G</div>
    <div style={{ font: `400 11px/1.3 ${LK.font}`, color: LK.textSecondary }}>RSSI −27 dBm · Canal 36</div>
  </div>
  <Badge color={LK.success}>Forte</Badge>
</Card>
```

## Non-negotiables

> **Atualizado em 2026-07-18** — a paleta abaixo estava presa na era Linka (`#6C2BFF`, sem
> secondary fixo, 15 estilos de tipo, Roboto-only) mesmo após a migração MD3 de 2026-07-13.
> Corrigido para bater com `.claude/skills/SignallQ-design/colors_and_type.css` (fonte de
> verdade real) — ver `.claude/CLAUDE.md`, seção "Design System".

- Primary (accent): `#5B21D6`. Secondary: azul FIXO `#2851B8` — não deriva mais do primary.
- Status colors carry meaning: green `#146C2E` = good, amber `#8A5000` = moderate, red `#BA1A1A` = critical.
- Icons: Material Symbols Outlined only, 24dp default.
- Fonte única do app: Google Sans Flex (fallback Google Sans, Roboto) — não mais Roboto-only.
- Copy: Brazilian Portuguese, sentence case, UPPERCASE overlines, no emoji.
- Raw metric always with human verdict: "486 Mbps · Excelente".
- Separator: middle dot `·`.
- Grid: 8dp base, 8 degraus (4/8/12/16/20/24/32/40px).
- Radius por componente: Card 16 / SheetFrame 28 / Button 20 / Field 12 / Chip-Badge 999 / Dialog 24.
- Cards: flat, 16dp radius, hairline border, no drop shadows (elevação tonal, sem sombra dura).
- Superfície SignallQ (IA, tokens `ORB`) é DESCONTINUADA no To-Be — não implementar rota/componente novo.
