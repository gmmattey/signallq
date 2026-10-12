# Deploy manual na Vercel

> **Migração (2026-10):** o site saiu do repositório `signallq-web` (arquivado) e vive em `web/` do repositório `signallq`. Workflows: `.github/workflows/web-ci.yml` e `web-deploy-vercel.yml` (**Web Deploy manual na Vercel**). Projeto Vercel, domínio e fluxo manual são os mesmos; os comandos abaixo rodam de dentro de `web/`, e os 3 secrets `VERCEL_*` precisam existir no repositório `signallq`.

A CI é automática e valida cada `push` e pull request destinado a `main`; ela nunca publica o site. A publicação acontece somente pelo workflow **Deploy manual na Vercel**, iniciado em **Actions**. Não há integração Git nativa entre este repositório e a Vercel.

As referências públicas canônicas do aplicativo usam `https://signallq.com`: Metadata API, Open Graph, compartilhamento, `robots.txt` e sitemap. Mantenha esse domínio apontado para a produção antes de publicar uma versão que contenha essas referências; a alteração de DNS não publica código.

## Preparação inicial

Em `web/`, execute os gates antes do primeiro deploy:

```powershell
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

Depois, autentique e vincule o projeto exclusivamente pela CLI:

```powershell
npx vercel login
npx vercel link
```

Use a conta pessoal gratuita do Luiz, crie ou selecione `signallq-web` em `web/` e confirme Next.js. Não conecte o projeto à integração Git da Vercel. A pasta local `.vercel/` continua ignorada e não deve ser versionada.

Faça primeiro um preview e valide a URL exibida; só então publique em produção:

```powershell
npx vercel deploy
npx vercel deploy --prod
```

Registre na issue #19 as URLs, o commit, o projeto/escopo vinculados e a confirmação no painel de que não há auto-deploy por Git. A autenticação, seleção da conta/projeto e eventual aprovação de produção são ações manuais do Luiz.

## Configuração do GitHub Actions

Após o `vercel link`, obtenha `VERCEL_ORG_ID` e `VERCEL_PROJECT_ID` na configuração local criada pela CLI e gere um `VERCEL_TOKEN` na Vercel. Cadastre os três valores como **Actions secrets** do repositório:

- `VERCEL_TOKEN`
- `VERCEL_ORG_ID`
- `VERCEL_PROJECT_ID`

Não copie seus valores para arquivos versionados, README, issue, pull request, logs ou artefatos. O workflow falha antes do deploy e informa qual secret falta quando essa configuração ainda não existir.

Opcionalmente, crie o GitHub Environment `production` e configure revisores obrigatórios. O job de produção já referencia esse Environment; sem proteção adicional, ele ainda é manual porque o único gatilho do workflow é `workflow_dispatch`.

## Publicar ou redeployar

Em **Actions**, abra **Web Deploy manual na Vercel** e clique em **Run workflow**. Escolha:

- `preview` para uma URL de validação;
- `production` apenas para publicação explícita;
- uma branch, tag ou SHA já validado em `ref`.

O workflow primeiro resolve a ref para um SHA imutável e o exibe antes de solicitar a aprovação do Environment de produção. Em seguida, faz checkout desse SHA, repete `npm ci`, lint, typecheck, testes e build, executa `vercel pull`, `vercel build` e publica o artefato pré-construído com a Vercel CLI 58.4.4. Em produção, somente então acrescenta `--prod`. A execução resume ambiente, ref, SHA efetivamente publicado, resultado e URL no GitHub Actions Summary.

Para redeployar uma versão anterior, informe o SHA daquela versão no campo `ref` e escolha o ambiente conscientemente. Não há rollback automático.

## Publicar pela Vercel CLI (sem o workflow)

Enquanto os secrets `VERCEL_*` não estiverem no repositório `signallq`, a publicação pode ser feita de dentro de `web/` com a CLI autenticada (`npx vercel whoami` deve mostrar a conta dona do projeto `signallq-web`):

```bash
npx vercel link --yes --project signallq-web
npx vercel deploy --yes          # preview
npx vercel deploy --prod --yes   # produção
```

Use `vercel deploy` **sem** `--prebuilt`: o build roda na Vercel (Linux). O `vercel build` local no Windows falha com `Unable to find lambda for route` nas rotas dinâmicas. Previews têm proteção por login; para testar rotas use `vercel curl <caminho> --deployment <url>` (no Git Bash do Windows, defina `MSYS_NO_PATHCONV=1`, senão `/caminho` vira caminho de disco).

## Variáveis de ambiente e domínios

- `SITE_INGEST_KEY` precisa estar em **Production** (`vercel env add SITE_INGEST_KEY production --sensitive`) com o mesmo valor da secret do Worker `signallq-admin`. Variável nova só vale em um **novo deploy**.
- Domínios do projeto: `signallq.com` (e `www`, que redireciona) e `speedtest.signallq.com`. O DNS está na **Hostinger**: `speedtest.signallq.com` usa um registro `A speedtest → 76.76.21.21`. Depois que o DNS propagar, a Vercel emite o certificado (se demorar, `npx vercel certs issue speedtest.signallq.com`).

## Falhas comuns

- **Secret ausente:** cadastre os três secrets acima sem registrar valores em qualquer artefato público.
- **Falha nos gates:** corrija o commit indicado; o workflow não publica uma build reprovada.
- **Falha no `vercel pull` ou `build`:** confirme que os IDs pertencem ao projeto vinculado e que o token tem acesso a ele.
- **`/api/track` responde 501:** falta `SITE_INGEST_KEY` em Production na Vercel (ou o deploy foi feito antes de criá-la).
- **Deploy inesperado após push/merge:** investigue a configuração do projeto na Vercel; este repositório não tem gatilho automático de deploy e a integração Git deve permanecer desativada.
