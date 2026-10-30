import React from 'react';
import { useTokens } from '../theme/ThemeProvider.js';

export interface TabsProps {
  /** Cada opção é `[id, label]`. */
  options: [string, string][];
  value: string;
  onChange?: (id: string) => void;
}

/**
 * Tabs com sublinhado (nav topo dentro de uma tela, ex.: Wi-Fi / Canal / Móvel).
 * A `borderBottom` do wrapper NÃO separa um container do fundo — tabs e o conteúdo abaixo
 * ficam no mesmo tom de superfície, então não há profundidade para diferenciar por tint.
 * É um divisor funcional entre a faixa de tabs e o conteúdo (mesmo papel do indicador de 3px
 * de cada aba, só que para o grupo inteiro) — permitido pela regra de profundidade
 * (ver docs/04-design-system.md, seção 6.4).
 */
export function Tabs({ options, value, onChange }: TabsProps) {
  const LK = useTokens();
  return (
    <div style={{ display: 'flex', borderBottom: `1px solid ${LK.outlineVariant}`, flex: 'none' }}>
      {options.map(([id, label]) => {
        const on = id === value;
        return (
          <button
            key={id}
            onClick={() => onChange && onChange(id)}
            style={{
              font: `500 14px/20px ${LK.font}`, letterSpacing: '.1px', flex: 1, background: 'none', border: 0, cursor: 'pointer',
              padding: '16px 0', color: on ? LK.primary : LK.onSurfaceVariant,
              borderBottom: on ? `3px solid ${LK.primary}` : '3px solid transparent', marginBottom: -1,
            }}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}
