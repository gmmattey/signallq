---
title: "Cobertura do protótipo — SignallQ Android 2.0"
description: "Quais telas e fluxos da Jornada Android 2.0 o protótipo cobre, e onde ele diverge do app real v1.0.9."
type: "funcional"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Cobertura do protótipo SignallQ Android 2.0

Inventário do protótipo (histórico do épico #1647) conferido contra o app v1.0.9 em 2026-10-04.
O protótipo contém 42 destinos navegáveis. Em divergência, vale o app (`docs/01-funcional.md`).

## Divergências conhecidas do app real (v1.0.9)

- **Fora do protótipo:** WiFi Casa (mapeamento por cômodo com Antes×Depois, `WifiCasaScreen`), status de
  conectividade ao vivo na trilha da Início e sonda UDP do Modo gamer — entregues depois do protótipo.
- **Só no protótipo:** destino "Mais" — o app não tem essa tela; Ajustes e demais itens abrem pelo perfil
  na barra superior.
- A barra inferior do app tem 4 abas (Início, Velocidade, Histórico, Ferramentas), como abaixo.

## Navegação principal

- Início — veredito atual, trilha da conexão e entrada do diagnóstico guiado.
- Velocidade — medição manual como fonte de evidência.
- Histórico — diagnósticos, medições e comparações anteriores.
- Ferramentas — acesso direto ao hub completo, sem ficar escondido em “Mais”.
- Perfil — acesso pela barra superior a Ajustes, Privacidade, Novidades, Ajuda, Termos e Sobre.

Todas as 39 telas de uso diário são alcançáveis a partir da Início. Boas-vindas, Permissões e LGPD
são contextuais ao primeiro acesso e, por isso, não aparecem na navegação cotidiana.

## Jornada principal

- Início
- Seleção de sintoma
- SignallQ Assist — pergunta breve para ajustar a análise ao caso informado
- Análise em andamento
- Resultado do diagnóstico
- Orientação
- Nova verificação
- Comparação antes e depois
- Evidência insuficiente
- Erro recuperável

## Medição e diagnóstico técnico

- Velocidade
- Medição em andamento
- Resultado da medição
- Detalhes técnicos
- Laudo compartilhável

## Sinal e infraestrutura

- Redes Wi-Fi
- Canais Wi-Fi
- Sinal móvel
- Sinal Wi-Fi em tempo real
- Dispositivos conectados
- Equipamento de internet
- Detalhe de rede
- Detalhe de dispositivo
- Acesso ao equipamento

## Ferramentas

- Hub de ferramentas
- Ping
- DNS
- Monitoramento
- Modo gamer
- Contato da operadora

## Histórico, ajustes e informações

- Histórico
- Mais
- Ajustes
- Privacidade
- Termos de uso
- Novidades
- Dados locais
- Ajuda e suporte
- Sobre o SignallQ

## Primeiro acesso

- Boas-vindas
- Permissões opcionais
- Consentimento LGPD

## Estados que devem ser revisados na próxima rodada visual

Cada recurso crítico ainda deve receber suas variações de carregamento, vazio, permissão negada,
offline, rede móvel, falha parcial e recurso temporariamente indisponível. Essas variações não devem
virar destinos permanentes na navegação: serão controladas dentro das próprias telas.

## Regra da trilha de conexão para mesh

- Exibir o nó “mesh” na trilha principal somente quando o motor retornar `NO_MESH` com confiança alta.
- `SISTEMA_MESH_PROVAVEL`, confiança média/baixa ou sinais conflitantes não autorizam uma afirmação
  visual; nesses casos, a possibilidade aparece apenas nos detalhes técnicos.
- SSID igual, isoladamente, nunca é evidência suficiente.
- O nó é opcional: quando não há evidência confiável, a trilha liga equipamento diretamente ao Wi-Fi.
