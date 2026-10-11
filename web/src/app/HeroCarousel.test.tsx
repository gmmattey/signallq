import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HeroCarousel } from "./HeroCarousel";
import * as telemetry from "@/lib/telemetry";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe("HeroCarousel — vitrine dinâmica de funcionalidades reais", () => {
  beforeEach(() => {
    vi.spyOn(telemetry, "trackFeatureUsed").mockImplementation(() => {});
  });

  it("renderiza o primeiro slide por padrão com captura real, título, benefício e link", () => {
    render(<HeroCarousel />);

    expect(screen.getByText("Diagnóstico SignallQ")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        level: 3,
        name: "Sua internet está ruim? Descubra a causa provável.",
      })
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Analise sua conexão, entenda os indicadores e saiba o que tentar melhorar."
      )
    ).toBeInTheDocument();

    const links = screen.getAllByRole("link", { name: "Conhecer recurso →" });
    expect(links[0]).toHaveAttribute("href", "/como-funciona");
  });

  it("renderiza todos os três slides configurados no DOM para SEO e acessibilidade", () => {
    render(<HeroCarousel />);

    expect(screen.getByText("Diagnóstico SignallQ")).toBeInTheDocument();
    expect(screen.getByText("SignallQ Gamer")).toBeInTheDocument();
    expect(screen.getByText("Análise Wi-Fi")).toBeInTheDocument();

    expect(
      screen.getByRole("heading", {
        level: 3,
        name: "O jogo está travando? Pode ser sua conexão.",
        hidden: true,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        level: 3,
        name: "Seu Wi-Fi pode estar no canal errado.",
        hidden: true,
      })
    ).toBeInTheDocument();
  });

  it("navega para o próximo slide ao clicar na seta 'Próximo slide' e dispara telemetria de navegação manual", async () => {
    const user = userEvent.setup();
    render(<HeroCarousel />);

    const nextBtn = screen.getByRole("button", { name: "Próximo slide" });
    await user.click(nextBtn);

    expect(telemetry.trackFeatureUsed).toHaveBeenCalledWith("hero_slide_manual");

    const gamerDot = screen.getByRole("button", {
      name: "Ir para o slide 2: SignallQ Gamer",
    });
    expect(gamerDot).toHaveAttribute("aria-current", "true");
  });

  it("navega para o slide anterior ao clicar na seta 'Slide anterior' e faz wrap-around", async () => {
    const user = userEvent.setup();
    render(<HeroCarousel />);

    const prevBtn = screen.getByRole("button", { name: "Slide anterior" });
    await user.click(prevBtn);

    expect(telemetry.trackFeatureUsed).toHaveBeenCalledWith("hero_slide_manual");

    const wifiDot = screen.getByRole("button", {
      name: "Ir para o slide 3: Análise Wi-Fi",
    });
    expect(wifiDot).toHaveAttribute("aria-current", "true");
  });

  it("navega diretamente para um slide ao clicar no indicador de posição correspondente", async () => {
    const user = userEvent.setup();
    render(<HeroCarousel />);

    const gamerDot = screen.getByRole("button", {
      name: "Ir para o slide 2: SignallQ Gamer",
    });
    await user.click(gamerDot);

    expect(telemetry.trackFeatureUsed).toHaveBeenCalledWith("hero_slide_manual");
    expect(gamerDot).toHaveAttribute("aria-current", "true");
  });

  it("permite navegação via teclado com setas esquerda e direita", () => {
    render(<HeroCarousel />);
    const region = screen.getByRole("region", {
      name: "Vitrine de funcionalidades do aplicativo",
    });

    fireEvent.keyDown(region, { key: "ArrowRight" });
    expect(telemetry.trackFeatureUsed).toHaveBeenCalledWith("hero_slide_manual");
    expect(
      screen.getByRole("button", { name: "Ir para o slide 2: SignallQ Gamer" })
    ).toHaveAttribute("aria-current", "true");

    fireEvent.keyDown(region, { key: "ArrowLeft" });
    expect(
      screen.getByRole("button", {
        name: "Ir para o slide 1: Diagnóstico SignallQ",
      })
    ).toHaveAttribute("aria-current", "true");
  });

  it("suporta gesto de deslizar (swipe) horizontal touch sem bloquear o usuário", () => {
    render(<HeroCarousel />);
    const region = screen.getByRole("region", {
      name: "Vitrine de funcionalidades do aplicativo",
    });

    fireEvent.touchStart(region, {
      touches: [{ clientX: 200, clientY: 100 }],
    });
    fireEvent.touchEnd(region, {
      changedTouches: [{ clientX: 120, clientY: 105 }],
    });

    expect(telemetry.trackFeatureUsed).toHaveBeenCalledWith("hero_slide_manual");
    expect(
      screen.getByRole("button", { name: "Ir para o slide 2: SignallQ Gamer" })
    ).toHaveAttribute("aria-current", "true");
  });

  it("dispara telemetria de recurso clicado ao acionar o link do slide", async () => {
    const user = userEvent.setup();
    render(<HeroCarousel />);

    const link = screen.getAllByRole("link", { name: "Conhecer recurso →" })[0];
    await user.click(link);

    expect(telemetry.trackFeatureUsed).toHaveBeenCalledWith("hero_recurso_clicado");
  });

  it("avança automaticamente a cada intervalo configurado sem disparar telemetria manual", () => {
    vi.useFakeTimers();
    render(<HeroCarousel autoRotateIntervalMs={8000} />);

    expect(
      screen.getByRole("button", {
        name: "Ir para o slide 1: Diagnóstico SignallQ",
      })
    ).toHaveAttribute("aria-current", "true");

    act(() => {
      vi.advanceTimersByTime(8000);
    });

    expect(
      screen.getByRole("button", { name: "Ir para o slide 2: SignallQ Gamer" })
    ).toHaveAttribute("aria-current", "true");

    // A rotação automática NÃO deve inflar a métrica de interesse do usuário
    expect(telemetry.trackFeatureUsed).not.toHaveBeenCalledWith("hero_slide_manual");
  });

  it("pausa rotação automática quando o cursor passa sobre o carrossel (hover)", () => {
    vi.useFakeTimers();
    render(<HeroCarousel autoRotateIntervalMs={8000} />);
    const region = screen.getByRole("region", {
      name: "Vitrine de funcionalidades do aplicativo",
    });

    fireEvent.mouseEnter(region);
    act(() => {
      vi.advanceTimersByTime(16000);
    });

    // Deve permanecer no slide 1 porque está em hover
    expect(
      screen.getByRole("button", {
        name: "Ir para o slide 1: Diagnóstico SignallQ",
      })
    ).toHaveAttribute("aria-current", "true");

    fireEvent.mouseLeave(region);
    act(() => {
      vi.advanceTimersByTime(8000);
    });

    // Retoma a rotação após a saída do cursor
    expect(
      screen.getByRole("button", { name: "Ir para o slide 2: SignallQ Gamer" })
    ).toHaveAttribute("aria-current", "true");
  });

  it("desativa rotação automática quando o usuário tem prefers-reduced-motion ativo", () => {
    vi.useFakeTimers();
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: query.includes("prefers-reduced-motion: reduce"),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    render(<HeroCarousel autoRotateIntervalMs={8000} />);

    act(() => {
      vi.advanceTimersByTime(24000);
    });

    // Deve permanecer estático no slide 1
    expect(
      screen.getByRole("button", {
        name: "Ir para o slide 1: Diagnóstico SignallQ",
      })
    ).toHaveAttribute("aria-current", "true");
  });
});
