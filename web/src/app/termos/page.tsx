import type { Metadata } from 'next'
import { LegalPage } from '../../components/LegalPage'
import { PAGE_META } from '../../lib/pageMetaCatalog'
import { routeMetadata } from '../../lib/routeMetadata'

export const metadata: Metadata = routeMetadata(PAGE_META['/termos'])

const SECTIONS = [
  {
    t: '1. Aceitação dos termos',
    ps: ['Ao usar o aplicativo SignallQ, você concorda com estes Termos de Uso. Se não concordar, não utilize o aplicativo.'],
  },
  {
    t: '2. Descrição do serviço',
    ps: ['O SignallQ é um aplicativo Android que mede velocidade, sinal Wi-Fi, sinal móvel e latência, e oferece diagnóstico de causa provável para problemas de conexão.'],
  },
  {
    t: '3. Uso permitido',
    ps: ['Você pode medir e entender sua própria conexão. Não pode usar o aplicativo para atacar, sobrecarregar ou interferir na infraestrutura de medição, nem para fins ilegais.'],
  },
  {
    t: '4. Gratuidade',
    ps: ['O download e as funcionalidades básicas do aplicativo são gratuitos. O app pode exibir anúncios quando configurado, conforme descrito na Política de Privacidade.'],
  },
  {
    t: '5. Disponibilidade',
    ps: ['O serviço é fornecido "como está". Não garantimos disponibilidade ininterrupta nem precisão absoluta: a medição depende de infraestrutura de terceiros (Cloudflare, Google).'],
  },
  {
    t: '6. Privacidade',
    ps: ['O tratamento dos seus dados é regido pela nossa Política de Privacidade, disponível em /privacidade.'],
  },
  {
    t: '7. Propriedade intelectual',
    ps: ['O SignallQ, incluindo código, design, marca e conteúdo, é propriedade exclusiva do SignallQ. Todos os direitos reservados.'],
  },
  {
    t: '8. Limitação de responsabilidade',
    ps: ['O SignallQ não se responsabiliza por danos decorrentes do uso do aplicativo, de decisões tomadas com base nos resultados ou de indisponibilidade temporária.'],
  },
  {
    t: '9. Alterações nos termos',
    ps: ['O SignallQ pode atualizar estes Termos a qualquer momento. O uso continuado após alterações implica aceitação.'],
  },
  {
    t: '10. Legislação aplicável',
    ps: ['Estes Termos são regidos pelas leis brasileiras, em conformidade com a LGPD (Lei 13.709/2018) e o Marco Civil da Internet (Lei 12.965/2014).'],
  },
  {
    t: '11. Contato',
    ps: ['Para dúvidas sobre estes Termos: suporte@signallq.com.'],
  },
]

export default function Page() {
  return <LegalPage title="Termos de Uso" updated="Última atualização: 27 de agosto de 2026" sections={SECTIONS} />
}
