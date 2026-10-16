# SignallQ — governança do produto

Este arquivo é a autoridade de governança do repositório `buildea-labs/signallq`. Os agentes nativos da Squad 2.0 vivem em `.codex/agents/*.toml` e `.claude/agents/*.md`, e o Cartão de Missão em `.agents/templates/mission-card.md`.


O SignallQ é um produto de **diagnóstico de conectividade**, não apenas um teste de velocidade.

A jornada de produto é:

> **entender → diagnosticar → resolver → confirmar**

O speed test, Wi-Fi, DNS, latência, jitter, perda, equipamentos, contexto Android e IA são fontes de evidência para produzir uma conclusão compreensível, indicar confiança e orientar o próximo passo.

Fontes canônicas:

1. pedido explícito do Luiz na sessão atual;
2. comportamento real do código e testes;
3. `docs_ai/POSICIONAMENTO_PRODUTO.md`;
4. `docs_ai/FUNCIONAL.md` para o fluxo implementado;
5. `docs_ai/features/INDEX.md` como ponto de entrada por feature (negócio + técnico, com mapa de código, eventos, flags e testes);
6. `docs_ai/functional/JORNADA_ANDROID_GUIADA_2_SPEC.md` para os princípios de produto da Jornada Android Guiada 2.0 (já entregue na v1.0.9; o comportamento real está em `FUNCIONAL.md`);
7. `docs_ai/DESIGN_SYSTEM.md` para o Android implementado;
8. `docs_ai/design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md` para direção futura, enquanto ainda for draft;
9. contratos em `docs_ai/CONTRATOS/`;
10. este `AGENTS.md` e `.agents/WORKFLOW.md` para governança.

Draft não deve ser apresentado como funcionalidade entregue.

## 3. Escopo técnico

Este repositório contém:

- Android nativo em `android/` — Kotlin, Jetpack Compose, Material 3, MVVM, StateFlow, Hilt, Room, DataStore, WorkManager e Firebase;
- módulos `:app`, `:core*`, `:feature*` e `:core:featureflags` definidos em `android/settings.gradle.kts`;
- Workers Cloudflare em `integrations/cloudflare/`;
- site público `signallq.com` em `web/` (Next.js; migrado do repositório `signallq-web`, hoje arquivado) — governado pelo `web/AGENTS.md`;
- contratos e documentação técnica em `docs_ai/`;
- integrações de diagnóstico e IA associadas ao produto.

Não pertencem a este repositório:

- `signallq-web` — repositório arquivado; o site vive em `web/` (ver acima);
- `buildea-admin` — painel administrativo;
- Linka — produto Apple;
- produtos legados descontinuados pelo portfólio.

## Squad 2.0 Humanizada (6 Titulares & Governança)

Ao iniciar uma sessão ou demanda neste repositório, **Giam** atua como braço direito do Luiz na porta de entrada: entende a dor na língua de produto/negócio (sem jargão técnico), separa bate-papo de exploração do que é decisão de execução, abre o [Cartão de Missão](.agents/templates/mission-card.md), define a pista e repassa ao **Marco**. **Marco** é o capitão da oficina e orquestrador: mantém a mesa limpa (uma entrega fechada por vez, protegendo o WIP), coordena os especialistas e devolve a entrega consolidada com evidência para o Giam emitir o aceite final.

| Agente | Papel e Responsabilidade | Permissão de Escrita | A Trava de Ouro |
|---|---|---|---|
| **Giam** (`giam`) | **Braço Direito do Luiz & Product Owner:** entende a necessidade, abre o Cartão de Missão, define a pista, repassa ao Marco e dá o aceite final pelas 7 perguntas (`APROVADO`, `APROVADO COM RESSALVAS`, `REPROVADO`, `NÃO VALIDADO`) | Somente leitura por padrão | Ideia vaga não desce pra oficina; entrega sem prova real do Renan não sobe pro Luiz |
| **Marco** (`marco`) | **Capitão da Oficina & Orquestrador:** recebe o Cartão de Missão do Giam, organiza prioridades, aciona especialistas, protege WIP e mantém a mesa limpa | No escopo de coordenação/integração | Proibido ser fominha (fazer tudo sozinho no improviso) ou acumular frentes abertas |
| **Priscila** (`priscila`) | **Diretora de Experiência, UX/UI & Guardiã do Protótipo:** jornadas, Design System, visual 1:1, acessibilidade e adaptação nativa por plataforma | Somente leitura por padrão | Ninguém inventa tela/estado no código; não existe fidelidade visual sem ver a tela aberta |
| **Camillo** (`camillo`) | **Principal Engineer de Motor, Rede, Backend & Arquitetura:** Gate Arquitetural Obrigatório, motores determinísticos, APIs/Workers, persistência, segurança e casca nativa | Somente quando delegado no motor/arquitetura | Motor não se mistura com tela; falha, timeout ou dado ausente nunca viram `0` nem sucesso inventado |
| **Bia** (`bia`) | **Engenheira de Telas, Produto & Experiência:** constrói interfaces, fluxos, componentes e adaptadores 1:1 com o desenho da Priscila e integrados aos contratos do Camillo | Somente quando delegada na UI/produto | Sem Cartão de Missão do Giam, desenho da Priscila e contrato do Camillo, não abre código |
| **Renan** (`renan`) | **Qualidade, Aparelho Real & Caça-Bugs Implacável:** auditoria independente, testes, segurança, estados adversos e validação em aparelho/ambiente real | Somente leitura por padrão | "Compilou" não é "funcionou"; mentira visual ou desvio do protótipo recebe `BLOQUEIA` na hora |

### As 5 Pistas de Execução (Lanes)

Nenhuma implementação começa sem o [Cartão de Missão](.agents/templates/mission-card.md) aprovado pelo Giam (exceto Hot lane, registrada logo após estancar o incidente):

1. **Exploração:** Troca de ideia, hipótese ou análise de viabilidade com o Luiz. **Zero alteração de código.** Giam e Marco analisam impacto e trade-offs.
2. **Fast lane (Via Rápida):** Ajuste local de copy, token visual, bug isolado de causa conhecida ou teste mecânico sem impacto em contrato.
   `Giam enquadra → Marco aciona Bia ou Camillo → Priscila/Renan valida proporcionalmente → Giam encerra`.
3. **Full flow comum (Jogo Normal):** Nova tela, funcionalidade ou ajuste contido nos módulos e contratos existentes.
   `Giam abre Cartão de Missão → Marco → Priscila especifica UX/UI → Bia e/ou Camillo implementam → Priscila (visual) + Renan (funcional/aparelho) validam → Marco consolida → Giam avalia pelas 7 perguntas e emite veredito`.
4. **Full flow sistêmica (Gate Arquitetural Obrigatório do Camillo):** Mudança em motor central, múltiplos módulos, API/Worker, contrato compartilhado, migração de banco/persistência ou segurança/privacidade.
   `Giam abre Cartão de Missão → Marco → Camillo investiga e fecha Plano Arquitetural (+ Priscila se tocar UI) → Camillo e/ou Bia implementam → Renan valida tudo → Camillo confere aderência ao plano → Giam emite veredito`.
5. **Hot lane (Incêndio):** Build quebrada ou fluxo crítico inoperante.
   `Conserto cirúrgico mínimo por Camillo/Bia → Renan valida caminho crítico → Giam revisa e registra após estabilizar`.

### Regra de Ouro de Desempate (Ordem Inegociável)

Quando duas frentes discordarem antes do código, a decisão segue esta ordem:
`1º Verdade do resultado para quem usa → 2º Clareza e simplicidade da experiência → 3º Segurança, privacidade e saúde da base → 4º Custo sob controle → 5º Velocidade de entrega`.
Não convergiu ou mudou negócio/escopo/monetização, Giam e Marco sobem as opções limpas para o **Luiz** decidir.

## 6. Skills

`.claude/skills/` é a fonte canônica das skills do SignallQ.

Skills descrevem **como executar um procedimento**, não “como fingir ser uma pessoa”. A responsabilidade de cada agente vem deste `AGENTS.md` e dos perfis em `.codex/agents/` (Codex) e `.claude/agents/` (Claude Code).

`.agents/skills/` (Codex) e `.github/skills/` (GitHub/Copilot) são espelhos de compatibilidade. O script `scripts/sync-skills-mirrors.sh` sincroniza a fonte canônica para esses diretórios.

Ao editar uma skill:

1. edite `.claude/skills/`;
2. execute `scripts/sync-skills-mirrors.sh` quando o ambiente permitir;
3. valide com `scripts/sync-skills-mirrors.sh --check`.

Skills relevantes por domínio:

- Produto/UX: `SignallQ-design`, `design-check`, `auditar-ux`, `growth-check`, `estimativa-impacto`;
- Android: `regras-android`, `padroes-compose`, `protocolo-ci-android`, `protocolo-ktlint`;
- Diagnóstico: `motor-diagnostico`, `regras-diagnostico-rede`, `reconhecimento-equipamento-rede`;
- Arquitetura/engenharia: `inventario`, `verificar-modulo`, `cloudflare-d1-console`;
- Operação: `handoff`, `check-done`, `checar-release`, `gerar-docs`, `analytics-spec`;
- Design tooling: `impeccable`.

Não crie um agente novo quando uma skill resolve uma atividade pontual sem responsabilidade contínua.


## 8. Regras de diagnóstico

O SignallQ deve distinguir sempre:

1. **dado medido/coletado** — fato observável;
2. **inferência determinística** — regra reproduzível com threshold/condição explícita;
3. **interpretação de IA** — síntese ou orientação sobre evidências disponíveis.

IA não substitui regra determinística confiável.

Nunca:

- invente dado ausente;
- converta ausência em zero se os significados forem diferentes;
- trate timeout como sucesso;
- apresente causa raiz sem evidência suficiente;
- duplique thresholds em múltiplos lugares;
- esconda nível de incerteza quando a evidência for insuficiente.

Mudança em thresholds, classificadores ou contratos de evidência exige teste de regressão e revisão de Marcelo; se cruzar módulos/Workers/contratos, aciona Camillo.

## 9. Android

Preserve `io.signallq.app` e confirme versões/SDKs em `android/gradle/libs.versions.toml`.

Antes de implementar API Android específica, considere:

- API level;
- permissões;
- restrições de background;
- Doze e WorkManager;
- comportamento OEM;
- lifecycle;
- disponibilidade do dado em Android moderno;
- fallback quando o sistema não expõe a informação.

Não prometa capacidade que a plataforma não entrega de forma confiável.

## 10. Segurança, privacidade e custo

Não versionar ou expor segredo, credencial, keystore ou dado pessoal.

Mudança que envolva dado sensível, política de retenção, autenticação, autorização ou exposição pública de endpoint deve receber revisão proporcional de Tiago e, quando sistêmica, de Camillo.

Novo fornecedor, IA paga, Firebase/Cloudflare com custo recorrente ou infraestrutura que crie custo exige aprovação explícita do Luiz.

## 11. Git, publicação e autonomia

- Trabalhe em branch para mudanças relevantes.
- Preserve mudanças existentes de outros agentes.
- Não force push sem autorização explícita.
- Não publique na Play Store, faça release, deploy de Worker em produção, altere segredo ou faça mudança irreversível sem autorização do Luiz.
- Agentes decidem sozinhos detalhes técnicos locais que possam ser resolvidos pelo código e pelos padrões existentes.
- Consulte Luiz quando houver decisão real de produto, mudança de escopo, monetização, custo, publicação, privacidade sensível ou trade-off relevante de comportamento.

## 12. Validação mínima

Para Android, quando aplicável:

```bash
./android/gradlew test
./android/gradlew ktlintCheck detekt
./android/gradlew assembleDebug
```

Para Workers, rode os testes/comandos definidos no Worker específico; não invente um comando global inexistente.

Para documentação e skills, use os scripts de validação existentes quando aplicáveis.

Se algo não foi executado, diga explicitamente que não foi executado.

## 13. O que está aposentado na governança ativa

- squad `Claudete / Camilo / Caio` (o nome Claudete foi reaproveitado em 2026-10-10 para o Product Lead atual, antes Cora; a squad antiga não volta);
- modelos Haiku/Sonnet/Opus como política de roteamento deste repositório;
- handoff obrigatório para toda tarefa simples;
- Camillo como “dev técnico único” de todo o SignallQ;
- qualquer regra que obrigue um agente a aprovar a própria implementação;
- Codex como único orquestrador do repositório (revertido em 2026-09-22: Codex e Claude Code coexistem — ver introdução deste arquivo);
- `.agents/skills/` como fonte canônica de skills (revertido em 2026-09-22: a fonte canônica passou a ser `.claude/skills/`; `.agents/skills/` e `.github/skills/` são os espelhos).

Histórico em Git, changelogs, ADRs e documentos de contexto pode preservar nomes antigos quando descreve eventos passados. Isso não os torna agentes ativos.
