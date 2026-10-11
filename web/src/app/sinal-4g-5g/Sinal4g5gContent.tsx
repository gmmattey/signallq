"use client";

import { FeatureMarketingLayout } from "@/components/FeatureMarketingLayout";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";
import { SITE_ORIGIN } from "@/lib/routeMetadata";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Diagnóstico de Sinal 4G e 5G no SignallQ Android",
  url: `${SITE_ORIGIN}/sinal-4g-5g`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Android",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

const FAQ_ITEMS: [string, string][] = [
  [
    "O que são os parâmetros RSRP e RSRQ?",
    "RSRP (Reference Signal Received Power) mede a potência do sinal de rádio celular que chega ao seu smartphone. RSRQ (Reference Signal Received Quality) mede a qualidade e o nível de ruído/interferência do sinal da torre. Valores baixos explicam chamadas caindo e vídeos engasgando mesmo quando o ícone 5G está aceso.",
  ],
  [
    "Por que o sinal mostra 5G mas a navegação continua lenta?",
    "O ícone 5G no topo da tela pode indicar uma conexão 5G NSA (Non-Standalone), que compartilha a infraestrutura de rádio com o 4G, ou que o sinal está fraco (-115 dBm ou pior). O SignallQ identifica a qualidade real e avisa quando o sinal é insuficiente.",
  ],
  [
    "O navegador consegue medir o sinal da torre móvel?",
    "Não. Leituras de telemetria celular (RSRP, RSRQ, operadora de rede e banda móvel) são restritas ao sistema operacional Android e exigem permissões de telefonia e localização. Sites web não têm acesso a essas informações.",
  ],
  [
    "Como usar o SignallQ para achar o melhor ponto da casa para usar o celular?",
    "Abra a tela de Sinal Móvel no app e observe os valores de RSRP atualizados em tempo real enquanto caminha até perto de janelas ou cômodos diferentes.",
  ],
];

export function Sinal4g5gContent() {
  return (
    <FeatureMarketingLayout
      eyebrow="Rede Móvel · Exclusivo no Android"
      title="Saiba o que a intensidade do sinal móvel significa."
      intro="O SignallQ para Android diagnostica o sinal celular em tempo real, informando a tecnologia ativa (4G LTE ou 5G NSA), a qualidade da conexão e parâmetros avançados de torre como RSRP e RSRQ."
      screenshot={APPROVED_SCREENSHOTS.sinalMovel}
      benefits={[
        {
          title: "Diagnóstico real de 5G e 4G",
          description:
            "Descubra se o seu 5G está entregando desempenho de ponta ou operando com sinal no limite da estabilidade.",
        },
        {
          title: "Parâmetros de torre (RSRP e RSRQ)",
          description:
            "Acesse indicadores técnicos de potência e ruído de rádio diretamente do modem do smartphone.",
        },
        {
          title: "Experiência esperada em palavras",
          description:
            "O app traduz números complexos em orientações claras: se a rede suporta chamadas de vídeo ou se oscilará.",
        },
      ]}
      howToUseSteps={[
        {
          step: 1,
          title: "Abra a ferramenta no app",
          description:
            "No SignallQ Android, acesse 'Wi-Fi e rede móvel' e selecione a aba 'Móvel'.",
        },
        {
          step: 2,
          title: "Verifique o Chip ativo",
          description:
            "Se o seu aparelho tem Dual SIM, o aplicativo permite visualizar a telemetria do chip de dados selecionado.",
        },
        {
          step: 3,
          title: "Avalie a qualidade",
          description:
            "Veja a tecnologia conectada, o nível de RSRP em dBm e a estimativa de estabilidade para chamadas e streaming.",
        },
      ]}
      resultsAndLimitations={{
        title: "Interpretação e limites da telemetria móvel",
        description:
          "Os dados são coletados diretamente através da TelephonyManager API do Android.",
        items: [
          {
            label: "RSRP (Potência Recebida)",
            explanation:
              "Acima de -80 dBm é excelente; de -90 a -105 dBm é bom/moderado; abaixo de -115 dBm o sinal é fraco e suscetível a quedas.",
          },
          {
            label: "RSRQ (Qualidade do Sinal)",
            explanation:
              "Acima de -10 dB é muito bom; abaixo de -15 dB indica interferência alta e congestionamento na torre da operadora.",
          },
        ],
        limitationNote:
          "As leituras de sinal móvel dependem exclusivamente do modem do aparelho Android e das permissões de localização e telefonia concedidas. O navegador web não tem suporte para acessar sinais celulares e não deve simular essas medições.",
      }}
      faqItems={FAQ_ITEMS}
      relatedFeatureLinks={[
        { title: "Sinal Wi-Fi em dBm", href: "/sinal-wifi", badge: "Wi-Fi" },
        { title: "Tempo de resposta", href: "/teste-de-ping", badge: "Latência" },
        { title: "Teste de velocidade", href: "/teste-de-velocidade", badge: "Web" },
      ]}
      jsonLd={JSON_LD}
    />
  );
}
