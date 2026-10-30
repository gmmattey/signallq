# Product — SignallQ 2.0

## Produto

SignallQ é um produto de diagnóstico de conectividade para Android, apoiado por uma presença Web
voltada à descoberta, educação e aquisição. Não é apenas mais um speedtest: mede quando necessário,
analisa o contexto da rede, explica o que encontrou e orienta o usuário até confirmar se o problema
foi resolvido.

## Para quem

Pessoas brasileiras que percebem que “a internet está ruim”, mas não dominam redes. Elas querem
respostas para quatro perguntas:

1. O que está acontecendo?
2. Onde provavelmente está o problema?
3. O que eu consigo fazer agora?
4. A ação resolveu?

## Promessa

**Descubra por que sua internet está ruim e o que fazer para melhorar.**

O sucesso não é exibir a maior quantidade de métricas. É permitir que a pessoa entenda o problema e
execute um próximo passo seguro em poucos minutos.

## Pilares da experiência

1. **Entender** — começar pelo sintoma e pelo contexto percebido pelo usuário.
2. **Diagnosticar** — selecionar e executar as análises adequadas, sem exigir conhecimento técnico.
3. **Resolver** — transformar evidências em uma orientação curta, concreta e priorizada.
4. **Confirmar** — repetir a verificação relevante e mostrar se houve melhora.

## Papel de cada plataforma

### Android

É o produto principal de diagnóstico. Deve parecer um aplicativo Android nativo, no espírito dos
apps feitos pelo Google: Material 3, hierarquia clara, movimento funcional e pouco texto. A entrada
principal é o problema percebido pelo usuário, não uma lista de ferramentas.

O app deve revelar suas capacidades no momento certo. Speedtest, Wi-Fi, DNS, rede móvel, fibra,
dispositivos e monitoramento são instrumentos do diagnóstico, não destinos que o usuário precisa
explorar como especialista.

### Web

O site e webapp em `signallq.com` têm como objetivo principal apresentar o valor do produto,
responder dúvidas reais sobre conectividade, conquistar tráfego orgânico e converter para o app.
Podem oferecer demonstrações ou verificações leves, mas não devem competir com o Android como
experiência principal de diagnóstico.

## Jornada-base do Android

**Sintoma → contexto → análise → resultado → próxima ação → nova verificação.**

O resultado deve seguir esta ordem:

1. veredito em linguagem humana;
2. causa provável e nível de confiança;
3. evidências essenciais;
4. ação recomendada;
5. confirmação após a ação.

## Personalidade da marca

Leve, confiável e profissional. A voz é calma, direta e humana. Fala com “você” e evita tanto o tom
de suporte burocrático quanto a aparência de resposta produzida por IA.

## Princípios de conteúdo

- Use frases curtas e palavras comuns.
- Diga primeiro o que importa; detalhe técnico é progressivo e opcional.
- Nunca mostre uma métrica sem explicar o que ela significa.
- Não prometa certeza quando houver apenas uma hipótese.
- Toda descoberta relevante deve levar a uma ação.
- Evite emojis, entusiasmo artificial, jargão e parágrafos longos.

## Identidade visual

- Bastante espaço em branco e baixa densidade.
- Violeta da marca reservado a CTAs, seleção e poucos momentos de identidade.
- Cores semânticas comunicam qualidade ou severidade; não decoram.
- Cards e pills só existem quando agrupam, selecionam ou distinguem conteúdo.
- Modo escuro usa preto total como fundo principal.
- Material Symbols e Google Sans Flex sustentam a aparência nativa Android.

## Anti-referências

- Speedtests centrados em velocímetro, números e rankings sem orientação.
- Dashboards de rede densos, técnicos ou com estética de “hacker”.
- Interfaces de IA baseadas em chat, brilho, gradientes roxos ou textos excessivos.
- Landing pages genéricas de SaaS com uma grade de cards para cada funcionalidade.

## Acessibilidade

Seguir Material 3 e WCAG aplicável: alvos de toque de pelo menos 48dp, foco visível, contraste
adequado, suporte a escala de fonte e status comunicado por palavra e ícone — nunca apenas por cor.

## Fontes canônicas

- `docs/POSICIONAMENTO_PRODUTO.md`
- `docs/design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md`
- `docs/functional/JORNADA_ANDROID_GUIADA_2_SPEC.md`
