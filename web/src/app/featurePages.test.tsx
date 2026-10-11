import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import TestePingPage from './teste-de-ping/page'
import TesteDownloadUploadPage from './teste-de-download-e-upload/page'
import TesteDnsPage from './teste-de-dns/page'
import TesteJitterPage from './teste-de-jitter/page'
import CanaisWifiPage from './canais-wifi/page'
import RelatorioPdfPage from './relatorio-pdf/page'
import SinalWifiPage from './sinal-wifi/page'
import QuemEstaNaMinhaRedePage from './quem-esta-na-minha-rede/page'
import MapaDeCalorWifiPage from './mapa-de-calor-wifi/page'
import Sinal4g5gPage from './sinal-4g-5g/page'

describe('feature pages', () => {
  it('renders ping test page', () => {
    const markup = renderToStaticMarkup(<TestePingPage />)
    expect(markup).toContain('Qual é o ping da sua internet?')
    expect(markup).toContain('Medir meu ping')
  })

  it('renders download and upload test page', () => {
    const markup = renderToStaticMarkup(<TesteDownloadUploadPage />)
    expect(markup).toContain('Quantos Mbps sua internet entrega?')
    expect(markup).toContain('Iniciar teste')
  })

  it('renders dns test page', () => {
    const markup = renderToStaticMarkup(<TesteDnsPage />)
    expect(markup).toContain('Qual DNS responde mais rápido para você?')
    expect(markup).toContain('Comparar DNS')
  })

  it('renders jitter test page', () => {
    const markup = renderToStaticMarkup(<TesteJitterPage />)
    expect(markup).toContain('Por que a chamada engasga com internet rápida?')
    expect(markup).toContain('Medir estabilidade')
  })

  it('renders canais wifi page', () => {
    const markup = renderToStaticMarkup(<CanaisWifiPage />)
    expect(markup).toContain('Qual é o melhor canal de Wi-Fi para o seu roteador?')
    expect(markup).toContain('Baixar no Google Play')
  })

  it('renders relatorio pdf page', () => {
    const markup = renderToStaticMarkup(<RelatorioPdfPage />)
    expect(markup).toContain('Leve o diagnóstico da sua internet em PDF.')
  })

  it('renders sinal wifi page', () => {
    const markup = renderToStaticMarkup(<SinalWifiPage />)
    expect(markup).toContain('Seu sinal de Wi-Fi está bom ou fraco?')
  })

  it('renders quem esta na minha rede page', () => {
    const markup = renderToStaticMarkup(<QuemEstaNaMinhaRedePage />)
    expect(markup).toContain('Quem está conectado ao seu Wi-Fi?')
  })

  it('renders mapa de calor wifi page', () => {
    const markup = renderToStaticMarkup(<MapaDeCalorWifiPage />)
    expect(markup).toContain('Onde o Wi-Fi da sua casa cai?')
  })

  it('renders sinal 4g 5g page', () => {
    const markup = renderToStaticMarkup(<Sinal4g5gPage />)
    expect(markup).toContain('Seu sinal 4G ou 5G está bom?')
  })
})
