---
name: regras-diagnostico-rede
description: Centraliza thresholds de diagnóstico de rede, padrões técnicos brasileiros (ANATEL), topologia doméstica (CGNAT, duplo-NAT), Wi-Fi, Fibra e redes móveis (4G/5G). Consultar antes de implementar qualquer engine de diagnóstico.
---

## Quando usar
Antes de implementar ou revisar thresholds de sinal, análise de velocidade, detecção de topologia, qualidade celular ou qualquer engine de diagnóstico de rede. Confirme sempre que a regra ainda corresponde ao código/testes e à referência técnica aplicável antes de alterá-la.

## Thresholds Wi-Fi (RSSI em dBm)

| Qualidade   | 2.4GHz     | 5GHz       |
|-------------|------------|------------|
| Excelente   | > -50      | > -55      |
| Boa         | -50 a -60  | -55 a -65  |
| Aceitável   | -60 a -70  | -65 a -75  |
| Ruim        | -70 a -80  | -75 a -82  |
| Inutilizável| < -80      | < -82      |

## Thresholds de qualidade de conexão (Brasil)

| Métrica          | Excelente | Bom      | Aceitável | Ruim   |
|------------------|-----------|----------|-----------|--------|
| Latência SP/RJ   | < 15ms    | 15-30ms  | 30-60ms   | > 60ms |
| Latência internt | < 100ms   | 100-150ms| 150-200ms | >200ms |
| Jitter           | < 5ms     | 5-10ms   | 10-20ms   | > 20ms |
| Perda de pacotes | 0%        | < 0.5%   | 0.5-2%    | > 2%   |

## Thresholds 4G LTE

| Métrica | Excelente | Bom       | Aceitável  | Ruim     |
|---------|-----------|-----------|------------|----------|
| RSRP    | > -80 dBm | -80 a -90 | -90 a -100 | < -100   |
| RSRQ    | > -10 dB  | -10 a -15 | -15 a -20  | < -20    |
| SINR    | > 20 dB   | 13-20     | 0-13       | < 0      |

## Thresholds 5G NR

| Métrica | Excelente | Bom       | Aceitável  | Ruim    |
|---------|-----------|-----------|------------|---------|
| RSRP    | > -80 dBm | -80 a -95 | -95 a -110 | < -110  |
| SINR    | > 20 dB   | 10-20     | 0-10       | < 0     |

## Topologia doméstica

- **CGNAT**: faixa 100.64.0.0/10 (RFC 6598). IP WAN com 10.x.x.x, 172.16-31.x.x ou 100.64-127.x.x indica CGNAT. Impacto: sem port forwarding real, P2P limitado, NAT Type Strict em jogos.
- **Duplo-NAT**: fibra em modo roteador com IP privado na WAN. Causa: ISP não configurou modo bridge. Diagnosticar pelo IP WAN do roteador.
- **Bridge vs. Roteador**: ISPs brasileiros (Vivo, Claro) frequentemente entregam em modo roteador. Duplo-NAT é o cenário mais comum.

## Fibra FTTH

- GPON (G.984): downstream 2.488 Gbps / upstream 1.25 Gbps.
- XGS-PON (G.9807): 10 Gbps simétrico.
- Potência óptica ONU RX típica: -8 a -27 dBm. Abaixo de -27 dBm = alarme.
- ONU/ONTs comuns no Brasil: Intelbras, TP-Link, Multilaser, Huawei, ZTE.

## 5G no Brasil

- **NSA (Non-Standalone)**: âncora LTE. Maioria da cobertura 5G atual. Latência e throughput ainda dependem do LTE anchor band.
- **SA (Standalone)**: latência < 5ms, núcleo 5G nativo. Implementação inicial em capitais (2024-2025).
- Diagnóstico DEVE diferenciar NSA de SA — throughput e latência esperados são diferentes.

## Regulamentação ANATEL
- Resolução 614/2013: referência para qualidade de banda larga fixa.
- Ato 7869/2022: qualidade de redes móveis.
- DFS obrigatório em canais UNII-2 do 5GHz.

## Limites
- Esta skill orienta regras e thresholds; não define quem implementa.
- Camillo responde pela coerência do domínio e implementa as partes Android; Renan exige teste de regressão para mudança de regra.
- Se a alteração cruza módulos, Worker/API ou contrato compartilhado, aplique o gate do Camillo.
- Se o comportamento for variável por ISP, declarar a incerteza e recomendar validação em campo.

> Renomeado de `network-diagnostic-rules` em 2026-06-21. Conteúdo equivalente.
