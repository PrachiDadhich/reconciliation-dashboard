import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

function App() {
  return <main className="shell"><p className="eyebrow">Ledger Match</p><h1>Reconciliation dashboard</h1><p>Upload your order and payment exports to find where the numbers disagree.</p></main>
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)