---
title: "Política de Privacidade — SignallQ"
description: "Quais dados o app coleta, como são usados, com quem são compartilhados e quais são os direitos do usuário. Documento público, espelhado pelo signallq-privacy-worker."
type: "referência"
status: "ativo"
owner: "Luiz Giammattey"
last_updated: "2026-10-04"
version: "2.1.0-rascunho"
---

# Política de Privacidade — SignallQ

**Última atualização:** 4 de outubro de 2026
**Vigência:** a partir de [DATA DE PUBLICAÇÃO — definir]

> Alteração desta revisão (outubro de 2026): a política passa a descrever com precisão o envio de dados técnicos ao servidor do SignallQ (o que é enviado, com consentimento, e que fica armazenado), o mapa do Wi-Fi Casa, a verificação de conectividade ao vivo, o Firebase Remote Config e as permissões realmente usadas.
>
> Alteração da revisão anterior (agosto de 2026): a versão anterior afirmava que o aplicativo não exibia anúncios. O SignallQ exibe anúncios do Google AdMob, que são **personalizados** pelo Google, e as seções 1, 2, 3, 6 e 7 foram atualizadas para descrever isso com precisão — incluindo o identificador de publicidade e como controlar a personalização. Uma seção nova sobre consentimento entrou como 4, e as seções seguintes foram renumeradas.

O SignallQ é um aplicativo de diagnóstico de conexão à internet para Android. Esta política descreve quais dados são coletados, como são usados, com quem são compartilhados e quais são os seus direitos como usuário.

---

## 1. Dados coletados e finalidade

O SignallQ coleta dados técnicos de conectividade para fins de diagnóstico. Não coletamos seu nome, e-mail ou endereço. Os anúncios exibidos no aplicativo, porém, usam o **identificador de publicidade do Android**, descrito abaixo e na seção 3.

### Dados coletados

- **Métricas de rede:** velocidade de download e upload, latência, jitter, perda de pacotes e bufferbloat.
- **Informações de Wi-Fi:** SSID, intensidade de sinal (RSSI), frequência de banda e canal.
- **Informações de rede móvel:** tecnologia (4G/5G), intensidade de sinal (RSRP/RSRQ/SINR) e operadora.
- **Dispositivos na rede local:** identificados por varredura da rede local (ARP, mDNS e SSDP/UPnP; somente nome, tipo e endereço MAC, nunca conteúdo de tráfego). Essa lista fica apenas no aparelho.
- **Mapa do Wi-Fi Casa:** nome do mapa, rótulo dos cômodos, posição no mapa, intensidade de sinal e banda. Fica **apenas no aparelho**.
- **Verificação de conectividade ao vivo:** enquanto o app está aberto, testa a cada poucos segundos o alcance do roteador, do DNS e de servidores públicos (Google, Cloudflare e Quad9). O resultado fica só no aparelho e não é enviado ao SignallQ.
- **Histórico de medições:** armazenado localmente no dispositivo do usuário.
- **Credenciais do modem:** armazenadas localmente com criptografia, usadas para acesso ao painel do modem quando configurado pelo usuário.
- **Identificador de publicidade (Advertising ID):** identificador do aparelho, redefinível por você nas configurações do Android, usado pelo Google AdMob para escolher e medir os anúncios. Não é coletado nem armazenado pelo SignallQ — quem o usa é o SDK do Google, dentro do aplicativo.

### Dados NÃO coletados

O SignallQ **não** coleta: nome, e-mail, endereço, localização GPS, contatos, fotos, arquivos nem histórico de navegação. A permissão de localização do Android é usada apenas para que o sistema libere a leitura do nome (SSID) e do canal do Wi-Fi.

O aplicativo **não envia** ao AdMob nenhum valor de medição, nenhum resultado ou conclusão do seu diagnóstico, nome de rede Wi-Fi (SSID), endereço MAC, endereço IP nem o texto do laudo — só o assunto da tela. Isso é diferente de dizer que o anúncio não é personalizado: o Google personaliza a partir do que ele já sabe do seu aparelho e da sua conta, não a partir do que o SignallQ mede.

---

## 2. Como os dados são usados

- Exibição de diagnóstico local no próprio dispositivo.
- Envio ao motor de inteligência artificial para geração de laudo técnico de conectividade.
- Com o seu consentimento, envio de dados técnicos ao servidor do SignallQ para análise de qualidade, suporte e melhoria do produto (seção 3).
- Monitoramento periódico em segundo plano para alertas de queda de qualidade.
- Exibição de anúncios para sustentar a gratuidade do aplicativo. O SignallQ informa ao AdMob apenas o assunto da tela em que o anúncio aparece; a personalização em si é feita pelo Google.

O pedido enviado ao motor de IA para gerar o laudo é processado em tempo real. Já os dados técnicos enviados ao servidor do SignallQ com o seu consentimento são armazenados, conforme descrito nas seções 3 e 5.

---

## 3. Compartilhamento com terceiros

Os dados de diagnóstico, sem nome, e-mail ou outra identificação pessoal, são enviados a servidores hospedados na Cloudflare e operados pelo próprio desenvolvedor do SignallQ, para duas finalidades:

- **Laudo de IA:** as métricas da medição são enviadas ao motor de IA, que devolve o laudo.
- **Análise de qualidade e suporte (somente com o seu consentimento):** resultados das medições (tipo de rede, velocidade, latência, jitter, perda, operadora, nota e principais problemas), o texto do laudo gerado, o uso de IA, o retorno que você dá às recomendações, eventos de uso e erros, junto com o modelo e a versão do aparelho, a versão do app, o nível de bateria e um **identificador anônimo do aparelho** gerado pelo app. O nome da rede Wi-Fi (SSID), o endereço MAC e o endereço IP não fazem parte desse envio. Sem consentimento, nada disso é enviado, e você pode revogá-lo em **Ajustes → Privacidade**.

Além disso, o app utiliza:

- **Firebase Analytics:** coleta de eventos anônimos de uso (telas visitadas, ações realizadas), mediante consentimento. Nenhum dado pessoal é vinculado a esses eventos.
- **Firebase Remote Config:** o app baixa configurações remotas (por exemplo, quais recursos e anúncios estão ativos). Não envia dados seus para isso.
- **Firebase Crashlytics:** coleta automática de relatórios de falha (crash reports) anônimos para melhoria da estabilidade do app.
- **Google AdMob:** o SignallQ exibe anúncios para sustentar a gratuidade do aplicativo. Os anúncios são **personalizados pelo Google**, que usa o identificador de publicidade do seu aparelho e os dados que ele já possui.

  O SignallQ acrescenta a isso **um único sinal**: o assunto da tela em que o anúncio aparece — por exemplo, "resultado de teste de velocidade". Nada mais sai do aparelho para o AdMob. **Nenhum valor de medição, resultado ou conclusão do seu diagnóstico, nome de rede Wi-Fi (SSID), endereço MAC, endereço IP ou o texto do laudo é enviado.** Consulte a [política de privacidade do Google](https://policies.google.com/privacy).

Nenhum dado é vendido ou alugado pelo SignallQ, e nós não construímos perfil de comportamento nem compartilhamos dados de diagnóstico com anunciantes. O Google, por sua vez, usa o identificador de publicidade para personalizar anúncios — é isso que a seção 4 explica como controlar.

---

## 4. Consentimento para anúncios

Antes de qualquer anúncio ser solicitado, o SignallQ usa a **User Messaging Platform (UMP)** do Google para verificar se o seu consentimento é necessário na sua região e, quando for, apresentar o formulário correspondente. Enquanto não houver resposta, ou se você recusar, o aplicativo **não solicita anúncios** — não se trata apenas de ocultar o anúncio da tela.

Fora dessas regiões — no Brasil, por exemplo — a legislação não exige o formulário, e os anúncios são solicitados sem ele.

Em qualquer região, você encontra **Privacidade → Preferências de anúncios** dentro do aplicativo. Onde a UMP tem formulário, o item o abre; onde não tem, ele leva às configurações de anúncios do Android, onde é possível **limitar a personalização** e **redefinir ou excluir o identificador de publicidade**. O SignallQ continua funcionando integralmente com a personalização desligada.

---

## 5. Armazenamento e segurança

- **Dados locais:** o histórico de medições é armazenado no dispositivo do usuário em banco de dados local. Credenciais do modem são armazenadas com criptografia. Todos os dados locais podem ser apagados pelo usuário a qualquer momento via configurações do app ou pela desinstalação.
- **Dados enviados ao servidor:** os dados técnicos enviados com o seu consentimento (seção 3) ficam armazenados em banco de dados na Cloudflare por [PRAZO DE RETENÇÃO — definir]. Para pedir a exclusão dos dados associados ao seu aparelho, use o contato da seção 9, informando o identificador anônimo exibido em Ajustes → Privacidade [CONFIRMAR se o app exibe esse identificador; caso contrário, remover esta frase].
- **Infraestrutura:** o servidor de processamento de IA opera na infraestrutura da Cloudflare, sujeita à [política de privacidade da Cloudflare](https://www.cloudflare.com/privacypolicy/).
- **Firebase:** os dados de analytics e crash são processados pelo Google Firebase conforme a [política de privacidade do Google](https://policies.google.com/privacy).

---

## 6. Permissões solicitadas

| Permissão | Finalidade | O que NÃO faz |
|---|---|---|
| **ACCESS_FINE_LOCATION** | Necessária pelo sistema Android para leitura do SSID e canal Wi-Fi | Não rastreia localização GPS |
| **READ_PHONE_STATE** | Leitura de métricas de sinal celular (RSRP/RSRQ/SINR) em redes 4G/5G | Não acessa chamadas, SMS ou contatos |
| **ACCESS_COARSE_LOCATION** | Exigida pelo Android junto com a localização precisa para ler o Wi-Fi | Não rastreia localização GPS |
| **NEARBY_WIFI_DEVICES** (declarada sem derivar localização) | Descobrir dispositivos Wi-Fi próximos no Android 13+ | Não usada para deduzir onde você está |
| **POST_NOTIFICATIONS** | Avisar sobre quedas de qualidade detectadas pelo monitoramento periódico | — |
| **INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE** | Realizar as medições e ler o estado da conexão | — |
| **CHANGE_WIFI_MULTICAST_STATE** | Descobrir dispositivos na rede local por mDNS | Não acessa o conteúdo do tráfego |
| **com.google.android.gms.permission.AD_ID** | Acesso ao identificador de publicidade, usado pelo Google AdMob para escolher e medir anúncios. Vem do SDK do Google, não é pedida em tela | Não identifica você pessoalmente e pode ser redefinida ou excluída por você nas configurações do Android |
| **ACCESS_ADSERVICES_TOPICS / ACCESS_ADSERVICES_AD_ID / ACCESS_ADSERVICES_ATTRIBUTION** | APIs de anúncios do Android (Privacy Sandbox), usadas pelo SDK do Google para personalizar e medir anúncios com interesses inferidos pelo sistema. Vêm do SDK, não são pedidas em tela | O SignallQ não lê nem armazena esses interesses; quem os usa é o Google |

---

## 7. Direitos do usuário (LGPD)

Em conformidade com a Lei Geral de Proteção de Dados (Lei 13.709/2018), você pode a qualquer momento:

- **Acessar** seus dados armazenados localmente diretamente no app (tela de Histórico).
- **Corrigir** dados que considere incorretos (configurações do modem).
- **Excluir** todo o histórico de medições salvo no aparelho pelo app (Ajustes > Limpar histórico) ou desinstalando o aplicativo.
- **Revogar** permissões do app nas configurações do sistema Android.
- **Solicitar informações** sobre o tratamento de dados pelo e-mail de contato abaixo.
- **Portar** seus dados: o histórico fica no seu dispositivo; para os dados enviados ao servidor, solicite pelo contato da seção 9.

O histórico de diagnósticos fica no seu aparelho, então boa parte desses direitos é atendida pela própria forma como o app funciona. Os dados técnicos enviados ao servidor com o seu consentimento (seção 3) são a exceção: você pode revogar o consentimento a qualquer momento e solicitar a exclusão pelo contato da seção 9.

O identificador de publicidade é a exceção, e vale dizer com clareza: ele é um identificador do seu aparelho, tratado pelo Google. Você o controla nas configurações do Android — pode limitar a personalização, redefini-lo ou excluí-lo — e o caminho está em **Privacidade → Preferências de anúncios** dentro do aplicativo.

---

## 8. Menores de idade

O SignallQ não é direcionado a menores de 13 anos e não coleta conscientemente dados de crianças.

---

## 9. Contato

Dúvidas, solicitações ou outros assuntos relacionados à privacidade:

**E-mail:** giammattey.luiz@gmail.com
**Desenvolvedor:** Luiz Giammattey — SignallQ

---

## 10. Alterações nesta política

Esta política pode ser atualizada periodicamente. A data de última atualização está indicada no topo do documento. O uso continuado do app após uma alteração implica aceitação da nova versão.
