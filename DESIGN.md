---
name: SignallQ 2.0
description: Sistema visual leve e nativo Android para diagnóstico de conectividade orientado.
colors:
  primary: "#5B21D6"
  on-primary: "#FFFFFF"
  primary-container: "#EAE0FF"
  on-primary-container: "#210A5C"
  secondary-legacy: "#2851B8"
  success: "#146C2E"
  warning: "#8A5000"
  error: "#BA1A1A"
  background-light: "#FFFFFF"
  surface-light: "#F8F5FB"
  surface-container-light: "#F3EEFA"
  text-primary-light: "#1C1B1F"
  text-secondary-light: "#49454F"
  outline-light: "#79747E"
  background-dark: "#000000"
  surface-dark: "#121212"
  surface-container-dark: "#1E1E1E"
  surface-container-high-dark: "#2A2A2A"
  card-surface-light: "#F7F7F8"
  card-surface-dark: "#161616"
  card-surface-elevated-light: "#EEEEF0"
  card-surface-elevated-dark: "#222222"
  text-primary-dark: "#F5F2F7"
  text-secondary-dark: "#CAC4D0"
  outline-dark: "#938F99"
typography:
  family: "'Google Sans Flex', 'Google Sans', 'Roboto', system-ui, sans-serif"
  display: "700 34px/40px"
  headline-large: "700 26px/32px"
  headline-small: "600 22px/28px"
  title-large: "600 20px/26px"
  title-medium: "500 16px/22px"
  body-large: "400 16px/24px"
  body-medium: "400 14px/20px"
  label-large: "500 14px/20px"
rounded:
  small: "8px"
  medium: "12px"
  large: "16px"
  sheet: "28px"
  full: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  base: "16px"
  lg: "20px"
  xl: "24px"
  xxl: "32px"
  xxxl: "40px"
  section: "48px"
  section-large: "64px"
---

# Design System — SignallQ 2.0

## Escopo desta versão

O sistema cobre **UI de produto**, evolui a marca SignallQ existente e, nesta primeira versão,
formaliza somente **foundations e componentes centrais**. Templates de tela, componentes de
domínio e implementação ficam para as próximas etapas.

## Direção criativa

**Um guia calmo para uma rede confusa.**

O SignallQ deve parecer um app nativo de um aparelho Android feito pelo Google: familiar, leve,
preciso e silenciosamente competente. O visual ajuda a pessoa a tomar uma decisão; não tenta
impressioná-la com complexidade técnica.

## Princípios

1. **Diagnóstico antes da ferramenta.** Comece pelo que a pessoa sente; escolha a medição depois.
2. **Humano antes do técnico.** Veredito e ação vêm antes de valores, siglas e gráficos.
3. **Uma decisão por tela.** O CTA principal deve ser evidente sem competir com ações secundárias.
4. **Espaço é estrutura.** Use respiro, tipografia e alinhamento antes de criar containers.
5. **Confiança sem falsa certeza.** Diferencie fato, indício e hipótese.
6. **Android de verdade.** Material 3, padrões de navegação e movimento coerentes com a plataforma.

## Cor

O violeta `#5B21D6` é assinatura, não papel de parede. Use-o em:

- CTA primário;
- seleção atual;
- foco;
- navegação ativa;
- momentos breves de identidade.

Não use violeta em grandes fundos, em todos os cards, em gráficos inteiros ou como substituto de
hierarquia. Verde, âmbar e vermelho são exclusivamente semânticos. O azul legado não participa da
paleta ativa da UI 2.0; permanece apenas no logo e como compatibilidade durante a migração.

### Tema claro

- Fundo: `#FFFFFF`
- Superfície discreta: `#F8F5FB`
- Container: `#F3EEFA`
- Texto principal: `#1C1B1F`
- Texto secundário: `#49454F`

### Tema escuro

O fundo principal é preto total `#000000`. A hierarquia nasce de superfícies próximas:
`#121212`, `#1E1E1E` e, apenas quando necessário, `#2A2A2A`. Evite transformar o tema escuro
em uma tela roxa ou cinza uniforme.

## Tipografia

Use Google Sans Flex, já incluída no Android, com Roboto como fallback. A hierarquia vem de tamanho,
peso e espaço — não de muitas famílias ou estilos.

- Headlines curtas, preferencialmente em sentence case.
- Corpo entre 14 e 16sp, com largura confortável.
- Números podem ser grandes quando são evidência principal, nunca o propósito isolado da tela.
- Unidades e explicações têm menor ênfase que o valor.
- Evite caixa alta, salvo rótulos muito curtos.

## Espaçamento e composição

Use grade-base de 4dp e intervalos recorrentes de 8, 12, 16, 24, 32, 48 e 64dp. Margem horizontal
móvel padrão de 20–24dp. Separe grandes blocos com espaço antes de recorrer a um card ou divisor.

As telas devem ter baixa densidade, leitura vertical clara e conteúdo técnico progressivamente
revelado.

## Componentes

### Botões

- CTA primário preenchido em violeta, com altura mínima de 48dp.
- Ação secundária tonal, outlined ou textual conforme a hierarquia.
- Um CTA primário por estado de tela.
- Labels diretos: “Analisar minha rede”, “Ver como resolver”, “Testar novamente”.

### Cards

Cards são usados apenas quando existe agrupamento, comparação, ação independente ou elevação
semântica. São tonais, sem gradiente e normalmente sem contorno perimetral. A profundidade vem da
diferença entre superfícies; sombra discreta fica reservada ao que realmente estiver elevado. Não
envolva cada linha, métrica ou seção em um card.

Quando houver card, usar cinza quase branco `#F7F7F8` no tema claro e cinza quase preto `#161616`
no escuro. Regiões internas elevadas usam `#EEEEF0` e `#222222`. O fundo-base permanece branco ou
preto, para que a profundidade seja percebida sem moldura.

### Pills e chips

Use somente para seleção compacta, filtros ou status breves. Não transforme títulos, categorias ou
parágrafos em pills decorativas.

### Listas

Prefira linhas abertas, ícone + título + descrição curta, separadas por espaço ou divisor discreto.
Listas são a escolha padrão para sintomas, ações e histórico.

### Resultados

A ordem visual obrigatória é:

1. veredito humano;
2. causa provável e confiança;
3. evidências essenciais;
4. próxima ação;
5. confirmação.

Detalhes técnicos ficam recolhidos. Nenhum valor de latência, jitter, perda, sinal ou velocidade
aparece sem uma tradução próxima.

### Linguagem de confiança

- Alta: “Encontramos um problema no Wi-Fi.”
- Média: “O Wi-Fi provavelmente está causando as interrupções.”
- Baixa: “Há sinais de que o problema pode estar no Wi-Fi.”

Não exibir porcentagem de confiança ao usuário comum.

## Navegação Android

Hipótese para o protótipo: três destinos principais — **Início, Histórico e Mais**. Ferramentas
especializadas aparecem dentro da jornada ou em “Mais”. Durante uma análise, reduza a navegação e
mantenha foco no processo.

A tela inicial deve apresentar:

- estado atual resumido;
- CTA “Analisar minha rede”;
- sintomas comuns como atalhos;
- último resultado ou continuidade, quando houver.

## Ícones e imagens

Use Material Symbols Outlined como sistema principal. Ícones têm função e não substituem texto
essencial. Não use emoji.

Assets oficiais:

- `brand/signallq-symbol-1024.png`
- `brand/signallq-symbol-512.png`
- `brand/signallq-lockup-light-bg.png`
- `brand/signallq-lockup-dark-bg.png`

Não redesenhe o símbolo e não aplique glow, 3D ou gradiente decorativo.

## Movimento

Movimento deve lembrar Android nativo e explicar mudança de estado:

- transições de conteúdo com fade-through;
- mudança de container com shared-axis quando houver continuidade;
- bottom sheets surgem da base;
- progresso é contínuo e calmo;
- feedback tátil/visual imediato ao toque.

Duração indicativa: 150–250ms para microinterações e 250–400ms para mudanças de tela. Respeite a
preferência de movimento reduzido. Evite parallax, bounce excessivo e animações ornamentais.

## Voz

Curta, direta, humana e tranquila. Não use voz de chatbot, frases como “nossa IA analisou”, excesso
de exclamações ou textos genéricos de assistência.

Exemplos:

- “O Wi-Fi está instável neste cômodo.”
- “A velocidade está boa, mas a resposta da rede oscila.”
- “Aproxime-se do roteador e teste novamente.”
- “Melhorou. As interrupções devem diminuir.”

## Estados obrigatórios

Todo fluxo deve prever: inicial, carregando/analisando, sucesso, problema encontrado, evidência
insuficiente, permissão necessária, offline, erro recuperável e conteúdo vazio.

## Acessibilidade

- Alvo de toque mínimo: 48×48dp.
- Contraste WCAG AA para texto e controles.
- Foco visível e ordem de navegação lógica.
- Suporte a fonte ampliada sem truncar ações essenciais.
- Status comunicado com texto + ícone + cor.
- Gráficos sempre acompanhados por resumo textual.

## Anti-padrões

- velocímetro hero como identidade central;
- mosaico de ferramentas na home;
- cards aninhados ou grade de cards sem necessidade;
- pills decorativas;
- excesso de roxo;
- gradientes, glassmorphism, neon ou estética de hacker;
- chat como interface principal;
- explicações longas antes do resultado;
- jargão sem tradução;
- menu de cinco abas expondo toda a arquitetura interna.

## Alvo do primeiro protótipo

Validar o fluxo completo com um único cenário: streaming ou chamada travando por instabilidade sob
carga. O protótipo deve cobrir Home, seleção de sintoma, contexto, análise, resultado, ação
recomendada, confirmação e acessos básicos a Histórico e Mais, em temas claro e escuro.

## Referências canônicas

- `PRODUCT.md`
- `docs/POSICIONAMENTO_PRODUTO.md`
- `docs/design-system/SIGNALLQ_DESIGN_SYSTEM_2_SPEC.md`
- `docs/functional/JORNADA_ANDROID_GUIADA_2_SPEC.md`
