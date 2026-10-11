"use client";

import { FeatureMarketingLayout } from "@/components/FeatureMarketingLayout";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";
import { SITE_ORIGIN } from "@/lib/routeMetadata";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Qualidade do Sinal Wi-Fi no SignallQ Android",
  url: `${SITE_ORIGIN}/sinal-wifi`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Android",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

const FAQ_ITEMS: [string, string][] = [
  [
    "O que significa a medição de sinal em dBm?",
    "dBm (decibel-miliwatt) é a unidade técnica real da potência do sinal de rádio. Como é uma escala logarítmica negativa, valores mais próximos de zero são melhores: -40 a -60 dBm é excelente; de -65 a -75 dBm é regular; abaixo de -80 dBm ocorrem quedas constantes.",
  ],
  [
    "Como o aplicativo detecta múltiplos nós Wi-Fi?",
    "Em redes com repetidores, roteadores secundários ou sistemas Wi-Fi Mesh, o SignallQ identifica os diferentes pontos de acesso emitindo o mesmo nome de rede (SSID) e mostra a qual nó seu aparelho está conectado.",
  ],
  [
    "Por que as barrinhas do celular enganam?",
    "As três ou quatro barras do sistema operacional não mostram se o sinal está em 2.4 GHz ou 5 GHz, nem se a taxa negociada caiu para poucos megabits. O SignallQ revela a potência real em dBm e a velocidade de enlace da placa de rede.",
  ],
  [
    "Esta ferramenta funciona no navegador do computador?",
    "Não. Leituras reais de nível de sinal dBm e nós de rede exigem permissão direta de hardware e localização do Android, algo bloqueado em qualquer navegador web por segurança.",
  ],
];

export function SinalWifiContent() {
  return (
    <FeatureMarketingLayout
      eyebrow="Sinal Wi-Fi em dBm · Exclusivo no Android"
      title="Entenda a qualidade do sinal Wi-Fi ao seu redor."
      intro="O SignallQ para Android mede a potência real do rádio em dBm, identifica os nós detectados na sua rede e mostra a velocidade de conexão negociada com o roteador."
      screenshot={APPROVED_SCREENSHOTS.sinalWifi}
      benefits={[
        {
          title: "Potência real de rádio",
          description:
            "Abandone as barrinhas genéricas e saiba exatamente quantos dBm seu aparelho está recebendo em cada cômodo.",
        },
        {
          title: "Detecção de nós e repetidores",
          description:
            "Descubra se o seu aparelho está conectado ao roteador principal ou a um repetidor distante com sinal fraco.",
        },
        {
          title: "Taxa de enlace (Link Speed)",
          description:
            "Veja a velocidade máxima negociada entre o hardware do celular e a antena do roteador no momento.",
        },
      ]}
      howToUseSteps={[
        {
          step: 1,
          title: "Abra Wi-Fi e rede móvel",
          description:
            "No app SignallQ, entre na ferramenta de diagnóstico de rádio.",
        },
        {
          step: 2,
          title: "Acesse a aba Sinal",
          description:
            "O app faz a leitura da frequência (2.4 GHz ou 5 GHz), geração do Wi-Fi e nível de recepção.",
        },
        {
          step: 3,
          title: "Caminhe pela residência",
          description:
            "Monitore o sinal enquanto se afasta do roteador para encontrar os pontos cegos da sua casa.",
        },
      ]}
      resultsAndLimitations={{
        title: "Interpretação e limites da leitura de sinal",
        description:
          "O aplicativo coleta os dados expostos pela API de Wi-Fi do sistema operacional Android.",
        items: [
          {
            label: "Estrutura Estimada de Nós",
            explanation:
              "A contagem e organização de nós da rede é estimada pelo aplicativo com base nos dados de fabricante (OUI), intensidade e banda anunciados no ar pelos pontos de acesso.",
          },
          {
            label: "Velocidade de Link",
            explanation:
              "Representa a capacidade teórica da comunicação física de rádio entre o celular e a antena do roteador, e não o tráfego de dados na internet.",
          },
        ],
        limitationNote:
          "A estrutura de nós exibida é uma estimativa inteligente baseada nas transmissões recebidas e não substitui uma topologia de rede física comprovada via protocolo de gerenciamento do fabricante. As leituras dependem das permissões de localização do Android concedidas ao app.",
      }}
      faqItems={FAQ_ITEMS}
      relatedFeatureLinks={[
        { title: "Canais do Wi-Fi", href: "/canais-wifi", badge: "Rádio" },
        { title: "Dispositivos conectados", href: "/quem-esta-na-minha-rede", badge: "Rede Local" },
        { title: "Teste de velocidade", href: "/teste-de-velocidade", badge: "Web" },
      ]}
      jsonLd={JSON_LD}
    />
  );
}
