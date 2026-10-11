import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import TestePingPage from "./teste-de-ping/page";
import TesteDownloadUploadPage from "./teste-de-download-e-upload/page";
import TesteDnsPage from "./teste-de-dns/page";
import TesteJitterPage from "./teste-de-jitter/page";
import CanaisWifiPage from "./canais-wifi/page";
import RelatorioPdfPage from "./relatorio-pdf/page";
import SinalWifiPage from "./sinal-wifi/page";
import QuemEstaNaMinhaRedePage from "./quem-esta-na-minha-rede/page";
import MapaDeCalorWifiPage from "./mapa-de-calor-wifi/page";
import Sinal4g5gPage from "./sinal-4g-5g/page";

describe("feature pages", () => {
  it("renders ping test page", () => {
    const markup = renderToStaticMarkup(<TestePingPage />);
    expect(markup).toContain("Qual é o ping da sua internet?");
    expect(markup).toContain("Medir meu ping");
  });

  it("renders download and upload test page", () => {
    const markup = renderToStaticMarkup(<TesteDownloadUploadPage />);
    expect(markup).toContain("Quantos Mbps sua internet entrega");
    expect(markup).toContain("Iniciar teste");
  });

  it("renders dns test page", () => {
    const markup = renderToStaticMarkup(<TesteDnsPage />);
    expect(markup).toContain("Qual servidor DNS responde mais rápido para você?");
    expect(markup).toContain("Iniciar comparação");
  });

  it("renders jitter test page", () => {
    const markup = renderToStaticMarkup(<TesteJitterPage />);
    expect(markup).toContain("Sua conexão oscila enquanto você usa?");
    expect(markup).toContain("Iniciar teste de jitter");
  });

  it("renders canais wifi page", () => {
    const markup = renderToStaticMarkup(<CanaisWifiPage />);
    expect(markup).toContain("Descubra se o canal do seu Wi-Fi está congestionado.");
    expect(markup).toContain("Baixar grátis no Google Play");
  });

  it("renders relatorio pdf page", () => {
    const markup = renderToStaticMarkup(<RelatorioPdfPage />);
    expect(markup).toContain("Leve o diagnóstico da sua internet em PDF.");
    expect(markup).toContain("Baixar grátis no Google Play");
  });

  it("renders sinal wifi page", () => {
    const markup = renderToStaticMarkup(<SinalWifiPage />);
    expect(markup).toContain("Entenda a qualidade do sinal Wi-Fi ao seu redor.");
    expect(markup).toContain("Baixar grátis no Google Play");
  });

  it("renders quem esta na minha rede page", () => {
    const markup = renderToStaticMarkup(<QuemEstaNaMinhaRedePage />);
    expect(markup).toContain("Descubra quais aparelhos estão visíveis na sua rede.");
    expect(markup).toContain("Baixar grátis no Google Play");
  });

  it("renders mapa de calor wifi page", () => {
    const markup = renderToStaticMarkup(<MapaDeCalorWifiPage />);
    expect(markup).toContain("Mapeie visualmente os pontos cegos do seu Wi-Fi.");
    expect(markup).toContain("Baixar grátis no Google Play");
  });

  it("renders sinal 4g 5g page", () => {
    const markup = renderToStaticMarkup(<Sinal4g5gPage />);
    expect(markup).toContain("Saiba o que a intensidade do sinal móvel significa.");
    expect(markup).toContain("Baixar grátis no Google Play");
  });
});
