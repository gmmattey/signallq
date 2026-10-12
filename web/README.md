# SignallQ Web

Site público do SignallQ (`https://signallq.com`): divulgação do app Android, páginas informativas, teste de velocidade no navegador e páginas legais. Vive em `web/` do repositório `signallq` (migrado do `signallq-web`, hoje arquivado). Governança: [`AGENTS.md`](AGENTS.md).

## Stack

- Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4.
- Testes com Vitest e Testing Library; lint com ESLint (`--max-warnings=0`).
- Tema claro fixo; layout 1:1 com o protótipo de 2026-10-10 (ver [`docs/handoffs/2026-10-novas-paginas-site.md`](docs/handoffs/2026-10-novas-paginas-site.md)).

## Rotas

| Rota | Conteúdo |
|---|---|
| `/` | Home: hero com captura real do app, "Quatro perguntas", ordem do resultado, FAQ e CTA |
| `/teste-de-velocidade` | Teste de latência, download e upload no navegador (também em `speedtest.signallq.com`) |
| `/como-funciona` | Entender → diagnosticar → resolver → confirmar |
| `/duvidas` | FAQ (JSON-LD `FAQPage`) |
| `/guias`, `/guias/[slug]` | 4 guias estáticos (JSON-LD `Article`); dados em `src/app/guias/guides-data.ts` |
| `/privacidade`, `/termos` | Páginas legais (texto auditado; só muda com aprovação) |
| `/api/track` | Proxy server-side de telemetria para o Worker `signallq-admin` |

Metadados por rota: `src/lib/pageMetaCatalog.ts`. Ao criar uma rota, registre-a ali, em `public/sitemap.xml` e no allowlist de `src/middleware.ts`.

## Variáveis de ambiente

Veja [`.env.example`](.env.example). Só quatro são lidas pelo código:

| Variável | Onde | Para quê |
|---|---|---|
| `NEXT_PUBLIC_SIGNALLQ_PLAY_STORE_URL` | navegador | Destino do CTA de download (default: ficha na Play Store) |
| `NEXT_PUBLIC_SPEEDTEST_DOWNLOAD_URL` / `_UPLOAD_URL` | navegador | Motor do teste de velocidade (default: `speed.cloudflare.com`) |
| `SITE_INGEST_KEY` | **só servidor** | Autentica `/api/track` no Worker; o mesmo valor é secret do Worker |

## Comandos

Sempre de dentro de `web/`:

```bash
npm ci
npm run dev
npm run lint
npm run typecheck
npm test
npm run build
```

## Deploy

CI automático em `.github/workflows/web-ci.yml`. A publicação é **manual**: workflow `Web Deploy manual na Vercel` ou a Vercel CLI de dentro de `web/`. Passo a passo, secrets e domínios: [`docs/deploy-vercel.md`](docs/deploy-vercel.md).

## Documentação

- [`docs/architecture/speed-test-flow.md`](docs/architecture/speed-test-flow.md) — arquitetura do teste de velocidade.
- [`docs/handoffs/`](docs/handoffs/) — decisões e pendências por entrega.
- [`skills/`](skills/) — procedimentos de qualidade deste site (quality gates, acessibilidade/SEO, rotas, componentes).
