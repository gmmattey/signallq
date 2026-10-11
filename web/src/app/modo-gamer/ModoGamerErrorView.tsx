interface ModoGamerErrorViewProps {
  errorMsg: string | null
  onRetry: () => void
  errorHeadingRef: React.RefObject<HTMLHeadingElement | null>
}

export function ModoGamerErrorView({
  errorMsg,
  onRetry,
  errorHeadingRef,
}: ModoGamerErrorViewProps) {
  return (
    <div className="py-6 text-center" role="alert">
      <h3
        ref={errorHeadingRef}
        tabIndex={-1}
        className="m-0 text-[24px] font-bold text-white outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#14171C]"
      >
        Não conseguimos medir a conexão.
      </h3>
      <p className="mx-auto mt-2.5 max-w-[22em] text-[15px] leading-[22px] text-[#CAC4D0]">
        {errorMsg || 'Verifique se você está conectado à internet e tente novamente.'}
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-6 cursor-pointer rounded-full border-0 bg-[#7B4DF0] px-8 py-3.5 text-[16px] font-semibold text-white hover:bg-[#8D64F5] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#14171C] transition-all"
      >
        Tentar de novo
      </button>
    </div>
  )
}
