# SignallQ Web

## Identidade e estado atual

- **Organização/repositório:** vive em `web/` do repositório `signallq` (migrado em 2026-10 do `signallq-web`, hoje arquivado e somente leitura). Rode os comandos abaixo de dentro de `web/`; o CI e o deploy estão em `.github/workflows/web-*.yml` na raiz do repositório.
- **Finalidade:** site público do SignallQ Android — divulgação do app, páginas informativas (Como funciona, Dúvidas, Guias), teste de velocidade no navegador e as páginas legais exigidas pelo Google Play, sempre direcionando para a Play Store.
- **Classificação:** produto.
- **Estado atual:** aplicação Next.js (App Router), sem PWA, **tema claro fixo**, 1:1 com os protótipos de 2026-10. Rotas públicas: `/`, `/como-funciona`, `/duvidas`, `/guias`, `/guias/[slug]` (4 guias), `/teste-de-velocidade`, `/funcionalidades`, `/modo-gamer`, `/privacidade`, `/termos`, e as 9 páginas de ferramentas/recursos (`/teste-de-ping`, `/teste-de-download-e-upload`, `/teste-de-dns`, `/teste-de-jitter`, `/canais-wifi`, `/relatorio-pdf`, `/sinal-wifi`, `/quem-esta-na-minha-rede`, `/mapa-de-calor-wifi`, `/sinal-4g-5g`); Route Handler de telemetria `/api/track`; `speedtest.signallq.com` reescreve para `/teste-de-velocidade`. Testes com Vitest.
- **Mudança de escopo (2026-08-27):** o site deixou de ser o produto de medição/PWA e passou a ser a landing do app Android.
- **Reexpansão de escopo (2026-10-10), decisão do Luiz:** o teste de velocidade voltou (`/teste-de-velocidade`, medição client-side, sem persistência) junto com Como funciona, Dúvidas, Guias, Modo Gamer e catálogo de Funcionalidades/Ferramentas conectadas ao app. Histórico local, PWA, comparador de planos e AdSense continuam fora. O texto de `/privacidade` e `/termos` é o auditado contra o código Android e só muda com aprovação do Luiz.
- **Publicação (2026-08-28):** PR #160 mergeada em `main` e publicada em produção via workflow **Deploy manual na Vercel** (`target=production`, ainda no repo `signallq-web`). `https://signallq.com` já serve a landing reduzida.
- **Publicação (2026-10-10):** o site novo foi publicado em produção pela Vercel CLI a partir de `web/`; domínio `speedtest.signallq.com` adicionado ao projeto (DNS na Hostinger).

## Escopo e exclusões

- **Pertence ao repositório:** as rotas públicas listadas acima, o catálogo de funcionalidades/ferramentas informativas direcionando para o app, o CTA de download para a Play Store, o teste de velocidade client-side, o proxy de telemetria (`/api/track`) e o rewrite do subdomínio `speedtest`.
- **Não pertence:** aplicativo Android e Workers do repositório `signallq`, portal administrativo `buildea-admin`, políticas corporativas completas, projetos pessoais, e (desde a mudança de escopo) qualquer histórico local, PWA, comparador de planos ou a antiga jornada de diagnóstico — essas funcionalidades foram descontinuadas, não movidas para outro lugar. A medição de velocidade permitida é só a de `/teste-de-velocidade`.

## Arquitetura comprovada

- **Componentes principais:** Next.js 16, React 19, TypeScript, Tailwind CSS, `src/app/`, `src/components/`, `src/lib/`, `src/styles/` e `public/`.
- **Rotas:** uma pasta por rota em `src/app/` (`page.tsx` fino + `*Content.tsx`); metadados em `src/lib/pageMetaCatalog.ts` (`PAGE_META`, fonte única, validada contra `public/sitemap.xml` por `sitemap.test.ts`); `src/middleware.ts` mantém o allowlist de rotas públicas e faz o rewrite de `speedtest.signallq.com`.
- **Integrações:** Play Store (`NEXT_PUBLIC_SIGNALLQ_PLAY_STORE_URL`); motor do teste de velocidade chamado direto do navegador (`NEXT_PUBLIC_SPEEDTEST_DOWNLOAD_URL`/`UPLOAD_URL`, default `speed.cloudflare.com`); proxy server-side `/api/track` → Worker `signallq-admin` em `signallq-admin.gmmattey.workers.dev`, autenticado por `SITE_INGEST_KEY` (secret do servidor, igual à do Worker). Visitas e cliques alimentam o informe diário no Discord (ver `docs_ai/technical/admin-api-schema.md`).
- **Dependências:** declaradas em `package.json`.

## Comandos essenciais comprovados

- **Instalação** (sempre de dentro de `web/`): `npm ci`.
- **Execução:** `npm run dev`.
- **Lint:** `npm run lint`.
- **Typecheck:** `npm run typecheck`.
- **Testes:** `npm test`.
- **Build:** `npm run build`.
- **Validações específicas:** a CI executa instalação, lint, typecheck, testes e build. Alterações de acessibilidade, SEO técnico ou interface exigem as validações locais aplicáveis em `skills/`.

## Restrições

- **Acessibilidade e performance:** preservar semântica, navegação por teclado, responsividade e desempenho. **Fidelidade ao protótipo:** medidas, cores e textos seguem `docs/handoffs/2026-10-novas-paginas-site.md`; não reintroduza tema escuro nem componentes do sistema de design antigo sem decisão do Luiz.
- **Privacidade:** segredos, incluindo `SITE_INGEST_KEY`, devem permanecer somente no servidor.
- **SEO técnico:** Renan responde por rotas, metadados, redirecionamentos, indexação e dados estruturados. SEO editorial e aquisição pertencem a Marcos.
- **Custos:** mudanças em telemetria, hospedagem ou serviços externos exigem aprovação do Luiz quando criarem custo ou compromisso externo.
- **Publicação:** o deploy continua manual (`Web Deploy manual na Vercel`, projeto Vercel `signallq-web`, secrets `VERCEL_*` deste repositório); deploy, produção, alteração pública de marca, rotas públicas, consentimento ou mudança irreversível exigem aprovação explícita do Luiz.

## Agentes aplicáveis

Reavaliado em 2026-08-28 após a redução de escopo: com o site limitado a três rotas estáticas e um proxy de telemetria de clique, nem todo agente listado no portfólio tem trabalho rotineiro aqui. A lista abaixo distingue quem é central deste repositório de quem atua só sob demanda.

- **Líder funcional (central):** Claudete — prioridade, escopo e critérios de aceite da landing.
- **Responsável técnico web (central):** Renan — implementação, SEO técnico, acessibilidade, performance e o único dono técnico do repositório.
- **Revisão independente (central):** Caio; não implementa a entrega que revisa — obrigatório em qualquer mudança de código, segurança ou produção.
- **Design (sob demanda):** Juliana — só quando a landing, o CTA ou as páginas legais mudarem visualmente; não há mais fluxo de produto complexo a desenhar.
- **Growth e SEO editorial (sob demanda, papel residual):** Marcos — o repositório não tem mais conteúdo editorial, blog ou comparador; acionar apenas para mensagem/posicionamento do CTA de download, não como rotina.
- **Operações, métricas e dados (sob demanda, papel residual):** Gustavo — o único dado é o evento de clique em `/api/track`; acionar apenas se o catálogo dessa métrica ou o pipeline de telemetria mudar, não há mais superfície de dados a manter.
- **Fonte organizacional:** os agentes corporativos canônicos vivem em `../ai-governance/agents/`. Este arquivo não redefine escopo organizacional, só a prioridade de engajamento dentro deste repositório.
- **Skills locais:** `skills/` contém instruções específicas deste repositório.

## Critérios locais de conclusão

- O escopo autorizado foi atendido, os comandos e validações aplicáveis têm evidência, acessibilidade/SEO técnico foram avaliados quando afetados e Caio revisou mudanças com código, segurança, produção ou risco relevante.

## Fontes complementares

- `README.md`
- `package.json`
- `next.config.ts`
- `.env.example`
- `docs/deploy-vercel.md`
- `skills/quality-gates/SKILL.md`
- `skills/accessibility-seo-review/SKILL.md`
- `skills/architecture-guardrails/SKILL.md`
- `../ai-governance/policies/agent-operating-contract.md`
- `../ai-governance/policies/demand-routing.md`
