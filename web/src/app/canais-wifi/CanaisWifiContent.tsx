"use client";

import { FeatureMarketingLayout } from "@/components/FeatureMarketingLayout";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";
import { SITE_ORIGIN } from "@/lib/routeMetadata";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Canais de Wi-Fi no SignallQ Android",
  url: `${SITE_ORIGIN}/canais-wifi`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Android",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

const FAQ_ITEMS: [string, string][] = [
  [
    "Como o canal do Wi-Fi interfere na velocidade da internet?",
    "Roteadores operam em canais de rádio específicos. Se você e seus vizinhos utilizam o mesmo canal (como o canal 6 em 2.4 GHz ou canal 40 em 5 GHz), os aparelhos precisam disputar o mesmo espaço no ar, gerando atrasos, perda de pacotes e lentidão.",
  ],
  [
    "O aplicativo altera o canal do roteador sozinho?",
    "Não. Por questões de segurança, a troca de canal é feita nas configurações de administração do seu próprio roteador. O SignallQ analisa o espectro de rádio e aponta qual canal tem menor competição na sua casa.",
  ],
  [
    "Qual a diferença entre canais de 2.4 GHz e 5 GHz?",
    "A faixa de 2.4 GHz tem maior alcance através de paredes, mas possui apenas 3 canais que não se sobrepõem (1, 6 e 11), ficando facilmente saturada em prédios. A faixa de 5 GHz é muito mais rápida e possui dezenas de canais livres, mas tem menor alcance.",
  ],
  [
    "Por que esta ferramenta só existe no app Android?",
    "Nenhum navegador web tem permissão para acessar os dados brutos da placa de rádio Wi-Fi por motivos de segurança e privacidade. O diagnóstico detalhado de canais é uma capacidade exclusiva do app no Android.",
  ],
];

export function CanaisWifiContent() {
  return (
    <FeatureMarketingLayout
      eyebrow="Canais de Wi-Fi · Exclusivo no Android"
      title="Descubra se o canal do seu Wi-Fi está congestionado."
      intro="O aplicativo SignallQ varre as redes vizinhas no ar, calcula o nível de disputa pelo mesmo canal de rádio e recomenda o canal menos congestionado para o seu roteador."
      screenshot={APPROVED_SCREENSHOTS.canaisWifi}
      benefits={[
        {
          title: "Varredura do espectro local",
          description:
            "Identifica quantas redes vizinhas estão disputando espaço com a sua e quais canais estão saturados.",
        },
        {
          title: "Recomendação do melhor canal",
          description:
            "Compara todas as frequências e sugere a alternativa com menor probabilidade de interferência.",
        },
        {
          title: "Separação 2.4 GHz e 5 GHz",
          description:
            "Veja claramente a diferença de ocupação entre a faixa de longo alcance (2.4 GHz) e de alta velocidade (5 GHz).",
        },
      ]}
      howToUseSteps={[
        {
          step: 1,
          title: "Abra o menu Ferramentas",
          description:
            "No app SignallQ para Android, selecione a opção Wi-Fi e rede móvel.",
        },
        {
          step: 2,
          title: "Acesse a aba Canais",
          description:
            "O aplicativo realiza uma leitura das redes detectáveis pelo rádio do seu celular.",
        },
        {
          step: 3,
          title: "Veja a recomendação",
          description:
            "Confira se o seu canal atual está livre, moderado ou congestionado e qual canal alternativo foi sugerido.",
        },
      ]}
      resultsAndLimitations={{
        title: "Interpretação e limites da análise de canais",
        description:
          "O diagnóstico baseia-se nas ondas de rádio recebidas pelo celular onde você está no momento.",
        items: [
          {
            label: "Canal Recomendado",
            explanation:
              "Canal detectado com o menor índice de tráfego e sobreposição de outras redes no momento da leitura.",
          },
          {
            label: "Seu Canal Atual",
            explanation:
              "Classificado como Livre, Moderado ou Congestionado com base na quantidade de redes vizinhas ativas.",
          },
        ],
        limitationNote:
          "Trocar o canal do roteador ajuda a reduzir interferências locais de Wi-Fi, mas não resolve lentidão causada pela infraestrutura externa da operadora. A disponibilidade de canais como o 149 depende do modelo do seu roteador e da regulamentação regional.",
      }}
      faqItems={FAQ_ITEMS}
      relatedFeatureLinks={[
        { title: "Sinal Wi-Fi em dBm", href: "/sinal-wifi", badge: "Hardware" },
        { title: "Dispositivos na rede", href: "/quem-esta-na-minha-rede", badge: "Rede Local" },
        { title: "Teste de velocidade", href: "/teste-de-velocidade", badge: "Web" },
      ]}
      jsonLd={JSON_LD}
    />
  );
}
