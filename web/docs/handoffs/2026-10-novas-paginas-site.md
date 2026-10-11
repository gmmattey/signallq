# Handoff — novas páginas do site (protótipos de 2026-10-10)

Fonte: `SignallQ - Prototipos.zip` (Claude Design, local do Luiz, não versionado). Os `.dc.html` foram usados como referência de conteúdo, copy e fluxo; o código reproduz as medidas do protótipo com Tailwind (cores e tipografia do design system do repo).

## Decisões do Luiz nesta rodada

- Reintroduzir o teste de velocidade (reverte o corte de 2026-08-27).
- `/privacidade` e `/termos`: manter o texto atual auditado; sem importar o texto do protótipo.
- `speedtest.signallq.com` deve servir o teste de velocidade.
- Tela exemplo do hero: captura real enviada por ele.

## Protótipo → código

| Protótipo | Código |
|---|---|
| `SignallQ Home.dc.html` | `AppLandingClient.tsx` + `HowItWorksSteps`, `ResultOrder`, `TrustPromises`, `LandingFaq`; galeria existente mantida |
| `SiteHeader.dc.html` | `SiteNav.tsx` (links + `aria-current` via `usePathname`) |
| `SiteFooter.dc.html` | `SiteFooter.tsx` — já equivalente, sem mudança |
| `SignallQ Como Funciona.dc.html` | `src/app/como-funciona/` |
| `SignallQ Duvidas.dc.html` | `src/app/duvidas/` (JSON-LD `FAQPage`) |
| `SignallQ Guias.dc.html` + `GuideBody.dc.html` + 4 guias | `src/app/guias/` (`guides-data.ts`, `[slug]`, JSON-LD `Article`) |
| `SignallQ Teste.dc.html` | `src/app/teste-de-velocidade/` — ver `docs/architecture/speed-test-flow.md` |
| `SignallQ Privacidade/Termos.dc.html` | não importados (decisão acima) |
| `SignallQ Android.dc.html`, `Speed Flow.dc.html` | fora do escopo (referência do app / catálogo interno) |
| `seo/google-play-listing.md` | fora do escopo (ficha da Play Store) |

## Estado em 2026-10-10 (fim da entrega)

- Páginas **1:1 com o protótipo**, validadas lado a lado (mesmo navegador e largura) na Home, Como funciona, Dúvidas, guia, Teste, Privacidade e Termos. Tema claro fixo; medidas copiadas do protótipo (largura de content-box, `line-height: normal`, fonte sem ajuste óptico automático).
- Home sem "Diferenciais" e sem galeria (decisão do Luiz); hero com a captura real do app (nome do Wi-Fi e da operadora borrados, status bar cortada).
- Privacidade/Termos: **layout** do protótipo com o **texto atual auditado** (decisão do Luiz). Os Termos atuais definem "Buildea" como titular em todo o site e aplicativo.
- Em produção em `signallq.com` e `speedtest.signallq.com` (Vercel `signallq-web`); DNS do subdomínio na Hostinger.
- Telemetria funcionando de ponta a ponta (`SITE_INGEST_KEY` criada nos dois lados; `/api/track` → `signallq-admin.gmmattey.workers.dev`). Informe diário no Discord ativo (cron 09:00 BRT).

## Divergências deliberadas

- OG image/favicon do protótipo (`og-image.png`, `favicon.png`) não existem em `public/`; mantido o default real (`signallq-symbol.png`).
- Ilustrações PNG do zip não importadas; o símbolo da marca vem de `public/assets/signallq-symbol-512.png` (do zip).
- Sitemap segue a convenção local (`changefreq`/`priority`), não `lastmod`.
- Nenhum item de menu é destacado em `/teste-de-velocidade` (como no protótipo, cujo `SiteHeader` não recebe `active` nessa página).
- `TelemetryInit` passou a chamar `initTelemetryDeferred()`, que existia mas nunca era chamado.
- Removidos por órfãos: `Banda`, `institutional/*` e os docs do produto antigo (diagnóstico, PWA, planos, histórico); recuperáveis pelo git.

## Pendências

| Dono | Item |
|---|---|
| Luiz | Cadastrar `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` e `VERCEL_TOKEN` como secrets do repositório `signallq` (hoje o deploy sai pela CLI) e rodar um `preview` pelo workflow |
| Luiz | Confirmar se o texto de Privacidade/Termos do protótipo substitui o atual auditado |
| Luiz | Variáveis de produção na Vercel sem uso no código: `NEXT_PUBLIC_ADSENSE_PUBLISHER_ID`, `SIGNALLQ_PLANS_API_URL` (podem ser removidas) |
| Caio | Revisão independente das rotas novas e da chamada do navegador a `speed.cloudflare.com` |
| Renan | Indexação das rotas novas; `middleware.ts` usa convenção depreciada no Next 16 (`proxy`) |
| Luiz | Search Console: reenviar o sitemap e pedir indexação das páginas novas |
