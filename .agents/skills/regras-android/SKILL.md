---
name: regras-android
description: Regras de plataforma Android (API levels, APIs deprecated, OEM quirks, restrições Play Store) e checklist de permissões do SignallQ. Consultar antes de implementar permissões, Wi-Fi, DNS, background service ou conectividade.
---

Use esta skill para validar comportamento de plataforma Android antes de implementar ou revisar código sensível a API level, permissão, lifecycle, OEM ou execução em background. O roteamento entre Marcelo, Priscila, Camillo e Renan é definido em `AGENTS.md`.

Consulte também `oem-quirks-e-testes-device.md` quando comportamento real puder divergir da documentação oficial.

---

## API Levels suportados pelo SignallQ

Confirme os valores atuais em `android/gradle/libs.versions.toml` antes de tomar decisão. Não trate números documentados aqui como mais autoritativos que a configuração real do build.

---

## Permissões

### Restrições por versão

| Permissão | Mudança | API |
|---|---|---|
| ACCESS_FINE_LOCATION | Obrigatória para vários dados/scans Wi-Fi em versões modernas | 26+ |
| ACCESS_BACKGROUND_LOCATION | Permissão separada para localização em background | 29+ |
| FOREGROUND_SERVICE | Declaração obrigatória no manifest | 28+ |
| FOREGROUND_SERVICE_CONNECTED_DEVICE | Tipo específico quando aplicável | 34+ |
| READ_PHONE_STATE | Runtime permission para APIs protegidas de telefonia | 26+ |
| SCHEDULE_EXACT_ALARM | Acesso especial para alarmes exatos | 31+ |

Sempre confirme a regra na documentação oficial da versão alvo antes de adicionar permissão nova.

### Checklist obrigatório

**Localização / Wi-Fi**
- [ ] A permissão é realmente necessária para o dado usado?
- [ ] Solicitação ocorre no contexto da ação, não por hábito no cold start?
- [ ] Existe fallback quando negada?
- [ ] Rationale e recuperação em Ajustes fazem sentido para a versão alvo?

**Foreground service**
- [ ] O tipo de serviço é compatível com o comportamento real?
- [ ] A notificação obrigatória é apresentada quando exigida?
- [ ] O serviço é encerrado/cancelado corretamente?

**Telefonia**
- [ ] A informação pode ser obtida sem permissão sensível?
- [ ] O app degrada corretamente quando o dado não está disponível?
- [ ] A justificativa de Play Store/privacidade continua verdadeira?

**Geral**
- [ ] Manifest e runtime estão coerentes?
- [ ] O comportamento foi verificado para min/target SDK atuais?
- [ ] OEM quirks relevantes foram considerados?

---

## Wi-Fi

Antes de usar `WifiInfo`, scan results, `NetworkCapabilities` ou `NetworkCallback`:

- valide a API recomendada na versão alvo;
- considere restrições de localização e throttling de scan;
- não assuma que SSID/BSSID estarão disponíveis;
- trate mudança de rede e informação desatualizada;
- teste em device real quando a feature depender do dado.

**OEM quirks conhecidos devem ser tratados como evidência histórica, não regra universal.** Confirme no arquivo de quirks e reproduza quando possível.

---

## DNS

- `LinkProperties.getDnsServers()` é a fonte do link ativo quando aplicável;
- DNS privado varia por versão/configuração e pode não expor hostname em todos os modos;
- resolução bloqueante nunca deve rodar na Main thread;
- timeout/falha de resolução não pode virar sucesso ou DNS “zero”;
- Wi-Fi e móvel podem ter propriedades diferentes: use a network correta.

---

## Background / Doze / WorkManager

Considere:

- Doze e App Standby;
- Battery Saver;
- restrições de foreground service;
- limites de rede em background;
- periodicidade mínima e políticas do WorkManager;
- encerramento/reagendamento após reboot quando aplicável;
- comportamento OEM agressivo.

Se o comportamento precisa funcionar “sempre”, prove isso nas restrições reais da plataforma antes de prometer no produto.

---

## ConnectivityManager

- prefira callbacks/eventos a polling contínuo;
- diferencie transporte conectado de internet realmente validada;
- consulte `LinkProperties` da network relevante;
- registre/desregistre callbacks no lifecycle correto;
- trate troca Wi-Fi ↔ móvel sem manter estado antigo como atual.

---

## Play Store e privacidade

Permissões sensíveis, foreground services, localização em background, identificadores e acesso especial do sistema podem exigir declaração no Play Console e mudança de política pública.

Se a tarefa altera dado coletado, finalidade ou permissão sensível, envolva Marcelo e Renan. Se houver impacto sistêmico, contrato ou nova integração, aplique também o gate do Camillo.

---

## Compose lifecycle

| Pitfall | Problema | Direção |
|---|---|---|
| `LaunchedEffect` com key incorreta | reexecução ou estado antigo | escolher key pela identidade real do efeito |
| coroutine longa em escopo de UI inadequado | cancelamento/lifecycle incorreto | usar o owner correto da operação |
| Flow coletado sem lifecycle | trabalho fora da tela | usar APIs lifecycle-aware |
| side effect durante composição | repetição em recomposição | mover para efeito explícito |

---

## Regra de consulta

Antes de implementar feature sensível à plataforma:

1. confirme o comportamento no código/configuração atual;
2. confirme API level e restrição oficial;
3. consulte quirks quando relevante;
4. defina fallback para dado indisponível/permissão negada;
5. planeje teste em device real quando simulador/unit test não provar o caso.

Documentação oficial e comportamento OEM podem divergir. Quando houver incerteza, declare e teste — não invente certeza.

## Limites

Esta skill orienta a análise de plataforma. Camillo normalmente implementa Android e responde pelo dado que alimenta o diagnóstico; Renan valida; o gate arquitetural do Camillo entra somente quando do `AGENTS.md` for acionado.
