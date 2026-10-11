"use client";

import { FeatureMarketingLayout } from "@/components/FeatureMarketingLayout";
import { SITE_ORIGIN } from "@/lib/routeMetadata";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Relatório de diagnóstico em PDF no SignallQ Android",
  url: `${SITE_ORIGIN}/relatorio-pdf`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Android",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

const FAQ_ITEMS: [string, string][] = [
  [
    "Para que serve o relatório em PDF do SignallQ?",
    "Ele reúne o histórico auditado de medições, latências sob carga, diagnósticos do canal de rádio e do modem da operadora em um arquivo formatado, ideal para protocolar reclamações no suporte técnico ou na Anatel com dados técnicos irrefutáveis.",
  ],
  [
    "Os meus dados pessoais e senhas aparecem no relatório?",
    "Não. O SignallQ protege sua privacidade mascarando identificadores sensíveis como senhas de rede e IPs privados que possam expor seu roteador.",
  ],
  [
    "Como exportar o arquivo no celular?",
    "Ao concluir um diagnóstico no app Android, basta tocar no ícone de compartilhamento para gerar o PDF ou enviar por e-mail e WhatsApp.",
  ],
  [
    "Por que a captura promocional não está sendo exibida agora?",
    "Seguimos a política de transparência técnica do produto: a captura de referência fornecida apresentava um estado de teste não concluído. Para não distorcer a experiência nem exibir diagnósticos falsos, mantemos as informações auditadas e atualizaremos a tela na próxima remessa de assets.",
  ],
];

export function RelatorioPdfContent() {
  return (
    <FeatureMarketingLayout
      eyebrow="Relatório Técnico · Exclusivo no Android"
      title="Leve o diagnóstico da sua internet em PDF."
      intro="O SignallQ para Android consolida histórico de velocidade, latência sob carga, saturação de canais e saúde de rota em um documento formatado para você protocolar chamados ou cobrar sua operadora."
      screenshotPendingNotice="Funcionalidade disponível no app Android (menu Ferramentas > Relatório). Captura de alta resolução com relatório completo em processo de validação técnica."
      benefits={[
        {
          title: "Histórico auditado com data e hora",
          description:
            "Comprove para a operadora que a instabilidade é recorrente ao longo de vários dias e horários.",
        },
        {
          title: "Privacidade e dados mascarados",
          description:
            "O relatório oculta identificadores privados sensíveis para que você possa anexá-lo com segurança a protocolos de suporte.",
        },
        {
          title: "Linguagem técnica e compreensível",
          description:
            "Apresenta diagnósticos estruturados em palavras e gráficos com parâmetros claros de perda, jitter e velocidade.",
        },
      ]}
      howToUseSteps={[
        {
          step: 1,
          title: "Execute os testes no app",
          description:
            "Realize medições de velocidade e diagnósticos de conectividade ao longo do dia no SignallQ Android.",
        },
        {
          step: 2,
          title: "Gere o relatório",
          description:
            "Na tela de Ferramentas ou Histórico, selecione 'Relatório para sua operadora'.",
        },
        {
          step: 3,
          title: "Compartilhe ou anexe",
          description:
            "Exporte o documento em PDF para enviar ao suporte técnico da operadora ou abrir protocolo de verificação.",
        },
      ]}
      resultsAndLimitations={{
        title: "Interpretação e finalidade do relatório",
        description:
          "O relatório reúne as evidências coletadas pelo motor determinístico do aplicativo Android.",
        items: [
          {
            label: "Evidência Documental",
            explanation:
              "Serve como comprovação de que as falhas ocorreram na camada de rota ou na infraestrutura da operadora, e não no Wi-Fi interno da casa.",
          },
          {
            label: "Parâmetros Padronizados",
            explanation:
              "Utiliza referências alinhadas aos padrões técnicos brasileiros (Anatel) para latência, jitter e entrega de velocidade contratada.",
          },
        ],
        limitationNote:
          "O relatório do SignallQ reflete as medições reais aferidas pelo dispositivo no momento dos testes. Ele serve como suporte técnico documentado para o consumidor e não substitui a perícia oficial da agência reguladora.",
      }}
      faqItems={FAQ_ITEMS}
      relatedFeatureLinks={[
        { title: "Histórico de medições", href: "/funcionalidades", badge: "App" },
        { title: "Equipamento da operadora", href: "/funcionalidades", badge: "ONT/Modem" },
        { title: "Teste de velocidade", href: "/teste-de-velocidade", badge: "Web" },
      ]}
      jsonLd={JSON_LD}
    />
  );
}
