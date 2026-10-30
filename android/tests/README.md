# Plano de Testes — SignallQ Android

> **App:** SignallQ · **Versão:** 0.25.0 (versionCode 60) · **Package:** `io.signallq.app`
> **Última atualização do plano:** v3.0 (estado atual do código)

Este documento descreve a estratégia de testes do app Android SignallQ. Cobre os
testes automatizados existentes (unitários JVM), os testes instrumentados, o
roteiro de testes manuais / E2E e os gaps conhecidos.

---

## 1. Estratégia

A garantia de qualidade do SignallQ se apoia em três camadas:

1. **Testes unitários (JVM)** — lógica de domínio pura e ViewModels, rodando na
   JVM com JUnit4 + Robolectric + coroutines-test. É a camada mais densa e a
   primeira linha de defesa contra regressão de lógica (thresholds de
   diagnóstico, classificação de qualidade, histerese de monitoramento,
   persistência, parsing de modem, etc.).
2. **Testes instrumentados (androidTest)** — hoje restritos à camada de banco
   (Room / migrations / DAO). Não há cobertura instrumentada de UI Compose.
3. **Testes manuais / E2E** — roteiro estruturado em
   [`signallq_test_cases.yaml`](signallq_test_cases.yaml), cobrindo os fluxos
   de ponta a ponta de todas as telas. Serve de checklist de QA e de base para
   automação futura (ex.: Firebase App Testing).

### Frameworks e bibliotecas

| Camada | Ferramentas |
|--------|-------------|
| Unitário JVM | JUnit4, Robolectric, `kotlinx-coroutines-test`, `androidx.room:room-testing` |
| Instrumentado | JUnit4 + AndroidX Test + Room in-memory (androidTest) |
| Manual / E2E | Roteiro YAML (`signallq_test_cases.yaml`) |

---

## 2. Testes unitários (JVM) existentes

Total atual: **37 classes de teste** em `*/src/test/`, distribuídas por módulo.

### Módulo `app`
| Teste | O que cobre |
|-------|-------------|
| `MainViewModelHistoricoTest` | Estado/lógica de histórico no ViewModel principal |
| `DnsScreenTest` | Estados e comportamento da tela de DNS |
| `FibraModemUiStateTest` | UiState da análise de modem/ONT (Fibra) |
| `monitoramento/DeteccaoDispositivoNovoTest` | Detecção de dispositivo novo na rede local |
| `monitoramento/MonitoramentoWorkerHistereseTest` | Histerese do worker de monitoramento (evita alertas por oscilação) |
| `monitoramento/MonitoramentoWorkerMedicaoTest` | Lógica de medição do worker de monitoramento passivo |
| `speedtest/SpeedtestPersistenceCoordinatorTest` | Coordenação de persistência dos resultados de speedtest |

### Módulo `coreDatabase`
| Teste | O que cobre |
|-------|-------------|
| `MedicaoEntityTest` | Mapeamento/integridade da entidade de medição |

### Módulo `coreNetwork`
| Teste | O que cobre |
|-------|-------------|
| `GatewayLatencyMeasurerTest` | Medição de latência até o gateway |
| `SnapshotRedeTest` | Captura de snapshot do estado da rede |
| `WifiLinkSnapshotTest` | Snapshot do link Wi-Fi (sinal, banda, etc.) |

### Módulo `coreTelephony`
| Teste | O que cobre |
|-------|-------------|
| `MonitorTelephonyTest` | Monitor de telefonia (sinal celular / operadora) |

### Módulo `featureDiagnostico`
| Teste | O que cobre |
|-------|-------------|
| `ai/DiagnosisAiContextFactoryTest` | Montagem do contexto enviado à IA de diagnóstico |
| `ai/AiFallbackFactoryTest` | Fallback de diagnóstico **local** quando a IA na nuvem não está disponível (rodapé "Diagnóstico local do SignallQ") |
| `ai/AiDiagnosisRepositoryTest` | Repositório de diagnóstico por IA (chamada ao Worker / tratamento de resposta) |
| `CanalTextGeneratorTest` | Geração de texto da análise de canais Wi-Fi |
| `DiagnosticDecisionEngineGatewayTest` | Motor de decisão de diagnóstico (regras de gateway) |
| `DiagnosticRunnerIntegrationTest` | Integração do runner que orquestra as etapas de diagnóstico |
| `DnsDiagnosticEngineTest` | Motor de diagnóstico de DNS |
| `HistoricalDegradationEngineTest` | Detecção de degradação a partir do histórico |
| `InternetDiagnosticEngineTest` | Motor de diagnóstico de internet/conectividade |
| `pulse/DynamicQuestionEngineTest` | Motor de perguntas dinâmicas do fluxo guiado |
| `WifiChannelDiagnosticEngineTest` | Motor de diagnóstico de canais Wi-Fi |

### Módulo `featureHistory`
| Teste | O que cobre |
|-------|-------------|
| `ExportadorHistoricoCSVTest` | Exportação do histórico em CSV |
| `ExportadorHistoricoPDFTest` | Exportação do histórico em PDF |
| `ExportHistoricoFlowTest` | Fluxo completo de exportação do histórico |
| `TendenciaCalculadorTest` | Cálculo de tendência das medições |
| `UptimeChartUseCaseTest` | Use case do gráfico de uptime |
| `UptimeGridChartLogicTest` | Lógica do grid chart de uptime |
| `UptimeNarrativaEngineTest` | Geração da narrativa humana de uptime |

### Módulo `featureSpeedtest`
| Teste | O que cobre |
|-------|-------------|
| `SpeedtestQualityClassifierTest` | Classificação de qualidade do speedtest (veredito humano) |

---

## 3. Testes instrumentados (androidTest)

Existem **2 classes** instrumentadas, ambas focadas na camada de banco
(Room / migrations / DAO) — **não há testes instrumentados de UI Compose**:

| Teste | Módulo | O que cobre |
|-------|--------|-------------|
| `chat/ChatSessionDaoTest` | `coreDatabase` | DAO de sessões de chat (tabela mantida — usada pelo `AdminSyncWorker` para sync de uso de IA legado) |
| `chat/Migration9Para10Test` | `coreDatabase` | Migração de schema Room v9 → v10 |

---

## 4. Testes manuais / E2E

O caderno mais completo e atual é
[`caderno-completo-testes-android.xlsx`](caderno-completo-testes-android.xlsx)
(178 casos, adicionado em 2026-07-17) — cobre tela por tela (Início, Velocidade,
Sinal, Histórico, Perfil/Ferramentas, overlays/detalhes), fluxos E2E, checklist
de tokens de design, template de bug para o GitHub e comandos ADB de apoio.
Reflete o estado atual do app (Ferramentas como quarta raiz, Ajustes/Perfil pelo avatar,
package `io.signallq.app`) e traz colunas de status (Não iniciado / Em execução
/ Passou / Falhou / Bloqueado) com resumo agregado por grupo.

> **`signallq_test_cases.yaml`** referencia `io.signallq.app` e cobre a jornada única com
> quatro raízes: Início, Velocidade, Histórico e Ferramentas.

Cobre, em PT-BR, os fluxos das 4 raízes (Início, Velocidade, Histórico e Ferramentas)
e as ferramentas associadas:

- **Onboarding** (permissões — aceitar e recusar)
- **Navegação / AppShell** (4 raízes, barra inferior, back em overlay/sheet)
- **Home** (dashboard, métricas, atalhos, estado offline)
- **Speedtest** (modos, execução, dados móveis, cancelamento, offline)
- **Resultado / Compartilhamento**
- **Wi-Fi / Sinal** (lista de redes, filtros de banda, BSSID mascarado, canais)
- **Diagnóstico** (local + IA + chat com o assistente **SignallQ** + **fallback local** quando a IA está fora)
- **Dispositivos** (scan da rede local, MAC mascarado, sem Wi-Fi)
- **DNS** (benchmark + erro humanizado)
- **Fibra / Modem** (análise em Wi-Fi e estado sem Wi-Fi)
- **Histórico** (estado vazio, uptime, filtros, detalhe, exportação)
- **Ajustes** (layout, perfil, tema, notificações/monitoramento, conexão, redefinir)
- **Monitoramento passivo** (notificação de degradação com histerese)
- **Privacidade / Linguagem** (IP mascarado, PT-BR em todas as telas)

Cada caso traz `goal`, `hint` e `finalScreenAssertion`. As pré-condições usam
`prerequisiteTestCaseId` para encadear cenários (ex.: histórico depende de um
speedtest concluído).

---

## 5. Gaps conhecidos / backlog

- **Sem testes instrumentados de UI Compose.** A cobertura androidTest se
  limita a Room/DAO/migrations. Telas, navegação e interações não têm testes
  de UI automatizados — toda a verificação de tela é manual hoje.
- **E2E ainda não automatizado.** O roteiro `signallq_test_cases.yaml` é
  executado manualmente; ainda não há harness automatizado (ex.: Firebase App
  Testing / Espresso / Compose UI test) rodando em CI.
- **Caminho da IA não exercitado fim a fim em CI.** A integração real com o
  Worker Cloudflare (Gemini 2.0 Flash primário, Qwen3 30B fallback cloud) e o fallback local são cobertos
  por testes unitários de repositório/factory, mas não por um teste de rede
  real automatizado. O fallback local deve ser validado manualmente
  (caso `diagnostico-ia-fallback`).
- **Cobertura de telefonia / OEM quirks** depende de hardware real e não é
  reproduzível em JVM/CI.

---

## 6. Como rodar

### Testes unitários (JVM) — todos os módulos
```bash
./gradlew test
```

### Testes unitários de um módulo específico
```bash
./gradlew :featureDiagnostico:testDebugUnitTest
./gradlew :app:testDebugUnitTest
```

### Testes instrumentados (exigem device/emulador conectado)
```bash
./gradlew connectedAndroidTest
```

### Testes manuais / E2E
Seguir o roteiro de [`signallq_test_cases.yaml`](signallq_test_cases.yaml)
em um device/emulador, validando cada `finalScreenAssertion`.
