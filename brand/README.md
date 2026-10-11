# SignallQ — Marca oficial

Fonte da verdade dos logos do SignallQ. **Use somente estes arquivos** em qualquer
material (app, apresentações, site, admin, loja, ícones). Não redesenhar, não recriar
em CSS/SVG à mão, não usar a marca anterior "linka/veloo".

## Símbolo novo — "anéis + Q" (2026-08-01)

Substitui o símbolo anterior de 4 barras de sinal. Origem: protótipo `SignallQ Web -
Prototipo (4).zip` (pasta Downloads do Luiz), tela `ScreenBrand.dc.html` — já em uso na página
`/brand` real do `SignallQ Site` (`signallq-lockup-*-bg-v5.png` e `signallq-icon-*-dark.png`)
antes desta atualização propagar para cá. Motivo da troca: pedido direto do Luiz para alinhar a
marca oficial (`brand/`, usada por Android e Admin) ao que já estava live no Site.

Os arquivos `signallq-symbol-*.png` novos foram gerados a partir de
`signallq-icon-1024-app-store.png` (fundo branco sólido) via chroma-key (remoção do branco,
sem outro branco presente na arte) — não é um export vetorial original; se precisar de mais
fidelidade/resolução, gerar de novo a partir do projeto Claude Design de origem do protótipo.
`signallq-lockup-*-bg.png` novos vêm do protótipo em resolução menor (994×276) que os
anteriores (2334×784) — adequado para a maioria dos usos web/mobile, mas sem a mesma folga de
upscaling do arquivo anterior.

**Pacote premium regenerado em 2026-08-15:** `scripts/generate-signallq-brand-assets.py` é a
fonte determinística dos ícones derivados. O símbolo oficial não é redesenhado; o script aplica
somente escala, safe area, fundo, máscara e exportação. O launcher Android usa fundo quase-preto
`#0D0D1A`, alinhado à expressão premium aprovada. As variantes claras continuam disponíveis para
contextos que exijam fundo branco.

**Pendência aberta (fora do escopo desta rodada, sinalizada e não executada):**
- Favicon do `SignallQ Admin/public/` continua com o símbolo antigo — mesma regeneração
  pendente, não feita nesta rodada (só `brand/` e `SignallQ Site/` foram atualizados).

**Ícone do launcher Android — sincronizado em 2026-08-15:** os mipmaps em
`android/app/src/main/res/mipmap-*/ic_launcher*` foram regenerados a partir de
`signallq-symbol-1024.png` pelo script versionado, preservando o símbolo anéis + Q e adotando o
fundo premium quase-preto. Detalhe em "Ícone do app (Android)" abaixo. Falta um release novo para
qualquer alteração valer em produção.

## Arquivos

| Arquivo | Uso |
|---|---|
| `signallq-symbol-1024.png` | Símbolo (anéis + Q) isolado, fundo transparente, 1024px. Base para ícone de app e usos quadrados. |
| `signallq-symbol-512.png` | Mesmo símbolo, 512px (usos menores / web). |
| `signallq-lockup-light-bg.png` | Lockup horizontal (símbolo + wordmark) para **fundos claros** — "Signall" em quase-preto, "Q" em violeta. |
| `signallq-lockup-dark-bg.png` | Lockup horizontal para **fundos escuros** — "Signall" em branco, "Q" em violeta. |
| `signallq-feature-graphic-1024x500.png` | Feature graphic premium da Play Store (1024×500), com símbolo atual, mensagem e CTA. |
| `signallq-icon-512-play-store.png` | Ícone de app para listagem da Play Store (512×512), fundo branco sólido. |
| `signallq-icon-512-play-store-dark.png` | Mesmo ícone, fundo escuro `#131217` (novo nesta rodada). |
| `signallq-icon-1024-app-store.png` / `-dark.png` | Mesmo ícone em 1024×1024, fundo claro/escuro (novo nesta rodada — reserva para uma eventual listagem iOS/App Store; SignallQ não publica em iOS hoje). |

## Anatomia

- **Símbolo:** anéis concêntricos (2 arcos + ponto central, referência a sinal/radar) com um
  traço que se estende formando a "cauda" do "Q" (lupa), em degradê **azul → violeta** da
  esquerda para a direita. Fundo transparente na versão isolada; fundo branco sólido ou
  `#0D0D1A` nas versões "-dark" usadas como ícone de app.
- **Wordmark:** "SignallQ" — "Signall" em `#0D0D1A` (fundo claro) ou branco (fundo escuro),
  e o **"Q" em violeta `#6C2BFF`**.

## Regras

- Fundo claro → `signallq-lockup-light-bg.png`. Fundo escuro → `signallq-lockup-dark-bg.png`.
- Para espaço quadrado / ícone / avatar → `signallq-symbol-*.png`.
- Manter área de respiro ao redor do lockup ≥ altura do símbolo.
- Não distorcer, não recolorir, não trocar a fonte do wordmark, não adicionar sombra/contorno.
- Cor de acento da marca: violeta `#6C2BFF` (mesma do design system).

## Ícone do app (Android)

O ícone do app deriva do **símbolo**. Os recursos em
`android/app/src/main/res/mipmap-*/ic_launcher*` devem sempre corresponder a
`signallq-symbol-1024.png`. Ao atualizar a marca, regenerar os mipmaps a partir deste símbolo.

**Sincronizado em 2026-08-15.** Os 5 arquivos por densidade
(`ic_launcher_foreground`, `ic_launcher_background`, `ic_launcher_monochrome`, `ic_launcher`
legado, `ic_launcher_round` legado, em `mdpi`/`hdpi`/`xhdpi`/`xxhdpi`/`xxxhdpi`) foram regenerados
a partir de `signallq-symbol-1024.png`. Detalhe da geração:
- `ic_launcher_background`: quase-preto `#0D0D1A`, acabamento principal aprovado para o ícone.
- `ic_launcher_foreground`/`ic_launcher_monochrome`: símbolo recortado (trim de transparência) e
  redimensionado mantendo aspect ratio, ocupando 62,5% da altura do canvas 108dp — mesma
  proporção de safe-zone medida no ícone antigo (4 barras: 62,5% de altura, 71,3% de largura no
  canvas 432px/xxxhdpi), sem inventar margem nova. `ic_launcher_monochrome` é a silhueta branca
  (alpha do símbolo, sem cor) do mesmo recorte, para o ícone temático do Android 13+.
- `ic_launcher`/`ic_launcher_round` (legado pré-API26): mesmo recorte/proporção, achatado sobre
  fundo quase-preto (`ic_launcher_round` com máscara circular e cantos transparentes).
- `mipmap-anydpi-v26/ic_launcher.xml` não mudou — o adaptive icon já referenciava os três layers via
  `@mipmap/`.
- **Achado na verificação em emulador (2026-08-01):** a regeneração inicial dos PNGs não bastou —
  `mipmap-anydpi-v26/ic_launcher_foreground.xml` e `ic_launcher_monochrome.xml` existiam como
  **vetores com o símbolo antigo (4 barras) embutido como path data**, e no Android 8+ (API 26+)
  esse vetor em `anydpi-v26` tem prioridade sobre os PNGs por densidade — por isso o launcher
  continuava mostrando o ícone velho mesmo com o build verde. Os 3 arquivos (`ic_launcher_foreground.xml`,
  `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`) foram removidos; a resolução cai
  agora para os PNGs corretos. Confirmado visualmente em emulador (Pixel 10, API 26+, install
  limpo) — não presumir "build verde" como prova de ícone correto numa próxima atualização de
  marca; sempre instalar e olhar o launcher de verdade.

Ainda falta um **release novo** (bump de `versionCode`) pra esse ícone valer em produção — a Play
Store hoje distribui o build anterior, com o símbolo antigo.

## Favicons / ícones web (`favicon/`)

Gerados a partir de `signallq-symbol-1024.png` (símbolo é colorido com fundo transparente —
funciona em fundo claro ou escuro sem precisar de variante própria).

| Arquivo | Uso | Fundo |
|---|---|---|
| `favicon.ico` | Favicon multi-resolução (16/32/48px) | Transparente |
| `favicon-16.png` / `favicon-32.png` / `favicon-48.png` | Favicon PNG por tamanho | Transparente |
| `icon-192.png` / `icon-512.png` | PWA `manifest.json` (`purpose` padrão) | Transparente |
| `icon-192-maskable.png` / `icon-512-maskable.png` | PWA `manifest.json` (`purpose: maskable`) | Quase-preto `#0D0D1A`, símbolo a 62% (zona segura) |
| `apple-touch-icon.png` (180×180) | Atalho web em home screen | Quase-preto `#0D0D1A`, símbolo a 72% |

O pacote canônico em `brand/favicon/` foi regenerado em 2026-08-15. Cópias em repositórios Web,
Admin ou outros consumidores não são atualizadas automaticamente e devem ser sincronizadas em
tarefa própria.

**Nota:** a landing page pública do SignallQ vive neste repositório em `web/` (`signallq.com`).

> Marca anterior ("linka") é histórica e **não deve ser usada** em nenhum material novo.

## Símbolo institucional 7A (`by 7A`, GH#1376) — LEGADO, substituído pela Buildea

Distinto do símbolo SignallQ acima — usado só pela assinatura institucional "by 7A"
(`BrandEndorsement`), nunca para o ícone/logo do produto em si. Superado pelo rebrand
7A Labs → Buildea (2026-07-29) — ver seção seguinte. Mantido aqui só como referência
histórica; não usar em material novo.

| Arquivo | Uso |
|---|---|
| `7alabs-symbol-dark.svg` | Símbolo "7A" isolado, para fundo escuro. Recortado das paths originais fornecidas pelo Luiz, viewBox `267 164 763 653` (não quadrado, ~1.17:1) — sem traço novo inventado. |
| `7alabs-symbol-light.svg` | Mesmo símbolo, variante para fundo claro. |
| `7alabs-lockup-dark.svg` | Marca completa "7ALabs" (símbolo + wordmark "Labs"), fundo escuro. **Não usar em `BrandEndorsement`/telas operacionais** — a issue #1376 proíbe lockup completo nessas superfícies; guardado só para uso institucional fora do componente (ex.: material de apresentação). |
| `7alabs-lockup-light.svg` | Mesmo lockup, variante para fundo claro. |

## Símbolo institucional Buildea (`by Buildea`, rebrand 2026-07-29)

Sucede o símbolo "7A" acima na assinatura institucional (`BrandEndorsement`), mesmo uso e
mesma regra: nunca para o ícone/logo do produto em si (SignallQ continua com sua própria
marca, seção acima). Fonte: avatar real da organização `buildea-labs` no GitHub e o banner
de capa do repo `buildea-labs/.github` (`profile/assets/cover-buildea.png`) — não é um
traço vetorial novo, foi extraído dos ativos oficiais já publicados pelo Luiz.

| Arquivo | Uso |
|---|---|
| `buildea-symbol.png` | Monograma "iB" isolado (preto/branco/amarelo), 408×408, fundo preto opaco embutido — não precisa de variante clara/escura separada, o próprio símbolo carrega o fundo. Fonte: avatar da org GitHub `buildea-labs`. |
| `buildea-lockup-dark-bg.png` | Lockup completo (símbolo + wordmark "buildea" + tagline "Ideas. Built."), fundo escuro, 1280×640. **Não usar em `BrandEndorsement`/telas operacionais** — mesma regra do lockup "7ALabs" antigo; guardado só para uso institucional fora do componente. |

Sem variante de fundo claro ainda — se for necessária, gerar a partir da mesma fonte
oficial (não redesenhar à mão) e documentar aqui.

Consumido via cópia local, não import cross-monorepo: `SignallQ Admin/public/brand/buildea/symbol.png`
e `SignallQ Site/public/brand/buildea-symbol.png` (nomenclatura de cada cópia segue a
convenção de `public/brand/` já existente em cada app — ver `BrandEndorsement.tsx` de cada um).
