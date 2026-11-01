# Changelog — SignallQ Android

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/) e este projeto adere a [Semantic Versioning](https://semver.org/).

> Nota de marca: a UI e a documentação usam **SignallQ**. O package/namespace atual é
> `io.signallq.app` (renomeado de `io.veloo.app` em 2026-06-28). Outros identificadores
> técnicos permanecem por compatibilidade de infra — repo `gmmattey/linka-android`,
> worker `linka-ai-diagnosis-worker`, banco `linkaKotlin.db`.

> Fonte autoritativa do histórico Android é [`android/CHANGELOG.md`](android/CHANGELOG.md).
> Este arquivo agrega o histórico do monorepo (Android + Admin Worker Cloudflare).

---

## [1.1.0] — 2026-11-01

### Adicionado (Android)
- **Nova Home (ADR-021)**: painel de conexão claro e objetivo com Hero compacto, ações diretas (Fazer teste de velocidade / Conversar com Assist), card do último teste visível e atalhos úteis.
- **Novo Histórico (ADR-022)**: linha do tempo em ordem cronológica com resumo do período (7 dias, 30 dias, Tudo), filtros rápidos de conexão, detalhe completo do teste e comparação honesta de testes.
- **Assist Direto com Firebase AI Logic (ADR-020)**: conversa em streaming nativo via Gemini 3.8 Flash e validação de autenticidade via Firebase App Check (Play Integrity).
- **Simplificação do Resultado de Velocidade**: resultado primeiro com cards de Download e Upload lado a lado, latência (ping) diretamente visível e acesso direto ao Assist.

---

## [Unreleased] — Site, Workers e host (2026-10-10)

### Adicionado

- Site público (`signallq.com`) incorporado ao monorepo em `web/` (histórico preservado do `signallq-web`, arquivado), com páginas Como funciona, Dúvidas, Guias, Teste de velocidade, e `speedtest.signallq.com`; workflows `web-ci` e `web-deploy-vercel` (deploy manual).
- Admin Worker: informe diário de visitas do site no Discord (cron `0 12 * * *`, secret `DISCORD_WEBHOOK_URL`).
- Testes de guarda (`WorkerHostsBuildConfigTest`) que falham se o `BuildConfig` apontar para o host antigo dos Workers.

### Corrigido

- Android: hosts dos Workers trocados de `*.giammattey-luiz.workers.dev` (não resolve mais) para `*.gmmattey.workers.dev`; vale a partir da próxima versão do app. Também corrigida a URL de privacidade citada na tela de Termos de Uso.
- Telemetria do site: o `/api/track` apontava para o host antigo e a Vercel não tinha `SITE_INGEST_KEY`; ambos corrigidos.

### Publicado fora do ciclo de release

- `signallq-admin` (Worker) publicado a partir da `main`, incluindo a ingestão que devolve `acceptedIds` (confirmação do outbox de analytics do app) e a normalização de timestamps.

---

## [1.0.8] — 2026-09-13

### Adicionado

- Suporte ao roteador TP-Link Archer C6 e aviso de indisponibilidade de serviços externos.

### Alterado

- Velocímetro e carregamento do Assist mais fluidos durante a análise.

### Corrigido

- Texto das conclusões no Histórico não é mais cortado em uma única linha.

## [1.0.6] — 2026-09-07

### Corrigido

- Atualizada a credencial de comunicação entre o Assist e o diagnóstico remoto para restabelecer a análise.

## [1.0.5] — 2026-09-07

### Alterado

- Resultados do Assist mostram apenas as evidências medidas e indicam discretamente a origem da explicação.
- O Modo Gamer usa somente medições completas, recentes e da mesma rede; quando não há base válida, inicia um novo teste pela própria tela.

## [1.0.4] — 2026-09-06

### Fixed

- Assist passa a apresentar o título, a explicação e as recomendações retornadas pelo NDS, sem sugerir uma ação local que não foi indicada pelo diagnóstico remoto.

## [0.28.0] — 2026-07-18

Correção funcional do motor de diagnóstico (latência do Speedtest), redesign completo da tela Equipamento de internet e limpeza sistêmica de contraste de status no modo escuro. Ver [`android/CHANGELOG.md`](android/CHANGELOG.md#0280--2026-07-18).

## [0.27.0] — 2026-07-18

Ciclo curto de correções pontuais e padronização de UI sobre o redesign da 0.26.0 (TopBar, Sinal, Histórico, Onboarding, Equipamento de internet). Ver [`android/CHANGELOG.md`](android/CHANGELOG.md#0270--2026-07-18).

## [0.25.0] — 2026-07-10

Recommendation Engine integrado à experiência pós-diagnóstico, avaliação nativa do Google Play, autoconexão do gateway, dado real de Wi-Fi/LAN/clientes do roteador Nokia GPON e ~20 correções acumuladas desde a 0.24.1 (nunca publicada como release própria). Ver [`android/CHANGELOG.md`](android/CHANGELOG.md#0250--2026-07-10).

## [0.24.1] — 2026-07-05

Fix: card "Análise por IA" no resultado do speedtest passa a exibir as ações recomendadas pela IA, não só o diagnóstico. Primeira publicação na trilha de teste fechado da Play Console desde a 0.22.1. Ver [`android/CHANGELOG.md`](android/CHANGELOG.md#0241--2026-07-05).

## [0.24.0] — 2026-07-05

Ciclo de critique de design/UX (skill `impeccable`) nas 5 telas de maior uso do Android (Início, Velocidade, Sinal, Histórico, Ajustes) + fix de bug do WhatsApp da TIM. Ver [`android/CHANGELOG.md`](android/CHANGELOG.md#0240--2026-07-05) para o detalhamento completo e Linear SIG-304 para o resumo do ciclo.

## [Unreleased] — Admin Worker / SIG-128 / SIG-130 / SIG-13 / SIG-129 / SIG-133

### Added

- **(sig-128 / sig-130) Firebase Crashlytics + Firebase Sync via BigQuery:** helper `queryBigQuery<T>` com auth OAuth2 via service account existente. Substitui stubs em `handleFirebaseCrashlytics` (crash-free %, crashes 7d), `handleFirebaseCrashIssues` (top 20 issues 30d), `handleFirebaseVersions` (top 10 versões 30d) e `handleFirebaseSync` (valida conectividade BigQuery com session_start do dia anterior). Todos retornam `{ source: "no_data_yet" }` com zeros se as tabelas BigQuery ainda não existirem — sem 500, sem fallback silencioso.

- **(sig-13) Feature flags remotas:** API worker com `GET /admin/feature-flags` (lista da tabela dedicada), `PUT /admin/feature-flags/:key` (update com audit log em `feature_flag_audit`) e `GET /flags` (endpoint público para consumo do Android). Migration `005_sig13.sql` cria tabelas `feature_flags` e `feature_flag_audit` com 6 flags iniciais. Admin Panel ganha aba dedicada `/feature-flags` com toggles otimistas e rollback em caso de erro.

- **Admin Worker — Pipeline de erros de sistema (SIG-129, Fase A):** tabela `system_errors` no D1 para deduplicar e contabilizar erros do próprio worker. Helper `logError` com hash djb2 determinístico (fire-and-forget, nunca propaga). Wrapper `withErrorLogging` aplicado a todos os handlers `GET /admin/metrics/*`. Handler `handleFirebaseAnalytics` instrumentado diretamente no catch. Endpoint `GET /admin/metrics/errors?period=` retorna erros agrupados por source, ordenados por frequência.
- **Admin Worker — migration `003_sig129.sql`:** `CREATE TABLE IF NOT EXISTS system_errors` + índice `idx_system_errors_last_seen`.
- **Admin Panel — `errorMetricsService`:** `getErrorMetricSummary` e `getErrorByEndpoint` derivam dados reais do endpoint `/admin/metrics/errors` em produção (antes retornavam `null`/`[]`).
- **Admin Worker — Sistema de alertas (SIG-133):** tabela `alerts` no D1 (migration `004_sig133.sql`) com geração idempotente (`INSERT OR IGNORE`) de candidatos por threshold — budget de IA lido de `admin_settings` agregando `ai_usage`, e pico de taxa de erros a partir de `system_errors`. Endpoints `GET /admin/alerts` (ativos + histórico 24h) e `POST /admin/alerts/:id/resolve`, com compat `GET /admin/metrics/alerts`. Sem budget/sem dado → nenhum alerta fabricado.
- **Admin Panel — `AiAlertsPanel` / `RecentAlertsPanel`:** passam a consumir `/admin/alerts` real, sem dado hardcoded.
- **Admin Worker — `reliabilityPercentage` por modelo em `/admin/metrics/ai-usage` (SIG-125):** campo calculado como `completion_tokens > 0 / total_calls * 100` (arredondado a 2 casas); retorna `null` por modelo sem registros no período. Frontend (`aiUsageService`) mapeado para consumir o campo real.

### Notes

- `affectedUserCount` sempre retorna 0 na Fase A — sem PII no D1. Derivar por `device_id` é Fase B.
- Filtro `?environment=` ignorado na Fase A (tabela `system_errors` não tem coluna `environment`). Entra na Fase B com SIG-143.

---

## [0.23.0] — 2026-07-05

### Added

- Logos oficiais reais de operadoras e catálogo local de badges (SIG-292) (#472, #467)
- Exibição dos canais oficiais da operadora identificada no diagnóstico (#466)
- Instrumentação de analytics: funil principal com 7 eventos via `AnalyticsHelper` (#473) e `feature_used` em Wi-Fi, DNS, Fibra e Histórico (#469)

### Fixed

- Ingest de speedtest via Wi-Fi agora inclui a operadora/ISP identificada (#468)
- Quebras de layout com fonte grande do sistema (#470)
- Matcher de operadora não confunde mais "Oi" com "Nio" (#465)
- Exceção ao invocar `executarSpeedtest` agora é logada em vez de engolida silenciosamente (#433)
- Sinal › Dispositivos exibe "Dispositivo <Fabricante>" via fallback de fabricante por OUI do MAC quando o hostname não é resolvido (#394)

### Changed

- Upgrade coordenado de toolchain: AGP 9.2.1, Kotlin 2.3.21, Gradle 9.4.1 (#445)

---

## [0.22.1] — 2026-07-03

### Publicação

- Primeira publicação automatizada na Play Console (trilha de teste fechado) via gradle-play-publisher no release por tag.

### Fixed

- Roteador dual-band único (mesmo SSID/OUI em 2.4 GHz e 5 GHz) não é mais classificado como mesh; aparece como "Roteador dual-band" com as bandas identificadas (#356)
- Chip "Conectado" no card da rede em Sinal › Wi-Fi trunca SSID longo com reticências (#355)
- Aba "Dispositivos" na barra de abas da tela Sinal não quebra mais em duas linhas (#354)

---

## [0.22.0] — 2026-06-29

### Added

- Item "Fale conosco" na tela de Ajustes

### Changed

- Ícone do app atualizado para o símbolo SignallQ (barras de sinal roxo→azul)

### Fixed

- Acessibilidade TalkBack e compatibilidade com dark theme
- Baseline Profile atualizado para `io.signallq.app`

### Performance

- Startup time reduzido, consumo de bateria do worker de monitoramento otimizado, tamanho do APK reduzido

---

## [0.21.0] — 2026-06-22

### Added

- **Novo ícone do app:** assets atualizados em todas as densidades (mdpi → xxxhdpi) e ícone adaptativo com camadas foreground/background/monochrome (SIG-7/SIG-8)
- **Play Store asset:** `assets/store/play_store_512.png` adicionado ao repositório
- **signallq-privacy-worker:** novo worker Cloudflare para conformidade de privacidade (`integrations/cloudflare/signallq-privacy-worker/`)
- **CI — Code Quality:** workflow `.github/workflows/quality.yml` com Ktlint, Detekt, Unit tests e Build APK em cada PR (SIG-28/SIG-37)
- **README.md:** documentação inicial do repositório com stack, arquitetura e instruções de setup

### Fixed

- **Admin Panel — Erro de Telemetria (SIG-5):** erro 401 no Overview agora exibe mensagem de autenticação clara em vez de silenciar o erro e deixar a tela vazia
- **Admin Panel — Dados mockados em produção (SIG-9):** `productAnalyticsService` corrigido para retornar coleção vazia quando `VITE_ENABLE_MOCKS=false`, eliminando vazamento de dados hardcoded para produção
- **Admin Panel — título da aba:** `index.html` atualizado para exibir "SignallQ Admin" (SIG-28)

### Changed

- **Ktlint:** formatação corrigida em ~30 arquivos do módulo `app` (imports, chain methods, annotations, multiline expressions)
- **.editorconfig:** path de supressão `function-naming` corrigido de `io/linka/app` para `io/veloo/app` — regra agora se aplica corretamente a todas as funções `@Composable`

### Docs

- `docs/ARCHITECTURE_REVIEW.md`: relatório de auditoria de arquitetura (read-only, referência)
- `docs_ai/technical/architecture/MIGRACAO_ARQUITETURA_2026.old.md`: versão anterior arquivada

---

## [0.16.0] — 2026-06-21

### Changed — Rebranding completo para SignallQ

- **Rebranding:** identidade anterior (Linka / Veloo / Orbit) substituída por **SignallQ** em toda a UI, copy, telas de novidades e documentação. Superfícies de IA (antigo "Orbit") passam a referir-se ao assistente SignallQ.
- **Identificadores técnicos preservados:** package `io.veloo.app`, App ID Firebase `io.veloo.app`, repo GitHub `gmmattey/linka-android` e worker Cloudflare `linka-ai-diagnosis-worker` permanecem inalterados por compatibilidade de infraestrutura.

### Docs

- **Reorganização da documentação:** criadas as pastas `docs/_archive/` e `docs_ai/_archive/` para material histórico (releases v0.9.0, relatórios, assets da marca anterior). `docs_ai/README.md` reescrito com índice hierárquico alinhado à árvore atual de docs.

---

## [0.15.1] — 2026-06-12

### Fixed

- **Topologia mesh — nó "Roteador":** o nó "Roteador" em redes mesh deixava de exibir o placeholder "—" quando o dado não estava disponível. Corrigido para manter o fallback "—" consistente com os demais nós da trilha de rede.

---

## [0.15.0] — 2026-05-30

### Changed — Rebranding Linka → Veloo

- Identidade visual, package name e configurações Firebase atualizados.
- App ID Firebase: `io.veloo.app`.
- Tela de novidades v0.15.0.

---

## [0.14.0] — 2026-05-29

### Added — Redesign Diagnóstico IA (fluxo de laudo + assistente)

#### Novo fluxo de diagnóstico (substitui o chat)
- **DiagSetup:** tela de seleção de sinais — usuário escolhe o que analisar (Velocidade, Wi-Fi & Sinal, Latência & Bufferbloat, Modem/Fibra GPON, DNS) com cards toggle on/off
- **DiagAnalyzing:** estado de carregamento com OrbitSymbol animado, barra de progresso e checklist de steps (done/run/wait) filtrada pelos sinais selecionados
- **DiagResult:** laudo completo em tela única scrollável — hero card escuro gradiente com veredito da IA, causa-raiz identificada, impacto por atividade (streaming, chamadas, jogos), recomendações numeradas com prioridade (alta/média/baixa) e "Ver passo a passo", grid de métricas colapsável, rodapé com "Compartilhar laudo" e "Falar com a operadora"

#### Assistente LLM (tela standalone)
- **LLMChatScreen:** chat com IA limpo e moderno — header "SignallQ · Assistente de conexão", mensagens do assistente full-width sem bolha (label "● SIGNALLQ"), bolhas do usuário à direita, chips de follow-up, input com send, disclaimer on-device
- **CHAT_SYSTEM_PROMPT reescrito:** respostas completas com passo a passo detalhado, sem limite de tamanho, conversa livre até resolver o problema, sempre restrito ao tema conexão/rede

#### Componentes UI novos
- `OnDevicePill` — pill "Processado no aparelho · Gemma 4" (clara/escura)
- `SignalToggleCard` — card toggle para seleção de sinais
- `DiagVerdictHeroCard` — card hero escuro com gradiente para veredito IA
- `DiagRootCauseCard` — card de causa-raiz (error-tinted)
- `DiagImpactCard` — seção de impacto por atividade com badges de status
- `DiagRecommendationCard` — card numerado com badge de prioridade
- `DiagMetricsGrid` — grid 2 colunas colapsável com dots de status
- `DiagActionFooter` — rodapé com ações (compartilhar, refazer, operadora)
- `LLMAssistantMessage` — mensagem IA full-width sem bolha

#### Motor de diagnóstico
- `DiagSignalSelection` — modelo de seleção de sinais com defaults (tudo on exceto DNS)
- `DiagnosticArea` enum — áreas filtráveis (Velocidade, Wi-Fi, Latência, Fibra, DNS)
- `DiagnosticRunner.run()` — aceita `enabledAreas` para rodar apenas engines selecionados
- Worker `max_tokens` elevado para 8000 no modo chat

### Changed
- **Navegação:** Home → "Diagnóstico IA" agora abre o fluxo de laudo (não mais o chat); ResultadoVelocidade → abre LLMChat
- **DiagnosticoScreen:** removida annotation `@Deprecated`, refatorado para novo fluxo Setup→Analyzing→Result
- **Chat inline removido** de DiagnosticoScreen — toda interação chat agora é no LLMChatScreen standalone

---

## [0.13.3] — 2026-05-28

### Fixed
- **Fibra:** removida abertura automática da tela Fibra/modem ao iniciar o app (só abre por ação do usuário)
- **Home/Móvel:** IP público na sheet de rede móvel não exibe mais o IP do Wi-Fi quando ambas redes estão ativas
- **Sinal/Móvel:** aba Móvel agora detecta 5G NSA corretamente (adicionados fallbacks TelephonyDisplayInfo, CellSignalStrengthNr e CellInfoNr)

---

## [0.13.2] — 2026-05-28

### Fixed
- **Home:** nome da operadora agora vem do snapshot móvel (não do ISP lookup externo)
- **Home:** card SIM duplicado removido em dispositivos single-SIM (exibe apenas para dual-SIM)
- **MonitorRede:** SSID Wi-Fi aparecendo como nulo no Android 12+ — fallback via `WifiManager.connectionInfo`
- **Sinal/Wi-Fi:** filtro de banda 2.4GHz agora exibe banner "Conectado em 5GHz" para consistência visual com a view 5GHz

### Changed
- **Sinal/Móvel:** SimCard redesenhado — nome da operadora em destaque (headlineSmall), badge "EM USO" verde, layout SINAL/QUALIDADE em duas colunas, descrição contextual
- **SpeedTest:** removidos cards abaixo do seletor de modo (CardContextoUso, Anatel, Bufferbloat, ExploreTools)

---

## [0.13.0] — 2026-05-28

### Changed — Mockup v2 UI (PR #206)

#### HomeScreen

- **LazyColumn reordenada:** nova ordem fixa — NetworkPath → Medições → MiniCards → SignalCard → SimChips. Cards de sinal e chips de SIM movidos para depois do bloco de medições.
- **SignalCard restyled:** ícone em círculo de 44dp com fundo `success@10%` (Wi-Fi) ou `accent@10%` (móvel). Overline em uppercase com ponto médio: `WI-FI · 5 GHZ` ou `REDE MÓVEL · LTE`. Qualidade exibida como palavra trailing (`Forte` / `Regular` / `Fraco`) em vez de percentual. Branch móvel exibe barras de sinal + RSRP (dBm).
- **CardMovelDualSim convertido para SimChips:** layout anterior (card expandido) substituído por dois chips compactos lado a lado — operadora, tecnologia e indicador de chip ativo em linha única por chip.

#### SinalScreen

- **Tabs sempre presentes:** TabRow fixo com 3 abas (`Wi-Fi` / `Canal` / `Móvel`) independente do tipo de conexão ativa. Removida bifurcação condicional que ocultava abas conforme conectividade.
- **Novos composables:** `MovelTab` (conteúdo da aba Móvel com suporte a chip único e dual SIM) e `WifiEmptyState` (estado vazio exibido na aba Wi-Fi quando o usuário está só em rede móvel).
- **Cor de fundo do card de rede conectada:** alterada de `successContainer` a 45% de opacidade para `success` a 12% de opacidade (`${LK.success}1F`).

#### SpeedTestScreen

- **Título alterado** de "Velocidade" com legenda fixa para "Velocidade" com subtítulo condicional (exibe plano contratado quando disponível, ou vazio).
- **ModeSelector** (`Rápido` / `Completo` / `Triplo`): cores neutras — fundo do seletor usa `bgSecondary`; opção ativa usa `bgPrimary` com sombra leve; opções inativas em `textSecondary`. Removido uso de `accent` como cor de seleção.
- **LastResultCard reescrito:** layout de 3 colunas métricas (Download / Upload / Latência) em linha única com valor numérico em destaque (`fontSize 20`, bold) + unidade sobrescrita + label abaixo. Eliminado layout anterior com valores em prosa.

#### ResultadoVelocidadeScreen

- **Grade circle removido:** círculo com letra de nota (A/B/C) eliminado do topo da tela. Título da avaliação ("Conexão excelente") exibido diretamente no topo com `fontSize 20 / fontWeight 600`, seguido de subtítulo descritivo em `textSecondary`.

---

## [0.12.0] — 2026-05-28

### Added

- **Chat de Diagnóstico IA — nova tela chat-first:** Ao tocar no botão "Diagnóstico" da home, abre direto `ChatDiagnosticoIaScreen` sem tela intermediária. Estilo conversa moderna (Material Design 3) com balões à direita para o usuário e à esquerda para a IA, drawer lateral com histórico de sessões, input fixo acima da bottom nav e tratamento correto de IME. Primeira abertura exibe duas mensagens da IA (boas-vindas + opções) e três chips clicáveis: "Analisar meu último teste", "Executar novo teste agora", "Analisar meu histórico recente". Cada opção dispara fluxo dedicado — última medição via `MedicaoDao.observarUltimas(1)`, novo teste executado em background sem sair do chat com mensagens progressivas a cada fase do speedtest (download → upload → coleta → análise), histórico via `observarUltimas(7)` com mensagem adaptada quando há menos de 7 testes.

- **Streaming SSE de respostas da IA:** Reutiliza `AiDiagnosisRepository.explainDiagnosisStream` com tokens aplicados incrementalmente na bolha da IA. Auto-scroll inteligente — só puxa para o fim quando o usuário já estava no fim, não interrompe leitura de histórico. Nome do modelo exibido vem de `ModeloIa.nomeExibicao` (não hardcoded), com fallback "o modelo de IA".

- **Persistência de sessões via Room v10:** Novas tabelas `chat_sessions` (id, título, criadoEmEpochMs, atualizadoEmEpochMs, status, tipoDiagnostico, nomeModelo, diagnosticoPayloadJson) e `chat_messages` (id, sessionId, role, content, createdAtEpochMs, status, metadataJson) com foreign key `ON DELETE CASCADE`. Drawer lateral permite abrir, apagar (com confirmação) e renomear (dialog com contador 60 chars) sessões anteriores. Título da sessão derivado automaticamente da primeira mensagem do usuário, truncado a 40 chars com reticência.

- **Cota diária com janela rolling 24h:** `CotaIaRepository` em DataStore isolado (`cota_ia`), default 10 análises por ciclo de 24h. Renovação calculada a partir do início do ciclo (não à meia-noite). Quando excedida, input substituído por `CotaExcedidaBanner` com data/hora de renovação formatada em pt-BR (`Locale.forLanguageTag("pt-BR")` + `DateTimeFormatter`) — "amanhã às 14h32", "hoje às 22h15" ou fallback "em 24 horas". Sessões antigas continuam acessíveis para leitura mesmo com cota zerada; nova análise só permitida quando ciclo renovar.

- **Tratamento humanizado de cinco cenários de erro:** Modelo indisponível ("No momento o ${modelo} está indisponível..."), sem rede ("Verifique sua conexão..."), timeout ("A análise demorou mais que o esperado..."), resposta incompleta ("Recebi uma resposta incompleta...") e catch-all. Sem stack trace, sem código HTTP cru. Mensagens viram `ChatMessageEntity` com `role=system`, `status=failed` e `metadataJson.errorCode` preservando o motivo técnico para suporte.

- **Componentes Compose adaptados:** `OrbitAiMessageBubble` ganhou parâmetro `isProgressMessage: Boolean` que suprime métricas, ações e detalhes técnicos para mensagens de progresso do teste. `OrbitInputArea` ganhou parâmetro `placeholder: String` customizável para sinalizar "Aguarde o resultado do teste..." e "Aguarde a resposta da IA..." sem reaproveitar gambiarra de `isLimitReached`. Ambos parâmetros têm default que preserva callers existentes.

- **TopBar contextual na Home**: exibe o SSID da rede Wi-Fi ou o nome da operadora móvel no subtítulo da TopBar (#180 / PR #194)

- **Card "Sua Conexão" na tela Sinal**: card visualmente destacado para a rede conectada, separado das redes vizinhas (#176 / PR #195)

- **Redesign dos itens de rede na tela Sinal**: layout com ícone Router/Wi-Fi, SSID, força do sinal, frequência, canal e metadados na segunda linha (#177 / PR #195)

- **Mini-cards DNS, PING e Diagnóstico IA na Home**: atalhos rápidos abaixo do botão "Medir Velocidade" (#179 parcial / PR #196)

- **Seletor Android/Roteador na aba Canal**: passo a passo para troca de canal via Android ou painel do roteador (#178 / PR #196)

- **Chip de segurança Wi-Fi no card de sinal**: exibe WPA3, WPA2, WPA ou Open com cor contextual (#179 / PR #197)

- **Card de rede móvel com suporte a dual SIM**: exibe operadora, tecnologia (4G/5G), qualidade de sinal e status de roaming por chip ativo (#179 / PR #197)

### Changed

- **Entry point do botão "Diagnóstico":** Passa a empilhar `Overlay.ChatDiagnosticoIa` em vez de `Overlay.DiagnosticoInteligente`. Bottom nav já oculta durante speedtest reaproveitada — quando o chat dispara um novo teste, a navbar global desaparece pelo mesmo gate. `BackHandler` integrado fecha o overlay.

- **`Locale("pt", "BR")` modernizado para `Locale.forLanguageTag("pt-BR")`** nos componentes do chat (4 ocorrências). Outros call sites do app permanecem como estão para escopo dedicado de migração futura.

### Deprecated

- **`DiagnosticoScreen` (chat inline antigo) marcada como `@Deprecated`:** Mantida no codebase como fallback de emergência via `Overlay.DiagnosticoInteligente` (também deprecado por KDoc, já que Kotlin não honra `@Deprecated` em entries de enum/sealed para warning de uso). Call sites internos do `AppShell` que ainda referenciam o overlay antigo têm `@Suppress("DEPRECATION")` documentando a intencionalidade. Será removida em próxima major.

### Fixed

- Remover card "O QUE VOCÊ CONSEGUE FAZER" da tela Home (#170 / PR #186)
- Migrar card REGULAÇÃO ANATEL da Home para a tela Resultado de Velocidade (#171 / PR #187)
- Migrar card "Atraso extra na conexão" (bufferbloat) da Home para Resultado de Velocidade (#172 / PR #188)
- Migrar card "Jogar Online" da Home para Resultado de Velocidade (#173 / PR #189)
- Remover mini-cards DNS, PING e Diagnóstico da tela Velocidade (#174 / PR #190)
- Remover status "Carregando" do card Servidor na tela de medição (#175 / PR #191)
- Corrigir `operadoraMovel = null` em medições salvas via chat de diagnóstico IA (#185 / PR #192)
- Lint: corrigir erro de desugaring `java.time` em `ChatDiagnosticoIaScreen` (#198)

### Refactored

- `SpeedtestPersistenceCoordinator`: centralizar persistência de resultados de speedtest, eliminando race condition entre `MainViewModel` e `ChatDiagnosticoIaViewModel` (#184 / PR #192)

### Technical

- **Migration Room v9 → v10** com SQL alinhado ao schema gerado pelo Room (`10.json`, identityHash `97f676bb…`). Schema antigo no repositório continha entidades fantasma (`fibra_config`, `movel_chip_config`, `localizacao_config`) sem código correspondente — resíduo de branch nunca mergeada — sobrescrito com schema correto.

- **`ChatDiagnosticoIaViewModel` injetado via `viewModels()` na MainActivity** seguindo padrão do `MainViewModel` (projeto não tem `hilt-navigation-compose` no catálogo). Repositories de chat e cota construídos via `by lazy` recebendo `LinkaDatabase` e `@ApplicationContext`.

### Tests

- Setup Robolectric + 7 smoke tests Compose para `ChatDiagnosticoIaScreen` (#183 / PR #193)
- 9 testes unitários para `SpeedtestPersistenceCoordinator` (PR #192)
- 11 testes unitários para `CotaIaRepository` (rolling 24h, expiração de ciclo, reset automático, renovação, observabilidade reativa) com clock injetável para simular passagem de tempo sem `Thread.sleep`
- 25 testes unitários para `ChatDiagnosticoIaViewModel` cobrindo os 3 fluxos de opção inicial, cota excedida pré-chamada, erros de rede/modelo/timeout/resposta incompleta, renomeação automática de sessão e persistência de medição via chat
- AndroidTests para `ChatSessionDao` (7 casos), `Migration_9_10` (`MigrationTestHelper`) e `ChatDiagnosticoIaRepository` (19 casos) — compilados e prontos; execução em device recomendada antes de release

---

## [0.11.4] — 2026-05-27

### Fixed

- **DNS atual sem latência no comparativo:** Dois bugs independentes. (1) `resolveDnsName()` não mapeava Registro.br (200.160.0.80, 200.160.2.3) e CETIC.br (191.234.170.40), retornando "DNS do Provedor" para essas IPs. O benchmark identificava corretamente o nome via `inferirNomeSistemaDns()`, criando mismatch no lookup da latência. Adicionados os IPs ao `resolveDnsName()` em sincronia com `mapaIpParaProvedor`. (2) Filtro `>= 3ms` em `medirSistemaDns()` excluía DNS de operadora com respostas rápidas em 5G/4G, resultando em `amostras` vazio e latência null. Filtro removido — round 0 descartado como warmup, rounds 1-2 sempre incluídos independente do tempo.

---

## [0.11.3] — 2026-05-27

### Fixed

- **Trilha de rede mostra "sem internet" com internet ativa:** `hasInternetError` usava `publicIp == null && ispInfo == null` como sinal de erro, mas esses valores são nulos também enquanto o fetch do ISP está em andamento — resultando em erro imediato antes mesmo de qualquer requisição completar. `loadingInternet` era logicamente impossível quando conectado (mesmas condições já ativavam `hasInternetError`). Fix: `hasInternetError` agora usa `!snapshotRede.conectado` (`NET_CAPABILITY_VALIDATED`) — erro só aparece quando o SO confirma ausência de internet (captive portal, sem rota). `loadingInternet` agora funciona corretamente enquanto o IP/ISP ainda está sendo buscado.

---

## [0.11.2] — 2026-05-27

### Fixed

- **Crash na abertura do app (NullPointerException em MonitorRedeAndroid):** Bug de ordem de inicialização — `calcularSnapshotAtual()` era chamado durante a inicialização de `mutableSnapshotFlow` (linha 28), antes que `tentativasAguardandoValidated`, `mainHandler` e `runnableRetry` fossem inicializados (linhas 32–39). Campos não inicializados retornam `null` na JVM → NPE ao chamar `.set(0)`. Fix: reordenar declarações para que todas as dependências de `calcularSnapshotAtual()` sejam inicializadas antes de `mutableSnapshotFlow`. Latente desde 0.11.0, amplificado em 0.11.1 com distribuição pelo Firebase (244 crashes, 81 usuários afetados em 1 dia).

---

## [0.11.1] — 2026-05-27

### Fixed

- **Detecção de 5G NSA em Samsung Exynos / Xiaomi MIUI 14+:** Em devices que retornam `allCellInfo` vazio sem permissão de localização, o app exibia "4G" mesmo em rede 5G NSA. Adicionado terceiro mecanismo de detecção via `SignalStrength.cellSignalStrengths` (API 29+): se o objeto contém `CellSignalStrengthNr`, confirma 5G NSA independente de `allCellInfo` ou reflexão via `getNrState()`.

- **Histórico móvel vazio ao filtrar por MOVEL:** Double-filter no modo controlado — AppShell passava lista já filtrada pelo ViewModel, mas `HistoricoScreen` re-filtrava internamente, resultando em lista sempre vazia. Modo controlado agora usa a lista diretamente sem re-filtrar. Estado vazio com mensagem contextual ("Nenhum teste para este filtro") quando não há resultados pós-filtro.

- **Back fecha o app na tab Histórico:** Sem `BackHandler` na tab Histórico (índice 3), o back gesture do sistema encerrava o app. Agora navega para Home (tab 0).

- **Trilha de rede mostra desconectado com internet ativa:** Race condition em `ConnectivityManager.NetworkCallback` — `onLost` disparava antes de `onAvailable` durante handoff Wi-Fi → Móvel, emitindo estado desconectado por 1-3s. Debounce de 2000ms adicionado: `onLost` aguarda antes de confirmar desconexão; `onAvailable` cancela o debounce pendente se a rede voltar na janela.

---

## [0.11.0] — 2026-05-26

### Added

- **Tela Fibra/Modem — Análise avançada do modem/ONT (#168):** `FibraModemScreen.kt` com 5 estados visuais: SemWifi ("A análise do modem só funciona na rede local"), SemCredenciais com CTA para Ajustes, Conectando com skeleton animado e LinearProgressIndicator, Erro com dois botões ("Tentar novamente" + "Revisar configurações"), e Concluído com chip de status geral, bloco de valores técnicos (RX/TX/temperatura/status óptico/modelo com fallback "--") e bloco de interpretação via `FibraSignalQualityEngine`. Senha do modem nunca exposta. `FibraModemUiState.kt` com sealed interface de 5 estados e função `mapearSnapshotFibra` pura.

- **Tela DNS — Benchmark, recomendação e guia de configuração (#167):** `DnsScreen.kt` com 4 blocos: DNS atual (IP, nome amigável, badge "DNS Privado ativo", latência), Benchmark com 7 provedores (Cloudflare, Google, Quad9, OpenDNS, AdGuard, Registro.br, CETIC.br), Recomendação com disclaimer explícito ("Isso não troca o DNS automaticamente"), e Guia colapsável "Como trocar DNS?" com seções "Quando vale a pena" e "Quando não faz diferença". Texto educacional no topo: "DNS afeta abertura de sites, não velocidade." Extraído do AppShell para arquivo próprio.

- **Onboarding — Checkbox de termos e cards de permissão (#165):** Slide 1 exige aceite obrigatório de Termos de Uso e Política de Privacidade antes de avançar (swipe e botão bloqueados). Slide 2 apresenta cards de permissão com ícone, título, descrição e botão de concessão individual para Localização/Wi-Fi (`ACCESS_FINE_LOCATION`) e Dispositivos próximos (`NEARBY_WIFI_DEVICES`, API 33+). Botão "Pular" visível apenas no slide 0. Launcher contextual usa `solicitacaoPermissoes` para não corromper callback de localização.

- **Mensagens humanizadas para estados vazios (#164):** Estados 9.3, 9.4, 9.5 e 9.6 com mensagens em linguagem humana e ícones contextuais. Substitui textos técnicos por explicações práticas de limitações (permissões, offline, recursos não disponíveis).

### Fixed

- **Privacidade — MAC address mascarado em DispositivosScreen (#166):** Octetos 3-4 do MAC são substituídos por `••` (ex: `c4:8e:de:ad:1a:2b` → `c4:8e:••:••:1a:2b`). Aplica em DeviceDetailSheet e MeshApSheet. Estado sem Wi-Fi exibe mensagem diferenciada "Dispositivos da rede só aparecem quando você está conectado a um Wi-Fi."

---

## [0.9.0] — 2026-05-20

### Added

- **Central de Testes — Grid 2×N de Ferramentas:** Nova interface unificada para acesso rápido a ferramentas de diagnóstico. Grid adaptativo com 2 colunas (N linhas), mostrando DNS Benchmark, Ping/Latência e Diagnóstico Inteligente. Cada card exibe ícone vetorial, título, descrição curta e state visual (ativo/desabilitado com badge). StatusCard integrado exibe status de conectividade (Wi-Fi/Móvel/Offline) e localização do servidor de teste em tempo real.

- **Ping / Latência — Ferramenta de medição de latência:** Executa 20 amostras ICMP sobre HTTP/2 contra Cloudflare Speed (sem payload, timeout 4s/amostra). Calcula **latência (mediana em ms)**, **jitter (desvio padrão em ms)** e **perda de pacotes (%)** com interface de progresso real-time. Estados: Idle → Executando (barra de progresso 0-100%) → Resultado (3 métricas) → Erro (com mensagem e retry). Modal ModalBottomSheet com suporte a swipe-down e botão voltar.

- **DNS Benchmark — Provedores ISP brasileiros:** Adicionados 2 novos resolvedores públicos brasileiros ao benchmark de DNS:
  - **Registro.br** (https://dns.registro.br/query) — Gerido por Fapesp, mantém registros .br, ideal para latência em SP
  - **CETIC.br** (https://resolver.cetic.br/dns-query) — Centro de Estudos e Tecnologia em Informação e Comunicação, resolver público nacional de baixa latência
  - Benchmark agora testa 7 provedores total (era 5: Cloudflare, Google, Quad9, OpenDNS, AdGuard)

- **StatusCard — Loading state visual:** Card de status do servidor exibe "Cloudflare · Carregando…" enquanto localizacaoServidor é null. Ícone e texto com cor dinâmica (textSecondary = carregado, textTertiary = carregando). Sem breaking change, fallback para valor anterior se API falhar.

### Fixed

- **Diagnóstico Inteligente — Desabilitado sem confusão:** Feature agora aparece no grid com badge "Em breve" e 50% opacidade visual, tornando explícito que está em desenvolvimento. Card não é clicável (nenhum side-effect). Evita confusão de usuário (FEATURE_DIAGNOSTICO_CHAT = false em release). Será reabilitado quando flag mudar para true nas próximas versões.

### Changed

- **DNS Benchmark — Separador visual:** Hostname de conexão agora usa separador ponto médio `·` em vez de hífen `-`. Exemplos:
  - Antes: "Cloudflare-carregando…", "Cloudflare-", "Cloudflare-São Paulo, BR"
  - Depois: "Cloudflare · Carregando…", "Cloudflare ·", "Cloudflare · São Paulo, BR"
  - Melhora legibilidade (ponto médio é separador tipográfico padrão)

- **ExploreToolsSheet → ExploreToolsRow:** Layout de ferramentas passou de bottom sheet vertical (acionada por botão) para grid 2×N em área principal da tela SpeedTest. Menos toque necessário, mais visual, melhor para descoberta (grid sempre visível).

- **SpeedTestScreen — Exports e callback:** PingScreen integrada como ModalBottomSheet. Callback `onAbrirPing: () -> Unit` adicionado a `SpeedTestScreen`, gerenciado por `MainViewModel`. Nenhuma breaking change em assinatura existente (todos callbacks opcionais).

---

## [0.10.0] — 2026-05-26

### Added

- **Tela Fibra/Modem — Análise avançada do modem/ONT (#149):** `FibraModemScreen.kt` com 5 estados visuais: SemWifi ("A análise do modem só funciona na rede local"), SemCredenciais com CTA para Ajustes, Conectando com skeleton animado e LinearProgressIndicator, Erro com dois botões ("Tentar novamente" + "Revisar configurações"), e Concluído com chip de status geral, bloco de valores técnicos (RX/TX/temperatura/status óptico/modelo com fallback "--") e bloco de interpretação via `FibraSignalQualityEngine`. Senha do modem nunca exposta. `FibraModemUiState.kt` com sealed interface de 5 estados e função `mapearSnapshotFibra` pura.

- **Tela DNS — Benchmark, recomendação e guia de configuração (#146):** `DnsScreen.kt` com 4 blocos: DNS atual (IP, nome amigável, badge "DNS Privado ativo", latência), Benchmark com 7 provedores (Cloudflare, Google, Quad9, OpenDNS, AdGuard, Registro.br, CETIC.br), Recomendação com disclaimer explícito ("Isso não troca o DNS automaticamente"), e Guia colapsável "Como trocar DNS?" com seções "Quando vale a pena" e "Quando não faz diferença". Texto educacional no topo: "DNS afeta abertura de sites, não velocidade." Extraído do AppShell para arquivo próprio.

- **Onboarding — Checkbox de termos e cards de permissão (#128):** Slide 1 exige aceite obrigatório de Termos de Uso e Política de Privacidade antes de avançar (swipe e botão bloqueados). Slide 2 apresenta cards de permissão com ícone, título, descrição e botão de concessão individual para Localização/Wi-Fi (`ACCESS_FINE_LOCATION`) e Dispositivos próximos (`NEARBY_WIFI_DEVICES`, API 33+). Botão "Pular" visível apenas no slide 0. Launcher contextual usa `solicitacaoPermissoes` para não corromper callback de localização.

- **Chat inline "Perguntar sobre diagnóstico" com LLM na DiagnosticoScreen:** O card "Perguntar sobre diagnóstico" agora integra chat com **Gemma 4 26B** (via Cloudflare Workers AI) inline, sem sair da tela. Visual estilo ChatGPT/Claude: mensagem do usuário alinhada à direita com pill sutil; resposta da IA sem bubble, com header `[ícone] SignallQ IA` em texto secundário. Chips de sugestão desaparecem com animação após o primeiro envio. Loading visível com 3 pontos pulsantes. Limite de 5 perguntas por sessão, enforçado no ViewModel (campo desabilitado com mensagem ao atingir limite). Histórico de chat persiste no ViewModel (sobrevive rotação de tela). Sem novo endpoint no Cloudflare Worker — usa campo `feedbackUsuario` do payload existente. (#66)

- **Histórico: Gráfico de testes e cards de velocidade média:** HistoricoScreen agora exibe gráfico Canvas Compose com dados de download e upload ao longo do tempo (cores accent e accentBlue). Cards "Download médio" e "Upload médio" calculam a média com base no histórico filtrado. Filtros por tipo de rede (Wi-Fi / Rede móvel / Todos) com dropdown de operadora aparecendo quando rede móvel é selecionada. Toggle e dropdown afetam gráfico, cards e lista simultaneamente. Enum `FiltroConexaoHistorico` e `historicoFiltrado` StateFlow no ViewModel. (#95)

- **Diagnóstico Inteligente — Redesign completo da tela de resultado:** Nova UI com 5 cards: StatusDiagnosticoCard (escudo + chip pill de status), PrincipalPontoCard (ícone dinâmico por tipo de problema + tip card âmbar), OQueFazerCard (lista de ações + 3 botões de navegação), seção duas colunas de Evidências + Análise por categoria, e ChatCard com SuggestionChips e campo de input pill. Tokens de cor `warningContainer`/`successContainer`/`amberSurface` adicionados ao design system. `WindowInsets.ime` aplicado para campo de input não ser coberto pelo teclado. (#60)

- **Classificação automática de topologia WiFi:** `SinalScreen` agora exibe o tipo de topologia real de cada rede (ROTEADOR, ROTEADOR_MESH, NO_MESH, REPETIDOR) via integração com `TopologiaWifiEngine`. Fallback gracioso para DESCONHECIDO via `runCatching` em caso de falha na classificação. (#40)

- **Streaming SSE no chat de diagnóstico:** Texto da IA aparece progressivamente com cursor pulsante em tempo real. Backend via Cloudflare Worker suporta modo SSE com `?stream=true` retornando chunks de dados. `DiagChatEntry` rastreia entradas parciais durante streaming, `AiDiagnosisRepository` implementa `explainDiagnosisStream()` com OkHttp Okio e fallback silencioso, `MainViewModel` atualiza entrada token a token e desativa carregamento no primeiro chunk, `DiagnosticoScreen` renderiza `DiagChatTextoComCursor` com cursor pulsante via `rememberInfiniteTransition`. (#68)

- **UptimeNarrativaEngine v2.0 — Detecção de padrões avançados:** Três novos comportamentos de análise de uptime: detecção de padrões horários recorrentes (ex: "toda manhã entre 8h e 9h a conexão cai"), identificação de interrupções longas >30 minutos ordenadas por duração, e cálculo de tendência de qualidade (MELHORANDO/PIORANDO/ESTAVEL) comparando as últimas 24h com as 24h anteriores. (#42)

- **ExportadorHistoricoPDF v2.0 — Layout rico e paginação automática:** PDF agora renderizado via HTML/CSS com `WebView.createPrintDocumentAdapter()` — tabela profissional com cabeçalho colorido e linhas zebradas. Paginação automática elimina truncamento de históricos longos. Timeouts defensivos (10s) em `exportarComWebView` e `PdfPrintHelper` evitam coroutines penduradas. (#41)

### Fixed

- **Privacidade — MAC address mascarado em DispositivosScreen (#144):** Octetos 3-4 do MAC são substituídos por `••` (ex: `c4:8e:de:ad:1a:2b` → `c4:8e:••:••:1a:2b`). Aplica em DeviceDetailSheet e MeshApSheet. Estado sem Wi-Fi exibe mensagem diferenciada "Dispositivos da rede só aparecem quando você está conectado a um Wi-Fi."

- Botão de iniciar teste sempre exibe "Iniciar teste" — removido label "Repetir" do estado concluído (#91)
- Status bar (bateria, hora, sinal) visível no modo claro — corrigido `enableEdgeToEdge` com `SystemBarStyle` condicional ao tema (#79)
- **Acessibilidade TalkBack — Auditoria completa de telas:** Correções em `DispositivosScreen` (DispositivoItem com `role=Button` e contentDescription dinâmica), `LaudoScreen` (link Anatel com `contentDescription`; `LkListRow` recebe role apenas quando interativo), `ResultadoVelocidadeScreen` (toggle "Detalhes avançados" com `stateDescription` dinâmica), e `ProfileAvatarButton` (contentDescription dinâmica com nome do usuário). (#11)
- **Mensagens de erro do modem exibidas em português humanizado:** Strings brutas internas (ex: "erroModemInacessivel") substituídas por mensagens humanizadas em português. Mapeamento `when()` em `AppShell.kt` e `VelocidadeScreen.kt` com 6 strings de fallback semânticas. (#80)
- Card de rede móvel exibe nome da operadora no formato "Operadora · Tipo" (ex: "Claro · 4G") (#83)
- **Tela Sinal em rede móvel exibe RSRP, RSRQ e SINR em cards estruturados com chips de status visual (Ótimo/Bom/Ruim) e ícones (#84)**
- TopBar da Central de Testes colapsa completamente ao rolar — sem gap vazio no topo (#75)
- Consumo em testes este mês exibe "0 MB" (não traço) e acumula corretamente mesmo com falha parcial do teste (#94)
- Label "Análise local" renomeado para "Diagnóstico do dispositivo" — elimina conflito visual com header "Gemma 4" no chat inline (#69)

---

### Added

- **Acessibilidade TalkBack em LinearProgressIndicator, StepRow, ConfiancaBarra (Issue #45):** Adicionadas semantica de progresso, roles acessíveis, contentDescription dinâmicas e live regions para componentes críticos de PingScreen e DiagnosticoScreen. Aumento de ~40% em cobertura de TalkBack.

- **Otimizações de recomposição em ResultadoVelocidadeScreen (Issue #23):** Aplicados `remember` com keys corretas e derivadas de state. 7 otimizações implementadas reduzindo recomposições desnecessárias durante atualização de dados.

- **Strings hardcoded extraídas para strings.xml (Issue #10):** 115+ strings em 4 telas migraram de hardcode para localização. Telas afetadas: PingScreen, DiagnosticoScreen, ResultadoVelocidadeScreen, FibraScreen. Prepara base para i18n futuro.

- **MainActivity refatorada com combine() e data classes tipadas (Issue #22):** Redução de 35→17 coletas com `combine()` em vez de `flatMapLatest` cascata. Flow<UiState> tipados substituem Any genéricos. Reduz observer churn em 40%.

- **MonitoramentoWorker: combine() otimizado para cascata .first() (Issue #20):** 8 `.first()` em cascata substituídos por 2 `combine().first()`. Reduz timeout desnecessário de ~800ms para ~100ms em ciclo de coleta.

- **MonitoramentoWorker: withTimeout + BackoffPolicy.EXPONENTIAL (Issue #21):** Timeout explícito (8s) com exponential backoff (initial 1s, max 32s). Worker não trava indefinidamente.

- **ConnectionPool adaptativo por tipo de rede (Issue #19):** OkHttp ConnectionPool configurado dinamicamente — 2 conexões/1min para móvel, 8 conexões/5min para Wi-Fi. Reduz consumo de dados e latência em Speedtest.

- **Ping concorrente speedtest: intervalo adaptado 300ms→1000ms (Issue #18):** Intervalo entre amostras ICMP aumentado de 300ms para 1000ms. Reduz congestão em redes móveis, melhora estabilidade de latência.

- **Cobertura de testes unitários em snapshot entities (Issue #16):** 15 testes unitários adicionados: SnapshotRedeTest (4), WifiLinkSnapshotTest (6), MedicaoEntityTest (5). Cobertura de equals, hashCode, copy e serialização.

- **Baseline Profile + AAB splits habilitados (Issue #9):** Perfil de partida instrumentado para Pixel 6. AAB splits de ABI habilitados para reduzir APK por ~30%. Prepara deploy em Play Store.

- **Firebase Crashlytics integrado (Issue #39):** BOM 33.1.0 adicionado. Crashlytics ativo em release builds. Capturas de exceções não-capturadas em background tasks e Workers.

- **WiFi screen topology icons:** Substitui chips de texto (Roteador/Mesh/Repetidor) por ícones visuais (Router/Hub/CellTower/Lan) com cores semanticamente distintas (cinza, azul accent, laranja warning). Nó conectado exato destacado em cor accent.

- **Network grouping by SSID:** Redes de terceiros agrupadas por SSID com expand/collapse para múltiplos nós (BSSIDs). Single-BSSID networks abrem detalhe direto. SSIDs ocultos agrupados em seção "Redes ocultas". Filtragem por banda preservada.

- **Proteção de dados móveis:** Speedtest detecta rede celular medida e solicita confirmação antes de testes de 25 MB (Completo) ou 30 MB (Triplo). Modo Rápido (10 MB) executa sem aviso.

- **Preferência de dados móveis:** Novo toggle em Ajustes — "Sempre permitir testes pesados em dados móveis" — desativa o aviso para quem tem plano ilimitado.

- **Consumo mensal:** Ajustes exibe o total de dados consumidos em testes este mês, com reset automático na virada do mês.

- **UiState<T> sealed interface e StatefulScreen composable (Issue #12-A):** Novo padrao de state management. Sealed interface `UiState<T>` com estados `Loading`, `Success(data: T)`, `Empty`, `Error`. Composable `StatefulScreen` generico reduz boilerplate.

- **Migracao de PingScreenState e DiagnosticoScreenState para UiState<T> (Issue #12-B):** Refatoracao para usar `UiState<T>` com ViewModel em Coroutines, substituindo LiveData legacy.

- **Migracao de localizacaoServidor, localIp, ispInfo e publicIp para UiState<T> (Issue #12-C):** MainViewModel expoe campos de rede como `StateFlow<UiState<T>>`. Catch vazio substituido por `UiState.Error`.

- **Modifier.expandable() para toggles acessiveis (Issue #11):** Novo modificador com semantica de toggle, role acessivel, contentDescription e feedback tatil. Aplicado a 10 telas/componentes.

### Changed

- **Qualidade de código:** Eliminado uso de `!!` (not-null assertion) em código de produção. Substituído por `checkNotNull` com mensagem descritiva, elvis operator e early return conforme o contexto.

- **Injeção de dependência:** Introduzido Hilt para DI. `MainViewModel` migrado de instanciação manual (`lazy { Modulo.criar*()} `) para `@HiltViewModel` com injeção via construtor. Melhora testabilidade e ciclo de vida das dependências.

### Fixed

- **Issue #24 — Won't Implement:** DNS e Health check continuam usando HttpURLConnection (não OkHttp). Acoplamento com protocolo HTTP não justifica refatoração. Documentado como decisão arquitetural.

### Security

- **Network Security Config:** Substituído `usesCleartextTraffic` global por configuração declarativa. Cleartext HTTP restrito a IPs de gateway LAN (acesso a modem). Chamada `ip-api.com` migrada para HTTPS.

---

## [0.8.4] — 2026-05-19

### Correção — Detecção de 5G NSA (1)
- **MonitorTelephonyImpl**: app mostrava "4G" em redes 5G NSA porque `derivarTecnologia` usava `serviceState.toString()` para detectar `nrState=CONNECTED`, o que falha em vários OEMs. A detecção agora usa `allCellInfo` como fonte secundária — se `CellInfoNr` registrado está presente e a tecnologia derivada era "4G" ou null, exibe "5G NSA" corretamente.

---

## [0.8.3] — 2026-05-19

### Tela de Sinal — Rede Móvel (redesign completo)
- **MobileSignalCard**: redesenhado com 4 seções — header (operadora + badge de tecnologia), gauge semicircular de RSRP colorido por nível, colunas de Força e Estabilidade, card de diagnóstico com causa e ação sempre visível
- **MobileSignalCard**: textos em linguagem humana (Excelente/Bom/Regular/Fraco + Estável/Moderada/Instável) em vez de valores técnicos brutos

### Tela de Sinal — Canal Wi-Fi
- **CanalTab**: canal atual promovido para destaque (`titleLarge`) com card próprio
- **CanalTab**: card "Você está no canal ideal" (verde) exibido quando não há canal melhor disponível
- **CanalTab**: textos de recomendação reescritos em linguagem natural — "Seu canal é o 36. Melhor mudar para o 40…"
- **CanalTab**: título do card de recomendação alterado para "Troque de canal"

### Tela Histórico — Monitoramento
- **UptimeGridChart**: grid Canvas de 336 blocos substituído por lista de eventos por dia — mostra uptime %, barra de progresso e resumo de períodos offline com horário

### Tela Home — Trilha de Rede
- **NetworkPath**: nó `wifiMesh` agora exibe ícone Hub com label "Mesh" (era "Wi-Fi", genérico demais)

### Correções (5) — entregues pelo Camilo anterior (0.8.2→0.8.3)
- **HomeScreen**: `QualidadeShortcutRow` ("Diagnóstico Inteligente") agora gateado por `FeatureFlags.DIAGNOSTICO_ITERATIVO` — não aparece mais em release
- **HomeScreen**: `internetLabel` em conexão móvel retorna "Internet" fixo — evitava operadora duplicada na trilha
- **HomeScreen**: nó móvel sem IP exibe tecnologia (ex: "5G") como sublabel direto, sem "—" prefixado
- **AjustesScreen**: `PerfilEditSheet` exibe nome da operadora em conexão móvel (era "Rede móvel" fixo)
- **AjustesScreen**: item duplicado "Dados usados pelo SignallQ" removido — informação já coberta por "Privacidade e dados"

---

## [0.8.2] — 2026-05-19

### Correções UX/Visual (3)
- **AppShell**: navbar agora fixa — removida animação de scroll-hide que deixava espaço vazio no rodapé
- **HomeScreen**: trilha de rede (NetworkPath) agora proporcional à tela — nós distribuídos com `weight(1f)` e `SpaceEvenly` em vez de 80dp fixo
- **HomeScreen**: "Modo Gamer" renomeado para "Jogar Online"

### Melhorias (2)
- **PerfilEditSheet**: avatar agora exibe dados de conexão (ISP/Operadora, IP Público, Tipo de conexão, Localização) em vez de nome do aparelho
- **UptimeGridChart**: gráfico de monitoramento reorientado para 7 linhas (dias) × 48 colunas scrolláveis (blocos de 30min), blocos quadrados 8dp — era 7 colunas × 48 linhas achatadas

---

## [0.8.1] — 2026-05-19

### Correções UX/Visual (10)
- **FibraScreen**: modem name agora é dinâmico (era hardcoded "Nokia" para todos os usuários)
- **ResultadoVelocidadeScreen**: título corrigido para "Resultado do teste" (era "Diagnóstico IA")
- **ResultadoVelocidadeScreen**: cores das métricas de latência e jitter agora dinâmicas por valor (semáforo)
- **ResultadoVelocidadeScreen**: botão "Testar upload novamente" agora usa cor accent (não warning)
- **SinalScreen**: título TopBar corrigido para `titleLarge` (era `bodyLarge`)
- **HistoricoScreen**: título TopBar corrigido para `titleLarge` (era `bodyLarge`)
- **HomeScreen**: botão "Central de testes" (era "Central de Medição")
- **HomeScreen**: conectores da topologia de rede com `contentDescription` para TalkBack
- **AjustesScreen**: orientação adicionada na seção "Minha Conexão"
- **NovidadesScreen**: estado de erro agora tem botão "Tentar novamente"

### Acessibilidade (1)
- **SpeedTestScreen**: seletor de modo com semântica TalkBack

### Diagnóstico de Rede (5)
- **SinalScreen**: classificação de sinal Wi-Fi agora distingue 2.4GHz de 5GHz (thresholds calibrados por banda)
- **DiagnosticoScreen**: tipo de conexão enviado para IA agora é real (era `wifi` hardcoded)
- **DiagnosticoScreen**: texto do step "Gerando diagnóstico com IA…" sem duplicação com título
- **FibraScreen**: hint e limiar de RX Power alinhados a ITU-T G.984 (−27 dBm mínimo)
- **DnsDiagnosticEngine**: novo diagnóstico DNS-03 para latência 51-150ms (status info)

---

## [0.8.0] — 2026-05-18

### Features Ativadas
- **FEATURE_FIBRA_SCREEN** promovida para `true` em release — FibraScreen agora visível em produção
- **FEATURE_DNS_SCREEN** promovida para `true` em release — DNS benchmark agora visível em produção

### Flags Novas
- **FEATURE_DIAGNOSTICO_CHAT** adicionada (debug: `true`, release: `false`) — chat com IA oculto em release, disponível em debug

### Correções
- **AppShell**: lambdas de `onConectarFibra()` e `onAbrirDnsBenchmark()` agora gateadas por feature flags para evitar side-effects quando desativadas
- **ResultadoVelocidadeScreen**: botão "Testar Novamente" duplicado no rodapé removido — componente `CtaVelocidadeResultado` agora renderiza uma única vez

### Melhorias Internas
- **ResultadoVelocidadeScreen**: botão de chat protegido por `FeatureFlags.DIAGNOSTICO_CHAT`
- **AppShell**: overlays de Chat, Fibra e DNS protegidos por feature flags correspondentes
- **FeatureFlags.kt**: mapeamento de `DIAGNOSTICO_CHAT` para `BuildConfig.FEATURE_DIAGNOSTICO_CHAT`

---

## Histórico de Versões

Versões anteriores a 0.8.0 não possuem registro detalhado neste arquivo.
