import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { HomePage } from './pages/HomePage'

// TODO(sessão 2): rotas de login, /oauth/callback (lê tokens do fragmento
// da URL após login social), claims e listas.
export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
      </Routes>
    </BrowserRouter>
  )
}
