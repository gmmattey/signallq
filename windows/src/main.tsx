import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import './styles.css'

async function start() {
  // Backend simulado só em desenvolvimento (VITE_MOCK=1); o build de produção descarta este ramo.
  if (import.meta.env.VITE_MOCK === '1') (await import('./devMock')).installDevMock()
  createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)
}
void start()
