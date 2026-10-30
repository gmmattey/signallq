---
description: Procedimento para schema, migration e query D1 do SignallQ Console/Workers, com compatibilidade, rollback e proteção de dados.
---

# Cloudflare D1 Console

Use antes de criar/alterar tabela, coluna, índice, migration ou query estrutural em D1.

## Fontes reais

Localize no componente afetado:

- `schema.sql`;
- `migrations/`;
- `wrangler.toml` e binding real;
- queries/repositories que consomem o schema;
- contratos/documentação em `docs/CONTRATOS/` quando existirem.

Não copie nome de banco ou número de migration desta skill sem confirmar no código atual.

## Migration

Antes de escrever:

1. identifique versão/ordem atual;
2. liste leitores e escritores afetados;
3. prefira migration aditiva quando possível;
4. defina comportamento para registros antigos/null/default;
5. avalie índice e custo de query;
6. considere rollout com app/Worker de versões diferentes;
7. planeje backup/rollback/forward-fix proporcional ao risco;
8. teste em ambiente não produtivo quando disponível.

Mudança destrutiva, rename sem compatibilidade, alteração de chave/relacionamento ou schema consumido por mais de um sistema aciona o gate do Camillo.

## Segurança e privacidade

- não persista segredo em D1;
- nova coluna com dado de usuário exige finalidade e retenção claras;
- confirme contrato de privacidade/telemetria;
- query administrativa não deve ampliar acesso sem autorização correspondente.

## Saída

```text
D1-PLAN
Banco/binding confirmado: ...
Schema atual: ...
Migration proposta: ...
Leitores/escritores afetados: ...
Compatibilidade: ...
Rollback/forward-fix: ...
Privacidade: ...
Testes: ...
Gate Camillo: SIM/NÃO — motivo
```

Camillo responde por Worker, contrato do diagnóstico e gate sistêmico. Renan revisa migration/compatibilidade. A skill não executa migration de produção nem deploy.
