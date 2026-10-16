---
name: matar-cheiro-de-ia
description: Filtro obrigatório contra linguagem e formato típicos de IA em copy, issue, PR, commit e conversa do SignallQ.
---

# Skill: matarCheiroDeIA

Filtro final de **tudo que é escrito no SignallQ**: copy do produto, texto do site, issue, PR, commit, mensagem para o Luiz, texto de erro, README, release notes.

Uma coisa só:

> **Se dá para sentir que um robô escreveu, refaz.**

O SignallQ fala com voz curta, técnica e calma ([`documentacao/PRODUTO.md`](../../../documentacao/PRODUTO.md)). Nada aqui pode soar como assistente virtual de banco, copy de release note SaaS ou onboarding de app de startup.

Esta skill não é a fonte da voz — a fonte é [`documentacao/PRODUTO.md`](../../../documentacao/PRODUTO.md). Aqui é a **lista do que mata**.

---

## 1. Palavras e frases banidas

### Abertura de robô

`Claro!` · `Com certeza!` · `Ótima pergunta!` · `Perfeito!` · `Excelente!` · `Vamos lá!` · `Boa!` seguido de explicação · repetir a pergunta antes de responder · `Aqui está o que eu fiz` · `Segue abaixo` · `Vamos mergulhar em`

### Fechamento de robô

`Espero que ajude!` · `Fico à disposição` · `Qualquer dúvida, é só chamar` · `Em resumo,` · `Concluindo,` · `## Conclusão` · `Me avise se quiser que eu ajuste` · oferecer três próximos passos que ninguém pediu

### Vocabulário de release note e marketing

robusto · poderoso · elegante (em copy — como direção interna do design system, tudo bem) · seamless · sem esforço · intuitivo · escalável · otimizar · alavancar · potencializar · impulsionar · elevar · desbloquear · maximizar · jornada do usuário · experiência fluida · solução completa · de forma eficiente e eficaz · valor agregado · alinhado com · leve e rápido · revolucionário · inovador · próxima geração · reinventando · redefinindo

### Corporativês

`Como usuário, eu quero…` · critérios de aceitação · definition of done · entregável · stakeholder · alinhamento · sinergia · impacto no negócio · priorização estratégica · nice to have · quick win · MVP como adjetivo de qualidade

### Muleta de estrutura

- **`Não é X. É Y.`** — funciona uma vez. Três vezes no mesmo texto é tique.
- **`Não apenas X, mas também Y.`**
- **`X — e isso muda tudo.`**
- **`A verdade é que…`** / **`O ponto é…`** / **`Vale notar que…`**
- **`É importante lembrar que…`**
- travessão dramático em toda frase — o texto vira gagueira com pausa.
- tríade forçada — "rápido, simples e elegante" quando cabia só uma palavra.

## 2. Formatos banidos

- **Emoji abrindo cada bullet.** Um emoji porque coube, ok. Um por linha, não.
- **Tudo virando lista.** Se era um parágrafo de três frases, continua sendo um parágrafo de três frases.
- **Negrito espalhado.** Negrito marca o que decide. Se metade do texto está em negrito, nada está.
- **Tabela para duas informações.** Tabela compara — não enfeita.
- **A tríade eterna.** Nem tudo tem exatamente três itens.
- **Título em tudo.** Bilhete de três frases não tem `## Contexto`.
- **Resumo do que acabou de ser dito**, logo embaixo do que foi dito.
- **Disclaimer preventivo** — "vale lembrar que isso pode variar" — quando ninguém perguntou.
- **Hedge empilhado** — "talvez possa eventualmente". Ou é, ou não é, ou não se sabe e fala que não se sabe.

## 3. O que fazer no lugar

| Cheiro de IA | SignallQ |
|---|---|
| `Ops! Tivemos uma pequena instabilidade.` | `Falha na medição. Tente novamente.` |
| `Parabéns! Sua medição foi processada com sucesso.` | `Download 91 Mbps · Upload 12 Mbps` |
| `Otimizamos a experiência de compartilhamento.` | `Agora o compartilhamento inclui o horário do teste.` |
| `Deseja tentar novamente?` | `Testar novamente` |
| `Sua sessão expirou. Por favor, faça login novamente.` | (o SignallQ não tem sessão) |
| `Estamos processando sua solicitação…` | `Preparando` |
| `Descubra uma nova forma revolucionária de medir sua internet.` | `Sua conexão. Sem distrações.` |
| `Nossa experiência minimalista foi criada para…` | (remova; a experiência demonstra) |

## 4. Copy do produto tem regra extra

Além de tudo acima, texto que vai para tela obedece [`documentacao/PRODUTO.md`](../../../documentacao/PRODUTO.md), inclusive:

- **rótulo de botão não varia** (é contrato) — `Testar novamente`, `Detalhes`, `Como medimos`;
- **erro fala a verdade** — não esconde falha atrás de mensagem simpática;
- **o SignallQ não opina no primeiro frame do resultado** — nunca "sua conexão está boa para X" antes do número. Interpretação vive em superfície secundária (detalhes, histórico, Assist) e precisa se sustentar em dado real (ver [`Produto`](../../../documentacao/PRODUTO.md));
- **voz não cria capacidade** — não escreve copy de coisa que não existe.

Ver também [`aplicarVozSignallQ`](../aplicarVozSignallQ/SKILL.md).

## 5. O teste

Leia em voz alta. Uma pergunta:

> **Isso soa como o produto ou como uma agência tentando vender o produto?**

Se soa como agência, refaz. Se soa como manifesto ("a nova forma de..."), refaz. Se soa como changelog de SaaS ("Otimizamos a experiência..."), refaz.

Segundo teste, para copy de tela:

> Cabe numa tela sem que o usuário tenha que ler duas vezes?

Terceiro teste, para issue/PR/commit:

> Um colega de outra squad entende o que a mudança faz sem precisar do Slack?

## 6. Uma coisa que não é cheiro de IA

Texto **claro e organizado** não é cheiro de IA. Um passo a passo de verdade, uma tabela que compara opções de verdade, um relatório de teste que separa o que foi verificado do que não foi — isso é trabalho bem feito.

O cheiro é o **enfeite**: a estrutura que existe porque enche o olho, não porque alguém precisava dela.

## Relacionados

- **A voz:** [`documentacao/PRODUTO.md`](../../../documentacao/PRODUTO.md)
- **Escrever copy do produto:** [`aplicarVozSignallQ`](../aplicarVozSignallQ/SKILL.md)
- **Falar com o Luiz:** [`conversarComOLuiz`](../conversarComOLuiz/SKILL.md)
- **Issue, PR e commit:** [`registrarIssue`](../registrarIssue/SKILL.md)
