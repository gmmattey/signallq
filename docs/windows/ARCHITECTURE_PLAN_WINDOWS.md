# Architecture Plan — SignallQ Windows Desktop

## Problema e comportamento esperado

Entregar um aplicativo Windows instalável focado em coletar, apresentar e guardar dados da conexão,
com interface local fiel ao protótipo aprovado, medições reais e limites explícitos para capacidades
indisponíveis. Interpretação guiada, Assist e consulta com IA ficam principalmente no app mobile.

## Decisão

Criar `windows/` como aplicação isolada **Tauri 2 + React/Vite + Rust**. O host Rust é dono de
coleta Windows, medições, processos controlados e persistência SQLite; a UI só consome comandos e
eventos DTO versionados. `android/` e `web/` não são acoplados nem movidos.

Electron foi rejeitado pelo runtime maior e uma superfície de execução nativa mais ampla. WinUI 3
foi rejeitado porque exigiria reescrever o protótipo HTML e reduziria a fidelidade visual.

## Fronteiras e contratos

- `windows/src/`: apresentação, navegação e estados; não calcula diagnóstico e não invoca shell.
- `windows/src-tauri/`: adaptadores Windows, medição, histórico local e comandos fechados.
- `measurement/v1`: `executionId`, status, proveniência, integridade da amostra, capacidades e `null` para
  indisponibilidade. Ausência não é zero.
- Diagnóstico/Assist remoto não integra o escopo Desktop atual. Uma iniciativa futura exigirá
  contrato de autenticação próprio; a UI atual não deve prometer laudo ou resposta de IA.

## Persistência e falhas

SQLite local versionado, hoje em `%APPDATA%\com.buildea.signallq`, separado de Room. O alvo arquitetural é
cancelamento cooperativo sem gravar resultado completo; na fundação atual ele é pendência.
Timeout, troca de rede e resultado parcial nunca viram sucesso. Bandeja e monitoramento persistente
também são pendências; sair encerra a sessão atual.

## Segurança e privacidade

Sem Node integration, segredo no frontend ou executável arbitrário. Ferramentas aceitam somente
destinos validados, binários/argumentos fixos, timeout e encerramento explícito. Inventário local
não sai do dispositivo por padrão.

## Testes e riscos

Cobrir unidades Rust/TypeScript, migrations SQLite, vetores de conformidade com os contratos
Android, E2E no runner Windows e comparação de screenshots a 100%, 125% e 150%. Windows 10/11,
WebView2, tray, DPI e instalador só serão considerados validados com evidência em Windows.

Riscos abertos: disponibilidade desigual de APIs Wi-Fi, dependências externas de sonda e a
necessidade de não duplicar regras de classificação do Android no Desktop.

## Não-objetivos desta fundação

Não importar banco Room, não alegar ping de servidor oficial de jogo, não inventar cobertura
Wi-Fi, não integrar NDS/Assist/IA nesta entrega e não publicar/deployar nenhum serviço remoto.

## Revisão para correção E2E — 2026-10-15

Após a auditoria em `docs/windows/E2E_AUDIT_2026-10-15.md`, o plano de entrega é
`docs/windows/CORRECTION_PLAN_2026-10-15.md`. Camillo revisou a separação arquitetural:

- O conserto da medição HTTP ocorre no núcleo Rust, sem depender do NDS. O fallback 403/429 para
  payload menor já existe no Android e serve como referência de comportamento, junto com os vetores
  de integridade. Uma resposta HTTP 200 isolada não autoriza declarar medição completa.
- Medição, erros por fase, status, proveniência e cancelamento exigem contrato versionado e migração
  aditiva do SQLite. O banco **atual** observado fica em
  `%APPDATA%\com.buildea.signallq\signallq.sqlite3`; uma eventual mudança de diretório deve migrar
  os dados existentes, não criar histórico vazio silenciosamente.
- Coleta Windows escolhe uma interface ativa por APIs tipadas; VPN e múltiplos adaptadores não podem
  produzir snapshot misturado. O host Rust mantém ferramentas com prazo total e encerramento real.
- Conforme a direção de produto atualizada pelo Luiz, o Desktop coleta, apresenta e guarda dados;
  interpretação guiada, Assist e consulta com IA ficam principalmente no app mobile. A UI Desktop
  deve mostrar método, origem e limite de cada medição, sem produzir score, causa raiz ou confiança
  por thresholds próprios.
- NDS, autenticação Desktop e Assist saem do caminho crítico desta correção. Uma integração futura
  exigirá novo escopo e os gates das ADR-017/019: NDS como fonte única de scoring/explicação, sem
  motor local paralelo, Worker shadow como substituto ou token NDS embutido no Windows.
- O contrato local de evidência continua versionado para eventual uso futuro; não transmite
  inventário, histórico, SSID ou IP para serviços remotos nesta entrega. Publicação de Worker,
  alteração de credencial, novo custo e distribuição pública continuam sujeitos à decisão do Luiz.
- Por ressalva explícita do Luiz ao aceitar o plano, cada responsabilidade Desktop parte do
  inventário Android em `docs/windows/REUSE_AUDIT.md`: reuso direto de contratos/assets/vetores,
  adaptação em Rust de algoritmos e protocolos, e adaptadores próprios para APIs Windows. A
  implementação registra divergências justificadas e passa por testes de paridade semântica.

## Gate de implementação — contrato Desktop e responsabilidades (2026-10-15)

Este desenho concretiza a correção em `docs/windows/CORRECTION_PLAN_2026-10-15.md`. O escopo é
evidência factual local; não há classificador, NDS, Assist, IA ou novo serviço. O código Android é
referência de semântica e vetores (`docs/windows/REUSE_AUDIT.md`), não dependência executável.

### DTO `measurement/v2` e ciclo da medição

- O Rust é a fonte única do status. `executionId` (UUID gerado **antes** da primeira sonda),
  `schemaVersion: 2`, `startedAtMs`, `capturedAtMs` (fim), `status`, métricas opcionais e `phases[]`
  compõem o DTO serializado em camelCase para Tauri/TypeScript. Métricas mantêm `Mbps`, `ms` e `%`;
  duração e horário são `ms`, volume é `bytes`. `null` significa não medido, nunca zero implícito.
- Cada fase (`latency`, `download`, `upload`; futuramente `loadedLatency`) inclui `method`,
  `endpoint` sem parâmetros sensíveis, `startedAtMs`, `durationMs`, `attempts`, `validSamples`,
  `bytesTransferred` quando aplicável, `status` e `error` opcional. `error` usa código estável
  (`HTTP_BLOCKED`, `HTTP_ERROR`, `TRANSPORT`, `TIMEOUT`, `NETWORK_CHANGED`, `INSUFFICIENT_SAMPLE`,
  `CANCELLED`) mais `httpStatus` opcional e mensagem segura; detalhe de baixo nível fica só no log
  local sem SSID/IP/payload. Registrar se o fallback 403/429 para 10 MB foi usado.
- Estado único: `COMPLETE`, `PARTIAL`, `INCONCLUSIVE`, `CONTAMINATED`, `CANCELLED`, seguindo
  `android/.../MeasurementStatus.kt`. `COMPLETE` exige todas as fases obrigatórias válidas e
  amostra mínima conforme vetores Android; `PARTIAL` preserva as métricas de fases válidas quando
  outra falha; `INCONCLUSIVE` indica amostra insuficiente; `CONTAMINATED` indica mudança de rede;
  `CANCELLED` indica interrupção solicitada. Prioridade: cancelamento ou contaminação encerram a
  execução; depois amostra insuficiente, falha de fase, completo. Nenhum deles é nota de qualidade
  da conexão. A UI não recalcula status, perda, jitter, score ou thresholds.
- `packetLossSource` e a descrição do método são obrigatórios quando houver perda estimada;
  timeout de sondagem HTTP não é rotulado como perda ICMP. Atraso sob carga permanece `null` sem
  sondas concorrentes válidas. Snapshot de interface de início/fim serve para detectar contaminação,
  mas identificadores sensíveis não entram em logs nem exportação por padrão.
- `start_speedtest` devolve imediatamente `executionId` e executa a medição no host;
  progresso por evento Tauri carrega `executionId`, fase e percentual limitado de 0 a 100. O
  comando de cancelamento usa esse ID; eventos de execução antiga são ignorados pela UI. Só uma
  medição ativa por vez; novo clique não inicia transferências concorrentes. Resultado final é
  emitido uma vez e persistido conforme a política abaixo.

### SQLite e compatibilidade

- Manter o arquivo em `%APPDATA%\com.buildea.signallq\signallq.sqlite3`. Não mover o banco nesta
  correção. Migração v2 transacional, idempotente e verificada por `schema_migrations`: acrescentar
  `schema_version`, `started_at_ms` e `evidence_json` (DTO de fases/proveniência) à tabela existente;
  preservar colunas e linhas v1. Índice temporal só se a consulta o justificar. Toda escrita v2
  inclui as colunas legadas de métricas; para `COMPLETE`, a coluna legada `status` guarda
  `COMPLETED`, enquanto o estado canônico v2 fica em `evidence_json`. Os demais estados v2
  ficam na coluna legada como `PARTIAL`, para o binário antigo não mostrá-los como completos;
  o estado canônico sempre vem do JSON na leitura v2. `evidence_json` é validado ao gravar/ler e
  não contém segredo ou conteúdo de ferramentas.
- Leitura de v1 cria DTO em memória sem fabricar fases, bytes ou motivo HTTP. `PARTIAL` legado
  continua parcial. `COMPLETED` legado vira `INCONCLUSIVE` na API v2, pois as transferências únicas
  antigas não comprovam integridade amostral; manter `legacyStatus: "COMPLETED"` na proveniência
  apresentada pelo histórico. Nunca converter `NULL` em zero. Leitura de v2 usa o estado persistido,
  sem recalculá-lo na UI. Migração deve ser testada com cópia/fixture do schema v1 e garantir que
  as seis linhas parciais observadas continuam acessíveis.
- Persistir `COMPLETE`, `PARTIAL` e `INCONCLUSIVE` para explicar o histórico; `CONTAMINATED` e
  `CANCELLED` não entram na série de medições comparáveis, mas podem registrar um evento local
  de execução separado, sem Mbps apresentado como válido. Falha do SQLite é erro explícito ao
  usuário, mesmo que a coleta tenha terminado: não alegar que o histórico foi salvo.

### Fronteiras dos módulos

- **Rust/host:** executor HTTP, adaptador de interface Windows por API tipada, ferramentas de
  processo com deadline/kill, cancelamento, eventos, validação de status, SQLite e seleção de
  destino permitido. O motor de I/O aplica teto de tempo e dados e adapta o comportamento
  caracterizado por `ExecutorSpeedtestCloudflare.kt` e `AnalisadorAmostragemPing.kt`; não copia
  OkHttp/Kotlin nem thresholds de diagnóstico. `network_snapshot` devolve campos de uma interface
  coerente com `capabilityNote` por campo ausente; rede inicial/final é capturada dentro do host.
- **Tauri:** comandos fechados `start/cancel_speedtest`, `list_history`, `network_snapshot` e
  `run/cancel_tool`; DTOs e eventos versionados. Nenhuma chamada de shell arbitrária, URL fornecida
  pela UI para speedtest, token ou acesso direto ao SQLite pelo WebView.
- **React:** estado visual, navegação, indicação de progresso, renderização de valores/limites e
  filtragem de histórico. `native.ts` tipa/valida a fronteira; `App.tsx` não agenda transferências
  concorrentes nem interpreta códigos como causa raiz. `qualitySummary` com thresholds locais sai
  da experiência factual. A tela atual “Diagnóstico” vira “Dados da conexão”.
- **Ferramentas:** resultado separado de `Measurement` com alvo, método, horário, duração, status
  (`COMPLETED`, `FAILED`, `TIMEOUT`, `CANCELLED`) e saída truncada. Ping/rota encerram o filho no
  prazo; DNS diferencia resposta vazia/NXDOMAIN, erro e timeout. O fluxo gateway → DNS → externo
  apresenta cada observação, sem inferir causa. Histórico de ferramentas não é criado por
  acidente ao ampliar o histórico de velocidade.
- **Monitor e bandeja:** ao minimizar, a janela some para a bandeja e a medição leve continua no
  host Rust, sem depender de `setInterval` ou da WebView. Abrir pelo ícone restaura a janela; o
  comando **Sair** da bandeja e fechar pelo X encerram o aplicativo e cancelam a sonda ativa de
  forma limitada no tempo. Desligar o monitor interrompe imediatamente o agendador, inclusive na
  bandeja. Não iniciar com o Windows nem manter serviço após encerrar. Preferência habilitada e
  intervalo ficam em armazenamento local; ao reabrir o aplicativo, retomar o monitor se a
  preferência persistida estiver habilitada, com estado visível ao usuário. Uma única instância
  do agendador e uma sonda por vez; atrasos não geram fila nem execução duplicada. Etapa 3
  substitui transferências periódicas por sondas leves com DTO/histórico próprios, prazo e limite
  de dados. O monitor não chama `run_speedtest` em intervalo. Estado ativo/pausado, último horário,
  falha e consumo estimado são expostos por comando/evento Tauri.
- **Histórico e exportação:** manter medições e amostras de monitor em tabelas ou tipos distintos,
  vinculados por horário e `executionId` quando aplicável; comparação usa somente dados de rede
  cuja identidade foi coletada com confiança. Não estabelecer prazo de retenção presumido:
  registros permanecem locais até remoção explícita pelo usuário ou desinstalação conforme o
  comportamento verificado do instalador. Oferecer exclusão local de registro e de todo o
  histórico com confirmação, sem alterar o banco Android. A exportação só ocorre após ação
  explícita e escolha do arquivo; o usuário pode incluir SSID, IP e identificador da rede em
  arquivo **local**, com prévia clara dos campos incluídos. Sem upload, sincronização ou envio
  automático. Exportar `null` como célula vazia e preservar unidades, horário, origem, estado e
  erro por fase; o CSV usa cabeçalho estável/versionado, UTF-8, quoting RFC 4180 e neutralização
  de fórmula em qualquer campo controlado pela rede/usuário (`=`, `+`, `-`, `@` e controles no
  início), inclusive SSID, destino e mensagem de erro. Escrever em arquivo temporário local e
  concluir por substituição atômica quando possível; falha/cancelamento não anuncia exportação
  concluída. Caminho de saída nunca é derivado de SSID/IP.

### Falhas, validação e riscos

Repetir 403/429 uma única vez com configuração reduzida, registrar ambos os resultados e parar
se o fallback falhar; nenhuma resposta 200 isolada torna Mbps válido. Timeout total, cancelamento,
troca de interface, 401/403 de ferramenta, falha SQLite e ausência de rede produzem status/erro
distintos. A UI mantém retentativa manual e nunca exibe último valor como se fosse da execução
atual. Nenhuma sonda bloqueia a thread da WebView.

Gate de paridade antes de fechar cada etapa: matriz campo/estado Android→Rust, fixtures sem rede
pública para sucesso, 403/429, parcial, amostra insuficiente, offline, timeout, cancelamento e
troca de rede; teste de migração v1→v2 e serialização Rust↔TypeScript; E2E no binário empacotado
Windows 10/11, duas redes reais e validação adversarial de Tiago. Marcelo revisa método, unidades
e proveniência. Camillo revisa aderência do contrato após a implementação sistêmica.

Riscos: comportamento variável do endpoint Cloudflare, custo de dados em throughput, divergência
estatística entre OkHttp e reqwest, ambiguidade de VPN/interface ativa, APIs Wi-Fi limitadas por
driver, migração de registros antigos, eventos tardios após cancelamento, sondas repetidas na
bandeja e exposição de dados da rede por CSV. Teto de tempo/volume, DTO tipado, estado por
execução, agendador único, exportação explícita e testes em Windows mitigam esses riscos.
Rollback local pode voltar ao build anterior porque colunas novas são aditivas; o build
anterior não interpreta o JSON novo, mas continua lendo as colunas legadas. Backup do banco antes
do primeiro upgrade de teste e recuperação explícita de falha de migração.

**Gate arquitetural fechado para iniciar a implementação das etapas 0–3 e dos aspectos locais da
etapa 4.** Luiz decidiu monitor ativo na bandeja após minimizar, fechamento como saída, e
exportação local explícita que pode conter SSID/IP/identificador da rede. O prazo de retenção não
foi definido: não criar expiração automática. Assinatura, canal e publicação continuam sujeitos a
decisão separada do Luiz.
