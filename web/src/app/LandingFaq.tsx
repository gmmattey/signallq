import { FaqList } from "@/components/FaqList";

const FAQ = [
  { q: 'O SignallQ é só um teste de velocidade?', a: 'Não. Ele mede quando necessário, mas o foco é explicar a causa do problema e orientar a solução.' },
  { q: 'Preciso entender de redes?', a: 'Não. O resultado usa frases curtas e palavras comuns. O detalhe técnico é opcional.' },
  { q: 'Funciona em iPhone?', a: 'O SignallQ é focado em Android. Para usuários de iPhone e iPad no ecossistema Apple, conheça o Linka, nossa solução para iOS.' },
  { q: 'Quanto custa?', a: 'Você pode começar de graça, sem cadastro.' },
];


export function LandingFaq() {
  return (
    <section id="duvidas" className="mx-auto w-full max-w-[808px] px-6 pb-[72px] pt-28">
      <h2 className="m-0 mb-8 text-[clamp(30px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px]">Dúvidas comuns</h2>
      <FaqList items={FAQ} />
    </section>
  );
}
