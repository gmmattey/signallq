---
name: motor-diagnostico
description: Procedimento para analisar e alterar diagnóstico, speedtest, Wi-Fi/DNS e IA do SignallQ sem duplicar engine, threshold ou fonte de verdade.
---

# Motor de diagnóstico

Use em mudanças que afetem diagnóstico, speedtest, Wi-Fi, DNS, latência, jitter, perda, equipamentos ou IA de diagnóstico.

A skill orienta o procedimento. O roteamento de agentes vem do `AGENTS.md`.

Contexto por feature (regras de decisão, estados, mapa de código e `thresholds_em`): `docs/features/INDEX.md` — em especial `assist-diagnostico`, `dns-ping`, `velocidade-resultado` e `wifi-canais-sinal`. Mudou comportamento, atualize a página.

## 1. Antes de propor mudança

Localize no código:

- modelos de entrada e saída;
- engine/orchestrator/use case/classifier existentes;
- thresholds e regras determinísticas atuais;
- persistência relacionada;
- Worker/API/contrato consumido;
- testes que caracterizam o comportamento.

Antes de criar engine nova, faça inventário em módulos relacionados. Se uma implementação existente cobre a maior parte do caso, prefira estender com responsabilidade clara.

## 2. Três camadas que não podem se misturar

Todo diagnóstico deve distinguir:

1. **fato medido/coletado**;
2. **inferência determinística reproduzível**;
3. **interpretação de IA**.

Regras:

- IA não substitui regra determinística confiável;
- ausência de dado não é zero;
- timeout/erro não é sucesso;
- causa raiz exige evidência suficiente;
- threshold tem fonte canônica única;
- resposta ao usuário deve ser sustentada pelas evidências disponíveis;
- quando a evidência é insuficiente, declare incerteza ou peça contexto em vez de adivinhar.

Para thresholds e padrões técnicos, consulte `regras-diagnostico-rede` em vez de duplicar valores nesta skill.

## 3. Gatilhos Android

Consulte `regras-android` quando envolver:

- DNS real/private DNS;
- Wi-Fi scan, RSSI, frequência ou NetworkCapabilities;
- ConnectivityManager/NetworkCallback;
- permissão Android;
- background/Doze;
- dado que varia por API level/OEM.

## 4. Gate arquitetural

Se a mudança atravessar módulos com alteração de responsabilidade, criar/alterar API, ligar app↔Worker, mudar contrato compartilhado, migrar persistência ou alterar estruturalmente o motor central, o gate de Camillo é obrigatório antes da implementação.

Use `.agents/architecture-plan.md` para registrar a decisão.

## 5. Speedtest

Ao tocar o fluxo de speedtest:

- preserve execução fora da Main thread;
- trate cancelamento, timeout e perda de conectividade;
- não reporte amostra incompleta como medição concluída;
- diferencie valor zero real de fase não medida;
- mantenha estado de UI coerente com o estado real do motor;
- persista apenas o que o contrato atual considerar resultado válido;
- adicione teste de regressão quando mudar cálculo, estado ou classificação.

Não fixe nesta skill uma sequência de classes se o código atual tiver evoluído; confirme a arquitetura real antes.

## 6. IA de diagnóstico

Antes de alterar prompt/contexto/Worker:

- liste todas as evidências enviadas;
- identifique quais já foram classificadas deterministicamente;
- confirme o contrato de entrada/saída;
- preserve fallback quando IA estiver indisponível;
- impeça que a resposta contradiga fatos medidos ou regras determinísticas;
- não exponha segredo no app.

## Saída esperada

```text
Impacto no fluxo:
Implementação existente encontrada:
Regras/thresholds relevantes:
Gate Camillo: SIM/NÃO — motivo
Plano de alteração:
Riscos de regressão:
Testes necessários:
Evidências/limitações:
```

Esta skill não faz deploy e não autoriza ampliar o diagnóstico além do comportamento definido por produto.
