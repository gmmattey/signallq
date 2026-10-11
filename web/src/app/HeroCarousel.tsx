"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { AppScreenshot } from "@/components/AppScreenshot";
import { APPROVED_SCREENSHOTS, type ApprovedScreenshot } from "@/lib/approvedScreenshotsManifest";
import {
  FEATURE_HERO_RECURSO_CLICADO,
  FEATURE_HERO_SLIDE_MANUAL,
  trackFeatureUsed,
} from "@/lib/telemetry";

export interface HeroSlide {
  id: string;
  featureName: string;
  title: string;
  description: string;
  screenshot: ApprovedScreenshot;
  href: string;
  actionText: string;
}

export const HERO_SLIDES: HeroSlide[] = [
  {
    id: "diagnostico",
    featureName: "Diagnóstico SignallQ",
    title: "Sua internet está ruim? Descubra a causa provável.",
    description: "Analise sua conexão, entenda os indicadores e saiba o que tentar melhorar.",
    screenshot: APPROVED_SCREENSHOTS.homeDiagnostico,
    href: "/como-funciona",
    actionText: "Conhecer recurso →",
  },
  {
    id: "gamer",
    featureName: "SignallQ Gamer",
    title: "O jogo está travando? Pode ser sua conexão.",
    description: "Avalie a estabilidade da rede para seu tipo de jogo e descubra o que pode estar prejudicando suas partidas.",
    screenshot: APPROVED_SCREENSHOTS.modoGamer,
    href: "/modo-gamer",
    actionText: "Conhecer recurso →",
  },
  {
    id: "canais-wifi",
    featureName: "Análise Wi-Fi",
    title: "Seu Wi-Fi pode estar no canal errado.",
    description: "Identifique congestionamento de canais e conheça alternativas para melhorar sua rede.",
    screenshot: APPROVED_SCREENSHOTS.canaisWifi,
    href: "/canais-wifi",
    actionText: "Conhecer recurso →",
  },
];

interface HeroCarouselProps {
  slides?: HeroSlide[];
  autoRotateIntervalMs?: number;
}

export function HeroCarousel({
  slides = HERO_SLIDES,
  autoRotateIntervalMs = 8000,
}: HeroCarouselProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const [hasInteracted, setHasInteracted] = useState(false);
  const [isDocumentVisible, setIsDocumentVisible] = useState(true);
  const [reducedMotion, setReducedMotion] = useState(false);

  const touchStartX = useRef<number | null>(null);
  const touchStartY = useRef<number | null>(null);

  // Detecta prefers-reduced-motion sem mismatch de hidratação
  useEffect(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") return;
    const mql = window.matchMedia("(prefers-reduced-motion: reduce)");
    setReducedMotion(mql.matches);

    const handleMqlChange = (e: MediaQueryListEvent) => {
      setReducedMotion(e.matches);
    };

    mql.addEventListener?.("change", handleMqlChange);
    return () => mql.removeEventListener?.("change", handleMqlChange);
  }, []);

  // Monitora visibilidade da aba para suspender rotação quando em segundo plano
  useEffect(() => {
    const handleVisibility = () => {
      setIsDocumentVisible(document.visibilityState === "visible");
    };
    document.addEventListener("visibilitychange", handleVisibility);
    return () => document.removeEventListener("visibilitychange", handleVisibility);
  }, []);

  // Rotação automática suave a cada ~8 segundos (desativada sob interação manual, foco, hover, aba inativa ou movimento reduzido)
  useEffect(() => {
    if (reducedMotion || isPaused || hasInteracted || !isDocumentVisible) {
      return;
    }

    const timer = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % slides.length);
    }, autoRotateIntervalMs);

    return () => clearInterval(timer);
  }, [reducedMotion, isPaused, hasInteracted, isDocumentVisible, slides.length, autoRotateIntervalMs]);

  const handleManualNavigation = (targetIndex: number) => {
    setHasInteracted(true);
    setCurrentIndex(targetIndex);
    trackFeatureUsed(FEATURE_HERO_SLIDE_MANUAL);
  };

  const handlePrev = () => {
    const nextIndex = (currentIndex - 1 + slides.length) % slides.length;
    handleManualNavigation(nextIndex);
  };

  const handleNext = () => {
    const nextIndex = (currentIndex + 1) % slides.length;
    handleManualNavigation(nextIndex);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowLeft") {
      e.preventDefault();
      handlePrev();
    } else if (e.key === "ArrowRight") {
      e.preventDefault();
      handleNext();
    }
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
    touchStartY.current = e.touches[0].clientY;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current === null || touchStartY.current === null) return;
    const deltaX = e.changedTouches[0].clientX - touchStartX.current;
    const deltaY = e.changedTouches[0].clientY - touchStartY.current;
    touchStartX.current = null;
    touchStartY.current = null;

    // Apenas dispara se o gesto for predominantemente horizontal e intencional (>45px)
    if (Math.abs(deltaX) > 45 && Math.abs(deltaX) > Math.abs(deltaY) * 1.4) {
      if (deltaX < 0) {
        handleNext();
      } else {
        handlePrev();
      }
    }
  };

  return (
    <section
      role="region"
      aria-roledescription="carrossel"
      aria-label="Vitrine de funcionalidades do aplicativo"
      tabIndex={0}
      onKeyDown={handleKeyDown}
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      onFocusCapture={() => setIsPaused(true)}
      onBlurCapture={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget)) {
          setIsPaused(false);
        }
      }}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
      className="relative flex w-full max-w-[340px] flex-col items-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] rounded-[44px]"
    >
      {/* Área deslizante da vitrine (apenas esta área anima, mantendo o restante do hero fixo) */}
      <div className="w-full overflow-hidden">
        <div
          className={`flex w-full ${
            reducedMotion ? "transition-none" : "transition-transform duration-500 ease-out"
          }`}
          style={{ transform: `translateX(-${currentIndex * 100}%)` }}
        >
          {slides.map((slide, idx) => {
            const isCurrent = idx === currentIndex;
            return (
              <div
                key={slide.id}
                role="group"
                aria-roledescription="slide"
                aria-label={`${idx + 1} de ${slides.length}: ${slide.featureName}`}
                aria-hidden={!isCurrent ? "true" : undefined}
                className="flex w-full shrink-0 flex-col items-center px-1"
              >
                {/* Smartphone com captura real Android */}
                <div className="w-[270px] sm:w-[290px]">
                  <AppScreenshot
                    screenshot={slide.screenshot}
                    priority={idx === 0}
                    maxWidth={290}
                    showBadge={true}
                    showCaption={false}
                  />
                </div>

                {/* Bloco discreto de benefício */}
                <div className="mt-4 flex min-h-[116px] flex-col items-center text-center px-2">
                  <span className="inline-block rounded-full bg-[#F3EEFA] px-3 py-1 text-[12px] font-bold text-[#5B21D6]">
                    {slide.featureName}
                  </span>
                  <h3 className="mt-2 text-[16px] sm:text-[17px] font-bold leading-snug text-[#1C1B1F]">
                    {slide.title}
                  </h3>
                  <p className="mt-1 max-w-[24em] text-[13px] leading-relaxed text-[#49454F]">
                    {slide.description}
                  </p>
                  <Link
                    href={slide.href}
                    tabIndex={isCurrent ? 0 : -1}
                    onClick={() => trackFeatureUsed(FEATURE_HERO_RECURSO_CLICADO)}
                    className="mt-2 inline-flex items-center gap-1 text-[13px] font-semibold text-[#5B21D6] hover:text-[#4A18B0] underline-offset-4 hover:underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] rounded-sm"
                  >
                    {slide.actionText}
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Controles manuais e indicadores de posição */}
      <div
        className="mt-3 flex items-center justify-center gap-4"
        aria-label="Controles do carrossel"
      >
        <button
          type="button"
          onClick={handlePrev}
          aria-label="Slide anterior"
          className="flex h-9 w-9 items-center justify-center rounded-full border border-[#E7E0EC] bg-white text-[#49454F] shadow-sm hover:border-[#D5C2FF] hover:bg-[#F3EEFA] hover:text-[#5B21D6] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] transition-all"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M15 19l-7-7 7-7" />
          </svg>
        </button>

        <div className="flex items-center gap-2">
          {slides.map((s, idx) => {
            const isActive = idx === currentIndex;
            return (
              <button
                key={s.id}
                type="button"
                onClick={() => handleManualNavigation(idx)}
                aria-label={`Ir para o slide ${idx + 1}: ${s.featureName}`}
                aria-current={isActive ? "true" : undefined}
                className={`h-2.5 rounded-full transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] ${
                  isActive ? "w-7 bg-[#5B21D6]" : "w-2.5 bg-[#E7E0EC] hover:bg-[#CAC4D0]"
                }`}
              />
            );
          })}
        </div>

        <button
          type="button"
          onClick={handleNext}
          aria-label="Próximo slide"
          className="flex h-9 w-9 items-center justify-center rounded-full border border-[#E7E0EC] bg-white text-[#49454F] shadow-sm hover:border-[#D5C2FF] hover:bg-[#F3EEFA] hover:text-[#5B21D6] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6] transition-all"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
    </section>
  );
}
