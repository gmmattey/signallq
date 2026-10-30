---
name: reconhecimento-equipamento-rede
description: Metodologia read-only para mapear capacidades e campos expostos por interfaces administrativas de equipamentos de rede local com credenciais autorizadas.
---

# Reconhecimento de equipamento de rede

Use para documentar um equipamento específico (ONT, roteador, AP, mesh) antes de integrar suporte ao SignallQ.

## Limites de segurança

- **Read-only.** Não alterar configuração, rebootar, resetar ou atualizar firmware.
- **Sem bypass/exploit/brute force.** Use somente credencial explicitamente fornecida pelo dono do equipamento.
- Uma falha de login encerra a tentativa; não teste variações de senha.
- Credenciais ficam apenas no processo/variável de ambiente e nunca entram em log, commit, fixture ou documentação.
- Não faça scan de terceiros nem de equipamento que o usuário não controla/autorizou.

## Procedimento

1. identifique fabricante, modelo e firmware;
2. documente o fluxo de autenticação necessário para leitura;
3. mapeie somente endpoints/campos relevantes ao diagnóstico;
4. registre tipo, unidade, semântica e disponibilidade de cada campo;
5. compare com drivers/adapters já existentes no repo antes de criar suporte novo;
6. produza field-map em `docs/technical/` seguindo os exemplos existentes;
7. identifique o que é estável por família e o que é específico do firmware.

## Integração

Reconhecimento não é implementação. Camillo valida a utilidade diagnóstica dos campos e implementa a integração Android quando local; Renan revisa segurança/regressão.

Se o suporte exigir novo contrato, driver estrutural, API/Worker ou atravessar múltiplos módulos com nova responsabilidade, aplique o gate do Camillo antes de implementar.

## Saída

```text
EQUIPAMENTO: fabricante/modelo/firmware
Autenticação observada: ...
Campos úteis:
- campo — unidade — semântica — endpoint/origem
Compatibilidade com implementação existente: ...
Lacunas: ...
Riscos/limitações: ...
Gate Camillo: SIM/NÃO
Documento field-map: ...
```

A skill não autoriza alteração remota, descoberta em massa, bypass de autenticação ou armazenamento de senha.
