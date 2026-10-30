---
title: "Feature — Velocidade e resultado"
description: "Teste de velocidade (modo automático por rede, execução em tela cheia) e tela de resultado com veredito do motor: regras, estados, mapa de código, eventos, flags e testes."
type: "feature"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.0.0"
feature: "velocidade-resultado"
tipo: "jornada"
modulos:
  - "android/feature/speedtest"
  - "android/core/diagnostico"
  - "android/core/network"
  - "android/core/featureflags"
  - "android/app"
arquivos:
  - "android/feature/speedtest/src/main/kotlin/io/signallq/app/feature/speedtest/"
  - "android/app/src/main/kotlin/io/signallq/app/speedtest/SpeedtestPersistenceCoordinator.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SpeedTestScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/VelocidadeScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/ResultadoVelocidadeScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/ResultadoIndisponivelScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShellResultadoVelocidadeOverlay.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/ContinuidadeMedicao.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/component/ClassificacaoMetricaLocal.kt"
contratos: []
eventos:
  - "feature_used (feature_id=speedtest | speedtest_iniciado | speedtest_completou | speedtest_compartilhou)"
  - "speedtest_iniciado (Firebase: modo, tipo_conexao)"
  - "speedtest_concluido (Firebase: modo, tipo_conexao_inicio, tipo_conexao_fim, contaminado, duracao)"
  - "screen_view (screen_name=speedtest)"
  - "feature_blocked_remote (feature_id=speedtest)"
flags:
  - "consumer_speedtest_enabled"
  - "consumer_speedtest_cloudflare_engine_enabled"
  - "speedtest_enabled"
testes:
  - "android/feature/speedtest/src/test/kotlin/io/signallq/app/feature/speedtest/"
  - "android/app/src/test/kotlin/io/signallq/app/speedtest/SpeedtestPersistenceCoordinatorTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SpeedTestScreenTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/VelocidadeScreenTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/ResultadoVelocidadeScreenTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/ResultadoIndisponivelTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/ContinuidadeMedicaoTest.kt"
adrs: []
thresholds_em: "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/MetricClassifier.kt"
---

# Feature — Velocidade e resultado

## Negócio

### 1. Problema e promessa

O usuário quer saber "como está minha internet agora". O teste mede download, upload, tempo de resposta, variação, falhas estimadas e lentidão sob carga, e traduz em veredito do motor. Não promete causa raiz: o speedtest é **uma das fontes de evidência** do diagnóstico, não o fim da jornada (`POSICIONAMENTO_PRODUTO.md`). "Falhas estimadas na conexão" é rótulo deliberadamente honesto: a medição é taxa de timeout de probes HTTP, não perda de pacotes IP.

### 2. Quando aparece e para quem

Aba **Velocidade** (`SpeedTestScreen`, raiz 1 da barra inferior) ou card "Medições" da Início; sem escolher modo. Para qualquer usuário; só precisa de `INTERNET` (sem permissão de runtime). O resultado (`ResultadoVelocidade`) abre sozinho ao concluir e também por "Ver resultado". A barra inferior fica oculta durante a execução.

### 3. Regras de decisão

- **Modo automático por rede** (GH#1737, `modoAutomaticoPara` em `ModoSpeedtest.kt`): rede móvel usa **Rápido** (só download); Wi-Fi e demais usam **Completo** (download e upload). Seletor manual, `MedicaoTipoSheet` e o modo Triplo foram removidos.
- **Integridade da execução**: `MeasurementStatus` (`COMPLETE`/`PARTIAL`/`INCONCLUSIVE`/`CONTAMINATED`/`CANCELLED`) é computado **uma única vez** em `ExecutorSpeedtestCloudflare.construirResultado` e nunca recalculado. Só `COMPLETE` libera diagnóstico conclusivo, IA, recomendação, contato com operadora, histórico e PDF completo.
- **Veredito**: título e mensagem vêm da decisão do motor de diagnóstico, não de texto fixo. Thresholds de classificação: ver `thresholds_em` (não copiados aqui).
- **Confiança amostral**: perda com amostra insuficiente não empurra sozinha o veredito para "ruim" (`SpeedtestQualityClassifier`).
- Resultado: dois cards (download, upload); toggle "Ver detalhes da conexão" revela tempo de resposta, variação, falhas estimadas e lentidão com a rede ocupada (bufferbloat). A seção "Como sua internet deve funcionar" traduz em vídeo em alta qualidade, jogos online e chamadas de vídeo.
- Fase de execução (`VelocidadeScreen`, tela cheia): gauge, pills LATÊNCIA → DOWNLOAD → UPLOAD → CONCLUÍDO, frase por fase, haptics; durante o upload o download concluído continua visível.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Conclusão não-`COMPLETE` (parcial, contaminada, inconclusiva; GH#1738) | `VelocidadeScreen` mostra título, ícone e explicação próprios do status antes do resultado, via `continuidadeDaMedicao` |
| Upload não detectado | Callout no resultado; mostra só as métricas válidas |
| Contaminado por mudança de rede | "O teste foi interrompido porque a conexão caiu ou mudou durante a medição." (distinto de interferência genérica de outros apps) |
| Cancelar durante execução | Confirmação (a mesma do `BackHandler`); encerra sem produzir resultado |
| Iniciar em rede móvel | `ForaDoWifiDialog` (aviso de consumo); confirmar pula o segundo gate de rede medida |
| Wi-Fi conectado sem internet | Speedtest é interrompido; mostra a conclusão do diagnóstico local (`DiagnosticoConectividadeDialog`) em vez de travar em "executando" |
| `ResultadoVelocidade` sem resultado em memória | `ResultadoIndisponivelScreen` (GH#1714), nunca tela em branco |
| Feature bloqueada por flag remota | Aba não clicável; snackbar neutro "Recurso temporariamente indisponível."; evento `feature_blocked_remote` |

Ausência de dado não vira zero; timeout/falha de fase não é tratado como sucesso (`MeasurementStatus`).

### 5. Próximo passo e confirmação

Do resultado: "Descobrir o que está acontecendo" (Assist, `assist-diagnostico`), "Ver detalhes da conexão" (`DetalhesTecnicos`), Modo gamer, DNS e Ping. **Compartilhar** gera PDF. O usuário confirma melhora repetindo o teste; o reteste vinculado ao Assist tem regra própria (ver `assist-diagnostico`). "Recomeçar do zero" é um CTA do `ResultadoVelocidadeScreen`.

### 6. Fora de escopo, status e flag

Entregue. Não gera laudo (ver `historico-laudo`), não persiste medição `CONTAMINATED` como válida, não escolhe servidor manualmente. Flags: `consumer_speedtest_enabled` gateia aba e overlay (fail-open, default `true`, criticidade HIGH); `consumer_speedtest_cloudflare_engine_enabled` está no catálogo com `androidImplemented: false` (não verificado se algum caminho do app a lê).

## Técnico

### 7. Mapa de código

| Arquivo | Responsabilidade |
|---|---|
| `feature/speedtest/.../ExecutorSpeedtestCloudflare.kt` | Motor real: pool HTTP adaptativo (móvel × Wi-Fi), fases ping/download/upload/latência sob carga, bufferbloat/estabilidade/picos, `construirResultado` |
| `feature/speedtest/.../SpeedtestViewModel.kt` | `@HiltViewModel`: execução, guarda de rede medida, acúmulo mensal de MB, analytics, interrupção por Wi-Fi sem internet, callback `onSpeedtestConcluido` |
| `feature/speedtest/.../MeasurementStatus.kt` | Fonte única de integridade da execução |
| `feature/speedtest/.../ModoSpeedtest.kt` | `modoAutomaticoPara(EstadoConexao)` |
| `feature/speedtest/.../AnalisadorAmostragemPing.kt`, `ValidadorBaselineLatencia.kt` | Estatística pura (mediana, jitter, perda, outlier, p95/max/picos, confiança amostral) e guardas de baseline |
| `feature/speedtest/.../SpeedtestQualityClassifier.kt`, `ClassificacaoMetricaLocal.kt` | Tradução de `MetricStatus` de `:core:diagnostico` para `SeveridadeBufferbloat`; seam NDS-02k |
| `feature/speedtest/.../ResultadoSpeedtest.kt` (+ modelos `Snapshot*`, `Fase*`, `Estado*`, `PontoAoVivo`) | Contrato de saída (campos aditivos/nullable) e estado de execução |
| `feature/speedtest/.../connectivity/` | `ConnectivityDiagnosisRepository/Presenter`, `ConnectivityBlockingPolicy` (interrupção por Wi-Fi sem internet) |
| `app/.../speedtest/SpeedtestPersistenceCoordinator.kt` | Persistência do resultado no histórico |
| `app/.../ui/screen/SpeedTestScreen.kt`, `VelocidadeScreen.kt`, `ResultadoVelocidadeScreen.kt`, `ResultadoIndisponivelScreen.kt`, `ContinuidadeMedicao.kt` | Telas (aba, execução, resultado, indisponível) e ponte de continuidade por status |
| `app/.../ui/screen/AppShellResultadoVelocidadeOverlay.kt`, `AppShellFeatureGating.kt` | Overlay e gating (`ConsumerFeatureModuleIds.SPEEDTEST = "speedtest"`) |
| `app/.../ui/component/ClassificacaoMetricaLocal.kt` | Espelho no `:app` da classificação local (inclui `classificarDownloadLocal`) |

`PingExecutor.kt` mora no mesmo módulo, mas pertence a `dns-ping`. Detalhe de módulo: [`feature-speedtest.md`](../modulos/feature-speedtest.md).

### 8. Dados e contratos

Sem contrato OpenAPI próprio. Saída: `ResultadoSpeedtest` (28+ campos, incluindo `MeasurementStatus`, `DiagnosticoQualidadeSpeedtest`, `DiagnosticoFasesSpeedtest`). Todo resultado é persistido no histórico via `:core:database` (exceto `CONTAMINATED`). Dependências do módulo: `:coreNetwork`, `:coreDatabase`, `:coreDatastore`, `:coreTelephony`, `:core:diagnostico`. MB acumulados em rede móvel vêm de `PreferenciasAppRepository` (`:core:datastore`). A latência-base usa o worker dedicado `GAME_LATENCY_PROBE_URL` (`FeatureSpeedtestModulo.kt`).

### 9. Eventos e flags

- `feature_used`: `speedtest` (`MainActivity.kt`), `speedtest_iniciado` e `speedtest_completou` (`SpeedtestViewModel.kt`, enviados ao admin-worker sem endpoint novo), `speedtest_compartilhou` (`MainActivity.kt`).
- Firebase (`FirebaseAnalyticsHelper.kt`): `speedtest_iniciado` (`modo`, `tipo_conexao`) e `speedtest_concluido` (`modo`, `tipo_conexao_inicio`, `tipo_conexao_fim`, `contaminado`, `duracao`).
- `screen_view` com `screen_name=speedtest` (`AppShellRoot.screenName()`). Nome do `screen_view` do overlay de resultado: não verificado.
- `feature_blocked_remote` (`ConsumerFeatureGateCoordinator.kt`).
- Flags: ver frontmatter; `speedtest_enabled` é o valor padrão local em `FeatureFlagRepository.kt` (legado). Detalhe: `technical/feature-flags-remote-config.md`, `technical/analytics-events-schema.md`.

### 10. Falhas e fallback

- Fase relevante falha → `PARTIAL`; amostras abaixo do mínimo estatístico → `INCONCLUSIVE`; rede muda → `CONTAMINATED`; cancelado → sem resultado.
- Baseline de latência fisicamente implausível ou probe indisponível → guardas de `ValidadorBaselineLatencia`.
- Teto de duração `latenciaOrcamentoMs` na fase de latência (só modo Rápido) e janela de confirmação de amostragem (`ExecutorSpeedtestCloudflare`).
- Wi-Fi sem internet → interrupção e diagnóstico local (`ConnectivityBlockingPolicy`); a decisão de bloquear mora em `MainViewModel`/`SpeedtestViewModel`.

### 11. Testes

Lista em `testes:` no frontmatter; o diretório de testes do módulo cobre `AnalisadorAmostragemPing`, `ClassificacaoMetricaLocal`, `ModoSpeedtest`, `SpeedtestQualityClassifier`, `ValidadorBaselineLatencia`, `SpeedtestMbEstimativa`, `ExecutorSpeedtestCloudflareConfirmacao`/`Falha` e o pacote `connectivity`. Lacuna: `ExecutorSpeedtestCloudflare.kt` não tem teste direto da orquestração completa (os testes cobrem peças puras e dois cenários do executor); não há `src/androidTest`.

### 12. Riscos

- `ExecutorSpeedtestCloudflare.kt` muito acima de 1200 linhas (dívida crítica, `higiene` §7): rede, concorrência, estatística e construção do resultado juntos.
- Telas ficam em `:app`, o motor em `:feature:speedtest`: separação UI/motor real, porém assimétrica.
- `MainViewModel.kt` também consome `ConnectivityDiagnosisRepository` (acoplamento do app ao pacote `connectivity`; candidato a `:coreNetwork`).
- `ClassificacaoMetricaLocal` existe duas vezes (módulo e `:app`) por causa da direção `:feature* -> :core*`; risco de divergência de limiar.
- `consumer_speedtest_cloudflare_engine_enabled` com `androidImplemented: false`: flag possivelmente sem efeito (não verificado).
