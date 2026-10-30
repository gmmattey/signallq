---
title: "Voz do produto"
description: "Tom, vocabulário e regras de copy do SignallQ: como o texto da tela fala de medição, indício, causa e próximo passo."
type: "funcional"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-30"
version: "1.0.0"
---

# Voz do produto

- **Fonte de verdade:** os "Princípios de linguagem" de [`POSICIONAMENTO_PRODUTO.md`](POSICIONAMENTO_PRODUTO.md). Este documento aplica esses princípios à copy de interface, mensagem de erro e texto público e não os redefine.
- **Escopo:** textos do app (Android e Windows), do site e da loja, em PT-BR. Filtro final de qualquer texto: skill `matar-cheiro-de-ia`.

## Como o SignallQ fala

Para quem quer saber o que está errado com a internet e o que fazer: direto, calmo e preciso. A conclusão útil vem primeiro; o detalhe técnico fica disponível, mas nunca é pré-requisito. Cada texto diz o que foi observado, o que isso pode significar e o que fazer em seguida.

## Medido, provável e inconclusivo

A copy nunca mistura as três camadas de [`AGENTS.md`](../AGENTS.md):

| Camada | Como soa | Exemplo |
| --- | --- | --- |
| Dado medido | Afirma o fato | "Tempo de resposta (ping): 84 ms." |
| Inferência determinística | Afirma com a regra à vista | "O sinal está fraco neste canto da casa." |
| Interpretação de IA ou hipótese | Marca como provável | "Encontramos um possível problema no roteador." |
| Sem evidência suficiente | Admite | "Ainda não temos dados suficientes para afirmar a causa." |

Quando faltar medição, a tela pede o teste que confirma: "Faça este teste para confirmar."

## Regras

- Palavra do cotidiano antes do termo técnico; termo técnico traduzido no mesmo lugar: **tempo de resposta (ping)**, **variação da conexão (jitter)**, **atraso quando a rede fica ocupada (bufferbloat)**.
- Nunca prometa "internet mais rápida", "problema resolvido", "garantido" ou desempenho futuro.
- Nunca culpe provedor, roteador ou a pessoa sem evidência que isole o ponto.
- Erro, timeout e dado ausente têm texto próprio. Nunca aparecem como `0`, `--` sem explicação ou sucesso.
- Termine com uma ação concreta e verificável sempre que possível, e deixe a pessoa conferir depois se funcionou.
- Sem alarmismo, sem marketing, sem tom de assistente genérico.
- Cada tela responde uma pergunta que a pessoa faria numa frase curta. Se precisa de legenda didática para fazer sentido, a tela está errada.

## Proibido

Abertura e fechamento de robô ("Claro!", "Espero que ajude!"), vocabulário de release note (robusto, poderoso, seamless, intuitivo, escalável), corporativês e a muleta "Não é X. É Y.". A lista completa está na skill `matar-cheiro-de-ia`.

## Onde cada texto vive

Strings do app em `android/app/src/main/res/values/strings.xml`; copy de cada feature em [`features/`](features/INDEX.md); loja em [`operations/PLAY_STORE_LISTING.md`](operations/PLAY_STORE_LISTING.md). Mudou a copy de uma feature, atualize a página dela na mesma PR.
