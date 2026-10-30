---
description: Checklist e padrões de implementação Jetpack Compose para o SignallQ — estrutura de Screen, ViewModel, StateFlow, estados visuais e anti-padrões.
---

# Padrões Compose

Use antes de implementar ou revisar Compose. A skill descreve estrutura; `design-check` valida tokens/visual depois.

## Estrutura

- Screen recebe `uiState` e callbacks; lógica de negócio fica fora da composição.
- ViewModel expõe estado imutável (`StateFlow<UiState>` ou padrão já adotado no módulo).
- Não exponha `MutableStateFlow`/`MutableState` publicamente.
- Repository/use case não é chamado diretamente de Composable filho.
- Hilt e dependências seguem o padrão real do projeto; não instancie cliente/repository compartilhado manualmente sem motivo.

## Estados

Toda tela assíncrona relevante considera, quando aplicável:

- loading;
- success;
- error com recuperação;
- empty;
- offline/permission denied quando o domínio exigir.

Não use zero/default visual para esconder dado não carregado ou erro.

## Lifecycle e coroutines

- efeitos Compose têm key coerente com sua identidade;
- operação longa pertence ao owner correto (normalmente ViewModel/domain), não a `GlobalScope`;
- coleta de Flow em UI deve respeitar lifecycle;
- cancelamento deve liberar recursos e impedir estado antigo de sobrescrever estado novo.

## Componentes

Antes de criar componente reutilizável, use `inventario`/`verificar-modulo` quando o caso justificar. Prefira parâmetros explícitos e componentes focados.

## Dependências

- `feature*` não deve depender informalmente de outro `feature*` sem arquitetura que justifique;
- regra compartilhada pertence ao módulo dono do domínio;
- mudança entre módulos/contratos que acione o gate do `AGENTS.md` passa pelo Camillo antes da implementação.

## Visual

Use tokens do SignallQ; não hardcode cor/tipografia por conveniência. Rode `design-check` em mudança visual relevante.

## Responsabilidades

Camillo normalmente aplica esta skill na implementação Android, com Priscila definindo os estados da tela. Renan revisa regressão/lifecycle. A skill em si não pertence a um agente nem define modelo de IA.
