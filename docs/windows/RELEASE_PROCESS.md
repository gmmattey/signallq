---
title: "Processo de release — SignallQ para Windows"
description: "Versionamento, tags, build, publicação e distribuição do instalador Windows em signallq.com/windows."
type: "runbook"
status: "draft"
owner: "Camillo"
last_updated: "2026-11-07"
version: "0.2.0"
---

# Processo de release — SignallQ para Windows

- **Fonte de verdade:** `.github/workflows/windows-release.yml`, `windows/scripts/version.mjs`, `windows/scripts/release-notes.mjs` e `windows/CHANGELOG.md`. Decisão: ADR-024 em [`../DECISOES.md`](../DECISOES.md).
- **Escopo:** o instalador do app desktop (`windows/`). Android tem fluxo próprio ([`../06-fluxo-de-release.md`](../06-fluxo-de-release.md)); o site em `web/` só consome o resultado.
- **Estado:** rascunho (ADR-024 ainda "Proposto"). O processo está implementado, mas **nenhuma release foi publicada ainda** e o workflow nunca rodou no GitHub. Ver [Pendências](#pendências-antes-da-primeira-release).

## Estados que não se misturam

| Estado | Significa | Quem faz |
| --- | --- | --- |
| Build de CI | `windows-ci.yml` compila e testa a cada mudança. Artefato temporário, não é release | automático |
| Ensaio | Disparo manual de `windows-release.yml`: constrói e guarda o instalador como artefato. Não cria release | Camillo |
| Rascunho | Push de tag `windows-vX.Y.Z`: o workflow cria a release como **rascunho**, com instalador, MSI e `SHA256SUMS.txt`. Ninguém de fora enxerga | automático, após autorização do Luiz para a tag |
| Publicada | O Luiz clica em "Publish release" no GitHub. A partir daí o site mostra o download | **só o Luiz** |
| Retirada | A release volta a rascunho (ou é apagada). O site deixa de oferecer o download | Luiz |

Instalado em um PC, validado no Windows real, rascunho e publicada são afirmações diferentes. Só afirme o que tem evidência do ambiente correto.

## Versionamento

- **Formato:** `MAJOR.MINOR.PATCH` ([SemVer](https://semver.org/lang/pt-BR/)), **independente** da versão do Android. Enquanto o app estiver em `0.x`, ele é pré-estável: `MINOR` traz recurso ou mudança visível e `PATCH` só correção.
- **Quando subir:**
  - `PATCH`: correção sem mudar o que a pessoa vê ou faz;
  - `MINOR`: tela, medição, ferramenta ou comportamento novo;
  - `MAJOR`: só a partir de `1.0.0` (primeira versão considerada estável) ou quebra de dados locais sem migração.
- **Sem sufixo** (`-rc.1`, `-beta`): o instalador MSI só aceita versão numérica. Teste antes de publicar com o **ensaio** e com o rascunho, não com uma versão intermediária.
- **Uma versão, cinco arquivos:** `package.json`, `package-lock.json`, `src-tauri/tauri.conf.json`, `src-tauri/Cargo.toml` e `src-tauri/Cargo.lock`. Nunca edite à mão: use `npm run version:set -- X.Y.Z` (em `windows/`) e confira com `npm run version:check`. O workflow reprova a release se a tag e os arquivos divergirem.
- **Tag:** `windows-vX.Y.Z`, sempre em um commit já presente na `main`. O prefixo evita colisão com as tags `v*` do Android, que disparam a publicação na Play Console. Tag publicada não se move nem se reaproveita: se algo der errado, a correção é um `PATCH` novo.
- **Dados locais:** mudança no esquema do SQLite do histórico exige migração testada e entra no CHANGELOG. Quebra sem migração é `MAJOR`.

## Passo a passo

1. **Preparar (Camillo).** Em branch a partir da `main`:
   - `npm run version:set -- X.Y.Z` e `npm run version:check`;
   - mover o conteúdo de "Não lançado" do [`windows/CHANGELOG.md`](../../windows/CHANGELOG.md) para `## [X.Y.Z] - AAAA-MM-DD`, em linguagem de quem usa o app;
   - `npm test` e `npm run build` em `windows/`; abrir PR e passar pelo `windows-ci.yml`, que roda testes, `fmt`, `clippy` e o bundle.
2. **Ensaio (opcional, recomendado na primeira vez e em mudança de instalador).** Em Actions, rode "Windows Release" com a tag (`windows-vX.Y.Z`). Baixe o artefato e instale em um Windows limpo.
3. **Validar no Windows real (Renan).** Instalador novo em Windows 10 e 11 sem o app instalado, e atualização sobre a versão anterior: instalar, abrir, medir, cancelar, exportar CSV, ligar o monitor, fechar, desinstalar. Registrar o que foi provado e o que não foi.
4. **Aceite (Marcelo).** Notas do CHANGELOG batem com o que mudou; limitações conhecidas estão escritas.
5. **Autorização (Luiz).** Autoriza criar a tag. Sem essa autorização ninguém cria tag `windows-v*`. Para travar isso no GitHub e não só no processo, crie uma regra de proteção de tags (Settings > Rules > Rulesets) restringindo `windows-v*` a quem pode criar release.
6. **Tag.** Depois do merge na `main`: `git tag windows-vX.Y.Z <commit>` e `git push origin windows-vX.Y.Z`. O workflow valida, constrói, testa e cria o **rascunho**.
7. **Conferir o rascunho.** Os três arquivos existem, os nomes seguem a tabela abaixo, o `SHA256SUMS.txt` confere com o arquivo baixado (`certutil -hashfile <arquivo> SHA256`) e o texto da release é o do CHANGELOG.
8. **Publicar (Luiz).** "Publish release" no GitHub, **sem** marcar como "Latest" (a release "Latest" do repositório é a do Android). Marque "pre-release" para uma versão que ainda não deve aparecer no site.
9. **Conferir o site.** `signallq.com/windows` consulta o GitHub a cada visita: recarregue e a versão nova já aparece (em até 5 minutos se o navegador tiver a consulta anterior em cache de sessão). Baixe pelo botão e confira o SHA-256 exibido.

## Arquivos de cada release

| Arquivo | Para quê |
| --- | --- |
| `SignallQ-Windows-X.Y.Z-x64-setup.exe` | Instalador padrão (NSIS). É o botão principal do site |
| `SignallQ-Windows-X.Y.Z-x64.msi` | Para quem instala por política da empresa |
| `SHA256SUMS.txt` | Uma linha por arquivo: `hash  nome`. O mesmo conteúdo vai no texto da release, num bloco de código; o site lê o hash de qualquer linha `<64 hex>  <nome do arquivo>` do texto |

Os nomes dos arquivos, o formato `.../releases/download/<tag>/<nome>` e as linhas de hash no texto da release são contrato com o site: ele aceita só a URL exata e lê o hash dessas linhas. Mudar qualquer um exige mudar `sites/signallq/src/lib/windowsRelease.ts` no repositório do site (`gmmattey/sites`, hoje `landings`) antes de publicar.

## Como o site oferece o download

O site `signallq.com` **não vive neste repositório**: o código está em `gmmattey/sites` (renomeado `landings`), em `sites/signallq/`, publicado na Cloudflare. A pasta `web/` deste repositório é uma cópia anterior e não é a fonte do site em produção (confirmar antes de qualquer deploy dela).

- A página `/windows` (`sites/signallq/src/app/windows/`, também em `/en/windows` e `/es/windows`) consulta no **navegador do visitante**, a cada visita, a API pública de releases do GitHub e escolhe a mais recente que cumpre **todas**: tag `windows-vX.Y.Z`, não é rascunho, não é pré-lançamento e tem o instalador `SignallQ-Windows-X.Y.Z-x64-setup.exe` com a mesma versão da tag.
- O botão aponta **direto** para o arquivo no GitHub Releases. Não há cópia do instalador no site nem armazenamento próprio: **sem custo novo**. Publicar a release atualiza a página na hora, **sem rebuild nem deploy do site**.
- Limites: a API pública do GitHub permite 60 consultas por hora por IP. A página guarda o resultado 5 minutos na sessão e, se a API falhar, mostra a última resposta boa (até 1 h).
- Se a API responder e não houver release do Windows publicada, a página diz que ainda não está disponível e **não mostra botão**. Se a API falhar sem resposta anterior, diz que não conseguiu verificar e aponta para a página de versões do GitHub; nunca afirma que "não existe".
- Mudar a própria página é deploy do site (`gmmattey/sites`), que continua manual e autorizado pelo Luiz.

## Assinatura digital

O instalador **não é assinado** hoje. Consequências: o Windows SmartScreen pode mostrar "O Windows protegeu o seu computador" e o editor aparece como desconhecido. Por isso a página mostra o aviso e o SHA-256.

Assinar exige certificado de code signing (custo recorrente) ou serviço de assinatura: **decisão de custo do Luiz**. Enquanto não houver, a constante `WINDOWS_INSTALLER_SIGNED` em `sites/signallq/src/lib/windowsRelease.ts` (repositório `gmmattey/sites`) fica `false`; ao assinar, mude para `true` na mesma PR que ativa a assinatura no workflow. Alternativas já mapeadas: certificado OV/EV, Azure Trusted Signing, MSIX pela Microsoft Store.

## Retirar ou corrigir uma release

- **Problema grave em versão publicada:** volte a release para rascunho. O site para de oferecer o download na visita seguinte; só se a API do GitHub estiver fora do ar a página pode mostrar a última resposta boa por até 1 hora. Depois corrija e publique um `PATCH`.
- **Erro só no texto da release:** edite no GitHub; o CHANGELOG é corrigido em PR.
- **Nunca** apague a tag nem force push dela. Se a tag foi criada por engano antes de publicar, apague o rascunho e a tag **com autorização do Luiz** e recomece com a mesma versão.

## Quem pode o quê

| Ação | Quem |
| --- | --- |
| Preparar versão, CHANGELOG, PR, ensaio | Camillo |
| Validar no Windows real | Renan |
| Aceite do conteúdo da release | Marcelo |
| Criar tag `windows-v*`, publicar release, assinar, mudar custo | **Luiz** |
| Deploy do site (`gmmattey/sites`) | **Luiz** |

Agentes preparam e conferem; não criam tag, não publicam e não fazem deploy sem autorização explícita.

## Pendências antes da primeira release

1. Nenhum instalador foi gerado por este workflow nem instalado em Windows limpo. O ensaio e a validação do passo 3 são obrigatórios antes da primeira tag.
2. Decidir assinatura (custo) ou aceitar publicar sem assinar, com o aviso na página.
3. Atualização automática dentro do app (`tauri-plugin-updater`) está **fora de escopo**: exige chave de assinatura própria e endpoint. Hoje a pessoa baixa a versão nova no site.
4. Telemetria de clique no botão de download não existe: um evento novo precisa de especificação (`analytics-spec`) e da aprovação do Luiz.
5. Escolher o número da primeira release pública (sugestão: `0.3.0`, com o novo visual) e rodar `version:set`.
6. A página `/windows` e o link no rodapé estão numa branch do repositório do site e **não foram publicados**. Merge e deploy dependem do Luiz.
7. `web/AGENTS.md` deste repositório diz que o site vive em `web/`. Isso diverge da realidade e precisa de decisão: arquivar `web/` ou atualizar o texto.
8. A frase "Windows 10 ou 11" não aparece na página porque nenhum instalador foi testado nessas versões; só entra depois da validação do passo 3.
