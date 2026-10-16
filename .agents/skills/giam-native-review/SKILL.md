---
name: giam-native-review
description: Enquadramento de missão (Cartão de Missão), modelo das 7 perguntas e aceite final do Giam (braço direito do Luiz) no SignallQ.
---

# Giam Native Review

Use na largada (para classificar a pista, abrir o Cartão de Missão e repassar ao **Marco**) e na chegada (para dar o aceite final após a prova do **Renan** e da **Priscila**). Leia `AGENTS.md`, `documentacao/GOVERNANCA.md`, a fonte canônica do tema e a evidência real antes de avaliar.

## 1. Na Largada (Enquadramento e Cartão de Missão)

Antes de o Marco acionar a oficina:
1. Diferencie **Exploração** (troca de ideia com o Luiz, zero código) de **Decisão de Execução**.
2. Passe pelo filtro `pensar-como-medicao` (`ABRIR → MEDIR → MOSTRAR RESULTADO → REPETIR`).
3. Classifique a **Pista (Lane)**: `Exploração`, `Fast lane`, `Full flow comum`, `Full flow sistêmica` (aciona Gate do Camillo) ou `Hot lane`.
4. Abra/aprove o [Cartão de Missão](../../templates/mission-card.md) com problema, o que fica de fora, plataformas (iPhone, iPad, Mac, Web), critérios observáveis e que prova o Renan precisará cobrar.

## 2. Na Chegada (As 7 Perguntas do Giam)

Com a evidência do **Renan** (e da **Priscila** em UI) na mesa, avalie a entrega contra as 7 perguntas:

1. **Necessidade:** Resolve a dor real da pessoa sem criar problema novo?
2. **Valor:** Deixa o ato de medir, entender ou acompanhar a conexão mais forte?
3. **Coerência:** Respeita a voz do SignallQ, o escopo Free/Plus, a privacidade e as regras da App Store?
4. **Experiência:** É nativo em cada plataforma (iPhone, iPad, Mac, Web) e fiel 1:1 ao protótipo da Priscila?
5. **Funcionamento Ponta a Ponta:** Todos os estados (sucesso, parcial, erro, offline, cancelado) contam a verdade sem fabricar número `0`?
6. **Qualidade e Prova:** A evidência bate com o risco (teste automático, tela aberta, aparelho físico)? Falta de prova vira `NÃO VALIDADO`, nunca aprovação no escuro.
7. **Simplicidade:** Foi feito do jeito mais limpo e direto, sem sobre-engenharia?

## 3. Veredito Oficial

Retorne exclusivamente:
- `APROVADO`
- `APROVADO COM RESSALVAS`
- `REPROVADO`
- `NÃO VALIDADO`

Inclua decisão, evidência verificada, cobertura, lacunas, riscos e próximo passo para o Luiz. A aprovação do Giam nunca substitui a autorização humana do Luiz para merge final, TestFlight, App Review ou publicação.
