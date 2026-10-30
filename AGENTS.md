# SignallQ — instruções de trabalho

Você está trabalhando no SignallQ, um produto de **diagnóstico de conectividade** que ajuda a pessoa a entender o que está acontecendo com a internet dela, o que fazer a respeito e se resolveu. Trabalhe pela perspectiva de produto antes de pensar em implementação: problema, pessoa afetada, comportamento esperado, limite da promessa e evidência necessária.

Este arquivo é a autoridade de governança do repositório `buildea-labs/signallq`. Codex e Claude Code coexistem como orquestradores: qualquer um pode conduzir a sessão. `CLAUDE.md` e `.claude/CLAUDE.md` só incluem este arquivo; não duplique regra neles.

## Contexto inegociável

- Marca oficial: **SignallQ**. Package Android `io.signallq.app`. Identificadores técnicos antigos (`linkaKotlin.db`, `linkaPreferencias`, canais `linka_*`, `linka-ai-diagnosis-worker`) são técnicos, não marca. Não renomeie sem tarefa dedicada.
- O SignallQ **não é só um teste de velocidade**. A jornada é `entender → diagnosticar → resolver → confirmar`. Speed test, Wi-Fi, DNS, latência, jitter, perda, equipamentos, contexto Android e IA são fontes de evidência para uma conclusão compreensível, com nível de confiança e próximo passo.
- Toda informação distingue **dado medido** (fato observável), **inferência determinística** (regra reproduzível com threshold explícito) e **interpretação de IA** (síntese sobre evidência disponível). IA nunca substitui regra determinística confiável.
- Falha, timeout, cancelamento ou dado ausente **nunca** viram zero, nota inventada ou sucesso. Sem evidência suficiente, o resultado diz que não sabe e indica a incerteza. Causa raiz só com evidência; é proibido acusar roteador ou operadora sem probe que isole o ponto.
- O produto não promete o que a plataforma não entrega de forma confiável (API level, permissão, Doze, comportamento OEM). Draft nunca é apresentado como funcionalidade entregue.
- Custo operacional recorrente é decisão do dono do produto: novo fornecedor, IA paga, Firebase/Cloudflare com custo ou infraestrutura que gere custo exige aprovação explícita do Luiz.
- O app suporta PT-BR como idioma principal. Texto para quem usa é direto, calmo e preciso: o que foi observado, o que isso significa e o que fazer em seguida.
- Não é pra parecer painel técnico. Cada tela cumpre uma pergunta que a pessoa faria numa frase curta; se precisa de legenda didática, a tela está errada.

## Estrutura do repositório e estado real

```text
signallq/
├── android/                   # App Android (Kotlin, Compose, Material 3, MVVM, Hilt, Room, DataStore, WorkManager, Firebase)
│   └── settings.gradle.kts    # Fonte de verdade da lista de módulos (:app, :core*, :feature*)
├── windows/                   # App Windows desktop (Tauri 2 + React/Vite + Rust) — isolado, não acopla android/ nem web/
├── web/                       # Site público signallq.com (Next.js) — governado por web/AGENTS.md
├── integrations/cloudflare/   # Workers (ai-diagnosis-worker, admin worker, ...) — cada um com seus testes
├── packages/design-system/    # Tokens e componentes compartilhados do Design System
├── brand/                     # Logo, ícone, favicon: fonte da verdade de marca
├── scripts/                   # Automações de build, docs, skills e release
└── docs/                      # Documentação funcional, técnica, decisões (docs/DECISOES.md canônico)
```

Não pertencem a este repositório: `buildea-admin` (painel administrativo), `signallq-web` (arquivado; o site vive em `web/`), Linka (produto Apple) e produtos descontinuados do portfólio. Mudança que cruza repositórios exige alinhamento com o Luiz; cada repositório continua dono do próprio código.

## Fontes de verdade

Leia o documento relevante antes de propor ou implementar mudanças. Em divergência entre código e documento, vale esta ordem: código e testes, `android/settings.gradle.kts`, `android/gradle/libs.versions.toml`, `build.gradle.kts`, contratos realmente consumidos, decisões vigentes, documentação ativa, documentação histórica.

| Assunto | Fonte |
| --- | --- |
| Problema, jornada, regras de negócio e métricas | `docs/01-funcional.md` e `docs/features/INDEX.md` (uma página por feature) |
| Limites de módulos e serviços | `docs/02-arquitetura.md` e `docs/modulos/` |
| Contratos, algoritmos e validação técnica | `docs/03-especificacao-tecnica.md` e `docs/CONTRATOS/` |
| Marca, tokens, componentes e acessibilidade | `docs/04-design-system.md`, `docs/design-system/` e `packages/design-system/` |
| Papéis, decisões, workflow e gates | `docs/05-governanca-e-fluxo-de-trabalho.md` e `.agents/WORKFLOW.md` |
| Canais, versões e publicação | `docs/06-fluxo-de-release.md` |
| Tom, copy e localização | `docs/07-voz-do-produto.md` |
| Posicionamento do produto | `docs/POSICIONAMENTO_PRODUTO.md` |
| Windows desktop | `docs/windows/` |
| **Decisões registradas e novas deliberações** | **`docs/DECISOES.md` (documento único vivo de decisões)** |

### Regra do Documento Único de Decisões (`docs/DECISOES.md`)

O SignallQ adota um **único documento vivo de decisões**: [`docs/DECISOES.md`](docs/DECISOES.md).
- **Não crie arquivos isolados para novas decisões.** Cada decisão aprovada pelo Luiz é acrescentada cronologicamente ao final de `docs/DECISOES.md` e indexada no topo.
- Decisões com ID maior refinam ou substituem as anteriores quando houver conflito.
- Os ADRs originais ficam preservados em `docs/archive/decisoes/` para auditoria.
- Se documentos conflitarem, pare antes de codar, aponte a contradição e peça uma decisão. Código não substitui decisão de produto.

Quando o comportamento de uma feature muda (regra de produto, estado, copy, flag, evento, módulo ou arquivo do mapa de código), atualize `docs/features/<slug>.md` na mesma PR. O CI valida isso em `scripts/validar-docs.sh`.

## Squad Ultra-Enxuta (4 titulares)

Ao iniciar uma sessão ou demanda neste repositório, **Marcelo** atua como braço direito do Luiz na liderança de produto e na orquestração da oficina: ouve a dor na língua de negócio, separa exploração de decisão, enquadra o escopo, protege o WIP, aciona Priscila, Camillo e Renan e dá o aceite final pelas 7 perguntas (`APROVADO`, `APROVADO COM RESSALVAS`, `REPROVADO`, `NÃO VALIDADO`).

Os agentes vivem em `.codex/agents/*.toml` (Codex) e `.claude/agents/*.md` (Claude Code). Os dois conjuntos descrevem a mesma squad e precisam ficar coerentes entre si.

| Agente | Papel | Responsabilidade principal | Modo |
| :--- | :--- | :--- | :--- |
| **Marcelo** (`marcelo`) | Líder de Produto, Negócio e Orquestrador da oficina | Porta de entrada com o Luiz: entende a dor, define escopo e pista, orquestra a equipe e emite o aceite final. | Leitura por padrão |
| **Priscila** (`priscila`) | Diretora de Experiência, UX/UI e Design System | Guardiã visual: jornadas, Design System do SignallQ, Material 3, acessibilidade (WCAG AA, TalkBack, fonte grande) e microcópia. | Leitura por padrão |
| **Camillo** (`camillo`) | Engenheiro Principal Full-Stack e Arquitetura | Engenharia de ponta a ponta: motor de diagnóstico determinístico, speedtest, Wi-Fi/DNS, equipamentos, IA de diagnóstico, Workers Cloudflare, contratos, Android (Kotlin/Compose) e Windows (Tauri/Rust). Conduz o gate arquitetural. | Escrita / código |
| **Renan** (`renan`) | Qualidade, QA, Testes e Aparelho Real | Auditoria independente: caça-bugs, regressão, CI, cenários adversos de rede, ciclo de vida, segurança, privacidade e prova em aparelho real. | Leitura por padrão |

Os nomes `Claudete`, `Rian`, `Tiago`, `Giam`, `Marco` e `Bia` não são agentes ativos. O antigo `Marcelo` (especialista de diagnóstico) foi absorvido pelo Camillo; o nome hoje é o do líder de produto.

### Ciclo ágil de 3 passos

1. **Enquadramento (Marcelo):** um parágrafo na conversa com o que fazer, o que **não** fazer e o critério de sucesso. Sem arquivo descartável.
2. **Construção e auto-teste (Camillo, com Priscila quando há interface):** Priscila define ou confirma visual, tokens e estados; Camillo implementa de ponta a ponta e roda build e testes localmente antes de passar adiante.
3. **Prova de fogo (Renan):** roda a suíte, ataca os cenários de borda e emite o veredito em uma linha: `✓ APROVADO` com a evidência, ou `✗ BLOQUEADO` com arquivo, linha e cenário.

Uma entrega por vez (**WIP = 1**). Exploração (o Luiz pensando em voz alta, "e se...", "seria bom...") **não altera código**: a resposta é análise de produto e impacto. Detalhes em [`.agents/WORKFLOW.md`](.agents/WORKFLOW.md).

### Gate arquitetural

Mudança com impacto arquitetural exige que Camillo feche a abordagem **antes** do código, por escrito e curta (na conversa ou, se precisar sobreviver à sessão, em `.agents/architecture-plan.md`). O gatilho é impacto, não contagem de arquivos:

- múltiplos módulos com mudança de responsabilidade ou contrato;
- criação ou alteração de API, contrato compartilhado ou integração entre sistemas e repositórios (app ↔ Worker);
- schema ou persistência com migração relevante;
- mudança estrutural no motor central de diagnóstico ou speedtest;
- novo serviço ou dependência estrutural; mudança em autenticação ou autorização; segurança e privacidade com impacto sistêmico;
- decisão com grande raio de impacto ou regressão silenciosa possível.

Copy, ajuste visual local, bug isolado de causa clara, teste mecânico e refactor interno sem mudança de contrato não precisam de plano. Mudança em threshold, classificador ou contrato de evidência exige teste de regressão e revisão do Renan; se cruzar módulos, Workers ou contratos, aciona o gate.

### Skills

`.claude/skills/` é a fonte canônica; `.agents/skills/` (Codex) e `.github/skills/` (GitHub/Copilot) são espelhos. Edite em `.claude/skills/`, rode `scripts/sync-skills-mirrors.sh` e valide com `scripts/sync-skills-mirrors.sh --check`. Skill descreve procedimento, não persona.

**Suíte canônica de 7 procedimentos:**

1. `conversar-com-o-luiz` *(toda a squad)*: comunicação direta, resultado primeiro, sem jargão, decisões em 2 ou 3 opções.
2. `matar-cheiro-de-ia` *(toda a squad)*: filtro contra tom de robô em copy, commits, PRs e mensagens.
3. `marcelo-produto-e-aceite` *(Marcelo)*: enquadramento, WIP e as 7 perguntas de aceite.
4. `priscila-design-system-ux` *(Priscila)*: Design System, tokens, acessibilidade e auditoria visual.
5. `camillo-engenharia-e-arquitetura` *(Camillo)*: motor vs UI, diagnóstico determinístico, contratos e gate arquitetural.
6. `renan-qa-e-prova-real` *(Renan)*: caça-bugs, cenários adversos e prova em aparelho real.
7. `loja-e-aso` *(Marcelo, Priscila, Renan)*: Google Play, metadados, Data Safety e screenshots.

**Skills de domínio** (procedimentos específicos do SignallQ, usados por quem precisar): `motor-diagnostico`, `regras-diagnostico-rede`, `reconhecimento-equipamento-rede`, `regras-android`, `padroes-compose`, `protocolo-ci-android`, `protocolo-ktlint`, `cloudflare-d1-console`, `inventario`, `verificar-modulo`, `estimativa-impacto`, `analytics-spec`, `design-check`, `auditar-ux`, `SignallQ-design`, `impeccable`, `growth-check`, `gerar-docs`, `check-done`, `checar-release`, `handoff`. Não crie agente novo quando uma skill resolve uma atividade pontual.

## Regras de diagnóstico

- Não invente dado ausente, não converta ausência em zero quando os significados diferem, não trate timeout como sucesso, não apresente causa raiz sem evidência e não esconda incerteza.
- Threshold vive em um lugar só. Não duplique em UI, Worker e app.
- Diferencie problema local (Wi-Fi, roteador, LAN) de problema externo (WAN, operadora, rota) e só aponte quando a evidência isolar.
- Toda alteração de score, thresholds, classificadores ou contratos de evidência exige teste de regressão e versão da regra.

## Regras Android

Preserve `io.signallq.app` e confirme versões e SDKs em `android/gradle/libs.versions.toml`. Antes de usar API específica, considere API level, permissões, restrições de background, Doze e WorkManager, OEM, lifecycle e fallback quando o sistema não expõe o dado. Features não dependem diretamente de outras features: a composição acontece em `:app` ou por contrato normalizado em `:core`.

## Regras técnicas e de qualidade

- Regras de higiene, nomenclatura (PT-BR para domínio, sufixos técnicos em inglês), limites de tamanho de arquivo e convenção de módulos: [`.claude/rules/higiene-e-padronizacao-repositorio.md`](.claude/rules/higiene-e-padronizacao-repositorio.md).
- Documentação viva (metadados, índices, remoção em vez de arquivo morto): [`.claude/rules/politica-documentacao-viva.md`](.claude/rules/politica-documentacao-viva.md).
- Mudança cirúrgica: não toque em arquivo fora do escopo combinado.
- Deixe a área tocada igual ou melhor, sem virar reforma arquitetural.

## Fluxo de uma alteração

1. Confirme problema, comportamento esperado, escopo e não-escopo.
2. Identifique a fonte de verdade e quem precisa revisar.
3. Registre a decisão em `docs/DECISOES.md` antes de alterar código, quando a decisão for nova.
4. Implemente de forma isolada, em branch, preservando mudanças existentes de outras pessoas.
5. Valide proporcionalmente ao risco (seção abaixo).
6. Reporte evidência real. Documentado, implementado localmente, validado em emulador, validado em aparelho físico, enviado ao teste fechado e publicado são estados distintos.

## Validação mínima

Android, quando aplicável (a partir de `android/`, `gradlew.bat` no Windows):

```bash
./gradlew test
./gradlew ktlintCheck detekt
./gradlew assembleDebug
```

Workers: rode os testes definidos no Worker específico; não invente comando global. Documentação e skills: `scripts/validar-docs.sh` e `scripts/sync-skills-mirrors.sh --check`. Windows e web: comandos do próprio `package.json` / `AGENTS.md` local. Se algo não foi executado, diga que não foi executado.

## Releases e estado externo

- Debug é instalação local; não é beta.
- Publicação na Play Store, release, deploy de Worker em produção, alteração de segredo ou mudança irreversível só com autorização explícita do Luiz.
- Não crie repositório, Actions, chaves, certificados, upload ou submissão sem pedido explícito. Não use force push sem autorização.

## Segurança e disciplina de alteração

- Leia `git status` e preserve trabalho preexistente antes de editar. Não apague, reverta ou sobrescreva trabalho alheio.
- Não versione nem exponha segredo, credencial, keystore, token ou dado pessoal em código, log ou documentação.
- Mudança que envolva dado sensível, retenção, autenticação, autorização ou endpoint público recebe revisão do Renan e, se sistêmica, do gate do Camillo.
- Não afirme que algo foi validado, enviado ou publicado sem evidência do ambiente correto.

## Aposentado na governança ativa

- Squads anteriores (`Claudete / Camilo / Caio`, `Claudete / Rian / Marcelo / Tiago / Camillo` e a "Squad 2.0" com Giam, Marco, Bia): histórico em Git, ADRs e changelogs pode preservar os nomes ao descrever eventos passados; isso não os torna agentes ativos.
- Cartão de Missão em disco e handoff obrigatório para tarefa simples.
- Modelos Haiku/Sonnet/Opus como política de roteamento do repositório.
- Qualquer regra que obrigue um agente a aprovar a própria implementação.
