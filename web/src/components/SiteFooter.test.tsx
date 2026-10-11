import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { SiteFooter } from "./SiteFooter";

describe("SiteFooter", () => {
  afterEach(() => cleanup());

  it("expõe Privacidade, Termos e o e-mail de suporte", () => {
    render(<SiteFooter />);
    expect(screen.getByRole("link", { name: "Privacidade" })).toHaveAttribute("href", "/privacidade");
    expect(screen.getByRole("link", { name: "Termos" })).toHaveAttribute("href", "/termos");
    expect(screen.getByRole("link", { name: "suporte@signallq.com" })).toHaveAttribute("href", "mailto:suporte@signallq.com");
    expect(screen.getByText(/Buildea/)).toBeInTheDocument();
  });
});
