# Cartão de Missão — SignallQ

> Obrigatório antes de o Marco abrir execução na oficina (exceto Hot lane, que registra logo após apagar o incêndio). Sem este cartão aprovado pelo Giam, a conversa continua em **Exploração** (zero alteração de código).

## 1. Enquadramento (Giam)

- **Dor / Problema da pessoa:**
- **Comportamento esperado na tela:**
- **Fora de escopo (Não vira):**
- **Pista (Lane):** Exploração | Fast lane | Full flow comum | Full flow sistêmica | Hot lane
- **Por que esta pista:**
- **Fonte de verdade afetada:** `PRODUTO.md` | `ARQUITETURA.md` | `OPERACAO.md` | `GOVERNANCA.md`

## 2. Donos e Fronteiras (Marco)

- **Braço direito e Aceite Final:** Giam
- **Orquestração e Mesa Limpa:** Marco
- **Responsável pela implementação:** Bia (Telas/App) | Camillo (Motor/Rede)
- **Especificação e Auditoria Visual:** Priscila | não aplicável
- **Gate Arquitetural (Camillo):** não aplicável | pendente | aprovado
- **Auditoria e Prova Real:** Renan
- **Decisão pendente do Luiz:** nenhuma | descrever

## 3. Comportamento, Plataformas e Risco

- **Plataformas afetadas:** iPhone | iPad | Mac | Web (explicitar diferenças nativas)
- **Estados obrigatórios:** inicial, medindo/carregando, resultado completo, parcial, erro, offline, cancelado e Reduced Motion
- **Maior risco de quebra ou mentira visual:**
- **Limite da promessa (o que a API/plataforma não mede):**

## 4. Matriz de Aceite e Evidência (Renan & Priscila → Giam)

| Critério observável | Dono da prova | Ambiente exigido (Código / Teste / Simulador / Mac / iPhone / iPad) | Estado |
|---|---|---|---|
| | | | pendente |

> Um nível de evidência nunca substitui o seguinte ("compilou" não é "funcionou na tela").

## 5. Encerramento e Parecer Final

- **Arquivos/módulos alterados:**
- **Parecer do Renan:** `SEM ACHADOS MATERIAIS` | `AJUSTA` | `BLOQUEIA` | `ISSUE_FUTURA`
- **Veredito Final do Giam:** `APROVADO` | `APROVADO COM RESSALVAS` | `REPROVADO` | `NÃO VALIDADO`
- **Pendências ou decisão necessária do Luiz:**
