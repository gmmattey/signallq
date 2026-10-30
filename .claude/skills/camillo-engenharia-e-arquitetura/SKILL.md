---
name: camillo-engenharia-e-arquitetura
description: Padrões de arquitetura, motor de diagnóstico determinístico, contratos, Workers e implementação full-stack (Android, Windows, Cloudflare) do Camillo, incluindo o gate arquitetural.
---

# Skill: camillo-engenharia-e-arquitetura

Guia o **Camillo** na engenharia de ponta a ponta do SignallQ: do motor de diagnóstico à montagem das telas. Procedimentos específicos de domínio estão em `motor-diagnostico`, `regras-diagnostico-rede`, `regras-android`, `padroes-compose` e `cloudflare-d1-console`.

## 1. Fronteiras claras
- **Motor (core):** medição e regras de diagnóstico não conhecem Compose, HTML nem layout.
- **Telas:** observam estado reativo (`StateFlow`, props) e despacham intenções. Nunca calculam métrica nem abrem rede avulsa.
- **Features:** uma não depende de outra. Composição em `:app` ou por contrato normalizado em `:core`.
- **Contratos compartilhados:** modelos tipados, imutáveis e testáveis. Mudou o contrato, mudou o teste e a doc (`docs/CONTRATOS/`).
- **Threshold em um lugar só.**

## 2. Rigor diagnóstico
- **Três camadas:** dado medido, inferência determinística, interpretação de IA. IA não substitui regra determinística nem inventa causa.
- **Verdade para quem usa:** falha, timeout, desconexão ou dado ausente **nunca** viram zero, nota inventada ou sucesso silencioso.
- **Cada medição** tem método, timeout explícito, cancelamento gracioso e tratamento de perda.
- **Local vs externo:** separe Wi-Fi/roteador/LAN de WAN/operadora/rota; só aponte com evidência que isole.

## 3. Gate arquitetural
Antes do código, feche por escrito (na conversa; em `.agents/architecture-plan.md` se precisa sobreviver à sessão) quando houver: múltiplos módulos com contrato novo, API, integração app ↔ Worker, migração de persistência, mudança no motor central, nova dependência estrutural, auth ou segurança sistêmica. Conteúdo, só o que se aplica: problema, arquitetura atual, decisão e alternativa rejeitada, contratos, fluxo de dados, falha/timeout/fallback, segurança e privacidade, testes, riscos, não-objetivos. Tarefa trivial não gera plano.

## 4. Plataforma
- **Android:** API level, permissões, Doze, WorkManager, OEM, lifecycle e fallback. Não prometa o que a plataforma não entrega.
- **Windows:** o host Rust é dono de coleta e medição; a UI só consome comandos e eventos versionados.
- **Workers:** custo e exposição pública passam pelo Luiz.

## 5. Disciplina de código
- **Mudança cirúrgica:** altere só o necessário. Não refatore código alheio sem motivo.
- **Proteja o WIP:** leia `git status` e preserve o que estava em andamento.
- **Auto-revisão:** antes de passar ao Renan, revise o diff, rode os testes locais e liste arquivos tocados e limitações.
