---
title: "Design System — SignallQ para Windows"
description: "Adaptação do Design System Android para o app desktop Windows: tokens, layout, componentes e regras."
type: "funcional"
status: "draft"
owner: "Priscila (DS) · Claudete (produto)"
last_updated: "2026-11-07"
version: "0.1.0"
---

# Design System — SignallQ para Windows (v0.1, rascunho)

Draft: ainda não implementado em `windows/src/`. O protótipo navegável está em
`windows/design/prototype/index.html`; os tokens em `windows/design/tokens-windows.css`.

## 1. Por que mudar

Auditoria do app atual (`windows/src/styles.css`, `App.tsx`):

| Problema | Evidência | Correção |
|---|---|---|
| Marca divergente do Android | accent `#6c2bff`, dark `#aa85ff`; DS usa `#5B21D6` / `#D0BCFF` | Tokens idênticos ao `LK`/`LK_DARK` |
| Sem escala tipográfica | `font:14px` global, tamanhos soltos | Escala MD3 do Android (§3) |
| Dashboard técnico, sem conclusão | Home mostra "Dados da sua conexão" e métricas cruas | Veredito → evidência → ação (§5) |
| 9 itens de menu no mesmo nível | Visão geral, Velocidade, Dados, Gamer, Rede, Monitoramento, Histórico, Ferramentas, Config | 6 destinos + Configurações (§4) |
| CSS monolítico em 14 linhas | Estilo não auditável | Tokens em arquivo próprio, componentes por nome |

## 2. Princípio: mesma alma, corpo de desktop

Herda do Android a jornada **entender → diagnosticar → resolver → confirmar**, os princípios de
`DESIGN.md` e toda a semântica de cor. O que muda é o corpo: mouse e teclado, janela larga,
densidade maior. Não é um app Android esticado nem um Fluent puro: é SignallQ com Windows 11 ao redor.

| Herda do Android (não negociar) | Adapta para Windows |
|---|---|
| Cores `LK`/`LK_DARK`; violeta só em CTA, seleção, foco, navegação ativa | Raios um degrau abaixo (controle 8, card 12, diálogo 16) |
| Escala tipográfica e fonte Google Sans Flex (embutida) | Grid de 4 px; alvo mínimo 32 px (40 px com toque) |
| Cor + ícone + palavra juntos em todo estado | `NavigationView` lateral no lugar da barra inferior |
| Fato / regra / IA sempre rotulados | Barra de título própria, Ctrl+K, atalhos, foco visível |
| Ausência de dado é `—`, nunca zero | Conteúdo em até 1120 px, 12 colunas, em vez de coluna única |
| Skeleton ao carregar, nunca card vazio | Hover e `focus-visible` obrigatórios (sem equivalente no toque) |

## 3. Tokens

Fonte: `windows/design/tokens-windows.css`. Cores copiadas de `packages/design-system/src/tokens.ts`.

- **Cor:** papéis MD3 (`--primary`, `--surface-container`, `--success`, `--warning`, `--error`…). Verde/âmbar/vermelho
  são exclusivamente semânticos. Azul `--info` só identifica a fase Latência.
- **Tema escuro:** segue `LK_DARK` (`#131217`). `DESIGN.md` cita `#000000`; o código vence (precedência de fontes). Divergência a resolver no DS Android.
- **Tipografia:** `display 34/40`, `headline-l 26/32`, `headline-s 22/28`, `title-l 20/26`, `title-m 16/22`, `title-s 14/20`,
  `body-m 14/20`, `body-s 12/16`, `label-l 14/20`, `label-s 11/16` (overline). Números sempre `tabular-nums`.
  Google Sans Flex embutida (os `.ttf` de `android/app/src/main/res/font/`, SIL OFL; o protótipo usa cópias em
  `prototype/assets/fonts/`). Fallback para `Segoe UI Variable` caso a fonte falhe.
- **Marca:** logo e símbolo reais de `brand/` (as quatro barras). As cores do logo (`#6D2BFF`, `#7C4DFF`, `#2563EB`,
  `#3B82F6`) só aparecem em arte de marca, nunca como papel de cor de interface.
- **Profundidade (sem contorno):** camadas em vez de linhas. Moldura da janela (`--frame`) < palco (`--stage`) < card
  (`--card`) < sobreposto. Card é branco sobre palco lilás-claro, com sombra em duas camadas (`--elev-1/2/3`, tingida
  de violeta). Área rebaixada dentro de card (`--sunken` + `--inset`) para evidência, campos e gráficos de apoio.
  No escuro a sombra é preta e ganha um filete de luz no topo. Contorno só como separador interno discreto (linhas de
  lista e tabela). Elevação sobe no hover (`--elev-3` + 3 px) apenas em card acionável.
- **Movimento:** curva única `--ease` (`cubic-bezier(.2,0,0,1)`); 140 ms para saída, 350–550 ms para entrada;
  `prefers-reduced-motion` zera tudo.

### Movimento e carregamento

| Situação | Comportamento |
|---|---|
| Trocar de tela | Saída rápida (fade + 14 px), entrada deslizando na direção da navegação (para frente ou para trás), filhos entram em cascata (70 ms). Indicador da navegação desliza entre itens |
| Dado carregando | Barra de progresso indeterminada sob a barra de título + **skeleton com o formato da tela** (nunca card vazio, nunca spinner solto). Brilho em 1,5 s |
| Dado chegou | Números sobem de zero (1 s, ease-out), barras crescem, sparkline e gráfico se desenham da esquerda, caminho da internet se constrói nó a nó |
| Recarregar parte da tela | Só a região afetada vira skeleton (ex.: gráfico ao trocar período), com a barra de progresso ativa |
| Ação em andamento | Botão vira "Exportando…" com giro e fica desabilitado; terminal mostra três pontos pulsando e imprime linha a linha; toast confirma ao final |
| Ocioso (esperando o usuário) | Botão "Iniciar" do teste "respira" (anel de brilho pulsa); ponto de conexão pulsa suave; nada mais se mexe |
| Medição | Anel de progresso na cor da fase; cartão da fase atual sobe de elevação e desloca 6 px |
| Falha | Nunca animação de sucesso: erro recuperável em banner com ação, dado ausente em `—` |

## 4. Estrutura do app

```
┌ Barra de título 40px: logo · busca (Ctrl+K) · tema · controles da janela
├ Navegação 232px (64px abaixo de 1180px)   │  Conteúdo (Mica: `surface-low` ao redor,
│  Início · Velocidade · Minha rede         │  `bg` dentro, canto superior esquerdo 16px)
│  Modo gamer · Histórico · Ferramentas     │  máx. 1120px, 12 colunas, gap 16px
│  ─────────  Configurações                 │
```

- **6 destinos + Configurações**, alinhado às 4 raízes do Android (Início · Velocidade · Histórico · Ferramentas) mais duas
  específicas de PC (Minha rede, Modo gamer). "Dados da conexão" e "Monitoramento" viram seções dentro de Minha rede e Ferramentas.
- Janela mínima 960 px. Sem scroll horizontal. Itens de menu mantêm nome visível via tooltip no modo compacto.
- Item ativo: fundo `selected-bg` + barra de 3 px em `primary` + ícone violeta.

## 5. Padrões de tela

**Início (decisão: "minha internet está boa?")**
1. Hero de veredito em card branco com borda fina (violeta só no CTA, nunca em laje): estado, frase em 40 px, ação
   primária "Medir agora" e, à direita, as quatro barras de qualidade (Velocidade, Latência, Estabilidade, Wi‑Fi) que
   ecoam o logo. Cor semântica por barra; a nota geral fica no topo do painel.
2. Faixa de métricas num único card dividido em 4 colunas (Download, Upload, Latência, Perda), cada uma com a cor da
   fase do teste (verde, âmbar, azul), veredito em linguagem comum e sparkline.
3. Caminho da internet (Este PC → Roteador → Provedor), nó em âmbar quando há problema.
4. Próximo passo (uma ação) ao lado de "O que sustenta essa conclusão" com tags **Medido / Regra / IA** e nível de confiança.

**Velocidade:** disco violeta "Iniciar" com anel de brilho (como no Android); ao medir, vira anel de progresso na cor da
fase com o valor ao vivo, e três cartões de fase ao lado (Latência, Download, Upload), três etapas visíveis (Latência → Download → Upload), custo em dados declarado antes do
início, resultado traduzido ("Streaming 4K em 3 telas: ok"). Cancelar sempre disponível durante a execução.

**Histórico:** gráfico com legenda e unidade, filtro de período em chips, tabela com `Estado` por badge. Medição
interrompida nunca vira número.

**Minha rede / Ferramentas / Modo gamer:** listas de linhas (`list-row`), saída bruta apenas em Ferramentas, com
atalho para o diagnóstico guiado. Limites da plataforma são avisados em banner, nunca escondidos.

## 6. Componentes

| Componente | Regra |
|---|---|
| Botão | Primário sólido (1 por região), secundário outline, texto. Altura 32 (44 em CTA principal) |
| Card | `surface-container` (agrupado) ou `flat` com borda `outline-variant` (conteúdo independente). Sem sombra |
| Badge | Pill, sempre cor + ícone/texto. Estados: Bom, Regular, Ruim, Indisponível, Não foi possível medir |
| Chip | Filtro/seleção retangular r=8; selecionado em `selected-bg` + borda `selected-border` |
| Métrica | `lbl` · número · unidade · veredito. `—` quando ausente; skeleton ao carregar |
| Linha de lista | ≥ 48 px, título + apoio + valor tabular + badge opcional |
| Evidência | Tag `Medido` (success), `Regra` (warning), `IA` (primary) + frase. Nunca misturar os três |
| Banner | Aviso de limite da plataforma ou falha recuperável, com ação |
| Switch | 40×22, `role=switch`, rótulo e descrição |
| Tabela | Cabeçalho `label-m`, linha 44 px, hover `surface-high`, números à direita |

## 7. Acessibilidade

- Contraste AA nos dois temas (herdado de `LK`/`LK_DARK`); estado nunca só por cor.
- `:focus-visible` com anel de 2 px em `--focus-ring` em todo controle; navegação completa por Tab.
- Atalhos: `Ctrl+K` busca, `Ctrl+1…6` destinos, `Esc` fecha camada. Modo alto contraste do Windows: usar `forced-colors`.
- Narrador: gauge e gráficos com `aria-label` descrevendo valor e unidade; resultados em `aria-live`.

## 8. Plano de adoção (não executado)

1. Aprovar este rascunho e o protótipo (Luiz / Claudete).
2. Importar `tokens-windows.css` em `windows/src/` e substituir `styles.css` por tokens + componentes.
3. Migrar Início → Velocidade → Histórico primeiro; demais telas depois, uma por vez.
4. Embutir Google Sans Flex em `windows/` (hoje não há fonte local).
5. Promover tokens comuns ao pacote `packages/design-system` quando o desktop consumir o pacote.
6. Tiago/Renan: validar contraste, foco, 125–200% de escala do Windows e janela de 960 px.

## 9. Fora de escopo

Lógica de medição, diagnóstico e IA permanecem em `windows/src-tauri/`; esta spec só governa apresentação.
O protótipo usa dados simulados e não deve ser lido como funcionalidade entregue.
