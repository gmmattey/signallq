---
title: "ADR-018 — Schema canônico DiagnosticSnapshot para o payload NDS"
description: "Define nome de campo, tipo, opcionalidade e origem no DiagnosticInput para os 16 blocos do snapshot diagnóstico enviado ao NDS, mais as convenções de proveniência e capabilities vs. campo presente."
type: "adr"
status: "ativo"
owner: "Camilo"
last_updated: "2026-09-06"
version: "1.6.0"
---

# ADR-018 — Schema canônico `DiagnosticSnapshot` para o payload NDS

- **Status:** Aceito
- **Data:** 2026-09-02
- **Autor:** Camilo
- **Sub-issue de:** [#1835](https://github.com/buildea-labs/signallq/issues/1835) (NDS-Snapshot-03),
  parte do épico [#1832](https://github.com/buildea-labs/signallq/issues/1832) (P0 — snapshot
  diagnóstico completo).
- **Complementa:** [ADR-017](ADR-017-motor-diagnostico-ia-migra-para-nds.md) — este documento não
  substitui o ADR-017 (que decide a migração do motor/IA para o NDS); formaliza o **contrato de
  dados** que o ADR-017 deixou como "sujeito a completar conforme a implementação avançar".

## Contexto

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

## Decisão

### Convenção geral

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

### Os 16 blocos do `DiagnosticSnapshot`

A tabela usa três colunas de estado:

- **Implementado** — bloco já existe em `NdsDiagnosticsRequest` e é populado por
  `NdsDiagnosticsRequestMapper.toNdsDiagnosticsRequest()`.
- **Parcial** — bloco existe mas tem campos comprovadamente faltando (gap documentado).
- **Planejado** — bloco ainda não existe no payload; a origem no `DiagnosticInput` já existe hoje
  (dado coletado, só não enviado) ou está marcada como não coletada.

#### 1. `execution`

**Estado: implementado** (campo de topo do payload, não um objeto aninhado).

| Campo NDS | Tipo | Opcional | Origem em `DiagnosticInput` |
|---|---|---|---|
| `request_id` | `String` | Não (sempre preenchido) | `DiagnosticInput.executionId` quando não-branco; caso contrário um `UUID` novo é gerado em `toNdsDiagnosticsRequest()` — nunca fica vazio no payload. |

Nota: não existe um `NdsExecutionInfo` dedicado hoje — `executionId` vira diretamente `request_id`
de topo. Ficará como bloco conceitual (para fins de inventário), não como objeto JSON aninhado,
enquanto nenhuma sub-issue pedir campos adicionais de execução (ex.: timestamp, duração total).

#### 2. `app`

**Estado: implementado.**

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `app.id` | `String` | Não | Constante `NDS_APP_ID = "io.signallq.app"` (identificador técnico preservado, `AGENTS.md`). |
| `app.version` | `String` | Não | Parâmetro `appVersion` de `toNdsDiagnosticsRequest()` — vem do `BuildConfig` do chamador (`core/nds` não conhece a versão do app consumer). |
| `locale` | `String` | Não (default `"pt-BR"`) | Constante fixa em `NdsDiagnosticsRequest`. |
| `profile` | `String?` | Sim | `ndsProfile(perfilGamer, context?.objective)` — `"gamer"` quando `perfilGamer=true` ou objetivo mapeia para um perfil; `null` (omitido) fora disso. Vocabulário fechado do NDS (ADR-017): `general`, `gamer`, `streaming`, `wfh`, `smarthome` — hoje o mapper só produz `gamer`/`null`, nunca `general` explícito (pendência já registrada no ADR-017). |
| `capabilities` | `List<String>` | Não (pode ser vazia) | `ndsCapabilities(...)` — ver seção "Capabilities" abaixo. |

#### 3. `connection`

**Estado: implementado.**

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `connection.type` | `String` | Não | `ndsConnectionType(DiagnosticInput.connectionType)` — vocabulário livre (`WIFI`/`MOBILE`/`ETHERNET`/`DISCONNECTED`/`UNKNOWN`), não enum, para não travar o cliente quando o servidor aceitar valor novo. |
| `connection.ssid` | `String?` | Sim | `DiagnosticInput.wifi?.ssid`. |
| `connection.bssid` | `String?` | Sim | `DiagnosticInput.wifi?.bssidMascarado` — sempre mascarado, nunca o BSSID bruto. |
| `connection.natStatus` | `String?` | Sim | `DiagnosticInput.natStatus?.name` (enum `NatStatus`: `DIRECT_PUBLIC`, `CGNAT`, `DOUBLE_NAT_OR_CGNAT`, `UNKNOWN`) — implementado na NDS-Snapshot-09 (issue #1841). Fica sob `connection` porque é uma propriedade da topologia da conexão atual, não um bloco próprio — a issue-mãe não lista `nat` como um dos 16 blocos de topo. `null` quando nenhuma classificação rodou nesta sessão; `"UNKNOWN"` quando rodou mas foi inconclusiva — os dois casos são distintos. |

#### 4. `wifi`

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

#### 5. `wifiScan`

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

#### 6. `speed`

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

#### 7. `quality`

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

#### 8. `dns`

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

#### 9. `gateway`

**Estado: implementado.**

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `gateway.rttGatewayMs` | `Int?` | Sim | `InternetDiagnosticInput.rttGatewayMs` |
| `gateway.connectedDevices` | `Int?` | Sim | `WifiDiagnosticInput.dispositivosNaRede` |
| `gateway.ip` *(planejado, avaliar inclusão segura)* | `String?` | Sim | `WifiDiagnosticInput.gatewayIp` — já coletado; #1832 seção 1 marca como "avaliar inclusão segura" (IP local, não IP público — risco de privacidade menor, mas não decidido ainda). |

Nota de implementação: o bloco `gateway` só é construído quando **pelo menos um** dos dois campos
implementados está presente (`rttGatewayMs != null || dispositivosNaRede != null`) — não quando
ambos. Preservar esse critério ao adicionar `gateway.ip`.

#### 10. `mobile`

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

#### 11. `fiber`

**Estado: implementado**, com o estado operacional (além das métricas ópticas) ainda fora.

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `fiber.rxPower_dbm` | `Double?` | Sim | `FibraDiagnosticInput.rxPowerDbm` |
| `fiber.txPower_dbm` | `Double?` | Sim | `FibraDiagnosticInput.txPowerDbm` |
| `fiber.temperature_c` | `Double?` | Sim | `FibraDiagnosticInput.temperatureCelsius` |
| `fiber.voltage_v` | `Double?` | Sim | Sem origem em `FibraDiagnosticInput` hoje — gap conhecido, já registrado em teste de caracterização (`NdsDiagnosticsRequestMapperTest`, "voltage nao existe... gap conhecido"). |
| `fiber.isUp` *(planejado)* | `Boolean` | Não (campo não-nulo em `FibraDiagnosticInput`) | `FibraDiagnosticInput.isUp` — estado operacional UP/DOWN da fibra (#1832 seção 1: "Estado UP/DOWN ❌ adicionar"). Único campo deste bloco que não é opcional na origem — mas o bloco inteiro (`fiber == null`) continua opcional quando não há equipamento de fibra. |

#### 12. `localEquipment`

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

#### 13. `historical`

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

#### 14. `plan`

**Estado: implementado** (NDS-Snapshot-09, issue #1841) — bloco de topo próprio; a origem continua
sendo um campo de topo do `DiagnosticInput`, não um objeto aninhado na origem.

| Campo NDS | Tipo | Opcional | Origem |
|---|---|---|---|
| `plan.contractedSpeedMbps` | `Int?` | Sim | `DiagnosticInput.velocidadeContratadaMbps` — fonte `PreferenciasAppRepository.planoInternetFlow` (informado pelo usuário). **Nunca inferido** este valor quando o usuário não informou (regra explícita do #1832 seção 6) — ausência do campo é o comportamento correto, não um valor default. Bloco inteiro fica `null` (omitido do JSON) quando o campo não existe. |

#### 15. `networkIdentity`

**Estado: planejado, sem origem coletada hoje.** Nenhum campo de ISP/ASN/servidor de teste existe
em `DiagnosticInput` no momento desta ADR — `InternetDiagnosticInput.serverName`/`serverRegion`/
`serverHost` existem (servidor usado no speedtest) mas não são ISP/ASN.

| Campo NDS (sugerido) | Tipo | Opcional | Origem |
|---|---|---|---|
| `networkIdentity.isp` | `String?` | Sim | **Sem origem hoje** — precisa de nova coleta (#1832 seção 10). |
| `networkIdentity.asn` | `String?` | Sim | **Sem origem hoje.** |
| `networkIdentity.testServer` | `String?` | Sim | `InternetDiagnosticInput.serverName` (já coletado, só não mapeado para este bloco). |

Geolocalização precisa **não** entra neste bloco — proibido explicitamente pelo #1832 seção 10.

#### 16. `context`

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

### Convenção de proveniência (`source`)

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

### `capabilities` vs. "campo presente" (seção 13 do #1832)

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

## Consequências

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

## Alternativas consideradas

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

## Pendências

- Revisão pelo dono técnico do NDS (`network-diagnostics-service`) antes de qualquer bloco
  "planejado" desta ADR começar a ser implementado — critério de aceite da #1835.
- Decisão de nome de campo definitivo para os blocos "planejado" cabe à sub-issue que implementar
  cada um; os nomes sugeridos aqui são o ponto de partida, não a palavra final caso a integração
  real com o NDS peça um vocabulário diferente.
- `networkIdentity.isp`/`asn` seguem sem fonte de coleta no app — decisão de produto (vale
  adicionar um provedor de lookup de ASN/ISP?) fica com Claudete/Luiz, fora do escopo desta ADR.
