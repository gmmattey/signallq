"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";

// O protótipo destaca a página ativa com pill roxo suave (#EAE0FF).
const LINKS: { href: string; label: string; neverActive?: boolean }[] = [
  { href: "/teste-de-velocidade", label: "Teste de velocidade", neverActive: true },
  { href: "/funcionalidades", label: "Funcionalidades" },
  { href: "/guias", label: "Guias" },
  { href: "/como-funciona", label: "Como funciona" },
  { href: "/duvidas", label: "Dúvidas" },
];

// Na home o protótipo inclui atalho para a seção "O resultado".
const HOME_LINKS: { href: string; label: string; neverActive?: boolean }[] = [
  { href: "/teste-de-velocidade", label: "Teste de velocidade" },
  { href: "/funcionalidades", label: "Funcionalidades" },
  { href: "/como-funciona", label: "Como funciona" },
  { href: "/guias", label: "Guias" },
  { href: "#resultado", label: "O resultado" },
  { href: "/duvidas", label: "Dúvidas" },
];

export function SiteNav() {
  const pathname = usePathname() ?? "";
  const links = pathname === "/" ? HOME_LINKS : LINKS;
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  // Fecha o menu ao mudar de rota ou ao pressionar Escape
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setMobileMenuOpen(false);
      }
    };
    if (mobileMenuOpen) {
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [mobileMenuOpen]);

  return (
    <header className="sticky top-0 z-30 leading-[normal] border-b border-[#F3EEFA] bg-white/[0.95] backdrop-blur-[8px]">
      <div className="mx-auto flex max-w-[1168px] items-center gap-4 sm:gap-8 px-4 sm:px-6 py-[14px]">
        <Link
          href="/"
          aria-label="Página inicial SignallQ"
          className="flex items-center gap-[10px] text-[18px] font-bold text-[#1C1B1F] no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] rounded-lg"
        >
          <Image src="/assets/signallq-symbol-512.png" alt="" width={32} height={32} className="h-8 w-8 object-contain" />
          SignallQ
        </Link>

        {/* Navegação desktop */}
        <nav className="ml-auto hidden items-center gap-1 text-[14px] sm:flex" aria-label="Navegação principal">
          {links.map((link) => {
            const active = !link.neverActive && !link.href.startsWith("#") && (pathname === link.href || pathname.startsWith(`${link.href}/`));
            return (
              <Link
                key={link.href}
                href={link.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-full px-3 py-2 no-underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] ${
                  active
                    ? "bg-[#EAE0FF] font-semibold text-[#210A5C]"
                    : "font-medium text-[#49454F] hover:text-[#210A5C]"
                }`}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>

        {/* CTA desktop/mobile */}
        <Link
          href="/#baixar"
          className="ml-auto hidden sm:inline-block rounded-full bg-[#5B21D6] px-5 py-[11px] text-[14px] font-medium text-white no-underline hover:bg-[#4A18B0] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]"
        >
          Baixar o app
        </Link>

        {/* Botão do menu mobile */}
        <button
          type="button"
          onClick={() => setMobileMenuOpen((open) => !open)}
          aria-expanded={mobileMenuOpen}
          aria-controls="mobile-navigation"
          aria-label={mobileMenuOpen ? "Fechar menu" : "Abrir menu de navegação"}
          className="ml-auto flex sm:hidden h-11 w-11 items-center justify-center rounded-full text-[#1C1B1F] hover:bg-[#F3EEFA] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] transition-colors"
        >
          {mobileMenuOpen ? (
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          ) : (
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          )}
        </button>
      </div>

      {/* Painel de navegação mobile */}
      {mobileMenuOpen && (
        <div
          id="mobile-navigation"
          className="sm:hidden border-t border-[#F3EEFA] bg-white px-6 pb-6 pt-3 shadow-xl animate-in fade-in slide-in-from-top-2 duration-150"
        >
          <nav className="flex flex-col gap-1 text-[16px]" aria-label="Navegação mobile">
            {links.map((link) => {
              const active = !link.neverActive && !link.href.startsWith("#") && (pathname === link.href || pathname.startsWith(`${link.href}/`));
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  onClick={() => setMobileMenuOpen(false)}
                  aria-current={active ? "page" : undefined}
                  className={`flex min-h-[44px] items-center rounded-xl px-4 py-2.5 no-underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] ${
                    active
                      ? "bg-[#EAE0FF] font-semibold text-[#210A5C]"
                      : "font-medium text-[#49454F] hover:bg-[#F8F5FB] hover:text-[#210A5C]"
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}
            <Link
              href="/#outras-solucoes"
              onClick={() => setMobileMenuOpen(false)}
              className="flex min-h-[44px] items-center rounded-xl px-4 py-2.5 font-medium text-[#49454F] hover:bg-[#F8F5FB] hover:text-[#210A5C] no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]"
            >
              Outras soluções
            </Link>
            <div className="mt-3 pt-3 border-t border-[#F3EEFA]">
              <Link
                href="/#baixar"
                onClick={() => setMobileMenuOpen(false)}
                className="flex min-h-[48px] w-full items-center justify-center rounded-full bg-[#5B21D6] px-5 py-3 text-[15px] font-semibold text-white no-underline hover:bg-[#4A18B0] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]"
              >
                Baixar no Google Play
              </Link>
            </div>
          </nav>
        </div>
      )}
    </header>
  );
}

