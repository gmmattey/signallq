---
title: "Início — status da conexão ao vivo"
description: "Tela Início: veredito humano, trilha de nós da rede com status ao vivo em Wi-Fi e regra de quando o nó mesh pode ser afirmado."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
feature: "inicio-status"
tipo: "jornada"
modulos:
  - "android/app"
  - "android/feature/home"
  - "android/core/network"
arquivos:
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/Inicio2Screen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/Inicio2ConnectionTrail.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/Inicio2StatusAoVivoMapper.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/Inicio2EstagioDetalheSheet.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/Inicio2UiState.kt"
  - "android/app/src/main/kotlin/io/signallq/app/conectividade/StatusConectividadeAoVivoCoordinator.kt"
  - "android/core/network/src/main/kotlin/io/signallq/app/core/network/topologia/engine/TopologiaRedeEngine.kt"
contratos: []
eventos: []
flags:
  - "consumer_home_enabled"
testes:
  - "android/app/src/test/kotlin/io/signallq/app/conectividade/StatusConectividadeAoVivoCoordinatorTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/Inicio2ConnectionTrailTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/Inicio2StatusAoVivoMapperTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/Inicio2UiStateTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/Inicio2ScreenTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/Inicio2HelpersTest.kt"
  - "android/core/network/src/test/kotlin/io/signallq/app/core/network/topologia/engine/TopologiaRedeEngineTest.kt"
adrs: []
thresholds_em: "android/core/network/src/main/kotlin/io/signallq/app/core/network/topologia/engine/TopologiaRedeEngine.kt"
---

# Início — status da conexão ao vivo

Migrado de `FUNCIONAL.md` (§4.3.2 e §5.12). Fatos conferidos no código em 2026-10-04.

## NEGÓCIO

### 1. Problema e promessa

Responde "minha internet está bem agora?" sem exigir que a pessoa entenda de redes. É a vitrine que costura os outros domínios, não um domínio próprio. Nunca afirma causa sem evidência: confiança baixa vira estado incerto.

### 2. Quando aparece e para quem

`Inicio2Screen` (aba 0) é a única Home. O topo mostra o estado da conexão (Wi-Fi, móvel, Ethernet, offline ou ainda sendo identificada), o veredito humano, uma explicação curta e exatamente um CTA, **Analisar minha conexão**. Cobre os estados: sem análise, último estado conhecido em memória, medição persistida tratada como resultado anterior, carregamento e análise interrompida. Abaixo, uma trilha horizontal de até cinco nós (Internet, equipamento principal, mesh quando confirmado, Wi-Fi e este aparelho). Há o atalho "Vídeos ou chamadas travam"; o antigo card "Outro problema" foi removido por duplicar a entrada. Não há grade técnica, catálogo de ferramentas, diagnóstico completo nem anúncio AdMob na Início. O aviso regulatório da Anatel também não aparece aqui.

### 3. Regras de decisão

- **Dado medido:** a sondagem leve gateway → DNS → rota externa (`ConnectivityDiagnosisSource`).
- **Inferência determinística:** a topologia (`TopologiaRedeEngine`; limiares nele, ver `thresholds_em`).
- **Nó mesh:** só aparece com papel `NO_MESH`, confiança `ALTA`, sem conflito **e** com confirmação independente de roteador central. SSID igual, BSSID único com OUI de fabricante mesh, `SISTEMA_MESH_PROVAVEL` e confiança média/baixa nunca viram afirmação na Início. Só a trilha Wi-Fi consulta `SnapshotScanWifi` e o motor de topologia; scan anterior nunca produz mesh em outro transporte.
- **Status ao vivo (v1.0.9, #1908):** em Wi-Fi, com a Início visível e em foreground, o `StatusConectividadeAoVivoCoordinator` repete a sondagem em rodadas nunca sobrepostas (intervalo em `INTERVALO_ENTRE_RODADAS_MS`). Cada nó Equipamento/Wi-Fi e Internet ganha badge de tom e o Hero usa o status ambiente ("Conexão estável", "Wi-Fi pode estar instável", "Provedor com lentidão"). Móvel e Ethernet não têm esse status. A sondagem ambiente não grava no histórico de diagnósticos.
- **CTA:** reutiliza `onIniciarDiagnostico`, portanto mantém motor e analytics existentes, sem evento paralelo. O ciclo single-flight vem do `DiagnosticOrchestrator`: cada solicitação aceita recebe uma geração monotônica e termina como concluída, erro ou cancelada. O terminal só é publicado depois do fechamento da telemetria e da liberação atômica do gate; o cancelamento antes do primeiro dispatch publica `cancelado` e libera só a mesma reserva. A UI guarda só uma proteção transitória, sem persistir geração em recriação do produtor.
- **Qual medição é exibida** (`ResolvedorMedicaoHome`, `:featureHome`): a da execução atual ou a última salva no histórico, atômica, nunca uma mistura de campos de execuções diferentes; a origem (`ATUAL`/`ANTERIOR`) rotula a UI, ex.: "Resultado anterior · Wi-Fi · há 2h". O módulo opera sobre a struct genérica `MetricasMedicaoHome` porque `feature/home → feature/speedtest` é proibido; a adaptação (`HomeMedicaoAdapter.kt`) vive no `:app`.
- A superfície não classifica resultados como válidos ou expirados (não existe contrato canônico de validade de timestamps e contexto de rede para o veredito).

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Antes da primeira leitura ao vivo | Hero: "Verificando sua rede" |
| Saiu da Início | Valor ao vivo descartado; nunca reexibido como atual |
| Confiança baixa ou exceção na sondagem | Estado **incerto** nos dois estágios; nunca causa afirmada |
| Sem permissão de localização, durante scan, erro ou offline | Trilha parcial e textual |
| Mesh incerto | Nó mesh não aparece |
| "Este aparelho" | Textual; não existe detalhe dedicado do aparelho local, não abre a lista geral da LAN |
| Móvel / Ethernet | Trilha própria, sem status ao vivo |

### 5. Próximo passo e confirmação

Tocar um nó com badge abre a sheet de explicação do estágio, com "Detalhes técnicos" (DNS/gateway/rota externa) só quando há evidência bruta da última rodada. O CTA "Analisar minha conexão" abre o **SignallQ Assist** (ver `FUNCIONAL.md` §5.7). Equipamento, Wi-Fi e sinal móvel reutilizam os overlays canônicos só quando aplicáveis; a trilha só reage ao toque nos nós com badge. O acesso direto ao resultado persistido exato é responsabilidade da issue #1601, não desta superfície.

### 6. Fora de escopo, status e flag

Status: entregue (status ao vivo na v1.0.9). Flag `consumer_home_enabled` gateia a aba 0 (módulo `home`). Fora de escopo: classificar validade de resultado, diagnóstico completo, catálogo de ferramentas.

## TÉCNICO

### 7. Mapa de código

| Responsabilidade | Módulo | Arquivo |
|---|---|---|
| Tela Início | android/app | `ui/screen/Inicio2Screen.kt`, `Inicio2Helpers.kt` |
| Estado e adaptação (`SnapshotRede`, `SnapshotDiagnostico`, medição) | android/app | `ui/screen/Inicio2UiState.kt` (`Inicio2UiStateMapper`) |
| Trilha de nós e regra do mesh | android/app | `ui/screen/Inicio2ConnectionTrail.kt` |
| Status ao vivo por estágio | android/app | `ui/screen/Inicio2StatusAoVivoMapper.kt` |
| Sheet de explicação do estágio | android/app | `ui/screen/Inicio2EstagioDetalheSheet.kt` |
| Coordenador da sondagem ao vivo | android/app | `conectividade/StatusConectividadeAoVivoCoordinator.kt` |
| Regra de medição exibida | android/feature/home | `ResolvedorMedicaoHome.kt`; adaptação em `app/.../ui/screen/HomeMedicaoAdapter.kt` |
| Topologia | android/core/network | `topologia/engine/TopologiaRedeEngine.kt` |

### 8. Dados e contratos

Sem contrato próprio nem persistência. Entradas: `SnapshotRede`, `SnapshotScanWifi`, `SnapshotDiagnostico`, última medição. O status ao vivo vive em memória.

### 9. Eventos e flags

Flag `consumer_home_enabled` (`FeatureFlagKeys.kt`, módulo `home` em `ConsumerFeatureModuleIds`). Eventos de analytics próprios da Início: nenhum verificado; o CTA reaproveita os eventos do diagnóstico existente. `screen_view` da aba e `feature_blocked_remote` para `home`: **não verificado**. Detalhe das flags: `docs/technical/feature-flags-remote-config.md`.

### 10. Falhas e fallback

Exceção na sondagem ou confiança baixa → estado incerto. Sem Wi-Fi → mapeamento sem-Wi-Fi (`mapSemWifi`). Sem permissão de localização, o motor de topologia nem é chamado.

### 11. Testes

Lista em `testes:`. Lacuna: o comportamento de lifecycle (visível/foreground) do coordenador não foi conferido linha a linha na migração.

### 12. Riscos

`MainViewModel` e `AppShell` (dívida crítica, `higiene` §4.2/4.3) ainda intermedeiam o estado. Divergência assumida: nenhuma incerteza de mesh é exibida (decisão #1661).
