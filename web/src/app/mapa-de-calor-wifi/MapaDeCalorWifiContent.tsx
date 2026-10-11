"use client";

import { FeatureMarketingLayout } from "@/components/FeatureMarketingLayout";
import { SITE_ORIGIN } from "@/lib/routeMetadata";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Mapa de calor do Wi-Fi e cobertura residencial no SignallQ Android",
  url: `${SITE_ORIGIN}/mapa-de-calor-wifi`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Android",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

const FAQ_ITEMS: [string, string][] = [
  [
    "Como funciona o mapeamento cômodo por cômodo no app?",
    "Na funcionalidade WiFi Casa, você caminha pela residência e registra a intensidade de sinal (dBm) em cada ambiente (sala, quartos, escritório). O app compara a perda de potência através de portas e paredes.",
  ],
  [
    "O aplicativo ajuda a encontrar o melhor lugar para o roteador?",
    "Sim. O principal objetivo é permitir testes 'antes e depois': você mede a cobertura na posição original, move o roteador ou instala um repetidor, e refaz a leitura para confirmar se os pontos cegos foram eliminados.",
  ],
  [
    "Por que não há captura de tela exibida nesta página?",
    "Mantemos um padrão rigoroso de integridade visual: usamos apenas capturas reais do aplicativo Android e não exibimos gráficos fictícios. A captura oficial desta ferramenta está sendo gerada para a próxima atualização da galeria.",
  ],
];

export function MapaDeCalorWifiContent() {
  return (
    <FeatureMarketingLayout
      eyebrow="WiFi Casa · Exclusivo no Android"
      title="Mapeie visualmente os pontos cegos do seu Wi-Fi."
      intro="O recurso WiFi Casa do SignallQ permite registrar a qualidade do sinal em cada cômodo da sua casa, identificando áreas de sombra e validando se a mudança de posição do roteador realmente funcionou."
      screenshotPendingNotice="Esta funcionalidade está disponível no app Android (menu Ferramentas > WiFi Casa). A captura oficial de alta resolução está em produção e será incluída assim que auditada."
      benefits={[
        {
          title: "Diagnóstico cômodo por cômodo",
          description:
            "Descubra exatamente onde a potência do sinal decai e onde ocorrem quedas de conexão na sua casa.",
        },
        {
          title: "Comparação antes e depois",
          description:
            "Comprove se trocar o roteador de tomada ou reposicionar uma antena melhorou a cobertura no quarto ou escritório.",
        },
        {
          title: "Sem equipamentos caros",
          description:
            "Faça um levantamento de cobertura residencial diretamente com o seu smartphone Android, sem precisar de técnicos.",
        },
      ]}
      howToUseSteps={[
        {
          step: 1,
          title: "Acesse WiFi Casa",
          description:
            "No menu Ferramentas do SignallQ Android, toque em WiFi Casa para iniciar o mapeamento residencial.",
        },
        {
          step: 2,
          title: "Meça em cada ambiente",
          description:
            "Vá até cada cômodo e faça a leitura da potência do sinal e velocidade de link negociada.",
        },
        {
          step: 3,
          title: "Compare as medições",
          description:
            "Veja a diferença entre os ambientes e decida onde posicionar repetidores ou pontos mesh.",
        },
      ]}
      resultsAndLimitations={{
        title: "Interpretação e limites do mapeamento residencial",
        description:
          "O mapeamento reflete a recepção de rádio da antena do seu smartphone nos cômodos visitados.",
        items: [
          {
            label: "Atenuação por Obstáculos",
            explanation:
              "Paredes de alvenaria maciça, espelhos, azulejos e estruturas metálicas reduzem significativamente o alcance da frequência de 5 GHz.",
          },
          {
            label: "Sensibilidade do Aparelho",
            explanation:
              "Modelos de celulares diferentes possuem ganhos de antena distintos. É recomendável realizar as medições com o mesmo aparelho.",
          },
        ],
        limitationNote:
          "O mapeamento cômodo por cômodo fornece uma referência comparativa de potência de rádio. Ele não substitui levantamentos com equipamentos de rádio de nível industrial nem gera plantas baixas arquitetônicas automáticas.",
      }}
      faqItems={FAQ_ITEMS}
      relatedFeatureLinks={[
        { title: "Sinal Wi-Fi em dBm", href: "/sinal-wifi", badge: "Potência" },
        { title: "Canais Wi-Fi", href: "/canais-wifi", badge: "Rádio" },
        { title: "Teste de velocidade", href: "/teste-de-velocidade", badge: "Web" },
      ]}
      jsonLd={JSON_LD}
    />
  );
}
