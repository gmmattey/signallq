---
title: "Jornada Android guiada — SignallQ 2.0"
description: "Princípios de produto da jornada guiada (Início → análise → conclusão → próximo passo → confirmação), já implementada na v1.0.x; comportamento atual em FUNCIONAL.md."
type: "funcional"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Jornada Android guiada — SignallQ 2.0

## 1. Status

A jornada proposta aqui foi implementada pelo épico #1647 (encerrado) e está na v1.0.9. **Este documento
guarda só os princípios de produto.** O comportamento real, tela a tela, está em
[`../FUNCIONAL.md`](../01-funcional.md); em divergência, vale o código. A versão integral anterior (arquitetura de
navegação proposta, telas, estados, questões para protótipo) está em
`git show f6f9d437:docs_ai/functional/JORNADA_ANDROID_GUIADA_2_SPEC.md`; ela descrevia o app antigo
(aberto em Velocidade, cinco abas) e não vale como estado atual. O spec do fluxo guiado/Modo gamer
(`DIAGNOSTICO_GUIADO_MODO_GAMER_SPEC.md`, rascunho de #550) foi removido: o fluxo foi entregue e vive em
`FUNCIONAL.md` §5.5b/§5.7/§5.11.

> **Problema → análise adequada → conclusão simples → próximo passo → confirmação.**

## 2. Princípios (decisão de produto)

1. **A unidade principal não é a ferramenta.** A jornada começa pelo que a pessoa percebe ("está lenta",
   "o vídeo trava") ou pelo desejo de verificar a conexão, não por Speed Test, Ping, DNS ou Sinal.
   Ferramentas são capacidades convocadas conforme sintoma, tipo de conexão, permissões, validade dos
   dados e limitações do aparelho ou da rede.
2. **O app abre em Início**, com o estado conhecido da conexão e o CTA **Analisar minha conexão**.
3. **Dois caminhos usando os mesmos motores:** análise guiada (principal, público não técnico) e Ferramentas
   (secundário). Ferramentas não mantêm lógica diagnóstica paralela; não existe segundo catálogo de
   sintomas nem motor concorrente.
4. Perguntas só quando alteram diagnóstico, recomendação ou confiança.
5. Toda análise termina em conclusão compreensível ou declaração honesta de insuficiência, com próximo passo
   concreto e possibilidade de repetir e comparar depois da ação.
6. Métricas continuam acessíveis em detalhes; a camada principal evita grid de cards, excesso de pills e
   múltiplos CTAs primários.
7. Permissão negada, offline, parcial, contaminado e inconclusivo têm continuidade útil.

## 3. Estado de implementação (conferido em 2026-10-04)

| Princípio | Estado |
|---|---|
| Abre em Início; CTA "Analisar minha conexão" | Implementado (`FUNCIONAL.md` §4.1, §4.3.2) |
| Quatro raízes: Início, Velocidade, Histórico, Ferramentas | Implementado |
| Análise guiada por objetivos fechados (SignallQ Assist, resultado do NDS) | Implementado |
| Ferramentas como área secundária | Implementado |
| Demais critérios (telemetria do funil, comparação antes/depois, movimento Material 3) | Não reverificados nesta auditoria |

## 4. Fontes relacionadas

- [`../POSICIONAMENTO_PRODUTO.md`](../POSICIONAMENTO_PRODUTO.md)
- [`../design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md`](../design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md)
- [`../FUNCIONAL.md`](../01-funcional.md)
