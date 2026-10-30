---
title: "Feature — Ajustes, perfil, privacidade e termos"
description: "Ajustes (perfil, minha conexão, aparência, dados locais), Privacidade, Termos, Novidades, onboarding de 1 tela e consentimento LGPD/anúncios: regras, estados, mapa de código, eventos, flags e testes."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
feature: "perfil-ajustes-legal"
tipo: "transversal"
modulos:
  - "android/feature/settings"
  - "android/core/datastore"
  - "android/core/featureflags"
  - "android/app"
arquivos:
  - "android/feature/settings/src/main/kotlin/io/signallq/app/feature/settings/"
  - "android/app/src/main/kotlin/io/signallq/app/RotaInicialApp.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/component/LgpdConsentDialog.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ads/ConsentManager.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AjustesScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AjustesUiState.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/PerfilEditSheet.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/PerfilScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/MinhaConexaoScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/DadosLocaisSheet.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AcaoDadosLocaisEstado.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/OnboardingScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/PrivacidadeScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/TermosDeUsoScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/NovidadesScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AjudaSuporteContent.kt"
  - "android/app/src/main/kotlin/io/signallq/app/DecisaoPermissaoContextual.kt"
  - "android/core/datastore/src/main/kotlin/io/signallq/app/core/datastore/PreferenciasAppRepository.kt"
contratos: []
eventos:
  - "screen_view (privacidade/termos/ajustes: nome do screen_name não verificado)"
  - "feature_blocked_remote (feature_id=settings)"
flags:
  - "consumer_settings_enabled"
testes:
  - "android/feature/settings/src/test/kotlin/io/signallq/app/feature/settings/"
  - "android/app/src/test/kotlin/io/signallq/app/RotaInicialAppTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/PermissaoContextualTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/AjustesUiStateTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/AcaoDadosLocaisEstadoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DadosLocaisSheetResumoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/PrivacidadeOpcoesAnunciosTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/PerfilScreenTest.kt"
adrs: []
thresholds_em: "android/feature/settings/src/main/kotlin/io/signallq/app/feature/settings/ValidadorVelocidadeContratada.kt"
---

# Feature — Ajustes, perfil, privacidade e termos

## Negócio

### 1. Problema e promessa

O usuário precisa ajustar o app à sua realidade (nome, operadora e plano contratado, tema), entender e controlar o que o app faz com seus dados e aceitar os termos antes de usar. Promessa: **nenhuma conta, nenhum login**; o perfil é local. Privacidade e Termos são obrigação legal e nunca ficam escondidos. Texto jurídico completo: `docs/legal/PRIVACY_POLICY.md` e `docs/legal/TERMS_OF_USE.md` (fonte; esta página não os copia). A `PrivacidadeScreen` e a `TermosDeUsoScreen` espelham esses textos no app.

### 2. Quando aparece e para quem

- **Onboarding** (1 tela, `OnboardingScreen`): só na primeira abertura. Boas-vindas com checkbox de aceite dos Termos de Uso e da Política de Privacidade; o botão "Começar" só habilita após o aceite (único bloqueio do fluxo). Os dois documentos abrem como overlay interno.
- **Consentimento LGPD** (`LgpdConsentDialog`): depois do onboarding, enquanto não houver resposta.
- **Ajustes** (`AjustesScreen`): overlay aberto pela ação de perfil da app bar das quatro raízes; não é raiz de navegação.
- **Privacidade, Termos, Novidades, Ajuda, Sobre**: destinos internos de Ajustes (Privacidade e Termos também pelo Perfil).
- Para qualquer usuário; sem permissão de runtime.

### 3. Regras de decisão

- **Ordem antes do shell** (`rotaInicialApp`, `RotaInicialApp.kt`): DataStore ainda sem resposta → tela vazia (evita o onboarding "piscar"); onboarding não concluído → `OnboardingScreen`; sem resposta de LGPD → `LgpdConsentDialog`; senão `AppShell`. Usuário existente nunca vê o onboarding de novo.
- **Analytics nasce desligado** (`SignallQApplication.kt`): a coleta só liga com consentimento LGPD positivo (`consentimentoLgpdFlow == true`).
- **Anúncios (UMP)**: a entrada "Preferências de anúncios" (GH#1703) reabre o formulário do Google User Messaging Platform, e só aparece onde a plataforma exige (`privacyOptionsRequirementStatus == REQUIRED`, regiões sob GDPR). É obrigação da plataforma, não preferência de produto. O LGPD do app e o consentimento de anúncios são independentes (`ConsentManager.kt`).
- **Permissões contextuais**: nenhuma é pedida no onboarding. Cada uma é solicitada ao entrar na funcionalidade que a usa (localização na aba Wi-Fi/Canal, telefonia na aba Móvel, notificação ao ligar o monitoramento); a decisão pura está em `DecisaoPermissaoContextual.kt`. Tabela completa de permissões: `FUNCIONAL.md` §6.
- **Perfil de conexão é por rede**, não global (`ConnectionProfile`). Provedor detectado diferente do salvo: se o usuário já confirmou o valor, mostra "Detectamos {provedor} nesta rede. / Usar este provedor?"; sem confirmação prévia, atualiza em silêncio (`DetectorDivergenciaPerfilConexao`).
- **Validação**: velocidade contratada e cidade/UF passam por `ValidadorVelocidadeContratada` e `ValidadorCidadeUf` (limites no próprio arquivo, ver `thresholds_em`; a validação de correspondência cidade↔UF real não existe, só as 27 siglas).
- **Validadores** (`feature/settings`): velocidade `null` é válida (campo vazio explícito), `0` e negativos não; cidade/UF: ambos vazios é válido, só um preenchido não, e a UF precisa estar nas 27 siglas (`UFS_VALIDAS`). `ThemePreference.parse` nunca lança e cai em `SYSTEM`. `ResolvedorNetworkId` foi promovido a `:coreDatabase` na #1707.
- **Ajustes — seis seções**: Perfil (nome, `PerfilEditSheet`; sem foto), Minha conexão (operadora, plano down/up, cidade/UF, todos abrem a mesma sheet), Aparência (Sistema/Claro/Escuro), Notificações (limite mínimo de download para alertas de qualidade), Dados e privacidade (`PrivacidadeScreen`, `DadosLocaisSheet`), Sobre (Novidades e versão).
- **Ajuda** tenta `mailto:` para o suporte; endereço e copiar ficam como fallback quando não há handler.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| DataStore ainda não respondeu | Tela vazia (nunca onboarding por engano) |
| Termos não aceitos no onboarding | "Começar" desabilitado |
| LGPD recusada | Analytics permanece desligado; o app continua utilizável |
| Região sem exigência UMP | Entrada "Preferências de anúncios" oculta |
| Ação de dados locais em andamento | Progresso nos botões, sheet aberto (`AcaoDadosLocaisEstado`: Ocioso/EmAndamento/Sucesso/Falha) |
| Ação de dados locais falha | Erro exibido, sheet não fecha; **nunca** apresenta falha como sucesso (#1670) |
| Apagar dados locais | Texto esclarece que **não remove dados já enviados a servidores do SignallQ** (ex.: Diagnóstico por IA, compartilhar resultado); decisão do Luiz, 2026-08-19: só explica, sem segundo caminho de pedido de remoção |
| Sem cliente de e-mail | Endereço e copiar como fallback |
| Flag `consumer_settings_enabled` desligada | Ajustes não abre: snackbar "Recurso temporariamente indisponível."; Privacidade e Termos continuam acessíveis |

### 5. Próximo passo e confirmação

Cada ação de Ajustes confirma na própria tela. As três ações destrutivas de `DadosLocaisSheet` — limpar histórico de testes, apagar dados locais e resetar o app — pedem **diálogo de confirmação**, escalonadas por gravidade. O estado do app é recarregado do DataStore após a ação.

### 6. Fora de escopo, status e flag

Entregue. Sem conta, autenticação, foto ou avatar remoto. Consentimento AdMob e exclusão/reset ficam nas superfícies atuais e não foram redesenhados. Flag `consumer_settings_enabled` (fail-open, default `true`, criticidade MEDIUM) gateia só Ajustes e o worker de monitoramento em segundo plano; **não** cobre Privacidade nem Termos (obrigação legal).

**Divergência real no código:** `AjustesScreen` recebe os estados de monitoramento e de dados móveis (permitir teste pesado em rede móvel, MB no mês), mas **não renderiza linha para eles**; monitoramento só se configura pelo hub Ferramentas e a preferência de dados móveis não tem ponto de entrada na UI hoje (`AjustesScreen.kt`).

## Técnico

### 7. Mapa de código

| Arquivo | Responsabilidade |
|---|---|
| `feature/settings/.../ConnectionProfile.kt` | Perfil vinculado a uma rede (`networkId`, provedor, plano down/up, cidade/UF, `userConfirmed`) |
| `feature/settings/.../DetectorDivergenciaPerfilConexao.kt` | Classificação tipada da divergência de perfil (4 casos) |
| `feature/settings/.../ValidadorVelocidadeContratada.kt`, `ValidadorCidadeUf.kt` | Validadores puros |
| `feature/settings/.../ThemePreference.kt` | `SYSTEM`/`LIGHT`/`DARK`; `parse` nunca lança e cai em `SYSTEM`, preservando strings já gravadas |
| `feature/settings/.../FeatureSettingsModulo.kt` | `object` vazio (ver riscos) |
| `app/.../RotaInicialApp.kt`, `MainActivity.kt` | `rotaInicialApp`, onboarding/LGPD antes do shell |
| `app/.../ui/screen/OnboardingScreen.kt`, `ui/component/LgpdConsentDialog.kt` | Onboarding de 1 tela e diálogo LGPD |
| `app/.../ui/screen/AjustesScreen.kt`, `AjustesUiState.kt`, `PerfilEditSheet.kt`, `PerfilScreen.kt`, `MinhaConexaoScreen.kt` | Tela e sheets de Ajustes; `MinhaConexaoScreen.kt` existe mas não é roteada, é consumida como bottom sheet dentro de Ajustes |
| `app/.../ui/screen/DadosLocaisSheet.kt`, `AcaoDadosLocaisEstado.kt` | Ações destrutivas e seu estado observável (`MainViewModel.dadosLocaisAcaoEstado`) |
| `app/.../ui/screen/PrivacidadeScreen.kt`, `TermosDeUsoScreen.kt`, `NovidadesScreen.kt`, `AjudaSuporteContent.kt` | Destinos internos; Termos espelha `docs/legal/TERMS_OF_USE.md` |
| `app/.../ui/screen/AppShellPrivacidadeOverlay.kt`, `AppShellTermosOverlay.kt`, `AppShellNovidadesOverlay.kt` | Overlays |
| `app/.../ads/` (`AdSlot`, `AdUnitIds`, `ConsentManager`, `AdsRemoteConfigRepository`) | Anúncios nativos AdMob: IDs reais ou de teste conforme `-PplayTrack`; `ConsentManager` faz a coleta e reabertura do consentimento UMP |
| `app/.../DecisaoPermissaoContextual.kt` | Decisão pura de pedido de permissão contextual |
| `core/datastore/.../PreferenciasAppRepository.kt` | Persistência de onboarding, LGPD, tema, perfil |

Detalhe do módulo: [`feature-settings.md`](../modulos/feature-settings.md).

### 8. Dados e contratos

Sem contrato OpenAPI próprio. Dados locais em DataStore (`consentimentoLgpdFlow`, onboarding concluído, tema, perfil de conexão por rede) e Room (histórico, apagado pelas ações de `DadosLocaisSheet`). Credenciais do modem em `CredenciaisModemStore` pertencem à feature `equipamento-internet`. `feature:settings` depende só de `:coreDatabase` (para `ResolvedorNetworkId`).

### 9. Eventos e flags

- `feature_blocked_remote` (`feature_id=settings`) quando a flag bloqueia a rota (`ConsumerFeatureGateCoordinator.kt`).
- Nome do `screen_view` de Ajustes, Privacidade, Termos e Novidades: **não verificado** nesta página. Ver `technical/analytics-events-schema.md`.
- Flag: `consumer_settings_enabled` (Remote Config, `disabledBehavior: HIDE_ENTRY_AND_BLOCK_ROUTE`). Detalhe: `technical/feature-flags-remote-config.md`.
- Analytics só dispara depois do aceite LGPD (`SignallQApplication.kt`).

### 10. Falhas e fallback

- Falha ao apagar/limpar/resetar dados: estado `Falha`, erro visível, sheet aberto.
- Ausência de handler de `mailto:`: endereço e cópia.
- UMP indisponível ou sem exigência: entrada oculta.
- DataStore sem resposta: tela vazia até responder.

### 11. Testes

Lista em `testes:`. Cobertura de lógica pura em `feature/settings` (detector de divergência, validadores, tema); `RotaInicialAppTest` e `PermissaoContextualTest` cobrem a decisão de rota e de permissão. Lacuna: sem teste dedicado de `OnboardingScreen`, `TermosDeUsoScreen` e `LgpdConsentDialog` (não verificado se coberto por outro teste); `ConnectionProfile` e `FeatureSettingsModulo` não têm teste (declarações sem comportamento).

### 12. Riscos

- `AjustesScreen.kt` ainda tem múltiplos fluxos; extrair sheets ao tocar (`higiene` §4.4, 771 linhas na última auditoria; não reconferido aqui).
- `FeatureSettingsModulo` é `object` vazio e o módulo não tem UI nem ViewModel; a tela vive em `:app`.
- Estado de monitoramento e dados móveis é injetado em `AjustesScreen` sem ponto de entrada: código morto ou funcionalidade perdida.
- Texto jurídico duplicado: `TermosDeUsoScreen.kt` reproduz `TERMS_OF_USE.md` à mão; versão e data podem divergir. A Política de Privacidade consta como `2.1.0-rascunho`; conferir antes de afirmar que a versão no app é a vigente.
- Nomes `screen_view` não verificados.
