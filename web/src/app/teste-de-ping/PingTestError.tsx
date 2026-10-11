import Link from 'next/link'

export function PingTestError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="text-center py-6">
      <h2 className="m-0 text-[clamp(28px,4vw,36px)] font-bold leading-[1.1] tracking-[-1px] text-[#1C1B1F]">
        Não conseguimos medir o ping.
      </h2>
      <p className="mx-auto mt-4 max-w-[26em] text-[16px] sm:text-[17px] leading-6 text-[#49454F]">
        Verifique sua conexão com a internet e tente novamente. Se a falha persistir, pode haver bloqueio de rota ou perda severa de pacotes.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <button
          type="button"
          onClick={onRetry}
          className="cursor-pointer rounded-full border-0 bg-[#5B21D6] px-7 py-3 text-[15px] font-semibold text-white shadow-sm hover:bg-[#6D28D9] transition-colors"
        >
          Tentar de novo
        </button>
        <Link
          href="/#baixar"
          className="rounded-full bg-[#F3EEFA] px-7 py-3 text-[15px] font-semibold text-[#210A5C] no-underline hover:bg-[#EAE0FF] transition-colors"
        >
          Baixar o app
        </Link>
      </div>
    </div>
  )
}
