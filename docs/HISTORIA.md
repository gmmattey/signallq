---
title: "A história do SignallQ"
description: "Origem, propósito e princípios que explicam por que o SignallQ existe"
type: "produto"
status: "ativo"
owner: "Luiz"
last_updated: "2026-10-04"
version: "1.1.0"
---

# A história do SignallQ

O SignallQ não começou porque eu queria criar uma startup. Na verdade, eu nem era desenvolvedor.

Tudo começou com uma planilha de investimentos.

Eu estava tentando organizar melhor algumas informações quando descobri que poderia automatizar parte daquele trabalho usando ferramentas do Google. Depois descobri que dava para conectar essas automações a páginas em HTML.

Eu não sabia programar aquilo.

Então comecei a usar inteligência artificial para me ajudar a entender o código, montar as coisas e corrigir o que dava errado. Eu perguntava, testava, quebrava, entendia um pouco mais e tentava novamente.

Foi assim que nasceu meu primeiro projeto, o Schill Invest.

Com o tempo, descobri ferramentas como o Codex e percebi uma coisa que até então não parecia possível para mim: eu podia transformar minhas próprias ideias em produtos reais, mesmo sem ter construído uma carreira como desenvolvedor.

Mas um aplicativo financeiro começou a exigir conhecimentos muito distantes da minha área.

Minha experiência profissional sempre esteve muito mais perto de telecomunicações, produtos digitais, atendimento e reparo. Era nesse mundo que eu realmente conhecia os problemas.

E foi justamente um problema bastante banal que mudou o rumo das coisas.

Eu precisava acessar meu próprio modem.

Sempre esquecia a senha, achava o processo ruim e comecei a investigar se conseguiria fazer aquilo de outra maneira. Fiz um mapeamento do funcionamento do equipamento, experimentei algumas abordagens e consegui.

O que deveria resolver apenas um problema meu virou uma pergunta:

> E se isso pudesse fazer mais coisas?

Comecei a adicionar funções.

Veio o teste de velocidade. Depois informações sobre a conexão, Wi-Fi, dispositivos e outras ferramentas que normalmente estavam espalhadas entre vários aplicativos.

O projeto mudou bastante no caminho. Já se chamou Linka. Depois Velu. Até finalmente virar SignallQ.

Mas juntar ferramentas nunca foi suficiente.

Existem excelentes aplicativos capazes de medir velocidade, analisar Wi-Fi ou identificar dispositivos conectados à rede. O problema é que boa parte dessas ferramentas foi criada para quem entende o que aqueles números significam.

A maioria das pessoas não quer saber o que significa RSSI, jitter, perda de pacotes ou bufferbloat.

Ela quer saber:

- Minha internet está ruim?
- O problema é o Wi-Fi ou a operadora?
- Tem alguma coisa que eu possa fazer?

Foi aí que nasceu a ideia central do SignallQ.

Não apenas medir a conexão.

**Entender o que está acontecendo e explicar isso para uma pessoa normal.**

O diagnóstico passou a ser uma parte fundamental do produto. A tecnologia pode analisar dezenas de informações, mas o usuário deve receber uma resposta simples, humana e direta.

Sem transformar todo mundo em técnico de redes só porque o Wi-Fi resolveu ficar uma porcaria naquela noite.

---

> **Nota (2026-08-15):** hoje "Linka" é o nome de outro produto do portfólio Buildea — exclusivo do
> ecossistema Apple, pago desde o lançamento (ver [ADR-016](archive/decisoes/ADR-016-portfolio-buildea.md)).
> A menção acima é sobre o nome anterior do próprio SignallQ, numa fase antes do rebrand para Velu e,
> depois, SignallQ — não tem relação com o produto Linka atual.

## Um produto que eu gostaria que existisse

Outra decisão veio desde o começo: as principais ferramentas deveriam continuar acessíveis gratuitamente.

Hoje várias funções úteis estão distribuídas entre diferentes aplicativos ou escondidas atrás de assinaturas.

O SignallQ tenta seguir outro caminho.

Reunir em um único lugar ferramentas para testar, investigar e entender uma conexão, mantendo seus principais recursos disponíveis para qualquer pessoa.

Isso obviamente tem custo.

Infraestrutura custa dinheiro. Inteligência artificial custa dinheiro. Desenvolvimento custa tempo — bastante tempo.

Por isso, o modelo precisa conseguir sustentar o próprio produto. A ideia é fazer isso principalmente por meio de publicidade não invasiva e outras fontes de receita que não destruam a experiência nem transformem cada botão em uma tentativa de vender uma assinatura.

O objetivo não é fingir que manter um produto é grátis.

É encontrar uma maneira de sustentá-lo sem estragar aquilo que fez o SignallQ nascer.

## Uma empresa que ainda não existe

Existe ainda uma característica um pouco incomum por trás do SignallQ.

Eu não tenho uma equipe de desenvolvimento.

Não tenho dinheiro para contratar engenheiros, designers, especialistas de produto, marketing, dados e todas as pessoas que normalmente seriam necessárias para construir algo assim.

Então comecei a organizar os agentes de inteligência artificial como se eles fossem essa equipe.

Cada um possui uma responsabilidade.

Existem agentes pensando em engenharia, experiência do usuário, produto, crescimento, qualidade e operação.

Eles não existem apenas para escrever código.

Como eu venho principalmente da área de negócio e produto, preciso que eles cubram justamente os pontos em que meu conhecimento é menor. Eles devem questionar decisões ruins, apontar riscos técnicos, sugerir soluções melhores e impedir que uma boa ideia vire um produto mal executado.

Nesse time, meu papel não é fingir que sei tudo.

É decidir para onde o produto deve ir.

E o papel deles é me ajudar a chegar lá com qualidade.

Porque usar inteligência artificial para construir mais rápido não é desculpa para entregar coisa malfeita.

Código precisa funcionar.

O produto precisa resolver um problema.

A experiência precisa ser simples.

E se tiver uma linha torta na interface, alguém vai ter que consertar aquela porra.

## O princípio que guia o produto

Essa é a ideia por trás do SignallQ:

**tecnologia complexa trabalhando por baixo e uma experiência simples para quem está usando.**

A pergunta que continua guiando o produto desde o começo é a mesma:

> Tá, mas o que está acontecendo com a minha internet?

O SignallQ existe para responder.

---

## Estado atual (para agentes futuros)

*(Seção adicionada em 2026-08-15 e atualizada em 2026-10-04, curadoria pós-ADR-016 — não faz parte do relato original do Luiz
acima; é só orientação factual objetiva.)*

**O que o SignallQ é hoje:** app de diagnóstico de conectividade em Android (Kotlin/Compose) e Web
(PWA em `signallq-web`), modelo freemium sustentado por publicidade não invasiva — núcleo gratuito,
sem assinatura obrigatória.

**O que o SignallQ não é (mais):**
- "Linka" é hoje outro produto do portfólio Buildea — exclusivo Apple (iOS/iPadOS/macOS), pago desde
  o lançamento, repositório próprio. Não confundir com o nome antigo citado na narrativa acima.
- SignallQ Pro, SignallQ ISP e Nethal foram descontinuados permanentemente — não retomar sem ADR
  novo do Luiz.

**Portfólio Buildea:** guarda-chuva comercial, não stack técnica. Dois produtos ativos — SignallQ
(Android+Web, freemium/ads, usuário doméstico brasileiro) e Linka (Apple, pago, diagnóstico
profissional). Squads, repositórios e governança separados; não compartilham código.

**Squad SignallQ:** Cora (produto), Davi (Android), Ramon (diagnóstico/Workers), Breno (QA) e Camillo
(arquitetura transversal) — governança em `AGENTS.md`. A squad Claudete/Camilo/Caio foi aposentada.

**Fonte canônica atualizada:** [ADR-016 — Portfólio Buildea](archive/decisoes/ADR-016-portfolio-buildea.md).
