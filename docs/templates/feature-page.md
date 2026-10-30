---
title: "<Nome de produto da feature>"
description: "<Uma frase: o problema que a feature resolve e para quem.>"
type: "feature"
status: "ativo | draft | congelado | deprecated"
owner: "<agente ou pessoa>"
last_updated: "YYYY-MM-DD"
version: "0.1.0"
feature: "<slug-kebab-case>"
tipo: "jornada | transversal"
modulos: []        # pastas de módulo Gradle, ex.: android/feature/dns
arquivos: []       # caminhos reais relativos à raiz do repo; listas curtas, prefira diretórios
contratos: []      # OpenAPI/schemas em docs/CONTRATOS/ que a feature consome ou expõe
eventos: []        # nomes reais de analytics, como estão no código
flags: []          # chaves reais de feature flag (remota ou compile-time)
testes: []         # caminhos reais de teste
adrs: []           # ADRs vigentes que afetam a feature
thresholds_em: ""  # caminho do arquivo onde os limiares vivem (ponteiro; nunca copiar valores)
---

# <Nome de produto da feature>

## NEGÓCIO

### 1. Problema e promessa

<Qual pergunta do usuário a feature responde, e o que ela nunca promete.>

### 2. Quando aparece e para quem

<Gatilho, pré-requisitos (permissão, tipo de rede, flag), persona. Se faltar algo, qual estado alternativo é mostrado.>

### 3. Regras de decisão

<O que é dado medido, o que é inferência determinística e o que é interpretação de IA. Cite a regra e aponte
`thresholds_em`; não copie valores.>

### 4. Estados e honestidade

| Estado | Texto / ação mostrada |
|---|---|
| <ex.: sem permissão> | <...> |
| <ex.: parcial / incerto> | <...> |
| <ex.: offline> | <...> |
| <ex.: timeout / erro> | <...> |

Ausência de dado nunca vira zero nem sucesso; timeout nunca é sucesso.

### 5. Próximo passo e confirmação

<Ação concreta oferecida ao usuário e como ele confirma que melhorou.>

### 6. Fora de escopo, status e flag

<Status (entregue/draft) e versão em que entrou; chave da flag e comportamento quando desligada; o que a feature não faz.>

## TÉCNICO

### 7. Mapa de código

| Responsabilidade | Módulo | Arquivo / diretório |
|---|---|---|
| <UI> | <...> | <...> |
| <estado / ViewModel> | <...> | <...> |
| <motor / regra> | <...> | <...> |

### 8. Dados e contratos

<Entradas, saídas, persistência (Room/DataStore), contratos consumidos. Link para `docs/CONTRATOS/`.>

### 9. Eventos e flags

<Eventos de analytics (nome, gatilho) e flags (chave, padrão, efeito). Devem bater com o frontmatter.>

### 10. Falhas e fallback

<Timeout, offline, permissão negada, serviço remoto indisponível, o que a feature faz em cada caso.>

### 11. Testes

<Testes que protegem o comportamento descrito, por caminho. Lacunas explícitas.>

### 12. Riscos

<Riscos técnicos e de produto, dívidas conhecidas, itens marcados como "não verificado".>
