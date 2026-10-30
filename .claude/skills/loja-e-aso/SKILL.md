---
name: loja-e-aso
description: Diretrizes de ASO, listagem da Google Play, títulos, screenshots e conformidade (Data Safety, política de privacidade) do SignallQ.
---

# Skill: loja-e-aso

Orienta Marcelo, Priscila e Renan na preparação e revisão da página do SignallQ na loja. Publicar é decisão do Luiz; esta skill prepara e confere, não publica. Complementa `growth-check`.

## 1. Metadados e posicionamento
- **Título e descrição curta:** diretos, com a palavra-chave de busca sem parecer spam. O produto é diagnóstico de conectividade, não "só um speed test".
- **Descrição longa:** as primeiras linhas (antes do "ver mais") dizem o problema resolvido e o diferencial: entender, diagnosticar, resolver, confirmar.
- **Promessa honesta:** nada de "internet mais rápida", "garantido" ou melhora de velocidade que o app não entrega. O app diagnostica e orienta.

## 2. Criativos e screenshots
- Imagens autorais, 1:1 com o produto real rodando.
- Manchetes curtas, legíveis em miniatura.
- Sem molduras distorcidas nem elemento que viole as diretrizes da loja. Gerador de molduras: `scripts/gerar-molduras-playstore.py`.

## 3. Conformidade
- **Data Safety** e política de privacidade alinhadas exatamente com o que o app coleta (Firebase Analytics, Crashlytics, Workers, telemetria).
- Permissões (localização para Wi-Fi, telefone) explicadas e justificadas.
- Se houver compra, assinatura ou publicidade: consentimento e termos claros.
- Confira contra o AAB/APK real, não contra a memória.

## 4. Antes de qualquer publicação
Verifique com `checar-release`. Release, promoção de trilha e alteração no Play Console só com autorização explícita do Luiz.
