---
title: "Design System — SignallQ consumer"
description: "Cores, tipografia, espaçamento, profundidade, componentes e regras do app Android consumer, derivados do código"
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Design System — SignallQ (Android, consumer)

- **Fonte de verdade:** o *código* — `android/app/src/main/kotlin/io/signallq/app/ui/SignallQTheme.kt`
  (`LkColors`, `LkTokens`, `LkSpacing`, `LkRadius`, `LkStateLayer`, `LkElevation`, `LkMotion`, shapes
  e `signallQTypography`) e `ui/component/`. Este documento é derivado deles; em divergência, o código vence.
- **Escopo:** app Android SignallQ consumer (`io.signallq.app`). Não cobre o Admin (`buildea-admin`)
  nem o site/PWA (`signallq-web`); o SignallQ Pro foi descontinuado (ADR-016).
- **Direção futura (draft, não implementada):** `docs/design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md`.
  Os foundations 2.0 já existem em código como camada de compatibilidade (pares claro/escuro,
  preto-base, superfícies de card, escala 4–64 dp, shapes, state layers, elevação tonal e movimento);
  a migração das telas é **incremental** — este registro não declara nenhuma jornada integralmente migrada.
- **Outros artefatos de design:** `DESIGN.md` e `PRODUCT.md` (raiz do repositório, formato `impeccable`,
  North Star "The Calm Translator"), skill `.claude/skills/SignallQ-design/`, pacote React
  `packages/design-system/` (espelho para protótipos; não é o app). Não duplicar conteúdo deles aqui.
- **Documentos substituídos:** consolidou `COLORS`, `COMPONENTS_ANDROID`, `DESIGN_TOKENS`,
  `MD3_GUIDELINES`, `SPACING` e `TYPOGRAPHY` (removidos em 2026-08-06; recuperáveis em
  `git show 10b2f05d:docs_ai/_archive/2026-07-16_COLORS.md` e equivalentes).

---

## 1. Princípios

- **Material 3** via `MaterialTheme` (`androidx.compose.material3`); `MaterialTheme.shapes` é alimentado por `LkRadius`.
- **Cor de marca fixa** — `lightColorScheme`/`darkColorScheme` com acento fixo, **sem dynamic color**.
- **Flat, elevação tonal** — profundidade vem de tint de superfície (`surfaceContainer*`, `cardSurface*`); sombra é reforço discreto.
- **Profundidade comunica hierarquia e interação, nunca decoração.**
- **Métrica crua sempre com veredito humano** — nenhum número solto (Excelente/Bom/Regular/Fraco/Forte).
- **Copy em PT-BR com "você"** — sentence case em títulos, UPPERCASE só em overlines, sem emoji, separador inline `·`.
- **Sem superfície de chat/IA conversacional** — descontinuada por decisão de produto; não criar rota nem componente novo para ela.

---

## 2. Cores

Valores em `LkColors` (objetos `Light` e `Dark`); `LocalLkTokens.current` e `MaterialTheme.colorScheme` são os pontos de consumo. Tabela completa: ler o código.

| Papel | Claro | Escuro | Uso |
|---|---|---|---|
| `primary` | `#5B21D6` | `#D0BCFF` | CTA primário, seleção, navegação ativa, marca |
| `secondary` | `#2851B8` (azul fixo, não deriva do primary) | `#AAC7FF` | Informação categorizada (rede móvel, DNS), links secundários |
| `success` | `#146C2E` | `#83DA99` | Conexão boa, teste OK |
| `warning` | `#8A5000` | `#FFB870` | Alerta moderado, veredito Regular |
| `error` | `#BA1A1A` | `#FFB4AB` | Falha crítica, veredito Fraco/Crítico |
| `surface` | `#FFFFFF` | `#000000` | Nível 0 — fundo da tela |
| `cardSurface` | `#F7F7F8` | `#161616` | Nível 1 — card necessário |
| `cardSurfaceElevated` | `#EEEEF0` | `#222222` | Card elevado / conteúdo interno |
| `surfaceContainer` | `#F1F1F2` | `#1E1E1E` | Conteúdo agrupado |
| `surfaceContainerHigh` | `#E8E8EA` | `#2A2A2A` | Interativo/destacado |
| `surfaceContainerHighest` | `#DEDEE1` | `#333333` | Sobreposto (sheets, dialogs) |
| `onSurface` / `onSurfaceVariant` | `#1C1C1F` / `#48484D` | `#F5F2F7` / `#CAC4D0` | Texto |
| `outline` / `outlineVariant` | `#73737A` / `#C8C8CD` | `#948F99` / `#49454F` | Contornos funcionais, divisores |
| `scrim` | `#80000000` | `#99000000` | Fundo de dialog/sheet modal |
| `phaseLatencia` / `phaseDownload` / `phaseUpload` | `#2563EB` / `#146C2E` / `#8A5000` | `#AAC7FF` / `#83DA99` / `#FFB870` | Fases do SpeedTest |

Cada papel tem o par `on*` e, para status, `*Container`/`on*Container`.

**Regras:**

- `primary` é ação/marca/seleção; `secondary` é informação categorizada. Não usar `secondary` como CTA nem `primary` como badge informativo.
- **Verde só sucesso, âmbar só atenção, vermelho só erro/falha.** Sem significado de status, usar `onSurfaceVariant`/`outline`.
- `phaseDownload`/`phaseUpload` reaproveitam hex de `success`/`warning` de propósito: numa fase do SpeedTest a cor identifica a *fase*, não um veredito.
- `Color(0x...)` fora do tema só para cor de marca de terceiro (logo de operadora), gráfico técnico com paleta própria justificada ou impossibilidade prática de usar token.
- Tokens escuros da antiga superfície de IA (`signallQBlack`, `signallQDarkSurface`, `signallQDarkCard`) permanecem em `LkColors` por legado — não usar em telas novas.

---

## 3. Tipografia

Família única: **Google Sans Flex** (`android/app/src/main/res/font/google_sans_flex_{regular,medium,semibold,bold}.ttf`, SIL OFL 1.1), embutida — sem dependência de rede. Nenhuma tela nova introduz segunda família.

| Token | Tamanho / linha | Peso | Tracking | Uso |
|---|---|---|---|---|
| `displaySmall` (e `displayLarge`/`displayMedium`, mapeados ao mesmo estilo) | 34 / 40 sp | Bold | 0 | Métrica hero |
| `headlineLarge` (e `headlineMedium`) | 26 / 32 sp | Bold | 0 | Título de tela |
| `headlineSmall` | 22 / 28 sp | SemiBold | 0 | Título de seção grande |
| `titleLarge` | 20 / 26 sp | SemiBold | 0 | Título de card/sheet |
| `titleMedium` | 16 / 22 sp | Medium | 0.1 | Título de linha/item |
| `titleSmall` | 14 / 20 sp | Medium | 0.1 | Subtítulo, rótulo de campo |
| `bodyLarge` | 16 / 24 sp | Normal | 0.15 | Corpo principal |
| `bodyMedium` | 14 / 20 sp | Normal | 0.2 | Corpo secundário |
| `bodySmall` | 12 / 16 sp | Normal | 0.25 | Legenda, apoio |
| `labelLarge` | 14 / 20 sp | Medium | 0.1 | Texto de botão |
| `labelMedium` | 12 / 16 sp | Medium | 0.3 | Badge, chip |
| `labelSmall` | 11 / 16 sp | Medium | 0.4 | Overline (+ UPPERCASE) |

**Regras:** usar `MaterialTheme.typography.*`; evitar `fontSize`/`letterSpacing` hardcoded fora de canvas, labels de gráfico e renderização custom. Ainda há telas com tipografia hardcoded — padronizar ao tocar na área, não replicar.

---

## 4. Espaçamento

`LkSpacing` (grid de 8 dp com degraus internos):

| Token | dp | Uso |
|---|---|---|
| `xs` | 4 | ajuste fino, ícone-texto compacto |
| `sm` | 8 | gaps simples, ícone-texto padrão |
| `md` | 12 | espaçamento interno padrão, gap entre cards da mesma seção |
| `base` | 16 | margem de tela, padding interno de card (`cardContent`) |
| `lg` | 20 | separação de blocos densos |
| `xl` | 24 | separação clara entre seções |
| `xxl` | 32 | grandes respiros |
| `xxxl` | 40 | aberturas verticais, CTA de onboarding |
| `compositionLarge` / `compositionExtraLarge` | 48 / 64 | separação entre grandes blocos |

**Regras:** margem de tela `base`; gap entre seções `xl`–`xxl`; gap entre cards da mesma seção `md`; padding interno de card `base` (nunca menor que `md`); pelo menos `xl` de respiro acima da barra inferior. Respeitar `WindowInsets` em todo componente. Preferir `LkSpacing` a `.dp` literal — há centenas de literais soltos (dívida de padronização, higiene §4.11): ao tocar um arquivo, trocar o literal local por token, sem expandir a tarefa.

---

## 5. Raios e bordas

`LkRadius`: `card` 16 dp · `button` 20 dp · `input` 12 dp · `sheet` 28 dp (cantos superiores) · `dialog` 24 dp · `pill` 999 dp (chip/badge). `MaterialTheme.shapes`: small = input, medium = card, large = dialog, extraLarge = sheet.

**Decisão do Luiz (2026-07-19) — borda nunca separa container do fundo.** A separação de card/container é **sempre** por diferença tonal de superfície (seção 6). Borda só quando é parte funcional da forma do componente: campo outlined, botão outlined, checkbox, switch desligado, segmented button, indicador de seleção; e hairline funcional entre duas regiões adjacentes do mesmo tom (ex.: faixa de tabs e conteúdo).

| Situação | Solução |
|---|---|
| Agrupar conteúdo relacionado sem ação | `surfaceContainer`, sem borda nem sombra |
| Separar container do fundo | diferença tonal (nível 1+), nunca borda |
| Elemento interativo/selecionável | `surfaceContainerHigh` + leve elevação; nunca borda + sombra juntas sem justificativa |
| Linha única numa lista | sem card — lista com divisor (`LkSheetDivider`) ou espaçamento |
| Dado que é a própria tela (resultado de SpeedTest) | sem card — o fundo já é a superfície |

---

## 6. Profundidade (4 níveis)

| Nível | Papel | Token | Exemplo |
|---|---|---|---|
| 0 | Fundo da tela | `surface` | fundo de `Inicio2Screen`, `SinalScreen` |
| 1 | Conteúdo agrupado | `cardSurface` / `surfaceContainer` — sem sombra, sem borda | card de resumo, lista de dispositivos |
| 2 | Interativo/destacado/selecionado | `cardSurfaceElevated` ou `surfaceContainerHigh` | recomendação prioritária, rede Wi-Fi conectada |
| 3 | Sobreposto | `surfaceContainerHighest` + scrim | `LkSheetFrame`, `ConfirmacaoDialog`, `LgpdConsentDialog` |

**Seleção = diferença de superfície (`surfaceContainerHigh`) + cor de destaque (`primary`), nunca só sombra.** Não há token nomeado para "superfície selecionada": hoje é resolvido por componente.

**Regras:** card não parece elevado se não for interativo ou prioritário; no escuro priorizar elevação tonal sobre sombra; sem glow permanente nem glassmorphism; gradiente só em ação principal, estados especiais, marca (avatar/logo), promocional secundário ou dado quando necessário; nunca borda + sombra + glow + gradiente no mesmo elemento; componentes equivalentes têm o mesmo nível em qualquer tela; cards aninhados no máximo 2 níveis visuais; sheets e modais sempre nível 3.

**Elevação em código:** `LkElevation` (`level0` 0 · `level1` 1 · `level2` 3 · `level3` 6 dp). **State layers** (`LkStateLayer`): hover 0.08 · focus 0.10 · pressed 0.12 · dragged 0.16 · disabled 0.38. **Movimento** (`LkMotion`): microinteração 200 ms, transição de container 300 ms, easing `cubic-bezier(.2, 0, 0, 1)`; com movimento reduzido ativo usar `durationMillis(..., reducedMotion = true)` (0 ms).

**Débito conhecido:** `AppShellBottomBar.kt` ainda desenha `HorizontalDivider(color = outlineVariant)` acima da barra inferior — é o padrão de borda-como-separação que a decisão acima elimina; a barra já usa `surfaceContainer`. Corrigir ao tocar o arquivo (higiene §4.11/§8).

---

## 7. Componentes

Localização: `android/app/src/main/kotlin/io/signallq/app/ui/component/`. Dois conjuntos convivem:

- **Legado `Lk*`** (`BaseComponents.kt` e correlatos) — contratos preservados, ainda a base da maioria das telas: `LkSurfaceCard`, `LkSectionOverline`, `LkPillBadge`, `LkStatusDot`, `LkSheetSectionTitle`, `LkInlineBulletText`, `LkInfoCallout`, `LkNumberedStep`, `LkSheetInfoRow`, `LkSheetDivider`, `LkSheetFrame`, `LkSymbol`, mais `ConfirmacaoDialog` e `LgpdConsentDialog`.
- **Biblioteca 2.0 opt-in** — `SignallQControls.kt`, `SignallQContainers.kt`, `SignallQFeedbackTone.kt`, `SignallQScreenState.kt` (catálogo de previews em `SignallQComponentPreviews.kt`):
  - controles: `SignallQButton`, `SignallQTextField`, `SignallQChoiceChip`, `SignallQBadge`;
  - estrutura: `SignallQListRow`, `SignallQSurfaceCard` (`BaseComponents.kt`), `SignallQTopAppBar`, `SignallQNavigationBar`, `SignallQSheet`, `SignallQDialog`, `SignallQExpandableDetails`;
  - feedback: `SignallQBanner`, `SignallQProgress`, `SignallQResultBlock`, `SignallQTranslatedMetric`, `SignallQSkeleton`, `SignallQOfflineBanner`;
  - tela: `SignallQStatefulScreen` (loading, conteúdo, vazio, offline, permissão necessária, erro recuperável).

  Alvo interativo mínimo de 48 dp, sem depender só de cor, texto multilinha. **Adoção é incremental:** `SignallQSurfaceCard` tem um único consumidor de produção (`OperadoraContactCard`) e os demais seguem em `LkSurfaceCard` — não inverter o default nem varrer oportunisticamente. `SignallQOfflineBanner` está em `DispositivosScreen` e `SinalScreen`; `SignallQStatefulScreen` em `DiagnosticoGuiadoProcessandoSection`. `SignallQComponentsContractTest` trava a lista de consumidores.
- **Estado de tela:** `SignallQScreenState<T>` é o modelo para estado de tela (cobre `Offline` e `PermissionRequired`); `PingScreen` já migrou. O legado `UiState<T>` (`ui/state/UiState.kt`) ainda é usado por `MainViewModel`/`AppShell` (IP local/público e ISP) — migrar é trabalho futuro. Telas de fluxo local ou wizard (Equipamento de Internet, Modo gamer, Histórico, Ajustes, Monitoramento) mantêm tratamento de estado próprio por decisão registrada: o modelo cobre disponibilidade de conteúdo, não passos de navegação nem feedback transitório de ação.

### Padrões de estrutura

- **TopBar:** (1) `CenterAlignedTopAppBar` com ação contextual — padrão das telas de nível superior; (2) TopBar com seta de voltar — telas secundárias. Não criar terceiro padrão sem conteúdo que os dois não comportam.
- **Barra inferior:** `NavigationBar` de 4 raízes (Início · Velocidade · Histórico · Ferramentas); Ajustes é overlay (ver `technical/SCREEN_MAP.md`). Ícone com `FILL 1` só no item ativo.
- **Botões:** primário sólido (`primary`), secundário outline, texto/link, `IconButton` 40×40, destrutivo (`error`), desabilitado (opacidade reduzida, sem cor semântica), anúncio nativo (outline, nunca sólido).
- **Cards (variantes):** resumo/métrica, recomendação, rede Wi-Fi (conectada/disponível), dispositivo, status de conexão, informativo (`LkInfoCallout`), anúncio nativo (`NativeAdCard`), oferta simulada (`SimulatedOfferCard`). Cada variante tem seu próprio composable — reaproveitar em vez de reimplementar.
- **Chips/badges:** raio `pill`; chip de filtro (banda Wi-Fi), chip de status (`LkPillBadge`), disclosure de anúncio (`AdBadge`), operadora (`OperadoraBadge`, fundo branco fixo + `outlineVariant` para o logo). Não misturar quadrado e pill no mesmo grupo.
- **Métricas:** padrão **valor + unidade + label + estado + veredito**. Ausência de dado nunca como valor: `—` quando não coletado; skeleton ao carregar; erro explícito (ícone + texto) quando falhou; rótulo "Simulado"/"Estimado" quando não vem de medição real.
- **Avatar de perfil:** `UserAvatar`/`AvatarNucleo` existem; `ProfileAvatarButton` não tem consumidor em telas hoje — confirmar antes de reutilizar.
- **Não recriar:** padrão visual do chat "SignallQ Pulse" (`ContextualQuestionCard`/`PulseResultCard`, removidos em GH#1682) nem `OfflineBanner`/`StatefulScreen` pré-2.0 (removidos em #1673).

---

## 8. Estados e variantes

| Estado | Cor | Ícone | Texto |
|---|---|---|---|
| Excelente / Bom | `success` | `check_circle` | "Excelente" / "Bom" |
| Regular | `warning` | `warning` | "Regular" |
| Ruim / Fraco | `error` | `error` | "Fraco" |
| Crítico | `error` (ênfase, ex. container) | `error` | "Crítico" |
| Indisponível | `onSurfaceVariant`/`outline` | `block`/`wifi_off` | "Indisponível" |
| Desconhecido | `onSurfaceVariant`/`outline` | `help` | "Não foi possível medir" |

Cor + ícone + palavra **sempre juntos**. Estados de interação seguem `LkStateLayer` (seção 6); `loading` usa skeleton/shimmer no lugar do conteúdo final, nunca card vazio.

---

## 9. Gráficos

- Legenda visível quando há mais de uma série; nunca só cor para diferenciar série.
- Eixos com unidade explícita (Mbps, ms, dBm, canal); grid discreto em `outlineVariant`.
- Cores seguem `phase*` para fases de SpeedTest; fora disso, `primary`/`secondary`/status conforme a semântica real.
- Tooltip em nível 3. Estado vazio nunca é gráfico em branco — "Sem dados suficientes ainda" + estado "Desconhecido".
- **Gráfico de canais Wi-Fi (`WifiChannelGuide`):** só canais reais da faixa (2.4 GHz: 1–13 no Brasil; 5 GHz: UNII conforme regulação; 6 GHz: Wi-Fi 6E); sem sobreposição de labels (agrupar/truncar); rede conectada em destaque (nível 2); redes ocultas agrupadas.

---

## 10. Conteúdo patrocinado (anúncio nativo / ofertas)

Componentes em `ui/component/ads/` (`AdBadge`, `NativeAdCard`, `NativeAdRow`, `NativeAdListRow`, `SimulatedOfferCard`, `DashedBorder`, `NativeAdCtaButton`, `NativeAdIconChip`) e carregamento em `ui/ads/`.

- **Disclosure sempre visível** — "Patrocinado" (neutro, AdMob) ou "Parceiro" (tom `secondary`, afiliado/curado); nunca omitir nem disfarçar de componente orgânico.
- **Hierarquia secundária** — nunca compete com o resultado orgânico; borda tracejada (`DashedBorder`), CTA outline (sólido é exclusivo do CTA primário orgânico), sem foto/hero, ícone do anunciante em chip quadrado.
- **Sem promessa técnica não comprovada** ("dobra sua velocidade").
- **Sem criativo carregado, o componente é omitido** — não vira placeholder.
- Três variantes por contexto, não por preferência: `NativeAdCard` (cheio, dispensável), `NativeAdRow` (linha compacta), `NativeAdListRow` (linha dentro de lista existente).

---

## 11. Acessibilidade

- Alvo de toque mínimo **48 dp**.
- Contraste de texto conforme MD3 (AA mínimo; preferir AAA em texto crítico de diagnóstico).
- TalkBack: todo ícone/estado semântico com `contentDescription`/`semantics` equivalente ao texto visível.
- Movimento reduzido: respeitar a preferência do sistema; animação de diagnóstico tem alternativa estática (`ReducedMotion.kt`).
- Fonte do sistema ampliada: layout não quebra até pelo menos 130%.
- Nunca depender só de cor para status.

## 12. Responsividade

Telefones Android (compact/medium); sem suporte formal a tablet/foldable. Medidas em `dp`. Respeitar `WindowInsets` (gesture nav, button nav, cutout) — **nenhum componente cortado pelas barras do sistema** é critério de aceite de toda tela nova. Campos de entrada visíveis acima do teclado (`imePadding`). Portrait-first; landscape não deve quebrar.

## 13. Governança

- Checar equivalente existente na biblioteca antes de criar componente (skill `verificar-modulo`).
- Sem valor visual hardcoded em tela — sempre token (`LkColors`/`LkSpacing`/`LkRadius`/`signallQTypography`).
- Deprecar antes de remover.
- Divergência entre protótipo/documento e código: registrar explicitamente e decidir qual lado corrige — nunca presumir que o código deve seguir o protótipo.
- Validação de tela: skills `design-check` (arquivo/tela) e `auditar-ux` (auditoria multi-tela).

## 14. Ícones

Material Symbols **Outlined** (variable font `material_symbols_outlined.ttf`, Apache 2.0; eixos FILL/wght/GRAD/opsz, via ligadura OpenType), encapsulado em `LkSymbol` (`filled = true` para estado selecionado). Conceitos de rede em uso: `wifi`, `cell_tower`, `router`, `speed`, `dns`, `devices`, `history`, `warning`/`error`/`check_circle`. Barras de sinal móvel são desenho próprio (`SignalBars`/`SpeedBarsChart`), não o ícone puro. Ícones novos: catálogo https://fonts.google.com/icons?icon.set=Material+Symbols, com peso e densidade consistentes na tela.

## 15. Copy

PT-BR com "você" (nunca "tu" nem tratamento formal); sentence case em títulos; UPPERCASE só em overlines; sem emoji; separador inline `·`; métrica crua sempre com veredito humano (seção 8).
