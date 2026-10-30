---
title: "Decisões do SignallQ"
description: "Documento único e vivo das decisões do produto e da engenharia do SignallQ, em ordem cronológica de numeração."
type: "adr"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-30"
version: "1.0.0"
---

# Decisões do SignallQ

- **Fonte de verdade:** este arquivo. Decisão nova entra **ao final**, numerada em sequência, e é indexada na tabela abaixo.
- **Não crie arquivo isolado por decisão.** Os ADRs originais ficam em [`archive/decisoes/`](archive/decisoes/) só para auditoria.
- ID maior refina ou substitui o anterior quando houver conflito. Divergência entre documentos sem decisão posterior pede alinhamento antes de implementar.
- Aprovação final de escopo, marca, monetização, custo e publicação é do Luiz.

## Índice

| ID | Decisão | Data | Status |
| --- | --- | --- | --- |
| [ADR-001](#adr-001) | Usar Timber como biblioteca de logging | 2026-05-24 | Accepted |
| [ADR-002](#adr-002) | Ktlint + Detekt como ferramentas de qualidade automática | 2026-05-24 | Accepted |
| [ADR-003](#adr-003) | DispatcherProvider via injeção de dependência (sem Hilt) | 2026-05-24 | Accepted |
| [ADR-004](#adr-004) | Arquitetura multi-módulo Android (app + core* + feature*) | 2026-05-24 | Accepted |
| [ADR-005](#adr-005) | Modelo de custo de IA e fallback (free tier) | 2026-06-23 | Accepted |
| [ADR-008](#adr-008) | Features futuras (#952, #951) usam arquitetura D1-only, sem R2/KV/Cron Trigger/Queue | 2026-07-16 | Accepted |
| [ADR-009](#adr-009) | Vocabulário canônico de issue de diagnóstico (Android x Admin) | 2026-07-16 | Aceito |
| [ADR-011](#adr-011) | Fase 0 do motor canônico de diagnóstico (issue #1228) | 2026-07-26 | Aceito |
| [ADR-012](#adr-012) | Fase 3 de #1228: `executionId`/`rulesVersion` em `MedicaoEntity` | 2026-07-31 | Aceito |
| [ADR-013](#adr-013) | Fatia 4 de #1228: plano técnico para latência/perda de pacotes/upload (`MetricClassifier` vs. `InternetDiagnosticEngine`) | 2026-07-31 | Aceito (arquitetura); código bloqueado até decisão de produto |
| [ADR-016](#adr-016) | Portfólio Buildea: SignallQ e Linka | 2026-08-15 | Aceito |
| [ADR-017](#adr-017) | Motor de diagnóstico e IA migram para o NDS | 2026-08-19 | Aceito |
| [ADR-018](#adr-018) | Schema canônico `DiagnosticSnapshot` para o payload NDS | 2026-09-02 | Aceito |
| [ADR-019](#adr-019) | SignallQ Windows Desktop | 2026-10-14 | Aceito |

---

<a id="adr-001"></a>
## ADR-001: Usar Timber como biblioteca de logging

**Data:** 2026-05-24  
**Status:** Accepted  
**Atualizado em:** 2026-06-28 — Crashlytics integrado via ReleaseTree (SIG-227)

### Contexto

O projeto Android herdou uso inconsistente de `Log.*` (Android logging nativo) espalhado pelo codebase. A biblioteca `Log.d()`, `Log.e()` etc. oferece pouca flexibilidade para:
- Redirecionamento de logs em diferentes ambientes (debug vs. release)
- Integração com ferramentas de crash reporting (Firebase Crashlytics, etc.)
- Formatação uniforme de mensagens
- Supressão de logs em release sem refactoring

### Decisão

Migrar todo logging do projeto para usar **Timber** (biblioteca mantida por Jake Wharton, depêndência padrão em arquitetura moderna Android).

**Implementação:**
- `com.jakewharton.timber:timber` em `libs.versions.toml`
- `SignallQApplication.onCreate()` planta `DebugTree` em debug e `ReleaseTree` em release
- Todas as ocorrências de `Log.*` migradas para `Timber.*`

**Benefícios:**
- Logs automaticamente suprimidos em release (via BuildConfig.DEBUG)
- Stack trace rastreável em relatório de crashes
- Formatação consistente com tag automática (classe + linha)
- Integração com Crashlytics via `ReleaseTree` sem mudança de código nos chamadores

### Estado atual da integração com Crashlytics

`ReleaseTree` (`app/src/main/kotlin/.../logging/ReleaseTree.kt`):
- Filtra apenas `WARN` e `ERROR` (`priority < Log.WARN` ignorado)
- Envia breadcrumb via `FirebaseCrashlytics.getInstance().log(...)`
- Registra exceções via `FirebaseCrashlytics.getInstance().recordException(t)`
- Para `ERROR`, registra evento `feature_crash` via `AnalyticsTracker`

**Debug**: `FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)` chamado
em `SignallQApplication.onCreate()` — coleta de crashes desabilitada para não poluir dados
de produção no Firebase Console com crashes de desenvolvimento.

**Release**: coleta habilitada por padrão (SDK default). `mappingFileUploadEnabled = true`
configurado em `build.gradle.kts` — o mapping.txt do R8 é enviado automaticamente ao Firebase
como dependência do task `assembleRelease`/`bundleRelease`.

### Consequências

- **Impacto:** ~70 ocorrências de `Log.*` refatoradas (PR #28)
- **Dependência nova:** Timber (leve, sem maiores dependências)
- **Performance:** Negligenciável (Timber é otimizado para zero-overhead quando desabilitado)
- **Reversão:** Improvável; Timber é padrão de ouro em Android moderno

### Referências

- PR #28: Refactor Log.* para Timber (70 ocorrências)
- Issue #6: Logger
- SIG-227: Validação Crashlytics em release build
- Site: https://github.com/JakeWharton/timber

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-001-timber-logging.md`.*


---

<a id="adr-002"></a>
## ADR-002: Ktlint + Detekt como ferramentas de qualidade automática

**Data:** 2026-05-24  
**Status:** Accepted

### Contexto

O projeto herdava sem instrumentação de qualidade de código. À medida que crescem contributors e features, manutenção manual de estilo e padrões fica insustentável:
- Inconsistência visual (indentação, espaçamento, imports)
- Padrões ruins não detectados (variáveis não usadas, lógica redundante)
- Code review manual lenta e subjetiva

### Decisão

Integrar **Ktlint** (style guide automático para Kotlin) + **Detekt** (análise estática de bugs e anti-patterns).

**Implementação:**
- **Ktlint:** enforcement via plugin Gradle com regras customizadas em `.editorconfig`
  - Auto-fix: `./gradlew ktlintFormat`
  - CI: bloqueia merge se houver violações
  - Regras: baseadas em Kotlin official style guide
  
- **Detekt:** análise estática via plugin Gradle com config em `detekt.yml`
  - CI: relatório de issues (severity high/medium/low)
  - Customizável por baseline (PR #26 estabeleceu baseline de issues conhecidas)

**Benefícios:**
- Estilo consistente sem discussão manual
- Detecção automática de bugs comuns
- CI verde automaticamente limpa quando regras são respeitadas
- Menores PRs (estilo já limpo antes de review)

### Consequências

- **Impacto:** Issue #29 resolveu 55 violações (33 auto-fix + 22 manuais)
- **Workflow:** Developers rodam `./gradlew ktlintFormat` antes de commit
- **Curva de aprendizado:** Mínima; erro de estilo é imediato no IDE
- **Baseline:** Estabelecido em PR #26; novos problemas bloqueiam merge

### Referências

- PR #26: Feat(infra) configurar Detekt + Ktlint + workflow CI (Issue #5)
- Issue #29: Chore(ktlint) resolver 22 violações manuais residuais
- `.editorconfig` — regras customizadas
- `detekt.yml` — configuração Detekt

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-002-ktlint-detekt-quality.md`.*


---

<a id="adr-003"></a>
## ADR-003: DispatcherProvider via injeção de dependência (sem Hilt)

**Data:** 2026-05-24  
**Status:** Accepted

### Contexto

Em arquitetura Android com Kotlin Coroutines, `Dispatchers.IO`, `Dispatchers.Main` etc. são frequentemente hardcoded em serviços e use cases. Isso causa:
- Dificuldade em testes (não dá para mockear dispatcher em testes unitários)
- Acoplamento forte com implementação nativa do Android
- Impossível injetar custom dispatcher para debug/profiling

Historicamente, projetos resolvem isso com Hilt + `@Provides`, mas o SignallQ decidiu usar injeção manual (sem DI framework).

### Decisão

Criar objeto `DispatcherProvider` com interface:
```kotlin
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}
```

Instâncias:
- **Production:** `DefaultDispatcherProvider` (mapeia para `Dispatchers.IO`, etc.)
- **Testing:** `TestDispatcherProvider` (usa `StandardTestDispatcher` ou equivalente)

**Uso:**
- Serviços e use cases recebem `DispatcherProvider` via construtor
- Usam `dispatcherProvider.io` em `launch(dispatcherProvider.io) { }`

**Benefícios:**
- Testability: testes injetam `TestDispatcherProvider`
- Flexibilidade: swappable em qualquer ambiente
- Sem framework DI (mantém simplicidade)

### Consequências

- **Impacto:** Refactor estrutural em MainViewModel e serviços que usam Coroutines
- **Boilerplate:** Minimal (constructor injection straightforward)
- **Performance:** Zero overhead (object é singleton)
- **Issue relacionada:** #7 (DispatcherProvider)

### Referências

- Issue #7: Feat(infra) abstrair Dispatchers com DispatcherProvider
- `TECNICO.md` Seção 3.2 — Injeção: "manual por construtor"

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-003-dispatcher-provider-di.md`.*


---

<a id="adr-004"></a>
## ADR-004: Arquitetura multi-módulo Android (app + core* + feature*)

**Data:** 2026-05-24  
**Status:** Accepted

### Contexto

O projeto SignallQ Android cresceu de um módulo único (app) para múltiplos módulos conforme features se acumulavam. Sem limite de dependency, o codebase virou um monolito acoplado onde:
- Mudança em uma classe quebrava múltiplos lugares
- Testes de feature isolada precisavam de toda a app context
- Compilação incremental ficava lenta
- Reutilização de código entre projetos era impossível

### Decisão

Adotar **arquitetura multi-módulo baseada em tipo**:

**Estrutura:**
```
app/                          # App principal, ponto de entrada
├─ build.gradle
├─ AndroidManifest.xml

core-common/                  # Utilities, extensões, constants
├─ exceptions/
├─ extensions/
├─ constants/

core-database/               # Banco de dados (Room, DAOs)
├─ entities/
├─ dao/
├─ migrations/

core-network/               # APIs, networking, DTOs
├─ models/
├─ interceptors/
├─ services/

feature-home/               # Feature Home (UI + lógica)
├─ ui/
├─ viewmodel/
├─ repository/

feature-auth/               # Feature Auth (UI + lógica)
├─ ui/
├─ viewmodel/
├─ repository/
```

**Regras de dependency:**
- `app` → pode depender de `core-*` e `feature-*`
- `feature-*` → podem depender de `core-*`
- `core-*` → dependem APENAS de `core-*` (ou nenhuma)
- `feature-*` → NÃO dependem de `feature-*` (sem ciclos)

**Benefícios:**
- Compilação incremental: mudança em `core-database` não recompila `feature-home`
- Testabilidade: `feature-home` pode ser testada sem `feature-auth`
- Separação de responsabilidade: clara divisão entre core (infraestrutura) e feature (UI)
- Reutilização: módulos `core-*` podem ser usados em outros projetos Android SignallQ

### Consequências

- **Estrutura:** Refactor em andamento (Issues #3, #7, etc. refletem essa migração)
- **Boilerplate:** Mínimo (Gradle multi-module é padrão; Android Studio gera scaffold)
- **Documentação:** `build.gradle` em cada módulo deve ter comentário sobre dependency rules
- **CI:** GitHub Actions valida dependency graph (opcional, mas recomendado futuramente)
- **Decisões futuras:** Possivelmente converter `core-*` em AAR (Android Archive) para distribuição

### Referências

- Issue #3: Feature home module + navigation
- Issue #7: Feat(infra) abstrair Dispatchers
- `TECNICO.md` — Seção 2: Arquitetura Modular
- Google: Android App Modularity: https://developer.android.com/guide/app-bundle/dynamic-feature-modules

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-004-module-structure-android.md`.*


---

<a id="adr-005"></a>
## ADR-005: Modelo de custo de IA e fallback (free tier)

**Data:** 2026-06-23
**Status:** Accepted

### Contexto

O diagnóstico por IA do SignallQ usa dois provedores, ambos no **free tier**, com degradação graciosa:

1. **Gemini** (Google AI Studio) — provedor primário.
2. **Qwen** (Cloudflare Workers AI) — fallback quando o Gemini atinge o limite do free tier.
3. **Análise local** (`AiFallbackFactory`, engines stateless do app) — quando ambos os provedores estouram o limite. Não há chamada de IA nem registro de uso.

Enquanto a operação permanece dentro do free tier dos dois provedores, **o custo monetário real é zero**.

O painel admin (`signallq-admin-worker`) registrava custo de IA em `ai_usage.cost_usd` usando uma taxa fixa fabricada (`total_tokens * 0.000000035`) quando o app não enviava `cost_usd`. Isso produzia valores financeiros fictícios no dashboard, sem relação com o custo real — que é zero (bug GitHub #234). O app, por sua vez, nunca calcula nem envia `cost_usd` (correto: o worker é a fonte autoritativa de preço).

### Decisão

O **worker** é a fonte autoritativa do custo. O cálculo passa a usar uma tabela de preço por modelo:

- Modelos do free tier (Gemini, Qwen via Workers AI) → **custo 0 por token**.
- Modelo desconhecido / sem tarifa cadastrada → 0 (a arquitetura atual é 100% free tier).
- Adotar um modelo **pago** é uma mudança deliberada: basta adicionar a tarifa em `AI_MODEL_RATE_USD` no worker — sem necessidade de release do app.

`cost_usd` enviado pelo app (hoje sempre nulo) continua tendo precedência se algum dia for preenchido.

O app continua enviando apenas `model` + tokens em `AiUsageIngestPayload`. O provedor é distinguível pelo `model` (`gemini-*` vs `@cf/qwen/*`). A análise local não gera registro em `ai_usage` — a taxa de fallback local é derivável correlacionando `diagnostic_sessions` sem `ai_usage` correspondente (fora do escopo deste ADR).

### Consequências

- O dashboard de custo reflete a realidade: **R$ 0 / $0** enquanto no free tier, em vez de um valor inventado.
- Métricas úteis hoje são **volume por provedor** (chamadas, tokens) e, futuramente, a **taxa de fallback local** — não dólares.
- Quando um provedor pago entrar, o custo passa a ser real automaticamente ao cadastrar a tarifa no worker.

### Implementação

- `integrations/cloudflare/signallq-admin-worker/src/index.ts`: helper `costForModel(model, totalTokens)` + tabela `AI_MODEL_RATE_USD`, substituindo a taxa fixa em `handleIngestAiUsage`.
- Frontend já trata ausência de métricas financeiras agregadas com estado vazio (PR #241, bugs #237/#238) — nenhum valor de custo é hardcoded no painel.

### Referências

- GitHub #234 — `cost_usd` nunca calculado; custo de IA sempre zero no D1.
- `docs/technical/AI_FLOW.md` — fluxo de IA e fallback.
- `docs/03-especificacao-tecnica.md` — workers e ingest do painel admin.

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-005-custo-ia-free-tier-fallback.md`.*


---

<a id="adr-008"></a>
## ADR-008: Features futuras (#952, #951) usam arquitetura D1-only, sem R2/KV/Cron Trigger/Queue

**Data:** 2026-07-16
**Status:** Accepted

### Contexto

O lote de execução do squad de 2026-07-16 incluía um grupo "Features futuras já bem adiantadas"
com duas issues épicas:

- **#952** — Motor de diagnóstico remoto no Cloudflare com fallback local versionado. Arquitetura
  original: Worker + **D1** (regras, versões, auditoria) + **KV ou R2** (pacote de ruleset
  publicado e imutável).
- **#951** — Diretório remoto de provedores com canais oficiais e atualização automática.
  Arquitetura original: Worker + **D1** (cadastro/canais/aliases/ASN/auditoria) + **R2** (logos e
  assets de marca) + **Cloudflare Cron Trigger** (revisão mensal) + **Cloudflare Queue**
  (enriquecimento e revalidação assíncronos).

Ambas as issues já se declaram explicitamente como épico ("quebrar em issues menores antes da
implementação", "não deve ser implementada em um único PR") — este ADR não substitui essa quebra,
só resolve o bloqueio de custo/arquitetura que impedia sequer começar a planejar a quebra.

Luiz decidiu (2026-07-16, conversa) que qualquer componente que introduza **custo novo** de
infraestrutura deve ser cortado, mantendo uma solução alternativa via **D1** (já em uso no
projeto, sem custo adicional).

### Decisão

Cortar todo componente Cloudflare pago/novo além do D1 já existente:

#### #952 — Motor de diagnóstico remoto

- ~~KV ou R2 para o pacote de ruleset publicado~~ → ruleset publicado vira uma linha em tabela D1
  (`rulesets`: versão, JSON do ruleset, status de publicação, metadados, auditoria). O Worker lê a
  versão ativa direto do D1 em vez de resolver um ponteiro KV/objeto R2.
- Resto da arquitetura proposta no corpo da issue (contrato de snapshot, formato declarativo de
  regra, shadow mode, rollout gradual, kill switch, testes dourados compartilhados) não depende de
  KV/R2 e continua válido sem alteração.

#### #951 — Diretório remoto de provedores

- ~~R2 para logos e assets de marca~~ → o cadastro guarda apenas a **URL do logo oficial** como
  coluna em D1 (`providers.logo_url` ou equivalente); o app carrega a imagem direto da fonte
  cadastrada. Isso remove o controle de proporção/otimização/versionamento de asset que o design
  original tinha via R2 — fica como evolução futura se o custo for aprovado depois.
- ~~Cloudflare Cron Trigger mensal~~ → a rotina de revisão periódica de provedores desatualizados
  sai do MVP. Fica como acionamento manual (endpoint administrativo chamado sob demanda) até uma
  decisão futura sobre automação.
- ~~Cloudflare Queue para enriquecimento assíncrono~~ → o fluxo de enriquecimento (buscar
  domínio/SAC/WhatsApp oficiais quando um provedor novo aparece) sai do MVP como automação. Vira
  candidato manual revisado no Console, sem fila de background.

### Consequências

- Nenhuma infraestrutura Cloudflare nova além do D1 (já usado pelo `signallq-admin-worker`) é
  necessária para viabilizar as duas features — sem custo novo de armazenamento (R2/KV) nem de
  automação (Cron/Queue).
- **#951 perde a característica central de "diretório vivo com atualização automática"** no MVP —
  a atualização mensal e o enriquecimento assíncrono por volume de detecção (o coração da proposta
  original) ficam fora até uma decisão futura de custo. O que resta no MVP é essencialmente um
  cadastro D1 consultável por API, com curadoria manual via Console — mais próximo de uma evolução
  do catálogo local atual do que do "diretório vivo" descrito no corpo da issue.
- Assets de logo carregados diretamente da fonte oficial (sem cópia pro R2) reintroduzem o risco de
  hotlink/link quebrado que o desenho original com R2 evitava explicitamente (regra 4 do corpo da
  issue: "não fazer hotlink para o site da operadora") — precisa ser resolvido na quebra em
  subissues (ex.: fallback visual quando a URL externa falhar).
- Ambas as issues continuam sendo épicos multi-fase. Este ADR não autoriza implementação direta —
  a quebra em subissues por fase continua obrigatória antes de qualquer PR de código.

### Referências

- #952 — Motor de diagnóstico remoto no Cloudflare (comentário de 2026-07-16 registra o desvio
  deste ADR).
- #951 — Diretório remoto de provedores (comentário de 2026-07-16 registra o desvio deste ADR).
- `.claude/CLAUDE.md`, seção "Autonomia dos Agentes" — custo novo e alteração arquitetural
  relevante exigem aprovação do Luiz; esta decisão já foi aprovada por ele em conversa.

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-008-grupo6-features-futuras-d1-only.md`.*


---

<a id="adr-009"></a>
## ADR-009 — Vocabulário canônico de issue de diagnóstico (Android x Admin)

**Data:** 2026-07-16
**Status:** Aceito

### Contexto

A auditoria de 2026-07-10 encontrou 3 vocabulários diferentes coexistindo para o mesmo conceito
("motivo de falha" / issue de diagnóstico) no SignallQ Admin (ver issue GH#881):

1. O tipo `DiagnosisIssue` em `SignallQ Admin/src/types/diagnostics.ts` — vocabulário em inglês
   (`wifi_signal_weak`, `bufferbloat_upload`, `dns_latency_high`, `mobile_congestion_suspected`,
   `gateway_slow`, `packet_loss`, `upload_bottleneck`, `unknown`), nunca correspondeu ao que o
   Android envia.
2. O dicionário `ISSUE_LABELS` em
   `SignallQ Admin/src/features/diagnostics/components/FailureReasonsPanel.tsx` — vocabulário
   em português snake_case (`sinal_fraco`, `alta_latencia`, `falha_dns`, `jitter_alto`,
   `perda_de_pacotes`, `upload_lento`, `download_lento`, `problema_fibra`,
   `gateway_inacessivel`, `bufferbloat`, `interferencia_canal_wifi`, `problema_banda`).
3. O que o Android realmente envia via `idParaIssueLabel()`
   (`android/feature/diagnostico/src/main/kotlin/io/veloo/app/kotlin/feature/diagnostico/ingest/AdminIngestPayloads.kt`).

### Decisão

**O vocabulário canônico é o vocabulário 2** (`ISSUE_LABELS` do `FailureReasonsPanel.tsx`), porque
já é exatamente o output real de `idParaIssueLabel()` no Android — confirmado por leitura direta
do código-fonte (`AdminIngestPayloads.kt:31-45`), não por suposição.

Vocabulário canônico (12 categorias + 2 estados especiais):

| Tag | Label PT-BR | Origem (Android) |
|---|---|---|
| `sinal_fraco` | Sinal fraco | id contém `RSSI`/`SINAL` |
| `alta_latencia` | Alta latência | id contém `LATENCIA`/`LATENCY` |
| `falha_dns` | Falha de DNS | id contém `DNS` |
| `jitter_alto` | Jitter alto | id contém `JITTER` |
| `perda_de_pacotes` | Perda de pacotes | id contém `PACKET`/`PERDA` |
| `upload_lento` | Upload lento | id contém `UPLOAD` |
| `download_lento` | Download lento | id contém `DOWNLOAD` |
| `problema_fibra` | Problema de fibra | id contém `FIBRA`/`GPON` |
| `gateway_inacessivel` | Gateway inacessível | id contém `GATEWAY` |
| `bufferbloat` | Bufferbloat | id contém `BUFFERBLOAT` |
| `interferencia_canal_wifi` | Interferência de canal Wi-Fi | id contém `CANAL`/`CHANNEL` |
| `problema_banda` | Problema de banda | id contém `BAND` |
| `none` | (não é falha — excluído do ranking) | sessão sem issue crítico/atenção |
| `unknown` | Outro problema não classificado | id sem keyword reconhecida, ou dado legado pré-normalização (ex: tags cruas históricas como `"Resposta"`, presentes em produção antes desta padronização) |

`types/diagnostics.ts` (`DiagnosisIssue`) foi atualizado para bater 1:1 com esta tabela.

`SignallQ Admin/src/types/ads.ts` (`DiagnosisIssue` de `ContextualAdOpportunity`, feature de
monetização/ads contextual ainda não implementada com dado real) usa um vocabulário próprio,
diferente deste. Fora do escopo desta decisão — é um contrato especulativo de uma feature futura,
não um espelho do dado real de diagnóstico. Fica registrado aqui para quem for implementar
monetização contextual não reusar esse tipo sem revisar.

### Consequências

- `FailureReasonsPanel.tsx` nunca mais exibe tag crua: qualquer valor fora da tabela acima cai no
  bucket `unknown` ("Outro problema não classificado").
- `none` é excluído do ranking e do total usado no cálculo de percentual — não conta mais como
  "motivo de falha" (correção do double-counting documentado também no backend, GH#765,
  `signallq-admin-worker/src/index.ts` `NON_ISSUE_LABELS`).
- Se o Android introduzir uma nova categoria de issue local (novo `id` de regra em
  `FindingEngine.kt`) sem keyword reconhecida por `idParaIssueLabel()`, ela cai em `unknown` dos
  dois lados — não quebra a UI, mas fica sem rótulo específico até alguém atualizar
  `idParaIssueLabel()` (Android) e `ISSUE_LABELS`/`DiagnosisIssue` (Admin) juntos.
- Mudança de contrato de tipo (`DiagnosisIssue`) é breaking apenas para consumidores TypeScript
  dentro do próprio Admin — não afeta o payload enviado pelo Android nem o schema D1 (o worker já
  armazena `issues` como JSON de strings livres, sem enum/CHECK constraint).

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-009-vocabulario-diagnostic-issue.md`.*


---

<a id="adr-011"></a>
## ADR-011 — Fase 0 do motor canônico de diagnóstico (issue #1228)

**Data:** 2026-07-26
**Status:** Aceito

### Contexto

A issue #1228 é a iniciativa arquitetural guarda-chuva que consolida os classificadores/motores de
diagnóstico duplicados (Speedtest, Internet, Wi-Fi, DNS, fibra, etc.) numa única fonte de verdade
canônica (`core:measurement` → `core:network-context` → `core:classification` → diagnóstico →
recomendação). A própria issue exige que a preparação ("Fase 0") **não altere o comportamento
observável do motor local** — só decisão, congelamento de comportamento atual em teste e definição
de contrato.

A issue #952 (motor de diagnóstico remoto versionado, D1-only) depende desta preparação: sua "Fase
inicial obrigatória" pede explicitamente para "definir e versionar `DiagnosticSnapshot` e
`DiagnosticResult`" antes de qualquer implementação de regra remota.

Este ADR resolve três decisões da Fase 0 de #1228, escopadas para não migrar nada ainda:

1. Se `InternetDiagnosticEngine` passa a consumir `MetricClassifier` já nesta fase.
2. Congelamento de comportamento atual (`InternetDiagnosticEngine`, `SpeedtestQualityClassifier`)
   como testes de caracterização/dourados.
3. Nomeação e versionamento dos contratos `DiagnosticSnapshot`/`DiagnosticResult` que #952 vai
   reusar — incluindo uma colisão de nome real encontrada durante a investigação, mais grave do que
   uma simples duplicata de nome.

### Decisão 1 — Migração de `InternetDiagnosticEngine` para `MetricClassifier`: adiada para Fase 1

`MetricClassifier` (`core/diagnostico/MetricClassifier.kt`) já centraliza os thresholds de
download/upload/latência/jitter/perda/bufferbloat/RSSI/RSRP/RSRQ/SINR/DNS num vocabulário canônico
de 6 valores (`MetricStatus`: excelente/bom/regular/ruim/crítico/inconclusivo). O próprio kdoc do
objeto já registra que `InternetDiagnosticEngine` **ainda não foi migrado**, pendente de issue
própria.

`InternetDiagnosticEngine.avaliar()` usa hoje thresholds literais embutidos (ex.: `lat > 100.0`,
`jit > 20.0`, `dl < 25.0`) que retornam `DiagnosticStatus` (5 valores: ok/info/attention/critical/
inconclusive) — vocabulário técnico interno diferente de `MetricStatus`, sem correspondência 1:1
trivial (`MetricStatus` tem granularidade `excelente`/`bom` que `DiagnosticStatus` não distingue, e
vice-versa `attention` cobre parte do que seriam `regular` e `ruim`).

Migrar agora exigiria: (a) escrever um adapter `MetricStatus → DiagnosticStatus` sem valor
inventado, (b) confirmar que cada um dos ~10 achados do engine (`IN-NORMAL-03` a `IN-NORMAL-09b`)
preserva exatamente o texto e o status hoje visível ao usuário, (c) validar contra os testes
dourados desta mesma Fase 0. Isso é migração real de comportamento, não preparação — contraria a
instrução explícita da issue #1228 de não alterar comportamento observável nesta fase.

**Decisão:** adapter e migração ficam para Fase 1 (Speedtest e pós-Speedtest), como dispatch
dedicado e testado contra os golden tests desta Fase 0. Nesta fase, `InternetDiagnosticEngine`
permanece com seus thresholds literais atuais, inalterados.

### Decisão 2 — Golden tests (testes de caracterização)

`InternetDiagnosticEngineTest.kt` e `SpeedtestQualityClassifierTest.kt` já existiam antes desta
Fase 0, mas cobriam cenários específicos (upload zero, perda de pacotes por tipo de conexão, Wi-Fi
não confiável) sem testar sistematicamente os **valores de borda** de cada threshold — o ponto mais
sensível a regressão silenciosa numa migração futura.

Adicionamos, nos mesmos arquivos, uma seção de testes de caracterização cobrindo a fronteira exata
de cada regra hoje em produção:

- `InternetDiagnosticEngineTest`: fronteiras de perda de pacotes (1.0/3.0), jitter (20.0),
  latência (100.0), bufferbloat (30.0/100.0), upload (5.0), download (25.0), e a prioridade
  upload-zero sobre upload-baixo.
- `SpeedtestQualityClassifierTest`: fronteiras de cada veredito (streaming/gamer/videochamada) nas
  transições good→acceptable→poor, e a ordem de prioridade do gargalo primário
  (packetLoss > bufferbloat > latency > upload > none).

Nenhuma classe de produção foi alterada — só teste novo. Esses testes são o contrato de não-
regressão que qualquer migração futura (Fase 1 em diante) precisa continuar satisfazendo, byte a
byte, até o dia em que uma migração deliberada e testada mude o valor esperado (nesse caso o próprio
teste muda, com justificativa, na mesma PR da migração).

### Decisão 3 — Nomeação e versionamento dos contratos

#### 3.1 `DiagnosticSnapshot` — já existe, já versionado, sem colisão real

`DiagnosticSnapshot` já é um contrato real e versionado: interface TypeScript em
`integrations/cloudflare/signallq-diagnostic-worker/src/contracts.ts:61`, com campo `schemaVersion`
(valor atual: 6, via `DiagnosticSnapshotMapper.REMOTE_DIAGNOSTIC_SNAPSHOT_SCHEMA_VERSION`). O lado
Kotlin não duplica esse nome — `DiagnosticInput` (`core/diagnostico/DiagnosticInput.kt`) é a
estrutura local que `DiagnosticSnapshotMapper.toJson()` serializa para esse contrato, só incluindo
campos presentes (nunca inventa valor pra campo ausente — já é a prática atual).

Comparando campo a campo com o que #952 pede ("tipo e estado da conexão; métricas Wi-Fi; métricas
móveis; download/upload/latência/jitter/perda/bufferbloat; DNS; fibra; contexto sanitizado do
equipamento local; permissões e capacidades relevantes"): `DiagnosticSnapshot` já cobre connection/
wifi/wifiScan/speed/quality/dns/fiber/mobile/historical/gateway. Faltam apenas dois grupos:
contexto sanitizado do equipamento local (`SafeLocalDeviceContext`, já existe em `DiagnosticInput`
mas não é mapeado hoje) e permissões/capacidades.

**Decisão:** não criar um segundo tipo `DiagnosticSnapshot` em Kotlin. #952 estende o contrato
existente (bump de `schemaVersion` para 7+, novos campos opcionais em `contracts.ts` e no mapper),
em vez de inventar um nome ou uma estrutura paralela. Não há colisão a resolver aqui — havia apenas
risco de #952 recriar algo que já existe.

#### 3.2 `DiagnosticResult` — colisão real, confirmada, entre dois conceitos diferentes

Esta é a colisão que a Claudete sinalizou, e é mais séria do que “mesmo nome, mesma coisa,
duplicada”: são **dois conceitos diferentes com o mesmo nome em lados opostos do mesmo sistema**.

- **Kotlin, local, já em produção** (`core/diagnostico/DiagnosticResult.kt`): representa **um
  achado individual** (id, título, status de 5 valores, evidência, mensagem ao usuário,
  recomendação, categoria, `podeConcluir`, `categoriaOrigem`). Sem versionamento de schema. **98
  usos** no código Kotlin (`grep -c` em `android/`) — é o tipo de retorno de todo motor de
  diagnóstico local (`InternetDiagnosticEngine`, `DnsDiagnosticEngine`, `FindingEngine` etc.) e é
  consumido por `DiagnosticReport`, telas, PDF, IA.
- **TypeScript, worker, já em produção** (`contracts.ts:158`): representa o **envelope completo de
  uma avaliação** — `resultSchemaVersion`, `engineVersion`, `rulesetVersion`, `evaluationSource`
  (`REMOTE`/`CACHED_LOCAL`/`BUNDLED_LOCAL`), `overallStatus`, `score`, `confidence`,
  `matchedRules`, `findings` (lista, cada um no formato de `DiagnosticFinding`), `recommendations`,
  `primaryFlow`, `humanSummary`, `evaluatedAt`, `traceId`. Esse é exatamente o "Contrato do
  resultado" que a issue #952 pede — já implementado do lado do worker, campo por campo.

O análogo Kotlin mais próximo do achado individual do TS não é `DiagnosticResult` (TS), é
`DiagnosticFinding` (TS) — que também não tem correspondência Kotlin ainda.

Se #952 seguir o caminho óbvio de espelhar o nome do contrato remoto (`DiagnosticResult` do TS) num
novo tipo Kotlin para o avaliador local, ele colide de frente com o `DiagnosticResult` Kotlin já
existente e usado em 98 lugares — duas classes completamente diferentes competindo pelo mesmo nome
no mesmo módulo (`core:diagnostico` é o destino natural de ambas).

**Decisão:**

- O `DiagnosticResult` Kotlin existente **não é renomeado** nesta fase nem em nenhuma fase próxima
  sem tarefa dedicada — 98 usos, mudança de contrato público, fora do escopo de "não alterar
  comportamento observável" e da regra de higiene sobre renomeação em massa (seção 9 da regra de
  higiene do repo).
- O tipo novo que #952 vai introduzir em Kotlin para espelhar o envelope de avaliação do TS
  **não pode se chamar `DiagnosticResult`**. Nome recomendado: **`DiagnosticEvaluation`** —
  consistente com o campo `evaluationSource` que já existe no contrato TS, com o endpoint
  `POST /diagnostic/evaluate`, e sem colidir com nada hoje existente em Kotlin ou TypeScript.
  Alternativas consideradas e descartadas: `DiagnosticVerdict` (colide conceitualmente com a
  propriedade computada `DiagnosticReport.veredito`, que já existe e significa outra coisa —
  texto curto "Excelente/Bom/Regular/Fraco"), `DiagnosticResultEnvelope` (nome genérico demais,
  viola a convenção de nomes vagos da regra de higiene), `DiagnosticOutcome` (vago).
- O lado TypeScript **mantém o nome `DiagnosticResult`** como está — já é consumido por
  `diagnostic-engine.ts`, `diagnostic-report.ts`, `diagnostic-ai.ts` em produção; renomear ali é um
  refactor não relacionado, maior e sem benefício imediato. A assimetria de nome entre os dois lados
  (TS `DiagnosticResult` ↔ Kotlin `DiagnosticEvaluation`) fica documentada aqui exatamente para que
  ninguém, ao implementar #952, tente "corrigir" a assimetria renomeando o lado errado sem revisar
  este ADR primeiro.
- `DiagnosticReport` (Kotlin, `core/diagnostico/DiagnosticReport.kt`) continua sendo a agregação
  local de UI (todos os `DiagnosticResult` por categoria + `decisao` + score + veredito) — não é
  schema-versionado, não é o contrato de fio (wire contract), e não deve ser confundido com
  `DiagnosticEvaluation`. Reconciliar os dois (ou decidir que `DiagnosticEvaluation` é a fonte e
  `DiagnosticReport` vira derivado dele) é decisão de #952/Fase 1, não desta Fase 0.

#### 3.3 Regra de versionamento (aplicável a `DiagnosticEvaluation` quando #952 o implementar)

- Versão via **campo** (`schemaVersion: Int`, seguindo o padrão já usado por
  `REMOTE_DIAGNOSTIC_SNAPSHOT_SCHEMA_VERSION` e por `resultSchemaVersion`/`engineVersion`/
  `rulesetVersion` do TS), nunca por sufixo no nome da classe (`V2`, `Final` etc. são proibidos pela
  regra de higiene, seção 6).
- Campo novo nasce opcional (`?`/nullable). Ausência de dado nunca vira valor inventado (`0`,
  string vazia) — já é o padrão em `DiagnosticSnapshotMapper` e deve continuar.
- Versão incompatível é rejeitada de forma controlada pelo consumidor, não silenciosamente
  truncada.

### Consequências

- Nenhum comportamento observável do motor local mudou nesta fase — só teste novo e documentação.
- `InternetDiagnosticEngine`/`SpeedtestQualityClassifier` agora têm cobertura de fronteira que
  qualquer migração futura (Fase 1) precisa respeitar ou justificar explicitamente ao quebrar.
- #952 tem um nome resolvido (`DiagnosticEvaluation`) para o tipo Kotlin que vai introduzir, evitando
  colisão descoberta tarde (ex.: durante implementação, com testes/imports já escritos).
- Dívida registrada, não resolvida aqui: `DiagnosticFinding` (TS) ainda não tem equivalente Kotlin
  nomeado; quando #952 precisar dele, aplicar o mesmo cuidado de nomeação deste ADR antes de
  escolher um nome.

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-011-fase0-motor-canonico-diagnostico.md`.*


---

<a id="adr-012"></a>
## ADR-012 — Fase 3 de #1228: `executionId`/`rulesVersion` em `MedicaoEntity`

**Data:** 2026-07-31
**Status:** Aceito

### Contexto

A auditoria completa da Fase 0 de #1228 (PR #1516, `docs/02-arquitetura.mdAUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`)
confirmou o achado **P0-9**: nenhuma linha de `MedicaoEntity` carregava `executionId` nem
`rulesVersion` — o requisito não-negociável da issue #1228 ("mudança futura de regra não
reescreve silenciosamente o significado de resultados antigos") estava violado. A mesma
auditoria confirmou **P0-3**: `LaudoScreen.gerarECompartilharLaudo()` podia combinar métricas de
uma execução de speedtest com o veredito/recomendação de um diagnóstico posterior e não
relacionado — `executionId` hardcoded para `""`.

Esta é a **Fatia 3** proposta na Parte 9 daquele documento — a próxima fatia recomendada, por não
depender de nenhuma decisão de produto pendente (ao contrário da Fatia 4, bloqueada pela decisão
da issue #1466) e por ser a base de dados que qualquer fatia futura de correção precisa para ser
auditável.

### Causa-raiz confirmada

Auditoria de código real (não só documento) revelou dois problemas distintos, ambos corrigidos
nesta fatia:

1. **`executionId` já existia parcialmente (GH#1221/#1225), mas nunca chegava ao banco.**
   `ResultadoSpeedtest.executionId` já é gerado uma única vez no início de
   `ExecutorSpeedtestCloudflare.executar()` (UUID) e já é usado corretamente por
   `ResultadoPdfGenerator`. Porém `SpeedtestPersistenceCoordinator` (responsável único por
   persistir `MedicaoEntity`) nunca mapeava esse campo — a entidade Room simplesmente não tinha
   onde guardá-lo. `DiagnosticInput`/`DiagnosticReport` (o pipeline de diagnóstico) também não
   carregavam identidade de execução nenhuma — o diagnóstico nunca sabia a qual execução
   pertencia.
2. **`LaudoScreen` combinava duas fontes vivas sem checagem.** `gerarECompartilharLaudo()` lia
   métricas de `ultimaMedicao` (última linha do Room) e veredito/recomendação de
   `snapshotDiagnostico.relatorio` (estado em memória) — sem qualquer garantia de que os dois
   pertenciam à mesma execução. Um diagnóstico de Wi-Fi rodado depois de um speedtest antigo (ex.:
   o fluxo de fibra em `MainViewModel.kt`, que dispara diagnóstico ao completar, usando a "última
   medição" do banco) podia contaminar o Laudo com veredito de uma execução diferente das
   métricas exibidas.

### Decisão 1 — `executionId`/`rulesVersion` são aditivos, não substituem `MedicaoEntity.id`

`MedicaoEntity.id` continua sendo o PK único gerado por linha (`UUID.randomUUID()` no momento da
persistência, sem mudança). `executionId` é um campo **novo e distinto**: representa a execução
de origem (mesma identidade usada por resultado/diagnóstico/IA/recomendação/PDF), gerada uma única
vez no **início** da execução do speedtest — não no momento de salvar a linha. Os dois raramente
divergem no caminho feliz, mas são conceitos diferentes: `id` é identidade da LINHA persistida,
`executionId` é identidade da EXECUÇÃO que a originou.

### Decisão 2 — `executionId` nunca é gerado na camada de persistência/diagnóstico

Nenhuma classe tocada nesta fatia (`SpeedtestPersistenceCoordinator`, `DiagnosticRunner`,
`DiagnosticOrchestrator`, `LaudoScreen`) gera um novo `executionId`. Todas propagam o valor já
existente:

- `ExecutorSpeedtestCloudflare.executar()` continua sendo a ÚNICA fonte que gera o UUID (GH#1221,
  inalterado nesta fatia).
- `DiagnosticInput.executionId` (novo campo, default `""`) é preenchido pelo chamador (`app`) a
  partir de `ResultadoSpeedtest.executionId` (fluxo pós-speedtest) ou de `MedicaoEntity.executionId`
  já persistido (fallback "última medição", quando não há resultado em memória).
- `DiagnosticReport.executionId` (novo campo) é preenchido por `DiagnosticRunner.run()` copiando
  `input.executionId` — nunca inventado.
- `MedicaoEntity.executionId` é preenchido por `SpeedtestPersistenceCoordinator` a partir de
  `resultado.executionId` no momento de montar a entidade.
- `RelatorioDiagnosticoSnapshot.executionId` (Laudo) usa `ultimaMedicao.executionId` — a fonte real
  dos números exibidos.

### Decisão 3 — `rulesVersion`: constante canônica única, não um valor por chamador

Criada `DiagnosticRulesVersion` (`core/diagnostico/DiagnosticRulesVersion.kt`), com
`CURRENT = "diagnostic-rules-v1"`. É a única fonte de verdade — nunca recalculada por
tela/mapper/presenter. Critério de incremento documentado no kdoc do próprio objeto (bump quando
qualquer threshold/vocabulário/lógica de `FindingEngine`/catálogo de recomendação REC-01..14
mudar de forma observável; não incrementa para copy/refactor sem mudança de comportamento).

Não reaproveitamos `DiagnosticEvaluation.rulesetVersion` (`Int`, ADR-011 seção 3.2) — é um conceito
do envelope remoto do worker (`signallq-diagnostic-worker`), ainda órfão em produção, versionando
o ruleset REMOTO. `DiagnosticRulesVersion.CURRENT` versiona o motor LOCAL
(`DiagnosticRunner`/`MetricClassifier`/`InternetDiagnosticEngine`/`SpeedtestQualityClassifier`).
Os dois podem evoluir de forma independente; não há necessidade de unificá-los nesta fatia.

### Decisão 4 — Contrato `DiagnosticExecutionContext`

Criado `core/diagnostico/DiagnosticExecutionContext.kt`: agrupa `executionId` + `rulesVersion` +
`startedAtEpochMs` (não `java.time.Instant` — `core/diagnostico` não tem core library desugaring e
o app declara `minSdk 24`). O factory `iniciar(executionId, ...)` nunca gera um id novo — recebe
um já existente e falha explicitamente (`IllegalArgumentException`) se vazio. Kotlin puro, zero
dependência de Android/Compose — reaproveitável pelo SignallQ Pro no futuro (mesmo princípio já
registrado na issue #1228: capacidades técnicas compartilháveis, UI/regra de negócio separadas por
produto).

### Decisão 5 — Migração Room 15→16 (aditiva)

`ALTER TABLE medicao ADD COLUMN executionId TEXT NOT NULL DEFAULT ''` seguido de
`UPDATE medicao SET executionId = 'legacy-' || id` (usa o próprio PK, já único, evitando
qualquer colisão) e `ALTER TABLE medicao ADD COLUMN rulesVersion TEXT NOT NULL DEFAULT
'legacy-unversioned'`. Nenhuma coluna existente é alterada, nenhuma linha é perdida. Mesmo padrão
de migração aditiva já usado 14 vezes no schema (`CoreDatabaseModulo.kt`).

`ConnectivityDiagnosisHistoryEntity` **não foi tocada** nesta fatia — o documento de auditoria
citava as duas tabelas como candidatas, mas o requisito desta fatia (definido por escopo
explícito) restringiu-se a `MedicaoEntity`. Registrado como possível fatia futura, não bloqueante.

### Decisão 6 — Correção do Laudo (P0-3): nunca combinar, sempre avisar

`LaudoScreen.kt` ganha `diagnosticoCorrespondeAMedicao(relatorioExecutionId, medicaoExecutionId)`
(regra pura: ambos os lados precisam ser não-vazios E iguais; "desconhecido" nunca vira "match"
por omissão) e `montarSnapshotLaudo(...)` (função pura, extraída de `gerarECompartilharLaudo`,
testável sem Context/Compose). Quando não há correspondência:

- as métricas continuam vindo de `ultimaMedicao` (fonte real dos números, comportamento mantido);
- veredito/resumo/recomendação da decisão são suprimidos;
- o resumo exibido informa explicitamente "Diagnóstico não disponível para esta medição" — nunca
  fica vazio de forma enganosa, nunca busca automaticamente um diagnóstico de outra execução.

O mesmo gate foi aplicado ao banner on-screen do `LaudoScreen` (não só ao PDF exportado) — é a
mesma causa-raiz, na mesma tela, e um fix parcial deixaria a tela mostrando dado inconsistente
enquanto o PDF exportado estaria correto.

### Decisão 7 — Retry técnico vs. repetição completa (mesmo `executionId` vs. novo)

Documentado explicitamente (kdoc de `ExecutorSpeedtestCloudflare.executar` já preexistente,
GH#1221/#1225, não alterado nesta fatia): o UUID é gerado uma única vez no topo de `executar()` e
reaproveitado por todas as fases internas da MESMA chamada (retry técnico de uma fase = mesmo
`executionId`). Uma nova chamada completa de `executar()` (repetição disparada pelo usuário) gera
um novo UUID. Coberto por teste de contrato em `SpeedtestPersistenceCoordinatorTest.kt`.

### Consequências

- `MedicaoEntity`, `DiagnosticInput`, `DiagnosticReport` agora carregam identidade de execução
  ponta a ponta: início do speedtest → resultado bruto → diagnóstico → persistência → histórico
  (leitura, sem redesenho de tela) → Laudo/PDF.
- Nenhum threshold, severidade, texto de diagnóstico ou recomendação foi alterado.
- Registros legados (anteriores à migração) ficam explicitamente marcados como
  `legacy-{id}`/`legacy-unversioned` — nunca reclassificados silenciosamente como se pertencessem
  à regra atual.
- Dívidas registradas, não resolvidas aqui: (1) `ConnectivityDiagnosisHistoryEntity` sem
  `executionId`/`rulesVersion` próprios (fora do escopo desta fatia); (2)
  `DiagnosticDivergenceReporter`/`RemoteDiagnosticRepository` (shadow-mode) ainda gera um
  `executionId` próprio (UUID por comparação, conceito de trace de telemetria, não de medição) —
  poderia futuramente reaproveitar `input.executionId` para melhor correlação, mas isso é
  telemetria de observabilidade, fora do requisito desta fatia; (3) descoberto de passagem:
  `core/database` não tinha `androidTestImplementation(libs.kotlinx.coroutines.test)`, quebrando a
  compilação do source-set `androidTest` inteiro (`ChatSessionDaoTest.kt`, pré-existente) —
  corrigido nesta fatia por estar na mesma área tocada (novo teste de migração instrumentado).

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-012-fase3-executionid-rulesversion.md`.*


---

<a id="adr-013"></a>
## ADR-013 — Fatia 4 de #1228: plano técnico para latência/perda de pacotes/upload (`MetricClassifier` vs. `InternetDiagnosticEngine`)

**Data:** 2026-07-31
**Status:** Aceito (arquitetura) — implementação de código bloqueada até decisão de produto (ver "Decisão pendente" abaixo)

### Contexto

A issue [#1466](https://github.com/buildea-labs/signallq/issues/1466) documenta uma divergência
real e já confirmada em produção (achado **P0-1** da auditoria completa de #1228,
`docs/02-arquitetura.mdAUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`, Parte 8): `MetricClassifier`
(tabela genérica, fonte `/regras-diagnostico-rede`) e `InternetDiagnosticEngine` (limiares de
produto documentados — Anatel RQUAL para latência, piso de videoconferência para upload, piso de
jogos/chamadas para perda de pacotes) têm réguas diferentes para as mesmas três métricas:

| Métrica | `InternetDiagnosticEngine` | `MetricClassifier` | Coincide? |
|---|---|---|---|
| Latência | achado a `>100.0 ms` (IN-NORMAL-05) | tabela `<100/<=150/<=200/>200` | Não |
| Perda de pacotes | achado a `>=1.0%`/`>=3.0%` (IN-NORMAL-07/07b) | tabela `<0.5/<=2.0/>2.0` | Não |
| Upload | achado a `<5.0 Mbps` (IN-NORMAL-04) | tabela `>=20/>=10/>=3/>=1/<1` | Não |

Confirmado por leitura de código nesta sessão (`InternetDiagnosticEngine.kt:51-196`): os três
achados **não foram migrados de propósito** (ADR-011/Fase 1), com comentário inline no próprio
arquivo apontando para #1466. Jitter/download/bufferbloat já migraram e coincidem exatamente com
`MetricClassifier` (fronteiras idênticas, ver #1466).

Confirmado também que **ambas as réguas são consumidas em produção hoje**, não é um caso de uma
delas ser código morto:

- `InternetDiagnosticEngine` alimenta `FindingEngine`/`ScoreEngine` → banner de diagnóstico,
  Diagnóstico Guiado, Score.
- `MetricClassifier.classificarLatencia/classificarPerdaPacotes` são chamados diretamente por
  `ModoGamerEngine.kt` (10 pontos de chamada, dimensões "Tempo de resposta com a rede ocupada" e
  "Falhas estimadas na conexão") e pelos cards de métrica de `ResultadoVelocidadeScreen.kt` — a
  mesma tela cujo banner vem do engine, daí o P0-1 ser visível **na mesma tela, ao mesmo tempo**.

### Por que isto não é só duplicação de código

As duas réguas não são a mesma regra implementada duas vezes (ao contrário de bufferbloat/P1-4,
já resolvido na Fatia 6). São **vocabulários estruturalmente diferentes**:

- `InternetDiagnosticEngine` produz **achados binários/ternários** (`DiagnosticStatus`: ok/attention/
  critical) com um único corte de negócio por métrica — desenhado para responder "isto merece
  virar um card de alerta?".
- `MetricClassifier` produz uma **classificação de qualidade em 6 níveis** (`MetricStatus`:
  excelente/bom/regular/ruim/crítico/inconclusivo) — desenhado para responder "que nota esta
  métrica tira, isoladamente?".

Colapsar os dois em um único conjunto de números, sem antes decidir o que cada consumidor
realmente precisa (achado vs. nota), arrisca perder a semântica de um dos dois lados mesmo que os
números batam.

### Decisão pendente (produto, fora do escopo desta ADR)

Não decidido nesta ADR — depende de validação de produto (Claudete/Luiz) antes de qualquer PR de
código, exatamente como a issue #1466 já registrava:

1. Aceitar a divergência como definitiva e documentá-la como comportamento pretendido (duas réguas,
   dois propósitos).
2. Fazer um lado prevalecer sobre o outro numericamente (ex.: `MetricClassifier` passa a usar os
   cortes do engine para estas 3 métricas) — muda comportamento observável de quem hoje lê
   `MetricClassifier` (`ModoGamerEngine`, cards do Resultado).
3. Manter os dois vocabulários, mas expressos como perfis nomeados do mesmo motor canônico (ver
   "Decisão de arquitetura" abaixo) — não resolve sozinho qual card muda de cor, só remove a
   duplicação de implementação.

Esta ADR **não escolhe entre 1/2/3 no nível de valores numéricos**. O que ela decide é a forma
técnica que qualquer uma dessas opções vai assumir no código, para que a implementação (Fatia 4b)
possa começar assim que a decisão de produto acima for tomada — sem redesenho de arquitetura no
meio do caminho.

### Decisão de arquitetura — perfis de classificação nomeados dentro de `MetricClassifier`

`MetricClassifier` ganha um conceito explícito de **perfil de regra** por métrica, em vez de uma
única tabela implícita. Formato proposto (nomes sujeitos a ajuste na implementação real, não
compromissados aqui):

```kotlin
enum class PerfilRegraLatencia { QUALIDADE_GENERICA, ACHADO_PRODUTO_INTERNET }

fun classificarLatencia(
    latenciaMs: Double,
    perfil: PerfilRegraLatencia = PerfilRegraLatencia.QUALIDADE_GENERICA,
): MetricStatus
```

- `QUALIDADE_GENERICA` preserva a tabela atual (6 níveis, fonte skill) — comportamento de hoje,
  sem mudança, é o default (nenhum chamador existente precisa mudar assinatura).
- `ACHADO_PRODUTO_INTERNET` expõe os cortes hoje hardcoded em `InternetDiagnosticEngine`
  (100ms/1%/3%/5Mbps) através da MESMA função pública, mapeados para o vocabulário de 6 níveis
  mais próximo (ex.: corte único de achado vira fronteira `bom`/`regular` — mapeamento exato é
  decisão de implementação da Fatia 4b, não desta ADR).
- `InternetDiagnosticEngine` passa a chamar `MetricClassifier.classificarLatencia(lat,
  PerfilRegraLatencia.ACHADO_PRODUTO_INTERNET)` em vez de reimplementar o corte inline — elimina a
  duplicação técnica (o motivo original de #1228) sem forçar as duas réguas de produto a virarem
  uma só.
- Repetir o mesmo padrão para perda de pacotes e upload (`PerfilRegraPerdaPacotes`,
  `PerfilRegraUpload`).

Por que perfil nomeado em vez de um segundo classificador separado (ex. `core/classification`
proposto no corpo original de #1228): os valores realmente pertencem ao mesmo domínio (mesma
métrica bruta, mesma unidade), só divergem no **corte**. Um enum de perfil no mesmo arquivo é menor
superfície de manutenção que duplicar toda a estrutura de `MetricClassifier`/`MetricStatus` — e
mantém válido o princípio "uma métrica, uma função de classificação" (ela só passa a aceitar
parâmetro de perfil, não deixa de ser uma função só).

### Rollout proposto (não iniciado)

- **Fatia 4a (baixo risco, não bloqueada por decisão de produto):** implementar o enum de perfil +
  `ACHADO_PRODUTO_INTERNET` espelhando exatamente os cortes atuais do engine, migrar
  `InternetDiagnosticEngine` para chamar `MetricClassifier` com esse perfil. **Zero mudança
  observável** — mesmo padrão já usado em jitter/download/bufferbloat (Fase 1/ADR-011). Testes
  dourados de `InternetDiagnosticEngineTest.kt` continuam verdes sem alteração de asserção.
- **Fatia 4b (bloqueada pela decisão de produto acima):** decidir se `ModoGamerEngine` e os cards
  de `ResultadoVelocidadeScreen` continuam em `QUALIDADE_GENERICA` (comportamento atual mantido,
  opção 1 formalizada) ou migram para `ACHADO_PRODUTO_INTERNET` (opção 2, muda cor/rótulo visível
  nesses dois consumidores). Esta fatia só começa depois de resposta explícita de Claudete/Luiz na
  issue #1466.

### Consequências

- Nenhum threshold muda nesta ADR. Nenhum arquivo de produção é tocado.
- #1466 permanece aberta — esta ADR só documenta o plano técnico e destrava a Fatia 4a (que não
  depende de decisão de produto) assim que for priorizada.
- A Fatia 4b continua explicitamente bloqueada até decisão de produto, conforme já registrado na
  Parte 9 da auditoria (`AUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`) e no item 8 do fechamento
  desta rodada.

### Referências

- Issue [#1466](https://github.com/buildea-labs/signallq/issues/1466)
- Issue [#1228](https://github.com/buildea-labs/signallq/issues/1228)
- `docs/archive/decisoes/ADR-011-fase0-motor-canonico-diagnostico.md`
- `docs/02-arquitetura.mdAUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`, achado P0-1 e Fatia 4

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-013-fatia4-unificacao-latencia-perda-upload.md`.*


---

<a id="adr-016"></a>
## ADR-016 — Portfólio Buildea: SignallQ e Linka

- **Status:** Aceito
- **Data:** 2026-08-15
- **Autor:** Luiz (CEO)
- **Consolida:** ADR-010 (monetização consumer grátis/ads), ADR-014 (squad canônico em `ai-governance/agents/`), ADR-015 (plataformas Android+Web). Todos removidos do disco na Fase 3 do épico #1623 em 2026-08-15 — git preserva por SHA.
- **Reforça:** ADR-007 (iOS adiado — também consolidado neste ADR na Fase 3).

### Contexto

O portfólio Buildea acumulou hipóteses e produtos derivados ao longo do tempo:

- **SignallQ Consumer** — Android + PWA em `signallq-web`, plataforma canônica.
- **SignallQ Pro** — 13 módulos Gradle `:pro:*`, on hold desde 2026-08-06.
- **SignallQ ISP** — nome tentativo, nunca chegou a produto.
- **Nethal** — mencionado em decisão de consolidação 7ALabs como "segundo produto possível", nunca definido.
- **iOS** — descontinuado em 2026-07-04 (ex-ADR-007, hoje consolidado aqui), reforçado como permanente.
- **Múltiplos ADRs de squad, monetização e plataforma** — divergentes, com sobreposições.

Sem consolidação, cada retomada de discussão ("e se voltarmos com o Pro?", "e o Nethal?", "e o desktop?") reabre esforço estratégico em cima de algo que já foi implicitamente decidido. Este ADR fecha o portfólio.

### Decisão

#### Dois produtos ativos, comerciais, sob o guarda-chuva Buildea

**Buildea** é o guarda-chuva comercial. Não é plataforma técnica nem stack — é a entidade que comercializa e opera os produtos.

**SignallQ**
- **Plataformas:** Android (nativo, Kotlin/Compose) + Web (`signallq.com`, PWA em `signallq-web`).
- **Modelo comercial:** freemium com propaganda. Funcionalidade principal (diagnóstico de rede doméstica, IA de diagnóstico, monitoramento passivo) gratuita e sustentada por ads. Recursos pagos podem entrar no futuro sem quebrar a promessa de gratuidade do núcleo.
- **Público:** usuário doméstico brasileiro.
- **Repositórios:** `signallq` (Android + Workers Cloudflare), `signallq-web` (PWA).

**Linka**
- **Plataformas:** exclusivamente ecossistema Apple (iOS, iPadOS, macOS conforme escopo do produto — não Android, não Web-agnóstico).
- **Modelo comercial:** pago desde o lançamento. Speedtest é o único recurso free (isca de aquisição). Todo o resto é pago.
- **Público:** usuário Apple que quer diagnóstico de rede profissional.
- **Repositório:** `linka` (a ser criado — foundation preparada na Fase 8 do épico #1623).

Os dois produtos **compartilham** a marca-guarda-chuva Buildea e potencialmente o entendimento de domínio (diagnóstico de rede, thresholds, vocabulário), mas **não compartilham código, squad, skills ou governança operacional**. Cada um é fundação própria.

#### Squads separadas

Cada produto tem sua própria squad, que vive no seu próprio repositório. Não há squad Buildea unificada. A padronização se dá por **contratos operacionais** compartilhados (ex.: formato de handoff, definition of done), não por pessoas.

Composição do squad SignallQ (definida na Fase 1 do épico #1623):

| Papel | Escopo |
|---|---|
| **Claudete** (PM) | Produto, prioridade, roadmap, absorve direção de design/growth via skills |
| **Camilo** (Dev) | Android + Web + Workers + Admin — dev técnico único |
| **Caio** (Revisor) | Gate único de revisão independente |

Design, growth e dados viram **skills invocáveis** (`/design-check`, `/growth-check`, `/analytics-spec`), não agentes permanentes.

Composição do squad Linka: definida quando o repo nascer (Fase 8), com personas próprias — não é replicação do squad SignallQ.

#### Personas dos agentes

Cada agente tem personalidade escrita: tom, palavrão sim/não, matriz de decisão trivial vs escalar, modelo default (Sonnet padrão; Opus para arquitetura/segurança/roadmap; Haiku para trivial/triagem). Objetivo: **melhor custo × benefício em velocidade, custo e qualidade**.

Agentes se dirigem ao Luiz como humanos, sem formalidades. Palavrão, piada, informalidade são permitidos conforme a personalidade — não são obrigatórios nem proibidos.

#### Autonomia

**Agentes decidem sozinhos** todas as questões técnicas dentro do domínio deles: arquitetura de feature, refactor até um módulo, dependência nova dentro do orçamento, escolha de padrão, spec de teste.

**Luiz decide** questões de produto (roadmap, priorização macro, monetização, marca) e as aprovações materiais do [contrato operacional Buildea §3](../../ai-governance/policies/agent-operating-contract.md): estratégia, marca, escopo material, orçamento, lançamento, custo recorrente novo, dado sensível, alteração irreversível, compromisso externo, aceite de risco crítico.

**Decisões triviais de produto** (ex.: ajustar copy de card, escolher entre dois ícones equivalentes, decidir ordem de exibição de menu) ficam com Claudete sem escalar.

#### Governança anti-duplicação

Prevenir código duplicado e reinvenção via **skill executável obrigatória** antes de criar código novo:

- **`/inventario`** — lê `settings.gradle.kts`, catálogo de módulos, exports públicos. Retorna o que já existe.
- **`/verificar-modulo <nome>`** — antes de criar módulo/serviço/componente novo, força justificativa se algo parecido existe.
- **`/check-done`** (piloto #1620 promovido a oficial) — enforcement final antes de merge.

Regra dura mantida: `:feature:*` nunca depende de `:feature:*`. Composição vive em `:core:*` compartilhado ou `:app`. Prioridade **módulos + compartilhamento de função** — nunca monolítico.

#### Produtos descontinuados

Ficam **permanentemente fora** do portfólio Buildea:

- **SignallQ Pro** — 13 módulos `:pro:*` a serem removidos (Fase 4a do épico #1623); docs `pro-onhold/` removidos (Fase 4b); skill `signallq-pro-design` removida (Fase 4b).
- **SignallQ ISP** — nunca foi produto; nome descartado.
- **Nethal** — nunca foi definido; descartado.
- **Qualquer plataforma que não seja Android/Web (SignallQ) ou Apple (Linka)** — iOS via SignallQ, macOS/Windows/Linux desktop, wearables, TV, embedded, extensões de navegador. Reforço permanente do que os antigos ADR-007 e ADR-015 já diziam (ambos consolidados aqui).

Se algum dia essas ideias voltarem, nascem em ADR próprio que supersede este.

### Consequências

#### Imediatas

- ADR-010, ADR-014, ADR-015 — **consolidados** neste ADR. Removidos do disco na Fase 3 do épico #1623 em 2026-08-15; git preserva.
- ADR-007 (iOS adiado) — **consolidado** neste ADR. Removido do disco na mesma Fase 3.
- ADR-006 (workflow squad de 5) — já era superseded desde 2026-08-15; removido do disco na Fase 3.

#### Épico #1623 destranca

- Fase 1 (novo squad de 3 agentes com personalidade) pode começar.
- Fase 3 (consolidar ADRs) pode remover 010, 014, 015 sem re-litigar decisão.
- Fase 4a-b (remover Pro) tem base canônica para justificar remoção.
- Fase 8 (foundation Linka) tem escopo declarado.

#### Cross-repo (follow-ups, não bloqueiam este ADR)

- `signallq-web` — AGENTS.md deve citar ADR-016 e declarar-se como o PWA do SignallQ. PR separada lá.
- `ai-governance/` — política `policies/product-platforms.md` opcional, referenciando este ADR se o Luiz quiser centralizar em nível org.
- `linka` (repo novo) — quando nascer, primeiro ADR local referencia este.

#### O que este ADR NÃO decide

- Stack de implementação do Web (`signallq-web` continua Next.js/PWA por decisão daquele repo).
- Distribuição do Android (Play Store hoje; F-Droid/sideload são decisões separadas).
- Timing de recursos pagos do SignallQ (produto ainda operando 100% free/ads em 2026-08).
- Timing de lançamento do Linka (Fase 8 só prepara foundation).

### Reabertura

Este ADR só é reaberto por novo ADR do Luiz que o supersede. Sem novo ADR:

- Proposta de retomar Pro/ISP/Nethal/derivado → rejeitada de plano; requer business case direto ao Luiz antes de qualquer trabalho técnico.
- Proposta de nova plataforma (desktop, wearable, TV, embedded) → rejeitada de plano; mesma regra.
- Proposta de mudar modelo comercial (SignallQ vira pago, Linka vira freemium) → escalação obrigatória ao Luiz.

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-016-portfolio-buildea.md`.*


---

<a id="adr-017"></a>
## ADR-017 — Motor de diagnóstico e IA migram para o NDS

- **Status:** Aceito
- **Data:** 2026-08-19
- **Autor:** Luiz
- **Substitui:** o papel de `core/diagnostico` (motor local), `ai-diagnosis-worker` (explicação em IA)
  e `signallq-diagnostic-worker` (shadow mode, motor remoto sem IA, nunca promovido).
- **Investigação de origem:** issue [#1742](https://github.com/buildea-labs/signallq/issues/1742)
  (inventário completo de consumidores, gaps de coleta e perguntas em aberto).

### Contexto

O SignallQ acumulou três caminhos paralelos de diagnóstico, nenhum deles unificado:

1. **Motor local** (`core/diagnostico`: `InternetDiagnosticEngine`, `MetricClassifier`,
   `DiagnosticReport`) — roda on-device, síncrono, alimenta praticamente toda a superfície de
   diagnóstico do app (Início, Resultado de Velocidade, Laudo, Histórico, Sinal, Dispositivos).
   Tem dois vocabulários de severidade paralelos (`MetricStatus` de 6 faixas e `DiagnosticStatus`
   de 5 valores) reconciliados via `comSeveridadeConciliada()`.
2. **`ai-diagnosis-worker`** — Worker Cloudflare separado, único call site de produção em
   `MainViewModel.analisarProblema()` via `AiDiagnosisRepository.explainDiagnosis`, só cobre a
   parte de explicação em linguagem natural.
3. **`signallq-diagnostic-worker`** — endpoint `/diagnostic/evaluate`, rodando em shadow mode em
   produção: cópia remota sem IA do motor local, com fallback de 3 níveis
   (`REMOTE`→`CACHED_LOCAL`→`BUNDLED_LOCAL`), nunca promovido a autoritativo, aguardando validação
   de paridade que nunca terminou.

O NDS (Network Diagnostics Service, `network-diagnostics-service.buildealabs.workers.dev`,
`POST /v1/diagnostics/evaluate`) é um serviço externo novo que unifica scoring e explicação em IA
num único contrato modular, recebendo o mesmo tipo de snapshot de rede que o app já coleta
on-device (wifi, wifiScan, speed, dns, fiber).

### Decisão

#### O NDS substitui os três caminhos existentes

- **Motor local** (`core/diagnostico`) é removido depois que todos os consumidores migrarem para
  o NDS. `MetricClassifier` (função pura, sem I/O) migra sua lógica de classificação para o NDS
  também — sem duplicar régua de severidade entre cliente e servidor.
- **`ai-diagnosis-worker`** é descontinuado — a explicação em IA passa a vir do módulo `ai` da
  resposta do NDS.
- **`signallq-diagnostic-worker`** (shadow mode) é descontinuado — o investimento anterior em
  motor remoto sem IA fica absorvido pelo NDS, não mantido em paralelo.

#### Exceção: zero conectividade

O único cenário em que o motor local faria sentido sobreviver é quando o dispositivo está sem
nenhuma rede (não "rede ruim" — sem Wi-Fi e sem dados móveis), porque nesse caso o app não
consegue nem chamar o NDS. A investigação em #1742 confirmou que **esse caminho não existe hoje**
no motor local — o único tratamento especial encontrado (`ExecutorFibra.marcarSemRede()`) evita
consultar o modem local sem rede, mas não é uma classificação de diagnóstico. Decisão: **não
construir isso como feature nova** nesta migração. Se o app ficar sem conseguir diagnosticar em
zero conectividade, isso é um gap conhecido e aceito, não um requisito desta migração.

#### Autenticação

O NDS usa Bearer token estático. Decisão consciente do Luiz: o token fica embutido no cliente
Android, com o risco de extração por engenharia reversa (descompilação do APK) explicitamente
aceito. Isso **não dispensa** a regra de higiene do repositório de nunca versionar segredos em
texto puro — o token entra via `local.properties`/`BuildConfig` (gitignorado), nunca hardcoded em
arquivo `.kt` commitado. Quem implementar a camada de rede (fatia NDS-01) não deve escrever o
valor real do token em nenhum arquivo do repositório; a inserção do valor é manual, feita pelo
Luiz (ou por quem tiver o token) no ambiente local/CI.

#### Sequenciamento com o épico #1647

A migração para o NDS entra **antes** de continuar o épico #1647 (Android 2.0 por fatias
verticais). Da issue #1647, 12 de 28 fatias de topo já fecharam (última: #1659, 2.0.11); nenhuma
fatia nova (#1660 em diante) abre até o NDS estar pronto — evita migrar telas visualmente e ter
que retocar a leitura de dados de novo quando o formato do relatório mudar.

### Contrato conhecido do NDS

Documentado a partir de testes reais (`curl`) e leitura do código-fonte do NDS pelo Luiz — sujeito
a completar conforme a implementação avançar.

**Request** (`POST /v1/diagnostics/evaluate`, `Authorization: Bearer <token>`):

```json
{
  "request_id": "req-12345",
  "app": { "id": "com.buildea.signallq", "version": "2.4.0" },
  "locale": "pt-BR",
  "profile": "gamer",
  "capabilities": ["scoring", "ai", "wifi", "fiber"],
  "connection": { "type": "WIFI", "ssid": "MinhaRede_5G", "bssid": "00:14:22:01:23:45" },
  "wifi": { "rssi": -65, "band": "5GHz", "channel": 36, "linkSpeed": 433, "standard": "802.11ac" },
  "wifiScan": { "channelCongestion": 15, "bestChannel": 149 },
  "speed": { "ping_ms": 151, "jitter_ms": 40, "download_mbps": 300, "upload_mbps": 150, "packet_loss_percent": 2 },
  "dns": { "primary": "8.8.8.8", "responseTime_ms": 35, "hijacked": false },
  "fiber": { "rxPower_dbm": -22, "txPower_dbm": 2.5, "temperature_c": 45, "voltage_v": 3.3 }
}
```

**Resposta de sucesso (200 OK):** modular, um item por módulo avaliado em `results[]`, mais um
`recommendation` de topo (ação única sugerida, ou `null`):

```json
{
  "recommendation": null,
  "results": [
    { "module": "diagnostics.wifi", "module_version": "1.0.0", "request_id": "req-12345", "warnings": [], "missing_inputs": [], "result": { "matched_rules": [] }, "cards": [] },
    { "module": "scoring", "module_version": "1.1.0", "request_id": "req-12345", "warnings": [], "missing_inputs": [], "result": { "score": 50, "veredicto": "regular", "tipo_conexao": "WIFI", "observed_dimensions": 1, "dimensoes": [] } },
    { "module": "ai", "module_version": "1.5.0", "request_id": "req-12345", "warnings": [], "missing_inputs": [], "result": { "tokens_used": 0, "ai_model_used": "copy-catalog", "fallback_used": false, "explanation_source": "copy_catalog", "explanation_status": "catalog_hit", "explanation": { "titulo_amigavel": "...", "resumo_tecnico_traduzido": "..." }, "source_finding_ids": [] } }
  ],
  "traces": [
    { "module": "wifi", "duration_ms": 2, "status": "ok" },
    { "module": "ai", "duration_ms": 15, "status": "ok", "source": "copy_catalog" }
  ]
}
```

Observação importante para o design do cliente: os módulos retornados **não mapeiam 1:1** com a
lista `capabilities` pedida — no teste real, `capabilities: ["scoring", "ai"]` ainda devolveu um
resultado do módulo `diagnostics.wifi`. O cliente deve buscar por `module` na lista `results[]`,
nunca assumir posição fixa ou presença garantida só porque foi pedida em `capabilities`.

#### Contrato canônico (PR [buildea-labs/network-diagnostics-service#12](https://github.com/buildea-labs/network-diagnostics-service/pull/12))

Fecha as lacunas que este ADR listava como pendentes. **Status: PR aberto, CI verde
(`npm run typecheck && npm test`), ainda em draft no repositório do NDS — não confirmado como
implantado em produção.** Tratar como o desenho canônico a seguir; reconfirmar contra o endpoint
real antes de depender dele em produção.

- **Envelope de erro único**, para toda falha pública (`400`, `401`, `429`, `500`, `503`, `504`):
  ```json
  { "error": { "code": "NDS_TIMEOUT", "message": "...", "retryable": true }, "request_id": "..." }
  ```
  Códigos estáveis: `INVALID_INPUT`, `UNAUTHORIZED`, `RATE_LIMITED`, `NDS_TIMEOUT`,
  `AI_UNAVAILABLE`, `SERVICE_UNAVAILABLE`, `NOT_FOUND`, `INTERNAL_ERROR`. `request_id` é gerado
  antes de autenticação/validação, presente mesmo em 4xx/5xx. Substitui o formato flat
  `{error, message}` dos exemplos de `401`/`429` acima — esses exemplos ficam como registro
  histórico do que foi observado antes do contrato formal existir.
- **Timeout de avaliação** configurável via `EVALUATE_TIMEOUT_MS` (padrão 10000ms, mínimo 1000,
  máximo 30000) — estouro devolve `504 NDS_TIMEOUT`, `retryable: true`.
- **`profile` fechado em 5 valores**: `general`, `gamer`, `streaming`, `wfh`, `smarthome`. Valor
  fora do conjunto é rejeitado com `400 INVALID_INPUT`.
- **`capabilities` redefinido como evidência**, não mistura mais com output desejado: `wifi`,
  `wifi_scan`, `fiber`, `mobile`, `dns`, `historical`, `gateway`, `local_equipment`. O que o
  cliente quer de volta (`scoring`, `ai`) agora é `requested_outputs`, campo novo — separado de
  `capabilities`. `capabilities: ["ai", "scoring"]` (o formato usado nos exemplos deste ADR e já
  implementado na fatia NDS-01/02a) continua aceito como **alias legado durante a transição**, não
  quebra o cliente atual, mas integrações novas devem usar `requested_outputs`.
- **Matriz de cobertura por domínio** publicada em
  [`docs/signallq-2-coverage.md`](https://github.com/buildea-labs/network-diagnostics-service/blob/e15a4ad64c86ee18119e3e711a112538fc6d90a3/docs/signallq-2-coverage.md)
  do repositório do NDS — usar como fonte para decidir se uma fatia NDS-02x pode migrar (domínio
  "Coberto") ou deve esperar (domínio "Parcial"/"Lacuna", rastreado nas issues P1
  [buildea-labs/network-diagnostics-service#13](https://github.com/buildea-labs/network-diagnostics-service/issues/13)
  ou P2 [#14](https://github.com/buildea-labs/network-diagnostics-service/issues/14) do repo do
  NDS). Regra do NDS, idêntica em espírito à deste ADR: "o cliente coleta/mede evidências; o NDS
  interpreta, cruza dados, gera findings, score e Next Best Action" — execução local de speedtest,
  scan Wi-Fi, leitura de rede móvel e descoberta de dispositivos são responsabilidade do cliente,
  nunca lacuna do NDS.

### Consequências

- **Perde:** diagnóstico funcionando sem chamada de rede. O motor local garantia classificação
  mesmo com dados parciais porque rodava on-device; o NDS exige que o dispositivo consiga alcançar
  a rede para ser diagnosticado — paradoxal apenas no caso de zero conectividade total, que fica
  como gap aceito (ver seção acima).
- **Ganha:** fonte única de verdade para scoring e explicação, elimina a reconciliação entre
  `MetricStatus` e `DiagnosticStatus` (`comSeveridadeConciliada()` deixa de ser necessária depois
  da migração completa).
- **Muda a superfície de custo:** cada diagnóstico vira uma chamada a um serviço externo pago/
  limitado (rate limit confirmado), diferente do motor local, que era grátis e instantâneo.
- **Remove dívida:** `signallq-diagnostic-worker` (shadow, nunca promovido) e sua validação de
  paridade pendente saem de cena.

### Alternativas consideradas

- **Manter o motor local como fallback do NDS** — rejeitada. O Luiz optou por fonte única de
  verdade em vez de manter dois motores de classificação sincronizados.
- **Manter `signallq-diagnostic-worker` em paralelo ao NDS** — rejeitada. Dois motores remotos
  mirando o mesmo objetivo é duplicação sem propósito claro.

### Pendências

#### Resolvidas pelo contrato canônico (PR #12 do NDS, ver seção acima)

- ~~Formato de resposta em erro genérico/timeout~~ — envelope único definido, `NDS_TIMEOUT` com
  timeout configurável.
- ~~Regra de mapeamento `profile`/`capabilities`~~ — vocabulário fechado do lado do NDS.
  Follow-up de implementação registrado (não bloqueante, nada em produção depende disso ainda):
  o mapper que a NDS-02a (#1747, já mergeada) construiu usa o modelo antigo (`capabilities`
  misturando evidência com output, `profile` omitido para "general" em vez de enviado
  explicitamente) — funciona via alias legado, mas precisa de uma fatia de ajuste para o modelo
  `capabilities`/`requested_outputs` quando o PR #12 do NDS sair de draft e for confirmado em
  produção.

#### Seguem em aberto

- Se vale priorizar a coleta de `dns.hijacked` (gap de coleta confirmado — não existe hoje;
  domínio DNS está listado como "Parcial" na matriz de cobertura do NDS, P1).
- Quebra completa em fatias de migração (NDS-01 em diante) — proposta inicial no inventário de
  #1742, refinada em #1746 (NDS-02, 11 sub-fatias + remoção final), cruzada com a matriz de
  cobertura do NDS para confirmar que cada fatia migra só domínio já "Coberto" do lado do NDS.

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-017-motor-diagnostico-ia-migra-para-nds.md`.*


---

<a id="adr-018"></a>
## ADR-018 — Schema canônico `DiagnosticSnapshot` para o payload NDS

- **Status:** Aceito
- **Data:** 2026-09-02
- **Autor:** Camilo
- **Sub-issue de:** [#1835](https://github.com/buildea-labs/signallq/issues/1835) (NDS-Snapshot-03),
  parte do épico [#1832](https://github.com/buildea-labs/signallq/issues/1832) (P0 — snapshot
  diagnóstico completo).
- **Complementa:** [ADR-017](#adr-017) — este documento não
  substitui o ADR-017 (que decide a migração do motor/IA para o NDS); formaliza o **contrato de
  dados** que o ADR-017 deixou como "sujeito a completar conforme a implementação avançar".

### Contexto

O ADR-017 documentou o contrato do NDS a partir de testes reais (`curl`) e leitura de código —
correto para o que existia em 2026-08-19, mas sem uma fonte única que amarre cada campo do payload
`NdsDiagnosticsRequest` ao campo real de origem em `DiagnosticInput`. Como consequência,
`NdsDiagnosticsRequestMapper.kt` cresceu de forma ad hoc a cada bloco novo (comentário do próprio
arquivo lista os campos "fora do contrato atual" sem uma explicação central de por quê).

A issue-mãe #1832 fez um inventário completo (seção 1) e propôs uma forma conceitual de
`DiagnosticSnapshot` com 16 blocos (seção 2). Esta ADR fecha essa proposta em um schema formal:
nome de campo, tipo, opcionalidade e origem — para que as sub-issues seguintes (mobile, histórico,
equipamento local, DNS ampliado, NAT/plano, proveniência) implementem contra uma referência única,
em vez de decidir nome de campo issue por issue.

**Escopo desta ADR:** contrato de dados (nomes, tipos, opcionalidade, origem, proveniência,
capabilities). **Fora do escopo:** implementação Kotlin de blocos novos (cada sub-issue implementa
o seu); esta ADR só formaliza os blocos e campos que **já existem** no `NdsDiagnosticsRequest`/
`DiagnosticInput` reais no momento da escrita (2026-09-02), e enumera os blocos futuros como
placeholders com a origem esperada, sem inventar shape que a sub-issue correspondente ainda vai
decidir.

### Decisão

#### Convenção geral

- Cada bloco é **opcional no payload**: quando o dado não existe para esta execução, o bloco (ou
  campo dentro do bloco) é **omitido** do JSON — nunca enviado como `null` explícito, `0` ou
  string vazia. **Ausência ≠ zero.** Um `packet_loss_percent: 0` significa "medimos e a perda foi
  zero"; a ausência da chave significa "não medimos". Esta convenção já está implementada em
  `NdsDiagnosticsRequest.toJson()` (cada bloco usa `?.let { put(...) }`) — as sub-issues que
  adicionarem blocos novos devem preservar o mesmo padrão, nunca usar `?: 0` ou `?: false` como
  fallback de serialização.
- Nomes de chave JSON seguem o vocabulário **já aceito pelo NDS**, documentado no ADR-017 (mistura
  `snake_case`/`camelCase` real do contrato — não uniformizado, porque o servidor espera as chaves
  como estão). Blocos novos desta ADR seguem `camelCase` por padrão (consistente com `wifi`,
  `wifiScan`, `gateway`), exceto onde o ADR-017 já fixou `snake_case` num bloco existente (`speed`,
  `fiber`) — nesse caso o campo novo dentro do bloco existente usa a mesma convenção do bloco.
- Tipo Kotlin de cada campo já é nulável no `DiagnosticInput` de origem sempre que o dado pode
  estar ausente — o mapper NUNCA deve inventar um valor não nulo para preencher um campo nulo na
  origem.

#### Os 16 blocos do `DiagnosticSnapshot`

A tabela usa três colunas de estado:

- **Implementado** — bloco já existe em `NdsDiagnosticsRequest` e é populado por
  `NdsDiagnosticsRequestMapper.toNdsDiagnosticsRequest()`.
- **Parcial** — bloco existe mas tem campos comprovadamente faltando (gap documentado).
- **Planejado** — bloco ainda não existe no payload; a origem no `DiagnosticInput` já existe hoje
  (dado coletado, só não enviado) ou está marcada como não coletada.

##### 1. `execution`

**Estado: implementado** (campo de topo do payload, não um objeto aninhado).

| Campo NDS | Tipo | Opcional | Origem em `DiagnosticInput` |
|---|---|---|---|
| `request_id` | `String` | Não (sempre preenchido) | `DiagnosticInput.executionId` quando não-branco; caso contrário um `UUID` novo é gerado em `toNdsDiagnosticsRequest()` — nunca fica vazio no payload. |

Nota: não existe um `NdsExecutionInfo` dedicado hoje — `executionId` vira diretamente `request_id`
de topo. Ficará como bloco conceitual (para fins de inventário), não como objeto JSON aninhado,
enquanto nenhuma sub-issue pedir campos adicionais de execução (ex.: timestamp, duração total).

##### 2. `app`

**Estado: implementado.**

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `app.id` | `String` | Não | Constante `NDS_APP_ID = "io.signallq.app"` (identificador técnico preservado, `AGENTS.md`). |
| `app.version` | `String` | Não | Parâmetro `appVersion` de `toNdsDiagnosticsRequest()` — vem do `BuildConfig` do chamador (`core/nds` não conhece a versão do app consumer). |
| `locale` | `String` | Não (default `"pt-BR"`) | Constante fixa em `NdsDiagnosticsRequest`. |
| `profile` | `String?` | Sim | `ndsProfile(perfilGamer, context?.objective)` — `"gamer"` quando `perfilGamer=true` ou objetivo mapeia para um perfil; `null` (omitido) fora disso. Vocabulário fechado do NDS (ADR-017): `general`, `gamer`, `streaming`, `wfh`, `smarthome` — hoje o mapper só produz `gamer`/`null`, nunca `general` explícito (pendência já registrada no ADR-017). |
| `capabilities` | `List<String>` | Não (pode ser vazia) | `ndsCapabilities(...)` — ver seção "Capabilities" abaixo. |

##### 3. `connection`

**Estado: implementado.**

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `connection.type` | `String` | Não | `ndsConnectionType(DiagnosticInput.connectionType)` — vocabulário livre (`WIFI`/`MOBILE`/`ETHERNET`/`DISCONNECTED`/`UNKNOWN`), não enum, para não travar o cliente quando o servidor aceitar valor novo. |
| `connection.ssid` | `String?` | Sim | `DiagnosticInput.wifi?.ssid`. |
| `connection.bssid` | `String?` | Sim | `DiagnosticInput.wifi?.bssidMascarado` — sempre mascarado, nunca o BSSID bruto. |
| `connection.natStatus` | `String?` | Sim | `DiagnosticInput.natStatus?.name` (enum `NatStatus`: `DIRECT_PUBLIC`, `CGNAT`, `DOUBLE_NAT_OR_CGNAT`, `UNKNOWN`) — implementado na NDS-Snapshot-09 (issue #1841). Fica sob `connection` porque é uma propriedade da topologia da conexão atual, não um bloco próprio — a issue-mãe não lista `nat` como um dos 16 blocos de topo. `null` quando nenhuma classificação rodou nesta sessão; `"UNKNOWN"` quando rodou mas foi inconclusiva — os dois casos são distintos. |

##### 4. `wifi`

**Estado: implementado**, com campos de `WifiDiagnosticInput` ainda fora do payload.

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `wifi.rssi` | `Int?` | Sim | `WifiDiagnosticInput.rssiDbm` |
| `wifi.band` | `String?` | Sim | `ndsBandaWifi(wifi.banda())` — deriva de `frequenciaMhz` (`"2.4GHz"`/`"5GHz"`/omitido se desconhecida) |
| `wifi.channel` | `Int?` | Sim | `WifiDiagnosticInput.canal` |
| `wifi.linkSpeed` | `Int?` | Sim | `WifiDiagnosticInput.linkSpeedMbps` |
| `wifi.standard` | `String?` | Sim | `WifiDiagnosticInput.wifiStandard` |
| `wifi.frequencyMhz` *(planejado)* | `Int?` | Sim | `WifiDiagnosticInput.frequenciaMhz` — já usado para derivar `band`, mas o valor bruto em MHz não é enviado (gap #1832: "Frequência MHz ❌ adicionar"). |
| `wifi.channelWidthMhz` *(planejado)* | `Int?` | Sim | `WifiDiagnosticInput.larguraCanalMhz` (gap #1832: "Largura do canal ❌ adicionar"). |
| — | — | — | `linkSpeedDownMbps`/`linkSpeedUpMbps`/`gatewayIp`/`localIp`/`routerType`/`is5GhzCapable` de `WifiDiagnosticInput` seguem sem consumidor no payload NDS — não estão nos gaps priorizados pela issue-mãe; ficam registrados aqui para a próxima auditoria de inventário decidir se valem a pena. |

##### 5. `wifiScan`

**Estado: implementado nesta ADR/#1834** (antes: bloco sempre `null` — gap crítico do #1832 seção 3).

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `wifiScan.connectedChannel` | `Int?` | Sim | `WifiScanDiagnosticInput.conectadoCanal` |
| `wifiScan.channelCongestion` | `Int?` | Sim | Calculado por `mapWifiScanToNds()` a partir do `ChannelScore` do canal conectado (`:coreNetwork`, `ChannelEvaluator.evaluateChannels()`) — percentual 0..100, `null` sem canal conectado ou sem score correspondente. |
| `wifiScan.bestChannel` | `Int?` | Sim | `ChannelScore.recommended` da mesma avaliação. |
| `wifiScan.neighborCount` | `Int?` | Sim | `WifiScanDiagnosticInput.redes.size` — `0` é valor legítimo (scan rodou, zero redes vizinhas encontradas), distinto de bloco ausente (scan não rodou). |
| `wifiScan.neighbors[].channel` | `Int?` | Sim | `RedeWifiVizinha.canal` |
| `wifiScan.neighbors[].frequencyMhz` | `Int?` | Sim | `RedeWifiVizinha.frequenciaMhz` |
| `wifiScan.neighbors[].rssiDbm` | `Int?` | Sim | `RedeWifiVizinha.rssiDbm` |
| `wifiScan.neighbors[].widthMhz` | `Int?` | Sim | `RedeWifiVizinha.larguraCanalMhz` — `null` quando o scan não reportou largura real (o motor assume 20 MHz internamente, mas o payload não inventa esse valor: envia `null`, não `20`). |
| `wifiScan.algorithmVersion` | `String?` | Sim | Constante `CHANNEL_EVALUATOR_VERSION` em `NdsWifiScanMapper` — identifica método/versão do algoritmo de avaliação de canal. `null` quando nenhum canal foi avaliado (sem vizinhos utilizáveis pelo `ChannelEvaluator`). |
| `wifiScan.currentScoreMw` | `Double?` | Sim | `ChannelScore.score` (mW, quanto menor melhor) do canal conectado dentro de `bandScores` — mesma fonte usada para calcular `channelCongestion`, mas sem a normalização percentual. `null` sem canal conectado ou fora do `bandScores` avaliado. Exigido pela regra `WIFI-CANAL-*` do NDS (issue #1874) para sair do estado inconclusivo `WIFI-CANAL-INC-02`. |
| `wifiScan.bestScoreMw` | `Double?` | Sim | `ChannelScore.score` (mW) do candidato marcado `recommended` em `bandScores` — mesmo critério usado para `bestChannel`. `null` quando não há candidato recomendado. |
| `wifiScan.validNetworkCount` | `Int?` | Sim | Quantidade de vizinhas que o `ChannelEvaluator` conseguiu efetivamente usar no cálculo (freq/RSSI/banda válidos) — distinto de `neighborCount`, que é a contagem bruta do scan. Permite à regra `WIFI-CANAL-*` diferenciar "poucas vizinhas" de "muitas vizinhas mas maioria sem dado utilizável". |

**Decisão de privacidade:** o BSSID das redes vizinhas **não** entra no payload (nem mascarado) —
só serve para o `ChannelEvaluator` identificar sobreposição espectral internamente. Enviar BSSID de
redes de terceiros (não a rede do usuário) não tem justificativa de produto e amplia a superfície
de dado sensível sem necessidade.

**Evidência, não só resultado:** por decisão explícita do #1832 seção 3 ("O NDS precisa conseguir
explicar POR QUE o canal foi considerado congestionado"), o bloco carrega a lista `neighbors[]`
bruta além de `channelCongestion`/`bestChannel` já calculados — o NDS pode reconstruir o raciocínio,
não só confiar no resultado do cliente.

##### 6. `speed`

**Estado: implementado**, incluindo a origem da perda de pacotes (NDS-Snapshot-10 —
issue #1842).

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `speed.ping_ms` | `Double?` | Sim | `InternetDiagnosticInput.latencyMs` |
| `speed.jitter_ms` | `Double?` | Sim | `InternetDiagnosticInput.jitterMs` |
| `speed.download_mbps` | `Double?` | Sim | `InternetDiagnosticInput.downloadMbps` |
| `speed.upload_mbps` | `Double?` | Sim | `InternetDiagnosticInput.uploadMbps` |
| `speed.packet_loss_percent` | `Double?` | Sim | `InternetDiagnosticInput.perdaPercentual` |
| `speed.packetLossSource` | `String?` (vocabulário fechado `NdsProvenance`, ver seção "Convenção de proveniência" abaixo) | Sim | `InternetDiagnosticInput.packetLossSource` traduzido por `toNdsPacketLossSource()` (`NdsDiagnosticsRequestMapper.kt`) — `"modem"`→`MEASURED`, `"estimated"`→`ESTIMATED`, `"unknown"`→`UNKNOWN`, `"naoMedido"`/`null`/valor não reconhecido→omitido (campo `null`). |

##### 7. `quality`

**Estado: implementado**, com os campos de `SpeedtestQualityInput` (veredito) ainda fora do payload.

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `quality.latencyMs` | `Double?` | Sim | `InternetDiagnosticInput.latencyMs` |
| `quality.jitterMs` | `Double?` | Sim | `InternetDiagnosticInput.jitterMs` |
| `quality.packetLossPercent` | `Double?` | Sim | `InternetDiagnosticInput.perdaPercentual` |
| `quality.loadedLatencyMs` | `Double?` | Sim | Derivado: `latencyMs + bufferbloatMs` quando ambos presentes; `null` se qualquer um faltar (nunca soma parcial). |
| `quality.bufferbloatMs` | `Double?` | Sim | `InternetDiagnosticInput.bufferbloatMs` |
| `quality.streamingVerdict` *(planejado)* | `String?` | Sim | `InternetDiagnosticInput.qualidadeUso?.vereditoStreaming` |
| `quality.gamerVerdict` *(planejado)* | `String?` | Sim | `InternetDiagnosticInput.qualidadeUso?.vereditoGamer` |
| `quality.videoCallVerdict` *(planejado)* | `String?` | Sim | `InternetDiagnosticInput.qualidadeUso?.vereditoVideochamada` |
| `quality.primaryBottleneck` *(planejado)* | `String?` | Sim | `InternetDiagnosticInput.qualidadeUso?.gargaloPrimario` |
| `quality.bufferbloatSeverity` *(planejado)* | `String?` | Sim | `InternetDiagnosticInput.qualidadeUso?.severidadeBufferbloat` |

`quality` e `speed` duplicam propositalmente `latencyMs`/`jitterMs`/`packetLossPercent` — decisão
herdada do ADR-017 (contrato já observado no NDS real), não uma duplicação a resolver por esta ADR.

##### 8. `dns`

**Estado: implementado** (bloco expandido pela NDS-Snapshot-08, issue #1840). Bloco opcional em
`NdsDiagnosticsRequest`, populado por `toNdsDnsInfo()` em `NdsDiagnosticsRequestMapper.kt` a partir
de `DiagnosticInput.dns` (`DnsDiagnosticInput`), montado em `MainViewModel.montarDnsInput()`
combinando o ultimo benchmark concluido (`feature/dns`, `BenchmarkDns`), o provedor ativo inferido
por `inferirProvedorAtivoDns` (mesma tabela ja usada para calcular a coerencia — nao existe uma
segunda tabela IP/hostname→provedor neste metodo, ver dívida #1823), a coerência calculada por
`AvaliadorCoerenciaDns` e o estado de Private DNS lido de `SnapshotRede` (`core/network`).

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `dns.primary` | `String?` | Sim | `DnsDiagnosticInput.currentDnsIp` |
| `dns.latencyMs` | `Int?` | Sim | `DnsDiagnosticInput.currentDnsLatencyMs` (chave JSON `latencyMs`, apesar do campo Kotlin se chamar `responseTimeMs` em `NdsDnsInfo` — inconsistência herdada, não corrigida aqui para não mudar o contrato JSON já aceito pelo NDS). |
| `dns.hijacked` | `Boolean?` | Sim | **Sempre `null` hoje** — gap de coleta confirmado (não existe fonte no `DiagnosticInput`). Nunca usar `false` como default para "não sabemos" (regra explícita do #1832 seção 9). |
| `dns.providerName` | `String?` | Sim | `DnsDiagnosticInput.currentDnsName` — inferido de `SnapshotRede.privateDnsHostname`/`dnsServidores` por `inferirProvedorAtivoDns` (`MainViewModel`). |
| `dns.bestName` | `String?` | Sim | `DnsDiagnosticInput.bestDnsNameFromComparison` — melhor resultado do último `BenchmarkDns` concluído (menor `tempoMs`, ignorando `isGatewayLocal`). |
| `dns.bestLatencyMs` | `Int?` | Sim | `DnsDiagnosticInput.bestDnsLatencyMsFromComparison` |
| `dns.grade` | `String?` | Sim | `DnsDiagnosticInput.dnsGrade` — `ResultadoBenchmarkDns.gradeRapidez` do melhor resultado ("A"≤15ms/"B"≤30ms/"C"≤50ms/"D">50ms). |
| `dns.comparisonAvailable` | `Boolean` | Não (default `false`, mas é um booleano de "a comparação rodou", não um dado de rede — `false` aqui é um valor legítimo, não "ausência") | `DnsDiagnosticInput.dnsComparisonAvailable` — `true` quando o benchmark encontrou pelo menos um resultado com `tempoMs` medido. |
| `dns.coherenceAlertLevel` | `String?` | Sim | `DnsDiagnosticInput.coerenciaNivelAlerta` (`"none"`/`"attention"`/`"critical"`) |
| `dns.coherenceConsecutiveDivergences` | `Int?` | Sim | `DnsDiagnosticInput.coerenciaDivergenciasConsecutivas` |
| `dns.coherenceDivergenceRatePercent` | `Double?` | Sim | `DnsDiagnosticInput.coerenciaTaxaDivergenciaPercentual` |
| `dns.privateDnsActive` | `Boolean?` | Sim | `DnsDiagnosticInput.privateDnsActive` — `SnapshotRede.privateDnsAtivo` (`LinkProperties.privateDnsServerName != null`). |
| `dns.privateDnsHostname` | `String?` | Sim | `DnsDiagnosticInput.privateDnsHostname` — `SnapshotRede.privateDnsHostname`, **filtrado por privacidade antes de sair do `MainViewModel`**: só é repassado quando o hostname bate com um provedor público conhecido (mesma tabela de `inferirProvedorAtivoDns`, chamada só com o hostname, sem lista de IPs). Hostname customizado (ex.: resolver DoH/DoT próprio do usuário) nunca chega ao NDS — pode ser um identificador pessoal. Decisão de privacidade desta issue (#1832 seção 9: "avaliar política"). |

**Decisão de privacidade — `privateDnsHostname`:** avaliadas duas situações. (1) hostname de um
provedor público conhecido (ex. `dns.google`, `cloudflare-dns.com`, `dns.quad9.net`) — não é dado
pessoal, mesma informação que `providerName` já expõe por outro caminho; enviado. (2) hostname de
um resolver DoH/DoT próprio do usuário (self-hosted) — pode ser um identificador (domínio pessoal,
subdomínio com nome do usuário) sem valor diagnóstico adicional sobre o que `privateDnsActive`
sozinho já informa; **nunca enviado**. O filtro é mecânico (mesma tabela de substring já usada para
`providerName`, sem tabela nova) e vive em `MainViewModel.montarDnsInput()`, não no mapper de
`core/nds` — o mapper só transcreve um valor que já chegou seguro.

##### 9. `gateway`

**Estado: implementado.**

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `gateway.rttGatewayMs` | `Int?` | Sim | `InternetDiagnosticInput.rttGatewayMs` |
| `gateway.connectedDevices` | `Int?` | Sim | `WifiDiagnosticInput.dispositivosNaRede` |
| `gateway.ip` *(planejado, avaliar inclusão segura)* | `String?` | Sim | `WifiDiagnosticInput.gatewayIp` — já coletado; #1832 seção 1 marca como "avaliar inclusão segura" (IP local, não IP público — risco de privacidade menor, mas não decidido ainda). |

Nota de implementação: o bloco `gateway` só é construído quando **pelo menos um** dos dois campos
implementados está presente (`rttGatewayMs != null || dispositivosNaRede != null`) — não quando
ambos. Preservar esse critério ao adicionar `gateway.ip`.

##### 10. `mobile`

**Estado: implementado** (issue #1837). Bloco opcional em `NdsDiagnosticsRequest`, populado por
`toNdsMobileInfo()` em `NdsDiagnosticsRequestMapper.kt`.

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `mobile.operator` | `String?` | Sim | `MobileDiagnosticInput.carrierName` |
| `mobile.technology` | `String?` | Sim | `MobileDiagnosticInput.mobileTechnology` |
| `mobile.rsrp_dbm` | `Int?` | Sim | `MobileDiagnosticInput.rsrpDbm` |
| `mobile.rsrq_db` | `Int?` | Sim | `MobileDiagnosticInput.rsrqDb` |
| `mobile.sinr_db` | `Int?` | Sim | `MobileDiagnosticInput.sinrDb` |
| `mobile.band` | `String?` | Sim | `MobileDiagnosticInput.band` |
| — | — | — | `MobileDiagnosticInput.signalStrengthDbm`/`signalQualityPercent`/`publicIp` **não** entram no schema — os dois primeiros são redundantes com RSRP/RSRQ/SINR na tecnologia 4G/5G; IP público é dado sensível sem justificativa clara de diagnóstico. Seguem fora até haver caso de uso explícito. |

Nomes de chave seguem `snake_case` para casar com o exemplo já publicado no #1832 seção 4.
Cell ID/TAC/MCC/MNC **não** entram — proibido explicitamente pela issue-mãe sem revisão de
privacidade dedicada.

**Gate de permissão de telefonia (issue #1735/#1837):** o bloco inteiro é omitido — não apenas
zerado — quando `MobileDiagnosticInput.capturaReduzida == true`, o flag que `MonitorTelephonyImpl`
já usa (GH#1662) para sinalizar que o snapshot foi capturado sem `READ_PHONE_STATE` (só
operadora/MCC/MNC, sem nenhuma medição real de sinal). Também fica `null` quando nenhum dos seis
campos acima teria conteúdo (nenhuma evidência útil), mesmo com permissão concedida (ex.: rádio
desligado). Capability `"mobile"` (vocabulário já previsto na seção "Capabilities" abaixo) só entra
na lista quando o bloco é efetivamente construído.

##### 11. `fiber`

**Estado: implementado**, com o estado operacional (além das métricas ópticas) ainda fora.

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `fiber.rxPower_dbm` | `Double?` | Sim | `FibraDiagnosticInput.rxPowerDbm` |
| `fiber.txPower_dbm` | `Double?` | Sim | `FibraDiagnosticInput.txPowerDbm` |
| `fiber.temperature_c` | `Double?` | Sim | `FibraDiagnosticInput.temperatureCelsius` |
| `fiber.voltage_v` | `Double?` | Sim | Sem origem em `FibraDiagnosticInput` hoje — gap conhecido, já registrado em teste de caracterização (`NdsDiagnosticsRequestMapperTest`, "voltage nao existe... gap conhecido"). |
| `fiber.isUp` *(planejado)* | `Boolean` | Não (campo não-nulo em `FibraDiagnosticInput`) | `FibraDiagnosticInput.isUp` — estado operacional UP/DOWN da fibra (#1832 seção 1: "Estado UP/DOWN ❌ adicionar"). Único campo deste bloco que não é opcional na origem — mas o bloco inteiro (`fiber == null`) continua opcional quando não há equipamento de fibra. |

##### 12. `localEquipment`

**Estado: implementado** (issue #1839). Bloco opcional em `NdsDiagnosticsRequest`, populado por
`toNdsLocalEquipmentInfo()` em `NdsDiagnosticsRequestMapper.kt` a partir de
`DiagnosticInput.localDevice` (`SafeLocalDeviceContext`) — origem já passa por allowlist
(`LocalDeviceSafeFilter`), nunca o snapshot bruto do equipamento.

| Campo NDS | Tipo | Opcional | Origem (`SafeLocalDeviceContext`) |
|---|---|---|---|
| `localEquipment.vendor` | `String?` | Sim | `vendor` |
| `localEquipment.model` | `String?` | Sim | `modelo` |
| `localEquipment.firmwareVersion` | `String?` | Sim | `firmwareVersion` |
| `localEquipment.deviceType` | `String` | Não | `deviceType.name` (enum `DeviceType`) |
| `localEquipment.supportLevel` | `String` | Não | `supportLevel.name` (enum `SupportLevel`) |
| `localEquipment.connectionStatus` | `String` | Não | `connectionStatus.name` (enum `LocalDeviceSectionStatus`) |
| `localEquipment.fiberStatus` | `String` | Não | `statusFibra.name` |
| `localEquipment.wanStatus` | `String` | Não | `statusWan.name` |
| `localEquipment.wifiStatus` | `String` | Não | `statusWifi.name` |
| `localEquipment.lanStatus` | `String` | Não | `statusLan.name` |
| `localEquipment.connectedClients` | `Int` | Não | `quantidadeClientes` |

Os enums viram string via `.name` (`UPPER_SNAKE_CASE`, ex.: `ONT_GPON`, `LAB_VALIDATED`) — não uma
tradução para o vocabulário ilustrativo (`"ONT"`/`"FULL"`/`"UP"`) do exemplo da issue-mãe #1832
seção 8, que não é uma tabela de tradução fixada por esta ADR.

O bloco inteiro é opcional (`DiagnosticInput.localDevice == null` quando nenhum equipamento foi
lido nesta sessão — resulta em bloco omitido, nunca zeros). `warnings`/`coletadoEmEpochMs` de
`SafeLocalDeviceContext` ficam fora do payload por decisão explícita desta sub-issue — não pedidos
pela issue-mãe, e `warnings` ainda não tem um vocabulário JSON definido. **Nunca enviado**:
`LocalNetworkDeviceSnapshot` bruto (senha, serial completo, credenciais, payload HTML, token de
sessão, MAC completo) — regra já imposta pelo filtro existente, confirmada por teste dedicado em
`NdsDiagnosticsRequestMapperTest` (`localEquipment serializado nunca carrega campo fora da
allowlist`). Capability `"local_equipment"` (vocabulário já previsto na seção "Capabilities" abaixo)
só entra na lista quando o bloco é efetivamente construído.

##### 13. `historical`

**Estado: implementado** (NDS-Snapshot-06, issue #1838). `HistoricalDiagnosticInput` já existia
sem nenhum produtor real em produção — esta fatia acrescentou a agregação (`agregarHistoricoNds`,
`:app`, consultando `MedicaoDao.buscarDesde` sobre uma janela de 30 dias) e o cálculo determinístico
de degradação (`DegradacaoHistoricoCalculadora`, `core/diagnostico`, reusado por
`RecomendacaoPraticaEngine.recomendarUpgradeRoteadorRecorrente`/REC-14, que já lia estes dois campos
sem nenhum caminho de produção que os preenchesse).

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `historical.tests_7d` | `Int` | Não (default `0` na origem — `0` testes é um fato real, não ausência) | `HistoricalDiagnosticInput.testsCount7d` |
| `historical.avg_download_7d` | `Double?` | Sim | `avgDownload7d` |
| `historical.avg_upload_7d` | `Double?` | Sim | `avgUpload7d` |
| `historical.avg_ping_7d` | `Double?` | Sim | `avgPing7d` |
| `historical.avg_dns_7d` | `Double?` | Sim | `avgDns7d` — **sem produtor hoje** (`MedicaoEntity` não persiste latência DNS por medição; `AvaliadorCoerenciaDns` lê em tempo real, sem histórico). Fica sempre omitido nesta fatia. |
| `historical.tests_30d` | `Int` | Não (mesma regra do `tests_7d`) | `testsCount30d` |
| `historical.avg_download_30d` | `Double?` | Sim | `avgDownload30d` |
| `historical.avg_upload_30d` | `Double?` | Sim | `avgUpload30d` |
| `historical.avg_ping_30d` | `Double?` | Sim | `avgPing30d` |
| `historical.avg_dns_30d` | `Double?` | Sim | `avgDns30d` — mesmo gap do `avg_dns_7d`. |
| `historical.degradation_detected` | `Boolean?` | Sim | `degradationDetected` — calculado por `DegradacaoHistoricoCalculadora.calcular` (download 7d vs. 30d, mínimo de 5 testes em 7d e 10 em 30d, limiar de 20% — mesma confiança estatística do nível "attention" de `HistoricalDegradationEngine`). |
| `historical.degradation_percent` | `Double?` | Sim | `degradationPercent` — positivo = queda, negativo = melhora. |
| `historical.worstTimeWindow`/`bestTimeWindow` | `String?` | Sim | `worstTimeWindow`/`bestTimeWindow` — **sem produtor hoje** (nenhuma fonte calcula janela de horário por medição); campo do schema preservado para quando essa coleta existir, sempre omitido nesta fatia. |

Bloco inteiro fica omitido do payload (`null`, nunca zeros) quando não há nenhuma medição nas duas
janelas — usuário novo ou sem uso nos últimos 30 dias. "Últimos testes" (lista bruta) mencionados no
#1832 seção 5 ficaram **fora desta fatia** — decisão explícita da issue (item opcional, "se houver
benefício real"), não um gap esquecido.

##### 14. `plan`

**Estado: implementado** (NDS-Snapshot-09, issue #1841) — bloco de topo próprio; a origem continua
sendo um campo de topo do `DiagnosticInput`, não um objeto aninhado na origem.

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `plan.contractedSpeedMbps` | `Int?` | Sim | `DiagnosticInput.velocidadeContratadaMbps` — fonte `PreferenciasAppRepository.planoInternetFlow` (informado pelo usuário). **Nunca inferido** este valor quando o usuário não informou (regra explícita do #1832 seção 6) — ausência do campo é o comportamento correto, não um valor default. Bloco inteiro fica `null` (omitido do JSON) quando o campo não existe. |

##### 15. `networkIdentity`

**Estado: planejado, sem origem coletada hoje.** Nenhum campo de ISP/ASN/servidor de teste existe
em `DiagnosticInput` no momento desta ADR — `InternetDiagnosticInput.serverName`/`serverRegion`/
`serverHost` existem (servidor usado no speedtest) mas não são ISP/ASN.

| Campo NDS (sugerido) | Tipo | Opcional | Origem |
|---|---|---|---|
| `networkIdentity.isp` | `String?` | Sim | **Sem origem hoje** — precisa de nova coleta (#1832 seção 10). |
| `networkIdentity.asn` | `String?` | Sim | **Sem origem hoje.** |
| `networkIdentity.testServer` | `String?` | Sim | `InternetDiagnosticInput.serverName` (já coletado, só não mapeado para este bloco). |

Geolocalização precisa **não** entra neste bloco — proibido explicitamente pelo #1832 seção 10.

##### 16. `context`

**Estado: implementado.**

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `context.reported_problem` | `String?` | Sim (omitido também quando string em branco) | `DiagnosticContext.reportedProblem` |
| `context.objective` | `String?` | Sim (idem) | `DiagnosticContext.objective` |
| `context.subcategory` | `String?` | Sim (idem) | `DiagnosticContext.subcategory` |
| `context.symptoms` | `List<String>` | Sim (omitido se vazia) | `DiagnosticContext.symptoms` |
| `context.answers` | `Map<String, String>` | Sim (omitido se vazio) | `DiagnosticContext.answers` |

`DiagnosticInput.deviceGamingSelecionado` **não** tem bloco correspondente — campo hoje sem
consumidor de UI ativo (comentário do próprio `DiagnosticInput.kt`, GH#1682); não confundir com
`profile="gamer"` (que já é enviado via `app.profile`, não via `context`). Fica fora do schema até
uma nova fonte de UI escrever esse campo.

#### Convenção de proveniência (`source`)

**Estado: implementado para `speed.packetLossSource`** (NDS-Snapshot-10 — issue #1842). Aplica-se a
**qualquer métrica cuja origem afete a confiança que o NDS/IA deve depositar nela** — não a todos os
campos. Critério de quando um campo precisa de `source`: o mesmo campo pode ser preenchido por
caminhos de coleta com confiabilidade diferente, e o consumidor (motor de regras ou IA) toma decisão
diferente dependendo de qual caminho foi usado.

**Formato:** quando um campo tiver proveniência relevante, o payload carrega um objeto irmão
`<campo>Source` (mesmo nível do campo, não aninhado dentro de um objeto `{value, source}") — decisão
que preserva compatibilidade com o formato flat já aceito pelo NDS em vez de reestruturar campos
existentes como objetos. Exemplo aplicado ao campo que já tem essa necessidade hoje:

```json
{
  "speed": {
    "packet_loss_percent": 2.1,
    "packetLossSource": "estimated"
  }
}
```

**Valores enumerados de `source` (vocabulário fechado — modelado pelo enum Kotlin `NdsProvenance` em
`NdsDiagnosticsRequest.kt`, serializado como string minúscula via `NdsProvenance.jsonValue`; string
livre no fio por decisão consistente com o resto do contrato — não trava o cliente quando o NDS
aceitar um valor novo, mas o mapper deste módulo só emite um destes cinco valores):**

| Valor | Significado |
|---|---|
| `measured` | Medição direta, alta confiança (ex.: RTT gateway via socket, RX óptico lido do equipamento). |
| `estimated` | Indício indireto, não uma medição direta (ex.: perda de pacotes inferida de timeout HTTP). |
| `derived` | Calculado a partir de outros campos do mesmo snapshot (ex.: `loadedLatencyMs = latencyMs + bufferbloatMs`). |
| `cached` | Valor de uma execução anterior, reaproveitado nesta (ex.: leitura de equipamento que falhou nesta execução mas teve sucesso recentemente — se essa política existir; hoje não há caminho de cache confirmado em `DiagnosticInput`, valor reservado para quando existir). |
| `unknown` | Fonte não determinada — usar apenas quando a coleta já retorna essa incerteza (ex.: `packetLossSource = "unknown"`, já existente). |

**Campo implementado com esta convenção:** `speed.packetLossSource`, traduzido de
`InternetDiagnosticInput.packetLossSource` (`"estimated"`/`"naoMedido"`/`"unknown"`/`"modem"`) por
`toNdsPacketLossSource()` em `NdsDiagnosticsRequestMapper.kt` — reaproveita exatamente o vocabulário
interno já existente, sem inventar um terceiro:

| `InternetDiagnosticInput.packetLossSource` | `NdsProvenance` no payload |
|---|---|
| `"modem"` (medição direta do equipamento) | `MEASURED` |
| `"estimated"` (indício via timeout HTTP) | `ESTIMATED` |
| `"unknown"` (coleta já retorna incerteza) | `UNKNOWN` |
| `"naoMedido"` ou `null` | campo omitido (nenhuma medição rodou — não é "fonte desconhecida") |
| qualquer valor não reconhecido | campo omitido (nunca propaga string livre arbitrária — vocabulário fechado) |

**Candidatos futuros a proveniência**, ainda não implementados nesta fatia — deferidos porque não
há hoje um caminho de coleta que produza um valor *diferente* de um único valor fixo (adicionar um
campo dinâmico sem um segundo caminho real de coleta seria over-engineering, não proveniência):
`localEquipment.*` (fonte é sempre `LocalDeviceSafeFilter`/leitura direta do equipamento —
`"measured"` fixo, não precisa de campo dinâmico, a não ser que surja um caminho de fallback, ex.
cache de leitura anterior); `quality.loadedLatencyMs` (já é `"derived"` por construção, hoje sem
campo explícito — candidato natural quando a proveniência for cobrada por consumidores externos ao
app); `wifi.channel` via scan Android vs. outra fonte futura (hoje só existe um caminho de coleta —
`WifiDiagnosticInput.canal` do scan do sistema — sem um segundo caminho concorrente que justifique
`source` dinâmico ainda).

#### `capabilities` vs. "campo presente" (seção 13 do #1832)

Duas perguntas diferentes, que o payload de hoje mistura implicitamente porque `capabilities` é
derivada dos mesmos blocos que decidem presença no JSON:

- **`capability`** = "o cliente **é capaz** de fornecer/usar este domínio" — uma afirmação sobre o
  aparelho/app nesta execução (ex.: "este é um aparelho com rádio Wi-Fi e o app tem permissão de
  scan"), não sobre o dado específico coletado.
- **`field present`** = "o dado **existe** nesta execução" — uma afirmação sobre o payload
  concreto (ex.: "o campo `wifi.rssi` está preenchido neste JSON").

**Consequência para o NDS (e para quem interpretar o payload):** `capabilities` incluir `"wifi"`
**não garante** que todo campo do bloco `wifi` esteja preenchido — só que o bloco existe e é
relevante para este dispositivo/execução. Um campo individual dentro de um bloco presente ainda
pode estar ausente (ex.: `wifi` presente com `channel=null` porque o Android não retornou o canal
nesta leitura). O inverso também vale: a ausência de uma capability não implica reprovação — pode
significar apenas que o domínio não se aplica (ex.: sem `"mobile"` porque a conexão é Wi-Fi, não
porque a leitura falhou).

**Implementação atual** (`NdsProfileCapabilitiesMapper.ndsCapabilities`): cada capability é incluída
quando o **bloco correspondente é não-nulo no request** (mesmo critério que decide se o bloco vira
uma chave no JSON) — cobre `wifi`/`fiber`/`wifi_scan` (NDS-Snapshot-02/#1834), `mobile`
(NDS-Snapshot-05/#1837), `historical` (NDS-Snapshot-06/#1838) e `local_equipment`
(NDS-Snapshot-07/#1839), todos usando o mesmo critério. Sempre inclui `"scoring"`+`"ai"`
(`requested_outputs` implícito, ainda não migrado para o modelo `requested_outputs` separado do
ADR-017).

**Vocabulário de capabilities aceito pelo NDS** (ADR-017, contrato canônico PR #12):
`wifi`, `wifi_scan`, `fiber`, `mobile`, `dns`, `historical`, `gateway`, `local_equipment` — os
blocos `speed`/`quality`/`context`/`plan`/`networkIdentity` não têm capability própria porque não
dependem de um sensor/permissão específica do aparelho (velocidade e contexto sempre "capazes" de
existir quando o app roda um teste ou o usuário responde a jornada guiada).

### Consequências

- **Ganha:** referência única para nome de campo/tipo/opcionalidade antes de cada sub-issue de bloco
  novo decidir isso isoladamente — reduz o risco de duas sub-issues inventarem convenções diferentes
  para o mesmo tipo de dado (ex.: duas formas de indicar "não medido").
- **Não resolve sozinha:** este documento não implementa nenhum bloco "planejado" — cada sub-issue
  (mobile, histórico, equipamento local, DNS ampliado, NAT/plano) ainda decide o shape Kotlin
  exato dentro do que esta ADR já fixou, e pode ajustar nome de campo se a integração real com o
  NDS pedir um valor diferente (esta ADR é a proposta do lado cliente, não a confirmação do
  servidor).
- **Dependência cruzada:** o schema aqui descrito precisa ser confirmado contra o lado NDS antes de
  qualquer bloco novo (além de `wifiScan`, já implementado por #1834) ser considerado "pronto para
  produção" — abrir issue irmã em `network-diagnostics-service` citando esta ADR, como já pede o
  critério de aceite da #1835.

### Alternativas consideradas

- **Reescrever `NdsDiagnosticsRequest` como um único `DiagnosticSnapshot` aninhado, espelhando a
  proposta conceitual do #1832 seção 2 na íntegra** — rejeitada por ora. O ganho de reorganizar a
  árvore de classes Kotlin é cosmético comparado ao risco de tocar em todo o mapper e todos os
  testes de uma vez só; o schema documentado aqui já cumpre o objetivo de contrato único sem exigir
  esse refactor. Se uma sub-issue futura decidir renomear `NdsDiagnosticsRequest` para
  `DiagnosticSnapshot` de fato, esta ADR continua válida como fonte de campos — só muda o nome do
  tipo Kotlin.
- **Aninhar `source` dentro de cada campo como `{value, source}`** — rejeitada. Quebraria o formato
  flat que o NDS já aceita para todos os blocos existentes; o formato `<campo>Source` irmão
  preserva compatibilidade.

### Pendências

- Revisão pelo dono técnico do NDS (`network-diagnostics-service`) antes de qualquer bloco
  "planejado" desta ADR começar a ser implementado — critério de aceite da #1835.
- Decisão de nome de campo definitivo para os blocos "planejado" cabe à sub-issue que implementar
  cada um; os nomes sugeridos aqui são o ponto de partida, não a palavra final caso a integração
  real com o NDS peça um vocabulário diferente.
- `networkIdentity.isp`/`asn` seguem sem fonte de coleta no app — decisão de produto (vale
  adicionar um provedor de lookup de ASN/ISP?) fica com Claudete/Luiz, fora do escopo desta ADR.

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-018-diagnostic-snapshot-schema-canonico.md`.*


---

<a id="adr-019"></a>
## ADR-019 — SignallQ Windows Desktop

- **Status:** Aceito
- **Data:** 2026-10-14
- **Autor:** Luiz (CEO), por solicitação explícita de entrega Windows E2E
- **Supersede parcialmente:** ADR-016, somente a exclusão de Windows Desktop para SignallQ.

### Contexto

O SignallQ precisa de uma superfície desktop Windows instalada, visualmente fiel ao protótipo e
honesta sobre o que mede. O Android continua a fonte de regras existentes; Web continua sendo o
site público. Nenhum deles é um host desktop executável.

### Decisão

Windows passa a ser plataforma permitida do SignallQ. A implementação vive em `windows/`, usa
Tauri 2, React/Vite e um núcleo Rust. Ela é independente de Android e Web, mas reaproveita
contratos, vetores de teste, assets oficiais e serviços remotos compatíveis.

O desktop não incorpora tokens de NDS/Assist, não executa Kotlin Android e não declara como medida
um dado indisponível. Assist e diagnóstico remoto permanecem explicitamente indisponíveis até que
um contrato de autenticação próprio de cliente desktop seja aprovado.

### Consequências

- A exclusão de Windows em ADR-016 deixa de se aplicar ao SignallQ.
- `web/` continua sendo o site público; não será empacotado como aplicativo desktop.
- Histórico desktop usa SQLite novo e versionado; não migra o banco Room.
- Instalador e validação de DPI/tray exigem runner ou equipamento Windows e não podem ser
  comprovados em macOS.

### Não decide

Não autoriza publicação na Microsoft Store, deploy de Workers, novo fornecedor pago, mudança na
autenticação do NDS ou coleta/transmissão adicional de dados sem decisão específica.

*Arquivo original preservado em `docs/archive/decisoes/decisoes\ADR-019-signallq-windows-desktop.md`.*
