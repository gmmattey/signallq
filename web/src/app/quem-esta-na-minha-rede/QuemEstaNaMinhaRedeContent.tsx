"use client";

import { FeatureMarketingLayout } from "@/components/FeatureMarketingLayout";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";
import { SITE_ORIGIN } from "@/lib/routeMetadata";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Dispositivos conectados na rede local no SignallQ Android",
  url: `${SITE_ORIGIN}/quem-esta-na-minha-rede`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Android",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

const FAQ_ITEMS: [string, string][] = [
  [
    "Como o aplicativo encontra outros aparelhos na rede?",
    "O SignallQ faz uma varredura na sub-rede local (ex: 192.168.1.0/24) utilizando requisições de resolução de nomes, mDNS e consultas de tabela de vizinhança para identificar dispositivos que respondem na rede local.",
  ],
  [
    "O aplicativo garante que mostrará 100% de todos os aparelhos da casa?",
    "Não necessariamente. Dispositivos configurados com firewall ativado, em modo de economia de energia profunda, ou isolados em redes de convidados (Guest Wi-Fi) podem não responder às sondagens. Além disso, sistemas operacionais modernos usam endereços MAC randômicos.",
  ],
  [
    "Posso bloquear um aparelho usando o SignallQ?",
    "O SignallQ é uma ferramenta de auditoria e diagnóstico. Ele identifica quais aparelhos estão ativos e visíveis para você investigar. O bloqueio físico deve ser realizado no painel de controle do seu roteador.",
  ],
  [
    "Por que isso não pode ser feito diretamente no navegador?",
    "Nenhum navegador tem permissão para disparar varreduras de rede local ou inspecionar a tabela ARP por razões rígidas de segurança contra ataques cross-site. O diagnóstico é exclusivo do app Android.",
  ],
];

export function QuemEstaNaMinhaRedeContent() {
  return (
    <FeatureMarketingLayout
      eyebrow="Rede Local · Exclusivo no Android"
      title="Descubra quais aparelhos estão visíveis na sua rede."
      intro="O aplicativo SignallQ realiza uma varredura nos endereços da sua rede local e lista os smartphones, computadores, TVs e dispositivos conectados que respondem ao seu aparelho."
      screenshot={APPROVED_SCREENSHOTS.dispositivosConectados}
      benefits={[
        {
          title: "Identificação de dispositivos ativos",
          description:
            "Veja a lista de aparelhos que estão utilizando a mesma conexão que você no momento da varredura.",
        },
        {
          title: "Endereços IP e fabricantes",
          description:
            "Consulte o endereço IP atribuído, nome de rede do host e informações de identificação do aparelho.",
        },
        {
          title: "Detecção de intrusos potenciais",
          description:
            "Encontre aparelhos desconhecidos consumindo sua internet sem o seu conhecimento.",
        },
      ]}
      howToUseSteps={[
        {
          step: 1,
          title: "Conecte-se ao Wi-Fi",
          description:
            "Certifique-se de que o seu celular Android está conectado na rede que você deseja escanear.",
        },
        {
          step: 2,
          title: "Abra a ferramenta Dispositivos",
          description:
            "No menu Ferramentas do SignallQ, selecione 'Quem está usando sua rede'.",
        },
        {
          step: 3,
          title: "Acompanhe a varredura",
          description:
            "O app lista os nós identificados e destaca qual dispositivo é o seu smartphone e qual é o roteador.",
        },
      ]}
      resultsAndLimitations={{
        title: "Interpretação e limitações da varredura de rede",
        description:
          "O escaneamento identifica o que está visível a partir do ponto de vista do smartphone.",
        items: [
          {
            label: "Dispositivos Visíveis",
            explanation:
              "Aparelhos que responderam ativamente aos protocolos de descoberta da sub-rede local no momento da leitura.",
          },
          {
            label: "Dispositivos Desconhecidos",
            explanation:
              "Equipamentos que respondem na rede mas não expõem um nome amigável de host (ex: câmeras IP, lâmpadas smart ou placas genéricas).",
          },
        ],
        limitationNote:
          "A varredura identifica apenas os aparelhos que respondem na camada de rede local para este celular. Dispositivos com firewall ativo, isolamento de clientes (AP Isolation) ativado no roteador ou em economia de bateria podem não aparecer na listagem.",
      }}
      faqItems={FAQ_ITEMS}
      relatedFeatureLinks={[
        { title: "Canais do Wi-Fi", href: "/canais-wifi", badge: "Rádio" },
        { title: "Sinal Wi-Fi", href: "/sinal-wifi", badge: "dBm" },
        { title: "Teste de velocidade", href: "/teste-de-velocidade", badge: "Web" },
      ]}
      jsonLd={JSON_LD}
    />
  );
}
