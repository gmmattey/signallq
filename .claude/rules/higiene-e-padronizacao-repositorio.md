# Regra permanente — Higiene e padronização do repositório

- **Status:** ativo
- **Última validação:** 2026-10-04 (§5 de módulos alinhada a `android/settings.gradle.kts`; regra de páginas de feature em §10; demais seções validadas em 2026-08-19)
- **Fonte de verdade:** este arquivo (`.claude/rules/higiene-e-padronizacao-repositorio.md`) — não duplicar em `docs/`, `AGENTS.md`, mirrors ou docs de módulo
- **Escopo:** repositório `buildea-labs/signallq` (monorepo SignallQ) inteiro — Android, Admin, Cloudflare, docs
- **Responsável:** Marcelo (dono do processo). Esta regra se aplica a todos os agentes autorizados e aplicáveis ao repositório, conforme a governança canônica em ../ai-governance, e a qualquer sessão humana no repo.

Declarada como obrigatória no `AGENTS.md` deste repositório (seção "Regras operacionais
obrigatórias") e referenciada pelos perfis dos agentes canônicos aplicáveis ao repositório. Não
copiar o conteúdo completo em nenhum outro lugar — só linkar.

`AGENTS.md` é a fonte de contexto do repositório para todas as ferramentas; `CLAUDE.md` e
`.claude/CLAUDE.md` são adaptadores mínimos que apenas incluem `AGENTS.md`. As regras operacionais
comuns a todos os repositórios vivem em `../ai-governance/policies/`. Não voltar a concentrar
processo em `.claude/CLAUDE.md`: em 2026-08-03 aquele arquivo passou de 371 linhas para uma linha
de include, e as referências a suas seções ficaram órfãs por três dias.

---

## 1. Princípio geral

Durante qualquer tarefa:

> Deixe a área tocada em estado igual ou melhor do que estava antes.

Isso não significa tentar arrumar o repositório inteiro. A melhoria deve ser:

- incremental;
- relacionada à área modificada;
- segura;
- comprovável;
- pequena o suficiente para não desviar da entrega principal.

Não transformar uma correção simples em uma reforma arquitetural. Não ignorar problemas evidentes
encontrados na área tocada.

---

## 2. Idioma padrão

O idioma padrão do projeto é português do Brasil. Use PT-BR em: respostas ao usuário, planos,
relatórios, documentação, issues, descrições de PR, mensagens de erro exibidas ao usuário,
comentários que expliquem regras de negócio, critérios de aceite, nomes de branches e commits
quando isso não contrariar a convenção técnica existente. Não usar inglês desnecessariamente em
documentação ou comunicação.

### Identificadores de código

Use português para conceitos de produto e domínio: diagnóstico, rede, dispositivo, velocidade,
histórico, ajustes, sinal, fibra, topologia, recomendação, monitoramento, operadora, provedor.

Mantenha em inglês apenas termos técnicos consolidados no ecossistema Android/Kotlin ou já adotados
como sufixo estrutural: `Screen`, `ViewModel`, `UiState`, `Repository`, `UseCase`, `Worker`, `Dao`,
`Entity`, `Mapper`, `Parser`, `Driver`, `Client`, `Provider`, `Factory`, `Coordinator`, `Module`,
`Test`.

Exemplos aceitáveis: `DiagnosticoScreen.kt`, `DiagnosticoViewModel.kt`, `DiagnosticoUiState.kt`,
`RepositorioDiagnosticoLocal.kt` ou `DiagnosticoRepository.kt` (conforme o padrão dominante da
área), `ClassificadorTopologiaRede.kt`, `MedirLatenciaGatewayUseCase.kt`,
`EquipamentoInternetMapper.kt`.

Não introduzir combinações arbitrárias como `NetworkDiagnosticoManager`, `WifiAnaliseHelper` ou
`DeviceRedeUtils`.

Ao tocar em código antigo que mistura idiomas, padronize somente quando a renomeação for local,
segura e completamente validável. Renomeações espalhadas por vários módulos são tarefa específica.

Não renomear identificadores técnicos preservados pelo projeto: `io.signallq.app`,
`buildea-labs/signallq`, `linkaKotlin.db`, `linkaPreferencias`, canais `linka_*`, workers cujos
nomes técnicos já estejam publicados (ver `AGENTS.md`, "Identificadores técnicos").

---

## 3. Precedência de fontes técnicas (código vs. documentação)

Distinta do roteamento de demandas entre agentes (`../ai-governance/policies/demand-routing.md`) —
esta ordem resolve divergência entre **código e documentação** quando os dois descrevem o mesmo
fato técnico:

1. código executado e testes;
2. `android/settings.gradle.kts`;
3. `android/gradle/libs.versions.toml`;
4. arquivos `build.gradle.kts`;
5. contratos e schemas realmente consumidos;
6. ADRs vigentes;
7. documentação ativa;
8. documentação histórica.

Nunca use como verdade atual um documento em `_archive`. Nunca atualize somente a data ou versão de
um documento para fazê-lo parecer atual. Antes de repetir versão, SDK, quantidade de módulos,
caminhos ou nomes de classes, confirme diretamente nas fontes acima.

---

## 4. Problemas estruturais já conhecidos

Dívidas conhecidas do repositório, validadas em 2026-07-15. Reconfirme se ainda existem antes de
agir — não presuma que seguem exatas conforme o tempo passa.

### 4.1 Caminho físico legado de packages (`io/veloo` vs `io.signallq.app`) — RESOLVIDO (2026-08-15)

Migração concluída em 2026-08-15 (issue #1645, épico de 1 PR). **525 arquivos `.kt`** foram
movidos de `io/veloo/app/kotlin/` para `io/signallq/app/` em todos os 15 módulos afetados
(`:app`, `core/database`, `core/datastore`, `core/network`, `core/permissions`, `core/telephony`,
e todos os 9 módulos `feature/*`), preservando blame via `git mv`. Nenhuma alteração de
`package` foi necessária — os arquivos já declaravam `io.signallq.app.*`; o path físico agora
está alinhado ao package Kotlin.

Estado atual: **zero arquivos** em `android/**/kotlin/io/veloo/**`. Não recriar essa árvore.
Todo código novo nasce em `android/<modulo>/src/<sourceSet>/kotlin/io/signallq/app/...`.

### 4.2 `MainViewModel.kt`

Caminho real: `android/app/src/main/kotlin/io/signallq/app/MainViewModel.kt` — **2191 linhas**
(acima do limiar de "dívida crítica" da seção 7). Concentra responsabilidades demais e não deve
continuar crescendo indiscriminadamente.

Ao tocar nele:
1. identifique qual responsabilidade está sendo modificada;
2. não adicione uma nova responsabilidade diretamente se ela puder viver em um componente dedicado;
3. prefira extrair orquestração, persistência, analytics, mapeamentos, diagnóstico, recomendações,
   ISP, DNS ou topologia para componentes próprios;
4. mantenha no `MainViewModel` apenas composição de estados e coordenação de alto nível;
5. crie testes de caracterização antes de extrações com risco de comportamento;
6. não crie outro ViewModel gigante apenas para reduzir linhas.

### 4.3 `AppShell.kt`

Caminho real: `android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShell.kt` — **1146
linhas** (acima do limiar de extração obrigatória da seção 7). Deve ser shell de composição e
navegação, não depósito de regras de negócio.

Ao tocar nele, prefira separar: estado de navegação, controle da pilha de overlays, adaptação de
estados das telas, wiring entre features, componentes da barra inferior, dialogs e sheets
independentes. Não mover lógica de uma tela gigante para outra função privada no mesmo arquivo e
chamar isso de modularização.

### 4.4 `AjustesScreen.kt`

Caminho real: `android/app/src/main/kotlin/io/signallq/app/ui/screen/AjustesScreen.kt` — **771
linhas** (acima do limiar de extração obrigatória da seção 7, mas diminuiu de 809 em relação ao último audit). Ainda contém múltiplos fluxos e
componentes.

Ao tocar em uma seção de Ajustes: extraia sheets e fluxos independentes para arquivos próprios,
agrupe por responsabilidade do usuário, não crie arquivos genéricos como `AjustesUtils.kt`, mantenha
`AjustesScreen.kt` como composição das seções, preserve uma única fonte para cada configuração.

### 4.5 `HomeScreen.kt`

Caminho real: `android/app/src/main/kotlin/io/signallq/app/ui/screen/HomeScreen.kt` — **2360
linhas** (a entrada citava 3938, desatualizada; #1659/#1743 já haviam reduzido para 2497 antes
desta correção, e a issue #1749 removeu ~140 linhas de código morto — `WifiFactorsSection`/
`WifiQualityBadge`/`FactorRow`, sem call site algum). Ainda acima do limiar de "dívida crítica" da
seção 7. Concentra a tela Início e múltiplas sheets (Meu dispositivo, Internet/Provedor, Rede
móvel, Medir agora, mais SignalQualitySheet, QualidadePlaceholderSheet, MedicaoTipoSheet).

Ao tocar nele:
1. identifique qual sheet ou seção está sendo modificada;
2. não adicione nova sheet diretamente — extraia para arquivo dedicado antes;
3. prefira separar: estado de cada sheet, orquestração de métrica de sinal, adaptadores de dados,
   componentes de visualização, wiring com features subjacentes;
4. mantenha em `HomeScreen.kt` apenas a composição da tela principal e delegação das sheets;
5. cada sheet independente deve ter seu próprio arquivo (ex.: `MeuDispositivoSheet.kt`,
   `InternetProveedorSheet.kt`, `MedicaoTipoSheet.kt`);
6. crie testes de caracterização antes de extrações com risco de comportamento visual ou estado.

### 4.6 `EquipamentoInternetScreen.kt` — RESOLVIDO (atualizado 2026-07-24)

Caminho real: `android/app/src/main/kotlin/io/signallq/app/ui/screen/EquipamentoInternetScreen.kt`
— **550 linhas** (abaixo do limiar de extração obrigatória da seção 7). A entrada anterior desta
seção citava 1549 linhas; o número está desatualizado — o redesign de 2026-07-18 (bug #6, spec de design)
já extraiu os painéis por capacidade em componentes próprios no mesmo pacote:
`EquipamentoStatusPanel.kt` (status/disponibilidade/uso/alerta), `EquipamentoModuloTecnicoCard.kt`
(módulos técnicos Fibra/WAN/LAN/Wi-Fi/dispositivos), `EquipamentoTopologiaCard.kt`,
`EquipamentoDeviceSelectorCard.kt`, `EquipamentoInfoTecnicaCard.kt`, `EquipamentoAcoesCard.kt`,
`EquipamentoPanelMapper.kt`. `EquipamentoInternetScreen.kt` hoje é só chrome (TopBar, estados
carregando/indisponível) e composição/ordem dos cards, como a seção pedia.

Continua valendo ao adicionar um painel novo: cada capacidade nova ganha seu próprio arquivo
(`Equipamento*Card.kt`), nunca inchar `ModuloTecnicoCard`/`StatusEquipamentoCard`. Reavaliar esta
seção se o arquivo voltar a crescer.

### 4.7 `DispositivosScreen.kt` — RESOLVIDO (atualizado 2026-08-19)

Caminho real: `android/app/src/main/kotlin/io/signallq/app/ui/screen/DispositivosScreen.kt`. A
entrada anterior citava 1386 linhas concentrando lista, sheets de detalhe e helpers — a issue
#1663 (épico #1647, Task 2.0.15) extraiu o arquivo em três: `DispositivosScreen.kt` (168 linhas,
scaffold puro — Scaffold/TopBar/roteamento de estado), `DispositivosLista.kt` (622 linhas — lista
com pull-to-refresh, `GatewayItem`/`ApMeshItem`/`DispositivoItem`, estados vazio/sem-Wi-Fi) e
`DispositivoDetalheSheet.kt` (617 linhas — `DeviceDetailSheet`, `MeshApSheet`, `LkListRow` e
helpers de ícone/rótulo compartilhados entre lista e sheets). Testes de caracterização
(`DispositivosScreenExtracaoCaracterizacaoTest.kt`) comprovam que a extração não mudou
comportamento.

Continua valendo ao adicionar uma sheet ou seção nova: cada sheet independente ganha seu próprio
arquivo, nunca inchar `DispositivoDetalheSheet.kt` com um fluxo não relacionado a detalhe de
dispositivo. Reavaliar esta seção se `DispositivosLista.kt` ou `DispositivoDetalheSheet.kt`
voltarem a crescer acima de 800 linhas.

### 4.8 `JogosScreen.kt` — RESOLVIDO (removido em 2026-07-26)

Caminho antigo: `android/app/src/main/kotlin/io/signallq/app/ui/screen/JogosScreen.kt` —
**1120 linhas** (acima do limiar de extração obrigatória da seção 7). Concentrava o fluxo de
teste direcionado por jogo com 5 etapas (GH#935).

A issue #1487 fundiu esse fluxo com o Modo gamer (Feature #550, `ModoGamerScreen.kt` +
`ModoGamerConfigResultadoSection.kt`, ambos bem menores e já divididos por etapa/responsabilidade)
— `JogosScreen.kt`, `JogosViewModel.kt`, `JogoConexaoEngine.kt`, `PerfilThresholds.kt`,
`GameCatalog.kt`, `GameArtworkCatalog.kt`/`GameIconCatalog.kt` foram removidos, junto com
`Overlay.Jogos`. A dívida está resolvida — não recriar um segundo fluxo "Jogos" paralelo ao
Modo gamer; qualquer refinamento de teste de jogo entra em `ModoGamerEngine`/`ModoGamerScreen`.

### 4.8b `SinalScreen.kt` — RESOLVIDO (atualizado 2026-08-19)

Caminho real: `android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalScreen.kt` — **476
linhas** (abaixo do limiar de extração obrigatória da seção 7). A entrada anterior citava 3503
linhas — desatualizada; a Task 2.0.12 (issue #1660, épico #1647, PR #1766, 2026-08-19) fez a
extração por aba que a seção já sugeria como próximo passo: `SinalMovelSection.kt` (539 linhas),
`SinalCanalSection.kt` (1215 linhas) e `SinalWifiSection.kt` (1110 linhas) ganharam arquivo
próprio no mesmo pacote, mais `SinalSharedComponents.kt` (79 linhas) pros componentes
compartilhados entre Wi-Fi e Canal. `SinalScreen.kt` hoje é scaffold (`Scaffold`, `TopBar`,
`TabRow`, delegação de abas, sheets de permissão de localização/telefonia), como a seção pedia.
Extração foi puramente estrutural — comportamento idêntico, comprovado por teste de
caracterização dedicado (`SinalScreenExtracaoAbaCaracterizacaoTest.kt`).

**Nova dívida gerada por essa extração, ainda não resolvida:** `SinalWifiSection.kt` (1110) e
`SinalCanalSection.kt` (1215) já nascem acima do limiar de extração obrigatória da seção 7 (800
linhas). Candidatas a nova extração incremental por componente — mas só depois que as fatias de
produto 2.0.13/2.0.14/2.0.20 (issues #1661/#1662/#1668, que migram essas abas pro Design System
2.0 de verdade) definirem a forma final, pra não extrair duas vezes.

Ao tocar em qualquer uma das seções (`SinalMovelSection.kt`/`SinalCanalSection.kt`/
`SinalWifiSection.kt`):
1. motor/classificador real (regra de negócio de diagnóstico, ex. limiares RSRP/canal/topologia)
   pertence a `core/diagnostico` ou `core/network`, não à Section; função pura só de apoio visual
   da própria tela (ícone, rótulo, cor, agrupamento) vai em `SinalTopologiaHelpers.kt` — não
   adicione nenhuma das duas direto no Composable;
2. mantenha em `SinalScreen.kt` apenas a composição do Scaffold, TabRow e delegação das abas —
   não volte a inchar esse arquivo;
3. crie testes de caracterização antes de extrações com risco de comportamento visual ou estado.

### 4.9 Identificação de topologia e dispositivos

Quando encontrar motores, heurísticas ou classificadores concorrentes:
1. liste todos os consumidores;
2. registre as entradas disponíveis para cada implementação;
3. compare os resultados para os mesmos cenários;
4. defina uma fonte de verdade;
5. preserve adaptadores somente quando houver diferenças legítimas de contrato;
6. não crie um novo classificador para contornar os existentes;
7. proteja a consolidação com testes de caracterização.

Features não podem depender diretamente de outras features. A composição entre domínios acontece em
`:app` ou por contratos normalizados em um módulo `core` adequado.

### 4.10 Documentação divergente

Valide antes de confiar: referências antigas a versões anteriores, quantidades antigas de módulos,
caminhos legados `io/veloo` (path físico removido em 2026-08-15 — ver §4.1), nomes antigos da
marca, navegação anterior, agentes arquivados, issues antigas, telas ou superfícies descontinuadas,
módulos que já mudaram de localização. Ao modificar uma funcionalidade, atualize somente a
documentação diretamente relacionada — não revise todos os documentos do projeto dentro de uma
tarefa comum.

### 4.11 Espaçamento hardcoded em vez de token (Android)

Auditoria (2026-07-26): ~270 ocorrências de `.dp` literal em `padding()`/`size()`/
`width()`/`height()`/`offset()` direto em Composables do Consumer (`android/app/.../ui/component/`,
`ui/screen/`), em vez de constante de espaçamento do design system. Tipografia está limpa (zero
achado — tudo via `MaterialTheme.typography.*`); cor está majoritariamente limpa (as ocorrências de
`Color(0xFF...)` encontradas são cor de marca de operadora/WhatsApp, não violação — ver #1499 pros
6 casos reais de `Color.White` hardcoded).

Volume disperso demais (~9 telas/componentes diferentes) pra virar um bug único executável — não
abrir issue "arrumar espaçamento do app inteiro". Se for tocar em um desses arquivos por outro
motivo, aproveite pra trocar o `.dp` literal local por token, sem expandir a tarefa. Migração
completa e deliberada (se algum dia for priorizada) deve ser incremental por tela, não
correção-em-massa numa PR só.

---

## 5. Convenção de módulos

Estrutura física atual (validada em `android/settings.gradle.kts`, 22 módulos):

```
android/
├── app/                   (:app)
├── core/
│   ├── network/           (:coreNetwork)
│   ├── database/          (:coreDatabase)
│   ├── datastore/         (:coreDatastore)
│   ├── permissions/       (:corePermissions)
│   ├── telephony/         (:coreTelephony)
│   ├── recommendation/    (:coreRecommendation)
│   ├── relatorio/         (:core:relatorio)
│   ├── diagnostico/       (:core:diagnostico)
│   ├── featureflags/      (:core:featureflags)
│   ├── nds/               (:core:nds)
│   └── probejogo/         (:core:probejogo)
└── feature/
    ├── home/              (:featureHome)
    ├── speedtest/         (:featureSpeedtest)
    ├── wifi/              (:featureWifi)
    ├── devices/           (:featureDevices)
    ├── dns/               (:featureDns)
    ├── fibra/             (:featureFibra)
    ├── router/            (:featureRouter)
    ├── diagnostico/       (:featureDiagnostico)
    ├── history/           (:featureHistory)
    └── settings/          (:featureSettings)
```

Os cinco módulos `:core:*` mais novos (`relatorio`, `diagnostico`, `featureflags`, `nds`,
`probejogo`) já nascem no padrão hierárquico. Os demais aliases (`:coreNetwork`, `:featureWifi`
etc.) são legado compatível, enquanto as pastas já usam estrutura hierárquica. O padrão desejado
para uma **futura migração dedicada** é renomear esses aliases para `:core:network`,
`:core:database`, `:core:datastore`, `:core:permissions`, `:core:telephony`,
`:core:recommendation`, `:feature:home`, `:feature:wifi`, `:feature:devices`, `:feature:dns`,
`:feature:speedtest`, `:feature:diagnostico`, `:feature:fibra`, `:feature:router`,
`:feature:history`, `:feature:settings`.

Não renomear aliases Gradle de forma oportunista — essa migração afeta dependências, CI, comandos,
documentação e possivelmente automações, e deve ser tarefa dedicada. Não criar novos módulos usando
o padrão antigo concatenado.

### Responsabilidade dos módulos

`:app` deve conter somente: inicialização, navegação, composição entre features, DI no nível da
aplicação, integrações que realmente dependam de múltiplas features, adaptação entre contratos.

`:feature:*` deve possuir: interface da feature, estado da feature, ViewModel ou state holder da
feature, casos de uso, regras específicas do domínio, componentes exclusivos daquela feature.

`:core:*` deve possuir: infraestrutura compartilhada, contratos normalizados, persistência, rede,
permissões, serviços reutilizáveis, regras que não pertencem a uma única feature.

Não criar `core-common`, `core-utils` ou outro módulo genérico usado como gaveta de bagunça.

---

## 6. Convenção de arquivos e símbolos Kotlin

### Arquivos

- Usar `PascalCase.kt`. O nome do arquivo deve corresponder ao principal símbolo público.
- Manter preferencialmente um tipo público principal por arquivo — tipos auxiliares pequenos e
  fortemente acoplados podem permanecer juntos.
- Não usar acentos, espaços, datas ou números arbitrários em nomes de arquivos.
- Não usar sufixos como `Novo`, `Antigo`, `Final`, `Final2`, `V2`, `Temp` ou `Backup`. Sufixos de
  versão só são permitidos quando representam contrato ou protocolo real, não tentativa informal de
  substituir código anterior.

### Classes e objetos proibidos como padrão genérico

Evitar nomes vagos: `Utils`, `Helper`, `Manager`, `Common`, `Misc`, `Base`, `Global`, `Data`,
`Service` (quando não for realmente um serviço), `Controller` (quando a responsabilidade não estiver
clara).

Substitua pelo comportamento real: `WifiUtils` → `CalculadoraCanalWifi`; `NetworkHelper` →
`MedidorLatenciaGateway`; `DeviceManager` → `ScannerDispositivosRede`; `DataMapper` →
`ResultadoSpeedtestMapper`.

### Funções

Use verbo que represente a ação. Evite nomes genéricos como `processar`, `executar`, `tratar` ou
`carregar` sem contexto. Função longa deve ser dividida por comportamento, não por blocos aleatórios
de linhas. Funções puras e regras determinísticas devem ser extraídas para permitir teste unitário.

### Enums

Novos enums devem usar constantes em `UPPER_SNAKE_CASE`. Antes de renomear enums existentes,
verifique: serialização, Room, DataStore, JSON, analytics, Worker Cloudflare, nomes persistidos,
compatibilidade de migrations.

---

## 7. Limites de tamanho como sinal de alerta

Os limites abaixo são alertas, não justificativa para criar abstrações inúteis.

- Arquivo acima de 400 linhas: revisar coesão.
- Arquivo acima de 800 linhas: considerar extração obrigatoriamente.
- Arquivo acima de 1.200 linhas: tratar como dívida crítica.
- Função acima de 60 linhas: revisar responsabilidades.
- Composable acima de 150 linhas: revisar componentes e estado.
- Classe com mais de 10 dependências no construtor: revisar orquestração e agregação.

Ao tocar em arquivo acima de 800 linhas: não aumentar seu tamanho sem justificativa; extrair ao
menos uma responsabilidade relacionada, quando isso for pequeno e seguro; caso a extração seja
arriscada ou ampla, registrar ou atualizar uma issue agrupada. Não dividir arquivos apenas para
satisfazer contagem de linhas. Cada extração deve possuir nome, responsabilidade e dependências
claras.

---

## 8. Regra para correção oportunista

Corrija na mesma tarefa quando o problema: estiver na área tocada; não alterar contrato público;
não exigir migration; não mudar comportamento não solicitado; não atingir vários módulos; puder ser
validado; não expandir significativamente a entrega.

Exemplos: nome local confuso, função longa diretamente relacionada, import sem uso, código morto
comprovado, duplicação pequena, comentário incorreto, documentação da área alterada, teste faltante
para a mudança, hardcode que já possui token ou constante oficial, arquivo temporário indevidamente
versionado.

Faça a melhoria na mesma branch e na mesma PR da tarefa principal. Quando a melhoria for relevante,
use commit separado — mas não abra uma PR para cada achado (ver a regra de batching em
`docs/operations/PROCESSO_PR_E_AGENTES_2026-07-16.md`, item 2).

---

## 9. Quando abrir ou atualizar uma issue

Não executar silenciosamente quando o problema envolver: renomeação em massa, mudança de módulo,
mudança de package, alteração de contrato público, banco de dados ou migration, APIs, navegação
ampla, arquitetura, muitos consumidores, risco de regressão, arquivos históricos cuja remoção não
esteja comprovada, consolidação de motores concorrentes, mudança extensa de source set.

Antes de abrir uma issue, pesquise se já existe equivalente (`gh issue list --search`). Agrupe
problemas relacionados por domínio — não abrir uma issue para cada arquivo ruim.

A issue deve conter: contexto, evidências, arquivos e módulos afetados, comportamento atual, risco,
destino arquitetural, plano incremental, critérios de aceite, testes necessários, dependências,
estratégia de rollback quando aplicável. Seguir `abrir-issue` para nomenclatura e roteamento.

---

## 10. Documentação

### Estrutura real (validada em 2026-07-16, pós-consolidação)

```
docs/
├── README.md              (índice curto)
├── FUNCIONAL.md            (o que o app faz)
├── TECNICO.md               (como o app é construído/integrado)
├── DESIGN_SYSTEM.md         (tokens/componentes Android)
├── features/                (uma página por feature: negócio + técnico + mapa de código; INDEX.md é o ponto de entrada)
├── ARQUITETURA/
│   ├── README.md            (visão de sistema, dependências entre módulos)
│   └── MODULOS/              (um doc por módulo Gradle real — 22 arquivos)
├── CONTRATOS/
│   ├── openapi/               (contrato OpenAPI 3.0 — 5 arquivos, um por Worker Cloudflare)
│   └── schemas/                (índice de schemas reais: Room, D1, analytics — referencia a origem)
├── RELEASES.md
├── decisions/                  (ADRs e decisões de negócio — preservados, não regeneráveis)
├── design-system/            (histórico — conteúdo vigente em DESIGN_SYSTEM.md)
├── functional/                (specs pontuais que não migraram para FUNCIONAL.md)
├── legal/
├── operations/
├── technical/                  (docs pontuais que não migraram para TECNICO.md/ARQUITETURA/)
├── templates/
└── _archive/                   (vazia por decisão — só o README com instrução de recuperação)
```

Nota: assets de marca (`signallq-*.png`) vivem em `brand/` na raiz do repo, não em `docs/` — é a
fonte da verdade de logo/ícone/favicon, referenciada por build Android e Admin (ver `brand/README.md`).

A árvore `FUNCIONAL.md`/`TECNICO.md`/`ARQUITETURA/`/`CONTRATOS/`/`DESIGN_SYSTEM.md` é o alvo para
conteúdo funcional, técnico, arquitetural, de contrato e de design — não uma exigência de mover
tudo para dentro dela. `decisions/`, `functional/` (residual), `legal/`, `operations/`,
`technical/` (residual) e `templates/` continuam existindo para o que não se encaixa nessa árvore
(ADRs, planos pontuais, mapas de campo de equipamento, runbooks, termos legais). Ver
`docs/README.md` para o índice completo.

**Perímetro (atualizado 2026-08-06):** `docs/` documenta **apenas** o app consumer Android e o
backend Cloudflare. O painel Admin vive em `buildea-admin` e o site/PWA em `signallq-web` — a
documentação deles pertence aos repositórios deles, aqui só existe ponteiro. O worker
`signallq-admin-worker` é exceção: é deste repositório, embora o painel que o consome não seja.
O SignallQ Pro está descontinuado permanentemente (ADR-016) — módulos `:pro:*`, docs e skill de
design foram removidos do repositório nas Fases 4a-b do épico #1623.

**Sem pasta de arquivo.** Documento substituído é **removido**, não movido para `_archive/`. O git
é o arquivo — a pasta duplicava o histórico e poluía toda busca com versões antigas, que agentes e
humanos liam como verdade atual. Ver `docs/archive/README.md` para recuperar qualquer documento
removido.

`docs/README.md` deve funcionar como índice, não como uma segunda documentação completa.

### Páginas de feature (`docs/features/`)

Mudou o comportamento de uma feature (regra de produto, estado ou copy, flag, evento, módulo ou
arquivo do mapa de código)? **Atualize `docs/features/<slug>.md` na mesma PR.** A página é a
fonte por feature; `FUNCIONAL.md`, `ARQUITETURA/MODULOS/*` e `technical/*` guardam só o que é
transversal ou específico do módulo e apontam para ela. Não copie limiares: use `thresholds_em`.

O CI valida (`scripts/validar-docs.sh`): todo caminho de `modulos`, `arquivos`, `contratos`,
`testes` e `thresholds_em` existe; todo evento existe no código Kotlin ou em
`technical/analytics-events*.md`; toda flag existe no código ou no catálogo; `INDEX.md` e
`features.json` batem com o gerado (`scripts/gerar-features-index.sh`); todo módulo Gradle aparece
em alguma feature (aviso, ou falha com `FEATURES_COBERTURA_ESTRITA=1`); e avisa quando um `.kt`
listado mudou sem tocar a página. Formato: `docs/features/README.md`.

### Nomes

Para novos documentos, usar português, minúsculas e `kebab-case`, exceto nomes convencionais:
`README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `LICENSE`, `ADR-0001-titulo-da-decisao.md`.

Documentos existentes só devem ser renomeados quando todos os links e consumidores forem
atualizados no mesmo trabalho.

### Metadados mínimos

Todo documento ativo relevante deve informar: status, última validação, fonte de verdade, escopo,
responsável ou domínio, documentos substituídos (quando houver).

### Templates de documento (decisão 2026-07-23)

O projeto [SignallQ Design System](https://claude.ai/design/p/2d25d7a1-31b2-4ac3-881f-72dbc8f35a29)
(`templates/`) define a estrutura de seção obrigatória para os três tipos de documento vivo — a
estrutura de metadados acima (status/última validação/fonte de verdade/escopo/responsável) cobre o
mesmo papel do cabeçalho do template (produto/autor/status/revisores/versão) e continua sendo usada
como está, em vez do formato visual do template.

- **Especificação Funcional** (`FUNCIONAL.md`, `functional/*`): Objetivo → Contexto e problema →
  Personas e casos de uso → Histórias de usuário → Fluxo principal → Requisitos funcionais (`RF-NN`)
  → Requisitos não funcionais → Critérios de aceite → Fora de escopo → Métricas de sucesso.
- **Especificação Técnica** (`TECNICO.md`, `technical/*`): Objetivo técnico → Visão geral da solução
  → Modelo de dados → APIs/Endpoints → Integrações e dependências → Segurança e privacidade →
  Performance e escalabilidade → Rollout e observabilidade → Riscos técnicos.
- **Arquitetura** (`ARQUITETURA/README.md`, `ARQUITETURA/MODULOS/*`): Visão geral → Diagrama de
  componentes → Componentes em detalhe → Fluxo de dados principal → Decisões arquiteturais (ADR) →
  Riscos e mitigação.

Documento novo desses três tipos nasce com essa estrutura de seções. Documento existente é
migrado quando tocado (não é obrigatório revisar tudo de uma vez — ver princípio geral, seção 1).

### ADRs

Cada ADR deve possuir número único. Existe histórico de numeração duplicada — antes de criar ADR:
1. liste os ADRs existentes;
2. identifique o maior número válido;
3. use o próximo número;
4. não reutilize números;
5. atualize o índice;
6. preserve links ou redirecionamentos ao renomear documento antigo.

### Remoção (substitui a antiga regra de arquivamento — mudou em 2026-08-06)

**Remova** o documento quando ele: foi substituído; descreve produto descontinuado; registra
processo antigo; cita arquitetura que não existe mais; serve apenas como memória histórica.

Não mova para `_archive`. O git preserva tudo, e manter cópias antigas na árvore custa caro: até
2026-08-06 havia 100 documentos arquivados em `docs/archive/`, e toda busca por um fato técnico
devolvia a versão substituída junto com a vigente — agentes e humanos liam a antiga como verdade
atual. A pasta foi esvaziada e não volta a receber arquivos.

Ao remover:
1. registre a substituição no documento que substituiu (campo "documentos substituídos");
2. se havia links apontando para o removido, atualize-os ou remova-os na mesma mudança;
3. cite na mensagem de commit o SHA em que o arquivo ainda existia, para facilitar recuperação
   (`git show <sha>:<caminho>`).

Exceção: documento de **produto pausado** não é removido nem arquivado — fica onde está, com um
README de selagem declarando o congelamento, a data, o estado no congelamento e a condição de
retomada. Se o pausado virar descontinuado permanente, o README de selagem some junto com o resto
(modelo histórico: `docs/pro-onhold/`, removido em 2026-08-15 — `git show 0daa424a:docs_ai/pro-onhold/README.md`).

---

## 11. Limpeza do ambiente

Durante a tarefa, verificar apenas a área relacionada quanto a: arquivos temporários, logs, dumps,
APKs ou builds indevidamente versionados, caches, diretórios de ferramenta, secrets, credenciais,
arquivos duplicados, assets sem uso, scripts abandonados.

Antes de remover um arquivo: buscar referências; verificar build scripts; verificar CI; verificar
documentação; verificar uso por ferramentas e agentes; confirmar que não é mirror intencional.

Os mirrors `.agents/skills/` e `.github/skills/` (sincronização de skill para Codex e hooks do
GitHub) já têm regra própria documentada em `AGENTS.md`, seção "Skills locais e espelhos" — não
duplicar aqui, só aplicar: fonte canônica é `.claude/skills/`, nunca
editar o mirror direto, resincronizar com `scripts/sync-skills-mirrors.sh` após editar a skill
original (`--check` valida sem escrever).

---

## 12. Validação obrigatória

Após mudanças Kotlin ou Gradle, executar ao menos as validações dos módulos afetados. Para mudança
estrutural relevante, executar (a partir de `android/`, usando `gradlew.bat` no Windows):

```
./gradlew ktlintCheck
./gradlew detekt
./gradlew test
./gradlew assembleDebug
```

Também executar `git diff --check` e `git status` antes de commitar.

Após renomear ou mover arquivos: buscar referências ao nome antigo; conferir imports; conferir
packages; conferir testes; conferir scripts; conferir documentação; conferir CI; verificar que não
existem duas implementações ativas por acidente.

A regra de nunca declarar merge, teste, build ou publicação como concluídos sem verificação real já
está registrada em `../ai-governance/policies/agent-operating-contract.md`, seção 5 item 5 ("não
alegar teste executado, funcionalidade comprovada ou maturidade sem evidência verificável") e seção
9 (critério comum de conclusão). Na prática: `gh pr view <N> --json state,merged`, `gh pr checks
<N>`, ou requisição direta contra o ambiente — nunca por inferência nem por relato de outro agente.
Esta seção só acrescenta os comandos técnicos específicos de Kotlin/Gradle acima.

---

## 13. Formato obrigatório da entrega

Ao concluir qualquer tarefa, apresentar:

**Entrega principal** — o que foi realizado para atender à solicitação original.

**Melhorias incrementais realizadas** — lista objetiva das melhorias de higiene realmente
executadas.

**Dívidas encontradas e não resolvidas** — problema; impacto; arquivos ou módulos; motivo de não
corrigir agora; issue criada ou atualizada, quando aplicável.

**Arquivos renomeados ou movidos** — formato `caminho antigo` → `caminho novo`.

**Validações executadas** — comandos e resultados reais.

**Pendências ou falhas** — não esconder validações que falharam.

---

## 14. Regra de decisão

Ao encontrar um problema, responda internamente:

1. Está diretamente relacionado à área tocada?
2. É pequeno ou médio?
3. O comportamento será preservado?
4. Não altera contratos, banco ou navegação ampla?
5. Pode ser validado objetivamente?
6. Não exige muitos arquivos ou módulos?
7. Não cria outra abstração genérica?
8. Não desvia da entrega principal?

Se todas as respostas forem "sim", corrija na mesma tarefa. Se alguma resposta for "não", registre
ou atualize uma issue e prossiga com a entrega original.
