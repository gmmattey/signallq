---
title: "Posicionamento de produto — SignallQ"
description: "Diretriz canônica de posicionamento para produto, design, conteúdo e implementação do SignallQ Android e Web/PWA."
type: "funcional"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.1"
---

# Posicionamento de produto — SignallQ

## Diretriz canônica

O SignallQ **não é mais um teste de velocidade**. É um produto de diagnóstico de conectividade que
analisa a rede, explica o que está acontecendo em linguagem amigável e orienta a pessoa sobre o que
fazer em seguida.

O teste de velocidade continua existindo, mas é uma das fontes de evidência do diagnóstico — não é
a categoria, a promessa principal nem o fim da jornada.

> **O SignallQ analisa sua conexão, explica o problema em linguagem simples e mostra o que fazer
> para melhorar.**

Esta diretriz vale para as duas superfícies do produto:

- **Android:** aplicativo nativo deste repositório;
- **Web/PWA:** `signallq.com`, implementado no repositório `signallq-web`.

## Problema que o produto resolve

Quando a internet está lenta, instável, cai ou trava, a maioria das pessoas não sabe distinguir se
a causa provável está no Wi-Fi, roteador, equipamento da operadora, DNS, rede móvel, provedor, rota
ou uso simultâneo da conexão.

Testes tradicionais entregam métricas como Mbps, ping, jitter ou perda e deixam a interpretação com
o usuário. O SignallQ transforma medições e contexto em uma resposta compreensível, honesta e
acionável.

As perguntas que o produto deve responder são:

1. **O que está acontecendo com a minha internet?**
2. **Qual é a causa provável?**
3. **O que posso fazer agora?**
4. **A ação melhorou a conexão?**

## Público principal

O público principal é a pessoa que enfrenta um problema de conectividade e **não entende de redes**.
Ela descreve sintomas cotidianos — “está lenta”, “o vídeo trava”, “o jogo dá lag”, “o Wi-Fi não
chega no quarto” — e espera uma orientação em português claro.

Usuários técnicos continuam tendo acesso a métricas e detalhes, mas esses dados não devem dominar a
experiência padrão nem obrigar o público principal a interpretá-los sozinho.

## Promessa e proposta de valor

**Categoria:** diagnóstico de conectividade.

**Promessa principal:**

> Descubra por que sua internet está ruim — e o que fazer para resolver.

**Proposta de valor:**

> Para pessoas que enfrentam lentidão, quedas ou travamentos e não sabem por onde começar, o
> SignallQ é um assistente de diagnóstico de internet que analisa a conexão, explica as causas
> prováveis em linguagem simples e orienta como resolver. Diferentemente dos testes tradicionais,
> ele não termina nos números.

**Síntese de marca:**

> Sua internet explicada. Seu próximo passo indicado.

## Pilares do produto

Toda experiência deve contribuir para pelo menos um destes quatro pilares:

1. **Entender:** traduzir sintomas e métricas em uma explicação compreensível.
2. **Diagnosticar:** combinar medições, contexto e evidências para apontar causas prováveis.
3. **Resolver:** recomendar ações concretas, seguras e priorizadas.
4. **Confirmar:** permitir repetir ou comparar medições para verificar se a ação funcionou.

Uma funcionalidade que apenas apresenta mais um número técnico, sem apoiar nenhum desses pilares,
não deve ocupar o centro da experiência.

## Jornada canônica

1. A pessoa informa ou seleciona o sintoma percebido.
2. O SignallQ escolhe ou recomenda as medições necessárias.
3. O produto cruza sintomas, contexto e métricas disponíveis.
4. O resultado apresenta a causa provável e o nível de confiança, sem fingir certeza.
5. O produto recomenda o próximo passo mais útil.
6. A pessoa repete ou compara o teste para confirmar a melhora.

O produto não termina quando a medição acaba. A jornada termina quando o usuário entende o
resultado e sabe o que fazer em seguida.

## Papel de cada plataforma

### Web/PWA

É a superfície de conteúdo, aquisição e conversão para o aplicativo Android. O diagnóstico no
navegador entrega valor imediato e demonstra a proposta do produto, mas não é sua promessa mais
completa. Deve:

- captar a intenção ou o sintoma do usuário;
- medir velocidade, latência, estabilidade, comportamento sob carga, DNS e cenários de jogos
  dentro dos limites do navegador;
- responder buscas sobre problemas reais com conteúdo confiável e indexável;
- entregar uma primeira explicação e ações recomendadas;
- permitir histórico e comparação local;
- mostrar visualmente os benefícios do aplicativo;
- converter para o Android com CTA contextual, especialmente quando o diagnóstico exigir
  capacidades nativas.

O teste de velocidade e as ferramentas web permanecem relevantes para descoberta, SEO e confiança,
mas não definem sozinhos a identidade da experiência. A Web deve resolver parte do problema antes
de convidar para o aplicativo, sem se reduzir a uma página de download.

### Android

É a experiência de diagnóstico aprofundado. Além das medições compartilhadas com a Web, pode usar
capacidades nativas para analisar sinal Wi-Fi, rede móvel, dispositivos, equipamento de fibra,
monitoramento e contexto do aparelho.

A continuidade entre plataformas deve ser comunicada assim:

> Comece agora pelo navegador. Para investigar sua rede dentro de casa, continue no aplicativo.

## Princípios de linguagem

- Começar pela conclusão útil; detalhes técnicos vêm depois.
- Usar palavras do cotidiano antes do termo técnico.
- Quando o termo técnico for necessário, traduzi-lo no mesmo contexto.
- Distinguir medição, indício, causa provável e confirmação.
- Admitir dados insuficientes, resultado parcial ou baixa confiança.
- Sempre que possível, terminar com uma ação concreta e verificável.
- Não culpar automaticamente o provedor, roteador ou usuário sem evidência suficiente.

Exemplos preferidos:

- **Tempo de resposta (ping)**, não apenas “ping”.
- **Variação da conexão (jitter)**, não apenas “jitter”.
- **Atraso quando a rede fica ocupada (bufferbloat)**, não apenas “bufferbloat”.
- “Encontramos um possível problema” em vez de apresentar hipótese como certeza.
- “Ainda não temos dados suficientes para afirmar a causa” quando a evidência for inconclusiva.
- “Faça este teste para confirmar” quando faltar uma medição.

## Implicações para produto, design e engenharia

Ao projetar ou implementar uma tela, fluxo, funcionalidade, conteúdo ou evento de telemetria, os
agentes devem validar:

- A experiência começa pelo problema da pessoa ou apenas pela ferramenta?
- Os números foram traduzidos em significado para usos reais?
- A causa apresentada é sustentada pelas evidências disponíveis?
- Existe um próximo passo claro, seguro e priorizado?
- O usuário consegue verificar depois se a ação funcionou?
- As limitações da plataforma ou medição estão explícitas?
- A experiência mantém detalhes técnicos disponíveis sem torná-los pré-requisito?

O velocímetro, gauge ou valor de Mbps não deve ser tratado como protagonista por padrão. O
protagonista é a resposta ao problema do usuário.

## Limites desta decisão

Este posicionamento não autoriza, por si só:

- alterar modelo comercial, custos recorrentes ou provedores;
- prometer diagnóstico definitivo onde há apenas indício;
- afirmar capacidades que ainda não existem no Android ou na Web;
- esconder métricas técnicas de quem deseja consultá-las;
- misturar o escopo dos repositórios `signallq` e `signallq-web`.

Mudanças materiais de estratégia, marca, monetização ou promessa continuam sob decisão do Luiz,
conforme o contrato operacional do repositório.

## Fontes relacionadas

- [`HISTORIA.md`](HISTORIA.md) — origem e propósito do produto.
- [`FUNCIONAL.md`](01-funcional.md) — comportamento atualmente implementado no Android.
- [`decisions/ADR-016-portfolio-buildea.md`](archive/decisoes/ADR-016-portfolio-buildea.md) — perímetro,
  plataformas e modelo comercial.
- [`design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md`](design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md)
  — direção de experiência e identidade para Android e Web/PWA.
