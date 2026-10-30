---
title: "WiFi Casa — mapeamento do sinal por cômodo"
description: "Mapeia o sinal Wi-Fi cômodo a cômodo num grid 2D e compara Antes×Depois ao reposicionar roteador ou mesh, para quem quer decidir com dado onde o Wi-Fi cai."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.0.1"
feature: "wifi-casa"
tipo: "jornada"
modulos:
  - "android/app"
  - "android/core/database"
  - "android/core/diagnostico"
arquivos:
  - "android/app/src/main/kotlin/io/signallq/app/wificasa/"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/WifiCasaScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/WifiCasaGridCanvas.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShellSinalWifiOverlay.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalWifiScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/sinalwifi/SinalWifiViewModel.kt"
  - "android/core/database/src/main/kotlin/io/signallq/app/core/database/wificasa/"
contratos: []
eventos: []
flags:
  - "consumer_wifi_enabled"
testes:
  - "android/app/src/test/kotlin/io/signallq/app/wificasa/WifiCasaComparacaoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/wificasa/WifiCasaGridPosicionamentoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/wificasa/WifiCasaViewModelTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/sinalwifi/SinalWifiViewModelTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalWifiScreenTest.kt"
  - "android/core/database/src/androidTest/kotlin/io/signallq/app/core/database/wificasa/MapeamentoWifiDaoTest.kt"
  - "android/core/database/src/androidTest/kotlin/io/signallq/app/core/database/Migration21Para22Test.kt"
adrs: []
thresholds_em: "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalTopologiaHelpers.kt"
---

# WiFi Casa — mapeamento do sinal por cômodo

Migrado de `functional/WIFI_CASA_MAPEAMENTO_SPEC.md` (v1.0.9; implementado e validado em 2026-09-26; spec removida, recuperável com `git show bdcae7c4:docs_ai/functional/WIFI_CASA_MAPEAMENTO_SPEC.md`), `FUNCIONAL.md` §5.13 e do trecho "WiFi Casa" da §5.3. Fatos conferidos no código em 2026-10-04.

## NEGÓCIO

### 1. Problema e promessa

Responde "onde exatamente o sinal cai na minha casa, e a mudança de lugar do roteador ajudou?". Atende quem tem Wi-Fi fraco em cômodos específicos, quem comprou mesh ou vai reposicionar o roteador e quem está se mudando. Evoluiu da antiga ferramenta "Encontrar um bom lugar" (indicador ao vivo, ponto único, sem persistência). Inspirada no app irmão iOS da família Buildea, mas só a camada de UX espacial foi importada, não o motor de diagnóstico.

**Nunca promete precisão de planta ou de metragem.** O grid é declarado pelo usuário (sem planta importada, sem escala); não há triangulação nem geometria inferida. O app diz "o sinal medido no marcador que você chamou de 'Quarto 2' foi X", nunca "seu sinal aqui é X porque você está a Y metros do roteador" (decisão de 2026-08-19, preservada).

### 2. Quando aparece e para quem

Card **"WiFi Casa"** no hub Ferramentas ("Mapeie o sinal em cada cômodo e compare antes e depois de mudar o roteador"). Requer permissão de localização (para ler o sinal) e a flag de Wi-Fi ligada. `TipoFerramenta.SINAL_WIFI` e o overlay `SinalWifi` seguem como identificadores técnicos; a tela vigente é `WifiCasaScreen`. Telas internas (`WifiCasaTela`): inicial, grid, captura, lista de anteriores e comparação.

### 3. Regras de decisão

- **Dado medido:** leitura pontual de RSSI no marcador (mesmo padrão de amostragem do indicador ao vivo `SinalWifiScreen`/`SinalWifiViewModel`, reaproveitado no fluxo de captura).
- **Classificação:** sempre `signalQuality(rssi, banda)` (categorias Excelente/Bom/Regular/Fraco), calculada na leitura e nunca guardada como coluna. O WiFi Casa consome o motor, não duplica vocabulário nem limiar (ver `thresholds_em`).
- **Fluxo:** (1) cria mapeamento com nome opcional (padrão: data/hora); (2) posiciona o marcador do roteador/mesh, opcional; (3) em cada cômodo, "Adicionar marcador aqui", posiciona no grid e rotula (rótulo obrigatório; sugestões Sala, Quarto, Cozinha, Escritório + campo livre); (4) o app amostra o RSSI por alguns segundos e salva o resultado classificado; (5) encerra com "Salvar e concluir" ou "Vou reposicionar o roteador" (fecha como baseline).
- **Antes×Depois é manual:** o usuário decide quando fechar o baseline. Não há detecção automática de troca de posição (BSSID/RSSI). Ao concluir um novo mapeamento com baseline pendente da mesma rede (`networkId`), o app oferece a comparação.
- **Comparação:** marcadores de cômodo casam por rótulo normalizado (trim, sem diferenciar maiúsculas; "Sala" = " sala ", mas "Sala 1" ≠ "Sala 2"). Resultado por marcador: melhorou, piorou, não mudou, novo ou removido, pela ordem Fraco < Regular < Bom < Excelente. O marcador de roteador nunca entra na comparação. Não inventa correspondência entre rótulos diferentes.
- O usuário pode editar rótulo ou remover marcador antes de concluir; pode retomar mapeamento incompleto após fechar o app; reabre mapeamentos anteriores em modo leitura.
- **Indicador ao vivo reaproveitado** (`SinalWifiScreen`, antiga "Encontrar um bom lugar"; já não é destino próprio): categoria Excelente/Bom/Regular/Fraco em destaque com dBm secundário, barras de sinal, velocidade do link, padrão Wi-Fi e MU-MIMO, selo "Ao vivo"; o pulso fica estático com "Remover animações". Amostra o RSSI a cada 1500 ms via `WifiManager`, só com a tela em `RESUMED` (cancela sozinha ao sair).

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Sem permissão de localização | Pede a permissão; não mostra leitura |
| Sem mapeamento em andamento | Oferece "Começar mapeamento novo" ou "Ver mapeamentos anteriores" (se houver histórico) |
| Marcador sem RSSI | Sem categoria; não vira "Fraco" nem zero |
| Marcador só em um dos lados | "novo" ou "removido" na comparação |
| Redes diferentes | Sem comparação entre `networkId` diferentes |
| Wi-Fi desligado | Botão "Ligar Wi-Fi": abre o painel do sistema `Settings.Panel.ACTION_WIFI` sem sair do app; em Android < 10 liga direto por `WifiManager.setWifiEnabled` |
| Permissão de localização negada | Botão "Conceder permissão"; "Abrir ajustes do Android" quando bloqueada permanentemente |
| Wi-Fi ligado sem rede associada | "Sem conexão Wi-Fi" |
| Wi-Fi desligado ou rede perdida durante a captura | Zera o estado anterior; nunca mostra leitura antiga como se fosse atual |

### 5. Próximo passo e confirmação

Após reposicionar roteador/mesh, o usuário refaz o mapeamento e vê a comparação Antes×Depois por cômodo, que confirma com dado se a mudança valeu a pena. A ferramenta não recomenda onde pôr o roteador.

### 6. Fora de escopo, status e flag

Status: entregue na v1.0.9. Flag: `consumer_wifi_enabled` (a mesma do módulo Wi-Fi; `ConsumerFeatureModuleIds.WIFI`). Fora de escopo: importar/desenhar planta, detecção automática de reposicionamento, recomendação por IA a partir do grid, comparação entre redes diferentes, exportar/compartilhar. Dados só locais (Room), sem envio à nuvem.

**Métricas de sucesso (da spec original; não há evento de analytics próprio, ver seção 9):** taxa de conclusão de mapeamentos iniciados (não abandonados no primeiro marcador); proporção que chega a ter um Antes×Depois comparado; recorrência (abrem "WiFi Casa" mais de uma vez em 30 dias).

**Critérios de aceite originais:** criar mapeamento com ao menos 2 marcadores rotulados com RSSI real e ver a classificação de cada um; fechar como baseline, criar o segundo e ver a comparação com ao menos um marcador correspondente por rótulo; fechar o app no meio e reabrir restaura os marcadores salvos; nenhuma alteração em `MetricClassifier`/`WifiSignalQualityEngine` (o WiFi Casa consome, não duplica). Requisitos não funcionais: sem polling em segundo plano entre marcadores; acessibilidade do grid com alternativa não visual (lista equivalente) é requisito, ver riscos; nenhuma dependência paga nova.

## TÉCNICO

### 7. Mapa de código

| Responsabilidade | Módulo | Arquivo |
|---|---|---|
| Tela e grid | android/app | `ui/screen/WifiCasaScreen.kt`, `WifiCasaGridCanvas.kt` |
| Overlay (nome técnico `SinalWifi`) | android/app | `ui/screen/AppShellSinalWifiOverlay.kt` |
| Estado, sessão, captura | android/app | `wificasa/WifiCasaViewModel.kt`, `WifiCasaUiState.kt` |
| Posicionamento no grid | android/app | `wificasa/WifiCasaGridPosicionamento.kt` |
| Comparação Antes×Depois (função pura) | android/app | `wificasa/WifiCasaComparacao.kt` |
| Amostragem de RSSI reaproveitada | android/app | `sinalwifi/SinalWifiViewModel.kt`, `ui/screen/SinalWifiScreen.kt` |
| Persistência | android/core/database | `wificasa/MapeamentoWifiEntity.kt`, `MarcadorMapeamentoEntity.kt`, `MapeamentoWifiDao.kt` |
| Classificação de sinal | android/app | `ui/screen/SinalTopologiaHelpers.kt` (`signalQuality`) |

### 8. Dados e contratos

Duas tabelas Room, sessão pai `mapeamento_wifi` + marcadores filhos (precedente `chat_sessions`/`chat_messages`), criadas pela `MIGRATION_21_22` (aditiva; banco na versão 22). Sessão: nome, `networkId`, criado/atualizado em, `status`, `comparadoComSessaoId` (auto-FK com `SET NULL`). Marcador: tipo `comodo` ou `roteador`, rótulo, posição no grid, RSSI e banda (nome do enum `BandaWifi`). Sem contrato OpenAPI.

### 9. Eventos e flags

`consumer_wifi_enabled` gateia o overlay (`AppShell.kt`, `bloquearRota` com módulo `wifi`). `screenName()` de `SINAL_WIFI` e `SINAL_CANAIS_MOVEL` é o mesmo, `sinal_wifi`. Evento próprio do WiFi Casa: nenhum verificado (o `feature_used` com `feature_id=wifi` em `MainActivity.kt` está no refresh do Sinal, não nesta tela).

### 10. Falhas e fallback

Amostragem só com a tela em foreground (`repeatOnLifecycle(RESUMED)`), sem polling em segundo plano entre marcadores. Leitura/escrita Room fora da thread principal. Sem permissão ou sem Wi-Fi, o fluxo de captura não produz leitura.

### 11. Testes

Lista em `testes:`: comparação, posicionamento, ViewModel, DAO e migração 21→22. Lacuna: sem teste de UI do grid; alternativa não visual do grid para TalkBack (lista equivalente) é requisito do spec original e **não foi verificada** nesta migração.

### 12. Riscos

- Os KDocs de `MapeamentoWifiEntity.kt`, `CoreDatabaseModulo.kt`, `WifiCasaUiState.kt`, `WifiCasaViewModel.kt`, `WifiCasaScreen.kt`, `AppShellSinalWifiOverlay.kt` e `Migration21Para22Test.kt` citam o spec removido (`docs/functional/WIFI_CASA_MAPEAMENTO_SPEC.md`); devem apontar para esta página (código fora do escopo da redução de docs).
- `FerramentasScreenTest.kt` e `AppShellRootRegistryTest.kt` eram "a atualizar" no spec: **não verificado** se já foram.
- Primeiro uso de canvas de posicionamento livre no app; vigiar acessibilidade.
